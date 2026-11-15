package com.example.jjkspells.veldora.entity;

import com.example.jjkspells.veldora.config.VelConfig;
import com.example.jjkspells.veldora.registry.*;
import com.example.jjkspells.veldora.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Dragon Breath - the Annihilation of Horizons. Entity at the caster; r1 = current beam length. fx type 1 = the locked beam {sx, sy, sz, ex, ey, ez}. */
public class BreathEntity extends SeqEntity {
    private static final double LOCK = 6.0;
    private boolean locked, hitFirst;
    private Vec3 start = Vec3.ZERO, end = Vec3.ZERO;

    public BreathEntity(EntityType<? extends BreathEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 80;
    }

    public static BreathEntity create(Level level, LivingEntity caster) {
        var c = VelConfig.BREATH;
        BreathEntity e = new BreathEntity(ModEntities.BREATH.get(), level);
        e.begin(caster, caster.position(), c.range.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(8.0);
    }

    /** Where the beam leaves the caster (the mouth). */
    public static Vec3 mouth(LivingEntity o) {
        return o.getEyePosition().add(o.getLookAngle().scale(1.2)).add(0, -0.25, 0);
    }

    private void aim(LivingEntity o) {
        var c = VelConfig.BREATH;
        start = mouth(o);
        Vec3 far = start.add(o.getLookAngle().scale(c.range.get()));
        var hit = level().clip(new net.minecraft.world.level.ClipContext(start, far, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, o));
        end = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : hit.getLocation();
        setR1(start.distanceTo(end));
    }

    private List<LivingEntity> inBeam(LivingEntity o) {
        var c = VelConfig.BREATH;
        Vec3 d = end.subtract(start);
        double len = d.length();
        if (len < 0.5) {
            return List.of();
        }
        Vec3 dn = d.scale(1.0 / len);
        List<LivingEntity> out = new ArrayList<>();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(start, end).inflate(c.width.get() / 2 + 3);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, x -> Util.isEnemy(o, x))) {
            Vec3 r = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(start);
            double s = r.dot(dn);
            if (s < -1 || s > len + 1) {
                continue;
            }
            double dist = r.subtract(dn.scale(s)).length();
            if (dist <= c.width.get() / 2 + e.getBbWidth() / 2 + e.getBbHeight() * 0.25) {
                out.add(e);
            }
        }
        return out;
    }

    private void hurt(LivingEntity o, LivingEntity e, double flat, double pct, double mult) {
        var c = VelConfig.BREATH;
        Util.hurt(o, e, flat, pct, mult, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
        track(e);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VelConfig.BREATH;
        setPos(o.getX(), o.getY(), o.getZ());
        if (!locked) {
            aim(o);
        }
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 5f, 0.4f);
        }
        if (age < tk(7.2) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 1.0, 2.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), start.x, start.y, start.z, 6, 0.6, 0.6, 0.6, 0.15);
        }
        if (at(age, 2.5)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.LASER.get(), 8f, 0.4f);
        }
        if (age >= tk(2.5) && age < tk(LOCK)) {
            if (age == tk(2.5) || age % 4 == 0) {
                Vec3 dn = end.subtract(start).normalize();
                for (double s = 2; s < start.distanceTo(end); s += 9) {
                    Vec3 q = start.add(dn.scale(s));
                    Util.fx(level(), ModParticles.SPARK.get(), q.x, q.y, q.z, 3, c.width.get() * 0.3, c.width.get() * 0.3, c.width.get() * 0.3, 0.05);
                }
                Util.sparkBurst(level(), end.x, end.y, end.z, 6, 60);
            }
        }
        if (at(age, 2.5) && !hitFirst) {
            hitFirst = true;
            int t = tk(c.overloadSeconds.get());
            for (LivingEntity e : inBeam(o)) {
                hurt(o, e, c.firstFlat.get(), c.firstPercent.get(), 1.0);
                if (e.isAlive()) {
                    Util.vulnerable(e, c.overloadVulnPercent.get(), t);
                    Util.healCut(e, c.overloadHealPercent.get(), t);
                    e.addEffect(new MobEffectInstance(ModEffects.OVERLOAD.get(), t, 0, false, false, true));
                }
            }
        }
        if (age >= tk(3.5) && age < tk(LOCK) && age % 20 == 0) {
            for (LivingEntity e : inBeam(o)) {
                hurt(o, e, c.dps.get(), 0, 1.0);
            }
            Util.sound(level(), end.x, end.y, end.z, ModSounds.BOOM.get(), 4f, 0.8f);
        }
        if (at(age, LOCK) && !locked) {
            locked = true;
            pushFx(1, start.x, start.y, start.z, end.x, end.y, end.z);
        }
        if (at(age, 7.0)) {
            for (LivingEntity e : foes2(o, end, c.finalRadius.get())) {
                hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()));
            }
            Util.sparkBurst(level(), end.x, end.y + 2, end.z, 22, 320);
            Util.ring(level(), ModParticles.SPARK.get(), end.add(0, 0.6, 0), 120, 3.4, 0.06);
            Util.sound(level(), end.x, end.y, end.z, ModSounds.COLLAPSE.get(), 8f, 0.5f);
        }
    }

    private List<LivingEntity> foes2(LivingEntity o, Vec3 at, double r) {
        return Util.enemies(level(), at, r, o);
    }
}
