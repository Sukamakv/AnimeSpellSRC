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

public class BlackThunderApocalypseSpell extends VelSpell {
    public BlackThunderApocalypseSpell() {
        super("black_thunder_apocalypse", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return VelConfig.THUNDER.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.THUNDER.cooldownSeconds.get();
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
                level.addFreshEntity(ThunderEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("black_thunder_apocalypse failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.THUNDER;
        return List.of(
                Component.literal("A black thunderstorm opens over the aimed point (range "+n(c.range)+"). The first bolt strikes "+n(c.firstRadius)+" blocks for "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% and paralyses ("+n(c.paralysisSlowPercent)+"% slower, "+n(c.paralysisAtkPercent)+"% slower attacks for "+n(c.paralysisSeconds)+"s)"),
                Component.literal("Then "+ni(c.strikeCount)+" black bolts of "+n(c.strikeFlat)+" fall across "+n(c.radius)+" blocks (at most "+ni(c.maxHitsPerTarget)+" per enemy) while you take "+n(c.selfReductionPercent)+"% less damage"),
                Component.literal("The divine thunder hits "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health). Enemies in water take +"+n(c.waterBonusPercent)+"% lightning damage"));
    }
}