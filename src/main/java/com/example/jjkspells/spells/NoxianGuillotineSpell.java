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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Noxian Guillotine. Darius locks onto an enemy (a crimson mark appears over its head, red energy gathers along his
 * axe and the world goes muffled), leaps, and crashes down on it with a vertical chop.
 *
 * Damage is 500% of the damage of the weapon in his main hand; against a wounded target it rises to 750% below 30%
 * health and 800% below 15%. If the strike kills an ordinary (non-boss) enemy the spell's cooldown is reset at once;
 * any kill makes Darius roar and raise his axe.
 *
 * The cast needs a target: if there is none in range the cast simply fails (no mana, no cooldown).
 */
@AutoSpellConfig
public class NoxianGuillotineSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "noxian_guillotine");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(50)
            .build();

    /** How far away a target can be locked. */
    public static final float RANGE = 8f;
    /** Damage as a multiple of the main-hand weapon's damage: normal, below 30% health, below 15% health. */
    public static final float BASE_MULT = 5.0f;
    public static final float WOUNDED_MULT = 7.5f;
    public static final float EXECUTE_MULT = 8.0f;
    public static final float WOUNDED_BELOW = 0.30f;
    public static final float EXECUTE_BELOW = 0.15f;

    private static final AnimationHolder CHOP_ANIMATION = SpellAnimLookup.find("OVERHEAD_MELEE_SWING_ANIMATION", "ONE_HANDED_VERTICAL_UPSWING_ANIMATION");

    public NoxianGuillotineSpell() {
        this.manaCostPerLevel = 8;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 80;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return CHOP_ANIMATION != null ? CHOP_ANIMATION : super.getCastFinishAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    /** Damage multiplier for a target with this fraction of its health left. */
    public static float multiplierFor(float healthFraction) {
        if (healthFraction < EXECUTE_BELOW) return EXECUTE_MULT;
        if (healthFraction < WOUNDED_BELOW) return WOUNDED_MULT;
        return BASE_MULT;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float w = MoonFX.weaponDamage(caster) * DariusFX.levelScale(spellLevel);
        return List.of(
                Component.literal("Damage: " + String.format("%.1f", w * BASE_MULT) + " (" + (int) (BASE_MULT * 100) + "% weapon damage)"),
                Component.literal("Below " + (int) (WOUNDED_BELOW * 100) + "% health: " + (int) (WOUNDED_MULT * 100) + "%; below "
                        + (int) (EXECUTE_BELOW * 100) + "%: " + (int) (EXECUTE_MULT * 100) + "%"),
                Component.literal("Range: " + (int) RANGE + " blocks"),
                Component.literal("Killing an ordinary enemy resets the cooldown")
        );
    }

    /** Runs before the cast starts: no target in range means no cast, so no mana or cooldown is spent. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (!(level instanceof ServerLevel server)) return true;
        LivingEntity target = GuillotineManager.pickTarget(server, entity, RANGE);
        if (target == null) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("No target in range"), true);
            }
            return false;
        }
        GuillotineManager.lock(entity, target);
        return true;
    }

    /** The cast begins: lock-on mark, gathering energy, muffled world. */
    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            GuillotineManager.beginWindup(server, entity);
        }
        super.onServerPreCast(level, spellLevel, entity, playerMagicData);
    }

    /** The cast ended: if it was interrupted, drop the lock-on. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        if (cancelled) {
            GuillotineManager.cancelWindup(entity);
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }

    /** The cast finished: leap. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            GuillotineManager.startLeap(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
