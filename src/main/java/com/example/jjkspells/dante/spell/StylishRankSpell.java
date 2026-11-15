package com.example.jjkspells.dante.spell;

import com.example.jjkspells.JJKSpells;
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
 * Dante - Stylish Rank.
 * A style meter (D, C, B, A, S, SS, SSS) appears beside the caster. Every Dante spell cast raises it by one rank. At S a 7 second timer starts;
 * when it ends the caster unleashes a bullet hell around himself (SS: bigger and stronger), or, at SSS, a massive Hellfire Slash.
 * Without any Dante spell for 10 seconds the spell turns itself off.
 */
public class StylishRankSpell extends AbstractSpell {
    public static final String NAME = "dante_stylish_rank";

    // ---- tweak here ----
    public static final double HELL_RADIUS_S = 7.0;
    public static final double HELL_RADIUS_SS = 15.0;
    public static final double SS_DPS_MULT = 1.3;
    public static final double HELL_DPS_FLAT = 60.0;
    public static final double HELL_DPS_PERCENT = 0.03;
    public static final int HELL_HIT_INTERVAL = 5;
    public static final double HELL_HEIGHT = 4.0;
    public static final double HELL_DEPTH = 2.0;
    public static final double SLASH_LENGTH = 110.0;
    public static final double SLASH_RADIUS = 8.0;
    public static final double SLASH_FLAT = 700.0;
    public static final double SLASH_PERCENT = 0.25;
    /** false: SSS = Hellfire Slash only. true: the slash is followed by the SS sized bullet hell. */
    public static final boolean SSS_ALSO_BULLET_HELL = false;
    public static final int MANA_COST = 250;
    public static final double COOLDOWN_SECONDS = 30.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public StylishRankSpell() {
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
                StylishRankEntity.cast(level, entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dante_stylish_rank failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Every Dante spell cast raises the style rank by one (D to SSS); 10 seconds without one turns it off"),
                Component.literal("Rank S starts a 7 second timer, then a bullet hell of " + (int) HELL_RADIUS_S + " blocks radius for 9 seconds"),
                Component.literal("Rank SS in time: " + (int) HELL_RADIUS_SS + " blocks radius and " + (int) ((SS_DPS_MULT - 1) * 100) + "% more damage"),
                Component.literal("Rank SSS in time: a massive Hellfire Slash instead: " + (int) SLASH_FLAT + " + " + (int) (SLASH_PERCENT * 100) + "% max HP"));
    }
}
