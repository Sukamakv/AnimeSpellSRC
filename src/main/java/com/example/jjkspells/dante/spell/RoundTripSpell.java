package com.example.jjkspells.dante.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.entity.RoundTripEntity;
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
 * Dante - Round Trip: Twin Rebellion.
 * Dante hurls two giant spinning Rebellions that fly out in wide mirrored loops, dragging in and shredding everything they pass, slam together
 * at the far end of the loop, then come back to him and burst into a ring of blades.
 */
public class RoundTripSpell extends AbstractSpell {
    public static final String NAME = "dante_round_trip";

    // ---- tweak here ----
    public static final double LENGTH = 40.0;
    public static final double WIDTH = 15.0;
    public static final double SWORD_RADIUS = 5.5;
    public static final double SPIN_FLAT = 30.0;
    public static final double SPIN_PERCENT = 0.012;
    public static final double APEX_RADIUS = 11.0;
    public static final double APEX_FLAT = 120.0;
    public static final double APEX_PERCENT = 0.05;
    public static final double FINAL_RADIUS = 16.0;
    public static final double FINAL_FLAT = 200.0;
    public static final double FINAL_PERCENT = 0.08;
    public static final int MANA_COST = 400;
    public static final double COOLDOWN_SECONDS = 45.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public RoundTripSpell() {
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
                level.addFreshEntity(RoundTripEntity.create(level, entity));
                StylishRankEntity.notifyDanteCast(entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dante_round_trip failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Two spinning Rebellions fly " + (int) LENGTH + " blocks in mirrored loops, dragging in and shredding enemies: " + (int) SPIN_FLAT + " + " + SPIN_PERCENT * 100 + "% max HP per hit"),
                Component.literal("They slam together at the far end: " + (int) APEX_FLAT + " + " + (int) (APEX_PERCENT * 100) + "% max HP in " + (int) APEX_RADIUS + " blocks"),
                Component.literal("On their return they burst: " + (int) FINAL_FLAT + " + " + (int) (FINAL_PERCENT * 100) + "% max HP in " + (int) FINAL_RADIUS + " blocks"));
    }
}
