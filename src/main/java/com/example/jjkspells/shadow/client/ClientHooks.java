package com.example.jjkspells.shadow.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.shadow.registry.ModEffects;
import com.example.jjkspells.shadow.registry.ModParticles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
        if (ClientFx.darkness > 0.01f) {
            int a = (int) (Math.min(0.85f, ClientFx.darkness) * 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0x080604);
        }
        if (ClientFx.flash > 0.01f) {
            int a = (int) (Math.min(1f, ClientFx.flash) * 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | (ClientFx.flashColor & 0xFFFFFF));
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (ClientFx.shake > 0.01f) {
            float s = ClientFx.shake;
            var r = net.minecraft.util.RandomSource.create();
            e.setYaw(e.getYaw() + (r.nextFloat() - 0.5f) * s * 1.6f);
            e.setPitch(e.getPitch() + (r.nextFloat() - 0.5f) * s * 1.6f);
            e.setRoll(e.getRoll() + (r.nextFloat() - 0.5f) * s * 2.2f);
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (Math.abs(ClientFx.fov) > 0.004f) {
            e.setFOV(e.getFOV() * (1.0 - ClientFx.fov));
        }
    }

    /** A caster who has vanished (armour and all) is not drawn. */
    @SubscribeEvent
    public static void hideBody(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
        if (!ClientFx.hide.isEmpty() && ClientFx.hide.contains(e.getEntity().getId())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hideHand(net.minecraftforge.client.event.RenderHandEvent e) {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null && ClientFx.hide.contains(p.getId())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent e) {
        var in = e.getInput();
        if (ClientFx.locked) {
            in.leftImpulse = 0f;
            in.forwardImpulse = 0f;
            in.jumping = false;
            in.shiftKeyDown = false;
            in.up = false;
            in.down = false;
            in.left = false;
            in.right = false;
        } else if (ClientFx.noJump) {
            in.jumping = false;
        }
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (ClientFx.locked) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    /** Particles drawn over the creatures carrying Tempest's effects. */
    @SubscribeEvent
    public static void marks(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) {
            return;
        }
        var rnd = v.level().getRandom();
        double w = Math.max(0.3, v.getBbWidth());
        double x = v.getX() + (rnd.nextDouble() - 0.5) * w * 1.3, y = v.getY() + rnd.nextDouble() * v.getBbHeight(), z = v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.3;
        int t = v.tickCount;
        if (t % 3 == 0 && v.hasEffect(ModEffects.BIND.get())) {
            v.level().addParticle(ModParticles.SLIME.get(), x, y, z, 0, -0.03, 0);
        }
        if (t % 4 == 0 && v.hasEffect(ModEffects.SUPPRESS.get())) {
            v.level().addParticle(ModParticles.RUNE.get(), x, y, z, 0, -0.02, 0);
        }
        if (t % 2 == 0 && v.hasEffect(ModEffects.RESIDUAL.get())) {
            v.level().addParticle(ModParticles.SPARK.get(), x, y + 0.5, z, 0, -0.04, 0);
        }
        if (t % 4 == 0 && v.hasEffect(ModEffects.FOCUS.get())) {
            v.level().addParticle(ModParticles.SLIME.get(), x, y, z, 0, 0.02, 0);
        }
    }
}
