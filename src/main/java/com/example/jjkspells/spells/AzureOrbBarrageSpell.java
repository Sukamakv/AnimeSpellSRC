package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.AzureOrbProjectile;
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
 * Azure Orb Barrage: the caster flings volleys of spinning blue-fire discs from both hands. Each disc detonates in a
 * large blue fire explosion that removes 5% of the maximum health of everything caught in it.
 * The same attack is used by the Azure Colossus.
 */
@AutoSpellConfig
public class AzureOrbBarrageSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "azure_orb_barrage");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(20)
            .build();

    public static final int ORBS_PER_VOLLEY = 3;
    public static final int VOLLEY_INTERVAL_TICKS = 7;

    public AzureOrbBarrageSpell() {
        this.manaCostPerLevel = 20;
        this.baseManaCost = 120;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 1;
        this.castTime = 20;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.BLAZE_AMBIENT); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public AnimationHolder getCastStartAnimation() { return SpellAnimations.ANIMATION_CHARGED_CAST; }

    /** Level 1-2: 2 volleys, 3-4: 3 volleys, 5: 4 volleys. */
    public int getVolleys(int spellLevel) {
        return 1 + (spellLevel + 1) / 2;
    }

    public float getRadius(int spellLevel) {
        return 5f + 0.5f * spellLevel;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Volleys: " + getVolleys(spellLevel) + " of " + ORBS_PER_VOLLEY + " orbs"),
                Component.literal("Explosion radius: " + getRadius(spellLevel) + " blocks"),
                Component.literal("Damage: " + (int) (AzureOrbProjectile.PERCENT_OF_MAX_HEALTH * 100) + "% of max health per orb caught in")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            AzureBarrage.cast(server, entity, getVolleys(spellLevel), getRadius(spellLevel));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
