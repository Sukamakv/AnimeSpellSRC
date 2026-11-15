package com.example.jjkspells.tempest.spell;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class FractureOfInfiniteWorldsSpell extends TemSpell {
    public FractureOfInfiniteWorldsSpell() {
        super("fracture_of_infinite_worlds", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.FRACTURE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.FRACTURE.cooldownSeconds.get();
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
                level.addFreshEntity(FractureEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("fracture_of_infinite_worlds failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.FRACTURE;
        return List.of(
                Component.literal("Aim at a target (range "+n(c.range)+"). You step through a spatial portal and emerge in front of it, then a "+n(c.slashLength)+"-block slash cuts across the battlefield: "+n(c.slashFlat)+" + "+n(c.slashPercent)+"%. Struck enemies carry Dimensional Wounds (+"+n(c.woundBonusPercent)+"% damage taken, -"+n(c.woundHealPercent)+"% healing for "+n(c.woundSeconds)+"s) and cannot teleport for "+n(c.noTeleportSeconds)+"s"),
                Component.literal("Then "+ni(c.fractureCount)+" spatial fractures ("+n(c.fractureFlat)+" each) and a collapse within "+n(c.collapseRadius)+" blocks: "+n(c.collapseFlat)+" + "+n(c.collapsePercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"));
    }
}