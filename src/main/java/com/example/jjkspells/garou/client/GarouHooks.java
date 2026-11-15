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

/** Screen flash, manga speed lines, camera shake and the input lock of the caster. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class GarouHooks {
    private static final ResourceLocation LINES = new ResourceLocation(JJKSpells.MODID, "textures/garou/lines.png");

    private GarouHooks() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            GarouFx.clientTick();
        }
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Pre e) {
        GuiGraphics g = e.getGuiGraphics();
        if (GarouFx.lines > 0.02f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 0.96f, 0.88f, Math.min(1f, GarouFx.lines));
            g.blit(LINES, 0, 0, g.guiWidth(), g.guiHeight(), 0f, 0f, 1024, 1024, 1024, 1024);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
        if (GarouFx.flash > 0.01f) {
            int a = (int) (Math.min(1f, GarouFx.flash) * 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | (GarouFx.flashColor & 0xFFFFFF));
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (GarouFx.shake > 0.01f) {
            float s = GarouFx.shake;
            var r = net.minecraft.util.RandomSource.create();
            e.setYaw(e.getYaw() + (r.nextFloat() - 0.5f) * s * 1.6f);
            e.setPitch(e.getPitch() + (r.nextFloat() - 0.5f) * s * 1.6f);
            e.setRoll(e.getRoll() + (r.nextFloat() - 0.5f) * s * 2.2f);
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (Math.abs(GarouFx.fov) > 0.004f) {
            e.setFOV(e.getFOV() * (1.0 - GarouFx.fov));
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent e) {
        if (GarouFx.locked) {
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
        if (GarouFx.locked) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }
}
