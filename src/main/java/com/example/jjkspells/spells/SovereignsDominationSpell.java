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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Sovereign's Domination. Viego thrusts out his free hand; green chains join him to a weakened enemy and its soul is
 * torn out of its body and drawn into his chest.
 *
 * Only an enemy that has Weakness II or stronger can be possessed (which Harrowed Path hands out). On success the
 * target loses 40% of its maximum health for 20 seconds, Viego restores 25% of his own maximum health, and gains
 * +50% spell power for 100 seconds.
 *
 * The cast needs a valid target: without one it just fails (no mana, no cooldown).
 */
@AutoSpellConfig
public class SovereignsDominationSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "sovereigns_domination");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(155)
            .build();

    /** How far away a target can be possessed. */
    public static final float RANGE = 12f;
    /** The target's maximum health is cut by 40% for 20 seconds. */
    public static final int CURSE_TICKS = 400;
    /** Viego heals this fraction of his maximum health... */
    public static final float HEAL_FRACTION = 0.25f;
    /** ...and gets +50% spell power for 100 seconds. */
    public static final int POWER_TICKS = 2000;
    /** Ticks the soul takes to be torn out and drawn in. */
    public static final int PULL_TICKS = 14;

    public SovereignsDominationSpell() {
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 90;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Needs a target with Weakness II or stronger, within " + (int) RANGE + " blocks"),
                Component.literal("Target loses 40% max health for " + CURSE_TICKS / 20 + "s"),
                Component.literal("You heal " + (int) (HEAL_FRACTION * 100) + "% max health and gain +50% spell power for " + POWER_TICKS / 20 + "s")
        );
    }

    /** Runs before the cast starts: without a weakened target there is no cast, so no mana or cooldown is spent. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (!(level instanceof ServerLevel server)) return true;
        LivingEntity target = GuillotineManager.pickTarget(server, entity, RANGE, ViegoFX::hasWeaknessII);
        if (target == null) {
            if (entity instanceof ServerPlayer player) {
                boolean anyTarget = GuillotineManager.pickTarget(server, entity, RANGE) != null;
                player.displayClientMessage(Component.literal(anyTarget ? "Target needs Weakness II or stronger" : "No target in range"), true);
            }
            return false;
        }
        DominationManager.lock(entity, target);
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            DominationManager.start(server, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
