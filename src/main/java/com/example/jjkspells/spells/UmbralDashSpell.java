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
 * Umbral Dash. Instant cast. Aatrox spreads his wings and surges 5 blocks in a quarter of a second (in the direction he
 * is moving, or the way he faces if he is standing still), leaving a crimson afterimage and a trail of red-black
 * particles. Every enemy he passes through takes 100 damage plus 4% of its max health, and knockback hardly moves him
 * during the dash. He ends with his feet planted and the sword up in a ready stance.
 *
 * It can be cast while The Darkin Blade is winding up: the slam happens from wherever he ends up, which is how he lines
 * up the centre of the strike.
 */
@AutoSpellConfig
public class UmbralDashSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "umbral_dash");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(7)
            .build();

    /** Dash length in blocks and duration in ticks (5 ticks = 0.25 seconds). */
    public static final float DISTANCE = 5f;
    public static final int DURATION_TICKS = 5;
    public static final float DAMAGE = 100f;
    public static final float MAX_HEALTH_PERCENT = 0.04f;
    /** How far to each side of the path he cuts. */
    public static final float CUT_WIDTH = 1.4f;

    private static final AnimationHolder DASH_ANIMATION = SpellAnimLookup.find("SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");

    public UmbralDashSpell() {
        this.manaCostPerLevel = 3;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 30;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return DASH_ANIMATION != null ? DASH_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Dash " + (int) DISTANCE + " blocks in " + String.format("%.2f", DURATION_TICKS / 20f) + "s"),
                Component.literal("Damage: " + String.format("%.0f", DAMAGE * DariusFX.levelScale(spellLevel)) + " + " + (int) (MAX_HEALTH_PERCENT * 100) + "% max health to enemies passed through"),
                Component.literal("Resists knockback while dashing")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            UmbralDashManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
