package com.example.jjkspells.diablo.spell;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.entity.*;
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

public class NetherApocalypseSpell extends DiaSpell {
    public NetherApocalypseSpell() {
        super("nether_apocalypse", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return DiaConfig.NETHER.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return DiaConfig.NETHER.cooldownSeconds.get();
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
                level.addFreshEntity(NetherApocalypseEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("nether_apocalypse failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = DiaConfig.NETHER;
        return List.of(
                Component.literal("Black Sun of Hell: a black sun rises over the aimed point (range "+n(c.range)+") and falls: "+n(c.impactFlat)+" + "+n(c.impactPercent)+"% max HP within "+n(c.radius)+" blocks, launching enemies"),
                Component.literal("Infernal Supernova ("+n(c.supernovaFlat)+" + "+n(c.supernovaPercent)+"% within "+n(c.superRadius)+") sets enemies on Eternal Hellfire: "+n(c.hellfireDps)+"/s for "+n(c.hellfireSeconds)+"s and "+n(c.hellfireHealCutPercent)+"% less healing"),
                Component.literal("Hell's Final Breath: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%, +"+n(c.ignitionBonusPercent)+"% against burning enemies. You take "+n(c.immunityReductionPercent)+"% less damage and cannot be moved during the snap and impact"));
    }
}
