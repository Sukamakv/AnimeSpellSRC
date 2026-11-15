package com.example.jjkspells.zegion.spell;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.*;
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

public class KaisersFinalFormSpell extends ZegSpell {
    public KaisersFinalFormSpell() {
        super("kaisers_final_form", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ZegConfig.FORM.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.FORM.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(SovereignEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("kaisers_final_form failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.FORM;
        return List.of(
                Component.literal("For "+t(c.durationSeconds.get())+"s: +"+t(c.physicalPercent.get())+"% physical damage, +"+t(c.speedPercent.get())+"% speed, "+t(c.reductionPercent.get())+"% damage reduction, +"+t(c.meleeFlat.get())+" melee damage"),
                Component.literal("Every "+c.strikesPerBlast.get()+"rd melee hit detonates ("+t(c.blastRadius.get())+" blocks, "+t(c.blastFlat.get())+" + "+t(c.blastPercent.get())+"% max HP)"),
                Component.literal("Wing shockwave "+t(c.openFlat.get())+" + "+t(c.openPercent.get())+"% max HP; final detonation "+t(c.finalFlat.get())+" + "+t(c.finalPercent.get())+"% max HP in "+t(c.finalRadius.get())+" blocks"));
    }
}
