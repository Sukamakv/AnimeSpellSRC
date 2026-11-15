package com.example.jjkspells.malphite.spell;

import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.entity.*;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.util.Util;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Unstoppable Force - Cataclysmic Impact (ultimate charge) */
public class UnstoppableForceSpell extends MalSpell {
    public UnstoppableForceSpell() {
        super("unstoppable_force", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return MalConfig.FORCE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MalConfig.FORCE.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(ForceEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Unstoppable Force failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = MalConfig.FORCE;
        return List.of(
                Component.literal("Charge up to "+Utils.stringTruncation(c.range.get(), 0)+" blocks, immune to crowd control and taking "+Utils.stringTruncation(c.chargeReductionPercent.get(), 0)+"% less damage"),
                Component.literal("Impact: "+Utils.stringTruncation(c.impactFlat.get(), 0)+" + "+Utils.stringTruncation(c.impactPercent.get(), 0)+"% max HP within "+Utils.stringTruncation(c.impactRadius.get(), 0)+" blocks, launching enemies"),
                Component.literal("Five seismic waves out to "+Utils.stringTruncation(c.seismicRadius.get(), 0)+" blocks, a final explosion, and a fractured battlefield"));
    }
}
