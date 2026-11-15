package com.example.jjkspells.overlord.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.config.OverlordConfig;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** Common plumbing: id, school, config-driven mana cost and cooldown. */
public abstract class OverlordSpell extends AbstractSpell {
    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;
    private final CastType castType;
    private final Supplier<? extends OverlordConfig.Base> cfg;

    protected OverlordSpell(String name, ResourceLocation school, SpellRarity rarity, CastType castType, Supplier<? extends OverlordConfig.Base> cfg) {
        this.id = JJKSpells.id(name);
        this.castType = castType;
        this.cfg = cfg;
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(rarity)
                .setSchoolResource(school)
                .setMaxLevel(5)
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
        return castType;
    }

    @Override
    public int getManaCost(int level) {
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) (cfg.get().manaCost.get() * (1.0 + 0.1 * (level - 1)) * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(cfg.get().cooldownSeconds.get() * 20.0));
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    protected static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}
