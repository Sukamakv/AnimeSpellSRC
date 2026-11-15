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

public class DragonBreathSpell extends VelSpell {
    public DragonBreathSpell() {
        super("dragon_breath", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return VelConfig.BREATH.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.BREATH.cooldownSeconds.get();
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
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dragon_breath failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.BREATH;
        return List.of(
                Component.literal("A true dragon breath tears along your line of sight (up to "+n(c.range)+" blocks, "+n(c.width)+" wide). The first hit does "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% and applies Draconic Overload (+"+n(c.overloadVulnPercent)+"% damage taken, -"+n(c.overloadHealPercent)+"% healing for "+n(c.overloadSeconds)+"s)"),
                Component.literal("Enemies inside take "+n(c.dps)+"/s; the endpoint then detonates within "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction. Immovable Dragon: "+n(c.selfReductionPercent)+"% less damage while breathing"));
    }
}