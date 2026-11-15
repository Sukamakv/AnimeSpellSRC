package com.example.jjkspells.prime;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** The four PRIME Gojo spells. Each is a thin shell: the work is in its manager. */
public final class PrimeSpells {
    private PrimeSpells() {
    }

    public static class VoidBlue extends PrimeSpell {
        public VoidBlue() {
            super("void_blue", SchoolRegistry.ENDER_RESOURCE, 60, 600, 30);
        }

        @Override
        public Optional<SoundEvent> getCastStartSound() {
            return Optional.of(SoundEvents.BEACON_ACTIVATE);
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.empty();
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(
                    Component.literal("Follows your aim, " + PrimeConfig.VB_DISTANCE.get().intValue() + " blocks ahead"),
                    Component.literal("Pull range: " + PrimeConfig.VB_PULL_RANGE.get().intValue() + " blocks"),
                    Component.literal("Duration: " + PrimeConfig.VB_SECONDS.get() + "s"),
                    Component.literal("Victims cannot cast or attack"));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel sl) VoidBlueManager.start(sl, entity);
            super.onCast(level, spellLevel, entity, src, data);
        }
    }

    public static class HeavenlyRed extends PrimeSpell {
        public HeavenlyRed() {
            super("heavenly_red", SchoolRegistry.EVOCATION_RESOURCE, 90, 700, 30);
        }

        @Override
        public Optional<SoundEvent> getCastStartSound() {
            return Optional.of(SoundEvents.RESPAWN_ANCHOR_CHARGE);
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.empty();
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(
                    Component.literal("Damage: " + PrimeConfig.HR_FLAT.get().intValue() + " + " + (int) (PrimeConfig.HR_PERCENT.get() * 100) + "% max health"),
                    Component.literal("Blast length: " + PrimeConfig.HR_RANGE.get().intValue() + " blocks"),
                    Component.literal("Erases terrain (see config)"));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel sl) HeavenlyRedManager.start(sl, entity);
            super.onCast(level, spellLevel, entity, src, data);
        }
    }

    public static class DivinePurple extends PrimeSpell {
        public DivinePurple() {
            super("divine_purple", SchoolRegistry.ENDER_RESOURCE, 240, 1400, 40);
        }

        @Override
        public Optional<SoundEvent> getCastStartSound() {
            return Optional.of(SoundEvents.RESPAWN_ANCHOR_CHARGE);
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.empty();
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(
                    Component.literal("Damage: " + (int) (PrimeConfig.DP_PERCENT.get() * 100) + "% max health"),
                    Component.literal("Beam radius: " + PrimeConfig.DP_RADIUS.get() + " blocks, range " + PrimeConfig.DP_RANGE.get().intValue()),
                    Component.literal("Everything in its path gets Slowness VI"));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel sl) DivinePurpleManager.start(sl, entity);
            super.onCast(level, spellLevel, entity, src, data);
        }
    }

    public static class HeavenlyOnslaught extends PrimeSpell {
        public HeavenlyOnslaught() {
            super("heavenly_onslaught", SchoolRegistry.ENDER_RESOURCE, 420, 2600, 40);
        }

        @Override
        public Optional<SoundEvent> getCastStartSound() {
            return Optional.of(SoundEvents.BEACON_ACTIVATE);
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.empty();
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(
                    Component.literal("Domain radius: " + PrimeConfig.HO_RADIUS.get().intValue() + " blocks, " + PrimeConfig.HO_SECONDS.get() + "s"),
                    Component.literal("Rampage: " + PrimeConfig.HO_FLAT.get().intValue() + " + " + (int) (PrimeConfig.HO_PERCENT.get() * 100) + "% max health"),
                    Component.literal("Finale: " + (int) (PrimeConfig.HO_BLAST_PERCENT.get() * 100) + "% max health in a " + PrimeConfig.HO_BLAST_RADIUS.get() + " block blast"));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel sl) HeavenlyOnslaughtManager.start(sl, entity);
            super.onCast(level, spellLevel, entity, src, data);
        }
    }

    public static class BoundlessSlash extends PrimeSpell {
        public BoundlessSlash() {
            super("boundless_slash", SchoolRegistry.EVOCATION_RESOURCE, 45, 700, 20);
        }

        @Override
        public Optional<SoundEvent> getCastStartSound() {
            return Optional.of(SoundEvents.AMETHYST_BLOCK_RESONATE);
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.empty();
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(
                    Component.literal("Damage: " + PrimeConfig.BS_FLAT.get().intValue() + " + " + (int) (PrimeConfig.BS_PERCENT.get() * 100) + "% max health"),
                    Component.literal("A " + PrimeConfig.BS_HEIGHT.get().intValue() + " block tall diagonal blade: hard to aim"),
                    Component.literal("Dismantled: no melee attacks for " + PrimeConfig.BS_DISMANTLE_SECONDS.get() + "s"));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel sl) BoundlessSlashManager.start(sl, entity);
            super.onCast(level, spellLevel, entity, src, data);
        }
    }

    public static class WorldDisintegration extends PrimeSpell {
        public WorldDisintegration() {
            super("world_disintegration", SchoolRegistry.FIRE_RESOURCE, 120, 1800, 10);
        }

        @Override
        public Optional<SoundEvent> getCastStartSound() {
            return Optional.of(SoundEvents.RESPAWN_ANCHOR_CHARGE);
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.empty();
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(
                    Component.literal("Damage: " + PrimeConfig.WD_FLAT.get().intValue() + " + " + (int) (PrimeConfig.WD_PERCENT.get() * 100) + "% max health"),
                    Component.literal("Charges " + PrimeConfig.WD_CHARGE_SECONDS.get() + "s, then a pillar of fire, radius " + PrimeConfig.WD_RADIUS.get().intValue()),
                    Component.literal("Erases terrain (see config)"));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel sl) WorldDisintegrationManager.start(sl, entity);
            super.onCast(level, spellLevel, entity, src, data);
        }
    }

    public static class UniverseSeveringSlash extends PrimeSpell {
        public UniverseSeveringSlash() {
            super("universe_severing_slash", SchoolRegistry.ENDER_RESOURCE, 150, 2000, 10);
        }

        @Override
        public Optional<SoundEvent> getCastStartSound() {
            return Optional.of(SoundEvents.ENDER_DRAGON_GROWL);
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.empty();
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(
                    Component.literal("Cuts: " + PrimeConfig.US_FLAT.get().intValue() + " + " + (int) (PrimeConfig.US_PERCENT.get() * 100) + "% max health"),
                    Component.literal("Ends in a black hole that throws " + PrimeConfig.US_PROJECTILES.get() + " slashes"),
                    Component.literal("Hits cause Dismantled"));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel sl) UniverseSeveringManager.start(sl, entity);
            super.onCast(level, spellLevel, entity, src, data);
        }
    }
}
