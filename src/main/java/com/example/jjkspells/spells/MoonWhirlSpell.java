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
 * Moon Breath: Heavenly Crescent Whirl. The caster whips their sword around themselves so fast that a vortex of
 * big purple slashes, each trailing small blue crescents, spirals out around them and cuts everything in the area
 * again and again. Damage per cut is a fraction of the damage of the weapon in the caster's main hand.
 */
@AutoSpellConfig
public class MoonWhirlSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "moon_whirl");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(14)
            .build();

    /** Damage per cut = weapon damage * (DAMAGE_MULT + DAMAGE_MULT_PER_LEVEL * (level - 1)). */
    public static final float DAMAGE_MULT = 0.5f;
    public static final float DAMAGE_MULT_PER_LEVEL = 0.06f;
    /** Radius of the whirl in blocks at level 1, and per extra level. */
    public static final float RADIUS = 5.5f;
    public static final float RADIUS_PER_LEVEL = 0.6f;
    /** Total length of the whirl in ticks, and how many ticks pass between two cuts. */
    public static final int DURATION = 32;
    public static final int CUT_INTERVAL = 4;

    private static final AnimationHolder SWING_ANIMATION = SpellAnimLookup.find("ONE_HANDED_HORIZONTAL_SWING_ANIMATION", "SLASH_ANIMATION");

    public MoonWhirlSpell() {
        this.manaCostPerLevel = 6;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
        this.baseManaCost = 45;
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

    public float getDamageMultiplier(int spellLevel) { return DAMAGE_MULT + DAMAGE_MULT_PER_LEVEL * (spellLevel - 1); }

    /** Number of cuts a target standing in the whirl for its whole length takes. */
    public static int cutCount() { return DURATION / CUT_INTERVAL; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float perCut = MoonFX.weaponDamage(caster) * getDamageMultiplier(spellLevel);
        return List.of(
                Component.literal("Damage: " + String.format("%.1f", perCut) + " x " + cutCount() + " cuts ("
                        + String.format("%.0f", getDamageMultiplier(spellLevel) * 100) + "% of your weapon's damage each)"),
                Component.literal("Radius: " + String.format("%.1f", getRadius(spellLevel)) + " blocks"),
                Component.literal("Lasts " + String.format("%.1f", DURATION / 20f) + "s")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            MoonWhirlManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
