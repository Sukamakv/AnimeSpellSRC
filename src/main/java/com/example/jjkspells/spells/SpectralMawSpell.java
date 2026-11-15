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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Spectral Maw. Viego gathers Black Mist around himself for 1 second, then charges forward up to 10 blocks like a
 * spectral beast. He crashes into the first enemy in his path: 90 damage plus 4% of its maximum health, and it is
 * stunned for 2 seconds while mist tendrils wrap around it.
 */
@AutoSpellConfig
public class SpectralMawSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "spectral_maw");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(10)
            .build();

    /** Charge-up: 1 second (set in the constructor). */
    public static final int CHARGE_TICKS = 20;
    /** How far the dash goes if nothing is in the way. */
    public static final float RANGE = 10f;
    public static final float DAMAGE = 90f;
    public static final float MAX_HEALTH_PERCENT = 0.04f;
    /** The spectral bolt thrown forward as the dash begins: it pierces everything in its path. */
    public static final float PROJECTILE_RANGE = 16f;
    public static final float PROJECTILE_DAMAGE = 60f;
    public static final float PROJECTILE_PERCENT = 0.03f;
    /** Stun duration: 2 seconds. */
    public static final int STUN_TICKS = 40;

    private static final AnimationHolder CHARGE_ANIMATION = SpellAnimLookup.find("CHARGE_ANIMATION", "ANIMATION_LONG_CAST");

    public SpectralMawSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = CHARGE_TICKS;
        this.baseManaCost = 45;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return CHARGE_ANIMATION != null ? CHARGE_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Damage: " + String.format("%.0f", DAMAGE * DariusFX.levelScale(spellLevel)) + " + " + (int) (MAX_HEALTH_PERCENT * 100) + "% of the target's max health"),
                Component.literal("Charge: " + CHARGE_TICKS / 20 + "s, then dash up to " + (int) RANGE + " blocks"),
                Component.literal("Stuns the first enemy hit for " + STUN_TICKS / 20 + "s")
        );
    }

    /** The cast begins: Black Mist starts to coil around Viego. */
    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            SpectralMawManager.beginCharge(server, entity);
        }
        super.onServerPreCast(level, spellLevel, entity, playerMagicData);
    }

    /** The cast ended: if it was interrupted, the mist just fades. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        if (cancelled) {
            SpectralMawManager.cancelCharge(entity);
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }

    /** The cast finished: charge. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            SpectralMawManager.startDash(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
