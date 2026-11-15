package com.example.jjkspells.spells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Aiming helpers shared by Gilgamesh's moves. */
public final class GoldTarget {
    private GoldTarget() {}

    /** The living thing closest to the caster's line of sight (within about 14 degrees), or null. */
    public static LivingEntity find(ServerLevel level, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        AABB box = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(10);
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == caster || e.isAlliedTo(caster) || e.isSpectator()) continue;
            if (e instanceof Player p && p.getAbilities().invulnerable) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < 0.5) continue;
            double dot = to.scale(1.0 / d).dot(look);
            if (dot < (d < 4 ? 0.8 : 0.97)) continue;
            double score = dot - d * 0.0005;
            if (best == null || score > bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    /** The first block or creature along the line of sight, or the far end of it. */
    public static Vec3 aimPoint(ServerLevel level, LivingEntity c, double range) {
        Vec3 eye = c.getEyePosition();
        Vec3 look = c.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        Vec3 hit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        AABB box = c.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, c, eye, hit, box, e -> e.isAlive() && !e.isSpectator() && e.isPickable());
        if (eh != null) hit = eh.getLocation();
        return hit;
    }

    /** Top of the floor under (x, z), searching down from startY. */
    public static double groundY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
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
