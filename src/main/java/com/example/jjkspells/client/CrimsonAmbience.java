package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The oppressive crimson sky of The Ruler of Death's Crimson Wrath: the fog turns blood red and a dark red haze
 * (heavier toward the top of the screen) settles over the view for everyone near the spell.
 *
 * The server does not need a network packet for this: it spawns an invisible particle (see {@link SignalParticle})
 * which every nearby client ticks, and that particle reports where the spell is and how strong it currently is.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public class CrimsonAmbience {
    private static final double FULL_DISTANCE = 70.0;   // inside this the effect is at full strength
    private static final double NONE_DISTANCE = 170.0;  // beyond this there is none

    private static float reported = 0f;   // strongest signal received during the current tick
    private static float shown = 0f;      // smoothed value
    private static Vec3 center = Vec3.ZERO;

    private static void report(Vec3 where, float strength) {
        if (strength >= reported) {
            reported = strength;
            center = where;
        }
    }

    /** 0..1: how strongly this camera position is inside the crimson sky. */
    private static float factor(Vec3 cam) {
        if (shown <= 0.003f) return 0f;
        double d = cam.distanceTo(center);
        double near = 1.0 - Mth.clamp((d - FULL_DISTANCE) / (NONE_DISTANCE - FULL_DISTANCE), 0.0, 1.0);
        return (float) (shown * near * near * (3 - 2 * near));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        shown = Mth.lerp(0.12f, shown, reported);
        if (shown < 0.003f) shown = 0f;
        reported = 0f;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float f = factor(event.getCamera().getPosition());
        if (f <= 0f) return;
        float k = f * 0.9f;
        event.setRed(Mth.lerp(k, event.getRed(), 0.30f));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.012f));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.035f));
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return;
        float f = factor(event.getCamera().getPosition());
        if (f <= 0.01f) return;
        event.setNearPlaneDistance(Mth.lerp(f * 0.5f, event.getNearPlaneDistance(), 0f));
        event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance() * 0.3f, Mth.lerp(f * 0.7f, event.getFarPlaneDistance(), 230f)));
        event.setCanceled(true);
    }

    /** A dark red haze over the screen, heaviest at the top (the sky), pulsing slowly like a heartbeat. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float f = factor(mc.gameRenderer.getMainCamera().getPosition());
        if (f <= 0.01f) return;
        float t = (mc.player.tickCount + event.getPartialTick()) * 0.07f;
        float pulse = 0.88f + 0.12f * Mth.sin(t) + 0.05f * Mth.sin(t * 2.3f);
        int top = (int) Mth.clamp(150 * f * pulse, 0, 255);
        int bottom = (int) Mth.clamp(55 * f * pulse, 0, 255);
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        event.getGuiGraphics().fillGradient(0, 0, w, h, (top << 24) | 0x3A0006, (bottom << 24) | 0x2A0004);
    }

    /** Invisible particle: while it is alive nearby clients get the crimson sky. yd (lifetime) is sent by the server. */
    public static class SignalParticle extends Particle {
        public SignalParticle(ClientLevel level, double x, double y, double z, int lifetime) {
            super(level, x, y, z);
            this.lifetime = Math.max(20, lifetime);
            this.hasPhysics = false;
        }

        @Override
        public void tick() {
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            float in = Math.min(1f, this.age / 45f);
            float out = Math.min(1f, (this.lifetime - this.age) / 60f);
            report(new Vec3(this.x, this.y, this.z), in * out);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.NO_RENDER;
        }

        @Override
        public boolean shouldCull() {
            return false;
        }
    }
}
