package com.example.jjkspells.spells;

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
 * World Cutting Slash. Sukuna's technique: after a short chant the world itself is cut along a huge grid of
 * slashes in front of the caster. Terrain is split into slabs along the grid, and everything inside is cut
 * for damage that ignores armor.
 */
@AutoSpellConfig
public class WorldCuttingSlashSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "world_cutting_slash");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(90)
            .build();

    // Balance: World Cutting Slash > Hollow Purple: Nine Ropes > Hollow Purple > Fuga.
    // (Originally 3.0 / 20% / 1.6x; cut by about 48-50%.)
    /** Damage dealt through Iron's = spell power * this. */
    public static final float DAMAGE_FACTOR = 3.1f;
    /** Plus this fraction of max health, dealt as magic damage (ignores armor). */
    public static final float PERCENT = 0.10f;
    /** Anything standing right on a cut takes this much more. */
    public static final float DIRECT_BONUS = 1.5f;

    public WorldCuttingSlashSpell() {
        this.manaCostPerLevel = 70;
        this.baseSpellPower = 16;
        this.spellPowerPerLevel = 4;
        this.castTime = 25;
        this.baseManaCost = 450;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.ENDER_DRAGON_GROWL); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    /** How far the cut reaches in front of the caster: 40 blocks at level 1 up to 72 at level 5. */
    public int getRange(int spellLevel) { return 40 + 8 * (spellLevel - 1); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float power = getSpellPower(spellLevel, caster);
        return List.of(
                Component.literal("Range: " + getRange(spellLevel) + " blocks forward"),
                Component.literal("Cut: " + String.format("%.1f", power * DAMAGE_FACTOR) + " + "
                        + (int) (PERCENT * 100) + "% max health (ignores armor)"),
                Component.literal("Splits the terrain along a grid")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            WorldCuttingSlashManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
