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

public class ScorchDragonsDominionSpell extends VgrSpell {
    public ScorchDragonsDominionSpell() {
        super("scorch_dragons_dominion", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.DOMINION.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.DOMINION.cooldownSeconds.get();
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
                level.addFreshEntity(DominionEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("scorch_dragons_dominion failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.DOMINION;
        return List.of(
                Component.literal("A world without cold spreads "+n(c.radius)+" blocks around you. Ignition "+n(c.firstFlat)+" + "+n(c.firstPercent)+"%, then "+n(c.dps)+"/s. Enemies: -"+n(c.healPercent)+"% healing, -"+n(c.slowPercent)+"% speed, -"+n(c.outPercent)+"% damage dealt"),
                Component.literal("You gain +"+n(c.spellBonusPercent)+"% spell damage, "+n(c.selfReductionPercent)+"% damage reduction and no knockback"),
                Component.literal("The final scorching deals "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"));
    }
}