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

/** Scorch Dragon's Breath - the Horizon Incinerator. Entity at the caster; r1 = current beam length. fx type 1 = the locked beam {sx, sy, sz, ex, ey, ez}. */
public class BreathEntity extends SeqEntity {
    private static final double LOCK = 5.5;
    private boolean locked, hitFirst;
    private Vec3 start = Vec3.ZERO, end = Vec3.ZERO;
    private final Map<UUID, Integer> inside = new HashMap<>();

    public BreathEntity(EntityType<? extends BreathEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 80;
    }

    public static BreathEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.BREATH;
        BreathEntity e = new BreathEntity(ModEntities.BREATH.get(), level);
        e.begin(caster, caster.position(), c.range.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(8.0);
    }

    /** Where the beam leaves the caster (her raised hand). */
    public static Vec3 mouth(LivingEntity o) {
        return o.getEyePosition().add(o.getLookAngle().scale(1.2)).add(0, -0.35, 0);
    }

    private void aim(LivingEntity o) {
        var c = VgrConfig.BREATH;
        start = mouth(o);
        Vec3 far = start.add(o.getLookAngle().scale(c.range.get()));
        var hit = level().clip(new net.minecraft.world.level.ClipContext(start, far, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, o));
        end = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : hit.getLocation();
        setR1(start.distanceTo(end));
    }

    private List<LivingEntity> inBeam(LivingEntity o) {
        var c = VgrConfig.BREATH;
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
        var c = VgrConfig.BREATH;
        Util.hurt(o, e, flat, pct, mult, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
        track(e);
    }

    private void scorch(LivingEntity o, LivingEntity e) {
        var c = VgrConfig.BREATH;
        if (e.isAlive()) {
            int t = tk(c.scorchSeconds.get());
            Util.burn(o, e, c.scorchDps.get(), t);
            Util.healCut(e, c.scorchHealPercent.get(), t);
            e.addEffect(new MobEffectInstance(ModEffects.SCORCH.get(), t, 0, false, false, true));
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.BREATH;
        setPos(o.getX(), o.getY(), o.getZ());
        if (!locked) {
            aim(o);
        }
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 5f, 0.5f);
        }
        if (age < tk(6.5) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 0.0, 2.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), start.x, start.y, start.z, 6, 0.6, 0.6, 0.6, 0.15);
        }
        if (at(age, 2.5)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.LASER.get(), 8f, 0.5f);
        }
        if (age >= tk(2.5) && age < tk(6.5) && age % 4 == 0) {
            Vec3 dn = end.subtract(start).normalize();
            for (double s = 2; s < start.distanceTo(end); s += 10) {
                Vec3 q = start.add(dn.scale(s));
                Util.fx(level(), ModParticles.SPARK.get(), q.x, q.y, q.z, 3, c.width.get() * 0.3, c.width.get() * 0.3, c.width.get() * 0.3, 0.05);
            }
            Util.sparkBurst(level(), end.x, end.y, end.z, 6, 60);
        }
        if (at(age, 2.5) && !hitFirst) {
            hitFirst = true;
            for (LivingEntity e : inBeam(o)) {
                hurt(o, e, c.firstFlat.get(), c.firstPercent.get(), 1.0);
                scorch(o, e);
            }
        }
        if (age >= tk(3.5) && age < tk(6.5) && age % 20 == 0) {
            for (LivingEntity e : inBeam(o)) {
                int s = inside.merge(e.getUUID(), 1, Integer::sum);
                double mult = 1.0 + Math.min(c.accelMaxPercent.get(), s * c.accelPercent.get()) / 100.0;
                hurt(o, e, c.dps.get(), 0, mult);
                scorch(o, e);
            }
            Util.sound(level(), end.x, end.y, end.z, ModSounds.BOOM.get(), 4f, 0.8f);
        }
        if (at(age, LOCK) && !locked) {
            locked = true;
            pushFx(1, start.x, start.y, start.z, end.x, end.y, end.z);
        }
        if (at(age, 6.5)) {
            for (LivingEntity e : Util.enemies(level(), end, c.finalRadius.get(), o)) {
                hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), 1.0);
                scorch(o, e);
            }
            Util.sparkBurst(level(), end.x, end.y + 2, end.z, 22, 320);
            Util.ring(level(), ModParticles.SPARK.get(), end.add(0, 0.6, 0), 120, 3.4, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), end.add(0, 0.9, 0), 96, 2.6, 0.05);
            Util.sound(level(), end.x, end.y, end.z, ModSounds.COLLAPSE.get(), 8f, 0.55f);
        }
    }
}
