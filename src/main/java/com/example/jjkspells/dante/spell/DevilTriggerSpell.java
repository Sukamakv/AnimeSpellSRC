package com.example.jjkspells.dante.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.entity.DevilTriggerEntity;
import com.example.jjkspells.dante.entity.StylishRankEntity;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Dante - Devil Trigger: Crimson Awakening.
 * Dante awakens his devil blood: a crimson shockwave tears out, flame wings unfold behind him and a burning aura surrounds him for 25 seconds.
 * He hits harder, takes less damage, heals and moves faster; the aura burns everything close and every 4 seconds a Devil Burst blasts the area.
 */
public class DevilTriggerSpell extends AbstractSpell {
    public static final String NAME = "dante_devil_trigger";

    // ---- tweak here ----
    public static final int DURATION_TICKS = 500;
    public static final double DAMAGE_BONUS = 0.30;
    public static final double RESIST_BONUS = 0.20;
    public static final double OPEN_RADIUS = 20.0;
    public static final double OPEN_FLAT = 120.0;
    public static final double OPEN_PERCENT = 0.05;
    public static final double AURA_RADIUS = 6.0;
    public static final double AURA_FLAT = 20.0;
    public static final double AURA_PERCENT = 0.015;
    public static final int PULSE0 = 60;
    public static final int PULSE_EVERY = 80;
    public static final double PULSE_RADIUS = 12.0;
    public static final double PULSE_FLAT = 60.0;
    public static final double PULSE_PERCENT = 0.03;
    public static final int MANA_COST = 550;
    public static final double COOLDOWN_SECONDS = 80.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public DevilTriggerSpell() {
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
                level.addFreshEntity(DevilTriggerEntity.create(level, entity));
                StylishRankEntity.notifyDanteCast(entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dante_devil_trigger failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Awakening shockwave in " + (int) OPEN_RADIUS + " blocks: " + (int) OPEN_FLAT + " + " + (int) (OPEN_PERCENT * 100) + "% max HP"),
                Component.literal(DURATION_TICKS / 20 + " seconds: +" + (int) (DAMAGE_BONUS * 100) + "% damage, +" + (int) (RESIST_BONUS * 100) + "% resistance, speed, strength, regeneration"),
                Component.literal("Burning aura (" + (int) AURA_RADIUS + " blocks) and a Devil Burst every 4 seconds (" + (int) PULSE_RADIUS + " blocks)"));
    }
}
