package com.example.jjkspells.yasuo.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yasuo.entity.ThousandWindsEntity;
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
 * Yasuo - Hasagi: Thousand Winds (ultimate).
 * Yasuo plants himself, spectral doppelgangers ring the field, and for three seconds giant blades of wind rain from the sky over a vast area,
 * seeking out his enemies; then every blade converges in one last cut that levels the arena.
 */
public class ThousandWindsSpell extends AbstractSpell {
    public static final String NAME = "yasuo_thousand_winds";

    // ---- tweak here ----
    public static final double RADIUS = 22.0;
    public static final int CHARGE_TICKS = 24;
    public static final int BARRAGE_TICKS = 60;
    public static final int VOLLEY_INTERVAL = 2;
    public static final double STRIKE_RADIUS = 3.5;
    public static final double STRIKE_FLAT = 45.0;
    public static final double STRIKE_PERCENT = 0.02;
    public static final double FINAL_FLAT = 220.0;
    public static final double FINAL_PERCENT = 0.12;
    public static final int MANA_COST = 450;
    public static final double COOLDOWN_SECONDS = 90.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public ThousandWindsSpell() {
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
                level.addFreshEntity(ThousandWindsEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yasuo_thousand_winds failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Giant blades rain over a " + (int) RADIUS + " block radius for " + BARRAGE_TICKS / 20 + " seconds, seeking enemies"),
                Component.literal("Each blade deals " + (int) STRIKE_FLAT + " + " + STRIKE_PERCENT * 100 + "% max HP in " + (int) STRIKE_RADIUS + " blocks"),
                Component.literal("The final convergence deals " + (int) FINAL_FLAT + " + " + FINAL_PERCENT * 100 + "% max HP to everything in the arena"),
                Component.literal("You are rooted and untouchable while it lasts"));
    }
}
