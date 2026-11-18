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
 * Stygian Resurge: a black sun gathers over the aimed point, falls, and everything in its radius is struck, clearing its knots; a
 * wider shockwave follows.
 * fx 1 = start {cx,cy,cz, yaw}, 2 = impact {x,y,z}, 3 = shockwave {x,y,z}.
 */
public class StygianEntity extends SeqEntity {
    public StygianEntity(EntityType<? extends StygianEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 200;
    }

    public static StygianEntity create(Level level, LivingEntity caster) {
        var c = AcheronConfig.STYX;
        Vec3 p = aimCenter(level, caster, c.range.get(), c.radius.get() * 0.5);
        StygianEntity e = new StygianEntity(ModEntities.STYGIAN.get(), level);
        e.dreamAmt = c.dreams.get();
        e.begin(caster, p, c.radius.get(), c.shockRadius.get(), Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(8.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = AcheronConfig.STYX;
        Vec3 p = position();
        if (age < tk(5.5)) {
            Util.LOCKED.add(ownerId);
            freeze(o);
            guard(o, 80);
        } else {
            Util.LOCKED.remove(ownerId);
        }
        if (age == 0) {
            Util.swing(o);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.STYGIAN_CHARGE.get(), 2.0f, 1.0f);
            pushFx(1, p.x, p.y, p.z, yaw());
        }
        if (at(age, 3.4)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.STYGIAN_DESCENT.get(), 2.0f, 1.0f);
            SpriteFx.beam(sv(), p.add(0, 30, 0), p.add(0, 0.1, 0), SpriteFx.Palette.WHITE, 1.6f, 14);
        }
        if (at(age, 4.2)) {
            Util.swing(o);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.STYGIAN_IMPACT.get(), 3.0f, 1.0f);
            pushFx(2, p.x, p.y, p.z);
            for (LivingEntity e : foes(o, c.radius.get())) {
                int k = Nihility.takeKnots(e, 0);
                Util.hurt(o, e, c.flat.get(), c.percent.get(), 1.0 + k * c.knotBonusPercent.get() / 100.0, Util.isBoss(e) ? 0.5 : 1.0);
                if (e.isAlive()) {
                    Util.stagger(e, 20);
                    Util.knock(e, p, 3.0, 0.5);
                }
                track(e);
            }
            Util.voidBurst(level(), p.x, p.y + 1, p.z, 6.0, (int) (40 * density()));
            java.util.Random sr = new java.util.Random(tickCount * 13L);
            SpriteFx.cracks(sv(), p, SpriteFx.Palette.CRIMSON, r1() * 2f / 7f);
            SpriteFx.ring(sv(), p, SpriteFx.Palette.CRIMSON, r1() / 3f);
            SpriteFx.fracture(sv(), p.add(0, 3.0, 0), SpriteFx.Palette.WHITE, 8.0f);
            SpriteFx.smoke(sv(), p.add(0, 1.5, 0), SpriteFx.Smoke.VOID, 5.0f);
            for (int k = 0; k < 6; k++) {
                double ang = sr.nextDouble() * Math.PI * 2, rr = 2.0 + sr.nextDouble() * r1() * 0.5;
                Vec3 sp = p.add(Math.cos(ang) * rr, 0.8, Math.sin(ang) * rr);
                SpriteFx.shards(sv(), sp, k % 2 == 0 ? SpriteFx.Palette.CRIMSON : SpriteFx.Palette.WHITE, 2.0f);
            }
        }
        if (at(age, 4.6)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.STYGIAN_SHOCKWAVE.get(), 3.0f, 1.0f);
            pushFx(3, p.x, p.y, p.z);
            SpriteFx.ring(sv(), p, SpriteFx.Palette.WHITE, r2() / 3f);
            SpriteFx.cracks(sv(), p, SpriteFx.Palette.CRIMSON, r2() * 2f / 7f);
            double inner = c.radius.get();
            for (LivingEntity e : foes(o, c.shockRadius.get())) {
                if (e.distanceToSqr(p.x, p.y, p.z) <= inner * inner) {
                    continue;
                }
                Util.hurt(o, e, c.shockFlat.get(), c.shockPercent.get(), 1.0, Util.isBoss(e) ? 0.5 : 1.0);
                if (e.isAlive()) {
                    Util.knock(e, p, 4.0, 0.3);
                }
                track(e);
            }
        }
        if (at(age, 5.0)) {
            SpriteFx.petals(sv(), p.add(0, 4.0, 0), SpriteFx.Palette.CRIMSON, 6.0f);
            award(o, c.dreams.get());
        }
        if (at(age, 5.6)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.ACHERON_SHEATH.get(), 1.0f, 1.0f);
        }
    }
}
