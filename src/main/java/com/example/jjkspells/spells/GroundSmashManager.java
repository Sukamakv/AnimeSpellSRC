package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
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
 * Runs every active Ground Smash.
 *
 *   WINDUP - the caster hops, dust and sparks gather at their feet, then they drive down into the ground.
 *   WAVE   - the impact: cracks spread across the ground, a ring of force rolls out to the full radius, throwing up
 *            debris. Everything near the ground that the front passes over is hurt and thrown away, once.
 *
 * No blocks are changed; everything on the ground is visual only.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class GroundSmashManager {

    // ---- Tuning -------------------------------------------------------------------------------
    private static final int WINDUP = 6;          // ticks from the cast to the impact
    private static final int SLAM_TICK = 4;       // the caster is driven down on this tick
    private static final int EXPAND = 10;         // ticks the front takes to reach the full radius
    private static final double HOP = 0.42;       // upward speed of the hop (a normal jump)
    private static final double SLAM = -0.8;      // downward speed of the slam
    private static final double REACH_UP = 2.0;   // targets whose feet are higher than this above the ground are missed
    private static final int MAX_FRONT_POINTS = 26;

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static class Smash {
        final ServerLevel level;
        final UUID casterId;
        final GroundSmashSpell spell;
        final int spellLevel;
        final float radius;
        int age = 0;
        Vec3 center = Vec3.ZERO;
        BlockState groundState = Blocks.STONE.defaultBlockState();
        final Set<UUID> hit = new HashSet<>();

        Smash(ServerLevel level, LivingEntity caster, GroundSmashSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.radius = spell.getRadius(spellLevel);
        }
    }

    private static final List<Smash> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, GroundSmashSpell spell, int spellLevel) {
        ACTIVE.add(new Smash(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Smash> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Smash s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done = false;
            try {
                done = tick(s, caster);
            } catch (Exception ex) {
                LOGGER.error("Ground Smash failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done || s.age > 200) it.remove();
        }
    }

    /** @return true when this smash is finished. */
    private static boolean tick(Smash s, @Nullable LivingEntity caster) {
        if (caster == null) return true;   // the caster died or left: the kick never lands

        if (s.age == 0) windupStart(s, caster);
        if (s.age < WINDUP) {
            windupTick(s, caster);
            return false;
        }
        if (s.age == WINDUP) impact(s, caster);

        int k = s.age - WINDUP + 1;        // 1 .. EXPAND
        double t = Math.min(1.0, k / (double) EXPAND);
        double r = s.radius * (1.0 - Math.pow(1.0 - t, 3));   // eases out, like the ring sprite
        sweep(s, caster, r);
        frontFx(s, r);
        return k >= EXPAND;
    }

    // =============================================================================================
    //  WINDUP
    // =============================================================================================

    private static void windupStart(Smash s, LivingEntity caster) {
        MartialFX.sound(s.level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        if (caster.onGround()) {
            Vec3 v = caster.getDeltaMovement();
            caster.setDeltaMovement(v.x * 0.3, HOP, v.z * 0.3);
            caster.hurtMarked = true;
        }
    }

    private static void windupTick(Smash s, LivingEntity caster) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        Vec3 feet = caster.position();

        if (s.age == SLAM_TICK) {
            Vec3 v = caster.getDeltaMovement();
            caster.setDeltaMovement(v.x * 0.2, SLAM, v.z * 0.2);
            caster.hurtMarked = true;
        }

        // dust and sparks gather in toward the kick
        double gy = findGroundY(level, feet);
        for (int i = 0; i < 3; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double rad = 1.0 + rng.nextDouble() * 1.0;
            Vec3 p = new Vec3(feet.x + Math.cos(ang) * rad, gy + 0.15, feet.z + Math.sin(ang) * rad);
            FX.one(level, ModParticles.MA_DUST.get(), p, new Vec3(-Math.cos(ang) * 0.06, 0.04, -Math.sin(ang) * 0.06));
        }
        FX.one(level, ModParticles.MA_SPARK.get(), feet.add(0, 0.9, 0), MartialFX.randomDir(rng).scale(0.18));
        if (s.age >= SLAM_TICK) {
            MartialFX.streak(level, feet.add(0, 2.2, 0), feet.add(0, 0.2, 0));
        }
    }

    // =============================================================================================
    //  IMPACT
    // =============================================================================================

    private static void impact(Smash s, LivingEntity caster) {
        ServerLevel level = s.level;
        Vec3 feet = caster.position();
        double gy = findGroundY(level, feet);
        s.center = new Vec3(feet.x, gy, feet.z);
        BlockState under = level.getBlockState(BlockPos.containing(feet.x, gy - 0.5, feet.z));
        if (!under.isAir()) s.groundState = under;
        caster.fallDistance = 0;
        Vec3 cv = caster.getDeltaMovement();
        if (cv.y < 0) {
            caster.setDeltaMovement(cv.x * 0.2, 0, cv.z * 0.2);
            caster.hurtMarked = true;
        }

        Vec3 c = s.center;
        MartialFX.sound(level, c, SoundEvents.GENERIC_EXPLODE, 1.0f, 1.35f);
        MartialFX.sound(level, c, SoundEvents.ANVIL_LAND, 0.9f, 0.55f);
        level.playSound(null, c.x, c.y, c.z, s.groundState.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.4f, 0.55f);

        FX.one(level, ModParticles.MA_SHOCK_RING_FLAT.get(), c.add(0, 0.07, 0), new Vec3(s.radius, 0, 0));
        FX.one(level, ModParticles.MA_CRACKS.get(), c.add(0, 0.04, 0), new Vec3(s.radius * 0.9, 0, 0));
        FX.one(level, ModParticles.MA_GLOW.get(), c.add(0, 0.5, 0), new Vec3(2.6, 0, 0));
        FX.one(level, ModParticles.MA_IMPACT.get(), c.add(0, 0.6, 0), new Vec3(1.6, 0, 0));

        RandomSource rng = level.random;
        for (int i = 0; i < 12; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double sp = 0.05 + rng.nextDouble() * 0.1;
            FX.one(level, ModParticles.MA_DUST.get(), c.add(Math.cos(ang) * 0.6, 0.2, Math.sin(ang) * 0.6),
                    new Vec3(Math.cos(ang) * sp, 0.05 + rng.nextDouble() * 0.08, Math.sin(ang) * sp));
        }
        for (int i = 0; i < 10; i++) {
            FX.one(level, ModParticles.MA_SPARK.get(), c.add(0, 0.3, 0), MartialFX.randomDir(rng).scale(0.35));
        }
    }

    /** Y of the top of the ground at or below {@code feet} (looks down up to 8 blocks; falls back to the feet). */
    private static double findGroundY(ServerLevel level, Vec3 feet) {
        BlockPos.MutableBlockPos p = BlockPos.containing(feet).mutable();
        for (int i = 0; i < 8; i++) {
            BlockState st = level.getBlockState(p);
            if (!st.getCollisionShape(level, p).isEmpty()) {
                return p.getY() + st.getCollisionShape(level, p).max(Direction.Axis.Y);
            }
            p.move(Direction.DOWN);
        }
        return feet.y;
    }

    // =============================================================================================
    //  THE WAVE
    // =============================================================================================

    /** Hurts and throws everything the front has newly reached. Spares the caster and their allies. */
    private static void sweep(Smash s, LivingEntity caster, double r) {
        ServerLevel level = s.level;
        Vec3 c = s.center;
        float power = s.spell.getSpellPower(s.spellLevel, caster);
        float baseDamage = GroundSmashSpell.DAMAGE_BASE + power * GroundSmashSpell.DAMAGE_FACTOR;
        AABB box = new AABB(c.x - r - 1, c.y - 2.0, c.z - r - 1, c.x + r + 1, c.y + REACH_UP + 3.0, c.z + r + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == caster || e.isAlliedTo(caster) || e.isSpectator()) continue;
            if (s.hit.contains(e.getUUID())) continue;
            if (e.getBoundingBox().minY > c.y + REACH_UP) continue;   // really airborne: rides over the wave

            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d - e.getBbWidth() * 0.5 > r) continue;
            s.hit.add(e.getUUID());

            double far = Math.min(1.0, d / s.radius);
            float dmg = (float) (baseDamage * (1.0 - 0.35 * far));
            e.invulnerableTime = 0;
            if (!DamageSources.applyDamage(e, dmg, s.spell.getDamageSource(caster))) continue;

            double strength = Mth.lerp(far, GroundSmashSpell.KNOCKBACK_NEAR, GroundSmashSpell.KNOCKBACK_FAR);
            MartialFX.shove(e, dx, dz, strength, GroundSmashSpell.LIFT);
            MartialFX.burst(level, e.getBoundingBox().getCenter(), 0.9f);
        }
    }

    /** Debris and dust thrown up along the current edge of the wave. */
    private static void frontFx(Smash s, double r) {
        if (r < 0.5) return;
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        Vec3 c = s.center;
        int n = Mth.clamp((int) (r * 2.4), 6, MAX_FRONT_POINTS);
        double phase = rng.nextDouble() * Math.PI * 2;
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, s.groundState);
        for (int i = 0; i < n; i++) {
            double ang = phase + i * (Math.PI * 2 / n) + (rng.nextDouble() - 0.5) * 0.15;
            double ox = Math.cos(ang), oz = Math.sin(ang);
            Vec3 p = new Vec3(c.x + ox * r, c.y + 0.15, c.z + oz * r);
            FX.one(level, debris, p, new Vec3(ox * 0.12, 0.28 + rng.nextDouble() * 0.25, oz * 0.12));
            if (rng.nextBoolean()) {
                FX.one(level, ModParticles.MA_DUST.get(), p, new Vec3(ox * 0.06, 0.05, oz * 0.06));
            }
        }
    }
}
