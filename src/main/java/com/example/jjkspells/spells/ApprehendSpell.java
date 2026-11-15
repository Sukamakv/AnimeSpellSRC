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
 * Apprehend. Darius plants his feet, pulls his axe behind him and swings it horizontally, throwing out a broad
 * dark-red crescent. Every enemy inside its arc (about 100 degrees wide, 10 blocks long) takes 25 damage, is
 * outlined in red and is dragged up to 6 blocks toward Darius, then slowed (Slowness II, 1.5 seconds) when it lands.
 * Bosses and anything that cannot be moved are not dragged: they just stagger and get slowed.
 */
@AutoSpellConfig
public class ApprehendSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "apprehend");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(12)
            .build();

    /** How far the crescent reaches. */
    public static final float RANGE = 10f;
    /** Half the arc in degrees: 50 on each side of where Darius faces is about 100 degrees. */
    public static final float HALF_ARC_DEGREES = 50f;
    /** Flat damage, not weapon-based (25 damage points = 12.5 hearts). */
    public static final float DAMAGE = 25f;
    /** Enemies are dragged up to this many blocks toward Darius, never closer than MIN_STOP_DISTANCE to him. */
    public static final float PULL_DISTANCE = 6f;
    public static final float MIN_STOP_DISTANCE = 1.8f;
    /** Slowness II for 1.5 seconds. */
    public static final int SLOW_TICKS = 30;
    public static final int SLOW_AMPLIFIER = 1;
    /** Ticks of wind-up before the swing, and ticks of recovery after it. */
    public static final int WINDUP_TICKS = 6;
    public static final int FINISH_TICKS = 6;

    private static final AnimationHolder SWING_ANIMATION = SpellAnimLookup.find("ONE_HANDED_HORIZONTAL_SWING_ANIMATION", "SLASH_ANIMATION");

    public ApprehendSpell() {
        this.manaCostPerLevel = 4;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 35;
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

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Damage: " + String.format("%.1f", DAMAGE * DariusFX.levelScale(spellLevel))),
                Component.literal("Range: " + (int) RANGE + " blocks, about " + (int) (HALF_ARC_DEGREES * 2) + " degree arc"),
                Component.literal("Pulls enemies up to " + (int) PULL_DISTANCE + " blocks toward you"),
                Component.literal("Slowness II for " + String.format("%.1f", SLOW_TICKS / 20f) + "s afterward"),
                Component.literal("Bosses are staggered and slowed instead of pulled")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            ApprehendManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
