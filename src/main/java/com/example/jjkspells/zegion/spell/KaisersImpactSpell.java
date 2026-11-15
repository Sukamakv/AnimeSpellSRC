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

public class KaisersImpactSpell extends ZegSpell {
    public KaisersImpactSpell() {
        super("kaisers_impact", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ZegConfig.IMPACT.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.IMPACT.cooldownSeconds.get();
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
                level.addFreshEntity(ImpactEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("kaisers_impact failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.IMPACT;
        return List.of(
                Component.literal("Zegion vanishes and punches through the target: "+t(c.punchFlat.get())+" + "+t(c.punchPercent.get())+"% max HP, ignores "+t(c.armorIgnorePercent.get())+"% of armor"),
                Component.literal("Heaven-shattering shockwave ("+t(c.shockRadius.get())+" blocks) and dimensional rupture ("+t(c.ruptureRadius.get())+" blocks)"),
                Component.literal("Kaiser Fracture for "+t(c.fractureSeconds.get())+"s: +"+t(c.fractureDamagePercent.get())+"% physical damage taken, -"+t(c.fractureHealPercent.get())+"% healing"));
    }
}
