package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.registry.ModEffects;
import com.example.jjkspells.rimuru.registry.ModEntities;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Predator - Gluttonous Devourer: a slime sphere that captures, consumes and compresses everything near its target. */
public class PredatorEntity extends DevourBase {
    public PredatorEntity(EntityType<? extends PredatorEntity> type, Level level) {
        super(type, level);
        tPull = 20;
        tCapture = 30;
        tL0 = 8;
        tL1 = 16;
        tConsumeEnd = 50;
        tFinal = 52;
        tEnd = 60;
    }

    public static PredatorEntity create(Level level, LivingEntity caster, Vec3 point) {
        PredatorEntity e = new PredatorEntity(ModEntities.PREDATOR.get(), level);
        var c = RimConfig.PREDATOR;
        e.init(caster, point, c.radius.get());
        e.pullStrength = c.pullStrength.get();
        e.initFlat = c.initialFlat.get();
        e.initPct = c.initialPercent.get();
        e.dps = c.dps.get();
        e.finFlat = c.finalFlat.get();
        e.finPct = c.finalPercent.get();
        e.threshold = c.executeThresholdPercent.get();
        e.bossBonus = c.bossBonus.get();
        e.healPer = c.healPerEnemy.get();
        e.manaPer = c.manaPerEnemy.get();
        e.healCap = c.healCap.get();
        e.bossSlow = c.bossSlowPercent.get();
        e.holdVictims = true;
        return e;
    }

    @Override
    protected void onStart(LivingEntity caster) {
        Util.sound(level(), caster.getX(), caster.getY(), caster.getZ(), ModSounds.SLIME.get(), 1.3f, 0.8f);
    }

    /** Adaptive Consumption: a kill makes the caster tougher and stronger for a few seconds (never stacking). */
    @Override
    protected void onFinal(LivingEntity caster, int total, int dead) {
        if (dead > 0 && caster.isAlive()) {
            var c = RimConfig.PREDATOR;
            int ticks = (int) Math.round(c.adaptSeconds.get() * 20);
            Util.reduce(caster, c.adaptReductionPercent.get() / 100.0, ticks);
            Util.spellBonus(caster, c.adaptBonusPercent.get(), ticks);
            caster.addEffect(new MobEffectInstance(ModEffects.ADAPT.get(), ticks, 0, false, false, true));
        }
    }
}
