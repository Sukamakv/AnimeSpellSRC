package com.example.jjkspells.shadow.spell;

import com.example.jjkspells.shadow.config.ShdConfig;
import com.example.jjkspells.shadow.entity.*;
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

public class SlimeArmamentSpell extends ShdSpell {
    public SlimeArmamentSpell() {
        super("slime_armament", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ShdConfig.SERPENT.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ShdConfig.SERPENT.cooldownSeconds.get();
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
                level.addFreshEntity(SerpentEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("slime_armament failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ShdConfig.SERPENT;
        return List.of(
                Component.literal("Black Serpent's Requiem: a liquid-metal serpent rises beside you and drives down on the aimed point (range "+n(c.range)+"): Piercing Form "+n(c.pierceDamage)+" to everything in its "+n(c.pierceWidth)+"-block line"),
                Component.literal("Predator Form circles for "+n(c.radius)+" blocks, lashing enemies with tendrils ("+n(c.strikeDamage)+" each, up to 5 strikes)"),
                Component.literal("Constriction: the strongest enemy in the circle is bound ("+n(c.bindSlowPercent)+"% slower, bosses only slowed) and crushed for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP"));
    }
}