package com.example.jjkspells.afterlife.spell;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModEffects;
import com.example.jjkspells.afterlife.registry.ModParticles;
import com.example.jjkspells.afterlife.registry.ModSounds;
import com.example.jjkspells.afterlife.util.Util;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Spell 1: Guide to Afterlife - Crimson Butterfly Dance (melee transformation). */
public class GuideToAfterlifeSpell extends AfterlifeSpell {
    public GuideToAfterlifeSpell() {
        super("guide_to_afterlife", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return AfterlifeConfig.GUIDE.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AfterlifeConfig.GUIDE.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("OVERHEAD_MELEE_SWING_ANIMATION", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            var c = AfterlifeConfig.GUIDE;
            // the price: a share of the CURRENT HP, but never the last point
            float hp = entity.getHealth();
            entity.setHealth(Math.max(1.0f, hp - hp * (float) (c.hpCostPercent.get() / 100.0)));
            int ticks = (int) Math.round(c.durationSeconds.get() * 20);
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, c.speedAmplifier.get(), false, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, c.strengthAmplifier.get(), false, false, true));
            entity.addEffect(new MobEffectInstance(ModEffects.GUIDE.get(), ticks, 0, false, true, true));
            entity.clearFire();
            // ring of burning butterflies sweeping outward
            double x = entity.getX(), y = entity.getY() + 0.9, z = entity.getZ();
            for (int i = 0; i < 28; i++) {
                double a = i * Math.PI * 2 / 28;
                var type = i % 3 == 0 ? ModParticles.BUTTERFLY_ORANGE.get() : i % 3 == 1 ? ModParticles.BUTTERFLY_RED.get() : ModParticles.BUTTERFLY_GOLD.get();
                Util.fxDir(level, type, x, y, z, Math.cos(a) * 0.28, 0.02, Math.sin(a) * 0.28);
                Util.fxDir(level, ModParticles.SPIRIT_FLAME.get(), x, y - 0.3, z, Math.cos(a) * 0.2, 0.05, Math.sin(a) * 0.2);
            }
            Util.fx(level, ModParticles.EMBER.get(), x, y, z, 50, 0.6, 0.8, 0.6, 0.15);
            Util.fx(level, ModParticles.FLASH.get(), x, y, z, 1, 0, 0, 0, 0);
            Util.sound(level, x, y, z, ModSounds.IGNITE.get(), 1.6f, 1.0f);
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AfterlifeConfig.GUIDE;
        return List.of(
                Component.literal("Costs " + Utils.stringTruncation(c.hpCostPercent.get(), 0) + "% of current HP"),
                Component.literal("Speed II, Strength III, fire immunity"),
                Component.literal("+" + Utils.stringTruncation(c.bonusFlat.get(), 0) + " fire damage and +" + Utils.stringTruncation(c.bonusPercent.get(), 1) + "% max HP per melee hit"),
                Component.translatable("ui.irons_spellbooks.duration", Utils.stringTruncation(c.durationSeconds.get(), 0) + "s"));
    }
}
