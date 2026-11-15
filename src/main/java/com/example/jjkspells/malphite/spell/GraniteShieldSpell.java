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

/** Granite Shield - Heart of the Mountain (armor, reflection, detonation) */
public class GraniteShieldSpell extends MalSpell {
    public GraniteShieldSpell() {
        super("granite_shield", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return MalConfig.GRANITE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MalConfig.GRANITE.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
        return h != null ? h : super.getCastStartAnimation();
    }

    /** A second cast while the armor is up detonates it instead of paying for a new one. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        if (!level.isClientSide && GraniteEntity.isActive(entity)) {
            GraniteEntity.tryRelease(entity);
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, data);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                if (GraniteEntity.tryRelease(entity)) {
                    return;
                }
                level.addFreshEntity(GraniteEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Granite Shield failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = MalConfig.GRANITE;
        return List.of(
                Component.literal("Armor of granite for "+Utils.stringTruncation(c.durationSeconds.get(), 0)+"s: "+Utils.stringTruncation(c.reductionPercent.get(), 0)+"% less damage, no knockback"),
                Component.literal("Reflects "+Utils.stringTruncation(c.reflectPercent.get(), 0)+"% of prevented damage and stores the rest"),
                Component.literal("Detonates in "+Utils.stringTruncation(c.radius.get(), 0)+" blocks: "+Utils.stringTruncation(c.flat.get(), 0)+" + "+Utils.stringTruncation(c.percent.get(), 0)+"% max HP plus stored force; cast again to release early"));
    }
}
