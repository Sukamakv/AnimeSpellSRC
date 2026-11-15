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

public class PrimordialDominionSpell extends DiaSpell {
    public PrimordialDominionSpell() {
        super("primordial_dominion", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return DiaConfig.DOMINION.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.DOMINION.cooldownSeconds.get();
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
                level.addFreshEntity(PrimordialDominionEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("primordial_dominion failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.DOMINION;
        return List.of(
                Component.literal("Weight of Infinity: for 7s you take "+n(c.selfReductionPercent)+"% less damage and cannot be knocked back while a colossal shadow crushes everything within "+n(c.radius)+" blocks"),
                Component.literal("Shockwave "+n(c.shockFlat)+" + "+n(c.shockPercent)+"%, pressure "+n(c.pressureDps)+"/s, final judgment "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("Primordial Terror: ordinary mobs flee and cannot target you for "+n(c.terrorSeconds)+"s. Absolute Suppression: -"+n(c.suppressSlowPercent)+"% speed, -"+n(c.suppressAtkPercent)+"% attack speed, -"+n(c.suppressOutPercent)+"% damage, -"+n(c.suppressHealPercent)+"% healing"));
    }
}
