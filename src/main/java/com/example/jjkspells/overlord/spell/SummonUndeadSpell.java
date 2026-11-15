package com.example.jjkspells.overlord.spell;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.entity.OverlordFxEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 1. Summon Undead: purple circle, skeletal hands, 7-10 netherite skeleton soldiers. */
public class SummonUndeadSpell extends OverlordSpell {
    public SummonUndeadSpell() {
        super("summon_undead", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.EPIC, CastType.INSTANT, () -> OverlordConfig.UNDEAD);
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_TWO_HANDS;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            Vec3 p = new Vec3(entity.getX(), com.example.jjkspells.overlord.util.Magic.groundY(level, entity.getX(), entity.getY(), entity.getZ()), entity.getZ());
            float r = OverlordConfig.UNDEAD.summonRadius.get().floatValue();
            level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.SUMMON, entity, p, 50, r, spellLevel));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.UNDEAD;
        return List.of(
                Component.literal("Minions: " + c.minMinions.get() + "-" + c.maxMinions.get()),
                Component.literal("450 HP, 20 armour, 75 dmg, Wither on hit, Speed I. Minion HP: " + Utils.stringTruncation(com.example.jjkspells.overlord.entity.UndeadSoldierEntity.HEALTH * com.example.jjkspells.overlord.util.Magic.scale(spellLevel), 0)),
                Component.translatable("ui.irons_spellbooks.duration", Utils.stringTruncation(c.durationSeconds.get(), 1) + "s"));
    }
}
