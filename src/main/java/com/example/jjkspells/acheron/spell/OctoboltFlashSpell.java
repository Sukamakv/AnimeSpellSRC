package com.example.jjkspells.acheron.spell;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.entity.*;
import com.example.jjkspells.acheron.util.Nihility;
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

public class OctoboltFlashSpell extends AcheronSpell {
    public OctoboltFlashSpell() {
        super("octobolt_flash", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AcheronConfig.OCTO.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AcheronConfig.OCTO.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(OctoEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("octobolt_flash failed", t);
            }
        }
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AcheronConfig.OCTO;
        return List.of(
                Component.literal("A violet bolt carries you to the target ("+n(c.range)+" blocks): five cuts of "+n(c.hitFlat)+" + "+n(c.hitPercent)+"% max HP within "+n(c.primaryRadius)+" blocks, lightning for "+n(c.nearbyDamage)+" three times on everything within "+n(c.adjacentRadius)+" blocks"),
                Component.literal("The target is tied with "+ni(c.knots)+" Crimson Knot and you gain "+ni(c.dreams)+" Slashed Dream"));
    }
}
