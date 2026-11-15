package com.example.jjkspells.veldora.spell;

import com.example.jjkspells.veldora.config.VelConfig;
import com.example.jjkspells.veldora.entity.*;
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

public class EndOfTheEternalStormSpell extends VelSpell {
    public EndOfTheEternalStormSpell() {
        super("end_of_the_eternal_storm", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VelConfig.ETERNAL.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.ETERNAL.cooldownSeconds.get();
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
                level.addFreshEntity(EternalEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("end_of_the_eternal_storm failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.ETERNAL;
        return List.of(
                Component.literal("Chaos King Nyarlathotep, ultimate: an eternal storm dimension ("+n(c.domainRadius)+" blocks) opens at the aimed point (range "+n(c.range)+") for 22 seconds. Judgment "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% (Chaos Extinction Mark: +"+n(c.markBonusPercent)+"% damage taken), dragon annihilation "+n(c.annihilationFlat)+" + "+n(c.annihilationPercent)+"%, probability collapse "+n(c.collapseFlat)+" + "+n(c.collapsePercent)+"%"),
                Component.literal("The final cataclysm within "+n(c.finalRadius)+" blocks: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.bossBonusPercent)+"% against bosses). Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"),
                Component.literal("Probability Domination: "+n(c.negatePercent)+"% of attacks against you miss, "+n(c.bonusChancePercent)+"% of yours deal +"+n(c.bonusDamagePercent)+"%. The last "+n(c.protectSeconds)+"s you take "+n(c.protectReductionPercent)+"% less damage but cannot move. Afterwards True Dragon's Blessing for "+n(c.blessSeconds)+"s"));
    }
}