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
 * Malevolent Shrine: an open-barrier domain. For a few seconds, everything
 * inside the radius is hit by Dismantle (fast slashes) and Cleave
 * (slashes that scale with the target's max health).
 */
@AutoSpellConfig
public class MalevolentShrineSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "malevolent_shrine");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(120)
            .build();

    public MalevolentShrineSpell() {
        this.manaCostPerLevel = 30;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;
        this.castTime = 10; // the real charge-up (chant, rising gate) is played by ShrineManager
        this.baseManaCost = 150;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BELL_RESONATE);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WARDEN_SONIC_BOOM);
    }

    public float getRadius(int spellLevel) { return 8 + 2 * spellLevel; }
    public int getDurationTicks(int spellLevel) { return 100 + 20 * spellLevel; }
    /** Dismantle damage = spell power * this. (0.26 gives ~7 per slash where it used to be ~13.4.) */
    public static final float DISMANTLE_FACTOR = 0.26f;

    /** Cleave = this fraction of the target's max health per strike. Level 5 -> ~1.57%, level 10 -> 2.7%. */
    public float cleavePercent(int spellLevel) { return 0.0045f + 0.00225f * spellLevel; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Radius: " + getRadius(spellLevel) + " blocks"),
                Component.literal("Duration: " + (getDurationTicks(spellLevel) / 20) + "s"),
                Component.literal("Dismantle: " + String.format("%.1f", getSpellPower(spellLevel, caster) * DISMANTLE_FACTOR) + " per slash"),
                Component.literal("Cleave: " + String.format("%.2f", cleavePercent(spellLevel) * 100) + "% max health per strike"),
                Component.literal("Blood Slash: " + (int) ShrineManager.SLASH_DAMAGE + " damage, volley every second"),
                Component.literal("Inflicts Bleeding and Irradiated (Alex's Caves): targets cannot heal"),
                Component.literal("Caster gains Simple Domain; Simple Domain holders are immune")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            ShrineManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
