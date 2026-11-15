package com.example.jjkspells.rimuru.spell;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.entity.*;
import com.example.jjkspells.rimuru.util.Util;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PredatorSpell extends RimSpell {
    public PredatorSpell() {
        super("predator", SpellRarity.RARE);
    }

    @Override
    protected int mana() {
        return RimConfig.PREDATOR.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.PREDATOR.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                LivingEntity t = RimEntityBase.pickTarget(entity, RimConfig.PREDATOR.range.get());
                Vec3 p;
                if (t != null) {
                    p = t.position();
                } else {
                    Vec3 aim = Util.aimPoint(level, entity, RimConfig.PREDATOR.range.get());
                    Double g = Util.findGround(level, aim.x, aim.y + 1, aim.z);
                    p = g != null ? new Vec3(aim.x, g, aim.z) : aim;
                }
                level.addFreshEntity(PredatorEntity.create(level, entity, p));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("predator failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.PREDATOR;
        return List.of(
                Component.literal("Gluttonous Devourer: a slime sphere swallows everything within "+n(c.radius)+" blocks of your target (range "+n(c.range)+"); capture "+n(c.initialFlat)+" + "+n(c.initialPercent)+"% max HP, "+n(c.dps)+"/s while consuming, final "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP"),
                Component.literal("Predatory Execution: enemies below "+n(c.executeThresholdPercent)+"% health are consumed (bosses take "+n(c.bossBonus)+" extra damage instead); every enemy gives you "+n(c.healPerEnemy)+" health and mana"),
                Component.literal("Adaptive Consumption: a kill gives you "+n(c.adaptReductionPercent)+"% damage reduction and "+n(c.adaptBonusPercent)+"% spell damage for "+n(c.adaptSeconds)+"s; bosses are slowed by "+n(c.bossSlowPercent)+"% instead of being trapped"));
    }
}
