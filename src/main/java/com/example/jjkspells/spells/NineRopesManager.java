package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
 * Hollow Purple: Nine Ropes.
 *
 *   CHARGE - the chant. The caster floats up, helpless. A Blue and a Red circle him faster and faster;
 *            on the last line they spiral off behind him and merge into a colossal Purple, pulling the
 *            surroundings toward it.
 *   IMPACT - the Purple detonates. A sphere of destruction sweeps outward (default radius 48 = 3x Fuga),
 *            wrecking terrain and everything standing in it. The caster is spared but pays with Backlash.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class NineRopesManager {

    // ---- Tuning -------------------------------------------------------------------------------
    private static final int MERGE_TICK = 108;
    private static final int CONVERGE_TICK = 84;
    private static final int CHARGE_TICKS = 132;
    private static final float ORB_RADIUS = 7f;           // the Purple before it detonates
    private static final double ORBIT_RADIUS = 3.6;       // distance of Red and Blue from the caster
    private static final float SPHERE_RADIUS = 1.1f;      // size of Red and Blue
    private static final double SHELL_SPEED = 1.0;        // blocks per tick the blast front travels
    private static final int LINGER_TICKS = 30;
    private static final double FLOAT_HEIGHT = 3.2;

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private enum Phase { CHARGE, IMPACT }

    private static class Rope {
        final ServerLevel level;
        final UUID casterId;
        final NineRopesSpell spell;
        final int spellLevel;
        Phase phase = Phase.CHARGE;
        int age = 0;
        int impactAge = 0;
        double startY;
        Vec3 back = new Vec3(0, 0, -1);
        Vec3 center = Vec3.ZERO;
        final Set<UUID> hit = new HashSet<>();

        Rope(ServerLevel level, LivingEntity caster, NineRopesSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.startY = caster.getY();
        }
    }

    private static final List<Rope> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, NineRopesSpell spell, int spellLevel) {
        ACTIVE.add(new Rope(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Rope> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Rope r = it.next();
            Entity e = r.level.getEntity(r.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;

            if (caster == null && r.phase == Phase.CHARGE) {
                it.remove();
                continue;
            }
            boolean done = false;
            try {
                if (r.phase == Phase.CHARGE) charge(r, caster);
                else done = impact(r, caster);
            } catch (Exception ex) {
                LOGGER.error("Nine Ropes failed, cancelling it", ex);
                done = true;
            }
            r.age++;
            if (done || r.age > 1200) it.remove();
        }
    }

    // =============================================================================================
    //  CHARGE
    // =============================================================================================
    private static void charge(Rope r, LivingEntity c) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        int t = r.age;
        double k = Math.min(1.0, t / (double) MERGE_TICK);

        // ---- floating, helpless -----------------------------------------------------------------
        c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, false));
        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        double rise = c.getY() < r.startY + FLOAT_HEIGHT ? 0.045 : 0.01;
        c.setDeltaMovement(0, rise, 0);
        c.hurtMarked = true;
        c.fallDistance = 0;

        Vec3 feet = c.position();
        Vec3 K0 = feet.add(0, c.getBbHeight() * 0.55, 0);        // the caster's chest
        if (t == CONVERGE_TICK) {
            Vec3 look = c.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
            r.back = flat.normalize().scale(-1);
        }
        Vec3 M = K0.add(r.back.scale(5.5)).add(0, 1.5, 0);       // where the Purple forms: behind him

        // ---- the chant --------------------------------------------------------------------------
        if (t == 0) {
            sound(level, feet, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3f, 0.5f);
            announce(level, c, Component.literal("Nine Ropes.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
        }
        if (t == 28) announce(level, c, Component.literal("Polarized Light.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
        if (t == 56) announce(level, c, Component.literal("Crow and Declaration.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
        if (t == CONVERGE_TICK) announce(level, c, Component.literal("Between Front and Back.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));

        // ---- aura: motes rising around the floating caster ----------------------------------------------
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            Vec3 p = feet.add(Math.cos(a) * 1.3, 0.1 + rng.nextDouble() * 0.4, Math.sin(a) * 1.3);
            one(level, ModParticles.HP_WISP.get(), p, new Vec3(0, 0.1 + rng.nextDouble() * 0.08, 0));
        }
        if (t % 12 == 0) ring(level, ModParticles.HP_RING_FLAT.get(), new Vec3(feet.x, r.startY + 0.06, feet.z), 3f + (float) k * 4f);
        if (t % 6 == 0) sound(level, K0, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, (float) (0.5 + k * 0.8));

        if (t < MERGE_TICK) {
            // ---- Red and Blue circle him, faster and faster -----------------------------------------------
            double s = Mth.clamp((t - CONVERGE_TICK) / (double) (MERGE_TICK - CONVERGE_TICK), 0.0, 1.0);
            double e = smooth(s);
            Vec3 centre = K0.lerp(M, e);
            double rho = ORBIT_RADIUS * (1 - e) + 0.15;
            double theta = 0.12 * t + 0.0012 * t * t;
            double phi = 0.02 * t;
            double tau = 0.55;
            Vec3 e1 = new Vec3(Math.cos(phi), 0, Math.sin(phi));
            Vec3 e2 = new Vec3(-Math.sin(phi) * Math.sin(tau), Math.cos(tau), Math.cos(phi) * Math.sin(tau));

            double appearBlue = Math.min(1.0, t / 14.0);
            double appearRed = Math.min(1.0, Math.max(0, t - 28) / 14.0);
            if (appearBlue > 0) coloredOrb(level, centre, rho, theta, e1, e2, (float) (SPHERE_RADIUS * appearBlue),
                    ModParticles.HP_GLOW_BLUE.get(), ModParticles.HP_WISP_BLUE.get(), rng, t);
            if (appearRed > 0) coloredOrb(level, centre, rho, theta + Math.PI, e1, e2, (float) (SPHERE_RADIUS * appearRed),
                    ModParticles.HP_GLOW_RED.get(), ModParticles.HP_WISP_RED.get(), rng, t);

            // arcs of purple between them once both are out
            if (t > 36 && t % 2 == 0) {
                Vec3 a = centre.add(e1.scale(Math.cos(theta) * rho)).add(e2.scale(Math.sin(theta) * rho));
                Vec3 b = centre.subtract(e1.scale(Math.cos(theta) * rho)).subtract(e2.scale(Math.sin(theta) * rho));
                Vec3 j = a.add(b).scale(0.5).add(rnd(rng).scale(0.5));
                line(level, a, j.subtract(a));
                line(level, j, b.subtract(j));
            }
            // as they leave, the space behind him starts to pull
            if (t >= CONVERGE_TICK) {
                for (int i = 0; i < 4; i++) {
                    Vec3 p = M.add(rnd(rng).normalize().scale(5 + rng.nextDouble() * 6));
                    one(level, ModParticles.HP_WISP.get(), p, M.subtract(p).scale(0.1));
                }
                if (t % 3 == 0) ring(level, ModParticles.HP_RING.get(), M, 3f + (float) (s * 3));
            }
            return;
        }

        // ---- the merge: one colossal Purple behind him ---------------------------------------------------
        int m = t - MERGE_TICK;
        double g = smooth(m / (double) (CHARGE_TICKS - MERGE_TICK));
        float rad = (float) (0.8 + (ORB_RADIUS - 0.8) * g);
        if (m == 0) {
            r.center = M;
            announce(level, c, Component.literal("Hollow Purple").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            sound(level, M, SoundEvents.LIGHTNING_BOLT_IMPACT, 4f, 1.2f);
            sound(level, M, SoundEvents.AMETHYST_BLOCK_RESONATE, 4f, 0.5f);
            glow(level, ModParticles.HP_CORE.get(), M, 6f);
            glow(level, ModParticles.HP_GLOW.get(), M, 11f);
            for (int i = 0; i < 4; i++) ring(level, ModParticles.HP_RING.get(), M, 5f + i * 3f);
            for (int i = 0; i < 60; i++) one(level, ModParticles.HP_WISP.get(), M, rnd(rng).normalize().scale(0.4 + rng.nextDouble() * 0.7));
            for (int i = 0; i < 24; i++) {
                Vec3 d = rnd(rng).normalize();
                line(level, M.add(d), d.scale(5 + rng.nextDouble() * 8));
            }
        }
        r.center = M;
        drawOrb(level, M, rad, r.back, t);
        if (t % 4 == 0) sound(level, M, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 3f, (float) (0.5 + g * 0.5));

        // purple lightning to the ground, ground ripples, debris pulled upward
        if (t % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double a = rng.nextDouble() * Math.PI * 2, d = 4 + rng.nextDouble() * 14;
                double x = M.x + Math.cos(a) * d, z = M.z + Math.sin(a) * d;
                double y = floorY(level, x, M.y, z, 40, M.y - 6);
                Vec3 to = new Vec3(x, y, z);
                Vec3 mid = M.lerp(to, 0.5).add(rnd(rng).scale(1.2));
                line(level, M, mid.subtract(M));
                line(level, mid, to.subtract(mid));
            }
        }
        if (t % 8 == 0) ring(level, ModParticles.HP_RING_FLAT.get(), new Vec3(M.x, r.startY + 0.06, M.z), 10f + (float) g * 16f);
        for (int i = 0; i < 6; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = 3 + rng.nextDouble() * 14;
            double x = M.x + Math.cos(a) * d, z = M.z + Math.sin(a) * d;
            double y = floorY(level, x, M.y, z, 40, Double.NaN);
            if (Double.isNaN(y)) continue;
            BlockPos below = BlockPos.containing(x, y - 0.5, z);
            BlockState st = level.getBlockState(below);
            if (st.isAir() || !st.getFluidState().isEmpty()) continue;
            Vec3 p = new Vec3(x, y + 0.2, z);
            one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), p, M.subtract(p).normalize().scale(0.35).add(0, 0.25, 0));
        }
        // everything nearby is dragged toward it
        AABB box = new AABB(M, M).inflate(30);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == c || e.isAlliedTo(c)) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            Vec3 pull = M.subtract(ec);
            double dist = Math.max(1, pull.length());
            e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.12 + 0.06 * g)));
            com.example.jjkspells.effect.OverloadEvents.forceMove(e);
            e.hurtMarked = true;
        }

        if (t >= CHARGE_TICKS - 1) detonate(r, c);
    }

    // =============================================================================================
    //  DETONATION
    // =============================================================================================
    private static void detonate(Rope r, LivingEntity c) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        Vec3 M = r.center;
        r.phase = Phase.IMPACT;
        r.impactAge = 0;

        // the price
        c.addEffect(new MobEffectInstance(ModEffects.BACKLASH.get(), NineRopesSpell.BACKLASH_TICKS, 0, false, true, true));
        c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 20, 0, false, false, false));
        c.fallDistance = 0;

        sound(level, M, SoundEvents.WARDEN_SONIC_BOOM, 6f, 0.5f);
        sound(level, M, SoundEvents.GENERIC_EXPLODE, 8f, 0.5f);
        sound(level, M, SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.4f);
        sound(level, c.position(), SoundEvents.END_GATEWAY_SPAWN, 6f, 0.7f);
        glow(level, ModParticles.HP_CORE.get(), M, 24f);
        glow(level, ModParticles.HP_GLOW.get(), M, 36f);
        for (int i = 0; i < 5; i++) ring(level, ModParticles.HP_RING.get(), M, 18f + i * 12f);
        for (int i = 0; i < 160; i++) one(level, ModParticles.HP_WISP.get(), M, rnd(rng).normalize().scale(0.6 + rng.nextDouble() * 1.6));
        for (int i = 0; i < 70; i++) {
            Vec3 d = rnd(rng).normalize();
            line(level, M.add(d.scale(5)), d.scale(12 + rng.nextDouble() * 24));
        }
    }

    /** @return true when the explosion is over */
    private static boolean impact(Rope r, @Nullable LivingEntity c) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        int i = r.impactAge;
        Vec3 M = r.center;
        int RC = JJKConfig.NINE_ROPES_CRATER_RADIUS.get();
        double cur = Math.min(RC, (i + 1) * SHELL_SPEED);

        // ---- terrain ------------------------------------------------------------------------------
        if (JJKConfig.breaks(JJKConfig.NINE_ROPES_BREAKS_BLOCKS)) breakShell(r, c, i, RC);

        // ---- entities: hit as the front passes them ------------------------------------------------
        if (i < 2 || i % 2 == 0) damageShell(r, c, cur, RC);

        // ---- visuals -------------------------------------------------------------------------------
        if (i < RC / SHELL_SPEED + 1) {
            for (int n = 0; n < 70; n++) {
                Vec3 d = rnd(rng).normalize();
                one(level, ModParticles.HP_WISP.get(), M.add(d.scale(cur)), d.scale(0.25 + rng.nextDouble() * 0.3));
            }
            for (int n = 0; n < 22; n++) {
                Vec3 d = rnd(rng).normalize();
                one(level, ModParticles.HP_VOID.get(), M.add(d.scale(cur * (0.9 + rng.nextDouble() * 0.1))), d.scale(0.08));
            }
            if (i % 3 == 0) {
                ring(level, ModParticles.HP_RING.get(), M, (float) (cur * 1.15));
                ring(level, ModParticles.HP_RING_FLAT.get(), new Vec3(M.x, r.startY + 0.1, M.z), (float) (cur + 10));
            }
            if (i % 2 == 0) {
                for (int n = 0; n < 4; n++) {
                    Vec3 d = rnd(rng).normalize();
                    line(level, M.add(d.scale(cur * 0.85)), d.scale(8 + rng.nextDouble() * 14));
                }
            }
            if (i < 26) {
                // a pillar of purple torn up through the sky
                for (int n = 0; n < 6; n++) {
                    double a = rng.nextDouble() * Math.PI * 2, d = rng.nextDouble() * 6;
                    one(level, ModParticles.HP_WISP.get(), M.add(Math.cos(a) * d, rng.nextDouble() * 4, Math.sin(a) * d),
                            new Vec3(0, 0.5 + rng.nextDouble() * 1.0, 0));
                }
            }
            if (i % 8 == 0) sound(level, M, SoundEvents.GENERIC_EXPLODE, 6f, 0.6f + rng.nextFloat() * 0.2f);
        }

        r.impactAge++;
        return i >= RC / SHELL_SPEED + LINGER_TICKS;
    }

    /** Destroys the terrain the blast front crosses this tick. The crater is flatter below the centre (half as deep as it is wide). */
    private static void breakShell(Rope r, @Nullable LivingEntity c, int i, int RC) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        double cur = Math.min(RC, (i + 1) * SHELL_SPEED);
        double prev = Math.min(RC, i * SHELL_SPEED);
        if (cur <= prev) return;

        BlockPos origin = BlockPos.containing(r.center);
        int ext = (int) Math.ceil(cur);
        double cur2 = cur * cur, prev2 = prev * prev;
        ServerPlayer player = c instanceof ServerPlayer sp ? sp : null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int debris = 0;

        for (int x = -ext; x <= ext; x++) {
            for (int z = -ext; z <= ext; z++) {
                double hd2 = x * x + z * z;
                if (hd2 > cur2) continue;
                for (int y = -ext / 2 - 1; y <= ext; y++) {
                    double dy = y < 0 ? y * 2.0 : y;
                    double d2 = hd2 + dy * dy;
                    if (d2 > cur2 || (i > 0 && d2 <= prev2)) continue;

                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(level, pos) < 0f) continue;

                    int h = (pos.getX() * 73856093) ^ (pos.getY() * 19349663) ^ (pos.getZ() * 83492791);
                    double jag = 0.92 + 0.08 * ((h & 1023) / 1023.0);
                    if (Math.sqrt(d2) > RC * jag) continue;
                    if (player != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, player))) continue;

                    if (debris < 60 && rng.nextInt(120) == 0 && state.getFluidState().isEmpty()) {
                        debris++;
                        Vec3 out = new Vec3(x, y * 0.5 + 1, z).normalize();
                        FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, state),
                                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                                out.scale(0.7 + rng.nextDouble() * 1.2).add(0, 0.4, 0));
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /** Hits everything the blast front has reached and not yet hit. Spares the caster and their allies. */
    private static void damageShell(Rope r, @Nullable LivingEntity c, double cur, int RC) {
        ServerLevel level = r.level;
        Vec3 M = r.center;
        float power = r.spell.getSpellPower(r.spellLevel, c);
        AABB box = new AABB(M, M).inflate(cur);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (c != null && (e == c || e.isAlliedTo(c))) continue;
            if (r.hit.contains(e.getUUID())) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double d = ec.distanceTo(M);
            if (d > cur) continue;
            r.hit.add(e.getUUID());

            double falloff = 1.0 - 0.5 * Math.min(1.0, d / RC);
            float dmg = (float) (power * NineRopesSpell.BLAST_FACTOR * falloff);
            e.invulnerableTime = 0;
            DamageSources.applyDamage(e, dmg, r.spell.getDamageSource(c));
            if (c != null) SpellDamage.percent(e, (float) (e.getMaxHealth() * NineRopesSpell.BLAST_PERCENT * falloff), c, level.damageSources().indirectMagic(c, c));

            Vec3 out = ec.subtract(M);
            if (out.lengthSqr() < 1.0E-4) out = new Vec3(0, 1, 0);
            out = out.normalize();
            double push = 1.0 + 3.0 * (1.0 - Math.min(1.0, d / RC));
            e.setDeltaMovement(out.x * push, out.y * push * 0.5 + 0.6, out.z * push);
            com.example.jjkspells.effect.OverloadEvents.forceMove(e);
            e.hurtMarked = true;
            glow(level, ModParticles.HP_CORE.get(), ec, 2f);
        }
    }

    // =============================================================================================
    //  VISUALS
    // =============================================================================================
    /** One of the circling spheres: layered glow, a white core, a shell of coloured wisps and a trail. */
    private static void coloredOrb(ServerLevel level, Vec3 centre, double rho, double theta, Vec3 e1, Vec3 e2, float rad,
                                   ParticleOptions glowType, ParticleOptions wispType, RandomSource rng, int t) {
        Vec3 off = e1.scale(Math.cos(theta)).add(e2.scale(Math.sin(theta)));
        Vec3 tangent = e1.scale(-Math.sin(theta)).add(e2.scale(Math.cos(theta)));
        Vec3 P = centre.add(off.scale(rho));

        glow(level, glowType, P, rad * 2.2f);
        glow(level, glowType, P, rad * 1.4f);
        glow(level, ModParticles.HP_CORE.get(), P, rad * 0.6f);

        // shell of wisps swirling over the surface
        for (int i = 0; i < 6; i++) {
            Vec3 d = rnd(rng).normalize();
            Vec3 swirl = d.cross(tangent).scale(0.12);
            one(level, wispType, P.add(d.scale(rad * 1.05)), swirl);
        }
        // trail: wisps left behind and a streak along the path
        for (int i = 0; i < 3; i++) {
            one(level, wispType, P.add(rnd(rng).scale(rad * 0.5)), tangent.scale(-0.22).add(rnd(rng).scale(0.04)));
        }
        line(level, P.subtract(tangent.scale(0.5)), tangent.scale(-(2.0 + rho * 0.4)));
    }

    /** The big purple orb: layered glows, three spinning gyroscope rings, surface arcs. */
    private static void drawOrb(ServerLevel level, Vec3 P, float rad, Vec3 facing, int age) {
        RandomSource rng = level.random;
        glow(level, ModParticles.HP_GLOW.get(), P, rad * 1.6f);
        glow(level, ModParticles.HP_GLOW.get(), P, rad * 1.2f);
        glow(level, ModParticles.HP_CORE.get(), P, rad * 0.78f);

        Vec3 f = facing.normalize();
        Vec3 rr = f.cross(new Vec3(0, 1, 0));
        if (rr.lengthSqr() < 1.0E-4) rr = new Vec3(1, 0, 0);
        rr = rr.normalize();
        Vec3 u = rr.cross(f).normalize();
        Vec3[][] planes = {{rr, u}, {rr, f}, {u, f}};
        int n = 3 + (int) (rad * 1.0);
        for (int pi = 0; pi < 3; pi++) {
            Vec3 A = planes[pi][0], B = planes[pi][1];
            double spin = age * (0.22 + pi * 0.07) * (pi == 1 ? -1 : 1);
            for (int i = 0; i < n; i++) {
                double a = spin + i * Math.PI * 2 / n;
                Vec3 off = A.scale(Math.cos(a)).add(B.scale(Math.sin(a)));
                Vec3 tang = A.scale(-Math.sin(a)).add(B.scale(Math.cos(a)));
                one(level, ModParticles.HP_WISP.get(), P.add(off.scale(rad * 1.05)), tang.scale(0.12 * Math.sqrt(rad)));
            }
        }
        for (int i = 0; i < 4 + (int) (rad * 0.6); i++) {
            Vec3 p1 = rnd(rng).normalize();
            Vec3 p2 = p1.add(rnd(rng).scale(0.7)).normalize();
            Vec3 a = P.add(p1.scale(rad));
            Vec3 e = P.add(p2.scale(rad * 1.15));
            line(level, a, e.subtract(a));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------
    private static double floorY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p)) return fallback;
        for (int i = 0; i < maxDown; i++) {
            if (level.isOutsideBuildHeight(p)) return fallback;
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty()) return p.getY() + 1.0;
            p.move(Direction.DOWN);
        }
        return fallback;
    }

    private static Vec3 rnd(RandomSource rng) {
        return new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static void one(ServerLevel level, ParticleOptions type, Vec3 pos, Vec3 vel) {
        FX.one(level, type, pos, vel);
    }

    private static void glow(ServerLevel level, ParticleOptions type, Vec3 pos, float radius) {
        FX.one(level, type, pos, new Vec3(radius, 0, 0));
    }

    private static void ring(ServerLevel level, ParticleOptions type, Vec3 pos, float radius) {
        FX.one(level, type, pos, new Vec3(radius, 0, 0));
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 vec) {
        FX.one(level, ModParticles.HP_LINE.get(), from, vec);
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void announce(ServerLevel level, LivingEntity caster, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(caster) < 96 * 96) sp.displayClientMessage(text, true);
        }
    }
}
