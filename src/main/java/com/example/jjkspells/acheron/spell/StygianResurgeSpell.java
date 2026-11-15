package com.example.jjkspells.acheron.spell;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.entity.*;
import com.example.jjkspells.acheron.util.Nihility;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public class StygianResurgeSpell extends AcheronSpell {
    public StygianResurgeSpell() {
        super("stygian_resurge", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AcheronConfig.STYX.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AcheronConfig.STYX.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(StygianEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("stygian_resurge failed", t);
            }
        }
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AcheronConfig.STYX;
        return List.of(
                Component.literal("A black sun falls on the aimed point ("+n(c.range)+" blocks): "+n(c.flat)+" + "+n(c.percent)+"% max HP within "+n(c.radius)+" blocks (half the percent on bosses), +"+n(c.knotBonusPercent)+"% per Crimson Knot, and every knot is cleared"),
                Component.literal("A shockwave to "+n(c.shockRadius)+" blocks deals "+n(c.shockFlat)+" + "+n(c.shockPercent)+"%. Gives "+ni(c.dreams)+" Slashed Dream"));
    }
}
