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
 * Heartbreaker. Viego dissolves into Black Mist, a ghostly green circle marks his target on the ground, and after
 * a 1.2 second wind-up he reforms in the air above it, sword raised, and plunges down in a devastating strike.
 *
 * The primary target takes 100 damage plus 12% of its maximum health. Enemies within 4 blocks take 70 plus 7% and are
 * knocked back. If the primary target is below 15% health it is executed outright instead.
 *
 * The cast needs a target within 16 blocks: without one it just fails (no mana, no cooldown).
 */
@AutoSpellConfig
public class HeartbreakerSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "heartbreaker");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(100)
            .build();

    /** How far away a target can be chosen. */
    public static final float RANGE = 16f;
    /** Wind-up: 1.2 seconds. */
    public static final int WINDUP_TICKS = 24;
    public static final float PRIMARY_DAMAGE = 100f;
    public static final float PRIMARY_PERCENT = 0.12f;
    public static final float SPLASH_DAMAGE = 70f;
    public static final float SPLASH_PERCENT = 0.07f;
    public static final float SPLASH_RADIUS = 4f;
    /** A primary target with less than this fraction of its health left is executed. */
    public static final float EXECUTE_BELOW = 0.15f;

    private static final AnimationHolder STRIKE_ANIMATION = SpellAnimLookup.find("OVERHEAD_MELEE_SWING_ANIMATION", "ONE_HANDED_VERTICAL_UPSWING_ANIMATION");

    public HeartbreakerSpell() {
        this.manaCostPerLevel = 15;
        this.baseSpellPower = 12;
        this.spellPowerPerLevel = 2;
        this.castTime = WINDUP_TICKS;
        this.baseManaCost = 140;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return STRIKE_ANIMATION != null ? STRIKE_ANIMATION : super.getCastFinishAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float s = DariusFX.levelScale(spellLevel);
        return List.of(
                Component.literal("Target: " + String.format("%.0f", PRIMARY_DAMAGE * s) + " + " + (int) (PRIMARY_PERCENT * 100) + "% max health"),
                Component.literal("Within " + (int) SPLASH_RADIUS + " blocks: " + String.format("%.0f", SPLASH_DAMAGE * s) + " + " + (int) (SPLASH_PERCENT * 100) + "% max health"),
                Component.literal("Executes a target below " + (int) (EXECUTE_BELOW * 100) + "% health"),
                Component.literal("Range: " + (int) RANGE + " blocks, " + String.format("%.1f", WINDUP_TICKS / 20f) + "s wind-up")
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
        HeartbreakerManager.lock(entity, target);
        return true;
    }

    /** The cast begins: Viego starts dissolving, the circle appears under the target. */
    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            HeartbreakerManager.beginWindup(server, entity);
        }
        super.onServerPreCast(level, spellLevel, entity, playerMagicData);
    }

    /** The cast ended: if it was interrupted, Viego simply reforms where he was. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        if (cancelled) {
            HeartbreakerManager.cancelWindup(entity);
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }

    /** The wind-up finished: reappear above the target and plunge. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            HeartbreakerManager.startStrike(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
