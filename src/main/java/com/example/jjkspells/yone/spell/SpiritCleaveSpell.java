package com.example.jjkspells.yone.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yone.entity.SpiritCleaveEntity;
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
 * Yone - Spirit Cleave: Veil of Two Blades.
 * He crosses both blades, then tears them apart in a wide sweep that fans crescents across a cone in front of him; the blades close again in a scissor cut
 * that drags the survivors toward the centre. Every enemy caught feeds a shield, and the two blades circle him as a veil while it lasts.
 */
public class SpiritCleaveSpell extends AbstractSpell {
    public static final String NAME = "yone_spirit_cleave";

    // ---- tweak here ----
    public static final int T1 = 4;
    public static final int T2 = 11;
    public static final double RADIUS = 11.0;
    public static final double HALF_ANGLE_DEG = 70.0;
    public static final double CLOSE_RADIUS = 8.0;
    public static final double CLOSE_HALF_ANGLE_DEG = 55.0;
    public static final double CUT1_FLAT = 90.0;
    public static final double CUT1_PERCENT = 0.04;
    public static final double CUT2_FLAT = 60.0;
    public static final double CUT2_PERCENT = 0.03;
    public static final float SHIELD_BASE = 8f;
    public static final float SHIELD_PER_HIT = 4f;
    public static final float SHIELD_MAX = 24f;
    public static final int SHIELD_TICKS = 100;
    public static final int MANA_COST = 160;
    public static final double COOLDOWN_SECONDS = 12.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public SpiritCleaveSpell() {
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
                level.addFreshEntity(SpiritCleaveEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yone_spirit_cleave failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Sweep: " + (int) CUT1_FLAT + " + " + CUT1_PERCENT * 100 + "% max HP in a " + (int) (HALF_ANGLE_DEG * 2) + " degree cone, " + (int) RADIUS + " blocks"),
                Component.literal("Closing cut: " + (int) CUT2_FLAT + " + " + CUT2_PERCENT * 100 + "% max HP, drags enemies in"),
                Component.literal("Shield: " + (int) SHIELD_BASE + " + " + (int) SHIELD_PER_HIT + " per enemy caught, up to " + (int) SHIELD_MAX + ", for " + SHIELD_TICKS / 20 + " seconds"));
    }
}
