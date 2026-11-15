package com.example.jjkspells.acheron.spell;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.entity.*;
import com.example.jjkspells.acheron.util.Nihility;
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

public class SlashedDreamCriesInRedSpell extends AcheronSpell {
    public SlashedDreamCriesInRedSpell() {
        super("slashed_dream_cries_in_red", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AcheronConfig.ULT.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AcheronConfig.ULT.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (Nihility.dreams(entity) < AcheronConfig.ULT.dreamsNeeded.get()) {
            if (!level.isClientSide && entity instanceof net.minecraft.world.entity.player.Player p) {
                p.displayClientMessage(Component.literal("Not enough Slashed Dream: " + Nihility.dreams(entity) + " / " + AcheronConfig.ULT.dreamsNeeded.get()), true);
            }
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                Nihility.spendDreams(entity, AcheronConfig.ULT.dreamsNeeded.get());
                level.addFreshEntity(UltimateEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("slashed_dream_cries_in_red failed", t);
            }
        }
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AcheronConfig.ULT;
        return List.of(
                Component.literal("Needs and spends "+ni(c.dreamsNeeded)+" Slashed Dream (every other Acheron spell gives one). A black hole opens in the middle of the zone ("+n(c.range)+" blocks) and pulls in everything within "+n(c.suckRadius)+" blocks; enemies lose "+n(c.slowPercent)+"% speed and deal "+n(c.outDamagePercent)+"% less damage, you take "+n(c.focusPercent)+"% less damage and cannot be pushed"),
                Component.literal("You cut it 13 times: each slash deals "+n(c.slashFlat)+" + "+n(c.slashPercent)+"% max HP to everything within "+n(c.radius)+" blocks. The final slash ("+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP within "+n(c.finalRadius)+" blocks) makes it explode and shatter reality. "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% magic reduction ignored; half the percent on bosses"));
    }
}
