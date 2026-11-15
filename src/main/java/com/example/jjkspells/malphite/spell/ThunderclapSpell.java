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

/** Thunderclap - Crushing Earth (ground slam, shockwave, earthquake zone) */
public class ThunderclapSpell extends MalSpell {
    public ThunderclapSpell() {
        super("thunderclap", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return MalConfig.CLAP.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MalConfig.CLAP.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(ClapEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Thunderclap failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = MalConfig.CLAP;
        return List.of(
                Component.literal("Slam the ground: "+Utils.stringTruncation(c.firstFlat.get(), 0)+" + "+Utils.stringTruncation(c.firstPercent.get(), 0)+"% max HP within "+Utils.stringTruncation(c.radius.get(), 0)+" blocks, staggering and crippling attack speed"),
                Component.literal("A shockwave grows to "+Utils.stringTruncation(c.outerRadius.get(), 0)+" blocks, then stone erupts: "+Utils.stringTruncation(c.finalFlat.get(), 0)+" + "+Utils.stringTruncation(c.finalPercent.get(), 0)+"% max HP"),
                Component.literal("Leaves fractured earth that slows enemies for "+Utils.stringTruncation(c.zoneSeconds.get(), 0)+"s"));
    }
}
