package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.CursedConfig;
import com.example.jjkspells.entity.LapseBlueOrb;
import com.example.jjkspells.ModSounds;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Cursed Technique Lapse: Blue - a swirling orb that sucks nearby entities toward it and deals small damage.
 * Level scales damage, orb size and pull range (5 levels by default).
 */
public class LapseBlueSpell extends AbstractSpell {
    private final ResourceLocation spellId = JJKSpells.id("lapse_blue");

    // Iron's reads this for school / rarity / max level defaults. Cooldown and mana come from cursed_techniques-common.toml.
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(18)
            .build();

    public LapseBlueSpell() {
        // Mana, cast time and cooldown are overridden below to read the config file; these are just sane fallbacks.
        this.baseManaCost = 60;
        this.manaCostPerLevel = 15;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 25;
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
        var cfg = CursedConfig.BLUE;
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) ((cfg.manaCostBase.get() + cfg.manaCostPerLevel.get() * (level - 1)) * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(CursedConfig.BLUE.cooldownSeconds.get() * 20.0));
    }

    @Override
    public int getCastTime(int spellLevel) {
        return Math.max(1, CursedConfig.BLUE.castTimeTicks.get());
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        var cfg = CursedConfig.BLUE;
        double base = cfg.damageBase.get() + cfg.damagePerLevel.get() * (spellLevel - 1);
        double power = cfg.useSpellPower.get() ? getEntityPowerMultiplier(caster) : 1.0;
        return (float) (base * power);
    }

    public float getOrbRadius(int spellLevel) {
        var cfg = CursedConfig.BLUE;
        return (float) (cfg.orbRadiusBase.get() + cfg.orbRadiusPerLevel.get() * (spellLevel - 1));
    }

    public float getPullRange(int spellLevel) {
        var cfg = CursedConfig.BLUE;
        return (float) (cfg.pullRangeBase.get() + cfg.pullRangePerLevel.get() * (spellLevel - 1));
    }

    public int getDurationTicks(int spellLevel) {
        var cfg = CursedConfig.BLUE;
        return Math.max(10, (int) Math.round((cfg.durationSecondsBase.get() + cfg.durationSecondsPerLevel.get() * (spellLevel - 1)) * 20.0));
    }

    // ---- casting ---------------------------------------------------------------------------------------------

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(ModSounds.LAPSE_BLUE_CHARGE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(ModSounds.LAPSE_BLUE_CAST.get());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        var cfg = CursedConfig.BLUE;
        float radius = getOrbRadius(spellLevel);

        // Point in front of the caster; stop short of any wall so the orb is never buried in a block.
        Vec3 eye = entity.getEyePosition();
        Vec3 look = entity.getLookAngle();
        Vec3 target = eye.add(look.scale(cfg.castDistance.get()));
        BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        Vec3 center = target;
        if (hit.getType() != HitResult.Type.MISS) {
            center = hit.getLocation().subtract(look.scale(Math.min(radius, 1.0) + 0.1));
        }

        LapseBlueOrb orb = new LapseBlueOrb(level, entity);
        orb.setup(radius,
                getPullRange(spellLevel),
                getDamage(spellLevel, entity),
                cfg.damageIntervalTicks.get(),
                getDurationTicks(spellLevel),
                cfg.pullStrength.get().floatValue(),
                cfg.maxPullSpeed.get().floatValue(),
                cfg.pullPlayers.get(),
                cfg.pullItems.get());
        // Entity origin is at its feet; the orb's hitbox is 1 block tall, so lower by half a block to centre it.
        orb.setPos(center.x, center.y - orb.getBbHeight() * 0.5, center.z);
        level.addFreshEntity(orb);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamage(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(getPullRange(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.duration", Utils.stringTruncation(getDurationTicks(spellLevel) / 20.0, 1) + "s")
        );
    }
}
