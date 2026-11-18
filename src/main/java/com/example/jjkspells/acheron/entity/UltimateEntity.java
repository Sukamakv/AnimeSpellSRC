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
 * Slashed Dream Cries in Red. A black hole opens in the middle of the zone and pulls in everything within suckRadius; the caster
 * then cuts it thirteen times, each cut hurting everything in the zone for a percent of max HP, and a last huge slash makes it
 * explode and shatter reality.
 * fx 1 = start {cx,cy,cz, yaw}, 2 = slash {i, enemies}, 3 = final {x,y,z}.
 */
public class UltimateEntity extends SeqEntity {
    public static final double HOLE_Y = 6.0;
    public static final double OPEN = 1.5, SLASH_AT = 6.0, SLASH_GAP = 0.4, FINAL_AT = 13.8, END = 18.5;
    public static final int SLASHES = 13;

    private final List<UUID> sucked = new ArrayList<>();
    private final Set<UUID> everSucked = new HashSet<>();

    public UltimateEntity(EntityType<? extends UltimateEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 460;
    }

    public static UltimateEntity create(Level level, LivingEntity caster) {
        var c = AcheronConfig.ULT;
        Vec3 p = groundPoint(level, caster, c.range.get());
        UltimateEntity e = new UltimateEntity(ModEntities.ULTIMATE.get(), level);
        e.begin(caster, p, c.radius.get(), c.finalRadius.get(), Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(END);
    }

    public Vec3 hole() {
        return position().add(0, HOLE_Y, 0);
    }

    private void suppress(LivingEntity o, int ticks) {
        var c = AcheronConfig.ULT;
        for (LivingEntity e : Util.enemies(level(), hole(), c.suckRadius.get(), o)) {
            boolean b = Util.isBoss(e);
            Util.slowPct(e, c.slowPercent.get() * (b ? 0.5 : 1.0), ticks);
            Util.outDamage(e, c.outDamagePercent.get() * (b ? 0.5 : 1.0), ticks);
            Util.effect(e, ModEffects.SUPPRESS.get(), ticks / 20.0, 0);
        }
    }

    private void suck(LivingEntity o, int age) {
        var c = AcheronConfig.ULT;
        Vec3 h = hole();
        if (age % 5 == 0) {
            sucked.clear();
            for (LivingEntity e : Util.enemies(level(), h, c.suckRadius.get(), o)) {
                sucked.add(e.getUUID());
                everSucked.add(e.getUUID());
            }
        }
        double ramp = smooth((age - tk(OPEN)) / (double) tk(4.5));
        double strength = 0.3 + 0.9 * ramp;
        boolean settled = age >= tk(SLASH_AT - 0.5);
        for (UUID id : sucked) {
            LivingEntity e = find(id);
            if (e == null) {
                continue;
            }
            double ph = (id.hashCode() & 0xFFFF) / 65535.0;
            double a = ph * Math.PI * 2 + age * (0.02 + 0.03 * ph);
            double r = settled ? 4.0 + 6.0 * ph : 0.0;
            Vec3 target = h.add(Math.cos(a) * r, (ph - 0.5) * 3.0, Math.sin(a) * r);
            e.setNoGravity(true);
            Util.pull(e, target, strength, 1.2);
            if (age % 10 == 0 && !settled) {
                Util.fx(level(), ModParticles.MIST.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
            }
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = AcheronConfig.ULT;
        Vec3 p = position(), h = hole();
        if (age < tk(16.5)) {
            Util.LOCKED.add(ownerId);
            freeze(o);
            guard(o, c.focusPercent.get());
        } else {
            Util.LOCKED.remove(ownerId);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ACHERON_ULTIMATE_SILENCE.get(), 3.0f, 1.0f);
            pushFx(1, p.x, p.y, p.z, yaw());
            Util.swing(o);
            suppress(o, tk(15.0));
        }
        if (at(age, 0.3) && o.position().distanceTo(p) > 26.0) {
            // the caster stands in front of the black hole
            Vec3 dir = flat(o.position().subtract(p));
            Vec3 want = p.add(dir.scale(22.0));
            Double g = Util.findGround(level(), want.x, o.getY() + 4, want.z);
            Vec3 at = new Vec3(want.x, g == null ? o.getY() : g, want.z);
            Vec3 safe = Util.safeNear(level(), o, at, 3.0);
            Util.snapTo(o, safe == null ? at : safe, Util.yawTo(at, h), 0);
        }
        if (age > 0 && age % 100 == 0 && age < tk(FINAL_AT)) {
            suppress(o, tk(11.0));
        }
        if (at(age, 0.8)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ACHERON_ULTIMATE_SUN.get(), 3.0f, 1.0f);
        }
        if (age >= tk(OPEN) && age < tk(FINAL_AT)) {
            suck(o, age);
        }
        for (int i = 0; i < SLASHES; i++) {
            double w = SLASH_AT + SLASH_GAP * i;
            if (at(age, w - 0.2)) {
                Util.swing(o);
                Util.sound(level(), o.getX(), o.getY(), o.getZ(), (i % 2 == 0 ? ModSounds.ACHERON_WEAPON_SWING_01 : ModSounds.ACHERON_WEAPON_SWING_02).get(), 1.0f, 0.8f);
            }
            if (at(age, w)) {
                int n = 0;
                Util.sound(level(), h.x, h.y, h.z, ModSounds.RAINBLADE_CUT.get(), 3.0f, 0.8f + 0.02f * i);
                if (i % 6 == 0) {
                    Util.sound(level(), h.x, h.y, h.z, ModSounds.ACHERON_ULTIMATE_RAINBLADE.get(), 3.0f, 0.9f);
                }
                for (LivingEntity e : Util.enemies(level(), h, c.radius.get(), o)) {
                    int k = Nihility.takeKnots(e, 1);
                    Util.hurt(o, e, c.slashFlat.get(), c.slashPercent.get(), 1.0 + k * 0.1, Util.isBoss(e) ? 0.5 : 1.0,
                            c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                    if (e.isAlive()) {
                        Util.stagger(e, 8);
                    }
                    track(e);
                    n++;
                }
                pushFx(2, i, n);
                Util.voidBurst(level(), h.x, h.y, h.z, 3.0, (int) (14 * density()));
                java.util.Random ur = new java.util.Random(i * 7919L + 13);
                Vec3 fc = faceCaster(o, h);
                float roll = i * 52f + ur.nextFloat() * 30f;
                SpriteFx.streak(sv(), h.add((ur.nextDouble() - 0.5) * 8, (ur.nextDouble() - 0.5) * 6, (ur.nextDouble() - 0.5) * 8), fc, roll, SpriteFx.Palette.WHITE, 14f);
                SpriteFx.streak(sv(), h, fc, roll + 12f, SpriteFx.Palette.CRIMSON, 10f);
                if (i % 2 == 0) {
                    SpriteFx.claws(sv(), h, fc, roll - 20f, SpriteFx.Palette.CRIMSON, 9f);
                }
                SpriteFx.fracture(sv(), h, SpriteFx.Palette.WHITE, 6f + i * 0.6f);
                SpriteFx.shards(sv(), h, SpriteFx.Palette.WHITE, 4.0f);
                if (i % 3 == 0) {
                    SpriteFx.cracks(sv(), p, SpriteFx.Palette.CRIMSON, (float) (c.radius.get() * 2.0 / 7.0) * (0.35f + 0.05f * i));
                }
            }
        }
        if (at(age, 11.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ACHERON_ULTIMATE_FLOWER.get(), 3.0f, 1.0f);
        }
        if (at(age, 11.6)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ACHERON_ULTIMATE_CHARGE.get(), 3.0f, 1.0f);
            Util.swing(o);
        }
        if (at(age, FINAL_AT)) {
            Util.swing(o);
            Util.sound(level(), h.x, h.y, h.z, ModSounds.RAINBLADE_FINAL.get(), 4.0f, 0.8f);
            Util.sound(level(), h.x, h.y, h.z, ModSounds.ACHERON_ULTIMATE_FLASH.get(), 4.0f, 1.0f);
            pushFx(3, h.x, h.y, h.z);
            for (LivingEntity e : Util.enemies(level(), h, c.finalRadius.get(), o)) {
                int k = Nihility.takeKnots(e, 0);
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), 1.0 + k * 0.1, Util.isBoss(e) ? 0.5 : 1.0,
                        c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                if (e.isAlive()) {
                    Util.stagger(e, 40);
                    Util.knock(e, h, 8.0, 0.8);
                }
                track(e);
            }
            releaseSucked();
            Util.voidBurst(level(), h.x, h.y, h.z, 10.0, (int) (60 * density()));
            Vec3 ffc = faceCaster(o, h);
            SpriteFx.streak(sv(), h, ffc, 32f, SpriteFx.Palette.WHITE, 40f);
            SpriteFx.streak(sv(), h, ffc, -34f, SpriteFx.Palette.CRIMSON, 34f);
            SpriteFx.fracture(sv(), h, SpriteFx.Palette.WHITE, 28f);
            SpriteFx.shards(sv(), h, SpriteFx.Palette.CRIMSON, 22f);
            SpriteFx.shards(sv(), h, SpriteFx.Palette.WHITE, 30f);
            SpriteFx.ring(sv(), p, SpriteFx.Palette.CRIMSON, r2() / 3f);
            SpriteFx.cracks(sv(), p, SpriteFx.Palette.CRIMSON, r2() * 2f / 7f);
            SpriteFx.petals(sv(), h.add(0, -2, 0), SpriteFx.Palette.CRIMSON, 12f);
        }
        if (at(age, 15.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ACHERON_ULTIMATE_AFTERMATH.get(), 2.0f, 1.0f);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.ACHERON_SHEATH.get(), 1.0f, 0.9f);
        }
    }

    private void releaseSucked() {
        for (UUID id : everSucked) {
            LivingEntity e = find(id);
            if (e != null) {
                e.setNoGravity(false);
                Util.FALL_IMMUNE.merge(id, level().getGameTime() + 100, Math::max);
            }
        }
        sucked.clear();
        everSucked.clear();
    }

    @Override
    protected void onFinish(boolean normal) {
        releaseSucked();
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
