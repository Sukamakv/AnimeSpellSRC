package com.example.jjkspells.vergil.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.vergil.entity.DevilTriggerEntity;
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
 * Vergil - Devil Trigger.
 * A huge spectral demon manifests behind the caster for 15 seconds. Stronger hits, speed, regeneration and extra resistance for the caster,
 * while phantom slashes keep cutting enemies within 30 blocks. When it ends the demon bursts in a final shockwave.
 */
public class DevilTriggerSpell extends AbstractSpell {
    public static final String NAME = "vergil_devil_trigger";

    // ---- tweak here ----
    public static final int DURATION_TICKS = 300;
    /** Phantom slash: radius, targets per slash, damage. */
    public static final double RADIUS = 30.0;
    public static final int SLASH_TARGETS = 4;
    public static final double SLASH_FLAT = 40.0;
    public static final double SLASH_PERCENT = 0.025;
    public static final double FINAL_FLAT = 150.0;
    public static final double FINAL_PERCENT = 0.06;
    /** Caster buffs: +25% max_hp_damage, +15% max_hp_resist. */
    public static final double DAMAGE_BONUS = 0.25;
    public static final double RESIST_BONUS = 0.15;
    public static final int MANA_COST = 600;
    public static final double COOLDOWN_SECONDS = 90.0;
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
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_LONG_CAST", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(DevilTriggerEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("vergil_devil_trigger failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Devil form for " + (DURATION_TICKS / 20) + " seconds: Speed II, Strength, Regeneration"),
                Component.literal("+" + (int) (DAMAGE_BONUS * 100) + "% max HP damage, +" + (int) (RESIST_BONUS * 100) + "% max HP resistance"),
                Component.literal("Phantom slashes hit up to " + SLASH_TARGETS + " enemies within " + (int) RADIUS + " blocks every 0.5s"),
                Component.literal("Ends in a shockwave: " + (int) FINAL_FLAT + " + " + (int) (FINAL_PERCENT * 100) + "% max HP"));
    }
}
