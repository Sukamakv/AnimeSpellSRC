package com.example.jjkspells.acheron.spell;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.entity.*;
import com.example.jjkspells.acheron.util.Nihility;
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

public class TrilateralWiltcrossSpell extends AcheronSpell {
    public TrilateralWiltcrossSpell() {
        super("trilateral_wiltcross", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AcheronConfig.WILT.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AcheronConfig.WILT.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(WiltEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("trilateral_wiltcross failed", t);
            }
        }
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AcheronConfig.WILT;
        return List.of(
                Component.literal("Three violet cuts form a triangle around the target ("+n(c.range)+" blocks), then a cross closes it: "+n(c.cutFlat)+" + "+n(c.cutPercent)+"% max HP per cut, "+n(c.crossFlat)+" + "+n(c.crossPercent)+"% for the cross, within "+n(c.radius)+" blocks. Gives "+ni(c.dreams)+" Slashed Dream"));
    }
}
