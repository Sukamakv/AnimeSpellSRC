package com.example.jjkspells.diablo.spell;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.entity.*;
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

public class AbyssalGenesisSpell extends DiaSpell {
    public AbyssalGenesisSpell() {
        super("abyssal_genesis", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return DiaConfig.GENESIS.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.GENESIS.cooldownSeconds.get();
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
                level.addFreshEntity(AbyssalGenesisEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("abyssal_genesis failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.GENESIS;
        return List.of(
                Component.literal("Seven black stars rise over the aimed point (range "+n(c.range)+") and fall on seven enemies, each struck once: "+n(c.starFlat)+" + "+n(c.starPercent)+"% in "+n(c.starImpactRadius)+" blocks"),
                Component.literal("Abyssal Supernova "+n(c.supernovaFlat)+" + "+n(c.supernovaPercent)+"% within "+n(c.radius)+", then Cosmic Collapse "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% within "+n(c.collapseRadius)+" (+"+n(c.bossBonusPercent)+"% against bosses). Ignores "+n(c.armorIgnorePercent)+"% armor"),
                Component.literal("Abyssal Gravity pulls enemies within "+n(c.pullRadius)+" blocks. Starfall Catastrophe: if "+ni(c.catastropheCount)+" or more enemies are struck, the supernova is +"+n(c.catastropheBonusPercent)+"% and the collapse reaches "+n(c.bigCollapseRadius)+" blocks"));
    }
}
