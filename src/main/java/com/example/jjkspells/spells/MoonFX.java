package com.example.jjkspells.spells;

import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/** Helpers shared by the Moon Breathing spells: weapon damage, ground finding, and the slash / crescent visuals. */
public final class MoonFX {
    private MoonFX() {}

    // ---- damage ---------------------------------------------------------------------------------

    /**
     * Damage of whatever the caster holds in their main hand: their attack damage attribute (which already includes
     * the held weapon's damage, and effects like Strength) plus the Sharpness-type enchantment bonus.
     * Bare-handed that is 1.
     */
    public static float weaponDamage(LivingEntity caster) {
        double base = 1.0;
        if (caster.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            base = caster.getAttributeValue(Attributes.ATTACK_DAMAGE);
        }
        base += EnchantmentHelper.getDamageBonus(caster.getMainHandItem(), MobType.UNDEFINED);
        return (float) Math.max(1.0, base);
    }

    /** True (and records the hit) if {@code id} has not been hit during the last {@code gap} ticks. */
    public static boolean ready(Map<UUID, Integer> lastHit, UUID id, int now, int gap) {
        Integer last = lastHit.get(id);
        if (last != null && now - last < gap) return false;
        lastHit.put(id, now);
        return true;
    }

    // ---- terrain --------------------------------------------------------------------------------

    /** Y of the top of the ground at (x, z), searching from a little above {@code refY} downward. */
    public static double groundAt(ServerLevel level, double x, double z, double refY) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, refY + 3, z).mutable();
        for (int i = 0; i < 14; i++) {
            BlockState st = level.getBlockState(p);
            if (!st.getCollisionShape(level, p).isEmpty()) {
                return p.getY() + st.getCollisionShape(level, p).max(Direction.Axis.Y);
            }
            p.move(Direction.DOWN);
        }
        return refY;
    }

    // ---- ribbon geometry (the same maths the client uses) ---------------------------------------

    /** Point at fraction p (0..1) along a ribbon, see {@link MoonRibbonOptions}. */
    public static Vec3 curvePoint(Vec3 c, Vec3 u, Vec3 v, float r0, float r1, float lift, float th0, float th1, float p) {
        float th = Mth.lerp(p, th0, th1);
        float r = Mth.lerp(p, r0, r1);
        Vec3 n = u.cross(v);
        return c.add(u.scale(Mth.cos(th) * r)).add(v.scale(Mth.sin(th) * r)).add(n.scale(lift * p));
    }

    // ---- emitters -------------------------------------------------------------------------------

    /** One purple slash ribbon. */
    public static void ribbon(ServerLevel level, Vec3 center, Vec3 u, Vec3 v, float r0, float r1, float lift,
                              float th0, float th1, float width, int life) {
        FX.one(level, MoonRibbonOptions.ribbon(u, v, r0, r1, lift, th0, th1, width, life), center, Vec3.ZERO);
    }

    /** One blue crescent that drifts with {@code vel}. */
    public static void crescent(ServerLevel level, Vec3 pos, Vec3 vel, float radius, float roll, int life) {
        FX.one(level, MoonRibbonOptions.crescent(radius, 3.6f, roll, radius * 0.55f, life), pos, vel);
    }

    /**
     * Scatters {@code count} small blue crescents along a ribbon, each a little off its centre line and drifting
     * along {@code drift} with some randomness: the "smaller moon-like slashes" that ride on every slash.
     */
    public static void crescentsAlong(ServerLevel level, Vec3 c, Vec3 u, Vec3 v, float r0, float r1, float lift,
                                      float th0, float th1, int count, float minSize, float maxSize,
                                      float scatter, Vec3 drift) {
        RandomSource rng = level.random;
        Vec3 n = u.cross(v);
        for (int i = 0; i < count; i++) {
            float p = (i + 0.15f + rng.nextFloat() * 0.7f) / count;
            Vec3 pt = curvePoint(c, u, v, r0, r1, lift, th0, th1, p);
            Vec3 off = n.scale((rng.nextDouble() - 0.5) * 2 * scatter)
                    .add(MartialFX.randomDir(rng).scale(scatter * 0.5));
            float size = Mth.lerp(rng.nextFloat(), minSize, maxSize);
            Vec3 vel = drift.add((rng.nextDouble() - 0.5) * 0.05, (rng.nextDouble() - 0.5) * 0.05, (rng.nextDouble() - 0.5) * 0.05);
            crescent(level, pt.add(off), vel, size, rng.nextFloat() * Mth.TWO_PI, 9 + rng.nextInt(7));
        }
    }

    /** Flash + star + sparks where a slash cuts something. */
    public static void hit(ServerLevel level, Vec3 p, float size) {
        RandomSource rng = level.random;
        FX.one(level, ModParticles.MOON_FLASH.get(), p, new Vec3(size * 1.2, 0, 0));
        FX.one(level, ModParticles.MOON_HIT.get(), p, new Vec3(size, 0, 0));
        int sparks = 3 + (int) (size * 3);
        for (int i = 0; i < sparks; i++) {
            FX.one(level, ModParticles.MOON_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.12 + rng.nextDouble() * 0.2));
        }
    }
}
