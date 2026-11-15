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
 * Ground Smash. The caster hops, drives a kick into the ground and a shockwave rolls outward from the impact,
 * hurting everything it passes over and throwing it away from the caster. Only things near the ground are hit:
 * a target that is really airborne rides over the wave.
 */
@AutoSpellConfig
public class GroundSmashSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "ground_smash");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(12)
            .build();

    /** Damage = DAMAGE_BASE + spell power * DAMAGE_FACTOR (the far edge of the wave deals up to 35% less). */
    public static final float DAMAGE_BASE = 3f;
    public static final float DAMAGE_FACTOR = 0.6f;
    /** Knockback speed (blocks per tick) right next to the caster and at the far edge, and the hop that goes with it. */
    public static final double KNOCKBACK_NEAR = 1.0;
    public static final double KNOCKBACK_FAR = 0.6;
    public static final double LIFT = 0.45;

    /** Animation: Iron's "stomp" if this version has it (looked up by name so a missing one can never break the build). */
    private static final AnimationHolder SMASH_ANIMATION = lookupAnimation("STOMP", "TOUCH_GROUND_ANIMATION");

    private static AnimationHolder lookupAnimation(String... names) {
        try {
            Class<?> c = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellAnimations");
            for (String n : names) {
                try {
                    Object o = c.getField(n).get(null);
                    if (o instanceof AnimationHolder h) return h;
                } catch (ReflectiveOperationException ignored) {
                    // not in this version of Iron's, try the next name
                }
            }
        } catch (Throwable ignored) {
            // no animation class: fall back to the default cast animation
        }
        return null;
    }

    public GroundSmashSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 30;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SMASH_ANIMATION != null ? SMASH_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    /** Radius of the shockwave in blocks: 5 at level 1, 9 at level 6. */
    public float getRadius(int spellLevel) { return 5f + 0.8f * (spellLevel - 1); }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return DAMAGE_BASE + getSpellPower(spellLevel, caster) * DAMAGE_FACTOR;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Shockwave radius: " + String.format("%.1f", getRadius(spellLevel)) + " blocks"),
                Component.literal("Damage: " + String.format("%.1f", getDamage(spellLevel, caster))),
                Component.literal("Knocks enemies away")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            GroundSmashManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
