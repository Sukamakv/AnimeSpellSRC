package com.example.jjkspells.tempest.entity;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.registry.*;
import com.example.jjkspells.tempest.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Megiddo - The Heavenly Execution Array. fx type 1 = a thin beam {lens, x, y, z}, type 2 = an execution beam {x, y, z}. */
public class ArrayEntity extends SeqEntity {
    public static final double LENS_HEIGHT = 55;
    private final Map<UUID, Integer> beams = new HashMap<>();
    private final Set<UUID> executed = new HashSet<>();
    private Random rand;
    private int lastThin = -1, lastExec = -1;

    public ArrayEntity(EntityType<? extends ArrayEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 14;
    }

    public static ArrayEntity create(Level level, LivingEntity caster) {
        var c = TemConfig.ARRAY;
        ArrayEntity e = new ArrayEntity(ModEntities.ARRAY.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(9.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = TemConfig.ARRAY;
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 7777L + 1);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 4f, 0.8f);
        }
        if (in(age, 0, 2.5) && age % 4 == 0) {
            Util.fx(level(), ModParticles.DROP.get(), p.x, p.y + 30, p.z, 6, c.radius.get() * 0.5, 10, c.radius.get() * 0.5, 0.02);
        }
        int t0 = tk(2.5), t1 = tk(4.0), n = c.thinBeams.get();
        if (age >= t0 && age < t1 && n > 0) {
            int idx = (age - t0) * n / (t1 - t0), due = idx - lastThin;
            for (int k = 0; k < due; k++) {
                thin(o, c);
            }
            lastThin = idx;
        }
        int e0 = tk(4.0), e1 = tk(6.0), m = c.execBeams.get();
        if (age >= e0 && age < e1 && m > 0) {
            int idx = (age - e0) * m / (e1 - e0), due = idx - lastExec;
            for (int k = 0; k < due; k++) {
                exec(o, c);
            }
            lastExec = idx;
        }
        if (at(age, 7.5)) {
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get());
            }
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 20, 260);
            Util.ring(level(), ModParticles.LIGHT.get(), p.add(0, 0.6, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 6f, 0.8f);
        }
    }

    private void thin(LivingEntity o, TemConfig.Array c) {
        List<LivingEntity> foes = new ArrayList<>();
        for (LivingEntity e : foes(o, c.radius.get())) {
            if (beams.getOrDefault(e.getUUID(), 0) < c.maxBeamsPerTarget.get()) {
                foes.add(e);
            }
        }
        Vec3 q;
        int lens = rand.nextInt(64);
        if (foes.isEmpty()) {
            q = spot(rand, c.radius.get());
        } else {
            LivingEntity e = foes.get(rand.nextInt(foes.size()));
            beams.merge(e.getUUID(), 1, Integer::sum);
            Util.hurt(o, e, c.beamDamage.get(), 0);
            q = e.position().add(0, e.getBbHeight() * 0.5, 0);
        }
        pushFx(1, lens, q.x, q.y, q.z);
        Util.fx(level(), ModParticles.FLASH.get(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.LIGHT.get(), q.x, q.y, q.z, 6, 0.3, 0.3, 0.3, 0.1);
        Util.sound(level(), q.x, q.y, q.z, ModSounds.LASER.get(), 1.5f, 1.4f + rand.nextFloat() * 0.3f);
    }

    private void exec(LivingEntity o, TemConfig.Array c) {
        LivingEntity best = null;
        for (LivingEntity e : foes(o, c.radius.get())) {
            if (!executed.contains(e.getUUID()) && (best == null || e.getMaxHealth() > best.getMaxHealth())) {
                best = e;
            }
        }
        Vec3 q;
        if (best == null) {
            q = spot(rand, c.radius.get());
        } else {
            executed.add(best.getUUID());
            q = best.position();
            Util.hurt(o, best, c.execFlat.get(), c.execPercent.get(), below(best, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0, c.armorIgnorePercent.get() / 100.0);
        }
        pushFx(2, q.x, q.y, q.z);
        Util.sparkBurst(level(), q.x, q.y + 1, q.z, 5, 70);
        Util.fx(level(), ModParticles.RING.get(), q.x, q.y + 0.5, q.z, 1, 0, 0, 0, 0);
        Util.sound(level(), q.x, q.y, q.z, ModSounds.BOOM.get(), 3f, 1.2f);
    }
}
