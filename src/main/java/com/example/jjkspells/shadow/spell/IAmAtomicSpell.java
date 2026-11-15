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

public class IAmAtomicSpell extends ShdSpell {
    public IAmAtomicSpell() {
        super("i_am_atomic", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ShdConfig.ATOMIC.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ShdConfig.ATOMIC.cooldownSeconds.get();
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
                level.addFreshEntity(AtomicEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("i_am_atomic failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ShdConfig.ATOMIC;
        return List.of(
                Component.literal("I Am Atomic: Shadow channels for 7s, wreathed in violet magic that gathers along himself and his ebony sword. While he channels he takes "+n(c.channelReductionPercent)+"% less damage and cannot be pushed or move"),
                Component.literal("One stroke of the sword at the point you aim at (range "+n(c.range)+") releases a single torrent of blue-violet light: everything within "+n(c.radius)+" blocks takes "+n(c.mainFlat)+" + "+n(c.mainPercent)+"% max health, ignoring "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% magic reduction"),
                Component.literal("A delayed shockwave reaches "+n(c.shockRadius)+" blocks and only pushes. Allies, your companions and protected names (Alexia) are never harmed. The crater is an illusion; blocks break only if the destructive mode is enabled in the config"));
    }
}
