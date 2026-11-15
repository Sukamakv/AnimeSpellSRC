package com.example.jjkspells.yasuo.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yasuo.entity.ReapingCycloneEntity;
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
 * Yasuo - Gale Harvest: Reaping Cyclone.
 * He spins his blade and a vast cyclone follows him for four seconds, dragging every enemy in reach into its spiral and slicing them over and over;
 * the last turn harvests them all and hurls them skyward.
 */
public class ReapingCycloneSpell extends AbstractSpell {
    public static final String NAME = "yasuo_reaping_cyclone";

    // ---- tweak here ----
    public static final double RADIUS = 9.0;
    public static final double HEIGHT = 16.0;
    public static final int DURATION_TICKS = 80;
    public static final int SLICE_INTERVAL = 6;
    public static final double SLICE_FLAT = 28.0;
    public static final double SLICE_PERCENT = 0.012;
    public static final double FINAL_FLAT = 120.0;
    public static final double FINAL_PERCENT = 0.07;
    public static final int MANA_COST = 240;
    public static final double COOLDOWN_SECONDS = 26.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public ReapingCycloneSpell() {
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
                level.addFreshEntity(ReapingCycloneEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yasuo_reaping_cyclone failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("A cyclone " + (int) RADIUS + " blocks wide follows you for " + DURATION_TICKS / 20 + " seconds"),
                Component.literal("Pulls enemies in and slices them every " + SLICE_INTERVAL + " ticks: " + (int) SLICE_FLAT + " + " + SLICE_PERCENT * 100 + "% max HP"),
                Component.literal("The last turn deals " + (int) FINAL_FLAT + " + " + FINAL_PERCENT * 100 + "% max HP and hurls them skyward"));
    }
}
