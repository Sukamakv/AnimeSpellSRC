package com.example.jjkspells.client;

import com.example.jjkspells.StormConfig;
import com.example.jjkspells.entity.StormTornadoEntity;
import com.example.jjkspells.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;

/** Per-tick client particles and the wind loop for a tornado. */
public final class TornadoFx {
    private TornadoFx() {
    }

    public static void tick(StormTornadoEntity e) {
        var level = e.level();
        RandomSource r = level.random;
        if (e.tickCount == 1) {
            Minecraft.getInstance().getSoundManager().play(new TornadoLoopSound(e));
        }
        int life = e.getLifetime();
        float grow = Math.min(1f, e.tickCount / (float) StormTornadoEntity.FADE_IN) * Math.min(1f, (life - e.tickCount) / (float) StormTornadoEntity.FADE_OUT);
        if (grow <= 0.05f) {
            return;
        }
        double H = StormConfig.SPELL.tornadoHeight.get() * grow;
        double bx = e.getX(), by = e.getY(), bz = e.getZ();

        // dark debris cloud at the foot
        for (int i = 0; i < 2; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rad = StormConfig.SPELL.baseRadius.get() * (0.8 + r.nextDouble() * 1.8);
            level.addParticle(ModParticles.STORM_CLOUD.get(), true, bx + Math.cos(a) * rad, by + 0.3, bz + Math.sin(a) * rad,
                    -Math.sin(a) * 0.35, 0.05 + r.nextDouble() * 0.08, Math.cos(a) * 0.35);
        }
        // sparks crawling over the funnel
        for (int i = 0; i < 4; i++) {
            double h = r.nextDouble() * H;
            double a = r.nextDouble() * Math.PI * 2;
            double rad = StormTornadoEntity.radiusAt(h, StormConfig.SPELL.tornadoHeight.get()) * (0.85 + r.nextDouble() * 0.5);
            level.addParticle(ModParticles.STORM_SPARK.get(), true, bx + Math.cos(a) * rad, by + h, bz + Math.sin(a) * rad,
                    (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3);
        }
    }
}
