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

/** Void God Azathoth: the ultimate. Centred on the caster; twenty-two seconds inside an artificial universe. fx type 1 = a cosmic spear {x, y, z}. */
public class AzathothEntity extends SeqEntity {
    private boolean ended;
    private Random rand;

    public AzathothEntity(EntityType<? extends AzathothEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 24;
    }

    public static AzathothEntity create(Level level, LivingEntity caster) {
        var c = TemConfig.AZATHOTH;
        AzathothEntity e = new AzathothEntity(ModEntities.AZATHOTH.get(), level);
        e.begin(caster, caster.position(), c.domainRadius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(22.0);
    }

    private void hit(LivingEntity o, double radius, double flat, double pct, boolean mark, double bossBonus, boolean devour) {
        var c = TemConfig.AZATHOTH;
        for (LivingEntity e : foes(o, radius)) {
            Util.hurt(o, e, flat, pct, bossBonus > 0 ? boss(e, bossBonus) : 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
            if (mark && e.isAlive()) {
                int t = total() - (tickCount - 1) + 40;
                Util.vulnerable(e, c.markBonusPercent.get(), t);
                Util.healCut(e, c.markHealPercent.get(), t);
                e.addEffect(new MobEffectInstance(ModEffects.MARK.get(), t, 0, false, false, true));
            }
            if (devour) {
                devourWeak(o, e, c.consumeThresholdPercent.get());
            } else {
                track(e);
            }
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = TemConfig.AZATHOTH;
        Vec3 p = position();
        double D = c.domainRadius.get(), R = c.radius.get();
        if (rand == null) {
            rand = new Random(getId() * 99991L + 7);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 6f, 0.4f);
        }
        // absolute protection: the last seconds
        if (age >= total() - tk(c.protectSeconds.get()) && age < total()) {
            Util.reduce(o, c.protectReductionPercent.get() / 100.0, 6);
            Util.kbImmune(o, 6);
            Util.ccImmune(o, 6);
            Util.LOCKED.add(ownerId);
            freeze(o);
        }
        if (in(age, 0, 2.0) && age % 4 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 0.2, o.getZ(), 8, 1.5, 0.3, 1.5, 0.15);
        }
        if (at(age, 2.0)) {
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 96, 2.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VORTEX.get(), 6f, 0.5f);
        }
        if (in(age, 2.0, 17.0) && age % 6 == 0) {
            Util.fx(level(), ModParticles.LIGHT.get(), p.x, p.y + 20, p.z, 10, D * 0.5, 14, D * 0.5, 0.01);
        }
        if (at(age, 4.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 6f, 0.5f);
        }
        if (at(age, 6.5)) {
            hit(o, R, c.firstFlat.get(), c.firstPercent.get(), true, 0, false);
            Util.voidBurst(level(), p.x, p.y + 3, p.z, 20, 240);
            Util.ring(level(), ModParticles.LIGHT.get(), p.add(0, 0.6, 0), 120, 3.2, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 6f, 0.5f);
        }
        if (in(age, 6.5, 8.5) && age % 5 == 0) {
            Vec3 q = spot(rand, R);
            pushFx(1, q.x, q.y, q.z);
            Util.flameBurst(level(), q.x, q.y, q.z, 4, 50);
            Util.sound(level(), q.x, q.y, q.z, ModSounds.LASER.get(), 2.5f, 0.6f);
        }
        if (in(age, 8.5, 11.0) && age % 10 == 0) {
            Vec3 q = spot(rand, R);
            Util.sparkBurst(level(), q.x, q.y + 2, q.z, 5, 60);
            Util.fx(level(), ModParticles.CRACK.get(), q.x, q.y + 1, q.z, 4, 1, 1, 1, 0);
        }
        if (at(age, 10.0)) {
            hit(o, R, c.destroyFlat.get(), c.destroyPercent.get(), false, 0, false);
            Util.voidBurst(level(), p.x, p.y + 4, p.z, 28, 300);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 6f, 0.45f);
        }
        if (age >= tk(11.0) && age < tk(14.0)) {
            if (age % 2 == 0) {
                for (LivingEntity e : foes(o, D)) {
                    Util.pull(e, p.add(0, 4, 0), c.pullStrength.get(), 3.0);
                }
            }
            if (age % 6 == 0) {
                Util.fx(level(), ModParticles.SOUL.get(), p.x, p.y + 1, p.z, 18, D * 0.35, 2, D * 0.35, 0.1);
            }
        }
        if (at(age, 13.0)) {
            hit(o, D, c.soulFlat.get(), c.soulPercent.get(), false, 0, true);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SUCTION.get(), 6f, 0.5f);
        }
        if (in(age, 14.0, 17.0) && age % 5 == 0) {
            Util.fx(level(), ModParticles.LIGHT.get(), p.x, p.y + 4, p.z, 30, 2, 2, 2, 0.6);
        }
        if (at(age, 16.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 6f, 0.4f);
        }
        if (at(age, 17.0)) {
            hit(o, c.finalRadius.get(), c.finalFlat.get(), c.finalPercent.get(), false, c.bossBonusPercent.get(), true);
            Util.voidBurst(level(), p.x, p.y + 3, p.z, 44, 480);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 144, 3.8, 0.06);
            Util.ring(level(), ModParticles.LIGHT.get(), p.add(0, 0.9, 0), 144, 2.8, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 8f, 0.4f);
        }
        if (age == total() - 1 && !ended) {
            ended = true;
            int t = tk(c.blessSeconds.get());
            if (dead.size() >= c.endKills.get()) {
                t += tk(c.endExtraSeconds.get());
                Util.refundFraction(o, ModSpells.NOTHING.get(), 1.0);
                Util.refundFraction(o, ModSpells.GLUTTONY.get(), 0.5);
                Util.heal(o, o.getMaxHealth() * c.endHealPercent.get() / 100.0);
                Util.flameBurst(level(), o.getX(), o.getY(), o.getZ(), 6, 80);
            }
            double dmg = c.blessDamagePercent.get();
            Util.spellBonus(o, dmg, t);
            Util.MELEE_BONUS.put(ownerId, new double[]{dmg / 100.0, o.level().getGameTime() + t});
            Util.reduce(o, c.blessReductionPercent.get() / 100.0, t);
            Util.hastePct(o, c.blessSpeedPercent.get(), t);
            Util.kbImmune(o, t);
            o.addEffect(new MobEffectInstance(ModEffects.BLESSING.get(), t, 0, false, false, true));
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
