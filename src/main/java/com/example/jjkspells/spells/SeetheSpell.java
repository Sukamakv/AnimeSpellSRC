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
 * Seethe - Eruption of the Scorched Earth. She drives her sword into the ground; glowing cracks race to the aimed
 * spot (20 blocks), then 8 magma pillars erupt across an 8 block radius, and the whole area detonates. Enemies that
 * are already burning each get an extra pillar of their own (Chain Combustion).
 */
@AutoSpellConfig
public class SeetheSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "seethe");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(18)
            .build();

    private static final AnimationHolder CAST_ANIMATION = SpellAnimLookup.find("ONE_HANDED_VERTICAL_UPSLASH_ANIMATION", "SLASH_ANIMATION");

    public SeetheSpell() {
        this.manaCostPerLevel = 8;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 90;
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
                Component.literal("Range: 20 blocks, eruption radius 8 blocks"),
                Component.literal("8 pillars: 35 damage + 3% each (at most 4 per enemy), launching enemies upward"),
                Component.literal("Final detonation: 65 damage + 6% of the target's max health"),
                Component.literal("Inflicts Combustion for 8s; burning enemies get an extra 25 + 2% pillar (max 6)")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            LaevatainManager.startSeethe(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
