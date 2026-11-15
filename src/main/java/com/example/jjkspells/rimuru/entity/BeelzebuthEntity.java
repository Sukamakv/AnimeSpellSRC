package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.registry.ModEffects;
import com.example.jjkspells.rimuru.registry.ModEntities;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.registry.ModSpells;
import com.example.jjkspells.rimuru.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Beelzebuth - Soul Consumption: a black vortex that pulls, suppresses and devours souls. */
public class BeelzebuthEntity extends DevourBase {
    private double harvestBonus, harvestHeal;
    private int harvestNeed, harvestTicks;

    public BeelzebuthEntity(EntityType<? extends BeelzebuthEntity> type, Level level) {
        super(type, level);
        tPull = 16;
        tCapture = 60;
        tL0 = 16;
        tL1 = 30;
        tConsumeEnd = 96;
        tFinal = 108;
        tEnd = 120;
    }

    public static BeelzebuthEntity create(Level level, LivingEntity caster, Vec3 point) {
        BeelzebuthEntity e = new BeelzebuthEntity(ModEntities.BEELZEBUTH.get(), level);
        var c = RimConfig.BEELZEBUTH;
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
        e.bossSlow = c.suppressSlowPercent.get();
        e.holdVictims = false;
        e.suppressSlow = c.suppressSlowPercent.get();
        e.suppressOut = c.suppressOutPercent.get();
        e.harvestNeed = c.harvestKills.get();
        e.harvestBonus = c.harvestBonusPercent.get();
        e.harvestTicks = (int) Math.round(c.harvestSeconds.get() * 20);
        e.harvestHeal = c.harvestHeal.get();
        return e;
    }

    @Override
    protected void onStart(LivingEntity caster) {
        Util.sound(level(), caster.getX(), caster.getY(), caster.getZ(), ModSounds.VORTEX.get(), 1.5f, 0.7f);
    }

    /** Soul Harvest: enough souls give a spell damage bonus, reset Predator and heal the caster (once per cast). */
    @Override
    protected void onFinal(LivingEntity caster, int total, int dead) {
        if (dead >= harvestNeed && caster.isAlive()) {
            Util.spellBonus(caster, harvestBonus, harvestTicks);
            Util.heal(caster, harvestHeal);
            Util.refundFraction(caster, ModSpells.PREDATOR.get(), 1.0);
            caster.addEffect(new MobEffectInstance(ModEffects.ADAPT.get(), harvestTicks, 0, false, false, true));
        }
    }
}
