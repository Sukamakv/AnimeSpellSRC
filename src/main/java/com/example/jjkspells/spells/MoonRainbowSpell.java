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
 * Moon Breath, Sixteenth Form: Moon Rainbow - Half Moon. The caster raises their sword and swings it down again and
 * again; tall curved purple slashes, each wrapped in blue crescents, erupt from the cracking ground all around them
 * and cut upward through everything they pass. Damage per slash is a multiple of the damage of the weapon in the
 * caster's main hand.
 */
@AutoSpellConfig
public class MoonRainbowSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "moon_rainbow");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(22)
            .build();

    /** Damage per slash = weapon damage * (DAMAGE_MULT + DAMAGE_MULT_PER_LEVEL * (level - 1)). */
    public static final float DAMAGE_MULT = 1.2f;
    public static final float DAMAGE_MULT_PER_LEVEL = 0.12f;
    /** Slashes = SLASHES_BASE + SLASHES_PER_LEVEL * level. */
    public static final int SLASHES_BASE = 8;
    public static final int SLASHES_PER_LEVEL = 2;
    /** Radius around the caster that the slashes erupt in, at level 1 and per extra level. */
    public static final float RADIUS = 6.5f;
    public static final float RADIUS_PER_LEVEL = 0.8f;
    /** How close (in blocks) to a slash something must be to get cut by it. */
    public static final double CUT_RANGE = 2.3;
    /** How many ticks before a slash erupts the ground starts cracking. */
    public static final int TELEGRAPH_TICKS = 5;

    private static final AnimationHolder SWING_ANIMATION = SpellAnimLookup.find("OVERHEAD_MELEE_SWING_ANIMATION", "ONE_HANDED_VERTICAL_UPSWING_ANIMATION");

    public MoonRainbowSpell() {
        this.manaCostPerLevel = 8;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 70;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SWING_ANIMATION != null ? SWING_ANIMATION : super.getCastFinishAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    public int getSlashCount(int spellLevel) { return SLASHES_BASE + SLASHES_PER_LEVEL * spellLevel; }

    public float getRadius(int spellLevel) { return RADIUS + RADIUS_PER_LEVEL * (spellLevel - 1); }

    public float getDamageMultiplier(int spellLevel) { return DAMAGE_MULT + DAMAGE_MULT_PER_LEVEL * (spellLevel - 1); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float perSlash = MoonFX.weaponDamage(caster) * getDamageMultiplier(spellLevel);
        return List.of(
                Component.literal(getSlashCount(spellLevel) + " slashes, " + String.format("%.1f", perSlash) + " damage each ("
                        + String.format("%.0f", getDamageMultiplier(spellLevel) * 100) + "% of your weapon's damage)"),
                Component.literal("Radius: " + String.format("%.1f", getRadius(spellLevel)) + " blocks"),
                Component.literal("Launches enemies into the air")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            MoonRainbowManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
