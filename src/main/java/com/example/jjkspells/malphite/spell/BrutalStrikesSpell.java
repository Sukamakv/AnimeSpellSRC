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

/** Brutal Strikes - Titanic Onslaught (three punches and a smash) */
public class BrutalStrikesSpell extends MalSpell {
    public BrutalStrikesSpell() {
        super("brutal_strikes", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return MalConfig.STRIKES.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MalConfig.STRIKES.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(StrikesEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Brutal Strikes failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = MalConfig.STRIKES;
        return List.of(
                Component.literal("Three heavy strikes ("+Utils.stringTruncation(c.flat1.get(), 0)+", "+Utils.stringTruncation(c.flat2.get(), 0)+", "+Utils.stringTruncation(c.flat3.get(), 0)+" + "+Utils.stringTruncation(c.percent3.get(), 0)+"% max HP) in a "+Utils.stringTruncation(c.coneDegrees.get(), 0)+" degree cone"),
                Component.literal("Titanic Smash: "+Utils.stringTruncation(c.flat4.get(), 0)+" + "+Utils.stringTruncation(c.percent4.get(), 0)+"% max HP, launches and breaks armor"),
                Component.literal("Every hit on the same enemy adds "+Utils.stringTruncation(c.momentumPercent.get(), 0)+"% damage (max "+Utils.stringTruncation(c.momentumMax.get(), 0)+"%); you take "+Utils.stringTruncation(c.reductionPercent.get(), 0)+"% less damage"));
    }
}
