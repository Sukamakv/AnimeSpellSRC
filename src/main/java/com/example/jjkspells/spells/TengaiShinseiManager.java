package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEntities;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.entity.MeteorEntity;
import com.example.jjkspells.entity.TengaiFxEntity;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every active Tengai Shinsei on the server.
 *
 *   FALL1  (200 ticks = 10 s)  meteor 1 plunges from far above the aimed spot while a seal pulses on the ground.
 *                              When it touches down: a shockwave deals 95 damage to everything it crosses (not the
 *                              caster) and the ground is cratered. Meteor 1 stays where it landed.
 *   GAP    (60 ticks = 3 s)    the crater settles. The first meteor sits there, still glowing.
 *   FALL2  (100 ticks = 5 s)   meteor 2 falls from the sky straight onto the top of meteor 1.
 *   BLAST                      the meteors collide in a nuclear-style explosion: everything inside loses 40% of its
 *                              max health, a huge crater is torn out, and a mushroom cloud rises.
 *
 * The meteors and the big effects are entities drawn by their renderers; this class only moves them, times the
 * phases, and deals the damage and terrain changes.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class TengaiShinseiManager {

    // ---- Tuning -------------------------------------------------------------------------------
    private static final double SHOCK1_SPEED = 3.0;        // blocks per tick the first shockwave travels
    private static final double CRATER1_SPEED = 2.5;       // blocks per tick the first crater is dug
    private static final double NUKE_SPEED = 4.5;          // blocks per tick the nuclear blast front travels
    private static final double CRATER2_SPEED = 3.0;       // blocks per tick the final crater is dug
    private static final int IMPACT_FX_LIFE = 80;
    private static final int NUKE_FX_LIFE = 440;           // the mushroom cloud lasts 22 seconds
    private static final double RESTING_SINK = 0.6;        // meteor 1's centre rests this many radii above the ground
    private static final double STACK_GAP = 1.8;           // distance between the two centres, in radii (they overlap a little)
    private static final int MAX_AGE = 20 * 60;

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final DustParticleOptions SEAL_DUST = new DustParticleOptions(new Vector3f(0.95f, 0.35f, 0.08f), 2.2f);

    // ---- State --------------------------------------------------------------------------------
    private enum Phase { FALL1, GAP, FALL2, BLAST }

    private static class Tengai {
        final ServerLevel level;
        final UUID casterId;
        final TengaiShinseiSpell spell;
        final double x, z, groundY;
        final double[] rest = new double[2];   // where each meteor's centre comes to rest
        final double[] sky = new double[2];    // where each meteor's centre starts its fall
        final double contactY;                 // world height of the blast point (where the two meteors touch)
        final int craterRadius1;
        final int craterRadius2;
        final MeteorEntity[] meteor = new MeteorEntity[2];
        Phase phase = Phase.FALL1;
        int phaseAge = 0;
        int age = 0;
        int nextRespawn = 0;
        int impact1 = -1;   // ticks since the first landing, -1 = not yet, -2 = finished
        int impact2 = -1;   // ticks since the explosion, -1 = not yet, -2 = finished
        final Set<UUID> hit1 = new HashSet<>();
        final Set<UUID> hit2 = new HashSet<>();

        Tengai(ServerLevel level, LivingEntity caster, TengaiShinseiSpell spell, double x, double groundY, double z) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.x = x;
            this.z = z;
            this.groundY = groundY;
            double r = TengaiShinseiSpell.METEOR_RADIUS;
            rest[0] = groundY + r * RESTING_SINK;
            rest[1] = rest[0] + r * STACK_GAP;
            contactY = rest[0] + r * STACK_GAP * 0.5;
            // dimensions with a ceiling cannot hold a sky 240 blocks high: start just under the roof instead
            double ceiling = level.dimensionType().hasCeiling() ? level.getLogicalHeight() - r - 2 : Double.MAX_VALUE;
            sky[0] = Math.min(rest[0] + TengaiShinseiSpell.SKY_HEIGHT, Math.max(ceiling, rest[0] + 30));
            sky[1] = Math.min(rest[1] + TengaiShinseiSpell.SKY_HEIGHT, Math.max(ceiling, rest[1] + 30));
            craterRadius1 = TengaiShinseiSpell.meteorCraterRadius();
            craterRadius2 = TengaiShinseiSpell.nukeCraterRadius();
        }
    }

    private static final List<Tengai> ACTIVE = new ArrayList<>();

    // =============================================================================================
    //  Start
    // =============================================================================================
    public static void start(ServerLevel level, LivingEntity caster, TengaiShinseiSpell spell) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(TengaiShinseiSpell.RANGE));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 hit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        boolean onTop = bh.getType() == HitResult.Type.BLOCK && bh.getDirection() == Direction.UP;

        AABB box = caster.getBoundingBox().expandTowards(look.scale(TengaiShinseiSpell.RANGE)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, caster, eye, hit, box, e -> e.isAlive() && !e.isSpectator() && e.isPickable());
        if (eh != null) {
            hit = eh.getLocation();
            onTop = false;
        }

        double gy = onTop ? hit.y : groundY(level, hit.x, hit.y + 0.5, hit.z, 96, Math.min(hit.y, caster.getY()));
        Tengai g = new Tengai(level, caster, spell, hit.x, gy, hit.z);
        ACTIVE.add(g);

        announce(level, caster, Component.literal("Tengai Shinsei").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sound(level, new Vec3(g.x, g.groundY, g.z), SoundEvents.ENDER_DRAGON_GROWL, 6f, 0.5f);
        sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 4f, 0.5f);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Tengai> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Tengai g = it.next();
            Entity e = g.level.getEntity(g.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;

            boolean done = false;
            try {
                done = tick(g, caster);
            } catch (Exception ex) {
                LOGGER.error("Tengai Shinsei failed, cancelling it", ex);
                done = true;
            }
            g.age++;
            if (done || g.age > MAX_AGE) {
                for (MeteorEntity m : g.meteor) {
                    if (m != null && !m.isRemoved()) m.discard();
                }
                it.remove();
            }
        }
    }

    /** @return true when the whole technique is over */
    private static boolean tick(Tengai g, @Nullable LivingEntity c) {
        switch (g.phase) {
            case FALL1 -> {
                fall(g, 0, TengaiShinseiSpell.FALL1_TICKS);
                if (g.phaseAge + 1 >= TengaiShinseiSpell.FALL1_TICKS) {
                    land1(g);
                    g.phase = Phase.GAP;
                    g.phaseAge = -1;
                }
            }
            case GAP -> {
                gap(g);
                if (g.phaseAge + 1 >= TengaiShinseiSpell.GAP_TICKS) {
                    g.phase = Phase.FALL2;
                    g.phaseAge = -1;
                    sound(g.level, new Vec3(g.x, g.groundY, g.z), SoundEvents.ENDER_DRAGON_GROWL, 8f, 0.4f);
                    announceAt(g, Component.literal("Tengai Shinsei").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
            case FALL2 -> {
                fall(g, 1, TengaiShinseiSpell.FALL2_TICKS);
                if (g.phaseAge + 1 >= TengaiShinseiSpell.FALL2_TICKS) {
                    land2(g);
                    g.phase = Phase.BLAST;
                    g.phaseAge = -1;
                }
            }
            case BLAST -> {
                // nothing to do here: the explosion is driven by impact2 below
            }
        }
        g.phaseAge++;

        if (g.impact1 >= 0) impact1(g, c);
        if (g.impact2 >= 0) impact2(g, c);

        // over once the explosion has finished (the mushroom cloud removes itself)
        return g.phase == Phase.BLAST && g.impact2 == -2;
    }

    // =============================================================================================
    //  Falling
    // =============================================================================================
    private static MeteorEntity meteor(Tengai g, int i, double y, boolean landed) {
        MeteorEntity m = g.meteor[i];
        if (m == null || m.isRemoved()) {
            if (m != null && g.age < g.nextRespawn) return null; // it was removed (chunk unloaded?): do not retry every tick
            m = new MeteorEntity(g.level, i, TengaiShinseiSpell.METEOR_RADIUS, (float) g.sky[i], (float) g.rest[i]);
            if (landed) m.setLanded();
            m.setPos(g.x, y, g.z);
            g.level.addFreshEntity(m);
            g.meteor[i] = m;
            g.nextRespawn = g.age + 10;
        }
        return m;
    }

    private static void fall(Tengai g, int i, int total) {
        ServerLevel level = g.level;
        int t = Math.max(0, g.phaseAge);
        double f = Mth.clamp(t / (double) total, 0.0, 1.0);
        double progress = 0.3 * f + 0.7 * f * f;                    // starts slowly, ends very fast
        double y = g.rest[i] + (g.sky[i] - g.rest[i]) * (1.0 - progress);
        MeteorEntity m = meteor(g, i, y, false);
        if (m != null) m.setPos(g.x, y, g.z);

        Vec3 ground = new Vec3(g.x, g.groundY, g.z);
        // distant rolling thunder that gets louder, then a heartbeat in the last seconds
        if (t % 25 == 0) {
            sound(level, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, (float) (6.0 + 14.0 * f), 0.4f + 0.2f * (float) f);
        }
        if (t > total - 70 && t % 14 == 0) {
            sound(level, ground, SoundEvents.WARDEN_HEARTBEAT, 10f, 0.6f);
        }
        if (t % 5 == 0) {
            seal(g, t, f);
        }
        // the ground shudders as it nears
        if (f > 0.7 && t % 2 == 0) {
            RandomSource rng = level.random;
            double r = TengaiShinseiSpell.METEOR_RADIUS * 1.3 * rng.nextDouble();
            double a = rng.nextDouble() * Math.PI * 2;
            BlockPos below = BlockPos.containing(g.x + Math.cos(a) * r, g.groundY - 0.5, g.z + Math.sin(a) * r);
            BlockState st = level.getBlockState(below);
            if (!st.isAir() && level.hasChunkAt(below)) {
                FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, st),
                        new Vec3(below.getX() + 0.5, below.getY() + 1.2, below.getZ() + 0.5),
                        new Vec3((rng.nextDouble() - 0.5) * 0.3, 0.25 + rng.nextDouble() * 0.3, (rng.nextDouble() - 0.5) * 0.3));
            }
        }
    }

    /** A glowing circle on the ground showing where it will land; it spins faster as the meteor nears. */
    private static void seal(Tengai g, int t, double f) {
        ServerLevel level = g.level;
        double spin = t * (0.02 + 0.05 * f);
        double outer = TengaiShinseiSpell.METEOR_RADIUS + 2.0;
        int n = 44;
        for (int k = 0; k < n; k++) {
            double a = spin + k * Math.PI * 2 / n;
            sealPoint(g, g.x + Math.cos(a) * outer, g.z + Math.sin(a) * outer);
            if (k % 2 == 0) {
                double a2 = -spin * 1.3 + k * Math.PI * 2 / n;
                sealPoint(g, g.x + Math.cos(a2) * outer * 0.7, g.z + Math.sin(a2) * outer * 0.7);
            }
        }
        // six spokes
        for (int s = 0; s < 6; s++) {
            double a = spin * 0.5 + s * Math.PI / 3.0;
            for (int k = 1; k < 8; k++) {
                double d = outer * k / 8.0;
                sealPoint(g, g.x + Math.cos(a) * d, g.z + Math.sin(a) * d);
            }
        }
    }

    private static void sealPoint(Tengai g, double px, double pz) {
        double y = groundY(g.level, px, g.groundY + 6, pz, 14, g.groundY);
        FX.one(g.level, SEAL_DUST, new Vec3(px, y + 0.15, pz), Vec3.ZERO);
    }

    // =============================================================================================
    //  Meteor 1 lands
    // =============================================================================================
    private static void land1(Tengai g) {
        ServerLevel level = g.level;
        MeteorEntity m = meteor(g, 0, g.rest[0], true);
        if (m != null) {
            m.setPos(g.x, g.rest[0], g.z);
            m.setLanded();
        }
        g.impact1 = 0;

        TengaiFxEntity fx = new TengaiFxEntity(level, TengaiFxEntity.KIND_IMPACT, IMPACT_FX_LIFE, 1f, 0f);
        fx.setPos(g.x, g.groundY, g.z);
        level.addFreshEntity(fx);

        Vec3 p = new Vec3(g.x, g.groundY, g.z);
        sound(level, p, SoundEvents.GENERIC_EXPLODE, 18f, 0.45f);
        sound(level, p, SoundEvents.DRAGON_FIREBALL_EXPLODE, 16f, 0.5f);
        sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 16f, 0.5f);
        debris(g, TengaiShinseiSpell.METEOR_RADIUS * 1.1, 70, 1.4);
    }

    private static void impact1(Tengai g, @Nullable LivingEntity c) {
        ServerLevel level = g.level;
        int i = g.impact1++;
        double front = Math.min(TengaiShinseiSpell.SHOCK_RADIUS, (i + 1) * SHOCK1_SPEED);
        Vec3 ctr = new Vec3(g.x, g.groundY + 3.0, g.z);

        if (i == 3) sound(level, ctr, SoundEvents.LIGHTNING_BOLT_THUNDER, 16f, 0.4f);

        float dmg = TengaiShinseiSpell.meteorDamage();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ctr, ctr).inflate(front + 1.0), LivingEntity::isAlive)) {
            if (skip(e, c)) continue;
            Vec3 ep = e.getBoundingBox().getCenter();
            double dist = ep.distanceTo(ctr);
            if (dist > front) continue;
            if (!g.hit1.add(e.getUUID())) continue;

            e.invulnerableTime = 0;
            if (c != null) {
                DamageSources.applyDamage(e, dmg, g.spell.getDamageSource(c));
            } else {
                e.hurt(level.damageSources().magic(), dmg);
            }
            double falloff = 1.0 - 0.5 * Math.min(1.0, dist / TengaiShinseiSpell.SHOCK_RADIUS);
            knock(e, ep, ctr, 2.6 * falloff, 0.8 * falloff + 0.25);
        }

        if (TengaiShinseiSpell.breaksBlocks()) {
            carveShell(level, new Vec3(g.x, g.groundY + 2.0, g.z), g.craterRadius1, i, CRATER1_SPEED, c);
        }

        double endAt = Math.max(TengaiShinseiSpell.SHOCK_RADIUS / SHOCK1_SPEED, g.craterRadius1 / CRATER1_SPEED) + 2;
        if (i >= endAt) g.impact1 = -2;
    }

    private static void gap(Tengai g) {
        // the landed meteor still rumbles and sheds dust while the crater settles
        ServerLevel level = g.level;
        int t = g.phaseAge;
        if (t % 15 == 0) {
            sound(level, new Vec3(g.x, g.groundY, g.z), SoundEvents.GRAVEL_BREAK, 6f, 0.4f);
        }
        if (t % 3 == 0) {
            debris(g, TengaiShinseiSpell.METEOR_RADIUS * 1.2, 4, 0.35);
        }
    }

    // =============================================================================================
    //  Meteor 2 lands: the explosion
    // =============================================================================================
    private static void land2(Tengai g) {
        ServerLevel level = g.level;
        MeteorEntity m = meteor(g, 1, g.rest[1], true);
        if (m != null) {
            m.setPos(g.x, g.rest[1], g.z);
            m.setLanded();
        }
        g.impact2 = 0;

        TengaiFxEntity fx = new TengaiFxEntity(level, TengaiFxEntity.KIND_NUKE, NUKE_FX_LIFE, 1f, (float) (g.contactY - g.groundY));
        fx.setPos(g.x, g.groundY, g.z);
        level.addFreshEntity(fx);

        Vec3 p = new Vec3(g.x, g.contactY, g.z);
        sound(level, p, SoundEvents.GENERIC_EXPLODE, 40f, 0.3f);
        sound(level, p, SoundEvents.DRAGON_FIREBALL_EXPLODE, 40f, 0.35f);
        sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 40f, 0.4f);
        sound(level, p, SoundEvents.WITHER_SPAWN, 24f, 0.5f);
        debris(g, TengaiShinseiSpell.METEOR_RADIUS * 1.4, 160, 2.0);
    }

    private static void impact2(Tengai g, @Nullable LivingEntity c) {
        ServerLevel level = g.level;
        int i = g.impact2++;
        double R = TengaiShinseiSpell.NUKE_RADIUS;
        double front = Math.min(R, (i + 1) * NUKE_SPEED);
        Vec3 ctr = new Vec3(g.x, g.groundY + 10.0, g.z);
        Vec3 blast = new Vec3(g.x, g.contactY, g.z);

        if (i == 2) {
            // the meteors are gone in the flash
            for (MeteorEntity m : g.meteor) {
                if (m != null && !m.isRemoved()) m.discard();
            }
        }
        if (i == 6) sound(level, blast, SoundEvents.LIGHTNING_BOLT_THUNDER, 40f, 0.35f);
        if (i == 16) sound(level, blast, SoundEvents.GENERIC_EXPLODE, 30f, 0.25f);
        if (i == 30) sound(level, blast, SoundEvents.LIGHTNING_BOLT_THUNDER, 40f, 0.3f);

        float pct = TengaiShinseiSpell.nukePercent();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ctr, ctr).inflate(front + 1.0), LivingEntity::isAlive)) {
            if (skip(e, c)) continue;
            Vec3 ep = e.getBoundingBox().getCenter();
            double dist = ep.distanceTo(ctr);
            if (dist > front) continue;
            if (!g.hit2.add(e.getUUID())) continue;

            float amount = e.getMaxHealth() * pct;
            e.invulnerableTime = 0;
            DamageSource src = c != null ? g.spell.getDamageSource(c) : level.damageSources().magic();
            if (c != null) SpellDamage.percent(e, amount, c, src); else e.hurt(src, amount);

            double falloff = 1.0 - 0.6 * Math.min(1.0, dist / R);
            knock(e, ep, ctr, 3.6 * falloff, 1.0 * falloff + 0.3);
            if (e instanceof ServerPlayer && dist < 80) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false, false)); // the flash
            }
        }

        if (TengaiShinseiSpell.breaksBlocks()) {
            carveShell(level, new Vec3(g.x, g.groundY + 4.0, g.z), g.craterRadius2, i, CRATER2_SPEED, c);
        }

        double endAt = Math.max(R / NUKE_SPEED, g.craterRadius2 / CRATER2_SPEED) + 4;
        if (i >= endAt) g.impact2 = -2;
    }

    // =============================================================================================
    //  Shared helpers
    // =============================================================================================

    private static boolean skip(LivingEntity e, @Nullable LivingEntity caster) {
        if (e.isSpectator()) return true;
        if (caster == null) return false;
        return e == caster || e.isAlliedTo(caster) || DamageSources.isFriendlyFireBetween(caster, e);
    }

    private static void knock(LivingEntity e, Vec3 ep, Vec3 ctr, double strength, double lift) {
        Vec3 push = new Vec3(ep.x - ctr.x, 0, ep.z - ctr.z);
        if (push.lengthSqr() < 1.0E-4) {
            double a = e.level().random.nextDouble() * Math.PI * 2;
            push = new Vec3(Math.cos(a), 0, Math.sin(a));
        } else {
            push = push.normalize();
        }
        e.setDeltaMovement(e.getDeltaMovement().add(push.x * strength, lift, push.z * strength));
        OverloadEvents.forceMove(e);
        e.hurtMarked = true;
    }

    /**
     * Digs out the part of the crater the blast front crosses this tick. Everything above the centre inside the radius
     * goes; below the centre the crater is only half as deep as it is wide. Edges are ragged.
     */
    private static void carveShell(ServerLevel level, Vec3 center, int RC, int i, double speed, @Nullable LivingEntity c) {
        double cur = Math.min(RC, (i + 1) * speed);
        double prev = Math.min(RC, i * speed);
        if (cur <= prev) return;

        RandomSource rng = level.random;
        BlockPos origin = BlockPos.containing(center);
        int ext = (int) Math.ceil(cur);
        double cur2 = cur * cur, prev2 = prev * prev;
        ServerPlayer player = c instanceof ServerPlayer sp ? sp : null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int debris = 0;

        for (int x = -ext; x <= ext; x++) {
            for (int z = -ext; z <= ext; z++) {
                double hd2 = (double) x * x + (double) z * z;
                if (hd2 > cur2) continue;
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                if (!level.hasChunkAt(wx, wz)) continue;

                int dyMax = (int) Math.floor(Math.sqrt(cur2 - hd2));
                double loSq = prev2 - hd2;
                int dyMin = (i > 0 && loSq > 0) ? (int) Math.ceil(Math.sqrt(loSq)) : 0;

                // above (and level with) the centre
                for (int dy = dyMin; dy <= dyMax; dy++) {
                    debris += carveOne(level, pos.set(wx, origin.getY() + dy, wz), hd2, dy, RC, player, rng, debris, x, dy, z);
                }
                // below the centre: a block k deep counts as 2k away, so the crater is flatter
                for (int k = Math.max(1, (dyMin + 1) / 2); k <= dyMax / 2; k++) {
                    debris += carveOne(level, pos.set(wx, origin.getY() - k, wz), hd2, 2 * k, RC, player, rng, debris, x, -k, z);
                }
            }
        }
    }

    /** @return 1 if a debris particle was thrown, else 0 */
    private static int carveOne(ServerLevel level, BlockPos.MutableBlockPos pos, double hd2, int effDy, int RC,
                                @Nullable ServerPlayer player, RandomSource rng, int debrisSoFar, int x, int y, int z) {
        if (level.isOutsideBuildHeight(pos)) return 0;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return 0;
        if (state.getDestroySpeed(level, pos) < 0f) return 0;

        double d = Math.sqrt(hd2 + (double) effDy * effDy);
        int h = (pos.getX() * 73856093) ^ (pos.getY() * 19349663) ^ (pos.getZ() * 83492791);
        double jag = 0.90 + 0.10 * ((h & 1023) / 1023.0);
        if (d > RC * jag) return 0;
        if (player != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, player))) return 0;

        int thrown = 0;
        if (debrisSoFar < 50 && rng.nextInt(140) == 0 && state.getFluidState().isEmpty()) {
            Vec3 out = new Vec3(x, y * 0.5 + 1, z);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
            FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, state),
                    new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                    out.scale(0.7 + rng.nextDouble() * 1.2).add(0, 0.4, 0));
            thrown = 1;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return thrown;
    }

    /** Throws chunks of whatever the ground is made of up and outwards from around the landing spot. */
    private static void debris(Tengai g, double radius, int count, double speed) {
        ServerLevel level = g.level;
        RandomSource rng = level.random;
        for (int n = 0; n < count; n++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * radius;
            BlockPos below = BlockPos.containing(g.x + Math.cos(a) * d, g.groundY - 0.5, g.z + Math.sin(a) * d);
            if (!level.hasChunkAt(below)) continue;
            BlockState st = level.getBlockState(below);
            if (st.isAir()) st = Blocks.STONE.defaultBlockState();
            FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, st),
                    new Vec3(below.getX() + 0.5, g.groundY + 0.6, below.getZ() + 0.5),
                    new Vec3(Math.cos(a) * speed * (0.4 + rng.nextDouble()), speed * (0.5 + rng.nextDouble()), Math.sin(a) * speed * (0.4 + rng.nextDouble())));
        }
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent ev, float volume, float pitch) {
        level.playSound(null, p.x, p.y, p.z, ev, SoundSource.PLAYERS, volume, pitch);
    }

    private static void announce(ServerLevel level, LivingEntity caster, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(caster) < 128 * 128) {
                sp.displayClientMessage(text, true);
            }
        }
    }

    private static void announceAt(Tengai g, Component text) {
        for (ServerPlayer sp : g.level.players()) {
            if (sp.distanceToSqr(g.x, g.groundY, g.z) < 160 * 160) {
                sp.displayClientMessage(text, true);
            }
        }
    }

    private static double groundY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        if (!level.hasChunkAt(p)) return fallback;
        for (int i = 0; i < maxDown; i++) {
            if (level.isOutsideBuildHeight(p)) return fallback;
            var shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty()) return p.getY() + shape.max(Direction.Axis.Y);
            p.move(Direction.DOWN);
        }
        return fallback;
    }
}
