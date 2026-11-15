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

public class BeelzebuthSpell extends RimSpell {
    public BeelzebuthSpell() {
        super("beelzebuth", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return RimConfig.BEELZEBUTH.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.BEELZEBUTH.cooldownSeconds.get();
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
                Vec3 aim = Util.aimPoint(level, entity, RimConfig.BEELZEBUTH.range.get());
                Double g = Util.findGround(level, aim.x, aim.y + 1, aim.z);
                Vec3 p = g != null ? new Vec3(aim.x, g, aim.z) : aim;
                level.addFreshEntity(BeelzebuthEntity.create(level, entity, p));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("beelzebuth failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.BEELZEBUTH;
        return List.of(
                Component.literal("Soul Consumption: a black vortex (range "+n(c.range)+") pulls in every enemy within "+n(c.radius)+" blocks: "+n(c.initialFlat)+" + "+n(c.initialPercent)+"% max HP, then "+n(c.dps)+"/s, then "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP"),
                Component.literal("Soul Devouring: enemies below "+n(c.executeThresholdPercent)+"% health are consumed (bosses take "+n(c.bossBonus)+" extra); each enemy gives you "+n(c.healPerEnemy)+" health and mana (max "+n(c.healCap)+")"),
                Component.literal("Gluttonous Suppression slows by "+n(c.suppressSlowPercent)+"% and cuts outgoing damage by "+n(c.suppressOutPercent)+"%; "+ni(c.harvestKills)+" or more souls give a Soul Harvest (spell power, healing, Predator reset)"));
    }
}
