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

public class BlackLightningSpell extends RimSpell {
    public BlackLightningSpell() {
        super("black_lightning", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return RimConfig.LIGHTNING.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.LIGHTNING.cooldownSeconds.get();
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
                LivingEntity t = RimEntityBase.pickTarget(entity, RimConfig.LIGHTNING.range.get());
                level.addFreshEntity(BlackLightningEntity.create(level, entity, t));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("black_lightning failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.LIGHTNING;
        return List.of(
                Component.literal("Storm of Destruction: a black bolt strikes your target (range "+n(c.range)+"): "+n(c.primaryFlat)+" + "+n(c.primaryPercent)+"% max HP and Electrical Paralysis (slowed, attacks slower)"),
                Component.literal("The lightning then chains to up to "+ni(c.chainMax)+" more enemies within "+n(c.chainRadius)+" blocks for "+n(c.chainFlat)+" each (further and stronger through water)"),
                Component.literal("A final detonation of "+n(c.blastFlat)+" + "+n(c.blastPercent)+"% max HP in "+n(c.blastRadius)+" blocks; striking "+ni(c.overloadCount)+" or more enemies overloads it and speeds you up"));
    }
}
