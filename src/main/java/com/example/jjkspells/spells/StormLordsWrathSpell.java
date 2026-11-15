package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.StormConfig;
import com.example.jjkspells.entity.StormTornadoEntity;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Storm Lord's Wrath: two giant black storm tornados circle the caster for 12 s, 80 damage per second. */
public class StormLordsWrathSpell extends AbstractSpell {
    private final ResourceLocation spellId = JJKSpells.id("storm_lords_wrath");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(60)
            .build();

    public StormLordsWrathSpell() {
        this.baseManaCost = 200;
        this.manaCostPerLevel = 60;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 30;
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

    @Override
    public int getManaCost(int level) {
        var c = StormConfig.SPELL;
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) ((c.manaCostBase.get() + c.manaCostPerLevel.get() * (level - 1)) * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(StormConfig.SPELL.cooldownSeconds.get() * 20.0));
    }

    @Override
    public int getCastTime(int spellLevel) {
        return Math.max(1, StormConfig.SPELL.castTimeTicks.get());
    }

    public float getDamagePerSecond(int level, LivingEntity caster) {
        var c = StormConfig.SPELL;
        double dps = c.damagePerSecond.get() + c.damagePerSecondPerLevel.get() * (level - 1);
        if (c.useSpellPower.get()) {
            dps *= getEntityPowerMultiplier(caster);
        }
        return (float) dps;
    }

    public int getDurationTicks() {
        return Math.max(20, (int) Math.round(StormConfig.SPELL.durationSeconds.get() * 20.0));
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(ModSounds.STORM_CHARGE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            float dps = getDamagePerSecond(spellLevel, entity);
            int life = getDurationTicks();
            for (int slot = 0; slot < 2; slot++) {
                StormTornadoEntity t = new StormTornadoEntity(level, entity);
                t.setup(slot, life, dps);
                t.moveToOrbit(entity);
                level.addFreshEntity(t);
            }
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.STORM_SUMMON.get(), SoundSource.PLAYERS, 4.0f, 1.0f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamagePerSecond(spellLevel, caster), 0) + "/s"),
                Component.translatable("ui.irons_spellbooks.duration", Utils.stringTruncation(getDurationTicks() / 20.0, 1) + "s")
        );
    }
}
