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

public class TemptationKingAzazelSpell extends DiaSpell {
    public TemptationKingAzazelSpell() {
        super("temptation_king_azazel", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return DiaConfig.AZAZEL.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.AZAZEL.cooldownSeconds.get();
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
                level.addFreshEntity(TemptationKingAzazelEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("temptation_king_azazel failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.AZAZEL;
        return List.of(
                Component.literal("Twenty seconds of judgment around you: first judgment "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% (Extinction Mark: +"+n(c.markBonusPercent)+"% damage taken), dimensional destruction "+n(c.dimFlat)+" + "+n(c.dimPercent)+"% within "+n(c.radius)+" blocks"),
                Component.literal("Soul Devouring pulls everything within "+n(c.domainRadius)+" blocks ("+n(c.soulFlat)+" + "+n(c.soulPercent)+"%, below "+n(c.executeThresholdPercent)+"% health is executed); the Absolute Extinction hits "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction. The last 10s: "+n(c.protectReductionPercent)+"% less damage but you cannot move. Afterwards True Authority for "+n(c.authSeconds)+"s: +"+n(c.authSpellPercent)+"% spell damage, "+n(c.authReductionPercent)+"% less damage taken, +"+n(c.authSpeedPercent)+"% speed"));
    }
}
