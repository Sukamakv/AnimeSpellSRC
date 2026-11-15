package com.example.jjkspells.yhwach.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yhwach.entity.SanktZwingerEntity;
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
 * Yhwach - Kirchenlied: Sankt Zwinger (Quincy sanctuary).
 * A giant insignia expands under the caster, eight pillars rise, arches and rings form a fortress of light.
 * Enemies inside get struck by golden-white counterattacks from the pillars.
 */
public class KirchenliedSpell extends AbstractSpell {
    public static final String NAME = "yhwach_kirchenlied_sankt_zwinger";

    // ---- tweak here ----
    /** Counterattack damage: flat part + percentage of the victim's max health. */
    public static final double FLAT_DAMAGE = 180.0;
    public static final double PERCENT_MAX_HEALTH = 12.0;
    /** Ticks before the same enemy can trigger another counterattack (2 s). */
    public static final int TRIGGER_COOLDOWN_TICKS = 40;
    /** Radius of the ground insignia; pillars stand at 65% of it. */
    public static final double SEAL_RADIUS = 20.0;
    public static final double PILLAR_HEIGHT = 26.0;
    /** Time the finished sanctuary stands after the build-up, in seconds. */
    public static final int ACTIVE_SECONDS = 15;
    public static final int MANA_COST = 400;
    public static final double COOLDOWN_SECONDS = 60.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public KirchenliedSpell() {
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
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(SanktZwingerEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yhwach_kirchenlied failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Raises a Quincy sanctuary for " + ACTIVE_SECONDS + " seconds"),
                Component.literal("Enemies inside are struck: " + (int) FLAT_DAMAGE + " + " + (int) PERCENT_MAX_HEALTH
                        + "% max HP (2s per enemy)"));
    }
}
