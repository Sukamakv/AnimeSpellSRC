package com.example.jjkspells.velgrynd.spell;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.entity.*;
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

public class FlameGodCthughaSpell extends VgrSpell {
    public FlameGodCthughaSpell() {
        super("flame_god_cthugha", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.CTHUGHA.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.CTHUGHA.cooldownSeconds.get();
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
                level.addFreshEntity(CthughaEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("flame_god_cthugha failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.CTHUGHA;
        return List.of(
                Component.literal("Flame God Cthugha: a stellar domain of "+n(c.domainRadius)+" blocks forms at the aimed point (range "+n(c.range)+"). Judgment "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% leaves the Scorch Extinction Mark (+"+n(c.markBonusPercent)+"% damage taken, -"+n(c.markHealPercent)+"% healing)"),
                Component.literal("Annihilation "+n(c.annihilationFlat)+" + "+n(c.annihilationPercent)+"%, stellar collapse "+n(c.stellarFlat)+" + "+n(c.stellarPercent)+"%, then the final scorching within "+n(c.finalRadius)+" blocks: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.bossBonusPercent)+"% against bosses). Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"),
                Component.literal("You take "+n(c.selfReductionPercent)+"% less damage and cannot move for the last "+n(c.lockSeconds)+"s. Afterwards Cardinal Sovereignty for "+n(c.blessSeconds)+"s; defeating "+ni(c.endKills)+" or more enemies resets Cardinal Cataclysm and heals you"));
    }
}