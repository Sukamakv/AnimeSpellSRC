package com.example.jjkspells.tempest.spell;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.entity.*;
import com.example.jjkspells.tempest.util.Util;
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

public class AbsoluteLawOfProtectionSpell extends TemSpell {
    public AbsoluteLawOfProtectionSpell() {
        super("absolute_law_of_protection", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.LAW.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.LAW.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!level.isClientSide && LawEntity.tryRelease(entity)) {
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(LawEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("absolute_law_of_protection failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.LAW;
        return List.of(
                Component.literal("Uriel, Absolute Law of Protection: for "+n(c.durationSeconds)+"s you take "+n(c.reductionPercent)+"% less damage, cannot be knocked back or stunned and take no fall damage; you can still move and cast"),
                Component.literal("The first "+ni(c.charges)+" attacks are fully negated; ordinary projectiles are destroyed and answered by a bolt of "+n(c.reflectDamage)+" damage (max "+ni(c.reflectMax)+")"),
                Component.literal("Cast again (or let it end) for the Rejection Release: "+n(c.explosionFlat)+" + "+n(c.explosionPercent)+"% max HP within "+n(c.radius)+" blocks, plus "+n(c.storedBonus)+" for every "+n(c.storedPer)+" damage prevented (max +"+n(c.storedMax)+")"));
    }
}
