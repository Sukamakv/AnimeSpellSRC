package com.example.jjkspells.pantheon.spell;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.entity.StarfallEntity;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Spell 4: Starfall Execution - The Spear of War (aerial leap and ground impact). */
public class StarfallExecutionSpell extends PanSpell {
    public StarfallExecutionSpell() {
        super("starfall_execution", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return PanConfig.STAR.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return PanConfig.STAR.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(StarfallEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Starfall Execution failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = PanConfig.STAR;
        return List.of(
                Component.literal("Leap into the sky and dive onto a point up to " + Utils.stringTruncation(c.range.get(), 0) + " blocks away"),
                Component.literal("Impact: " + Utils.stringTruncation(c.coreFlat.get(), 0) + " + " + Utils.stringTruncation(c.corePercent.get(), 0) + "% max HP at the centre, "
                        + Utils.stringTruncation(c.outerFlat.get(), 0) + " + " + Utils.stringTruncation(c.outerPercent.get(), 0) + "% out to " + Utils.stringTruncation(c.radius.get(), 0) + " blocks"),
                Component.literal("The spearpoint stuns and sets enemies ablaze"));
    }
}
