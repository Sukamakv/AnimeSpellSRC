package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Tengai Shinsei (Heavenly Star Falling). Madara's meteor technique: a colossal meteor, 40 blocks wide, is called
 * down from the sky onto the spot the caster aims at and lands ten seconds later in a shockwave. Three seconds after
 * that a second meteor falls onto the first, and when the two collide they detonate in a nuclear-style explosion.
 * See TengaiShinseiManager for the sequence. One level only.
 */
@AutoSpellConfig
public class TengaiShinseiSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "tengai_shinsei");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(300)
            .build();

    // ---- Tuning ----------------------------------------------------------------------------------------------
    /** How far away the caster can aim, in blocks. */
    public static final double RANGE = 128.0;
    /** Radius of each meteor (so they are 40 blocks wide). */
    public static final float METEOR_RADIUS = 20f;
    /** The first meteor lands this many ticks after the cast (10 seconds). */
    public static final int FALL1_TICKS = 200;
    /** The second meteor starts to fall this many ticks after the first has landed (3 seconds). */
    public static final int GAP_TICKS = 60;
    /** The second meteor takes this long to land on the first (5 seconds: it falls faster). */
    public static final int FALL2_TICKS = 100;
    /** Height above the landing spot the meteors fall from. */
    public static final double SKY_HEIGHT = 240.0;
    /** Radius of the first meteor's landing shockwave. */
    public static final double SHOCK_RADIUS = 48.0;
    /** Radius of the final explosion's damage. */
    public static final double NUKE_RADIUS = 72.0;

    public TengaiShinseiSpell() {
        this.manaCostPerLevel = 0;
        this.baseManaCost = 800;
        this.baseSpellPower = 20;
        this.spellPowerPerLevel = 0;
        this.castTime = 40; // the chant; the meteors are called when it ends
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.ENDER_DRAGON_GROWL); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    // ---- Config readers (safe to call before the config file has loaded) ----------------------------------------
    public static float meteorDamage() {
        try {
            return JJKConfig.TENGAI_METEOR_DAMAGE.get().floatValue();
        } catch (IllegalStateException e) {
            return 95f;
        }
    }

    public static float nukePercent() {
        try {
            return JJKConfig.TENGAI_NUKE_HEALTH_PERCENT.get().floatValue();
        } catch (IllegalStateException e) {
            return 0.40f;
        }
    }

    public static int meteorCraterRadius() {
        try {
            return JJKConfig.TENGAI_METEOR_CRATER_RADIUS.get();
        } catch (IllegalStateException e) {
            return 22;
        }
    }

    public static int nukeCraterRadius() {
        try {
            return JJKConfig.TENGAI_NUKE_CRATER_RADIUS.get();
        } catch (IllegalStateException e) {
            return 36;
        }
    }

    public static boolean breaksBlocks() {
        try {
            return JJKConfig.breaks(JJKConfig.TENGAI_BREAKS_BLOCKS);
        } catch (IllegalStateException e) {
            return true;
        }
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Range: " + (int) RANGE + " blocks"),
                Component.literal("Meteor: " + (int) (METEOR_RADIUS * 2) + " blocks wide, lands after " + (FALL1_TICKS / 20) + "s"),
                Component.literal("Landing shockwave: " + (int) meteorDamage() + " damage"),
                Component.literal("Second meteor falls " + (GAP_TICKS / 20) + "s later"),
                Component.literal("Final explosion: " + (int) (nukePercent() * 100) + "% max health")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            TengaiShinseiManager.start(server, entity, this);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
