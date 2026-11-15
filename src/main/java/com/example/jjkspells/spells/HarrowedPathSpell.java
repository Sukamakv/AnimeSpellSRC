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
 * Harrowed Path. Viego lets the Black Mist loose: a 20 block radius field of mist that lasts 15 seconds. No blocks
 * are touched; the mist is made of particles (dense around any player inside it, with a darkened, closer fog on that
 * player's screen) and a ring along the ground at its edge.
 *
 * Inside it Viego has +35% movement speed, turns invisible, and his melee attacks deal double damage.
 * Enemies inside are 20% slower, get Weakness II and have 35% less spell power, for as long as they stay in it.
 */
@AutoSpellConfig
public class HarrowedPathSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "harrowed_path");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(260)
            .build();

    /** Radius of the mist in blocks, and how long it lasts (15 seconds). */
    public static final float RADIUS = 20f;
    public static final int DURATION_TICKS = 300;

    private static final AnimationHolder RELEASE_ANIMATION = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");

    public HarrowedPathSpell() {
        this.manaCostPerLevel = 15;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 120;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return RELEASE_ANIMATION != null ? RELEASE_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    /** Each spell level above 1 lengthens the mist by 8%. */
    public int getDuration(int spellLevel) { return Math.round(DURATION_TICKS * DariusFX.levelScale(spellLevel)); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Mist radius: " + (int) RADIUS + " blocks, lasts " + String.format("%.0f", getDuration(spellLevel) / 20f) + "s"),
                Component.literal("You: +35% speed, invisible, melee damage doubled"),
                Component.literal("Enemies: -20% speed, Weakness II, -35% spell power")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            HarrowedPathManager.start(server, entity, getDuration(spellLevel));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
