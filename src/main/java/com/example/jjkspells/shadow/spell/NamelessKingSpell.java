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

public class NamelessKingSpell extends ShdSpell {
    public NamelessKingSpell() {
        super("nameless_king", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ShdConfig.THRONE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ShdConfig.THRONE.cooldownSeconds.get();
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
                level.addFreshEntity(ThroneEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("nameless_king failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ShdConfig.THRONE;
        return List.of(
                Component.literal("The Throne of False Shadows: a gothic throne rises ("+n(c.radius)+" block stage) and shocks everyone for "+n(c.shockDamage)+". Five false shadows stand around it; the real one sits on the throne, untouchable"),
                Component.literal("Anyone who attacks you or a false shadow is answered by a shadow behind them: "+n(c.counterDamage)+" damage (max "+ni(c.maxCounters)+" counters) while you take 60% less damage"),
                Component.literal("King's Judgment: the strongest enemy and all within "+n(c.finalRadius)+" blocks of it take "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP"));
    }
}