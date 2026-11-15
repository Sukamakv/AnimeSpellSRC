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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Oblivion's Descent. Aatrox picks a spot up to 16 blocks away (the enemy he is aiming at, or the ground under his
 * crosshair), then for 1.5 seconds rises and flies above it with his wings fully spread, a circle burning on the ground
 * below. His wings fold inward as red energy gathers along the blade, and he plunges down gripping the hilt with both
 * hands. The impact is a massive crimson shockwave 10 blocks wide, with jagged spikes of red energy, a towering burst
 * at the centre and black-red embers raining down afterwards.
 *
 * Everything within 10 blocks takes 280 damage plus 10% of its max health and is thrown outward. No blocks are
 * changed or destroyed: it is all particles and a server-side damage zone.
 */
@AutoSpellConfig
public class OblivionsDescentSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "oblivions_descent");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(50)
            .build();

    /** How far away the target area can be. */
    public static final float RANGE = 16f;
    /** Wind-up: 1.5 seconds. */
    public static final int WINDUP_TICKS = 30;
    /** Radius of the blast in blocks. */
    public static final float RADIUS = 10f;
    public static final float DAMAGE = 280f;
    public static final float MAX_HEALTH_PERCENT = 0.10f;

    private static final AnimationHolder SLAM_ANIMATION = SpellAnimLookup.find("OVERHEAD_MELEE_SWING_ANIMATION", "ONE_HANDED_VERTICAL_UPSWING_ANIMATION");

    public OblivionsDescentSpell() {
        this.manaCostPerLevel = 15;
        this.baseSpellPower = 12;
        this.spellPowerPerLevel = 2;
        this.castTime = WINDUP_TICKS;
        this.baseManaCost = 150;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SLAM_ANIMATION != null ? SLAM_ANIMATION : super.getCastFinishAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Damage: " + String.format("%.0f", DAMAGE * DariusFX.levelScale(spellLevel)) + " + " + (int) (MAX_HEALTH_PERCENT * 100) + "% max health"),
                Component.literal("Radius: " + (int) RADIUS + " blocks, enemies are thrown outward"),
                Component.literal("Range: " + (int) RANGE + " blocks, " + String.format("%.1f", WINDUP_TICKS / 20f) + "s wind-up")
        );
    }

    /** Runs before the cast starts: needs a spot to come down on, otherwise no cast (no mana, no cooldown). */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (!(level instanceof ServerLevel server)) return true;
        Vec3 spot = null;
        LivingEntity target = GuillotineManager.pickTarget(server, entity, RANGE);
        if (target != null) {
            spot = target.position();
        } else {
            Vec3 eye = entity.getEyePosition();
            var hit = server.clip(new ClipContext(eye, eye.add(entity.getLookAngle().scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.MISS) spot = hit.getLocation();
        }
        if (spot == null) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("No target area in range"), true);
            }
            return false;
        }
        double gy = MoonFX.groundAt(server, spot.x, spot.z, spot.y + 0.5);
        OblivionsDescentManager.lock(entity, new Vec3(spot.x, gy, spot.z));
        return true;
    }

    /** The cast begins: he rises and flies above the marked area. */
    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            OblivionsDescentManager.beginWindup(server, entity);
        }
        super.onServerPreCast(level, spellLevel, entity, playerMagicData);
    }

    /** The cast ended: if it was interrupted, he floats back down safely. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        if (cancelled) {
            OblivionsDescentManager.cancelWindup(entity);
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }

    /** The wind-up finished: plunge. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            OblivionsDescentManager.startPlunge(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
