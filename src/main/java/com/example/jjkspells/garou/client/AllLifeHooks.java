package com.example.jjkspells.garou.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Screen darkening, speed lines, white-out, camera shake, zoom and the input lock of All Life Eradication Fist. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class AllLifeHooks {
    private static final ResourceLocation LINES = new ResourceLocation(JJKSpells.MODID, "textures/garou/lines.png");

    private AllLifeHooks() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            AllLifeFx.clientTick();
        }
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Pre e) {
        GuiGraphics g = e.getGuiGraphics();
        if (AllLifeFx.dark > 0.01f) {
            int a = (int) (Math.min(0.75f, AllLifeFx.dark) * 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0x07040F);
        }
        if (AllLifeFx.lines > 0.02f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(0.92f, 0.9f, 1f, Math.min(1f, AllLifeFx.lines));
            g.blit(LINES, 0, 0, g.guiWidth(), g.guiHeight(), 0f, 0f, 1024, 1024, 1024, 1024);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
        if (AllLifeFx.flash > 0.01f) {
            int a = (int) (Math.min(1f, AllLifeFx.flash) * 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | (AllLifeFx.flashColor & 0xFFFFFF));
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (AllLifeFx.shake > 0.01f) {
            float s = AllLifeFx.shake;
            var r = net.minecraft.util.RandomSource.create();
            e.setYaw(e.getYaw() + (r.nextFloat() - 0.5f) * s * 1.6f);
            e.setPitch(e.getPitch() + (r.nextFloat() - 0.5f) * s * 1.6f);
            e.setRoll(e.getRoll() + (r.nextFloat() - 0.5f) * s * 2.2f);
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (Math.abs(AllLifeFx.fov) > 0.004f) {
            e.setFOV(e.getFOV() * (1.0 - AllLifeFx.fov));
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent e) {
        if (AllLifeFx.locked) {
            var in = e.getInput();
            in.leftImpulse = 0f;
            in.forwardImpulse = 0f;
            in.jumping = false;
            in.shiftKeyDown = false;
            in.up = false;
            in.down = false;
            in.left = false;
            in.right = false;
        }
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (AllLifeFx.locked) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }
}
