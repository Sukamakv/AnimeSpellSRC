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

/** Sun Breathing, Thirteenth Form: The Sun's Eternal Dance. All twelve forms in a row in four seconds, ending in a ring of solar fire. */
@AutoSpellConfig
public class SunEternalSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "sun_eternal");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(240)
            .build();

    private static final AnimationHolder ANIM = SpellAnimLookup.find("SLASH_ANIMATION", "ONE_HANDED_HORIZONTAL_SWING_ANIMATION");

    public SunEternalSpell() {
        this.manaCostPerLevel = 0;
        this.baseManaCost = 900;
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
        return List.of(Component.literal("Twelve attacks in 4 seconds, invulnerable throughout"), Component.literal("Final eruption: " + String.format("%.0f", JJKConfig.SUN_ERUPTION_DAMAGE.get()) + " extra damage in 16 blocks"), Component.literal("Each hit: a multiple of your weapon's damage (" + String.format("%.1f", w) + ")"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            SunBreathingManager.start(SunBreathingManager.Form.ETERNAL, server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
