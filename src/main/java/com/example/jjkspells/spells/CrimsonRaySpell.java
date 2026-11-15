package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The Ruler of Death's Crimson Ray. One enormous crimson laser, 1.75x the width of Crimson Wrath's lasers, hangs in the
 * sky directly above the caster and fires down at wherever they aim (up to 110 blocks), following the aim for 9 seconds.
 */
@AutoSpellConfig
public class CrimsonRaySpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "crimson_ray");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(240)
            .build();

    public static final double RANGE = 110.0;
    public static final int DURATION_TICKS = 180; // 9 seconds
    public static final float WIDTH_SCALE = 1.75f;

    public CrimsonRaySpell() {
        this.manaCostPerLevel = 0;
        this.baseManaCost = 700;
        this.baseSpellPower = 20;
        this.spellPowerPerLevel = 0;
        this.castTime = 20;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.WARDEN_SONIC_CHARGE); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        double pct = JJKConfig.CRIMSON_RAY_PERCENT_PER_TICK.get() * 100.0;
        return List.of(
                Component.literal("Range: " + (int) RANGE + " blocks"),
                Component.literal("Duration: " + (DURATION_TICKS / 20) + "s, follows your aim"),
                Component.literal("Damage: " + String.format("%.2f", pct) + "% max health every tick")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            CrimsonRayManager.start(server, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
