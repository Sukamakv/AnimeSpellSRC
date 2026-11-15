package com.example.jjkspells.diablo.spell;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.entity.*;
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

public class DimensionalFuneralSpell extends DiaSpell {
    public DimensionalFuneralSpell() {
        super("dimensional_funeral", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return DiaConfig.FUNERAL.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.FUNERAL.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("SLASH_ANIMATION", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(DimensionalFuneralEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dimensional_funeral failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.FUNERAL;
        return List.of(
                Component.literal("Severing of Reality: a rift opens at the aimed point (range "+n(c.range)+") and a "+n(c.slashLength)+"-block slash cuts across it: "+n(c.slashFlat)+" + "+n(c.slashPercent)+"%"),
                Component.literal("Then "+ni(c.fractureCount)+" dimensional fractures ("+n(c.fractureFlat)+" each) and a collapse within "+n(c.collapseRadius)+" blocks: "+n(c.collapseFlat)+" + "+n(c.collapsePercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction. Dimensional Wounds: +"+n(c.woundBonusPercent)+"% damage taken and -"+n(c.woundHealPercent)+"% healing for "+n(c.woundSeconds)+"s"));
    }
}
