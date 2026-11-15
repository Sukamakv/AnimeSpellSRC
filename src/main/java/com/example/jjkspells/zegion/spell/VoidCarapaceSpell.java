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

public class VoidCarapaceSpell extends ZegSpell {
    public VoidCarapaceSpell() {
        super("void_carapace", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ZegConfig.SING.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.SING.cooldownSeconds.get();
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
                level.addFreshEntity(SingularityEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("void_carapace failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.SING;
        return List.of(
                Component.literal("Throws a singularity: "+t(c.openFlat.get())+" + "+t(c.openPercent.get())+"% max HP, then pulls and slows enemies "+t(c.slowPercent.get())+"%"),
                Component.literal("Devours for "+t(c.dps.get())+" damage per second inside "+t(c.radius.get())+" blocks"),
                Component.literal("Collapses in "+t(c.finalRadius.get())+" blocks for "+t(c.finalFlat.get())+" + "+t(c.finalPercent.get())+"% max HP, +"+t(c.perEnemyBonusPercent.get())+"% per trapped enemy"));
    }
}
