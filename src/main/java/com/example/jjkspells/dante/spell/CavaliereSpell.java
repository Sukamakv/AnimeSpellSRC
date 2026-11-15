package com.example.jjkspells.dante.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.entity.CavaliereEntity;
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
 * Dante - Cavaliere Angelo: Lightning Ride.
 * Dante summons the lightning motorcycle and rides it in the direction he looks (steerable), ramming everything on the way and shocking
 * whatever is near. When the ride ends a thunderstorm crashes down around him, finished by one colossal bolt.
 */
public class CavaliereSpell extends AbstractSpell {
    public static final String NAME = "dante_cavaliere_ride";

    // ---- tweak here ----
    public static final double SPEED = 1.05;
    public static final double TURN_DEG_PER_TICK = 7.0;
    public static final double RAM_RADIUS = 4.2;
    public static final double RAM_FLAT = 160.0;
    public static final double RAM_PERCENT = 0.06;
    public static final double SHOCK_RADIUS = 7.0;
    public static final double SHOCK_FLAT = 25.0;
    public static final double SHOCK_PERCENT = 0.01;
    public static final double BOLT_RADIUS = 8.0;
    public static final double BOLT_FLAT = 180.0;
    public static final double BOLT_PERCENT = 0.07;
    public static final double BIG_RADIUS = 20.0;
    public static final double BIG_FLAT = 320.0;
    public static final double BIG_PERCENT = 0.10;
    public static final int MANA_COST = 380;
    public static final double COOLDOWN_SECONDS = 40.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public CavaliereSpell() {
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
                level.addFreshEntity(CavaliereEntity.create(level, entity));
                StylishRankEntity.notifyDanteCast(entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dante_cavaliere_ride failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Ride the lightning motorcycle where you look; ram: " + (int) RAM_FLAT + " + " + (int) (RAM_PERCENT * 100) + "% max HP"),
                Component.literal("Everything near the bike is shocked and slowed"),
                Component.literal("Thunderstorm at the end: " + (int) BOLT_FLAT + " + " + (int) (BOLT_PERCENT * 100) + "% per bolt, then a giant bolt: " + (int) BIG_FLAT + " + " + (int) (BIG_PERCENT * 100) + "% in " + (int) BIG_RADIUS + " blocks"));
    }
}
