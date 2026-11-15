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

public class StormDragonsFistSpell extends VelSpell {
    public StormDragonsFistSpell() {
        super("storm_dragons_fist", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return VelConfig.FIST.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.FIST.cooldownSeconds.get();
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
                level.addFreshEntity(FistEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("storm_dragons_fist failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.FIST;
        return List.of(
                Component.literal("You vanish in lightning and reappear at the target (range "+n(c.range)+"): "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% within "+n(c.radius)+" blocks, then "+ni(c.strikeCount)+" lightning-fast strikes of "+n(c.strikeFlat)+" each"),
                Component.literal("Every strike that lands adds "+n(c.comboBonusPercent)+"% to the final dragon fist (max +"+n(c.comboMaxPercent)+"%): "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% within "+n(c.finalRadius)+" blocks, ignoring "+n(c.armorIgnorePercent)+"% armor"),
                Component.literal("Enemies hit "+ni(c.paralysisHits)+" times are paralysed for "+n(c.paralysisSeconds)+"s. You take "+n(c.selfReductionPercent)+"% less damage and cannot be slowed"));
    }
}