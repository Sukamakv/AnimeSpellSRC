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

public class DimensionalScorchSpell extends VgrSpell {
    public DimensionalScorchSpell() {
        super("dimensional_scorch", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.PURSUIT.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.PURSUIT.cooldownSeconds.get();
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
                level.addFreshEntity(PursuitEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dimensional_scorch failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.PURSUIT;
        return List.of(
                Component.literal("Brands the nearest enemy at the aimed point (range "+n(c.range)+"): "+n(c.brandFlat)+" + "+n(c.brandPercent)+"%. Dimensional portals then hunt it with "+ni(c.strikeCount)+" strikes of "+n(c.strikeFlat)+" that accelerate (+"+n(c.accelPercent)+"% each, max +"+n(c.accelMaxPercent)+"%); the marked target takes +"+n(c.markBonusPercent)+"%"),
                Component.literal("The final strike hits "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%, ignoring "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"));
    }
}