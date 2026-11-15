package com.example.jjkspells.yasuo.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yasuo.entity.SteelTempestEntity;
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
 * Yasuo - Steel Tempest: Gathering Storm.
 * Yasuo stabs twice with lightning speed, each thrust lunging him forward and skewering everything in front of him. The third thrust gathers the wind
 * into a towering tornado that sweeps forward, lifting every enemy it touches into the air and shredding them.
 */
public class SteelTempestSpell extends AbstractSpell {
    public static final String NAME = "yasuo_steel_tempest";

    // ---- tweak here ----
    public static final double THRUST_LENGTH = 9.0;
    public static final double THRUST_HALF_WIDTH = 2.2;
    public static final double THRUST_FLAT = 60.0;
    public static final double THRUST_PERCENT = 0.02;
    public static final double LAST_LENGTH = 12.0;
    public static final double LAST_HALF_WIDTH = 3.0;
    public static final double LAST_FLAT = 100.0;
    public static final double LAST_PERCENT = 0.03;
    public static final double TORNADO_SPEED = 0.9;
    public static final double TORNADO_RADIUS = 5.5;
    public static final double TORNADO_HEIGHT = 18.0;
    public static final double CATCH_FLAT = 140.0;
    public static final double CATCH_PERCENT = 0.06;
    public static final double TICK_FLAT = 25.0;
    public static final double TICK_PERCENT = 0.01;
    public static final double DROP_FLAT = 80.0;
    public static final double DROP_PERCENT = 0.03;
    public static final int MANA_COST = 180;
    public static final double COOLDOWN_SECONDS = 14.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public SteelTempestSpell() {
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
                level.addFreshEntity(SteelTempestEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yasuo_steel_tempest failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Two lunging thrusts: " + (int) THRUST_FLAT + " + " + (int) (THRUST_PERCENT * 100) + "% max HP each"),
                Component.literal("Third thrust: " + (int) LAST_FLAT + " + " + (int) (LAST_PERCENT * 100) + "% max HP and it launches a tornado"),
                Component.literal("The tornado lifts enemies into the air: " + (int) CATCH_FLAT + " + " + (int) (CATCH_PERCENT * 100) + "% max HP on contact, then keeps shredding them"));
    }
}
