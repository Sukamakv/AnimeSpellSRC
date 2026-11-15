package com.example.jjkspells.spells;

import com.example.jjkspells.AzureConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.AzureColossus;
import com.example.jjkspells.entity.AzureOrbProjectile;
import com.example.jjkspells.entity.ColossusBond;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
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
 * Summons the Azure Colossus: a 25-block-tall guardian of blue chakra fire that fights beside the caster.
 * <p>
 * Bond: as long as the colossus is alive and active, the caster takes 85% less damage from every source.
 * The colossus attacks with a sword slash, an orb barrage and a ground-smashing shockwave.
 */
@AutoSpellConfig
public class AzureColossusSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "azure_colossus");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(90)
            .build();

    public AzureColossusSpell() {
        this.manaCostPerLevel = 50;
        this.baseManaCost = 250;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 40;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.WARDEN_SONIC_CHARGE); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public AnimationHolder getCastStartAnimation() { return SpellAnimations.ANIMATION_CHARGED_CAST; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Bond: " + (int) (ColossusBond.REDUCTION * 100) + "% less damage taken from all sources while it lives"),
                Component.literal("Duration: " + AzureConfig.durationTicks(spellLevel) / 20 + "s"),
                Component.literal("Health: " + (int) (AzureConfig.BASE_HEALTH.get() * AzureConfig.healthMult(spellLevel))),
                Component.literal("Sword slash: " + (int) (AzureConfig.SLASH_DAMAGE.get() * AzureConfig.damageMult(spellLevel)) + " damage"),
                Component.literal("Orb barrage: " + (int) (AzureOrbProjectile.PERCENT_OF_MAX_HEALTH * 100) + "% max health per orb"),
                Component.literal("Ground smash: " + (int) (AzureConfig.SMASH_DAMAGE.get() * AzureConfig.damageMult(spellLevel)) + " damage, huge shockwave")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            AzureColossus.summon(server, entity, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
