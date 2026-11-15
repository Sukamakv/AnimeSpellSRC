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

public class ImaginaryCollapseSpell extends RimSpell {
    public ImaginaryCollapseSpell() {
        super("imaginary_collapse", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return RimConfig.COLLAPSE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.COLLAPSE.cooldownSeconds.get();
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
                Vec3 aim = Util.aimPoint(level, entity, RimConfig.COLLAPSE.range.get());
                Double g = Util.findGround(level, aim.x, aim.y + 1, aim.z);
                Vec3 p = g != null ? new Vec3(aim.x, g, aim.z) : aim;
                level.addFreshEntity(CollapseEntity.create(level, entity, p));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("imaginary_collapse failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.COLLAPSE;
        return List.of(
                Component.literal("Nihility Break: a void sphere flies to the marked point (range "+n(c.range)+"): "+n(c.impactFlat)+" + "+n(c.impactPercent)+"% max HP within "+n(c.radius)+" blocks, slowing and weakening everyone hit"),
                Component.literal("Gravity then pulls everything within "+n(c.ruptureRadius)+" blocks in, a second explosion deals "+n(c.blastFlat)+" + "+n(c.blastPercent)+"% max HP, and a nihility rupture "+n(c.ruptureFlat)+" + "+n(c.rupturePercent)+"% max HP"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% of armor and "+n(c.reduceIgnorePercent)+"% of damage reduction; hitting "+ni(c.overloadCount)+" or more enemies with the first explosion overloads the rest"));
    }
}
