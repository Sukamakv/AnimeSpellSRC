package com.example.jjkspells.tempest.entity;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.registry.*;
import com.example.jjkspells.tempest.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Spatial Domination - The Fracture of Infinite Worlds. The entity stands at the target; the caster steps through a portal 10 blocks short of it. fx type 1 = a fracture {x, y, z}. */
public class FractureEntity extends SeqEntity {
    public static final double STEP_BACK = 10.0;
    private final Set<UUID> sliced = new HashSet<>();
    private Random rand;
    private double lastS = -1.0E9;
    private Vec3 dest;

    public FractureEntity(EntityType<? extends FractureEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 30;
    }

    public static FractureEntity create(Level level, LivingEntity caster) {
        var c = TemConfig.FRACTURE;
        FractureEntity e = new FractureEntity(ModEntities.FRACTURE.get(), level);
        Vec3 p = groundPoint(level, caster, c.range.get());
        e.begin(caster, p, c.slashLength.get(), c.collapseRadius.get(), Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(8.0);
    }

    /** Direction the slash travels (the way the caster was facing the target). */
    public static Vec3 dir(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = TemConfig.FRACTURE;
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 6007L + 11);
        }
        Vec3 d = dir(yaw());
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 3f, 0.8f);
        }
        if (at(age, 1.0)) {
            Util.UNTOUCHABLE.add(ownerId);
            Util.LOCKED.add(ownerId);
            o.setInvisible(true);
            Util.fx(level(), ModParticles.CRACK.get(), o.getX(), o.getY() + 1, o.getZ(), 10, 0.4, 0.8, 0.4, 0);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.VOID.get(), 3f, 1.2f);
        }
        if (at(age, 1.9)) {
            Vec3 want = p.subtract(d.scale(STEP_BACK));
            Double g = Util.findGround(level(), want.x, p.y + 8, want.z);
            Vec3 w2 = new Vec3(want.x, g == null ? p.y : g, want.z);
            Vec3 safe = Util.safeNear(level(), o, w2, 5);
            dest = safe == null ? w2 : safe;
            Util.snapTo(o, dest, yaw(), 0);
        }
        if (at(age, 2.0)) {
            o.setInvisible(false);
            Util.UNTOUCHABLE.remove(ownerId);
            Util.fx(level(), ModParticles.CRACK.get(), o.getX(), o.getY() + 1, o.getZ(), 10, 0.4, 0.8, 0.4, 0);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.TEAR.get(), 4f, 0.6f);
        }
        if (age == tk(3.5)) {
            Util.LOCKED.remove(ownerId);
        }
        double L = c.slashLength.get(), W = c.slashWidth.get();
        if (age >= tk(2.0) && age <= tk(3.5)) {
            Vec3 origin = dest != null ? dest : p.subtract(d.scale(STEP_BACK));
            double f = (age - tk(2.0)) / (double) (tk(3.5) - tk(2.0)), u = L * f;
            for (LivingEntity e : Util.enemies(level(), origin.add(d.scale(L / 2)), L / 2 + 8, o)) {
                Vec3 r = e.position().subtract(origin);
                double s = r.dot(d), perp = Math.abs(r.x * d.z - r.z * d.x);
                if (s >= lastS - 1 && s <= u + 1 && perp <= W / 2 + e.getBbWidth() && Math.abs(r.y) < 25 && sliced.add(e.getUUID())) {
                    Util.hurt(o, e, c.slashFlat.get(), c.slashPercent.get(), 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                    track(e);
                    if (e.isAlive()) {
                        int t = tk(c.woundSeconds.get());
                        Util.vulnerable(e, c.woundBonusPercent.get(), t);
                        Util.healCut(e, c.woundHealPercent.get(), t);
                        Util.noTeleport(e, tk(c.noTeleportSeconds.get()));
                        e.addEffect(new MobEffectInstance(ModEffects.WOUND.get(), t, 0, false, false, true));
                    }
                }
            }
            lastS = u;
            Vec3 head = origin.add(d.scale(u));
            Util.fx(level(), ModParticles.STREAK.get(), head.x, head.y + 2, head.z, 8, 0.4, 3, 0.4, 0.2);
            Util.fx(level(), ModParticles.CRACK.get(), head.x, head.y + 3, head.z, 2, 2, 3, 2, 0);
            if (age == tk(2.0)) {
                Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.LASER.get(), 5f, 0.5f);
            }
        }
        int f0 = tk(3.5), f1 = tk(5.5), n = c.fractureCount.get(), step = Math.max(1, (f1 - f0) / Math.max(1, n));
        if (n > 0 && age >= f0 && age < f1 && (age - f0) % step == 0 && (age - f0) / step < n) {
            double ang = (age - f0) / (double) step * Math.PI * 2 / n + rand.nextDouble() * 0.3;
            Vec3 q = p.add(Math.cos(ang) * 9, 0, Math.sin(ang) * 9);
            Double g = Util.findGround(level(), q.x, p.y + 8, q.z);
            q = new Vec3(q.x, g == null ? p.y : g, q.z);
            for (LivingEntity e : Util.enemies(level(), q, 6, o)) {
                Util.hurt(o, e, c.fractureFlat.get(), 0, 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                track(e);
            }
            pushFx(1, q.x, q.y, q.z);
            Util.voidBurst(level(), q.x, q.y + 1, q.z, 4, 40);
            Util.sound(level(), q.x, q.y, q.z, ModSounds.CRACKLE.get(), 2.5f, 0.8f);
        }
        if (in(age, 5.5, 7.0) && age % 2 == 0) {
            for (LivingEntity e : foes(o, c.collapseRadius.get())) {
                Util.pull(e, p.add(0, 2, 0), 0.3, 2.5);
            }
        }
        if (at(age, 7.0)) {
            for (LivingEntity e : foes(o, c.collapseRadius.get())) {
                Util.hurt(o, e, c.collapseFlat.get(), c.collapsePercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
            }
            Util.voidBurst(level(), p.x, p.y + 3, p.z, 16, 240);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.7f);
        }
    }
}
