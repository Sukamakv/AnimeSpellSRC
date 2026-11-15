package com.example.jjkspells.yone.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yone.entity.HornedVolleyEntity;
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
 * Yone - Oni Mask: Horned Volley.
 * An Oni mask rises over him and spectral swords slide out of its mouth, arranging themselves into a pair of horns, blue on the left and red on the right.
 * One after another they fly at his enemies and stay buried in them; when the volley is over every buried sword explodes.
 */
public class HornedVolleySpell extends AbstractSpell {
    public static final String NAME = "yone_horned_volley";

    // ---- tweak here ----
    public static final int SWORDS = 12;
    public static final int FIRE_START = 24;
    public static final int FIRE_INTERVAL = 2;
    public static final int FLIGHT = 8;
    public static final double RANGE = 32.0;
    public static final double IMPACT_FLAT = 60.0;
    public static final double IMPACT_PERCENT = 0.03;
    public static final double BLAST_FLAT = 40.0;
    public static final double BLAST_PERCENT = 0.02;
    public static final int MANA_COST = 260;
    public static final double COOLDOWN_SECONDS = 28.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public HornedVolleySpell() {
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
                level.addFreshEntity(HornedVolleyEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yone_horned_volley failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal(SWORDS + " spectral swords fly one after another at the enemies within " + (int) RANGE + " blocks"),
                Component.literal("Each sword deals " + (int) IMPACT_FLAT + " + " + IMPACT_PERCENT * 100 + "% max HP and stays buried in its target"),
                Component.literal("At the end every buried sword explodes for " + (int) BLAST_FLAT + " + " + BLAST_PERCENT * 100 + "% max HP"),
                Component.literal("You are rooted while the volley lasts"));
    }
}
