package com.example.jjkspells.velgrynd.entity;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.registry.*;
import com.example.jjkspells.velgrynd.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Parallel Existence - the Fourfold Dragon Calamity. Entity at the target; r1 = distance of the four bodies, yaw = the angle of the first. fx type 1 = a beam hit {x, y, z, body}. */
public class ParallelEntity extends SeqEntity {
    private final Map<UUID, Integer> beams = new HashMap<>();
    private boolean applied;

    public ParallelEntity(EntityType<? extends ParallelEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 40;
    }

    public static ParallelEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.PARALLEL;
        ParallelEntity e = new ParallelEntity(ModEntities.PARALLEL.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.distance.get(), c.radius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(12.0);
    }

    /** Position of body k relative to the target. */
    public static Vec3 body(int k, double dist, float yaw) {
        double a = Math.toRadians(yaw + 45.0 + k * 90.0);
        return new Vec3(-Math.sin(a) * dist, 0, Math.cos(a) * dist);
    }

    private boolean onBeam(LivingEntity e, Vec3 a, Vec3 b, double width) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        Vec3 dn = d.scale(1.0 / len);
        Vec3 r = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(a);
        double s = r.dot(dn);
        if (s < 0 || s > len) {
            return false;
        }
        return r.subtract(dn.scale(s)).length() <= width / 2 + e.getBbWidth() / 2 + e.getBbHeight() * 0.25;
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.PARALLEL;
        Vec3 p = position();
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 5f, 0.5f);
        }
        if (age < tk(11.0) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 0.0, 1.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.RUNE.get(), o.getX(), o.getY() + 1, o.getZ(), 6, 1.0, 1.0, 1.0, 0.04);
        }
        if (at(age, 1.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 6f, 0.7f);
            for (int k = 0; k < 4; k++) {
                Vec3 b = p.add(body(k, c.distance.get(), yaw()));
                Util.ring(level(), ModParticles.SPARK.get(), b.add(0, 0.5, 0), 40, 1.4, 0.05);
            }
        }
        if (at(age, 3.5)) {
            double w = c.beamWidth.get();
            for (int k = 0; k < 4; k++) {
                Vec3 b = p.add(body(k, c.distance.get(), yaw())).add(0, 1.6, 0);
                Vec3 far = p.add(0, 1.6, 0).add(p.add(0, 1.6, 0).subtract(b).normalize().scale(c.radius.get() * 0.4));
                for (LivingEntity e : Util.enemies(level(), p, c.distance.get() + c.radius.get(), o)) {
                    if (onBeam(e, b, far, w)) {
                        Util.hurt(o, e, c.beamFlat.get(), 0);
                        track(e);
                        beams.merge(e.getUUID(), 1, Integer::sum);
                        pushFx(1, e.getX(), e.getY(), e.getZ(), k);
                    }
                }
                Util.sound(level(), b.x, b.y, b.z, ModSounds.LASER.get(), 6f, 0.6f);
                Vec3 dn = p.subtract(b).normalize();
                for (double s = 2; s < c.distance.get() + 10; s += 8) {
                    Vec3 q = b.add(dn.scale(s));
                    Util.fx(level(), ModParticles.SPARK.get(), q.x, q.y, q.z, 3, 0.8, 0.8, 0.8, 0.05);
                }
            }
        }
        if (at(age, 4.5) && !applied) {
            applied = true;
            for (LivingEntity e : foes(o, c.radius.get() * 1.5)) {
                int n = beams.getOrDefault(e.getUUID(), 0);
                if (n > 0 && e.isAlive()) {
                    Util.vulnerable(e, Math.min(c.vulnMaxPercent.get(), n * c.vulnPercent.get()), tk(9.0));
                    e.addEffect(new MobEffectInstance(ModEffects.BRAND.get(), tk(9.0), 0, false, false, true));
                }
            }
        }
        if (age >= tk(6.0) && age < tk(11.0)) {
            if (age % 10 == 0) {
                for (LivingEntity e : foes(o, c.radius.get())) {
                    Util.slowPct(e, Util.isBoss(e) ? c.slowPercent.get() * c.bossSlowPercent.get() / 100.0 : c.slowPercent.get(), 30);
                }
            }
            if (age < tk(9.0) && age % 2 == 0) {
                for (LivingEntity e : foes(o, c.radius.get())) {
                    Util.pull(e, p.add(0, 2, 0), 0.2, 3.0);
                }
            }
            if (age % 5 == 0) {
                Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 2, p.z, 14, c.radius.get() * 0.3, 3, c.radius.get() * 0.3, 0.2);
            }
        }
        if (at(age, 7.5)) {
            for (LivingEntity e : foes(o, c.radius.get())) {
                Util.hurt(o, e, c.judgeFlat.get(), c.judgePercent.get());
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 3, p.z, 18, 260);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 8f, 0.5f);
        }
        if (at(age, 10.0)) {
            for (LivingEntity e : foes(o, c.radius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get());
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 32, 420);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.8, 0), 160, 3.8, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 10f, 0.5f);
        }
        if (at(age, 11.0)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.CHARGE.get(), 5f, 1.2f);
            Util.flameBurst(level(), o.getX(), o.getY() + 1, o.getZ(), 4, 60);
        }
    }
}
