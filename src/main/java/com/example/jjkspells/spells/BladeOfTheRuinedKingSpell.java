package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.*;
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
 * Blade of the Ruined King. Instant cast. Viego lowers his stance, his sword flares emerald, and he lunges 7 blocks
 * (up to 12 to reach the enemy he is aiming at, if any) slashing through everything in his path. Each enemy struck takes
 * 100 damage plus 4% of its maximum health and is slowed for 1.5 seconds. A ghostly green blade trail lingers behind.
 */
@AutoSpellConfig
public class BladeOfTheRuinedKingSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "blade_of_the_ruined_king");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(6.5)
            .build();

    /** Dash length in blocks. */
    public static final float RANGE = 7f;
    /** How far away an enemy can be and still be locked on to (the dash then carries you all the way to it). */
    public static final float LOCK_RANGE = 12f;
    /** Flat damage, plus this fraction of the target's maximum health. */
    public static final float DAMAGE = 100f;
    public static final float MAX_HEALTH_PERCENT = 0.04f;
    /** Slowness II for 1.5 seconds. */
    public static final int SLOW_TICKS = 30;
    public static final int SLOW_AMPLIFIER = 1;

    private static final AnimationHolder SLASH_ANIMATION = SpellAnimLookup.find("SLASH_ANIMATION", "ONE_HANDED_HORIZONTAL_SWING_ANIMATION");

    public BladeOfTheRuinedKingSpell() {
        this.manaCostPerLevel = 4;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 35;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SLASH_ANIMATION != null ? SLASH_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Damage: " + String.format("%.0f", DAMAGE * DariusFX.levelScale(spellLevel)) + " + " + (int) (MAX_HEALTH_PERCENT * 100) + "% of the target's max health"),
                Component.literal("Dash: " + (int) RANGE + " blocks, or up to " + (int) LOCK_RANGE + " to reach the enemy you look at, cutting through everything in the way"),
                Component.literal("Slows struck enemies for " + String.format("%.1f", SLOW_TICKS / 20f) + "s")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            BladeManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
