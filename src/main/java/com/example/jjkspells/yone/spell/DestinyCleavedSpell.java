package com.example.jjkspells.yone.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yone.entity.DestinyCleavedEntity;
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
 * Yone - Ultimate Sundered Sky: Destiny Cleaved.
 * Yone and his spirit split apart and cross two giant slashes over a huge area; everything caught is frozen in place, cut twice and then
 * cleaved along the crossing in a final blast.
 */
public class DestinyCleavedSpell extends AbstractSpell {
    public static final String NAME = "yone_destiny_cleaved";

    // ---- tweak here ----
    public static final double CENTRE_DISTANCE = 14.0;
    public static final double RADIUS = 24.0;
    public static final int WINDUP = 16;
    public static final int GAP = 6;
    public static final int CROSS_DELAY = 8;
    public static final double CUT_FLAT = 120.0;
    public static final double CUT_PERCENT = 0.06;
    public static final double CLEAVE_FLAT = 260.0;
    public static final double CLEAVE_PERCENT = 0.12;
    public static final double AFTER_FLAT = 80.0;
    public static final double AFTER_PERCENT = 0.03;
    public static final int MANA_COST = 600;
    public static final double COOLDOWN_SECONDS = 120.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public DestinyCleavedSpell() {
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
                level.addFreshEntity(DestinyCleavedEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yone_destiny_cleaved failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Yone and his spirit cross two giant slashes over a " + (int) RADIUS + " block radius ahead of you"),
                Component.literal("Two cuts of " + (int) CUT_FLAT + " + " + CUT_PERCENT * 100 + "% max HP freeze everything caught in place"),
                Component.literal("The crossing cleaves for " + (int) CLEAVE_FLAT + " + " + CLEAVE_PERCENT * 100 + "% max HP, then an aftershock of " + (int) AFTER_FLAT + " + " + AFTER_PERCENT * 100 + "%"));
    }
}
