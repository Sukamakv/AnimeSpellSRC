package com.example.jjkspells.yone.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yone.entity.TwinCrescentsEntity;
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
 * Yone - Mortal Steel: Twin Crescents.
 * Three strokes with his two blades: a blue Mortal Steel cut, a red Spirit Blade cut, then both at once in a cross. Each stroke sends a crescent of
 * cut air flying forward, slicing everything it passes through.
 */
public class TwinCrescentsSpell extends AbstractSpell {
    public static final String NAME = "yone_twin_crescents";

    // ---- tweak here ----
    public static final int T0 = 4;
    public static final int STEP = 6;
    public static final double SPEED = 1.5;
    public static final int LIFE = 20;
    public static final double HALF_WIDTH = 3.0;
    public static final double HALF_WIDTH_BIG = 5.0;
    public static final double STROKE_FLAT = 55.0;
    public static final double STROKE_PERCENT = 0.025;
    public static final double CROSS_FLAT = 80.0;
    public static final double CROSS_PERCENT = 0.035;
    public static final int MANA_COST = 140;
    public static final double COOLDOWN_SECONDS = 8.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public TwinCrescentsSpell() {
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
                level.addFreshEntity(TwinCrescentsEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yone_twin_crescents failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Three strokes: blue, red, then a crossed cut"),
                Component.literal("Each sends a crescent " + (int) (SPEED * LIFE) + " blocks forward through every enemy"),
                Component.literal("Strokes deal " + (int) STROKE_FLAT + " + " + STROKE_PERCENT * 100 + "% max HP, each cross blade " + (int) CROSS_FLAT + " + " + CROSS_PERCENT * 100 + "%"));
    }
}
