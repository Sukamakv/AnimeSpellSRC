package com.example.jjkspells.dante.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.entity.RoyalGuardEntity;
import com.example.jjkspells.dante.entity.StylishRankEntity;
import com.example.jjkspells.spells.SpellAnimLookup;
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
 * Dante - Royal Guard: Release.
 * Dante takes the Royal Guard stance. For up to 8 seconds every hit he receives is absorbed and stored. When the guard ends (or he casts the
 * spell again) all of it is released in a colossal shockwave around him.
 */
public class RoyalGuardSpell extends AbstractSpell {
    public static final String NAME = "dante_royal_guard";

    // ---- tweak here ----
    public static final int GUARD_TICKS = 160;
    public static final double RADIUS = 24.0;
    public static final double BASE_DAMAGE = 150.0;
    public static final double STORED_MULT = 1.8;
    public static final double MAX_STORED = 3000.0;
    public static final double DAMAGE_CAP = 5000.0;
    public static final double PERCENT = 0.07;
    public static final int MANA_COST = 300;
    public static final double COOLDOWN_SECONDS = 35.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public RoyalGuardSpell() {
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
                RoyalGuardEntity.cast(level, entity);
                StylishRankEntity.notifyDanteCast(entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dante_royal_guard failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Guard stance for " + GUARD_TICKS / 20 + " seconds: every hit is absorbed and stored (up to " + (int) MAX_STORED + ")"),
                Component.literal("Release (guard ends or recast): " + (int) BASE_DAMAGE + " + " + STORED_MULT + "x stored damage + " + (int) (PERCENT * 100) + "% max HP in " + (int) RADIUS + " blocks"),
                Component.literal("Maximum release damage: " + (int) DAMAGE_CAP));
    }
}
