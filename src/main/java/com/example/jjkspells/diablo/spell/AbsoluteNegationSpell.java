package com.example.jjkspells.diablo.spell;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.entity.*;
import com.example.jjkspells.diablo.util.Util;
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

public class AbsoluteNegationSpell extends DiaSpell {
    public AbsoluteNegationSpell() {
        super("absolute_negation", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return DiaConfig.NEGATION.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.NEGATION.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!level.isClientSide && NegationEntity.tryRelease(entity)) {
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(NegationEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("absolute_negation failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.NEGATION;
        return List.of(
                Component.literal("Law of the Primordial: for "+n(c.durationSeconds)+"s you take "+n(c.reductionPercent)+"% less damage, cannot be knocked back or stunned and take no fall damage; you can still move and cast"),
                Component.literal("The first "+ni(c.charges)+" attacks are fully negated; ordinary projectiles are destroyed and answered by a bolt of "+n(c.reflectDamage)+" damage (max "+ni(c.reflectMax)+")"),
                Component.literal("Cast again (or let it end) for the Rejection Release: "+n(c.explosionFlat)+" + "+n(c.explosionPercent)+"% max HP within "+n(c.radius)+" blocks, plus "+n(c.storedBonus)+" for every "+n(c.storedPer)+" damage prevented (max +"+n(c.storedMax)+")"));
    }
}
