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

public class ParallelExistenceSpell extends VgrSpell {
    public ParallelExistenceSpell() {
        super("parallel_existence", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.PARALLEL.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.PARALLEL.cooldownSeconds.get();
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
                level.addFreshEntity(ParallelEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("parallel_existence failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.PARALLEL;
        return List.of(
                Component.literal("Four parallel bodies surround the aimed target (range "+n(c.range)+"), each firing a beam of "+n(c.beamFlat)+"; every beam that hits adds +"+n(c.vulnPercent)+"% damage taken (max +"+n(c.vulnMaxPercent)+"%)"),
                Component.literal("A cage of fire within "+n(c.radius)+" blocks slows enemies "+n(c.slowPercent)+"% (bosses "+n(c.bossSlowPercent)+"% of that), then judgment "+n(c.judgeFlat)+" + "+n(c.judgePercent)+"%"),
                Component.literal("The finale deals "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%. You take "+n(c.selfReductionPercent)+"% less damage"));
    }
}