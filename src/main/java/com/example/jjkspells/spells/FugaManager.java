package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every active Fuga on the server, in three phases:
 *
 *   CHARGE  - "Kai". A seal blazes on the ground, a bow of flame unfolds and a huge arrow forms and is drawn.
 *   FLIGHT  - the arrow is loosed and streaks forward until it hits a block or an entity.
 *   IMPACT  - a blast front sweeps outward: damage, knockback, crater, fireball, mushroom column, embers,
 *             then burning ground and smoke for a few seconds.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class FugaManager {

    // ---- Tuning -------------------------------------------------------------------------------
    private static final int CHARGE_TICKS = 50;          // how long the bow takes to draw
    private static final double ARROW_SPEED = 3.2;       // blocks per tick (64 blocks per second)
    private static final double MAX_RANGE = 120;         // arrow detonates in mid-air after this distance
    private static final double ARROW_LEN = 5.5;         // drawn length of the arrow
    private static final double SHELL_SPEED = 1.6;       // blocks per tick the blast front travels
    private static final int MAX_CRATER_RADIUS = 16;     // terrain is destroyed up to this far (visuals reach further)
    private static final int LINGER_TICKS = 110;         // how long the fire and smoke keep burning
    private static final boolean SCORCH_AND_FIRE = true; // blackened ground and fires after the blast

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    // ---- State --------------------------------------------------------------------------------
    private enum Phase { CHARGE, FLIGHT, IMPACT }

    /** A right-handed frame: f = forward, r = right, u = up (relative to a look direction). */
    private record Basis(Vec3 f, Vec3 r, Vec3 u) {
        static Basis of(Vec3 look) {
            Vec3 f = look.normalize();
            Vec3 r = f.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0); // looking straight up or down
            r = r.normalize();
            Vec3 u = r.cross(f).normalize();
            return new Basis(f, r, u);
        }
    }

    private static class Fuga {
        final ServerLevel level;
        final UUID casterId;
        final FugaSpell spell;
        final int spellLevel;
        Phase phase = Phase.CHARGE;
        int age = 0;

        // flight
        Vec3 start = Vec3.ZERO;
        Vec3 head = Vec3.ZERO;
        Vec3 dir = new Vec3(0, 0, 1);
        double travelled = 0;

        // impact
        Vec3 center = Vec3.ZERO;
        float radius;
        int craterRadius;
        int impactAge = 0;
        int scorchTick;
        double floorY;
        @Nullable UUID direct;
        final Set<UUID> hit = new HashSet<>();

        Fuga(ServerLevel level, LivingEntity caster, FugaSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
        }
    }

    private static final List<Fuga> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, FugaSpell spell, int spellLevel) {
        ACTIVE.add(new Fuga(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Fuga> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fuga g = it.next();
            Entity e = g.level.getEntity(g.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;

            // the charge needs a living caster; once the arrow is loosed the blast carries on regardless
            if (caster == null && g.phase != Phase.IMPACT) {
                it.remove();
                continue;
            }

            boolean done = false;
            try {
                switch (g.phase) {
                    case CHARGE -> charge(g, caster);
                    case FLIGHT -> flight(g, caster);
                    case IMPACT -> done = impact(g, caster);
                }
            } catch (Exception ex) {
                LOGGER.error("Fuga failed, cancelling it", ex);
                done = true;
            }
            g.age++;
            if (done || g.age > 1200) it.remove();
        }
    }

    // =============================================================================================
    //  PHASE 1: CHARGE
    // =============================================================================================
    private static void charge(Fuga g, LivingEntity c) {
        ServerLevel level = g.level;
        RandomSource rng = level.random;
        int t = g.age;
        double p = t / (double) CHARGE_TICKS;

        Basis b = Basis.of(c.getLookAngle());
        Vec3 f = b.f(), r = b.r(), u = b.u();
        Vec3 eye = c.getEyePosition();
        Vec3 feet = c.position();

        // hold still while the bow is drawn
        if (t % 5 == 0) {
            c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        }

        // ---- opening -------------------------------------------------------------------------
        double gy = feet.y + 0.06;
        if (t == 0) {
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3f, 0.5f);
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2f, 0.6f);
            announce(level, c, Component.literal("Kai").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        if (t % 12 == 0) {
            ringFlat(level, new Vec3(feet.x, gy, feet.z), 5.0 + t * 0.14);
        }

        // ---- the seal on the ground: two counter-rotating triangles in a ring ---------------------
        if (t % 2 == 0) {
            double spin = t * 0.045;
            for (int k = 0; k < 6; k++) {
                double a0 = spin + k * Math.PI / 3.0;
                double a1 = spin + (k + 2) * Math.PI / 3.0;
                Vec3 pa = new Vec3(feet.x + Math.cos(a0) * 3.3, gy, feet.z + Math.sin(a0) * 3.3);
                Vec3 pb = new Vec3(feet.x + Math.cos(a1) * 3.3, gy, feet.z + Math.sin(a1) * 3.3);
                line(level, pa, pb);
            }
        }
        if (t % 3 == 0) {
            double spin = -t * 0.03;
            for (int k = 0; k < 14; k++) {
                double a0 = spin + k * Math.PI * 2 / 14.0;
                double a1 = spin + (k + 1) * Math.PI * 2 / 14.0;
                Vec3 pa = new Vec3(feet.x + Math.cos(a0) * 3.9, gy, feet.z + Math.sin(a0) * 3.9);
                Vec3 pb = new Vec3(feet.x + Math.cos(a1) * 3.9, gy, feet.z + Math.sin(a1) * 3.9);
                line(level, pa, pb);
            }
        }

        // ---- three pillars of flame spiralling up around the caster ---------------------------
        for (int k = 0; k < 3; k++) {
            double a = t * 0.2 + k * (Math.PI * 2 / 3.0);
            double rad = 2.6 - 0.8 * p;
            Vec3 base = new Vec3(feet.x + Math.cos(a) * rad, feet.y + rng.nextDouble() * 0.3, feet.z + Math.sin(a) * rad);
            FX.one(level, ModParticles.FUGA_FLAME.get(), base, new Vec3(0, 0.22 + rng.nextDouble() * 0.12, 0));
            FX.one(level, ModParticles.FUGA_FLAME.get(), base.add(0, 0.5, 0), new Vec3(-Math.sin(a) * 0.05, 0.2, Math.cos(a) * 0.05));
            if (t > 20 && t % 3 == k) {
                FX.one(level, ModParticles.FUGA_BLAST.get(), base, new Vec3(0, 0.14, 0));
            }
        }

        // ---- the bow ------------------------------------------------------------------------------
        double pull = smooth(Mth.clamp((t - 16) / (double) (CHARGE_TICKS - 16), 0.0, 1.0));
        double unfold = smooth(Mth.clamp(t / 14.0, 0.0, 1.0));
        double half = 1.9 * unfold;                          // half the bow's height
        double curve = 0.35 + 0.45 * pull;                   // limbs bend more the harder it is drawn
        Vec3 grip = eye.add(f.scale(2.4)).add(r.scale(0.45)).subtract(u.scale(0.3));
        Vec3 nock = grip.subtract(f.scale(curve + 1.1 * pull));

        Vec3[] limb = new Vec3[9];
        for (int i = 0; i < 9; i++) {
            double tt = -1.0 + i * 0.25;
            limb[i] = grip.add(u.scale(tt * half)).subtract(f.scale(curve * tt * tt));
        }
        for (int i = 0; i < 8; i++) {
            beam(level, limb[i], limb[i + 1]);
        }
        for (int i = 0; i < 4; i++) {                        // flames licking off the limbs
            Vec3 lp = limb[rng.nextInt(9)];
            FX.one(level, ModParticles.FUGA_FLAME.get(), lp.add((rng.nextDouble() - 0.5) * 0.3, 0, (rng.nextDouble() - 0.5) * 0.3),
                    new Vec3((rng.nextDouble() - 0.5) * 0.04, 0.05 + rng.nextDouble() * 0.06, (rng.nextDouble() - 0.5) * 0.04));
        }
        if (unfold > 0.2) {                                  // bowstring
            line(level, limb[0], nock);
            line(level, nock, limb[8]);
        }

        // ---- the arrow -------------------------------------------------------------------------------
        double grow = smooth(Mth.clamp((t - 8) / 18.0, 0.0, 1.0));
        double len = ARROW_LEN * grow;
        if (len > 0.3) {
            Vec3 tail = nock;
            Vec3 head = nock.add(f.scale(len));
            drawArrow(level, tail, head, f, t);
            // sparks gathering into the arrow
            int sparks = 2 + (int) (6 * p);
            for (int i = 0; i < sparks; i++) {
                Vec3 from = nock.add(randomDir(rng).scale(2.5 + rng.nextDouble() * 3.0));
                FX.one(level, ModParticles.FUGA_EMBER.get(), from, nock.add(f.scale(len * rng.nextDouble())).subtract(from).scale(0.08));
            }
        }

        // rising shimmer and crackle
        if (t % 6 == 0) {
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2f, (float) (0.5 + 1.5 * p));
        }
        if (t % 8 == 0) {
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2f, (float) (0.7 + 0.6 * p));
        }
        if (t == CHARGE_TICKS - 10) {
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 1.6f);
        }
        // the last few ticks: the arrowhead flares white
        if (t >= CHARGE_TICKS - 7 && len > 0.3) {
            glow(level, nock.add(f.scale(len)), 1.2 + (t - (CHARGE_TICKS - 7)) * 0.35);
        }

        // ---- release ---------------------------------------------------------------------------------
        if (t >= CHARGE_TICKS) {
            g.phase = Phase.FLIGHT;
            g.dir = f;
            g.start = eye;
            g.head = nock.add(f.scale(1.2));
            g.travelled = 0;

            announce(level, c, Component.literal("Fuga!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4f, 1.8f);
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4f, 1.3f);
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3f, 0.6f);

            // muzzle flash: white burst, shock rings, rays flying out of the bow
            Vec3 muzzle = nock.add(f.scale(1.2));
            glow(level, muzzle, 4.5);
            ringFacing(level, muzzle, 7.0);
            ringFlat(level, new Vec3(feet.x, gy, feet.z), 14.0);
            for (int i = 0; i < 26; i++) {
                Vec3 d = randomDir(rng);
                Vec3 spread = f.scale(0.4).add(d).normalize();
                line(level, muzzle, muzzle.add(spread.scale(3.0 + rng.nextDouble() * 6.0)));
            }
            FX.emit(level, ModParticles.FUGA_FLAME.get(), muzzle.x, muzzle.y, muzzle.z, 40, 0.6, 0.6, 0.6, 0.25);

            // recoil
            c.setDeltaMovement(c.getDeltaMovement().add(-f.x * 0.9, 0.25, -f.z * 0.9));
            c.hurtMarked = true;
        }
    }

    // =============================================================================================
    //  PHASE 2: FLIGHT
    // =============================================================================================
    private static void flight(Fuga g, LivingEntity c) {
        ServerLevel level = g.level;
        RandomSource rng = level.random;

        Vec3 from = g.travelled == 0 ? g.start : g.head;
        Vec3 to = g.head.add(g.dir.scale(ARROW_SPEED));

        // blocks
        BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        boolean blockHit = bh.getType() != HitResult.Type.MISS;
        Vec3 end = blockHit ? bh.getLocation() : to;

        // entities: sweep along the segment
        LivingEntity target = null;
        double segLen = from.distanceTo(end);
        int steps = Math.max(1, (int) Math.ceil(segLen / 0.8));
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i <= steps; i++) {
            Vec3 sp = from.lerp(end, i / (double) steps);
            AABB box = new AABB(sp, sp).inflate(1.2);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    x -> x != c && x.isAlive() && !x.isSpectator() && !x.isAlliedTo(c))) {
                double d = e.position().distanceToSqr(from);
                if (d < bestDist) {
                    bestDist = d;
                    target = e;
                }
            }
            if (target != null) break;
        }
        if (target != null) {
            end = target.getBoundingBox().getCenter();
        }

        // ---- draw the arrow and everything it leaves behind ------------------------------------
        Vec3 tail = end.subtract(g.dir.scale(ARROW_LEN));
        drawArrow(level, tail, end, g.dir, g.age);
        glow(level, end, 2.2);
        if (g.age % 3 == 0) ringFacing(level, end, 3.5);

        Vec3 trailFrom = g.head;
        double trailLen = trailFrom.distanceTo(end);
        int n = Math.max(1, (int) (trailLen / 0.6));
        for (int i = 0; i < n; i++) {
            Vec3 q = trailFrom.lerp(end, i / (double) n);
            Vec3 jitter = new Vec3((rng.nextDouble() - 0.5) * 0.9, (rng.nextDouble() - 0.5) * 0.9, (rng.nextDouble() - 0.5) * 0.9);
            FX.one(level, ModParticles.FUGA_FLAME.get(), q.add(jitter), g.dir.scale(-0.04).add(0, 0.03, 0));
            if (i % 2 == 0) {
                FX.one(level, ModParticles.FUGA_EMBER.get(), q.add(jitter), jitter.scale(0.15));
            }
            if (i % 5 == 0) {
                FX.one(level, ModParticles.FUGA_BLAST.get(), q, g.dir.scale(-0.03));
            }
        }
        if (g.age % 2 == 0) {
            FX.one(level, ModParticles.FUGA_SMOKE.get(), end.subtract(g.dir.scale(3.0)), new Vec3(0, 0.04, 0));
        }
        // speed streaks
        Basis b = Basis.of(g.dir);
        for (int i = 0; i < 4; i++) {
            Vec3 side = b.r().scale(rng.nextGaussian()).add(b.u().scale(rng.nextGaussian())).normalize();
            Vec3 s0 = end.subtract(g.dir.scale(1.0 + rng.nextDouble() * 2.5)).add(side.scale(0.5 + rng.nextDouble() * 0.9));
            line(level, s0, s0.subtract(g.dir.scale(2.5 + rng.nextDouble() * 4.0)));
        }
        if (g.age % 2 == 0) {
            level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.5f, 0.6f);
        }

        g.travelled += segLen;
        g.head = end;

        // ---- did it hit something? --------------------------------------------------------------------
        if (target != null) {
            startImpact(g, end, target);
        } else if (blockHit) {
            startImpact(g, end.subtract(g.dir.scale(0.6)), null);
        } else if (g.travelled >= MAX_RANGE) {
            startImpact(g, end, null);
        }
    }

    // =============================================================================================
    //  PHASE 3: IMPACT
    // =============================================================================================
    private static void startImpact(Fuga g, Vec3 center, @Nullable LivingEntity direct) {
        g.phase = Phase.IMPACT;
        g.center = center;
        g.direct = direct == null ? null : direct.getUUID();
        g.radius = g.spell.getRadius(g.spellLevel);
        g.craterRadius = Math.min(MAX_CRATER_RADIUS, Math.round(g.radius * 0.7f));
        g.impactAge = 0;
        g.scorchTick = (int) Math.ceil(g.craterRadius / SHELL_SPEED) + 1;
        g.floorY = floorY(g.level, center.x, center.y + 2.0, center.z, 24, center.y);
    }

    /** @return true when the whole effect is over */
    private static boolean impact(Fuga g, @Nullable LivingEntity c) {
        ServerLevel level = g.level;
        RandomSource rng = level.random;
        int i = g.impactAge++;
        Vec3 ctr = g.center;
        double R = g.radius;
        double shell = Math.min(R, (i + 1) * SHELL_SPEED);

        // ---- the first instant: flash, shock rings, thunder --------------------------------------------
        if (i == 0) {
            glow(level, ctr, R * 0.5);
            glow(level, ctr.add(0, R * 0.12, 0), R * 0.32);
            ringFlat(level, new Vec3(ctr.x, g.floorY + 0.1, ctr.z), R * 1.35);
            ringFacing(level, ctr, R * 1.1);
            level.playSound(null, ctr.x, ctr.y, ctr.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 12f, 0.5f);
            level.playSound(null, ctr.x, ctr.y, ctr.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 12f, 0.55f);
            level.playSound(null, ctr.x, ctr.y, ctr.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 12f, 0.5f);
        }
        if (i == 2) {
            level.playSound(null, ctr.x, ctr.y, ctr.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 12f, 0.45f);
        }
        if (i == 3) ringFacing(level, ctr, R * 0.8);
        if (i == 5) ringFlat(level, new Vec3(ctr.x, g.floorY + 0.1, ctr.z), R * 1.0);
        if (i == 6 || i == 12) {
            level.playSound(null, ctr.x, ctr.y, ctr.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8f, 0.35f);
        }

        // ---- damage: everything the blast front sweeps over --------------------------------------------
        float power = c != null ? g.spell.getSpellPower(g.spellLevel, c) : 20f;
        float baseDamage = power * FugaSpell.BLAST_FACTOR;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ctr, ctr).inflate(shell + 1.0))) {
            if (e == c || !e.isAlive()) continue;
            if (c != null && e.isAlliedTo(c)) continue;
            Vec3 ep = e.getBoundingBox().getCenter();
            double dist = ep.distanceTo(ctr);
            if (dist > shell) continue;
            if (!g.hit.add(e.getUUID())) continue;

            float falloff = (float) (1.0 - 0.6 * Math.min(1.0, dist / R));
            float dmg = baseDamage * falloff;
            boolean direct = e.getUUID().equals(g.direct);
            if (direct) {
                dmg += power * FugaSpell.DIRECT_FACTOR;
            }
            if (c != null) {
                e.invulnerableTime = 0;
                DamageSources.applyDamage(e, dmg, g.spell.getDamageSource(c));
                if (direct) SpellDamage.percent(e, e.getMaxHealth() * FugaSpell.DIRECT_PERCENT, c, level.damageSources().indirectMagic(c, c));
            }
            e.setSecondsOnFire(4 + g.spellLevel / 2); // burning is part of the damage, so it is kept short

            Vec3 push = ep.subtract(ctr);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : push.normalize();
            double kb = 2.0 * falloff + (direct ? 1.0 : 0.0);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x * kb, 0.55 * falloff + 0.25, push.z * kb));
            com.example.jjkspells.effect.OverloadEvents.forceMove(e);
            e.hurtMarked = true;
            if (e instanceof ServerPlayer && dist < R * 0.9) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false)); // the flash
            }
            FX.emit(level, ModParticles.FUGA_FLAME.get(), ep.x, ep.y, ep.z, 14, 0.4, 0.6, 0.4, 0.06);
        }

        // ---- terrain ---------------------------------------------------------------------------------------
        if (com.example.jjkspells.JJKConfig.breaks(com.example.jjkspells.JJKConfig.FUGA_BREAKS_BLOCKS)) breakShell(g, c, i);
        if (SCORCH_AND_FIRE && i == g.scorchTick && com.example.jjkspells.JJKConfig.SPELLS_BREAK_BLOCKS.get()) scorch(g); // scorching changes blocks too

        // ---- visuals -----------------------------------------------------------------------------------------
        // blast front: a shell of huge fireballs racing outwards (flatter underneath, so it hugs the ground)
        if (i * SHELL_SPEED < R + SHELL_SPEED * 2) {
            int pts = (int) (18 + shell * 1.6);
            for (int k = 0; k < pts; k++) {
                Vec3 d = randomDir(rng);
                if (d.y < 0) d = new Vec3(d.x, -d.y * 0.35, d.z).normalize();
                Vec3 pp = ctr.add(d.scale(shell * (0.85 + rng.nextDouble() * 0.15)));
                FX.one(level, ModParticles.FUGA_BLAST.get(), pp, d.scale(0.1 + rng.nextDouble() * 0.1));
                if (k % 3 == 0) {
                    FX.one(level, ModParticles.FUGA_FLAME.get(), pp, d.scale(0.2 + rng.nextDouble() * 0.2));
                }
            }
        }

        // the mushroom: a column of fire climbing, then a cap that rolls outward
        double top = R * 1.9;
        if (i >= 1 && i <= 50) {
            double h = Math.min(top, (i - 1) * 1.5);
            double rad = R * 0.12 + (h / top) * R * 0.10;
            for (int k = 0; k < 8; k++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(rng.nextDouble()) * rad;
                Vec3 pp = ctr.add(Math.cos(a) * rr, h, Math.sin(a) * rr);
                FX.one(level, ModParticles.FUGA_BLAST.get(), pp, new Vec3(Math.cos(a) * 0.03, 0.18, Math.sin(a) * 0.03));
            }
            for (int k = 0; k < 6; k++) {                    // white-hot core of the stem
                double a = rng.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(rng.nextDouble()) * rad * 0.5;
                Vec3 pp = ctr.add(Math.cos(a) * rr, h * rng.nextDouble(), Math.sin(a) * rr);
                FX.one(level, ModParticles.FUGA_FLAME.get(), pp, new Vec3(0, 0.3, 0));
            }
            if (h >= top - 0.1) {                            // cap
                for (int k = 0; k < 14; k++) {
                    double a = (Math.PI * 2 / 14.0) * k + rng.nextDouble() * 0.3;
                    Vec3 pp = ctr.add(0, top + rng.nextDouble() * 2.0, 0);
                    FX.one(level, ModParticles.FUGA_BLAST.get(), pp,
                            new Vec3(Math.cos(a) * (0.35 + rng.nextDouble() * 0.2), 0.02 - rng.nextDouble() * 0.05, Math.sin(a) * (0.35 + rng.nextDouble() * 0.2)));
                }
            }
            if (i >= 8) {                                    // soot climbing out of the top
                for (int k = 0; k < 3; k++) {
                    FX.one(level, ModParticles.FUGA_SMOKE.get(),
                            ctr.add((rng.nextDouble() - 0.5) * rad, h, (rng.nextDouble() - 0.5) * rad), new Vec3(0, 0.12, 0));
                }
            }
        }

        // embers thrown into the air
        if (i < 26) {
            for (int k = 0; k < 28; k++) {
                Vec3 d = randomDir(rng);
                Vec3 pp = ctr.add(d.scale(R * 0.3 * rng.nextDouble()));
                FX.one(level, ModParticles.FUGA_EMBER.get(), pp,
                        new Vec3(d.x * (0.5 + rng.nextDouble() * 1.3), Math.abs(d.y) * 1.0 + 0.3, d.z * (0.5 + rng.nextDouble() * 1.3)));
            }
        }

        // the aftermath: ground fire and smoke
        if (i >= 6 && i % 2 == 0) {
            for (int k = 0; k < 14; k++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(rng.nextDouble()) * R * 0.9;
                double px = ctr.x + Math.cos(a) * d, pz = ctr.z + Math.sin(a) * d;
                double py = floorY(level, px, ctr.y + 3, pz, 30, g.floorY);
                FX.one(level, ModParticles.FUGA_FLAME.get(), new Vec3(px, py + 0.2, pz), new Vec3(0, 0.05 + rng.nextDouble() * 0.12, 0));
            }
        }
        if (i >= 6 && i % 4 == 0 && i < LINGER_TICKS - 20) {
            for (int k = 0; k < 4; k++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(rng.nextDouble()) * R * 0.8;
                FX.one(level, ModParticles.FUGA_SMOKE.get(), new Vec3(ctr.x + Math.cos(a) * d, g.floorY + 0.5, ctr.z + Math.sin(a) * d), new Vec3(0, 0.07, 0));
            }
        }
        if (i >= 10 && i % 12 == 0 && i < LINGER_TICKS - 10) {
            level.playSound(null, ctr.x, ctr.y, ctr.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 4f, 0.7f);
        }

        return i >= LINGER_TICKS;
    }

    /** Destroys the terrain in the part of the crater the blast front crosses this tick. */
    private static void breakShell(Fuga g, @Nullable LivingEntity c, int i) {
        ServerLevel level = g.level;
        RandomSource rng = level.random;
        double rc = g.craterRadius;
        double cur = Math.min(rc, (i + 1) * SHELL_SPEED);
        double prev = Math.min(rc, i * SHELL_SPEED);
        if (cur <= prev) return;

        BlockPos origin = BlockPos.containing(g.center);
        int ext = (int) Math.ceil(cur);
        double cur2 = cur * cur;
        double prev2 = prev * prev;
        ServerPlayer player = c instanceof ServerPlayer sp ? sp : null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int debris = 0;

        for (int x = -ext; x <= ext; x++) {
            for (int y = -ext; y <= ext; y++) {
                for (int z = -ext; z <= ext; z++) {
                    double d2 = x * x + y * y + z * z;
                    if (d2 > cur2 || (i > 0 && d2 <= prev2)) continue;

                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    // ragged edge: each block gets its own reach between 84% and 100% of the radius
                    int h = (pos.getX() * 73856093) ^ (pos.getY() * 19349663) ^ (pos.getZ() * 83492791);
                    double jag = 0.84 + 0.16 * ((h & 1023) / 1023.0);
                    if (Math.sqrt(d2) > rc * jag) continue;

                    if (state.getBlock().getExplosionResistance() >= 1000f || state.getDestroySpeed(level, pos) < 0f) continue;
                    if (player != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, player))) continue;

                    if (debris < 45 && rng.nextInt(9) == 0 && state.getFluidState().isEmpty()) {
                        debris++;
                        Vec3 out = new Vec3(x, y + 0.5, z).normalize();
                        FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, state),
                                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                                out.scale(0.5 + rng.nextDouble() * 0.9).add(0, 0.35, 0));
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /** Blackened ground and scattered fires over the whole blast area. */
    private static void scorch(Fuga g) {
        ServerLevel level = g.level;
        RandomSource rng = level.random;
        double R = g.radius;
        int tries = (int) (R * R * 1.2);
        for (int n = 0; n < tries; n++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * R * 0.95;
            BlockPos gp = topSolid(level, g.center.x + Math.cos(a) * d, g.center.y + 4, g.center.z + Math.sin(a) * d, 40);
            if (gp == null) continue;

            BlockState s = level.getBlockState(gp);
            if (d / R < 0.8 && (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT)
                    || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA))) {
                float roll = rng.nextFloat();
                if (roll < 0.55f) {
                    level.setBlock(gp, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (roll < 0.62f) {
                    level.setBlock(gp, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            BlockPos above = gp.above();
            if (rng.nextFloat() < 0.4f && level.getBlockState(above).isAir()) {
                level.setBlock(above, BaseFireBlock.getState(level, above), Block.UPDATE_ALL);
            }
        }
    }

    // =============================================================================================
    //  Shared drawing helpers
    // =============================================================================================

    /** The arrow: a thick white-hot shaft, a spear-like head, three flame feathers and a spiral of fire. */
    private static void drawArrow(ServerLevel level, Vec3 tail, Vec3 head, Vec3 dir, int age) {
        RandomSource rng = level.random;
        Basis b = Basis.of(dir);
        Vec3 f = b.f(), r = b.r(), u = b.u();
        double len = tail.distanceTo(head);

        beam(level, tail, head);
        Vec3 o1 = r.scale(0.14), o2 = u.scale(0.14);
        line(level, tail.add(o1), head.add(o1));
        line(level, tail.subtract(o1), head.subtract(o1));
        line(level, tail.add(o2), head.add(o2));
        line(level, tail.subtract(o2), head.subtract(o2));

        // spear-like head
        Vec3 neck = head.subtract(f.scale(Math.min(1.1, len * 0.4)));
        line(level, neck.add(r.scale(0.3)), head);
        line(level, neck.subtract(r.scale(0.3)), head);
        line(level, neck.add(u.scale(0.3)), head);
        line(level, neck.subtract(u.scale(0.3)), head);

        // fletching: three feathers of flame at the tail
        for (int k = 0; k < 3; k++) {
            double a = age * 0.12 + k * (Math.PI * 2 / 3.0);
            Vec3 out = r.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
            Vec3 root = tail.add(f.scale(Math.min(1.0, len * 0.3)));
            line(level, root, tail.add(out.scale(0.6)).subtract(f.scale(0.15)));
        }

        // spiral of flame around the shaft
        for (int k = 0; k < 4; k++) {
            double s = rng.nextDouble() * len;
            double a = s * 2.4 - age * 0.5;
            Vec3 pp = tail.add(f.scale(s)).add(r.scale(Math.cos(a) * 0.34)).add(u.scale(Math.sin(a) * 0.34));
            FX.one(level, ModParticles.FUGA_FLAME.get(), pp, f.scale(0.05).add((rng.nextDouble() - 0.5) * 0.02, 0.02, (rng.nextDouble() - 0.5) * 0.02));
        }
        glow(level, head, 0.9);
    }

    private static void line(ServerLevel l, Vec3 a, Vec3 b) {
        FX.one(l, ModParticles.FUGA_LINE.get(), a, b.subtract(a));
    }

    private static void beam(ServerLevel l, Vec3 a, Vec3 b) {
        FX.one(l, ModParticles.FUGA_BEAM.get(), a, b.subtract(a));
    }

    private static void glow(ServerLevel l, Vec3 p, double size) {
        FX.one(l, ModParticles.FUGA_GLOW.get(), p, new Vec3(size, 0, 0));
    }

    private static void ringFlat(ServerLevel l, Vec3 p, double radius) {
        FX.one(l, ModParticles.FUGA_RING_FLAT.get(), p, new Vec3(radius, 0, 0));
    }

    private static void ringFacing(ServerLevel l, Vec3 p, double radius) {
        FX.one(l, ModParticles.FUGA_RING.get(), p, new Vec3(radius, 0, 0));
    }

    private static Vec3 randomDir(RandomSource rng) {
        double z = rng.nextDouble() * 2 - 1;
        double a = rng.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - z * z);
        return new Vec3(s * Math.cos(a), z, s * Math.sin(a));
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }

    private static void announce(ServerLevel level, LivingEntity caster, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(caster) < 96 * 96) {
                sp.displayClientMessage(text, true);
            }
        }
    }

    /** Top of the first solid block at or below startY, or null if there isn't one within maxDown blocks. */
    @Nullable
    private static BlockPos topSolid(ServerLevel level, double x, double startY, double z, int maxDown) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p)) return null;
        for (int i = 0; i < maxDown; i++) {
            if (level.isOutsideBuildHeight(p)) return null;
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty()) return p.immutable();
            p.move(Direction.DOWN);
        }
        return null;
    }

    private static double floorY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
        BlockPos p = topSolid(level, x, startY, z, maxDown);
        return p == null ? fallback : p.getY() + 1.0;
    }
}
