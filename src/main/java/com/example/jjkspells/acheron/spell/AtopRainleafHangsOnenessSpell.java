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

public class AtopRainleafHangsOnenessSpell extends AcheronSpell {
    public AtopRainleafHangsOnenessSpell() {
        super("atop_rainleaf_hangs_oneness", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AcheronConfig.RAIN.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AcheronConfig.RAIN.cooldownSeconds.get();
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
                level.addFreshEntity(RainleafEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("atop_rainleaf_hangs_oneness failed", t);
            }
        }
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AcheronConfig.RAIN;
        return List.of(
                Component.literal("Everything within "+n(c.markRadius)+" blocks of the aimed point ("+n(c.range)+") is tied to you for "+n(c.durationSeconds)+"s and gathers a Crimson Knot every "+n(c.knotInterval)+"s (up to "+ni(c.maxKnots)+")"),
                Component.literal("Each knot hurts for "+n(c.tickFlat)+" + "+n(c.tickPercent)+"% max HP per second and slows by "+n(c.slowPercent)+"%. A marked enemy that dies hands its knots to the nearest enemy. Gives "+ni(c.dreams)+" Slashed Dream"));
    }
}
