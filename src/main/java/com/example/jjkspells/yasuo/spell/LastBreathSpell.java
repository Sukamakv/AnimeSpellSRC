package com.example.jjkspells.yasuo.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yasuo.entity.LastBreathEntity;
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
 * Yasuo - Last Breath: Executioner's Mark.
 * A gust lifts every enemy around him into the air and holds them there. He blinks from one to the next cutting each, a doppelganger left behind at every
 * cut, then the whole mark detonates and slams them back to the ground.
 */
public class LastBreathSpell extends AbstractSpell {
    public static final String NAME = "yasuo_last_breath";

    // ---- tweak here ----
    public static final double RANGE = 16.0;
    public static final int MAX_TARGETS = 6;
    public static final double LIFT = 3.5;
    public static final int STEP = 6;
    public static final double CUT_FLAT = 80.0;
    public static final double CUT_PERCENT = 0.05;
    public static final double FINAL_FLAT = 150.0;
    public static final double FINAL_PERCENT = 0.10;
    public static final int MANA_COST = 260;
    public static final double COOLDOWN_SECONDS = 34.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public LastBreathSpell() {
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
                LastBreathEntity duel = LastBreathEntity.create(level, entity);
                if (duel != null) {
                    level.addFreshEntity(duel);
                } else if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("No enemies in range"), true);
                }
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yasuo_last_breath failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Lifts up to " + MAX_TARGETS + " enemies within " + (int) RANGE + " blocks into the air and holds them"),
                Component.literal("Blinks to each, cutting for " + (int) CUT_FLAT + " + " + CUT_PERCENT * 100 + "% max HP"),
                Component.literal("Then detonates the mark: " + (int) FINAL_FLAT + " + " + FINAL_PERCENT * 100 + "% max HP and slams them down"));
    }
}
