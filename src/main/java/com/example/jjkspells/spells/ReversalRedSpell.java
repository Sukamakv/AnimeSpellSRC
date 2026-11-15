package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.CursedConfig;
import com.example.jjkspells.ModSounds;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Cursed Technique Reversal: Red - a shotgun-style blast of repulsion. The caster is the barrel: everything in a cone
 * in front of them is hurt (more the closer it is) and thrown back. Level scales damage, reach and knockback.
 */
public class ReversalRedSpell extends AbstractSpell {
    private final ResourceLocation spellId = JJKSpells.id("reversal_red");

    // Iron's reads this for school / rarity / max level defaults. Cooldown and mana come from cursed_techniques-common.toml.
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(12)
            .build();

    public ReversalRedSpell() {
        // Mana, cast time and cooldown are overridden below to read the config file; these are just sane fallbacks.
        this.baseManaCost = 50;
        this.manaCostPerLevel = 15;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 20;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    // ---- config-driven numbers -------------------------------------------------------------------------------

    @Override
    public int getManaCost(int level) {
        var cfg = CursedConfig.RED;
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) ((cfg.manaCostBase.get() + cfg.manaCostPerLevel.get() * (level - 1)) * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(CursedConfig.RED.cooldownSeconds.get() * 20.0));
    }

    @Override
    public int getCastTime(int spellLevel) {
        return Math.max(1, CursedConfig.RED.castTimeTicks.get());
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        var cfg = CursedConfig.RED;
        double base = cfg.damageBase.get() + cfg.damagePerLevel.get() * (spellLevel - 1);
        double power = cfg.useSpellPower.get() ? getEntityPowerMultiplier(caster) : 1.0;
        return (float) (base * power);
    }

    public float getExplosionRadius(int spellLevel) {
        var cfg = CursedConfig.RED;
        return (float) (cfg.explosionRadiusBase.get() + cfg.explosionRadiusPerLevel.get() * (spellLevel - 1));
    }

    public float getKnockback(int spellLevel) {
        var cfg = CursedConfig.RED;
        return (float) (cfg.knockbackBase.get() + cfg.knockbackPerLevel.get() * (spellLevel - 1));
    }

    // ---- casting ---------------------------------------------------------------------------------------------

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(ModSounds.REVERSAL_RED_CHARGE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(ModSounds.REVERSAL_RED_CAST.get());
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CHARGED_CAST;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            ReversalRedBlast.fire(server, entity, this, spellLevel);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamage(spellLevel, caster), 1)),
                Component.literal("Blast length: " + Utils.stringTruncation(getExplosionRadius(spellLevel) * CursedConfig.RED.blastRangeMultiplier.get(), 1) + " blocks")
        );
    }
}
