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

public class MegiddoSpell extends RimSpell {
    public MegiddoSpell() {
        super("megiddo", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return RimConfig.MEGIDDO.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return RimConfig.MEGIDDO.cooldownSeconds.get();
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
                boolean day = MegiddoEntity.daylight(level, entity.position());
                double range = day ? RimConfig.MEGIDDO.daylightRange.get() : RimConfig.MEGIDDO.range.get();
                Vec3 aim = Util.aimPoint(level, entity, range);
                Double g = Util.findGround(level, aim.x, aim.y + 1, aim.z);
                Vec3 p = g != null ? new Vec3(aim.x, g, aim.z) : aim;
                level.addFreshEntity(MegiddoEntity.create(level, entity, p, day));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("megiddo failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = RimConfig.MEGIDDO;
        return List.of(
                Component.literal("God's Wrath: lenses of water form in the sky above the marked area (range "+n(c.range)+", more in clear daylight); "+ni(c.beamCount)+" beams of "+n(c.beamFlat)+" strike the weakest enemies within "+n(c.radius)+" blocks"),
                Component.literal("A focused judgment hits the weakest enemy for "+n(c.focusFlat)+" + "+n(c.focusPercent)+"% max HP (+"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health)"),
                Component.literal("A final rain hits everyone in the area for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% max HP; +"+n(c.daylightBonusPercent)+"% damage under a clear sky by day"));
    }
}
