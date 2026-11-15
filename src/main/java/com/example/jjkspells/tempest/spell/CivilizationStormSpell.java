package com.example.jjkspells.tempest.spell;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class CivilizationStormSpell extends TemSpell {
    public CivilizationStormSpell() {
        super("civilization_storm", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.STORM.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.STORM.cooldownSeconds.get();
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
                level.addFreshEntity(StormEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("civilization_storm failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.STORM;
        return List.of(
                Component.literal("Black storm clouds gather over the aimed point (range "+n(c.range)+"). The first bolt strikes "+n(c.firstRadius)+" blocks for "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% and paralyses ("+n(c.paralysisSlowPercent)+"% slower, "+n(c.paralysisAtkPercent)+"% slower attacks for "+n(c.paralysisSeconds)+"s)"),
                Component.literal("Then "+ni(c.strikeCount)+" lightning strikes of "+n(c.strikeFlat)+" fall across "+n(c.radius)+" blocks (at most "+ni(c.maxHitsPerTarget)+" per enemy) while you take "+n(c.selfReductionPercent)+"% less damage"),
                Component.literal("The final pillar hits "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health). Enemies standing in water take +"+n(c.waterBonusPercent)+"% from lightning"));
    }
}