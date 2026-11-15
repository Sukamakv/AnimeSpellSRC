package com.example.jjkspells.velgrynd.entity;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.registry.*;
import com.example.jjkspells.velgrynd.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Flame God Cthugha - the Final Scorching of Creation. Centred on the aimed point; twenty-four seconds. fx type 1 = an energy pillar {x, y, z}. */
public class CthughaEntity extends SeqEntity {
    private boolean ended;
    private Random rand;

    public CthughaEntity(EntityType<? extends CthughaEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 24;
    }

    public static CthughaEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.CTHUGHA;
        CthughaEntity e = new CthughaEntity(ModEntities.CTHUGHA.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.domainRadius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(24.0);
    }

    private void hit(LivingEntity o, double radius, double flat, double pct, boolean mark, double bossBonus) {
        var c = VgrConfig.CTHUGHA;
        for (LivingEntity e : foes(o, radius)) {
            Util.hurt(o, e, flat, pct, bossBonus > 0 ? boss(e, bossBonus) : 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
            if (mark && e.isAlive()) {
                int t = total() - (tickCount - 1) + 40;
                Util.vulnerable(e, c.markBonusPercent.get(), t);
                Util.healCut(e, c.markHealPercent.get(), t);
                e.addEffect(new MobEffectInstance(ModEffects.MARK.get(), t, 0, false, false, true));
            }
            track(e);
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.CTHUGHA;
        Vec3 p = position();
        double D = c.domainRadius.get();
        if (rand == null) {
            rand = new Random(getId() * 99991L + 29);
        }
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 6f, 0.4f);
        }
        if (age < total() - 1 && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (age >= total() - tk(c.lockSeconds.get()) && age < total()) {
            Util.LOCKED.add(ownerId);
            freeze(o);
        }
        if (in(age, 0.0, 2.0) && age % 4 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 0.2, o.getZ(), 8, 1.5, 0.3, 1.5, 0.15);
        }
        if (at(age, 2.0)) {
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 96, 2.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VORTEX.get(), 8f, 0.5f);
        }
        if (in(age, 2.0, 20.0) && age % 6 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 40, p.z, 10, D * 0.5, 10, D * 0.5, 0.05);
            Util.fx(level(), ModParticles.SLIME.get(), p.x, p.y + 1, p.z, 10, D * 0.5, 1, D * 0.5, 0.03);
        }
        if (at(age, 4.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THUNDER.get(), 8f, 0.4f);
        }
        if (in(age, 7.0, 9.0) && age % 3 == 0) {
            Vec3 q = spot(rand, c.radius.get());
            pushFx(1, q.x, q.y, q.z);
            Util.sparkBurst(level(), q.x, q.y + 1, q.z, 5, 70);
            Util.sound(level(), q.x, q.y, q.z, ModSounds.BOOM.get(), 3f, 0.6f + rand.nextFloat() * 0.4f);
        }
        if (at(age, 8.0)) {
            hit(o, c.radius.get(), c.firstFlat.get(), c.firstPercent.get(), true, 0);
            Util.sparkBurst(level(), p.x, p.y + 3, p.z, 22, 300);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 120, 3.2, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 9f, 0.5f);
        }
        if (at(age, 9.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.LASER.get(), 9f, 0.4f);
        }
        if (in(age, 9.5, 12.0) && age % 5 == 0) {
            double ang = (age - tk(9.5)) / (double) tk(2.5) * Math.PI * 1.2 - 0.6 + Math.toRadians(yaw());
            Vec3 q = p.add(Math.cos(ang) * c.radius.get() * 0.5, 0, Math.sin(ang) * c.radius.get() * 0.5);
            Util.sparkBurst(level(), q.x, q.y + 2, q.z, 6, 100);
            Util.fx(level(), ModParticles.CRACK.get(), q.x, q.y + 1, q.z, 4, 2, 1, 2, 0);
        }
        if (at(age, 10.5)) {
            hit(o, c.radius.get(), c.annihilationFlat.get(), c.annihilationPercent.get(), false, 0);
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 30, 400);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 10f, 0.45f);
        }
        if (at(age, 14.0)) {
            hit(o, c.stellarRadius.get(), c.stellarFlat.get(), c.stellarPercent.get(), false, 0);
            Util.sparkBurst(level(), p.x, p.y + 8, p.z, 36, 460);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.8, 0), 144, 3.6, 0.06);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 10f, 0.4f);
        }
        if (age >= tk(15.0) && age < tk(19.0)) {
            if (age % 2 == 0) {
                for (LivingEntity e : foes(o, D)) {
                    Util.pull(e, p.add(0, 5, 0), c.pullStrength.get(), 3.0);
                }
            }
            if (age % 6 == 0) {
                Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 6, p.z, 24, 3, 3, 3, 0.7);
            }
        }
        if (at(age, 17.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 9f, 0.4f);
        }
        if (at(age, 20.5)) {
            hit(o, c.finalRadius.get(), c.finalFlat.get(), c.finalPercent.get(), false, c.bossBonusPercent.get());
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 52, 560);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 160, 4.4, 0.06);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.9, 0), 160, 3.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 12f, 0.4f);
        }
        if (age == total() - 1 && !ended) {
            ended = true;
            int t = tk(c.blessSeconds.get());
            if (dead.size() >= c.endKills.get()) {
                t += tk(c.endExtraSeconds.get());
                Util.refundFraction(o, ModSpells.STAR.get(), 1.0);
                Util.refundFraction(o, ModSpells.PARALLEL.get(), 0.5);
                Util.heal(o, o.getMaxHealth() * c.endHealPercent.get() / 100.0);
                Util.flameBurst(level(), o.getX(), o.getY(), o.getZ(), 6, 80);
            }
            Util.spellBonus(o, c.blessSpellPercent.get(), t);
            Util.MELEE_BONUS.put(ownerId, new double[]{c.blessScorchPercent.get() / 100.0, o.level().getGameTime() + t});
            Util.reduce(o, c.blessReductionPercent.get() / 100.0, t);
            Util.hastePct(o, c.blessSpeedPercent.get(), t);
            Util.kbImmune(o, t);
            o.addEffect(new MobEffectInstance(ModEffects.SOVEREIGN.get(), t, 0, false, false, true));
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
