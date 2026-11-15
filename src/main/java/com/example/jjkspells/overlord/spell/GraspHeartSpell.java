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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** 8. Grasp Heart (my own addition): crush a target's heart for a chance at instant death. */
public class GraspHeartSpell extends OverlordSpell {
    public GraspHeartSpell() {
        super("grasp_heart", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.EPIC, CastType.INSTANT, () -> OverlordConfig.GRASP);
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (level.isClientSide) {
            return true;
        }
        if (Magic.lookedAtLiving(level, entity, OverlordConfig.GRASP.range.get()) == null) {
            if (entity instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("spell.jjk_spells.grasp_heart.no_target").withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            LivingEntity t = Magic.lookedAtLiving(level, entity, OverlordConfig.GRASP.range.get());
            if (t != null) {
                level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.GRASP, entity, t.position(), 40, 1f, spellLevel).mark(24).target(t));
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.GRASP;
        return List.of(
                Component.literal("Instant death: only if the target is below " + Utils.stringTruncation(c.instantDeathHealthThreshold.get() * 100, 0) + "% health (" + Utils.stringTruncation(c.instantDeathChance.get() * 100, 0) + "% chance)"),
                Component.literal("On failure: " + Utils.stringTruncation(c.failDamageFractionOfMaxHealth.get() * 100, 0) + "% max HP + stun"));
    }
}
