package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Optional;

/** Shared boilerplate of the four Garen spells; mana, cooldown and cast time come from jjk_spells-garen.toml. */
public abstract class GarenSpellBase extends AbstractSpell {
    private final ResourceLocation spellId;
    private final DefaultConfig defaultConfig;
    private final CastType castType;

    protected GarenSpellBase(String id, CastType castType, SpellRarity rarity) {
        this.spellId = new ResourceLocation(JJKSpells.MODID, id);
        this.castType = castType;
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(rarity)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(10)
                .build();
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
    }

    protected abstract int configMana();

    protected abstract double configCooldownSeconds();

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public int getCastTime(int spellLevel) { return 0; }
    @Override public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }
    @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }
    /** One-shot swing: it ends by itself. The old charged-cast pose is a held pose that never released on instant spells. */
    private static final AnimationHolder SWING = SpellAnimLookup.find("ONE_HANDED_HORIZONTAL_SWING_ANIMATION", "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
    @Override public AnimationHolder getCastStartAnimation() { return SWING != null ? SWING : super.getCastStartAnimation(); }

    @Override
    public int getManaCost(int level) {
        return Math.max(0, configMana());
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(configCooldownSeconds() * 20.0));
    }
}
