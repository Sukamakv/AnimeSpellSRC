package com.example.jjkspells.diablo.entity;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.registry.*;
import com.example.jjkspells.diablo.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Temptation King Azazel: the ultimate. Centred on the caster; twenty seconds of judgment ending in the absolute extinction. */
public class TemptationKingAzazelEntity extends SeqEntity {
    private final Set<UUID> dead = new HashSet<>();
    private boolean cataclysm;

    public TemptationKingAzazelEntity(EntityType<? extends TemptationKingAzazelEntity> type, Level level) {
        super(type, level);
    }

    public static TemptationKingAzazelEntity create(Level level, LivingEntity caster) {
        var c = DiaConfig.AZAZEL;
        TemptationKingAzazelEntity e = new TemptationKingAzazelEntity(ModEntities.AZAZEL.get(), level);
        e.begin(caster, caster.position(), c.domainRadius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(20.0);
    }

    private void count(LivingEntity e) {
        if (!e.isAlive()) {
            dead.add(e.getUUID());
        }
    }

    private void hit(LivingEntity o, double radius, double flat, double pct, double bossBonus, boolean mark) {
        var c = DiaConfig.AZAZEL;
        for (LivingEntity e : foes(o, radius)) {
            strike(o, e, flat, pct, bossBonus > 0 ? boss(e, bossBonus) : 1.0, c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
            if (mark && e.isAlive()) {
                int t = total() - Math.max(0, tickCount - 1) + 40;
                Util.vulnerable(e, c.markBonusPercent.get(), t);
                Util.healCut(e, c.markHealPercent.get(), t);
                e.addEffect(new MobEffectInstance(ModEffects.MARK.get(), t, 0, false, false, true));
            }
            count(e);
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = DiaConfig.AZAZEL;
        Vec3 p = position();
        double D = c.domainRadius.get(), R = c.radius.get();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HEARTBEAT.get(), 5f, 0.4f);
        }
        // primordial invulnerability: the last ten seconds
        if (age >= tk(10.0) && age < total()) {
            Util.reduce(o, c.protectReductionPercent.get() / 100.0, 6);
            Util.kbImmune(o, 6);
            Util.ccImmune(o, 6);
            Util.LOCKED.add(ownerId);
            freeze(o);
        }
        if (in(age, 0, 2.0) && age % 5 == 0) {
            Util.fx(level(), ModParticles.ASH.get(), o.getX(), o.getY() + 1, o.getZ(), 10, 1.5, 1.5, 1.5, 0.08);
        }
        if (at(age, 2.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BELL.get(), 5f, 0.4f);
            Util.ring(level(), ModParticles.EMBER.get(), p.add(0, 0.5, 0), 72, 2.0, 0.04);
        }
        if (at(age, 4.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 5f, 0.4f);
        }
        if (in(age, 4.0, 6.0) && age % 4 == 0) {
            Util.fx(level(), ModParticles.FLAME.get(), p.x, p.y + 25, p.z, 8, 6, 6, 6, 0.05);
        }
        if (at(age, 6.0)) {
            hit(o, R, c.firstFlat.get(), c.firstPercent.get(), 0, true);
            Util.flameBurst(level(), p.x, p.y + 2, p.z, 18, 240);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 5f, 0.45f);
        }
        if (at(age, 8.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 5f, 0.4f);
        }
        if (in(age, 8.0, 10.5) && age % 8 == 0) {
            Util.fx(level(), ModParticles.CRACK.get(), p.x, p.y + 6, p.z, 8, R * 0.4, 6, R * 0.4, 0);
        }
        if (at(age, 10.0)) {
            hit(o, R, c.dimFlat.get(), c.dimPercent.get(), 0, false);
            Util.voidBurst(level(), p.x, p.y + 4, p.z, 24, 260);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.5f);
        }
        if (age >= tk(10.5) && age < tk(13.0)) {
            if (age % 2 == 0) {
                for (LivingEntity e : foes(o, D)) {
                    Util.pull(e, p, c.pullStrength.get(), 3.0);
                }
            }
            if (age % 6 == 0) {
                Util.fx(level(), ModParticles.SOUL.get(), p.x, p.y + 1, p.z, 16, D * 0.35, 2, D * 0.35, 0.1);
            }
        }
        if (at(age, 12.5)) {
            for (LivingEntity e : foes(o, D)) {
                strike(o, e, c.soulFlat.get(), c.soulPercent.get(), 1.0, c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
                if (e.isAlive() && Util.consume(o, e, c.executeThresholdPercent.get(), c.bossSoul.get())) {
                    Util.fx(level(), ModParticles.SOUL.get(), e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.6, 0.3, 0.1);
                }
                count(e);
            }
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ROAR.get(), 5f, 0.5f);
        }
        if (in(age, 13.0, 16.0) && age % 5 == 0) {
            Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1, p.z, 24, 1.5, 1.5, 1.5, 0.5);
        }
        if (at(age, 16.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SNAP.get(), 5f, 0.35f);
        }
        if (at(age, 17.0)) {
            hit(o, c.finalRadius.get(), c.finalFlat.get(), c.finalPercent.get(), c.bossBonusPercent.get(), false);
            Util.voidBurst(level(), p.x, p.y + 3, p.z, 40, 400);
            Util.ring(level(), ModParticles.FLAME.get(), p.add(0, 0.5, 0), 120, 3.5, 0.06);
            Util.ring(level(), ModParticles.SOUL.get(), p.add(0, 0.8, 0), 120, 2.6, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 6f, 0.4f);
        }
        if (age == total() - 1) {
            int t = tk(c.authSeconds.get());
            if (!cataclysm && dead.size() >= c.cataclysmKills.get()) {
                cataclysm = true;
                t += tk(c.cataclysmExtraSeconds.get());
                Util.refundFraction(o, ModSpells.DESPAIR.get(), 1.0);
                Util.refundFraction(o, ModSpells.WORLD.get(), 0.5);
                Util.heal(o, o.getMaxHealth() * c.cataclysmHealPercent.get() / 100.0);
                Util.flameBurst(level(), o.getX(), o.getY(), o.getZ(), 6, 80);
            }
            Util.spellBonus(o, c.authSpellPercent.get(), t);
            Util.reduce(o, c.authReductionPercent.get() / 100.0, t);
            Util.hastePct(o, c.authSpeedPercent.get(), t);
            Util.kbImmune(o, t);
            o.addEffect(new MobEffectInstance(ModEffects.AUTHORITY.get(), t, 0, false, false, true));
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
