package com.example.jjkspells.spells;

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
 * The Ruler of Death's Crimson Wrath. The caster chants "Death is mine to command" while countless crimson eyes open
 * in the sky and look down on them. A vast red-and-black circle then forms where the caster aims (up to 100 blocks
 * away), and for 8 seconds red lasers rain on its centre. Everything inside the circle loses 10% of its max health
 * every second. One level only.
 */
@AutoSpellConfig
public class CrimsonWrathSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "crimson_wrath");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(300)
            .build();

    /** How far away the caster can aim, in blocks. */
    public static final double RANGE = 100.0;
    /** Radius of the magic circle, in blocks. */
    public static final float RADIUS = 26f;
    /** How long the lasers rain, in ticks (8 seconds). */
    public static final int RAIN_TICKS = 160;
    /** Fraction of max health lost per second by everything inside the circle while the lasers fall. */
    public static final float DPS_PERCENT = 0.10f;

    public CrimsonWrathSpell() {
        this.manaCostPerLevel = 0;
        this.baseManaCost = 900;
        this.baseSpellPower = 20;
        this.spellPowerPerLevel = 0;
        this.castTime = 20; // the real chant and charge are played by CrimsonWrathManager
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.WARDEN_ROAR); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Range: " + (int) RANGE + " blocks"),
                Component.literal("Circle radius: " + (int) RADIUS + " blocks"),
                Component.literal("Duration: " + (RAIN_TICKS / 20) + "s of crimson lasers"),
                Component.literal("Damage: " + (int) (DPS_PERCENT * 100) + "% max health per second to everything inside")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            CrimsonWrathManager.start(server, entity, this);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
