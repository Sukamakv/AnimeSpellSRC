package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.JJKConfig;
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
 * Hollow Purple: Nine Ropes. The version Gojo used on Sukuna: a long chant while floating, Red and Blue circling
 * him until they merge behind him into one huge Purple that detonates. It erases everything around it (3x Fuga's
 * crater) and costs the caster 10% of their maximum health for 17 seconds. Only one level.
 */
@AutoSpellConfig
public class NineRopesSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "hollow_purple_nine_ropes");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(300)
            .build();

    // Balance: below World Cutting Slash, above Hollow Purple (originally 15.0 / 45%; cut by about 90% / 80%).
    /** Damage at the centre = spell power * this ... */
    public static final float BLAST_FACTOR = 2.8f;
    /** ... plus this fraction of the target's max health. */
    public static final float BLAST_PERCENT = 0.09f;
    /** Backlash: -10% max health for 17 seconds. */
    public static final int BACKLASH_TICKS = 17 * 20;

    public NineRopesSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 32; // same as a level-6 Hollow Purple
        this.spellPowerPerLevel = 0;
        this.castTime = 20;
        this.baseManaCost = 900;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.RESPAWN_ANCHOR_CHARGE); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float power = getSpellPower(spellLevel, caster);
        return List.of(
                Component.literal("Destruction radius: " + JJKConfig.NINE_ROPES_CRATER_RADIUS.get() + " blocks"),
                Component.literal("Blast: " + String.format("%.0f", power * BLAST_FACTOR) + " + "
                        + (int) (BLAST_PERCENT * 100) + "% max health at the centre"),
                Component.literal("Backlash: -10% max health for 17s")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            NineRopesManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
