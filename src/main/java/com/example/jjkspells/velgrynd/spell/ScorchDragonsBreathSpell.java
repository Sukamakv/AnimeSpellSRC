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

public class ScorchDragonsBreathSpell extends VgrSpell {
    public ScorchDragonsBreathSpell() {
        super("scorch_dragons_breath", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.BREATH.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.BREATH.cooldownSeconds.get();
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
                level.addFreshEntity(BreathEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("scorch_dragons_breath failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.BREATH;
        return List.of(
                Component.literal("Horizon Incinerator: a stellar beam ("+n(c.width)+" wide, range "+n(c.range)+") fires from your hand. First hit "+n(c.firstFlat)+" + "+n(c.firstPercent)+"%, then "+n(c.dps)+"/s; the Accelerated Destruction grows +"+n(c.accelPercent)+"% per second inside it (max +"+n(c.accelMaxPercent)+"%)"),
                Component.literal("Absolute Scorch: "+n(c.scorchDps)+"/s for "+n(c.scorchSeconds)+"s that cannot be extinguished, -"+n(c.scorchHealPercent)+"% healing. Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"),
                Component.literal("The beam locks and detonates within "+n(c.finalRadius)+" blocks: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%. You take "+n(c.selfReductionPercent)+"% less damage and cannot be knocked back"));
    }
}