package com.example.jjkspells.shadow.spell;

import com.example.jjkspells.shadow.config.ShdConfig;
import com.example.jjkspells.shadow.entity.*;
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

public class ShadowsGardenSpell extends ShdSpell {
    public ShadowsGardenSpell() {
        super("shadows_garden", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ShdConfig.GARDEN.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ShdConfig.GARDEN.cooldownSeconds.get();
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
                level.addFreshEntity(GardenEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("shadows_garden failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ShdConfig.GARDEN;
        return List.of(
                Component.literal("The Silent Execution: you vanish and cut up to "+ni(c.maxTargets)+" enemies within "+n(c.range)+" blocks, but nothing lands yet. The cuts only store damage ("+n(c.initialDamage)+" on the aimed target, "+n(c.secondaryDamage)+" on each other)"),
                Component.literal("Delayed Reality: one final line is drawn through the aimed target ("+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP). When the sword is sheathed, every stored hit lands at once"),
                Component.literal("If nothing is in range the cast is refunded. You cannot be touched while it plays"));
    }
}