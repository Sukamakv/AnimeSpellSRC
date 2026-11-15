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
 * Domain Expansion: Unlimited Void. A starry black dome opens around the caster. Every enemy inside gets
 * Overload for as long as the domain lasts. The caster is immune. Level 1 lasts 6 seconds, level 6 lasts 11.5.
 */
@AutoSpellConfig
public class UnlimitedVoidSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "unlimited_void");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(120)
            .build();

    public UnlimitedVoidSpell() {
        this.manaCostPerLevel = 100;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 20;
        this.baseManaCost = 500;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.BEACON_ACTIVATE); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    /** Dome radius: 10 blocks at level 1 up to 22.5 at level 6. */
    public float getRadius(int spellLevel) { return 10f + 2.5f * (spellLevel - 1); }

    /** 6.0 s at level 1, +1.1 s per level, 11.5 s at level 6. */
    public int getDurationTicks(int spellLevel) { return Math.round((6.0f + 1.1f * (spellLevel - 1)) * 20f); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Radius: " + String.format("%.1f", getRadius(spellLevel)) + " blocks"),
                Component.literal("Duration: " + String.format("%.1f", getDurationTicks(spellLevel) / 20f) + "s"),
                Component.literal("Enemies inside get Overload"),
                Component.literal("The caster is immune")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            UnlimitedVoidManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
