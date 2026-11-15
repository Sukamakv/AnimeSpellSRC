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

public class WorldDevouringAbyssSpell extends TemSpell {
    public WorldDevouringAbyssSpell() {
        super("world_devouring_abyss", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.ABYSS.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.ABYSS.cooldownSeconds.get();
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
                level.addFreshEntity(AbyssEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("world_devouring_abyss failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.ABYSS;
        return List.of(
                Component.literal("Predator at world scale: a living abyss of slime opens at the aimed point (range "+n(c.range)+"). Within "+n(c.radius)+" blocks: "+n(c.waveFlat)+" + "+n(c.wavePercent)+"% max HP, then "+n(c.dps)+"/s while everything is pulled in (-"+n(c.slowPercent)+"% speed, -"+n(c.outPercent)+"% damage)"),
                Component.literal("Hostile projectiles entering the mass are destroyed; each adds "+n(c.projectileBonus)+" to the final collapse (max +"+n(c.projectileBonusMax)+"). Final collapse within "+n(c.finalRadius)+" blocks: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%"),
                Component.literal("Absolute Predation: ordinary mobs below "+n(c.consumeThresholdPercent)+"% health are consumed. Soul Assimilation: "+n(c.healPercent)+"% max health and mana per enemy defeated (max "+n(c.restoreMaxPercent)+"%)"));
    }
}