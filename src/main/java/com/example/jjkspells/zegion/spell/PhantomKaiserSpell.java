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

public class PhantomKaiserSpell extends ZegSpell {
    public PhantomKaiserSpell() {
        super("phantom_kaiser", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ZegConfig.PHANTOM.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.PHANTOM.cooldownSeconds.get();
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
                AssaultEntity e = AssaultEntity.create(level, entity);
                if (e != null) {
                    level.addFreshEntity(e);
                }
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("phantom_kaiser failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.PHANTOM;
        return List.of(
                Component.literal("Up to "+c.strikes.get()+" blinking strikes of "+t(c.strikeDamage.get())+" damage, "+t(c.reductionPercent.get())+"% damage reduction meanwhile"),
                Component.literal("Dimensional finisher: "+t(c.finisherFlat.get())+" + "+t(c.finisherPercent.get())+"% max HP (more after "+c.perfectStrikes.get()+" strikes or on weakened enemies)"),
                Component.literal("Phantom collapse in "+t(c.radius.get())+" blocks: "+t(c.detonationFlat.get())+" + "+t(c.detonationPercent.get())+"% max HP"));
    }
}
