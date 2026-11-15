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
 * Decimate. Darius winds up, then sweeps his axe through a full circle around himself.
 *
 * Spacing matters: enemies caught by the outer blade take 500% of the damage of the weapon in his main hand,
 * enemies too close, caught by the handle, only 250%. Hitting at least one enemy with the outer blade heals 15% of
 * Darius's maximum health, once per cast.
 *
 * It is an instant cast (nothing to interrupt) followed by a fixed 0.7 second swing: 5 ticks of wind-up,
 * 5 of sweep, 4 of recovery.
 */
@AutoSpellConfig
public class DecimateSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "decimate");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(10)
            .build();

    /** Radius of the sweep in blocks at level 1, and per extra level. */
    public static final float RADIUS = 4.0f;
    public static final float RADIUS_PER_LEVEL = 0.25f;
    /** Closer than this to Darius is the handle; this far or farther is the blade. */
    public static final float HANDLE_RADIUS = 2.0f;
    /** Damage as a multiple of the main-hand weapon's damage. */
    public static final float OUTER_MULT = 5.0f;
    public static final float INNER_MULT = 2.5f;
    /** Fraction of max health restored, at most once per cast, if the outer blade hit anything. */
    public static final float HEAL_FRACTION = 0.15f;
    /** Timeline in ticks: 5 + 5 + 4 = 14 ticks = 0.7 seconds. */
    public static final int WINDUP_TICKS = 5;
    public static final int SWEEP_TICKS = 5;
    public static final int RECOVERY_TICKS = 4;
    public static final int TOTAL_TICKS = WINDUP_TICKS + SWEEP_TICKS + RECOVERY_TICKS;

    private static final AnimationHolder SWING_ANIMATION = SpellAnimLookup.find("ONE_HANDED_HORIZONTAL_SWING_ANIMATION", "SLASH_ANIMATION");

    public DecimateSpell() {
        this.manaCostPerLevel = 4;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 40;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SWING_ANIMATION != null ? SWING_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    public float getRadius(int spellLevel) { return RADIUS + RADIUS_PER_LEVEL * (spellLevel - 1); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float w = MoonFX.weaponDamage(caster) * DariusFX.levelScale(spellLevel);
        return List.of(
                Component.literal("Outer blade: " + String.format("%.1f", w * OUTER_MULT) + " (" + (int) (OUTER_MULT * 100) + "% weapon damage)"),
                Component.literal("Inner handle: " + String.format("%.1f", w * INNER_MULT) + " (" + (int) (INNER_MULT * 100) + "% weapon damage)"),
                Component.literal("Radius: " + String.format("%.1f", getRadius(spellLevel)) + " blocks"),
                Component.literal("Heals " + (int) (HEAL_FRACTION * 100) + "% max health if the outer blade hits")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            DecimateManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
