package com.example.jjkspells.acheron.entity;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.registry.*;
import com.example.jjkspells.acheron.util.Nihility;
import com.example.jjkspells.acheron.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Trilateral Wiltcross: three violet cuts drawn as a triangle around the target, then the closing cross.
 * fx 1 = start {x,y,z, yaw}.
 */
public class WiltEntity extends SeqEntity {
    public WiltEntity(EntityType<? extends WiltEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 80;
    }

    public static WiltEntity create(Level level, LivingEntity caster) {
        var c = AcheronConfig.WILT;
        LivingEntity t = pickTarget(caster, c.range.get());
        Vec3 p = t != null ? t.position() : aimCenter(level, caster, c.range.get(), c.radius.get());
        WiltEntity e = new WiltEntity(ModEntities.WILTCROSS.get(), level);
        e.dreamAmt = AcheronConfig.WILT.dreams.get();
        e.begin(caster, p, c.radius.get(), 0, Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(2.4);
    }

    private static final double[] CUTS = {0.45, 0.85, 1.25};

    @Override
    protected void run(int age, LivingEntity o) {
        var c = AcheronConfig.WILT;
        Vec3 p = position();
        guard(o, 50);
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.ACHERON_WEAPON_SWING_01.get(), 1.0f, 1.0f);
            pushFx(1, p.x, p.y, p.z, yaw());
        }
        for (int i = 0; i < CUTS.length; i++) {
            if (at(age, CUTS[i] - 0.1)) {
                Util.swing(o);
                Util.sound(level(), o.getX(), o.getY(), o.getZ(), (i % 2 == 0 ? ModSounds.ACHERON_WEAPON_SWING_02 : ModSounds.ACHERON_WEAPON_SWING_01).get(), 1.0f, 1.0f + 0.1f * i);
            }
            if (at(age, CUTS[i])) {
                Util.sound(level(), p.x, p.y, p.z, ModSounds.ACHERON_VIOLET_IMPACT.get(), 1.0f, 0.9f + 0.1f * i);
                for (LivingEntity e : foes(o, c.radius.get())) {
                    strike(o, e, c.cutFlat.get(), c.cutPercent.get(), 1.0, 0, 0);
                    track(e);
                    Util.fx(level(), ModParticles.ARC_V.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 3, 0.3, 0.4, 0.3, 0.05);
                }
                Util.sparkBurst(level(), p.x, p.y + 1.0, p.z, 1.2, (int) (6 * density()));
            }
        }
        if (at(age, 1.7)) {
            Util.swing(o);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.WILTCROSS_CROSS.get(), 1.4f, 1.0f);
            for (LivingEntity e : foes(o, c.radius.get())) {
                strike(o, e, c.crossFlat.get(), c.crossPercent.get(), 1.0, 0, 0);
                if (e.isAlive()) {
                    Util.stagger(e, 8);
                    Util.knock(e, p, 1.2, 0.2);
                }
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 1.0, p.z, 2.0, (int) (14 * density()));
            award(o, c.dreams.get());
        }
    }
}
