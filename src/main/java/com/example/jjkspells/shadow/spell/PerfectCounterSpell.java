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

public class PerfectCounterSpell extends ShdSpell {
    public PerfectCounterSpell() {
        super("perfect_counter", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ShdConfig.COUNTER.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ShdConfig.COUNTER.cooldownSeconds.get();
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
                level.addFreshEntity(CounterEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("perfect_counter failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ShdConfig.COUNTER;
        return List.of(
                Component.literal("You lower the sword and wait. For "+n(c.windowSeconds)+"s every attack on you (range "+n(c.range)+") is caught, deals no damage and is answered by one counter: "+n(c.counterFlat)+" + "+n(c.counterPercent)+"% max HP"),
                Component.literal("Perfect Counter: an attack that lands in the last "+n(c.perfectSeconds)+"s of the window adds "+n(c.perfectBonus)+" damage and interrupts the attacker for "+n(c.interruptSeconds)+"s (bosses are only slowed)"),
                Component.literal("If nobody attacks, "+n(c.refundPercent)+"% of the cooldown is refunded"));
    }
}