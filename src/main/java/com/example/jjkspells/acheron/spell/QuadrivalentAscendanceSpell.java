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

public class QuadrivalentAscendanceSpell extends AcheronSpell {
    public QuadrivalentAscendanceSpell() {
        super("quadrivalent_ascendance", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AcheronConfig.QUAD.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AcheronConfig.QUAD.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(QuadEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("quadrivalent_ascendance failed", t);
            }
        }
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AcheronConfig.QUAD;
        return List.of(
                Component.literal("Four dashes through a square around the aimed point ("+n(c.range)+" blocks, "+n(c.radius)+" radius), "+n(c.dashFlat)+" + "+n(c.dashPercent)+"% max HP each, then you rise and strike down"),
                Component.literal("Ordinary enemies below "+n(c.executeThresholdPercent)+"% health are executed (others take "+n(c.ordinaryFlat)+" + "+n(c.ordinaryPercent)+"%); elite enemies and bosses take "+n(c.eliteFlat)+" + "+n(c.elitePercent)+"%. Gives "+ni(c.dreams)+" Slashed Dream"));
    }
}
