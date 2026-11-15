package com.example.jjkspells.client;

import com.example.jjkspells.GaleConfig;
import com.example.jjkspells.entity.GaleBarrageEntity;
import com.example.jjkspells.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client particles for each strike (the big slash shapes are drawn by the renderer). */
public final class BarrageFx {
    private BarrageFx() {
    }

    public static void tick(GaleBarrageEntity e, Entity caster) {
        var level = e.level();
        RandomSource r = level.random;
        int life = e.getLife();
        if (e.tickCount > life) {
            return;
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(up).normalize();
        Vec3 realUp = right.cross(look).normalize();
        double reach = GaleConfig.SPELL.reach.get();

        // constant stream of wind wisps rushing forward
        for (int i = 0; i < 2; i++) {
            double d = 0.8 + r.nextDouble() * 2.0;
            double s = (r.nextDouble() - 0.5) * 1.2, u = (r.nextDouble() - 0.5) * 1.0;
            Vec3 p = eye.add(look.scale(d)).add(right.scale(s)).add(realUp.scale(u));
            Vec3 v = look.scale(0.7 + r.nextDouble() * 0.6);
            level.addParticle(ModParticles.WIND_WISP.get(), true, p.x, p.y, p.z, v.x, v.y, v.z);
        }

        int n = GaleBarrageEntity.strikesAt(e.tickCount, life, e.getHits());
        for (int k = 0; k < n; k++) {
            // sparks sprayed along the strike
            for (int i = 0; i < 9; i++) {
                double d = 1.0 + r.nextDouble() * (reach - 1.0);
                double s = (r.nextDouble() - 0.5) * (1.0 + d * 0.5), u = (r.nextDouble() - 0.5) * (1.0 + d * 0.4);
                Vec3 p = eye.add(look.scale(d)).add(right.scale(s)).add(realUp.scale(u));
                Vec3 v = look.scale(0.5 + r.nextDouble() * 0.8).add(right.scale((r.nextDouble() - 0.5) * 0.3)).add(realUp.scale((r.nextDouble() - 0.5) * 0.3));
                level.addParticle(ModParticles.SLASH_SPARK.get(), true, p.x, p.y, p.z, v.x, v.y, v.z);
            }
            for (int i = 0; i < 3; i++) {
                double d = 1.5 + r.nextDouble() * 3.0;
                Vec3 p = eye.add(look.scale(d)).add(right.scale((r.nextDouble() - 0.5) * 2.0)).add(realUp.scale((r.nextDouble() - 0.5) * 1.6));
                Vec3 v = right.scale((r.nextDouble() - 0.5) * 0.9).add(look.scale(0.6));
                level.addParticle(ModParticles.WIND_WISP.get(), true, p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
    }
}
