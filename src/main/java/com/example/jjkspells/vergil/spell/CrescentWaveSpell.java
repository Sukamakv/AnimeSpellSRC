package com.example.jjkspells.vergil.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.vergil.entity.CrescentWaveEntity;
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
 * Vergil - Yamato Draw: Crescent Wave.
 * A colossal crescent of cut space (40 blocks wide) sweeps forward 84 blocks, cutting everything it crosses. Every enemy it cuts is marked:
 * about 1.2 seconds later the cut catches up and slices them again.
 */
public class CrescentWaveSpell extends AbstractSpell {
    public static final String NAME = "vergil_crescent_wave";

    // ---- tweak here ----
    /** Half width of the wave, travel distance and speed (blocks per tick). */
    public static final double HALF_WIDTH = 20.0;
    public static final double DISTANCE = 84.0;
    public static final double SPEED = 3.0;
    public static final double WAVE_FLAT = 150.0;
    public static final double WAVE_PERCENT = 0.06;
    public static final double DELAYED_FLAT = 100.0;
    public static final double DELAYED_PERCENT = 0.05;
    public static final int DELAY_TICKS = 24;
    public static final int MANA_COST = 400;
    public static final double COOLDOWN_SECONDS = 35.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public CrescentWaveSpell() {
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
                level.addFreshEntity(CrescentWaveEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("vergil_crescent_wave failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Crescent wave " + (int) (HALF_WIDTH * 2) + " blocks wide, " + (int) DISTANCE + " blocks long"),
                Component.literal("Wave cut: " + (int) WAVE_FLAT + " + " + (int) (WAVE_PERCENT * 100) + "% max HP"),
                Component.literal("Cut catches up after " + (DELAY_TICKS / 20.0) + "s: " + (int) DELAYED_FLAT + " + " + (int) (DELAYED_PERCENT * 100) + "% max HP"));
    }
}
