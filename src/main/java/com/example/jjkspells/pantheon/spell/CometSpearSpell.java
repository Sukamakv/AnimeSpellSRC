package com.example.jjkspells.pantheon.spell;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.entity.SpearEntity;
import com.example.jjkspells.pantheon.registry.ModParticles;
import com.example.jjkspells.pantheon.registry.ModSounds;
import com.example.jjkspells.pantheon.util.Util;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spell 1: Comet Spear - Piercing the Heavens. A channelled throw: tap for the quick spear, hold up to 2.5 s for the
 * charged one. The spear flies when the channel ends (button released or time out).
 */
public class CometSpearSpell extends PanSpell {
    /** Caster -> game time the channel began. */
    private static final Map<UUID, Long> START = new ConcurrentHashMap<>();
    /** Caster -> game time of the last throw (guards against Iron's completing a cast twice). */
    private static final Map<UUID, Long> THROWN = new ConcurrentHashMap<>();

    public CometSpearSpell() {
        super("comet_spear", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return PanConfig.COMET.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return PanConfig.COMET.cooldownSeconds.get();
    }

    @Override
    public CastType getCastType() {
        return CastType.CONTINUOUS;
    }

    @Override
    public int getCastTime(int spellLevel) {
        return Math.max(1, PanConfig.COMET.maxChannelTicks.get());
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_CONTINUOUS_OVERHEAD", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    private static void begin(Level level, LivingEntity e) {
        long now = level.getGameTime();
        Long old = START.get(e.getUUID());
        Long thrown = THROWN.get(e.getUUID());
        if (old == null || (thrown != null && thrown > old) || now - old > 400) {
            START.put(e.getUUID(), now);
            Util.sound(level, e.getX(), e.getY() + 1, e.getZ(), ModSounds.CHARGE.get(), 1.0f, 1.0f);
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            begin(level, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    /** The golden spark gathering around the spearhead, the sigil beneath the caster, the flash at full charge. */
    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        if (level.isClientSide) {
            return;
        }
        begin(level, entity);
        var c = PanConfig.COMET;
        long ticks = level.getGameTime() - START.get(entity.getUUID());
        double f = Math.min(1.0, ticks / (double) c.fullChargeTicks.get());
        Vec3 look = entity.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 head = entity.getEyePosition().add(right.scale(0.45)).add(look.scale(0.3)).add(0, 0.35, 0);
        if (ticks % 2 == 0) {
            Util.fx(level, ModParticles.SPARK.get(), head.x, head.y, head.z, 1 + (int) (f * 3), 0.25, 0.25, 0.25, 0.04);
            Util.fx(level, ModParticles.EMBER.get(), head.x, head.y, head.z, 1, 0.15, 0.15, 0.15, 0.02);
        }
        if (ticks >= 24 && ticks % 2 == 0) {
            Util.fx(level, ModParticles.FLAME.get(), head.x, head.y, head.z, 1 + (int) (f * 2), 0.1, 0.1, 0.1, 0.02);
        }
        if (ticks == 1 || ticks % 12 == 0) {
            Util.fxDir(level, ModParticles.RING.get(), entity.getX(), entity.getY() + 0.1, entity.getZ(), 0, 0, 0);
            Util.ring(level, ModParticles.SPARK.get(), entity.position().add(0, 0.15, 0), 10, 0.08 + 0.07 * f, 0.02);
        }
        if (ticks == c.fullChargeTicks.get()) {
            Util.fx(level, ModParticles.FLASH.get(), head.x, head.y, head.z, 1, 0, 0, 0, 0);
            Util.fx(level, ModParticles.STAR.get(), head.x, head.y, head.z, 6, 0.3, 0.3, 0.3, 0.1);
            Util.sound(level, head.x, head.y, head.z, ModSounds.STAR.get(), 0.8f, 1.2f);
        }
    }

    /** Throws the spear when the channel ends, whether it was released early, ran out, or was cut off. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData data, boolean cancelled) {
        if (!level.isClientSide && entity.isAlive()) {
            long now = level.getGameTime();
            Long start = START.remove(entity.getUUID());
            Long thrown = THROWN.get(entity.getUUID());
            if (thrown == null || now - thrown > 6) {
                THROWN.put(entity.getUUID(), now);
                int charge = start == null ? 0 : (int) (now - start);
                try {
                    SpearEntity.launch(level, entity, charge);
                } catch (Throwable t) {
                    org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Comet Spear launch failed", t);
                }
            }
        }
        super.onServerCastComplete(level, spellLevel, entity, data, cancelled);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = PanConfig.COMET;
        return List.of(
                Component.literal("Tap: " + Utils.stringTruncation(c.quickFlat.get(), 0) + " + " + Utils.stringTruncation(c.quickPercent.get(), 0) + "% max HP, pierces " + c.quickPierce.get() + " enemies"),
                Component.literal("Hold " + Utils.stringTruncation(c.fullChargeTicks.get() / 20.0, 1) + "s: " + Utils.stringTruncation(c.fullFlat.get(), 0) + " + " + Utils.stringTruncation(c.fullPercent.get(), 0) + "% max HP, pierces everything"),
                Component.literal("+" + Utils.stringTruncation(c.executeBonusPercent.get(), 0) + "% damage below " + Utils.stringTruncation(c.executeBelowPercent.get(), 0) + "% health"));
    }
}
