package com.example.jjkspells.afterlife.client;

import com.example.jjkspells.afterlife.entity.FuneralEntity;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Client-only state driven by the funeral entity: input lock, darkening, flashes and camera shake. */
public final class ClientFx {
    public static boolean locked;
    public static float darkness, flash, shake;
    private static boolean wantLock;
    private static float wantDark;

    private ClientFx() {
    }

    public static void clientTick() {
        flash *= 0.9f;
        if (flash < 0.01f) {
            flash = 0f;
        }
        shake *= 0.86f;
        if (shake < 0.01f) {
            shake = 0f;
        }
        darkness += (wantDark - darkness) * 0.3f;
        wantDark = 0f;
        locked = wantLock;
        wantLock = false;
    }

    public static void tickFuneral(FuneralEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        if (owner != null && owner == mc.player && age < FuneralEntity.T_END) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 3 || age == FuneralEntity.T4 - 12) {
                SpellAnimations.CHARGE_RAISED_HAND.getForPlayer().ifPresent(id -> AnimationHelper.animatePlayerStart(ap, id));
            }
            if (age == FuneralEntity.T2 || age == FuneralEntity.T5) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        if (mc.player == null) {
            return;
        }
        double dist = Math.sqrt(mc.player.distanceToSqr(e));
        float near = Mth.clamp(1f - (float) (dist / 48.0), 0f, 1f);
        // the atmosphere darkens so the crimson flames glow
        float d;
        if (age < FuneralEntity.T2) {
            d = Mth.clamp((age - 4) / 14f, 0f, 1f);
        } else if (age < FuneralEntity.T_BOOM) {
            d = 1f;
        } else {
            d = 1f - Mth.clamp((age - FuneralEntity.T_BOOM) / 24f, 0f, 1f);
        }
        wantDark = Math.max(wantDark, 0.5f * d * near);
        if (age == FuneralEntity.T_BOOM) {
            flash = Math.max(flash, 0.9f * near);
        }
        if (age >= FuneralEntity.T_BOOM && age < FuneralEntity.T_BOOM + 14) {
            shake = Math.max(shake, (1f - (age - FuneralEntity.T_BOOM) / 14f) * near * 1.6f);
        }
    }
}
