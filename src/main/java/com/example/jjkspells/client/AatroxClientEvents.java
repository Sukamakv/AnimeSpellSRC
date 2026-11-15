package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of World Ender's "the environment darkens". The server marks players near Aatrox with a hidden effect;
 * their client tints the fog dark red and pulls it in a little. Purely visual, nothing crosses the network.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public class AatroxClientEvents {

    private static final float FOG_FAR = 72f;

    private static boolean darkened() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.hasEffect(ModEffects.AT_DARKNESS.get());
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!darkened()) return;
        event.setRed(Mth.lerp(0.55f, event.getRed(), 0.16f));
        event.setGreen(Mth.lerp(0.55f, event.getGreen(), 0.01f));
        event.setBlue(Mth.lerp(0.55f, event.getBlue(), 0.02f));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!darkened() || event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), FOG_FAR));
        event.setCanceled(true);          // tells the renderer to use the distances set above
    }
}
