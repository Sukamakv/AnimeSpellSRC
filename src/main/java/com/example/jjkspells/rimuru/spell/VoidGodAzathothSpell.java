package com.example.jjkspells.rimuru.spell;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.entity.*;
import com.example.jjkspells.rimuru.util.Util;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class VoidGodAzathothSpell extends RimSpell {
    public VoidGodAzathothSpell() {
        super("void_god_azathoth", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return RimConfig.AZATHOTH.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.AZATHOTH.cooldownSeconds.get();
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
                Vec3 aim = Util.aimPoint(level, entity, RimConfig.AZATHOTH.range.get());
                Double g = Util.findGround(level, aim.x, aim.y + 1, aim.z);
                Vec3 p = g != null ? new Vec3(aim.x, g, aim.z) : aim;
                level.addFreshEntity(AzathothEntity.create(level, entity, p));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("void_god_azathoth failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.AZATHOTH;
        return List.of(
                Component.literal("End of Creation: you become the Void God and mark a place up to "+n(c.range)+" blocks away; a black sun ruptures reality there ("+n(c.beamFlat)+" + "+n(c.beamPercent)+"% max HP), then collapses for "+n(c.collapseFlat)+" + "+n(c.collapsePercent)+"% max HP in "+n(c.radius)+" blocks"),
                Component.literal("Souls within "+n(c.voidRadius)+" blocks are pulled in and struck ("+n(c.soulFlat)+" + "+n(c.soulPercent)+"% max HP); the end of creation hits for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP"),
                Component.literal("Void God Authority ignores "+n(c.armorIgnorePercent)+"% of armor and "+n(c.reduceIgnorePercent)+"% of magic reduction; you are almost invulnerable while casting and gain the Chaos Creator's Blessing for "+n(c.blessSeconds)+"s afterwards"));
    }
}
