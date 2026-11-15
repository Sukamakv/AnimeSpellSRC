package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModSounds;
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

/** Sun Breathing, Seventh Form: Beneficent Radiance. A 0.4 second thrusting stance, then an instant 12-block piercing dash behind a golden beam. */
@AutoSpellConfig
public class SunRadianceSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "sun_radiance");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(10)
            .build();

    private static final AnimationHolder ANIM = SpellAnimLookup.find("SLASH_ANIMATION", "ONE_HANDED_HORIZONTAL_SWING_ANIMATION");

    public SunRadianceSpell() {
        this.manaCostPerLevel = 5;
        this.baseManaCost = 45;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return ANIM != null ? ANIM : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float w = MoonFX.weaponDamage(caster);
        float m = SunFX.levelMult(spellLevel);
        return List.of(Component.literal("Dash: 12 blocks, pierces everything on the line"), Component.literal("Thrust: " + String.format("%.1f", w * 2.6f * m) + " (260% of your weapon's damage)"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            SunBreathingManager.start(SunBreathingManager.Form.RADIANCE, server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
