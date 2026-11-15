package com.example.jjkspells.yone.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yone.entity.GhostWalkEntity;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Yone - Soul Unbound: Ghost Walk.
 * His spirit steps out of his body and rushes ahead, steered by where he looks, cutting and marking everything it passes. When it snaps back to the body
 * every marked enemy is torn toward him and ripped, and the return shatters the air around him.
 */
public class GhostWalkSpell extends AbstractSpell {
    public static final String NAME = "yone_ghost_walk";

    // ---- tweak here ----
    public static final int GO_TICKS = 28;
    public static final int RETURN_TICKS = 10;
    public static final double SPEED = 0.85;
    public static final double MAX_RANGE = 26.0;
    public static final double TURN_RATE_DEG = 14.0;
    public static final int CUT_INTERVAL = 4;
    public static final double CUT_RADIUS = 3.4;
    public static final double CUT_FLAT = 30.0;
    public static final double CUT_PERCENT = 0.015;
    public static final double RIP_FLAT = 100.0;
    public static final double RIP_PERCENT = 0.06;
    public static final double SNAP_RADIUS = 6.0;
    public static final double SNAP_FLAT = 70.0;
    public static final double SNAP_PERCENT = 0.04;
    public static final int MANA_COST = 180;
    public static final double COOLDOWN_SECONDS = 16.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public GhostWalkSpell() {
        this.id = JJKSpells.id(NAME);
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
        this.castTime = 0;
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
        return CastType.INSTANT;
    }

    @Override
    public int getManaCost(int level) {
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) (MANA_COST * mult));
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
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "ANIMATION_LONG_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(GhostWalkEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yone_ghost_walk failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Your spirit rushes up to " + (int) MAX_RANGE + " blocks, steered by your aim, while you stand untouchable"),
                Component.literal("It cuts and marks enemies it passes: " + (int) CUT_FLAT + " + " + CUT_PERCENT * 100 + "% max HP every " + CUT_INTERVAL + " ticks"),
                Component.literal("On return marked enemies are torn to you for " + (int) RIP_FLAT + " + " + RIP_PERCENT * 100 + "% max HP, and the shockwave deals " + (int) SNAP_FLAT + " + " + SNAP_PERCENT * 100 + "%"));
    }
}
