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

/** Nether Apocalypse - Black Sun of Hell. 7 seconds: spark, black sun, ascension, snap, impact, supernova, final breath. */
public class NetherApocalypseEntity extends SeqEntity {
    public NetherApocalypseEntity(EntityType<? extends NetherApocalypseEntity> type, Level level) {
        super(type, level);
    }

    public static NetherApocalypseEntity create(Level level, LivingEntity caster) {
        var c = DiaConfig.NETHER;
        NetherApocalypseEntity e = new NetherApocalypseEntity(ModEntities.NETHER.get(), level);
        float yaw = caster.getYRot();
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.superRadius.get(), yaw);
        return e;
    }

    @Override
    public int total() {
        return tk(7.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = DiaConfig.NETHER;
        Vec3 p = position();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 3f, 0.6f);
        }
        if (in(age, 0, 0.8) && age % 2 == 0) {
            Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1.2, p.z, 3, 0.6, 0.6, 0.6, 0.02);
        }
        if (in(age, 0.8, 3.0)) {
            if (age % 3 == 0) {
                Util.fx(level(), ModParticles.FLAME.get(), p.x, p.y + 6, p.z, 4, 3, 3, 3, 0.05);
                Util.fx(level(), ModParticles.ASH.get(), p.x, p.y + 2, p.z, 3, c.radius.get() * 0.4, 0.5, c.radius.get() * 0.4, 0.02);
            }
            if (age % 2 == 0) {
                for (LivingEntity e : foes(o, c.gravityRadius.get())) {
                    Util.pull(e, p, c.gravityStrength.get(), 3.0);
                }
            }
        }
        if (at(age, 3.0)) {
            int t = tk(1.5) + 6;
            Util.reduce(o, c.immunityReductionPercent.get() / 100.0, t);
            Util.kbImmune(o, t);
            Util.ccImmune(o, t);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SNAP.get(), 3f, 0.7f);
        }
        if (at(age, 3.5)) {
            for (LivingEntity e : foes(o, c.radius.get())) {
                Util.hurt(o, e, c.impactFlat.get(), c.impactPercent.get());
                Util.launchBlocks(e, 9);
            }
            Util.flameBurst(level(), p.x, p.y, p.z, 10, 160);
            Util.ring(level(), ModParticles.FLAME.get(), p.add(0, 0.4, 0), 48, 1.2, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 4f, 0.6f);
        }
        if (at(age, 4.5)) {
            int t = tk(c.hellfireSeconds.get());
            for (LivingEntity e : foes(o, c.superRadius.get())) {
                Util.hurt(o, e, c.supernovaFlat.get(), c.supernovaPercent.get());
                if (e.isAlive()) {
                    Util.burn(o, e, c.hellfireDps.get(), t);
                    Util.healCut(e, c.hellfireHealCutPercent.get(), t);
                    e.addEffect(new MobEffectInstance(ModEffects.HELLFIRE.get(), t, 0, false, false, true));
                    Util.knock(e, p, 7, 0.25);
                }
            }
            Util.flameBurst(level(), p.x, p.y + 4, p.z, 18, 300);
            Util.ring(level(), ModParticles.EMBER.get(), p.add(0, 0.6, 0), 72, 2.2, 0.08);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ROAR.get(), 5f, 0.5f);
        }
        if (in(age, 4.5, 5.8) && age % 4 == 0) {
            Util.ring(level(), ModParticles.FLAME.get(), p.add(0, 0.5, 0), 36, 1.6, 0.03);
        }
        if (at(age, 5.8)) {
            double ign = c.ignitionBonusPercent.get();
            for (LivingEntity e : foes(o, c.superRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), Util.burning(e) ? 1.0 + ign / 100.0 : 1.0, 1.0);
            }
            Util.flameBurst(level(), p.x, p.y + 2, p.z, 20, 240);
            Util.ring(level(), ModParticles.ASH.get(), p.add(0, 0.5, 0), 72, 2.6, 0.04);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.6f);
        }
    }
}
