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
 * Fuga (Fire Arrow). After a short incantation a bow of flame and a huge arrow form in front of the
 * caster. The arrow is drawn, loosed, and detonates in a colossal fire explosion that tears a crater
 * in the terrain and leaves the ground burning. See FugaManager for the sequence.
 */
@AutoSpellConfig
public class FugaSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "fuga");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(150)
            .build();

    // ---- Damage tuning (multiplied by spell power) ----
    /** Blast damage at the centre = spell power * this. Falls off to 40% at the edge. */
    // Balance: the weakest of the four (originally 1.6 / 2.0 / 12%; cut by about 72% / 85% / 58%).
    public static final float BLAST_FACTOR = 0.9f;
    /** Extra damage for whatever the arrow hits directly = spell power * this ... */
    public static final float DIRECT_FACTOR = 0.6f;
    /** ... plus this fraction of the target's max health. */
    public static final float DIRECT_PERCENT = 0.05f;

    public FugaSpell() {
        this.manaCostPerLevel = 40;
        this.baseSpellPower = 14;
        this.spellPowerPerLevel = 3;
        this.castTime = 20; // the "Kai" incantation; the bow is drawn after this
        this.baseManaCost = 250;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.FIRECHARGE_USE);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BLAZE_SHOOT);
    }

    /** Blast radius in blocks. */
    public float getRadius(int spellLevel) { return 9 + 1.6f * spellLevel; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float power = getSpellPower(spellLevel, caster);
        return List.of(
                Component.literal("Blast radius: " + String.format("%.0f", getRadius(spellLevel)) + " blocks"),
                Component.literal("Blast damage: " + String.format("%.1f", power * BLAST_FACTOR) + " at the centre"),
                Component.literal("Direct hit: +" + String.format("%.1f", power * DIRECT_FACTOR)
                        + " and " + (int) (DIRECT_PERCENT * 100) + "% max health"),
                Component.literal("Sets targets ablaze and destroys terrain")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            FugaManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
