package com.example.jjkspells.pantheon.spell;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.entity.AegisEntity;
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

/** Spell 3: Aegis Storm - The Immortal Phalanx (defensive channel with thrusts and a final detonation). */
public class AegisStormSpell extends PanSpell {
    public AegisStormSpell() {
        super("aegis_storm", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return PanConfig.AEGIS.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return PanConfig.AEGIS.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        return super.checkPreCastConditions(level, spellLevel, entity, data) && !com.example.jjkspells.pantheon.util.Util.AEGIS.containsKey(entity.getUUID());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(AegisEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Aegis Storm failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = PanConfig.AEGIS;
        return List.of(
                Component.literal("Channel for " + Utils.stringTruncation(c.durationTicks.get() / 20.0, 1) + "s, stopping " + Utils.stringTruncation(c.frontBlockPercent.get(), 0) + "% of damage from the front"),
                Component.literal(Utils.stringTruncation(c.hitFlat.get(), 0) + " damage thrusts every " + Utils.stringTruncation(c.thrustGap.get() / 20.0, 2) + "s"),
                Component.literal("Final detonation: " + Utils.stringTruncation(c.finalFlat.get(), 0) + " + " + Utils.stringTruncation(c.finalPercent.get(), 0) + "% max HP, stronger for every hit blocked"));
    }
}
