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

public class TemptationWorldSpell extends DiaSpell {
    public TemptationWorldSpell() {
        super("temptation_world", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return DiaConfig.WORLD.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.WORLD.cooldownSeconds.get();
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
                level.addFreshEntity(TemptationWorldEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("temptation_world failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.WORLD;
        return List.of(
                Component.literal("A black-red kingdom of "+n(c.radius)+" blocks at the aimed point (range "+n(c.range)+"): "+n(c.openFlat)+" + "+n(c.openPercent)+"%, then "+ni(c.spearCount)+" demonic spears ("+n(c.spearFlat)+" each)"),
                Component.literal("Absolute Dominion: -"+n(c.slowPercent)+"% speed, -"+n(c.outPercent)+"% damage, -"+n(c.healCutPercent)+"% healing, +"+n(c.vulnPercent)+"% damage taken; ordinary mobs cannot leave. Collapse "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("The King's Authority: inside the domain you deal +"+n(c.selfSpellPercent)+"% spell damage, take "+n(c.selfReductionPercent)+"% less damage and cannot be knocked back"));
    }
}
