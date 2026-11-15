package com.example.jjkspells.frieren.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.DefensiveMagicEntity;
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
 * Frieren - Defensive Magic. Both hands up, a seal opens in front of the caster, then a hexagon-panelled barrier unfolds
 * round them. Plain model: 80% damage reduction for allies inside, paid from a durability pool; at zero it shatters into a pulse.
 */
public class DefensiveMagicSpell extends AbstractSpell {
    // ---- tuning ------------------------------------------------------------------------------------------------------
    public static final int MANA = 220;
    public static final double COOLDOWN_SECONDS = 30.0;
    public static final int DURATION_TICKS = 12 * 20;
    public static final float RADIUS = 3f;
    /** Share of every incoming hit that the barrier takes (and pays for from its durability). */
    public static final float REDUCTION = 0.80f;
    public static final float DURABILITY = 1500f;
    /** Shatter pulse. */
    public static final float PULSE_FLAT = 100f;
    public static final float PULSE_PERCENT = 0.05f;
    public static final float PULSE_RADIUS = 5f;

    private final ResourceLocation id = JJKSpells.id("frieren_defensive_magic");
    private final DefaultConfig defaultConfig;

    public DefensiveMagicSpell() {
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
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST", "ANIMATION_LONG_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(DefensiveMagicEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("frieren_defensive_magic failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Barrier of radius " + (int) RADIUS + " for " + DURATION_TICKS / 20 + "s; allies inside take " + (int) (REDUCTION * 100) + "% less damage"),
                Component.literal("Durability: " + (int) DURABILITY + " blocked damage"),
                Component.literal("On shatter: " + (int) PULSE_FLAT + " + " + (int) (PULSE_PERCENT * 100) + "% max HP in " + (int) PULSE_RADIUS + " blocks"));
    }
}
