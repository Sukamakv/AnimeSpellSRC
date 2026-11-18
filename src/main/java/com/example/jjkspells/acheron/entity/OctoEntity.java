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
 * Octobolt Flash: the caster flashes to the target in a violet bolt, makes five cuts, lightning strikes everything around, and the
 * last cut ties a Crimson Knot and gives a Slashed Dream.
 * fx 1 = dash {fromx,fromy,fromz, tox,toy,toz, yaw}, 2 = lightning strike {x,y,z}, 3 = final {x,y,z}.
 */
public class OctoEntity extends SeqEntity {
    private UUID target;

    public OctoEntity(EntityType<? extends OctoEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 120;
    }

    public static OctoEntity create(Level level, LivingEntity caster) {
        var c = AcheronConfig.OCTO;
        LivingEntity t = pickTarget(caster, c.range.get());
        Vec3 p = t != null ? t.position() : aimCenter(level, caster, c.range.get(), c.adjacentRadius.get());
        OctoEntity e = new OctoEntity(ModEntities.OCTOBOLT.get(), level);
        e.dreamAmt = c.dreams.get();
        e.begin(caster, p, c.primaryRadius.get(), c.adjacentRadius.get(), Util.yawTo(caster.position(), p));
        e.target = t == null ? null : t.getUUID();
        return e;
    }

    @Override
    public int total() {
        return tk(4.5);
    }

    private static final double[] HITS = {0.75, 1.05, 1.35, 1.65, 1.95};
    private static final double[] WAVES = {1.2, 1.8, 2.4};

    @Override
    protected void run(int age, LivingEntity o) {
        var c = AcheronConfig.OCTO;
        if (age < tk(3.3)) {
            Util.LOCKED.add(ownerId);
            Util.UNTOUCHABLE.add(ownerId);
            freeze(o);
        } else {
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
        }
        if (age == 0) {
            Vec3 f = o.position(), p = position();
            Util.sound(level(), f.x, f.y, f.z, ModSounds.OCTOBOLT_DASH.get(), 1.4f, 1.0f);
            pushFx(1, f.x, f.y, f.z, p.x, p.y, p.z, yaw());
        }
        LivingEntity t = find(target);
        if (at(age, 0.35)) {
            Vec3 tp = t != null ? t.position() : position();
            setPos(tp.x, tp.y, tp.z);
            Vec3 dir = flat(tp.subtract(o.position()));
            Vec3 want = tp.subtract(dir.scale(1.8));
            Vec3 safe = Util.safeNear(level(), o, want, 2.5);
            Util.snapTo(o, safe == null ? want : safe, Util.yawTo(want, tp), 0);
            Util.sound(level(), tp.x, tp.y, tp.z, ModSounds.OCTOBOLT_LIGHTNING.get(), 1.4f, 1.0f);
            Util.sparkBurst(level(), tp.x, tp.y + 1, tp.z, 2.0, (int) (14 * density()));
            SpriteFx.lightning(sv(), tp.add(0, 18, 0), tp.add(0, 0.2, 0), SpriteFx.Palette.VIOLET, 1.2f);
            SpriteFx.impact(sv(), tp.add(0, 1.0, 0), SpriteFx.Palette.VIOLET, 1.8f);
            SpriteFx.cracks(sv(), tp, SpriteFx.Palette.VIOLET, 0.8f);
        }
        Vec3 p = position();
        for (int i = 0; i < HITS.length; i++) {
            if (at(age, HITS[i])) {
                Util.swing(o);
                Util.sound(level(), p.x, p.y, p.z, ModSounds.OCTOBOLT_SLASH.get(), 1.0f, 0.9f + 0.08f * i);
                Vec3 fc = faceCaster(o, p), hc = p.add(0, 1.1, 0);
                SpriteFx.streak(sv(), hc, fc, i * 72f + 25f, SpriteFx.Palette.VIOLET, 3.2f);
                if (i % 2 == 1) {
                    SpriteFx.claws(sv(), hc, fc, i * 40f - 30f, SpriteFx.Palette.WHITE, 3.0f);
                }
                SpriteFx.shards(sv(), hc, SpriteFx.Palette.VIOLET, 1.4f);
                for (LivingEntity e : foes(o, c.primaryRadius.get())) {
                    strike(o, e, c.hitFlat.get(), c.hitPercent.get(), 1.0, 0, 0);
                    track(e);
                }
            }
        }
        for (double w : WAVES) {
            if (at(age, w)) {
                int n = 0;
                Util.sound(level(), p.x, p.y, p.z, ModSounds.OCTOBOLT_LIGHTNING.get(), 1.2f, 1.1f);
                for (LivingEntity e : foes(o, c.adjacentRadius.get())) {
                    if (e.distanceToSqr(p.x, p.y, p.z) <= c.primaryRadius.get() * c.primaryRadius.get()) {
                        continue;
                    }
                    strike(o, e, c.nearbyDamage.get(), 0, 1.0, 0, 0);
                    track(e);
                    Util.sparkBurst(level(), e.getX(), e.getY() + 1, e.getZ(), 1.0, (int) (6 * density()));
                    if (n++ < 8) {
                        pushFx(2, e.getX(), e.getY(), e.getZ());
                        SpriteFx.lightning(sv(), e.position().add(0, 14, 0), e.position().add(0, 0.2, 0), SpriteFx.Palette.VIOLET, 0.8f);
                        SpriteFx.impact(sv(), e.position().add(0, 1.0, 0), SpriteFx.Palette.VIOLET, 1.2f);
                    }
                }
            }
        }
        if (at(age, 2.7)) {
            Util.swing(o);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.OCTOBOLT_FINAL.get(), 1.6f, 1.0f);
            pushFx(3, p.x, p.y, p.z);
            Util.voidBurst(level(), p.x, p.y + 1, p.z, 2.0, (int) (14 * density()));
            SpriteFx.fracture(sv(), p.add(0, 1.1, 0), SpriteFx.Palette.VIOLET, 3.5f);
            SpriteFx.shards(sv(), p.add(0, 1.1, 0), SpriteFx.Palette.CRIMSON, 2.4f);
            SpriteFx.tendrils(sv(), p.add(0, 1.1, 0), SpriteFx.Palette.CRIMSON, 2.2f);
            SpriteFx.ring(sv(), p, SpriteFx.Palette.VIOLET, 0.9f);
            if (t != null && t.isAlive()) {
                Nihility.addKnots(t, c.knots.get(), Nihility.MAX);
                Util.sound(level(), t.getX(), t.getY(), t.getZ(), ModSounds.CRIMSON_KNOT_APPLY.get(), 1.0f, 1.0f);
            }
            award(o, c.dreams.get());
        }
        if (at(age, 3.6)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.ACHERON_SHEATH.get(), 1.0f, 1.0f);
        }
    }
}
