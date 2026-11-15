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
 * Infernal Chains. Aatrox thrusts out his free hand and hurls a jagged mass of dark red energy. If it hits an enemy
 * within 12 blocks it deals 70 damage, applies alexscaves:irradiated for 20 seconds (if Alex's Caves is installed),
 * slows the target for 2 seconds, and binds it with glowing chains to a cursed circle on the ground.
 *
 * If the target is still inside the circle 1.5 seconds later, the chains drag it to the circle's centre and it is
 * stunned for 3 seconds. Leaving the circle in time snaps the chains. Bosses and anything immovable are slowed
 * heavily instead of pulled and stunned.
 */
@AutoSpellConfig
public class InfernalChainsSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "infernal_chains");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(14)
            .build();

    /** How far the projectile flies. */
    public static final float RANGE = 12f;
    public static final float DAMAGE = 70f;
    /** alexscaves:irradiated for 20 seconds. */
    public static final int IRRADIATED_TICKS = 400;
    /** Slowness II for 2 seconds. */
    public static final int SLOW_TICKS = 40;
    public static final int SLOW_AMPLIFIER = 1;
    /** Radius of the cursed circle, and how long the target has to get out of it: 1.5 seconds. */
    public static final float CIRCLE_RADIUS = 3.2f;
    public static final int ESCAPE_TICKS = 30;
    /** Stun after the pull: 3 seconds. */
    public static final int STUN_TICKS = 60;
    /** Ticks of wind-up before the projectile leaves his hand. */
    public static final int WINDUP_TICKS = 6;

    private static final AnimationHolder THRUST_ANIMATION = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");

    public InfernalChainsSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 55;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return THRUST_ANIMATION != null ? THRUST_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Damage: " + String.format("%.0f", DAMAGE * DariusFX.levelScale(spellLevel)) + ", Irradiated for " + IRRADIATED_TICKS / 20 + "s, slowed for " + SLOW_TICKS / 20 + "s"),
                Component.literal("Range: " + (int) RANGE + " blocks"),
                Component.literal("Stay in the circle for " + String.format("%.1f", ESCAPE_TICKS / 20f) + "s and be dragged to its centre and stunned for " + STUN_TICKS / 20 + "s")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            InfernalChainsManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
