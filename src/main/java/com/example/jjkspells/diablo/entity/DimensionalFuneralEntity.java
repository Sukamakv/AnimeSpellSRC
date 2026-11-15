package com.example.jjkspells.diablo.entity;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.registry.*;
import com.example.jjkspells.diablo.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Dimensional Funeral - Severing of Reality. fx type 1 = a fracture {x, y, z}. */
public class DimensionalFuneralEntity extends SeqEntity {
    private final Set<UUID> sliced = new HashSet<>();
    private Random rand;
    private double lastU = -1.0E9;

    public DimensionalFuneralEntity(EntityType<? extends DimensionalFuneralEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 30;
    }

    public static DimensionalFuneralEntity create(Level level, LivingEntity caster) {
        var c = DiaConfig.FUNERAL;
        DimensionalFuneralEntity e = new DimensionalFuneralEntity(ModEntities.FUNERAL.get(), level);
        Vec3 p = groundPoint(level, caster, c.range.get());
        float yaw = Util.yawTo(caster.position(), p);
        e.begin(caster, p, c.slashLength.get(), c.collapseRadius.get(), yaw);
        return e;
    }

    @Override
    public int total() {
        return tk(6.5);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = DiaConfig.FUNERAL;
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 7919L + 17);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 3f, 0.6f);
        }
        if (at(age, 0.7)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 4f, 0.5f);
        }
        if (in(age, 0.7, 1.8) && age % 2 == 0) {
            Util.fx(level(), ModParticles.CRACK.get(), p.x, p.y + 12, p.z, 2, 0.4, 12, 0.4, 0);
            Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 12, p.z, 3, 0.6, 12, 0.6, 0.02);
        }
        double L = c.slashLength.get(), W = c.slashWidth.get();
        // the severing slash sweeps along the line through the crack (perpendicular to the look direction)
        double yawRad = Math.toRadians(yaw());
        Vec3 ax = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
        if (age >= tk(1.8) && age <= tk(2.8)) {
            double f = (age - tk(1.8)) / (double) (tk(2.8) - tk(1.8));
            double u = -L / 2 + L * f;
            for (LivingEntity e : foes(o, L / 2 + 6)) {
                Vec3 d = e.position().subtract(p);
                double s = d.dot(ax), perp = Math.abs(d.x * -ax.z + d.z * ax.x);
                if (s >= lastU - 1 && s <= u + 1 && perp <= W / 2 + e.getBbWidth() && Math.abs(d.y) < 22 && sliced.add(e.getUUID())) {
                    strike(o, e, c.slashFlat.get(), c.slashPercent.get(), 1.0, c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
                    if (e.isAlive()) {
                        int t = tk(c.woundSeconds.get());
                        Util.vulnerable(e, c.woundBonusPercent.get(), t);
                        Util.healCut(e, c.woundHealPercent.get(), t);
                        e.addEffect(new MobEffectInstance(ModEffects.WOUND.get(), t, 0, false, false, true));
                    }
                }
            }
            lastU = u;
            Vec3 at = p.add(ax.scale(u));
            Util.fx(level(), ModParticles.STREAK.get(), at.x, at.y + 6, at.z, 6, 0.3, 8, 0.3, 0.1);
            Util.fx(level(), ModParticles.FLASH.get(), at.x, at.y + 2, at.z, 1, 0, 0, 0, 0);
            if (age == tk(1.8)) {
                Util.sound(level(), p.x, p.y, p.z, ModSounds.SNAP.get(), 4f, 0.5f);
            }
        }
        int f0 = tk(2.8), f1 = tk(4.5), n = c.fractureCount.get();
        if (n > 0 && age >= f0 && age < f1 && (age - f0) % Math.max(1, (f1 - f0) / n) == 0 && (age - f0) / Math.max(1, (f1 - f0) / n) < n) {
            Vec3 q = spot(rand, c.collapseRadius.get() * 0.9);
            for (LivingEntity e : Util.enemies(level(), q, 6, o)) {
                strike(o, e, c.fractureFlat.get(), 0, 1.0, c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
            }
            pushFx(1, q.x, q.y, q.z);
            Util.voidBurst(level(), q.x, q.y + 1, q.z, 4, 40);
            Util.sound(level(), q.x, q.y, q.z, ModSounds.CRACKLE.get(), 2.5f, 0.6f);
        }
        if (in(age, 4.5, 5.8) && age % 2 == 0) {
            for (LivingEntity e : foes(o, c.collapseRadius.get())) {
                Util.pull(e, p.add(0, 1, 0), 0.3, 2.5);
            }
        }
        if (at(age, 5.8)) {
            for (LivingEntity e : foes(o, c.collapseRadius.get())) {
                strike(o, e, c.collapseFlat.get(), c.collapsePercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
            }
            Util.voidBurst(level(), p.x, p.y + 2, p.z, 14, 220);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.55f);
        }
    }
}
