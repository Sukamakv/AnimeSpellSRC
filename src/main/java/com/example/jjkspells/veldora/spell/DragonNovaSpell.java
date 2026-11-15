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

public class DragonNovaSpell extends VelSpell {
    public DragonNovaSpell() {
        super("dragon_nova", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VelConfig.NOVA.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.NOVA.cooldownSeconds.get();
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
                level.addFreshEntity(NovaEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("dragon_nova failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.NOVA;
        return List.of(
                Component.literal("A star of compressed storm dragon energy rises and falls on the aimed point (range "+n(c.range)+"): "+n(c.impactFlat)+" + "+n(c.impactPercent)+"% within "+n(c.radius)+" blocks, then a supernova of "+n(c.supernovaFlat)+" + "+n(c.supernovaPercent)+"% within "+n(c.supernovaRadius)+" that pulls enemies in and leaves Storm Fracture (+"+n(c.fractureVulnPercent)+"% damage taken, -"+n(c.fractureHealPercent)+"% healing for "+n(c.fractureSeconds)+"s)"),
                Component.literal("The star collapses within "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"));
    }
}