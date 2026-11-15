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

public class BirthOfNothingnessSpell extends TemSpell {
    public BirthOfNothingnessSpell() {
        super("birth_of_nothingness", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.NOTHING.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.NOTHING.cooldownSeconds.get();
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
                level.addFreshEntity(NothingEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("birth_of_nothingness failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.NOTHING;
        return List.of(
                Component.literal("A star of nothingness rises over you and falls on the aimed point (range "+n(c.range)+"): "+n(c.impactFlat)+" + "+n(c.impactPercent)+"% within "+n(c.radius)+" blocks, then a supernova of "+n(c.supernovaFlat)+" + "+n(c.supernovaPercent)+"% within "+n(c.supernovaRadius)+"; survivors suffer Nihility Fracture (+"+n(c.fractureVulnPercent)+"% damage taken, -"+n(c.fractureHealPercent)+"% healing)"),
                Component.literal("Space then folds inward ("+n(c.gravityRadius)+" blocks) and the Birth of Nothingness hits "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"));
    }
}