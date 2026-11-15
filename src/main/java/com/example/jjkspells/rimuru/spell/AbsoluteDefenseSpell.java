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

public class AbsoluteDefenseSpell extends RimSpell {
    public AbsoluteDefenseSpell() {
        super("absolute_defense", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return RimConfig.DEFENSE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.DEFENSE.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!level.isClientSide && DefenseEntity.tryRelease(entity)) {
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(DefenseEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("absolute_defense failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.DEFENSE;
        return List.of(
                Component.literal("Uriel's Covenant: for "+n(c.durationSeconds)+"s you take "+n(c.reductionPercent)+"% less damage, cannot be knocked back and block ordinary projectiles; you can still move and cast"),
                Component.literal("The first "+ni(c.charges)+" attacks are fully negated; blocked projectiles return as a bolt of "+n(c.reflectDamage)+" damage (max "+ni(c.reflectMax)+")"),
                Component.literal("Cast again (or let it end) for the Covenant Release: "+n(c.explosionFlat)+" + "+n(c.explosionPercent)+"% max HP within "+n(c.radius)+" blocks, plus "+n(c.storedBonus)+" for every "+n(c.storedPer)+" damage prevented (max +"+n(c.storedMax)+")"));
    }
}
