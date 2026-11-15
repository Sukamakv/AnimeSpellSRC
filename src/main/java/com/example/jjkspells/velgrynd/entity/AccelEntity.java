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

/** Cardinal Acceleration - the Light-Speed Execution. Entity at the primary target. fx type 1 = a strike {x, y, z, index, fromX, fromY, fromZ}, type 2 = the vanish {x, y, z}. */
public class AccelEntity extends SeqEntity {
    private final Map<UUID, Integer> perTarget = new HashMap<>();
    private Random rand;
    private int hits, nextStrike;
    private Vec3 last;

    public AccelEntity(EntityType<? extends AccelEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 60;
    }

    public static AccelEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.ACCEL;
        AccelEntity e = new AccelEntity(ModEntities.ACCEL.get(), level);
        Vec3 p = groundPoint(level, caster, c.range.get());
        e.begin(caster, p, c.radius.get(), c.radius.get(), Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(7.0);
    }

    private void appear(LivingEntity o, Vec3 near, Vec3 face) {
        Vec3 safe = Util.safeNear(level(), o, near, 3);
        Util.snapTo(o, safe == null ? near : safe, Util.yawTo(near, face), 0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.ACCEL;
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 6007L + 5);
        }
        if (age == 0) {
            last = o.position();
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 4f, 0.8f);
        }
        if (age >= tk(0.8) && age < tk(5.8) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 0.0, 0.8) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 0.3, o.getZ(), 8, 0.6, 0.4, 0.6, 0.1);
        }
        if (at(age, 0.8)) {
            Util.LOCKED.add(ownerId);
            Util.UNTOUCHABLE.add(ownerId);
            o.setInvisible(true);
            pushFx(2, o.getX(), o.getY(), o.getZ());
            Util.sparkBurst(level(), o.getX(), o.getY() + 1, o.getZ(), 5, 80);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.THUNDER.get(), 4f, 1.5f);
        }
        if (in(age, 0.8, 1.5) && age % 3 == 0) {
            Util.arc(level(), ModParticles.SPARK.get(), last.add(0, 1, 0), p.add(0, 1, 0), 3.0);
        }
        if (at(age, 1.5)) {
            Vec3 near = p.add(2.5, 0, 0);
            appear(o, near, p);
            o.setInvisible(false);
            Util.UNTOUCHABLE.remove(ownerId);
            for (LivingEntity e : Util.enemies(level(), p, 8, o)) {
                Util.hurt(o, e, c.firstFlat.get(), c.firstPercent.get(), 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, 0.0);
                track(e);
            }
            last = p;
            Util.sparkBurst(level(), p.x, p.y + 1, p.z, 8, 140);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 6f, 1.0f);
        }
        int s0 = tk(1.8), s1 = tk(4.5), n = c.strikeCount.get();
        if (n > 0 && age >= s0 && age < s1 && nextStrike < n && age >= s0 + nextStrike * Math.max(1, (s1 - s0) / n)) {
            strike(o, c, nextStrike);
            nextStrike++;
        }
        if (at(age, 4.5)) {
            o.setNoGravity(true);
            o.setDeltaMovement(Vec3.ZERO);
            appear(o, p.add(0, 11, 0), p);
            o.setNoGravity(true);
            pushFx(2, p.x, p.y + 11, p.z);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 6f, 0.9f);
        }
        if (in(age, 4.5, 5.8)) {
            o.setDeltaMovement(Vec3.ZERO);
            if (age % 3 == 0) {
                Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 1, o.getZ(), 10, 1.0, 1.0, 1.0, 0.25);
            }
        }
        if (at(age, 5.8)) {
            o.setNoGravity(false);
            Util.snapTo(o, p.add(0, 0.2, 0), yaw(), 0);
            double mult = 1.0 + Math.min(c.precisionMaxPercent.get(), hits * c.precisionPercent.get()) / 100.0;
            for (LivingEntity e : foes(o, c.radius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), mult, 1.0, c.armorIgnorePercent.get() / 100.0, 0.0);
                track(e);
            }
            pushFx(1, p.x, p.y, p.z, -1, p.x, p.y + 11, p.z);
            Util.sparkBurst(level(), p.x, p.y + 3, p.z, 22, 320);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 140, 3.6, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.9, 0), 100, 2.6, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 9f, 0.6f);
        }
        if (age == tk(6.0)) {
            Util.LOCKED.remove(ownerId);
        }
    }

    private void strike(LivingEntity o, VgrConfig.Accel c, int i) {
        List<LivingEntity> foes = new ArrayList<>();
        for (LivingEntity e : foes(o, c.radius.get())) {
            if (perTarget.getOrDefault(e.getUUID(), 0) < c.maxHitsPerTarget.get()) {
                foes.add(e);
            }
        }
        Vec3 q;
        if (!foes.isEmpty()) {
            LivingEntity t = foes.get(rand.nextInt(foes.size()));
            q = t.position();
            double a = rand.nextDouble() * Math.PI * 2;
            appear(o, q.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2), q);
            Util.swing(o);
        } else {
            q = spot(rand, 8);
        }
        Vec3 from = last == null ? o.position() : last;
        for (LivingEntity e : Util.enemies(level(), q, 3.5, o)) {
            int h = perTarget.getOrDefault(e.getUUID(), 0);
            if (h >= c.maxHitsPerTarget.get()) {
                continue;
            }
            perTarget.put(e.getUUID(), h + 1);
            Util.hurt(o, e, c.strikeFlat.get(), 0, 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, 0.0);
            track(e);
            hits++;
            if (h + 1 == c.disorientHits.get() && e.isAlive()) {
                Util.slowPct(e, c.disorientSlowPercent.get(), tk(c.disorientSeconds.get()));
                e.addEffect(new MobEffectInstance(ModEffects.BRAND.get(), tk(c.disorientSeconds.get()), 0, false, false, true));
            }
        }
        pushFx(1, q.x, q.y, q.z, i, from.x, from.y, from.z);
        last = q;
        Util.sparkBurst(level(), q.x, q.y + 1, q.z, 4, 60);
        Util.sound(level(), q.x, q.y, q.z, ModSounds.BOOM.get(), 3f, 1.1f + rand.nextFloat() * 0.5f);
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
        }
    }
}
