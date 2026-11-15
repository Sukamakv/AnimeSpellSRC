package com.example.jjkspells.yone.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yone.entity.TwinMoonEclipseEntity;
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
 * Yone - Sundered Realm: Twin Moon Eclipse.
 * A blue moon and a red moon rise over the spot he points at and slide together into an eclipse. Beneath them everything is dragged into a funnel, and when
 * the moons align a giant X of blue and red blades falls through the middle and splits the realm.
 */
public class TwinMoonEclipseSpell extends AbstractSpell {
    public static final String NAME = "yone_twin_moon_eclipse";

    // ---- tweak here ----
    public static final double RANGE = 34.0;
    public static final double PULL_RADIUS = 16.0;
    public static final int PULL_START = 6;
    public static final int CHARGE = 36;
    public static final int X_DELAY = 8;
    public static final double X_RADIUS = 10.0;
    public static final double X_FLAT = 220.0;
    public static final double X_PERCENT = 0.10;
    public static final double AFTER_RADIUS = 13.0;
    public static final double AFTER_FLAT = 60.0;
    public static final double AFTER_PERCENT = 0.03;
    public static final int MANA_COST = 360;
    public static final double COOLDOWN_SECONDS = 50.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public TwinMoonEclipseSpell() {
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
                level.addFreshEntity(TwinMoonEclipseEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yone_twin_moon_eclipse failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Twin moons rise over the spot you aim at, up to " + (int) RANGE + " blocks away"),
                Component.literal("Enemies within " + (int) PULL_RADIUS + " blocks are dragged into a funnel under the eclipse"),
                Component.literal("A giant X falls through the centre: " + (int) X_FLAT + " + " + X_PERCENT * 100 + "% max HP within " + (int) X_RADIUS + " blocks, then an aftershock of " + (int) AFTER_FLAT + " + " + AFTER_PERCENT * 100 + "%"));
    }
}
