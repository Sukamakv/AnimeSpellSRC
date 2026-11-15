package com.example.jjkspells.vergil.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.vergil.entity.DimensionSeverEntity;
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
 * Vergil - Dimension Sever: Shattered Sky (ultimate).
 * One slash at the aim point cuts the world: a 50 block cage of cut space closes around the target area, the sky cracks and darkens like glass,
 * enemies are dragged inward while sheets of falling sky rain down in eight pulses, a rift opens at the centre, and finally the whole sky shatters.
 */
public class DimensionSeverSpell extends AbstractSpell {
    public static final String NAME = "vergil_dimension_sever";

    // ---- tweak here ----
    public static final double RANGE = 80.0;
    public static final double RADIUS = 50.0;
    public static final double SLASH_FLAT = 150.0;
    public static final double SLASH_PERCENT = 0.05;
    public static final double PULSE_FLAT = 60.0;
    public static final double PULSE_PERCENT = 0.025;
    public static final double FINAL_FLAT = 500.0;
    public static final double FINAL_PERCENT = 0.15;
    public static final int MANA_COST = 1000;
    public static final double COOLDOWN_SECONDS = 300.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public DimensionSeverSpell() {
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
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_LONG_CAST", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(DimensionSeverEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("vergil_dimension_sever failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Cuts a " + (int) RADIUS + " block radius around the aim point (range " + (int) RANGE + ")"),
                Component.literal("Opening cut: " + (int) SLASH_FLAT + " + " + (int) (SLASH_PERCENT * 100) + "% max HP, then enemies are dragged to the centre"),
                Component.literal("8 falling sky pulses: " + (int) PULSE_FLAT + " + " + (int) (PULSE_PERCENT * 1000) / 10.0 + "% max HP each"),
                Component.literal("The sky shatters: " + (int) FINAL_FLAT + " + " + (int) (FINAL_PERCENT * 100) + "% max HP"));
    }
}
