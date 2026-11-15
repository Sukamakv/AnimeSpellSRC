package com.example.jjkspells.afterlife.effect;

import com.example.jjkspells.afterlife.registry.ModParticles;
import com.example.jjkspells.afterlife.util.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.phys.Vec3;

/** Guide to Afterlife state: aura of butterflies, flame on the held weapon. Bonuses are applied in the event handlers. */
public class GuideEffect extends MobEffect {
    public GuideEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF4A18);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level) || e.tickCount % 2 != 0) {
            return;
        }
        e.clearFire();
        Vec3 pos = e.position();
        // butterflies leaving the body
        if (e.tickCount % 4 == 0) {
            Util.fx(level, ModParticles.BUTTERFLY_RED.get(), pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.6, 0.4, 0.03);
            Util.fx(level, ModParticles.BUTTERFLY_ORANGE.get(), pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.6, 0.4, 0.03);
        }
        Util.fx(level, ModParticles.EMBER.get(), pos.x, pos.y + 1.0, pos.z, 2, 0.35, 0.7, 0.35, 0.02);
        // flame wrapped around the held weapon (works with any item): along the main-hand arm and past it
        double yaw = Math.toRadians(e.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double hx = pos.x + rx * 0.45 + fx * 0.45, hz = pos.z + rz * 0.45 + fz * 0.45, hy = pos.y + 1.15;
        for (int k = 0; k < 3; k++) {
            double s = 0.2 + k * 0.4;
            Util.fxDir(level, ModParticles.SPIRIT_FLAME.get(), hx + fx * s, hy + 0.3 * k, hz + fz * s, 0, 0.03, 0);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity e, AttributeMap map, int amplifier) {
        super.removeAttributeModifiers(e, map, amplifier);
        if (e.level() instanceof ServerLevel level) {
            // the flames scatter into dozens of butterflies
            Vec3 p = e.position();
            Util.fx(level, ModParticles.BUTTERFLY_RED.get(), p.x, p.y + 1.0, p.z, 24, 0.5, 0.8, 0.5, 0.12);
            Util.fx(level, ModParticles.BUTTERFLY_ORANGE.get(), p.x, p.y + 1.0, p.z, 24, 0.5, 0.8, 0.5, 0.12);
            Util.fx(level, ModParticles.BUTTERFLY_GOLD.get(), p.x, p.y + 1.0, p.z, 12, 0.5, 0.8, 0.5, 0.12);
            Util.fx(level, ModParticles.EMBER.get(), p.x, p.y + 1.0, p.z, 30, 0.5, 0.8, 0.5, 0.1);
        }
    }
}
