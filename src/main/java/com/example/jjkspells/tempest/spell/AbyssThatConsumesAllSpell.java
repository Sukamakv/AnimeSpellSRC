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

public class AbyssThatConsumesAllSpell extends TemSpell {
    public AbyssThatConsumesAllSpell() {
        super("abyss_that_consumes_all", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.GLUTTONY.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.GLUTTONY.cooldownSeconds.get();
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
                level.addFreshEntity(GluttonyEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("abyss_that_consumes_all failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.GLUTTONY;
        return List.of(
                Component.literal("A black vortex opens above the aimed point (range "+n(c.range)+"): "+n(c.openFlat)+" + "+n(c.openPercent)+"% within "+n(c.radius)+" blocks, then "+n(c.dps)+"/s while everything is dragged in (-"+n(c.slowPercent)+"% speed, -"+n(c.outPercent)+"% damage)"),
                Component.literal("The collapse hits "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%. Ordinary mobs below "+n(c.consumeThresholdPercent)+"% health are devoured; each gives you "+n(c.healPercent)+"% max health and mana (max "+n(c.restoreMaxPercent)+"%)"),
                Component.literal("Survivors get Soul Fracture: +"+n(c.fractureVulnPercent)+"% damage taken and -"+n(c.fractureHealPercent)+"% healing for "+n(c.fractureSeconds)+"s"));
    }
}