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

public class PhantomBlossomSpell extends MelSpell {
    public PhantomBlossomSpell() {
        super("phantom_blossom", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return MelConfig.PHANTOM.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MelConfig.PHANTOM.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST","CHARGE_RAISED_HAND");
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
        if (WaterfowlEntity.pickTarget(level, entity, MelConfig.PHANTOM.range.get(), entity.position()) == null) {
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
                LivingEntity t = WaterfowlEntity.pickTarget(level, entity, MelConfig.PHANTOM.range.get(), entity.position());
                if (t != null) {
                    level.addFreshEntity(PhantomEntity.create(level, entity, t));
                }
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("phantom_blossom failed", t);
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
        var c = MelConfig.PHANTOM;
        return List.of(
                Component.literal("Rises and calls seven crimson phantoms, each striking its own target for "+n(c.phantomFlat)+" (range "+n(c.range)+")"),
                Component.literal("Each hit leaves a Scarlet Wound; "+ni(c.stackThreshold)+" wounds burst into Scarlet Rot ("+n(c.rotDps)+"/s for "+n(c.rotSeconds)+"s)"),
                Component.literal("Then dives at the primary target for "+n(c.diveFlat)+" + "+n(c.divePercent)+"% max HP; if every phantom hit: bonus damage, a heal and a slow"));
    }
}
