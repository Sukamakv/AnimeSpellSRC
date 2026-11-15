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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Za Warudo: Time Stop. The caster chants "Time is mine to control" on a vast golden clock circle, the screen flashes
 * in negative, and time stops for everything except the caster. Level 1 stops time for 4 s, level 5 for 10 s.
 */
@AutoSpellConfig
public class TimeStopSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "time_stop");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(120)
            .build();

    public TimeStopSpell() {
        this.manaCostPerLevel = 100;
        this.baseManaCost = 400;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 0;
        this.castTime = 20; // the real 4 second chant is played by TimeStopManager
    }

    /** Ticks time stays stopped at this level: 4 / 5.5 / 7 / 8.5 / 10 seconds. */
    public static int stopTicks(int spellLevel) {
        int l = Math.max(1, Math.min(5, spellLevel));
        return 80 + (l - 1) * 30;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }
    @Override public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }
    @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Chant: 4 seconds, then time stops"),
                Component.literal("Time stopped for: " + (stopTicks(spellLevel) / 20.0) + " seconds"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            TimeStopManager.start(server, entity, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
