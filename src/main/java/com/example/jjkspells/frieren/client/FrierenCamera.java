package com.example.jjkspells.frieren.client;

import com.example.jjkspells.JJKSpells;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Light cinematic camera for Frieren's spells: a slight zoom and screen shake only. The player keeps full control - nothing is
 * taken over, the aim is untouched, and everything ends by itself. Renderers feed values during the frame; they are applied
 * on the next camera update (so a missing renderer simply means no effect).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FrierenCamera {
    private static float pendingAmp = 0f, pendingFov = 1f;
    private static float amp = 0f, fov = 1f;

    private FrierenCamera() {
    }

    /** Shake strength in degrees (the largest request of the frame wins). */
    public static void feedShake(float degrees) {
        pendingAmp = Math.max(pendingAmp, degrees);
    }

    /** Field-of-view multiplier (the smallest request of the frame wins). */
    public static void feedFov(float mult) {
        pendingFov = Math.min(pendingFov, mult);
    }

    private static float noise(float x) {
        return 0.6f * (float) Math.sin(x * 1.3f) + 0.4f * (float) Math.sin(x * 2.9f + 1.3f);
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        amp = pendingAmp;
        fov = pendingFov;
        pendingAmp = 0f;
        pendingFov = 1f;
        if (amp <= 0.001f || Minecraft.getInstance().level == null) return;
        float t = (float) (Minecraft.getInstance().level.getGameTime() + event.getPartialTick());
        event.setYaw(event.getYaw() + amp * noise(t * 2.1f));
        event.setPitch(event.getPitch() + amp * noise(t * 2.3f + 11f));
        event.setRoll(event.getRoll() + amp * 0.6f * noise(t * 1.9f + 23f));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (fov < 0.9999f) event.setFOV(event.getFOV() * fov);
    }
}
