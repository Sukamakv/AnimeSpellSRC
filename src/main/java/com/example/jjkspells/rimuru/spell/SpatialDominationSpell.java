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

public class SpatialDominationSpell extends RimSpell {
    public SpatialDominationSpell() {
        super("spatial_domination", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return RimConfig.SPATIAL.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.SPATIAL.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!level.isClientSide && RimEntityBase.pickTarget(entity, RimConfig.SPATIAL.range.get()) == null) {
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                LivingEntity t = RimEntityBase.pickTarget(entity, RimConfig.SPATIAL.range.get());
                if (t != null) {
                    level.addFreshEntity(SpatialEntity.create(level, entity, t));
                }
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("spatial_domination failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.SPATIAL;
        return List.of(
                Component.literal("Severance of Dimensions: you vanish and reappear right behind your target (range "+n(c.range)+"), then a dimensional slash travels "+n(c.slashRange)+" blocks for "+n(c.slashFlat)+" + "+n(c.slashPercent)+"% max HP and Spatial Lock (slow)"),
                Component.literal("Four dimensional cracks open around the target and cut for "+n(c.cutFlat)+" each; the space then collapses for "+n(c.ruptureFlat)+" + "+n(c.rupturePercent)+"% max HP within "+n(c.ruptureRadius)+" blocks"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% of armor; a perfect position behind the target adds "+n(c.perfectBonusPercent)+"% slash damage and a larger rupture"));
    }
}
