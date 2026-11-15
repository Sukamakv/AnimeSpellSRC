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

public class RainbladeSpell extends AcheronSpell {
    public RainbladeSpell() {
        super("rainblade", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AcheronConfig.BLADE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AcheronConfig.BLADE.cooldownSeconds.get();
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
                level.addFreshEntity(BladeEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("rainblade failed", t);
            }
        }
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AcheronConfig.BLADE;
        return List.of(
                Component.literal("Space within "+n(c.radius)+" blocks of the aimed point ("+n(c.range)+") is cut three times: "+n(c.cut1)+", "+n(c.cut2)+" and "+n(c.cut3)+" + "+n(c.percent)+"% max HP"),
                Component.literal("Each wave eats up to "+ni(c.knotsPerCut)+" Crimson Knots of every enemy it hits, for +"+n(c.knotBonusPercent)+"% damage each. Gives "+ni(c.dreams)+" Slashed Dream"));
    }
}
