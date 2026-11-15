package com.example.jjkspells.frieren.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.AncientReleaseEntity;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
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
 * Frieren - Zoltraak: Ancient Release. 4s charge (the caster is rooted), then a colossal 8-block-wide beam fires for 3s down the
 * line the caster was aiming along, and finishes with a 15-block explosion and a pillar of light.
 *
 * Same hook layout as The Height of Magic: onServerPreCast starts the controller, onCast releases, onServerCastComplete(cancelled)
 * dissolves it.
 */
public class AncientReleaseSpell extends AbstractSpell {
    // ---- tuning ------------------------------------------------------------------------------------------------------
    public static final int MANA = 750;
    public static final double COOLDOWN_SECONDS = 100.0;
    public static final int CAST_TICKS = 4 * 20;
    public static final int BEAM_TICKS = 3 * 20;
    /** Maximum reach measured from the caster. */
    public static final float RANGE = 75f;
    public static final float BEAM_WIDTH = 8f;
    public static final float EXPLOSION_RADIUS = 15f;
    /** The first time the beam touches a target. */
    public static final float INITIAL_FLAT = 500f;
    public static final float INITIAL_PERCENT = 0.20f;
    /** Repeated beam damage, at most once per INTERVAL_TICKS per target. */
    public static final float TICK_FLAT = 120f;
    public static final float TICK_PERCENT = 0.05f;
    public static final int INTERVAL_TICKS = 10;
    /** Final explosion, once per target. */
    public static final float BLAST_FLAT = 400f;
    public static final float BLAST_PERCENT = 0.25f;

    private final ResourceLocation id = JJKSpells.id("frieren_ancient_release");
    private final DefaultConfig defaultConfig;

    public AncientReleaseSpell() {
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(SpellRarity.LEGENDARY)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(0)
                .build();
        this.baseManaCost = 100;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = CAST_TICKS;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return id;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public int getManaCost(int level) {
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) (MANA * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(COOLDOWN_SECONDS * 20.0));
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "FINISH_ANIMATION");
        return h != null ? h : super.getCastFinishAnimation();
    }

    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (level instanceof ServerLevel) {
            try {
                level.addFreshEntity(AncientReleaseEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("frieren_ancient_release failed", t);
            }
        }
        super.onServerPreCast(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        if (cancelled && !level.isClientSide) AncientReleaseEntity.cancelFor(entity);
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            try {
                AncientReleaseEntity.release(level, entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("frieren_ancient_release release failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Charge " + CAST_TICKS / 20 + "s (rooted), beam " + BEAM_TICKS / 20 + "s, " + (int) BEAM_WIDTH + " blocks wide, range " + (int) RANGE),
                Component.literal("Beam: " + (int) INITIAL_FLAT + " + " + (int) (INITIAL_PERCENT * 100) + "% max HP on contact, then " + (int) TICK_FLAT + " + "
                        + (int) (TICK_PERCENT * 100) + "% every " + INTERVAL_TICKS / 20.0 + "s"),
                Component.literal("Final explosion: " + (int) BLAST_FLAT + " + " + (int) (BLAST_PERCENT * 100) + "% max HP in " + (int) EXPLOSION_RADIUS + " blocks"));
    }
}
