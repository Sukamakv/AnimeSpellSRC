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

/** Predator - The World-Devouring Abyss. fx type 1 = an absorbed projectile {x, y, z}. */
public class AbyssEntity extends SeqEntity {
    public static final double SPHERE_START = 7.5, SPHERE_BIG = 12.0;
    private int absorbed;

    public AbyssEntity(EntityType<? extends AbyssEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 12;
    }

    public static AbyssEntity create(Level level, LivingEntity caster) {
        var c = TemConfig.ABYSS;
        AbyssEntity e = new AbyssEntity(ModEntities.ABYSS.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(8.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = TemConfig.ABYSS;
        Vec3 p = position();
        Vec3 core = p.add(0, 8, 0);
        double R = c.radius.get();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SLIME.get(), 3f, 0.6f);
        }
        if (at(age, 1.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SUCTION.get(), 4f, 0.7f);
        }
        if (in(age, 1.0, 7.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SLIME.get(), p.x, p.y + 6, p.z, 6, 6, 5, 6, 0.1);
            Util.fx(level(), ModParticles.DROP.get(), p.x, p.y + 3, p.z, 5, R * 0.4, 2, R * 0.4, 0.05);
        }
        // magical consumption: projectiles that enter the mass are destroyed
        if (age >= tk(1.0) && age < tk(7.0) && age % 2 == 0) {
            double sr = age < tk(2.5) ? SPHERE_START : SPHERE_BIG;
            for (Projectile pr : level().getEntitiesOfClass(Projectile.class, new AABB(core, core).inflate(sr + 3))) {
                if (pr.getOwner() == o || pr.position().distanceToSqr(core) > sr * sr) {
                    continue;
                }
                pushFx(1, pr.getX(), pr.getY(), pr.getZ());
                Util.fx(level(), ModParticles.SPARK.get(), pr.getX(), pr.getY(), pr.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
                pr.discard();
                absorbed++;
            }
        }
        if (at(age, 2.5)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.waveFlat.get(), c.wavePercent.get());
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 10, 90);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 4f, 0.8f);
        }
        if (age >= tk(2.5) && age < tk(7.0)) {
            if (age % 2 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    double f = Util.isBoss(e) ? c.bossPullPercent.get() / 100.0 : 1.0;
                    Util.pull(e, p.add(0, 3, 0), c.pullStrength.get() * f, 2.5);
                }
            }
            if (age % 10 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    Util.slowPct(e, c.slowPercent.get(), 30);
                    Util.outDamage(e, c.outPercent.get(), 30);
                    e.addEffect(new MobEffectInstance(ModEffects.DEVOUR.get(), 40, 0, false, false, true));
                }
            }
        }
        if (age >= tk(4.0) && age < tk(6.0) && age % 20 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.dps.get(), 0);
                track(e);
            }
        }
        if (at(age, 6.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VOID.get(), 4f, 0.6f);
        }
        if (at(age, 7.0)) {
            double bonus = Math.min(c.projectileBonusMax.get(), absorbed * c.projectileBonus.get());
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get() + bonus, c.finalPercent.get());
                devourWeak(o, e, c.consumeThresholdPercent.get());
            }
            restoreFromKills(o, c.healPercent.get(), c.manaPercent.get(), c.restoreMaxPercent.get());
            Util.voidBurst(level(), p.x, p.y + 6, p.z, 22, 240);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 1, 0), 96, 2.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.7f);
        }
    }
}
