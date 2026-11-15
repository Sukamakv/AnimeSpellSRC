package com.example.jjkspells.yasuo.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yasuo.entity.WindboundDuelEntity;
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
 * Yasuo - Windbound Duel: Isolation.
 * A ring of wind closes around his chosen enemy. Nobody else may enter, the enemy cannot leave, and every blow Yasuo lands on him bites harder.
 */
public class WindboundDuelSpell extends AbstractSpell {
    public static final String NAME = "yasuo_windbound_duel";

    // ---- tweak here ----
    public static final double TARGET_RANGE = 26.0;
    public static final double MIN_RADIUS = 8.0;
    public static final double HEIGHT = 12.0;
    public static final int DURATION_TICKS = 200;
    public static final double BONUS = 0.40;
    public static final double PULSE_FLAT = 35.0;
    public static final double PULSE_PERCENT = 0.02;
    public static final double KILL_HEAL_PERCENT = 0.25;
    public static final int MANA_COST = 200;
    public static final double COOLDOWN_SECONDS = 28.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public WindboundDuelSpell() {
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
                WindboundDuelEntity duel = WindboundDuelEntity.create(level, entity);
                if (duel != null) {
                    level.addFreshEntity(duel);
                } else if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("No target in range"), true);
                }
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yasuo_windbound_duel failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Locks the enemy you face inside a wind ring for " + DURATION_TICKS / 20 + " seconds"),
                Component.literal("Others cannot enter, the enemy cannot leave; killing him heals you " + (int) (KILL_HEAL_PERCENT * 100) + "% max HP"),
                Component.literal("Your hits on the enemy deal +" + (int) (BONUS * 100) + "% damage, and the wind cuts it for " + (int) PULSE_FLAT + " + " + PULSE_PERCENT * 100 + "% max HP each second"));
    }
}
