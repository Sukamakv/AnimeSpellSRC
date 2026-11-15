package com.example.jjkspells.velgrynd.spell;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public class CardinalCataclysmSpell extends VgrSpell {
    public CardinalCataclysmSpell() {
        super("cardinal_cataclysm", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VgrConfig.STAR.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VgrConfig.STAR.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(StarEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("cardinal_cataclysm failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VgrConfig.STAR;
        return List.of(
                Component.literal("A thousand stars fall on the aimed point (range "+n(c.range)+"): "+n(c.impactFlat)+" + "+n(c.impactPercent)+"% within "+n(c.radius)+" blocks, then a supernova of "+n(c.supernovaFlat)+" + "+n(c.supernovaPercent)+"% within "+n(c.supernovaRadius)+" that leaves Cardinal Overheat (+"+n(c.overheatPercent)+"% damage taken for "+n(c.overheatSeconds)+"s)"),
                Component.literal("The stars contract, then burst for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% within "+n(c.finalRadius)+" blocks (up to +"+n(c.centerBonusPercent)+"% near the centre). Ignores "+n(c.armorIgnorePercent)+"% armor and "+n(c.reduceIgnorePercent)+"% damage reduction"),
                Component.literal("You take "+n(c.protectPercent)+"% less damage during the last "+n(c.protectSeconds)+"s"));
    }
}