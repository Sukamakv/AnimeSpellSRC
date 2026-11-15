package com.example.jjkspells.garou.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.config.GarConfig;
import com.example.jjkspells.garou.entity.GammaBeamEntity;
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
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Garou, Cosmic Fear Mode: GAMMA RAY BURST. A dark cosmic sphere forms between the hands, compresses, throws out two
 * white-blue beams that merge into one catastrophic beam. The whole sequence is run by {@link GammaBeamEntity}.
 */
public class GammaRayBurstSpell extends AbstractSpell {
    private final ResourceLocation id = JJKSpells.id("gamma_ray_burst");
    private final DefaultConfig defaultConfig;

    public GammaRayBurstSpell() {
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
        return Math.max(0, (int) (GarConfig.GAMMA.manaCost.get() * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(GarConfig.GAMMA.cooldownSeconds.get() * 20.0));
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    /** Both hands thrust forward (falls back to whatever this Iron's version has). */
    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_CONTINUOUS_CAST", "CONTINUOUS_CAST_ANIMATION", "ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return !GammaBeamEntity.isCasting(entity);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(GammaBeamEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("gamma_ray_burst failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = GarConfig.GAMMA;
        return List.of(
                Component.literal("Cosmic Compression: you stop, a dark cosmic sphere forms between your hands and collapses; two white-blue beams burst out and merge into one"),
                Component.literal("Initial impact: " + n(c.initialFlat.get()) + " + " + n(c.initialPercent.get()) + "% max HP, then " + n(c.tickFlat.get()) + " + " + n(c.tickPercent.get()) + "% max HP every "
                        + Utils.stringTruncation(c.intervalSeconds.get(), 2) + "s for up to " + n(c.durationSeconds.get()) + "s"),
                Component.literal("Beam: " + n(c.beamWidth.get()) + " blocks wide, up to " + n(c.maxLength.get()) + " blocks long. A target takes each hit at most once per interval and the beam can hit it only "
                        + (1 + (int) Math.floor(c.durationSeconds.get() / c.intervalSeconds.get())) + " times per cast"));
    }

    private static String n(double v) {
        return Utils.stringTruncation(v, 0);
    }
}
