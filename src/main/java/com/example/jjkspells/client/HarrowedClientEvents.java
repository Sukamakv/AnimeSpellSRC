package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of Harrowed Path. While the server says the local player is inside the mist (Mist Vision effect),
 * the client fills the air around them with Black Mist and spectral flames, tints the fog dark green and pulls it in.
 * Nothing here touches blocks, and nothing crosses the network: it is all drawn locally.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public class HarrowedClientEvents {

    private static final float FOG_FAR = 48f;

    private static boolean inMist() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.hasEffect(ModEffects.MIST_VISION.get());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.isPaused() || !inMist()) return;
        RandomSource r = level.random;
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 2 + r.nextDouble() * 14;
            level.addParticle(ModParticles.VG_MIST.get(), p.getX() + Math.cos(a) * d, p.getY() + r.nextDouble() * 2.6 - 0.4, p.getZ() + Math.sin(a) * d,
                    (r.nextDouble() - 0.5) * 0.02, 0.004, (r.nextDouble() - 0.5) * 0.02);
        }
        for (int i = 0; i < 2; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 3 + r.nextDouble() * 10;
            level.addParticle(ModParticles.VG_FLAME.get(), p.getX() + Math.cos(a) * d, p.getY() + r.nextDouble() * 0.6, p.getZ() + Math.sin(a) * d,
                    0, 0.04 + r.nextDouble() * 0.03, 0);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inMist()) return;
        event.setRed(Mth.lerp(0.6f, event.getRed(), 0.03f));
        event.setGreen(Mth.lerp(0.6f, event.getGreen(), 0.14f));
        event.setBlue(Mth.lerp(0.6f, event.getBlue(), 0.10f));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!inMist() || event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), FOG_FAR));
        event.setCanceled(true);          // tells the renderer to use the distances set above
    }
}
