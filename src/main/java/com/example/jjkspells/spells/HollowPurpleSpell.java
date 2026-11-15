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
 * Hollow Purple. Blue and Red collide and merge into a purple orb that is fired forward,
 * erasing blocks and crushing everything in its path. Size and damage scale with spell level (max 6).
 */
@AutoSpellConfig
public class HollowPurpleSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "hollow_purple");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(240)
            .build();

    /** First contact = spell power * this + FIRST_HIT_PERCENT of max health. */
    // Balance: below Nine Ropes, above Fuga (originally 5.0 / 15% / 2.25; cut by about 82% / 47% / 92%).
    public static final float FIRST_HIT_FACTOR = 1.8f;
    public static final float FIRST_HIT_PERCENT = 0.08f;
    /** Every 6 ticks the orb stays inside a target = spell power * this. */
    public static final float REPEAT_FACTOR = 0.34f;

    public HollowPurpleSpell() {
        this.manaCostPerLevel = 60;
        this.baseSpellPower = 12;
        this.spellPowerPerLevel = 4;
        this.castTime = 15;
        this.baseManaCost = 300;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.RESPAWN_ANCHOR_CHARGE); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.of(SoundEvents.END_GATEWAY_SPAWN); }

    /** Orb radius in blocks: 1.5 at level 1 up to 5.5 at level 6. */
    public float getRadius(int spellLevel) { return 1.5f + 0.8f * (spellLevel - 1); }
    public double getRange(int spellLevel) { return 70 + 10 * spellLevel; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float power = getSpellPower(spellLevel, caster);
        return List.of(
                Component.literal("Orb radius: " + String.format("%.1f", getRadius(spellLevel)) + " blocks"),
                Component.literal("Range: " + (int) getRange(spellLevel) + " blocks"),
                Component.literal("Impact: " + String.format("%.1f", power * FIRST_HIT_FACTOR) + " + "
                        + (int) (FIRST_HIT_PERCENT * 100) + "% max health"),
                Component.literal("Crush: " + String.format("%.1f", power * REPEAT_FACTOR) + " every 0.3s"),
                Component.literal("Erases blocks (see config)")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            HollowPurpleManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
