package com.example.jjkspells.prime;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;

/** Base of the PRIME spells: one level, legendary, long cast. */
public abstract class PrimeSpell extends AbstractSpell {
    private final ResourceLocation id;
    private final DefaultConfig cfg;

    protected PrimeSpell(String path, ResourceLocation school, int cooldownSeconds, int mana, int castTicks) {
        this.id = JJKSpells.id(path);
        this.cfg = new DefaultConfig().setMinRarity(SpellRarity.LEGENDARY).setSchoolResource(school).setMaxLevel(1).setCooldownSeconds(cooldownSeconds).build();
        this.baseManaCost = mana;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = castTicks;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return id;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return cfg;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }
}
