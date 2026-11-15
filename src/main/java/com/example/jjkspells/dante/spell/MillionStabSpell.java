package com.example.jjkspells.dante.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.dante.entity.MillionStabEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Dante - Rebellion: Million Stab.
 * Dante lunges forward with a Stinger, skewering and carrying every enemy in his path. At the end of the dash he unleashes fourteen
 * lightning fast stabs on everything around him, then a colossal Rebellion plunges from the sky and detonates.
 */
public class MillionStabSpell extends AbstractSpell {
    public static final String NAME = "dante_million_stab";

    // ---- tweak here ----
    public static final double DASH_SPEED = 3.4;
    public static final double DASH_RADIUS = 4.8;
    public static final double STAB_RADIUS = 18.0;
    public static final double FINAL_RADIUS = 24.0;
    public static final double DASH_FLAT = 120.0;
    public static final double DASH_PERCENT = 0.05;
    public static final double STAB_FLAT = 25.0;
    public static final double STAB_PERCENT = 0.012;
    public static final double FINAL_FLAT = 220.0;
    public static final double FINAL_PERCENT = 0.09;
    public static final int MANA_COST = 450;
    public static final double COOLDOWN_SECONDS = 40.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public MillionStabSpell() {
        this.id = JJKSpells.id(NAME);
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(SpellRarity.LEGENDARY)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(0)
                .build();
        this.baseManaCost = 100;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return id;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public int getManaCost(int level) {
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) (MANA_COST * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(COOLDOWN_SECONDS * 20.0));
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "ANIMATION_LONG_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(MillionStabEntity.create(level, entity));
                com.example.jjkspells.dante.entity.StylishRankEntity.notifyDanteCast(entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dante_million_stab failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Stinger dash that skewers and carries every enemy in its path: " + (int) DASH_FLAT + " + " + (int) (DASH_PERCENT * 100) + "% max HP"),
                Component.literal("14 rapid stabs within " + (int) STAB_RADIUS + " blocks: " + (int) STAB_FLAT + " + " + (int) (STAB_PERCENT * 1000) / 10.0 + "% max HP each"),
                Component.literal("A giant Rebellion plunges and detonates: " + (int) FINAL_FLAT + " + " + (int) (FINAL_PERCENT * 100) + "% max HP"));
    }
}
