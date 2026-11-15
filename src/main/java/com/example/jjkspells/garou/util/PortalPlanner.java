package com.example.jjkspells.garou.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Works out where a Cosmic Portal teleport ends. Server side only.
 *  1. An enemy on the look ray (within enemyRange, aim assist) -> a free spot BEHIND it, facing it.
 *  2. Otherwise the point the caster looks at (a block hit, or forwardRange blocks of open air), pulled back until it
 *     fits the caster's hitbox and has ground within reach.
 * If the chosen spot is blocked, nearby alternatives are tried; if none fits the plan is null (teleport cancelled).
 */
public final class PortalPlanner {
    private PortalPlanner() {}

    /** dest = feet position, yaw = facing on arrival, targetId = enemy entity id or -1. */
    public record Plan(Vec3 dest, float yaw, int targetId) {}

    public static Plan plan(Level level, LivingEntity caster, float enemyRange, float forwardRange) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        // ---- 1. enemy on the look ray?
        Vec3 end = eye.add(look.scale(enemyRange));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double maxD = bh.getType() == HitResult.Type.MISS ? enemyRange : eye.distanceTo(bh.getLocation());
        Vec3 segEnd = eye.add(look.scale(maxD));
        LivingEntity best = null;
        double bestD = maxD;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, segEnd).inflate(2.0), e -> GarouDamage.isEnemy(caster, e))) {
            AABB bb = t.getBoundingBox().inflate(0.8);
            Optional<Vec3> hit = bb.clip(eye, segEnd);
            double d = hit.isPresent() ? eye.distanceTo(hit.get()) : (bb.contains(eye) ? 0 : Double.MAX_VALUE);
            if (d < bestD) { bestD = d; best = t; }
        }
        if (best != null) {
            Vec3 d = behind(level, caster, best);
            if (d == null) return null;                       // enemy picked but no room behind it: cancel
            return new Plan(d, yawToward(d, best.position()), best.getId());
        }
        // ---- 2. visible position
        Vec3 fEnd = eye.add(look.scale(forwardRange));
        BlockHitResult fh = level.clip(new ClipContext(eye, fEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        boolean blocked = fh.getType() != HitResult.Type.MISS;
        double d0 = blocked ? eye.distanceTo(fh.getLocation()) - 0.6 : forwardRange;
        for (int i = 0; i <= 12; i++) {
            double dist = d0 - i * 0.5;
            if (dist < 2.0) break;
            Vec3 p = eye.add(look.scale(dist));
            Vec3 s = findStand(level, caster, p.x, p.y - 1.0, p.z);
            if (s != null) return new Plan(s, caster.getYRot(), -1);
        }
        return null;
    }

    /** A free standing spot behind (preferred) or beside the target, close enough to hit it. */
    public static Vec3 behind(Level level, LivingEntity caster, LivingEntity t) {
        float yaw = t.getYHeadRot() * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));       // the direction the target faces
        Vec3 side = new Vec3(-f.z, 0, f.x);
        double[][] offs = {{-1.7, 0}, {-2.3, 0}, {-1.2, 0}, {-1.7, 1.3}, {-1.7, -1.3}, {-2.5, 1.7}, {-2.5, -1.7}, {-1.0, 1.9}, {-1.0, -1.9}, {0.0, 2.3}, {0.0, -2.3}};
        for (double[] o : offs) {
            Vec3 p = t.position().add(f.scale(o[0])).add(side.scale(o[1]));
            Vec3 s = findStand(level, caster, p.x, t.getY(), p.z);
            if (s != null) {
                double d = s.distanceTo(t.position());
                if (d >= 0.8 && d <= 3.6) return s;
            }
        }
        return null;
    }

    public static float yawToward(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(-(to.x - from.x), to.z - from.z) * Mth.RAD_TO_DEG);
    }

    /**
     * Free feet position near (x, y, z): the hitbox must not collide, there must be ground within ~10 blocks below (we drop
     * to it), and the spot must not be in lava. Tries a few heights around y first. Null if nothing fits.
     */
    public static Vec3 findStand(Level level, LivingEntity caster, double x, double y, double z) {
        double[] dys = {0, 0.5, 1.0, 1.5, 2.0, -0.5, -1.0, -1.5, -2.0, -3.0};
        for (double dy : dys) {
            double yy = y + dy;
            if (!level.hasChunkAt(BlockPos.containing(x, yy, z))) continue;
            if (!fits(level, caster, x, yy, z)) continue;
            double g = yy;
            boolean grounded = false;
            for (int k = 0; k < 40; k++) {
                if (g - 0.25 < level.getMinBuildHeight()) break;
                if (fits(level, caster, x, g - 0.25, z)) g -= 0.25; else { grounded = true; break; }
            }
            if (!grounded) continue;
            BlockPos feet = BlockPos.containing(x, g, z), below = feet.below();
            if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(below).is(FluidTags.LAVA)) continue;
            return new Vec3(x, g, z);
        }
        return null;
    }

    private static boolean fits(Level level, LivingEntity caster, double x, double y, double z) {
        AABB box = caster.getDimensions(caster.getPose()).makeBoundingBox(x, y, z).deflate(0.02);
        return level.noCollision(caster, box);
    }
}
