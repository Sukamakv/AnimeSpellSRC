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
 * Rainblade: space around the aimed point is cut three times, each wave stronger, each cut eating the Crimson Knots of what it hits.
 * fx 1 = start {cx,cy,cz, yaw}, 2 = wave {i, enemies hit}, 3 = struck enemy {x,y,z, knots}.
 */
public class BladeEntity extends SeqEntity {
    public BladeEntity(EntityType<? extends BladeEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 160;
    }

    public static BladeEntity create(Level level, LivingEntity caster) {
        var c = AcheronConfig.BLADE;
        Vec3 p = aimCenter(level, caster, c.range.get(), c.radius.get());
        BladeEntity e = new BladeEntity(ModEntities.RAINBLADE.get(), level);
        e.dreamAmt = c.dreams.get();
        e.begin(caster, p, c.radius.get(), 0, Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(7.0);
    }

    private static final double[] WAVE_AT = {2.2, 3.8, 5.4};

    @Override
    protected void run(int age, LivingEntity o) {
        var c = AcheronConfig.BLADE;
        Vec3 p = position();
        if (age < tk(6.2)) {
            Util.LOCKED.add(ownerId);
            freeze(o);
            guard(o, 70);
        } else {
            Util.LOCKED.remove(ownerId);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.RAINBLADE_CHARGE.get(), 1.6f, 1.0f);
            pushFx(1, p.x, p.y, p.z, yaw());
        }
        for (double s = 0.8; s < 5.5; s += 0.45) {
            if (at(age, s) && !(at(age, 2.2) || at(age, 3.8) || at(age, 5.4))) {
                Util.swing(o);
                Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.ACHERON_WEAPON_SWING_02.get(), 0.7f, 1.2f);
            }
        }
        double[] dmg = {c.cut1.get(), c.cut2.get(), c.cut3.get()};
        for (int i = 0; i < 3; i++) {
            if (at(age, WAVE_AT[i])) {
                int n = 0;
                Util.swing(o);
                Util.sound(level(), p.x, p.y, p.z, (i == 2 ? ModSounds.RAINBLADE_FINAL : ModSounds.RAINBLADE_CUT).get(), 1.6f, 1.0f);
                Util.sound(level(), p.x, p.y, p.z, ModSounds.RAINBLADE_FRACTURE.get(), 1.0f, 0.9f + 0.1f * i);
                for (LivingEntity e : foes(o, c.radius.get())) {
                    int k = Nihility.takeKnots(e, c.knotsPerCut.get());
                    strike(o, e, dmg[i], c.percent.get(), 1.0 + k * c.knotBonusPercent.get() / 100.0, 0, 0);
                    if (e.isAlive()) {
                        Util.stagger(e, 10);
                    }
                    track(e);
                    if (n++ < 12) {
                        pushFx(3, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), k);
                    }
                }
                pushFx(2, i, n);
                Util.voidBurst(level(), p.x, p.y + 1, p.z, 3.0, (int) (12 * density()));
            }
        }
        if (at(age, 5.5)) {
            award(o, c.dreams.get());
        }
        if (at(age, 6.3)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.ACHERON_SHEATH.get(), 1.0f, 1.0f);
        }
    }
}
