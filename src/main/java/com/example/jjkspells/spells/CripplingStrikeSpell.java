package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Crippling Strike. Darius grips his axe in both hands and the head glows dark crimson: for the next 4 seconds his
 * next melee attack is a brutal chop that deals bonus damage equal to 500% of his weapon's damage, slows its victim
 * (Slowness III for 3 seconds), batters shields, and staggers the target backward. If it kills an ordinary enemy,
 * half of the remaining cooldown is refunded.
 *
 * Casting only arms the strike; everything that happens on the hit lives in effect/CripplingStrikeEvents.
 */
@AutoSpellConfig
public class CripplingStrikeSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "crippling_strike");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(7)
            .build();

    /** How long the empowered strike waits for a melee attack: 4 seconds. */
    public static final int DURATION_TICKS = 80;
    /** Bonus damage as a multiple of the main-hand weapon's damage (added on top of the normal hit). */
    public static final float DAMAGE_MULT = 5.0f;
    /** Slowness III (amplifier 2) for 3 seconds. */
    public static final int SLOW_TICKS = 60;
    public static final int SLOW_AMPLIFIER = 2;
    /** Durability a blocking shield loses, and how long it is disabled for (like being hit by an axe: 5 seconds). */
    public static final int SHIELD_DURABILITY_DAMAGE = 48;
    public static final int SHIELD_DISABLE_TICKS = 100;
    /** Killing an ordinary (non-boss) enemy with the strike refunds this fraction of the remaining cooldown. */
    public static final float KILL_COOLDOWN_REFUND = 0.5f;

    private static final AnimationHolder GRIP_ANIMATION = SpellAnimLookup.find("ONE_HANDED_HORIZONTAL_SWING_ANIMATION", "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");

    public CripplingStrikeSpell() {
        this.manaCostPerLevel = 3;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 25;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return GRIP_ANIMATION != null ? GRIP_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float bonus = MoonFX.weaponDamage(caster) * DAMAGE_MULT * DariusFX.levelScale(spellLevel);
        return List.of(
                Component.literal("Empowers your next melee attack for " + DURATION_TICKS / 20 + "s"),
                Component.literal("Bonus damage: " + String.format("%.1f", bonus) + " (" + (int) (DAMAGE_MULT * 100) + "% weapon damage)"),
                Component.literal("Slowness III for " + SLOW_TICKS / 20 + "s; heavily damages shields"),
                Component.literal("Killing an ordinary enemy refunds " + (int) (KILL_COOLDOWN_REFUND * 100) + "% of the cooldown")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            // the amplifier carries the spell level to the hit
            entity.addEffect(new MobEffectInstance(ModEffects.CRIPPLING_STRIKE.get(), DURATION_TICKS, spellLevel - 1, false, false, true));
            Vec3 hand = DariusFX.handPos(entity);
            DariusFX.gather(server, hand, 1.8, 14);
            FX.one(server, ModParticles.DR_GLOW.get(), hand.add(0, 0.2, 0), new Vec3(0.8, 0, 0));
            MartialFX.sound(server, entity.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 0.55f);
            MartialFX.sound(server, entity.position(), SoundEvents.FIRECHARGE_USE, 0.7f, 0.6f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
