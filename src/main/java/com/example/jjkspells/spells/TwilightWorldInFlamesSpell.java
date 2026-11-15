package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Laevatain's ultimate, Twilight: The World in Flames. A 9 second cinematic: the giant Sviga Laevi awakens, six great
 * sword strikes, eight magma pillars, then a 25 block overhead cleave that detonates over 18 blocks, and burning
 * ground afterwards. She cannot be hurt until it ends. Below 40% HP it is empowered (Scorching Heart).
 */
@AutoSpellConfig
public class TwilightWorldInFlamesSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "twilight_world_in_flames");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(240)
            .build();

    private static final AnimationHolder CAST_ANIMATION = SpellAnimLookup.find("ONE_HANDED_VERTICAL_UPSLASH_ANIMATION", "SLASH_ANIMATION");

    public TwilightWorldInFlamesSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 600;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return CAST_ANIMATION != null ? CAST_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Invulnerable while it plays (9 seconds)"),
                Component.literal("Six sword strikes: 40 + 2% up to 90 + 6%, each inflicting Combustion"),
                Component.literal("Eight pillars: 50 damage + 3% each"),
                Component.literal("Final cleave: 200 + 15%, explosion 125 + 10% over 18 blocks, +50 within 6 blocks"),
                Component.literal("Burning ground: 10 damage every second for 5 seconds"),
                Component.literal("Scorching Heart (below 40% HP): +20% damage, +75 + 5% explosion, Resistance IV, heals 20% after")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            LaevatainManager.startTwilight(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
