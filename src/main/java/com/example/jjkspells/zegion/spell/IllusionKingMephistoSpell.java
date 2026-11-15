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

public class IllusionKingMephistoSpell extends ZegSpell {
    public IllusionKingMephistoSpell() {
        super("illusion_king_mephisto", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ZegConfig.MEPHISTO.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.MEPHISTO.cooldownSeconds.get();
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
                level.addFreshEntity(MephistoEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("illusion_king_mephisto failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.MEPHISTO;
        return List.of(
                Component.literal("A "+t(c.domainRadius.get())+" block artificial dimension: enemies are slowed "+t(c.slowPercent.get())+"% and deal "+t(c.outDownPercent.get())+"% less damage"),
                Component.literal("Four judgments: "+t(c.firstFlat.get())+", "+t(c.execFlat.get())+", "+t(c.collapseFlat.get())+" and "+t(c.finalFlat.get())+" + up to "+t(c.finalPercent.get())+"% max HP"),
                Component.literal("Afterwards: Sovereign of the Void for "+t(c.afterSeconds.get())+"s (+"+t(c.afterDamagePercent.get())+"% damage, "+t(c.afterReductionPercent.get())+"% damage reduction)"));
    }
}
