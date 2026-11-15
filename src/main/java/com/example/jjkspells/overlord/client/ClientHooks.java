package com.example.jjkspells.overlord.client;

import com.example.jjkspells.JJKSpells;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events: atmosphere overlay, ambient muting, stand-still input lock. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            ClientFx.clientTick();
        }
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Pre e) {
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (ClientFx.darkness > 0.01f) {
            int a = (int) (Math.min(0.85f, ClientFx.darkness) * 255);
            g.fill(0, 0, w, h, (a << 24) | 0x08000F);
        }
        if (ClientFx.flash > 0.01f) {
            int a = (int) (Math.min(1f, ClientFx.flash) * 255);
            int rgb = ((int) (ClientFx.flashR * 255) << 16) | ((int) (ClientFx.flashG * 255) << 8) | (int) (ClientFx.flashB * 255);
            g.fill(0, 0, w, h, (a << 24) | rgb);
        }
    }

    @SubscribeEvent
    public static void sound(PlaySoundEvent e) {
        if (ClientFx.silence < 0.3f || e.getSound() == null) {
            return;
        }
        SoundSource src = e.getSound().getSource();
        if (src == SoundSource.AMBIENT || src == SoundSource.WEATHER || src == SoundSource.MUSIC || src == SoundSource.NEUTRAL) {
            if (!e.getSound().getLocation().getNamespace().equals(JJKSpells.MODID)) {
                e.setSound(null);
            }
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent e) {
        if (ClientFx.locked) {
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
        if (ClientFx.locked) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }
}
