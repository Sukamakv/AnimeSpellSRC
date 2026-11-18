package com.example.jjkspells.acheron.entity;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.registry.*;
import com.example.jjkspells.acheron.util.Nihility;
import com.example.jjkspells.acheron.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.vfx.sprite.SpriteFx;
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
        // thin cuts of space between the waves
        if (age > tk(0.8) && age < tk(5.6) && age % 5 == 0) {
            java.util.Random cr = new java.util.Random(tickCount * 31L);
            Vec3 cp = p.add((cr.nextDouble() - 0.5) * c.radius.get() * 1.2, 1.5 + cr.nextDouble() * 6.0, (cr.nextDouble() - 0.5) * c.radius.get() * 1.2);
            SpriteFx.streak(sv(), cp, faceCaster(o, p), cr.nextFloat() * 360f, SpriteFx.Palette.WHITE, 3.0f + cr.nextFloat() * 2.5f);
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
                        Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
                        SpriteFx.impact(sv(), ec, SpriteFx.Palette.CRIMSON, 1.5f);
                        SpriteFx.shards(sv(), ec, SpriteFx.Palette.WHITE, 1.2f);
                        if (k > 0) {
                            SpriteFx.tendrils(sv(), ec, SpriteFx.Palette.CRIMSON, 1.4f);
                        }
                    }
                }
                pushFx(2, i, n);
                Util.voidBurst(level(), p.x, p.y + 1, p.z, 3.0, (int) (12 * density()));
                java.util.Random wr = new java.util.Random(tickCount * 17L + i);
                int cuts = i == 2 ? 7 : 5;
                for (int k = 0; k < cuts; k++) {
                    Vec3 cp = p.add((wr.nextDouble() - 0.5) * c.radius.get() * 1.0, 1.5 + wr.nextDouble() * 6.5, (wr.nextDouble() - 0.5) * c.radius.get() * 1.0);
                    SpriteFx.streak(sv(), cp, faceCaster(o, p), wr.nextFloat() * 360f, SpriteFx.Palette.WHITE, 5.0f + i);
                }
                SpriteFx.claws(sv(), p.add(0, 3.0, 0), faceCaster(o, p), 20f + 50f * i, SpriteFx.Palette.CRIMSON, 7.0f + 2f * i);
                SpriteFx.cracks(sv(), p, SpriteFx.Palette.CRIMSON, (float) (c.radius.get() * 2.0 / 7.0) * (0.5f + 0.25f * i));
                SpriteFx.ring(sv(), p, SpriteFx.Palette.CRIMSON, (float) (c.radius.get() / 3.0) * (0.6f + 0.2f * i));
                if (i == 2) {
                    SpriteFx.fracture(sv(), p.add(0, 3.0, 0), SpriteFx.Palette.WHITE, 9.0f);
                }
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
