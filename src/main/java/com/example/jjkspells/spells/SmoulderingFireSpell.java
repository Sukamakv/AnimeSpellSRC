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
 * Smouldering Fire - Magma Fragment. Laevatain gathers five magma fragments on her blade, fuses them into a molten
 * projectile and hurls it (it homes in, 24 blocks). On impact it shatters into fragments that each strike a different
 * enemy, then they collapse into a final explosion. Hitting an enemy that is already burning triggers Molten Overload.
 */
@AutoSpellConfig
public class SmoulderingFireSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "smouldering_fire");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(12)
            .build();

    private static final AnimationHolder CAST_ANIMATION = SpellAnimLookup.find("ONE_HANDED_VERTICAL_UPSLASH_ANIMATION", "SLASH_ANIMATION");

    public SmoulderingFireSpell() {
        this.manaCostPerLevel = 6;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 70;
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
                Component.literal("Projectile: 45 damage + 4% of the target's max health"),
                Component.literal("Fragments: 12 damage + 0.5% each, then a 5 block explosion for 35 + 3%"),
                Component.literal("Inflicts Combustion for 6s (6 damage every second)"),
                Component.literal("Molten Overload on a burning target: 7 block explosion, +20 damage, 3 extra fragments")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            LaevatainManager.startFragment(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
