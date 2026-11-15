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

/** Storm Dragon's Wrath - the Heavensplitting Tempest. Centred on the aimed point. No fx events: everything is drawn from the age. */
public class WrathEntity extends SeqEntity {
    public WrathEntity(EntityType<? extends WrathEntity> type, Level level) {
        super(type, level);
    }

    public static WrathEntity create(Level level, LivingEntity caster) {
        var c = VelConfig.WRATH;
        WrathEntity e = new WrathEntity(ModEntities.WRATH.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(9.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VelConfig.WRATH;
        Vec3 p = position();
        double R = c.radius.get();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THUNDER.get(), 5f, 0.5f);
        }
        if (age < tk(8.0) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 0, 1.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 0.2, o.getZ(), 8, 1.2, 0.3, 1.2, 0.15);
        }
        if (in(age, 1.0, 8.0) && age % 4 == 0) {
            Util.fx(level(), ModParticles.FRAGMENT.get(), p.x, p.y + 20, p.z, 8, R * 0.5, 12, R * 0.5, 0.05);
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 40, p.z, 6, R * 0.4, 4, R * 0.4, 0.1);
        }
        if (at(age, 2.5)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.firstFlat.get(), c.firstPercent.get(), water(e, c.waterBonusPercent.get()), 1.0);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 14, 200);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.5, 0), 96, 2.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THUNDER.get(), 6f, 0.6f);
        }
        if (age >= tk(2.5) && age < tk(8.0)) {
            if (age % 2 == 0) {
                double boost = age >= tk(6.5) ? 1.8 : 1.0;
                for (LivingEntity e : foes(o, R)) {
                    double f = Util.isBoss(e) ? c.bossPullPercent.get() / 100.0 : 1.0;
                    Util.pull(e, p.add(0, 3, 0), c.pullStrength.get() * f * boost, 3.0);
                }
            }
            if (age % 10 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    Util.slowPct(e, c.slowPercent.get(), 30);
                    Util.outDamage(e, c.outPercent.get(), 30);
                }
            }
        }
        if (age >= tk(4.0) && age < tk(6.5) && age % 20 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.dps.get(), 0, water(e, c.waterBonusPercent.get()), 1.0);
                if (e.isAlive() && !Util.isBoss(e)) {
                    Util.launchBlocks(e, 1.5);
                }
                track(e);
            }
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VORTEX.get(), 4f, 0.7f);
        }
        if (at(age, 8.0)) {
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()) * water(e, c.waterBonusPercent.get()), 1.0);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 5, p.z, 24, 320);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 144, 3.6, 0.06);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 7f, 0.5f);
        }
    }
}
