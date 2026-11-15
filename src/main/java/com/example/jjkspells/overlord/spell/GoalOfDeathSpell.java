package com.example.jjkspells.overlord.spell;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.entity.OverlordFxEntity;
import com.example.jjkspells.overlord.util.Magic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 4. The Goal of All Life Is Death: 1-2 s cast, then a 7 s stand-still charge, then the death wave. */
public class GoalOfDeathSpell extends OverlordSpell {
    public GoalOfDeathSpell() {
        super("goal_of_all_life_is_death", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.LEGENDARY, CastType.LONG, () -> OverlordConfig.GOAL);
    }

    @Override
    public int getCastTime(int spellLevel) {
        return ticks(OverlordConfig.GOAL.castTimeSeconds.get());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            var c = OverlordConfig.GOAL;
            int charge = ticks(c.chargeSeconds.get());
            Vec3 p = new Vec3(entity.getX(), Magic.groundY(level, entity.getX(), entity.getY(), entity.getZ()), entity.getZ());
            level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.GOAL, entity, p, charge + 70, c.radius.get().floatValue(), spellLevel).mark(charge).lock(charge));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.GOAL;
        return List.of(
                Component.literal("Max HP lost: " + Utils.stringTruncation(c.maxHealthLoss.get() * 100, 0) + "% for " + Utils.stringTruncation(c.curseSeconds.get(), 0) + "s"),
                Component.literal("Charge: " + Utils.stringTruncation(c.chargeSeconds.get(), 1) + "s (stand still)"),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(c.radius.get(), 1)));
    }
}
