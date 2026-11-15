package com.example.jjkspells.tempest.spell;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class EndOfAllCreationSpell extends TemSpell {
    public EndOfAllCreationSpell() {
        super("end_of_all_creation", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.AZATHOTH.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.AZATHOTH.cooldownSeconds.get();
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
                level.addFreshEntity(AzathothEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("end_of_all_creation failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.AZATHOTH;
        return List.of(
                Component.literal("Void God Azathoth: your surroundings become an artificial universe ("+n(c.domainRadius)+" blocks). Judgment "+n(c.firstFlat)+" + "+n(c.firstPercent)+"% (Void Extinction Mark: +"+n(c.markBonusPercent)+"% damage taken), destruction "+n(c.destroyFlat)+" + "+n(c.destroyPercent)+"% within "+n(c.radius)+" blocks"),
                Component.literal("Soul Consumption "+n(c.soulFlat)+" + "+n(c.soulPercent)+"%, then the Annihilation within "+n(c.finalRadius)+" blocks: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.bossBonusPercent)+"% against bosses). Mobs below "+n(c.consumeThresholdPercent)+"% health are consumed"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction. The last "+n(c.protectSeconds)+"s: "+n(c.protectReductionPercent)+"% less damage but you cannot move. Afterwards the Chaos Creator's Blessing for "+n(c.blessSeconds)+"s"));
    }
}