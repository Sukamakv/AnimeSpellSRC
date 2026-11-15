package com.example.jjkspells.veldora.spell;

import com.example.jjkspells.veldora.config.VelConfig;
import com.example.jjkspells.veldora.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public class StormDragonsWrathSpell extends VelSpell {
    public StormDragonsWrathSpell() {
        super("storm_dragons_wrath", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return VelConfig.WRATH.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.WRATH.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(WrathEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("storm_dragons_wrath failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.WRATH;
        return List.of(
                Component.literal("Storm Dragon's Wrath: black thunderclouds gather over the aimed point (range "+n(c.range)+") and a colossal cyclone descends. Everything within "+n(c.radius)+" blocks takes "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% max HP, then "+n(c.dps)+"/s while being dragged in and lifted (-"+n(c.slowPercent)+"% speed, -"+n(c.outPercent)+"% damage)"),
                Component.literal("The storm collapses within "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health). Enemies in water take +"+n(c.waterBonusPercent)+"% lightning damage"),
                Component.literal("True Dragon Stability: you take "+n(c.selfReductionPercent)+"% less damage, cannot be knocked back or interrupted"));
    }
}