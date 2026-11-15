package com.example.jjkspells.frieren.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.HeightOfMagicEntity;
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
 * Frieren - The Height of Magic. A 4-second cast: a vast seal opens under the caster, a miniature star gathers overhead, and on
 * release the star fires a beam at the aimed point. The impact explodes out to 50 blocks.
 *
 * Flow with Iron's hooks: onServerPreCast starts the controller (the cast bar IS the preparation), onCast fires it,
 * onServerCastComplete(cancelled) dissolves it if the cast was interrupted.
 */
public class HeightOfMagicSpell extends AbstractSpell {
    // ---- tuning ------------------------------------------------------------------------------------------------------
    public static final int MANA = 1000;
    public static final double COOLDOWN_SECONDS = 180.0;
    public static final int CAST_TICKS = 4 * 20;
    public static final float RANGE = 45f;
    public static final float DAMAGE_FLAT = 850f;
    public static final float DAMAGE_PERCENT = 0.30f;
    public static final float EXPLOSION_RADIUS = 50f;
    /**
     * Damage multiplier at the very edge of the explosion (linear from 1.0 at the centre). 1.0 = every enemy inside the radius takes
     * the full hit; lower it (e.g. 0.4) if 50 blocks of full damage is too much.
     */
    public static final float EDGE_DAMAGE_MULT = 1.0f;

    private final ResourceLocation id = JJKSpells.id("frieren_height_of_magic");
    private final DefaultConfig defaultConfig;

    public HeightOfMagicSpell() {
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

    /** The cast begins: the seal and the star start forming straight away. */
    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (level instanceof ServerLevel) {
            try {
                level.addFreshEntity(HeightOfMagicEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("frieren_height_of_magic failed", t);
            }
        }
        super.onServerPreCast(level, spellLevel, entity, playerMagicData);
    }

    /** The cast ended: if it was interrupted the gathered magic dissolves. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        if (cancelled && !level.isClientSide) HeightOfMagicEntity.cancelFor(entity);
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }

    /** The cast finished: release toward whatever the caster is aiming at right now. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            try {
                HeightOfMagicEntity.release(level, entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("frieren_height_of_magic release failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Casting time: " + CAST_TICKS / 20 + "s. Range: " + (int) RANGE + " blocks"),
                Component.literal("Damage: " + (int) DAMAGE_FLAT + " + " + (int) (DAMAGE_PERCENT * 100) + "% max HP"),
                Component.literal("Explosion radius: " + (int) EXPLOSION_RADIUS + " blocks"));
    }
}
