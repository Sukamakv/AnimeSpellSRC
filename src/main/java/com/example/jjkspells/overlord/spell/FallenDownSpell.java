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

/** 5. Fallen Down: super-tier holy. 1-2 s cast, 6 s stand-still charge, then a golden sphere falls on the target area. */
public class FallenDownSpell extends OverlordSpell {
    public FallenDownSpell() {
        super("fallen_down", SchoolRegistry.HOLY_RESOURCE, SpellRarity.LEGENDARY, CastType.LONG, () -> OverlordConfig.FALLEN);
    }

    @Override
    public int getCastTime(int spellLevel) {
        return ticks(OverlordConfig.FALLEN.castTimeSeconds.get());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            var c = OverlordConfig.FALLEN;
            int charge = ticks(c.chargeSeconds.get());
            Vec3 p = Magic.aimPoint(level, entity, c.range.get());
            level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.FALLEN, entity, p, charge + 10 + 90, c.radius.get().floatValue(), spellLevel).mark(charge).lock(charge));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.FALLEN;
        return List.of(
                Component.literal("Damage: " + Utils.stringTruncation(c.damageFractionOfMaxHealth.get() * Magic.scale(spellLevel) * 100, 0) + "% of max HP"),
                Component.literal("Charge: " + Utils.stringTruncation(c.chargeSeconds.get(), 1) + "s (stand still)"),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(c.radius.get(), 1)));
    }
}
