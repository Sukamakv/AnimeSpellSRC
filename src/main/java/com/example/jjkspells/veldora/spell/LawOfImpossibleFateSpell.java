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

public class LawOfImpossibleFateSpell extends VelSpell {
    public LawOfImpossibleFateSpell() {
        super("law_of_impossible_fate", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VelConfig.CHAOS.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.CHAOS.cooldownSeconds.get();
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
                level.addFreshEntity(ChaosEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("law_of_impossible_fate failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.CHAOS;
        return List.of(
                Component.literal("Chaos King Nyarlathotep: a probability domain of "+n(c.radius)+" blocks opens at the aimed point (range "+n(c.range)+") for 12 seconds ("+n(c.firstFlat)+" + "+n(c.firstPercent)+"% when it opens). Enemies inside are slowed by "+n(c.slowPercent)+"%, deal "+n(c.outPercent)+"% less damage and heal "+n(c.healPercent)+"% less"),
                Component.literal("Inside it you take "+n(c.selfReductionPercent)+"% less damage; "+n(c.negatePercent)+"% of ordinary attacks miss you, "+n(c.bonusChancePercent)+"% of your attacks deal +"+n(c.bonusDamagePercent)+"%, and "+n(c.redirectPercent)+"% of hostile projectiles are redirected"),
                Component.literal("Twelve impossible strikes ("+n(c.strikeFlat)+" each) tear through the domain, then it collapses for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"));
    }
}