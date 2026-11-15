package com.example.jjkspells.malphite.spell;

import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.entity.*;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.util.Util;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Seismic Shard - Splinter of the Monolith (1.5 s cast, homing stone) */
public class SeismicShardSpell extends MalSpell {
    public SeismicShardSpell() {
        super("seismic_shard", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return MalConfig.SHARD.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MalConfig.SHARD.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public int getCastTime(int spellLevel) {
        return Math.max(1, MalConfig.SHARD.castTicks.get());
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(ModSounds.GRIND.get());
    }

    /** Stone plates split from the arm and gather above the hand while the cast winds up. */
    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        if (level.isClientSide) {
            return;
        }
        int t = entity.tickCount;
        Vec3 look = entity.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
        Vec3 hand = entity.getEyePosition().add(right.scale(0.5)).add(look.scale(0.7)).add(0, 0.1, 0);
        if (t % 2 == 0) {
            Util.fx(level, ModParticles.ROCK.get(), hand.x, hand.y, hand.z, 2, 0.35, 0.35, 0.35, 0.05);
            Util.fx(level, ModParticles.GRAVEL.get(), hand.x, hand.y, hand.z, 3, 0.3, 0.3, 0.3, 0.04);
            Util.fx(level, ModParticles.DUST.get(), entity.getX(), entity.getY() + 1.0, entity.getZ(), 1, 0.4, 0.5, 0.4, 0.01);
        }
        if (t % 6 == 0) {
            Util.fx(level, ModParticles.EMBER.get(), hand.x, hand.y, hand.z, 2, 0.25, 0.25, 0.25, 0.03);
            Util.sound(level, hand.x, hand.y, hand.z, ModSounds.GRAVEL.get(), 0.4f, 0.8f);
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                ShardEntity s = ShardEntity.launch(level, entity);
                level.addFreshEntity(s);
                Util.swing(entity);
                Util.sound(level, s.getX(), s.getY(), s.getZ(), ModSounds.THROW.get(), 1.4f, 0.9f);
                Util.blast(level, s.getX(), s.getY() - 0.4, s.getZ(), 0.8, 10);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Seismic Shard failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = MalConfig.SHARD;
        return List.of(
                Component.literal("Hurls a homing stone up to " + Utils.stringTruncation(c.range.get(), 0) + " blocks"),
                Component.literal(Utils.stringTruncation(c.flat.get(), 0) + "+" + Utils.stringTruncation(c.percent.get(), 0) + "% max HP; "+Utils.stringTruncation(c.aoeFlat.get(), 0)+" to enemies within "+Utils.stringTruncation(c.aoeRadius.get(), 0)+" blocks"),
                Component.literal("Steals " + Utils.stringTruncation(c.slowPercent.get(), 0) + "% movement speed and shatters armor by " + Utils.stringTruncation(c.armorPercent.get(), 0) + "%"));
    }
}
