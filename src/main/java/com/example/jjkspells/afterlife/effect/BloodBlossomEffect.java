package com.example.jjkspells.afterlife.effect;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModParticles;
import com.example.jjkspells.afterlife.util.Blossom;
import com.example.jjkspells.afterlife.util.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Pulsing flower mark: damages its host every few seconds. The sigil itself is a client particle. */
public class BloodBlossomEffect extends MobEffect {
    public BloodBlossomEffect() {
        super(MobEffectCategory.HARMFUL, 0xC01818);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % Blossom.intervalTicks() == 1;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level) || !e.isAlive()) {
            return;
        }
        double flat = AfterlifeConfig.BLOSSOM.flat.get(), pct = AfterlifeConfig.BLOSSOM.percent.get();
        LivingEntity owner = Blossom.owner(level, e);
        if (owner != null) {
            Util.hurt(owner, e, flat, pct, 1.0);
        } else {
            double m = AfterlifeConfig.G.damageMultiplier.get();
            e.invulnerableTime = 0;
            if (flat > 0) {
                e.hurt(level.damageSources().magic(), (float) (flat * m * AfterlifeConfig.G.flatScale.get()));
            }
            if (e.isAlive()) {
                com.example.jjkspells.spells.SpellDamage.percent(e, Util.budget(e, (float) (pct / 100.0 * e.getMaxHealth() * m * AfterlifeConfig.G.percentScale.get())), null, level.damageSources().magic());
            }
        }
        Vec3 c = e.getBoundingBox().getCenter();
        Util.fx(level, ModParticles.PETAL.get(), c.x, c.y, c.z, 14, 0.35, 0.45, 0.35, 0.12);
        Util.fx(level, ModParticles.EMBER.get(), c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.08);
        Util.fx(level, ModParticles.FLASH.get(), c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0);
    }
}
