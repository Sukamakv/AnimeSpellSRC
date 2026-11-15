package com.example.jjkspells.dragon.client;

import com.example.jjkspells.dragon.entity.AscensionFxEntity;
import com.example.jjkspells.dragon.registry.ModParticles;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Client state (input lock, white-gold flash) and per-tick particles of the ascension. */
public final class ClientFx {
    public static float flash;
    public static boolean locked;
    private static boolean wantLock;

    private ClientFx() {
    }

    public static void clientTick() {
        flash *= 0.92f;
        if (flash < 0.01f) {
            flash = 0f;
        }
        locked = wantLock;
        wantLock = false;
    }

    public static void tick(AscensionFxEntity e) {
        Level l = e.level();
        var rnd = l.getRandom();
        int t = e.tickCount;
        int rise = e.getRise();
        float R = e.getRadius();
        Entity owner = e.getOwnerEntity();
        double x = e.getX(), y = e.getY(), z = e.getZ();

        if (owner != null) {
            if (e.isLocking() && owner == Minecraft.getInstance().player) {
                wantLock = true;
            }
            if (owner instanceof AbstractClientPlayer ap) {
                if (t == 6) {
                    SpellAnimations.CHARGE_RAISED_HAND.getForPlayer().ifPresent(id -> AnimationHelper.animatePlayerStart(ap, id));
                }
                if (t == e.lockEnd()) {
                    AnimationHelper.cancelPlayerAnimation(ap);
                }
            }
        }
        // golden motes spiralling up around the rising caster
        if (owner != null && t < rise + 20) {
            for (int i = 0; i < 3; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 0.6 + rnd.nextDouble() * 1.6;
                l.addParticle(ModParticles.GOLD.get(), owner.getX() + Math.cos(a) * r, owner.getY() + rnd.nextDouble() * 2.2, owner.getZ() + Math.sin(a) * r, -Math.sin(a) * 0.04, 0.04, Math.cos(a) * 0.04);
            }
        }
        // ground circle sparkles
        if (t < rise + 40 && t % 2 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * R;
            l.addParticle(ModParticles.RUNE.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.04, 0);
        }
        if (t == rise) {
            flash = 0.85f;
        }
        if (t >= rise && t < e.lockEnd() && t % 1 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = R * Math.sqrt(rnd.nextDouble());
                l.addParticle(ModParticles.GOLD.get(), x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d, 0, 0.6 + rnd.nextDouble() * 0.8, 0);
            }
        }
    }
}
