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

public class DespairTimeSpell extends DiaSpell {
    public DespairTimeSpell() {
        super("despair_time", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return DiaConfig.DESPAIR.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.DESPAIR.cooldownSeconds.get();
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
                level.addFreshEntity(DespairTimeEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("despair_time failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.DESPAIR;
        return List.of(
                Component.literal("A clock of despair opens a domain of "+n(c.radius)+" blocks at the aimed point (range "+n(c.range)+"): "+n(c.openFlat)+" + "+n(c.openPercent)+"%, then "+n(c.tormentDps)+"/s of Soul Torment"),
                Component.literal("Enemies inside: -"+n(c.slowPercent)+"% speed, -"+n(c.atkPercent)+"% attack speed, -"+n(c.outPercent)+"% damage (bosses "+n(c.bossFactorPercent)+"% of that); time then stops and the domain collapses for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%"),
                Component.literal("Despair Accumulation: +"+n(c.stackBonusPercent)+"% final damage per second spent inside (max "+ni(c.stackMax)+" stacks). Soul Fracture: +"+n(c.fractureVulnPercent)+"% damage taken, -"+n(c.fractureHealPercent)+"% healing for "+n(c.fractureSeconds)+"s"));
    }
}
