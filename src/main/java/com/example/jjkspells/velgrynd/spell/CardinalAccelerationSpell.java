package com.example.jjkspells.velgrynd.spell;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.entity.*;
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

public class CardinalAccelerationSpell extends VgrSpell {
    public CardinalAccelerationSpell() {
        super("cardinal_acceleration", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.ACCEL.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.ACCEL.cooldownSeconds.get();
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
                level.addFreshEntity(AccelEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("cardinal_acceleration failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.ACCEL;
        return List.of(
                Component.literal("You vanish and strike the aimed target (range "+n(c.range)+") at light speed: "+n(c.firstFlat)+" + "+n(c.firstPercent)+"%, then "+ni(c.strikeCount)+" strikes of "+n(c.strikeFlat)+" within "+n(c.radius)+" blocks (at most "+ni(c.maxHitsPerTarget)+" per enemy), ignoring "+n(c.armorIgnorePercent)+"% armor"),
                Component.literal("Enemies hit "+ni(c.disorientHits)+" times are slowed "+n(c.disorientSlowPercent)+"% for "+n(c.disorientSeconds)+"s. You take "+n(c.selfReductionPercent)+"% less damage during the assault"),
                Component.literal("Finally you fall from above: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%, +"+n(c.precisionPercent)+"% per strike landed (max +"+n(c.precisionMaxPercent)+"%)"));
    }
}