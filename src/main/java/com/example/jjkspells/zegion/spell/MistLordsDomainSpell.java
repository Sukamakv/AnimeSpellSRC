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

public class MistLordsDomainSpell extends ZegSpell {
    public MistLordsDomainSpell() {
        super("mist_lords_domain", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ZegConfig.DOMAIN.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.DOMAIN.cooldownSeconds.get();
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
                level.addFreshEntity(DomainEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("mist_lords_domain failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.DOMAIN;
        return List.of(
                Component.literal("A "+t(c.radius.get())+" block domain of violet mist: enemies are slowed "+t(c.slowPercent.get())+"%, deal "+t(c.outDownPercent.get())+"% less damage"),
                Component.literal("Twelve spectral Kaisers strike for "+t(c.strikeDamage.get())+" each; "+t(c.fractureStacks.get())+" strikes add "+t(c.soulDamage.get())+" soul damage"),
                Component.literal("Collapses for "+t(c.finalFlat.get())+" + "+t(c.finalPercent.get())+"% max HP; inside Zegion takes "+t(c.selfReductionPercent.get())+"% less damage"));
    }
}
