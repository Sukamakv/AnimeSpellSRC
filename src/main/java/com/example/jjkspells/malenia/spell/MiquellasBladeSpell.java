package com.example.jjkspells.malenia.spell;

import com.example.jjkspells.malenia.config.MelConfig;
import com.example.jjkspells.malenia.entity.*;
import com.example.jjkspells.malenia.util.Util;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MiquellasBladeSpell extends MelSpell {
    public MiquellasBladeSpell() {
        super("miquellas_blade", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return MelConfig.BLADE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MelConfig.BLADE.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST","SLASH_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        if (!super.checkPreCastConditions(level, spellLevel, entity, data)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        if (WaterfowlEntity.pickTarget(level, entity, MelConfig.BLADE.range.get(), entity.position()) == null) {
            if (entity instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("No target in range"), true);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                LivingEntity t = WaterfowlEntity.pickTarget(level, entity, MelConfig.BLADE.range.get(), entity.position());
                if (t != null) {
                    level.addFreshEntity(BladeEntity.create(level, entity, t));
                }
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("miquellas_blade failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String n(MelConfig.Val<Double> v) {
        return io.redspace.ironsspellbooks.api.util.Utils.stringTruncation(v.get(), 0);
    }

    private static String ni(MelConfig.Val<Integer> v) {
        return String.valueOf(v.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = MelConfig.BLADE;
        return List.of(
                Component.literal("Dashes at a target (range "+n(c.range)+") for "+n(c.dashFlat)+", cuts four times for "+n(c.slashFlat)+" each, thrusts for "+n(c.thrustFlat)+" + "+n(c.thrustPercent)+"% max HP and finishes with a leaping execution of "+n(c.executeFlat)+" + "+n(c.executePercent)+"% max HP"),
                Component.literal("Steals health with every strike (max "+n(c.lifestealCap)+"); takes "+n(c.evasiveReductionPercent)+"% less damage while it lasts"),
                Component.literal("The thrust ignores "+n(c.armorIgnorePercent)+"% of armor; landing every strike on the target empowers the execution"));
    }
}
