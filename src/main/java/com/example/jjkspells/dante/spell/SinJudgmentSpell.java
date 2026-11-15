package com.example.jjkspells.dante.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.entity.SinJudgmentEntity;
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
 * Dante - Sin Devil Trigger: Judgment of Sparda (ultimate).
 * Dante rises into the air in his Sin Devil Trigger form while a heptagram of hellfire spreads over 30 blocks and a ring of giant Rebellions
 * stands up around it. Enemies are locked on and a rain of giant Rebellions crashes down on them, then Dante dives to the centre and detonates.
 */
public class SinJudgmentSpell extends AbstractSpell {
    public static final String NAME = "dante_sparda_judgment";

    // ---- tweak here ----
    public static final double RADIUS = 30.0;
    public static final double HOVER = 7.0;
    public static final double STRIKE_RADIUS = 5.5;
    public static final double STRIKE_FLAT = 90.0;
    public static final double STRIKE_PERCENT = 0.035;
    public static final double FINAL_RADIUS = 34.0;
    public static final double FINAL_FLAT = 800.0;
    public static final double FINAL_PERCENT = 0.25;
    public static final int MANA_COST = 900;
    public static final double COOLDOWN_SECONDS = 150.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public SinJudgmentSpell() {
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
                level.addFreshEntity(SinJudgmentEntity.create(level, entity));
                StylishRankEntity.notifyDanteCast(entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dante_sparda_judgment failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Rise in Sin Devil Trigger; every enemy within " + (int) RADIUS + " blocks is locked on"),
                Component.literal("36 giant Rebellions crash down: " + (int) STRIKE_FLAT + " + " + STRIKE_PERCENT * 100 + "% max HP each, " + (int) STRIKE_RADIUS + " block radius"),
                Component.literal("Judgment dive: " + (int) FINAL_FLAT + " + " + (int) (FINAL_PERCENT * 100) + "% max HP in " + (int) FINAL_RADIUS + " blocks"));
    }
}
