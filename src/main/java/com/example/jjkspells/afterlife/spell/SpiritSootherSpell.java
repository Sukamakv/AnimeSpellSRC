package com.example.jjkspells.afterlife.spell;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.entity.SpiritGhostEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Spell 3: Spirit Soother - The Ghost's Final Embrace (summoned ghost bomb + heal). */
public class SpiritSootherSpell extends AfterlifeSpell {
    public SpiritSootherSpell() {
        super("spirit_soother", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return AfterlifeConfig.SOOTHER.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AfterlifeConfig.SOOTHER.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            SpiritGhostEntity.launch(level, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AfterlifeConfig.SOOTHER;
        return List.of(
                Component.literal("Explosion: " + Utils.stringTruncation(c.explFlat.get(), 0) + " + " + Utils.stringTruncation(c.explPercent.get(), 1) + "% max HP"),
                Component.literal("Radius " + Utils.stringTruncation(c.radius.get(), 0) + " blocks, sets enemies on fire"),
                Component.literal("Heals " + Utils.stringTruncation(c.healPercent.get(), 0) + "% max HP per enemy hit (max " + Utils.stringTruncation(c.healCapPercent.get(), 0) + "%)"));
    }
}
