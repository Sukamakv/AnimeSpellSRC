package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Shared boilerplate of DIO's four spells: instant cast, fixed numbers, blood school. */
public abstract class DioSpellBase extends AbstractSpell {
    private final ResourceLocation spellId;
    private final DefaultConfig defaultConfig;
    private final int mana;
    private final int cooldownTicks;

    protected DioSpellBase(String id, SpellRarity rarity, int mana, int cooldownSeconds) {
        this.spellId = new ResourceLocation(JJKSpells.MODID, id);
        this.mana = mana;
        this.cooldownTicks = cooldownSeconds * 20;
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(rarity)
                .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(cooldownSeconds)
                .build();
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = mana;
        this.manaCostPerLevel = 0;
        this.castTime = 0;
    }

    protected abstract void begin(ServerLevel level, LivingEntity caster);

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public int getCastTime(int spellLevel) { return 0; }
    @Override public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }
    @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }
    @Override public int getManaCost(int level) { return mana; }
    @Override public int getSpellCooldown() { return cooldownTicks; }

    /** One-shot swing: it ends by itself (held poses never release on instant spells). */
    private static final AnimationHolder SWING = SpellAnimLookup.find("ONE_HANDED_HORIZONTAL_SWING_ANIMATION", "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
    @Override public AnimationHolder getCastStartAnimation() { return SWING != null ? SWING : super.getCastStartAnimation(); }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) begin(server, entity);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
