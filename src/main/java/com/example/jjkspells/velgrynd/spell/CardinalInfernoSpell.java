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

public class CardinalInfernoSpell extends VgrSpell {
    public CardinalInfernoSpell() {
        super("cardinal_inferno", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.INFERNO.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.INFERNO.cooldownSeconds.get();
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
                level.addFreshEntity(InfernoEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("cardinal_inferno failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.INFERNO;
        return List.of(
                Component.literal("Seven suns fall across "+n(c.radius)+" blocks at the aimed point (range "+n(c.range)+"): "+n(c.sunFlat)+" each within "+n(c.sunRadius)+" blocks. Solar Branding: +"+n(c.brandPercent)+"% damage per sun hit (max +"+n(c.brandMaxPercent)+"%)"),
                Component.literal("The field slows enemies "+n(c.slowPercent)+"% and cuts healing "+n(c.healPercent)+"%. The suns converge for "+n(c.convergeFlat)+" + "+n(c.convergePercent)+"%"),
                Component.literal("The supernova hits "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health). You take "+n(c.selfReductionPercent)+"% less damage"));
    }
}