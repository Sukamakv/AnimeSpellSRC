package com.example.jjkspells.acheron.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.acheron.registry.ModEffects;
import com.example.jjkspells.acheron.registry.ModParticles;
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
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | (ClientFx.darkColor & 0xFFFFFF));
        }
        if (ClientFx.mono > 0.01f) {
            float k = ClientFx.shaderActive() ? 0.12f : 0.46f;
            int a = (int) (Math.min(1f, ClientFx.mono) * k * 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0x7C7C86);
        }
        if (ClientFx.crack > 0.02f) {
            float k = Math.min(1f, ClientFx.crack);
            net.minecraft.resources.ResourceLocation tx = new net.minecraft.resources.ResourceLocation(JJKSpells.MODID, "textures/vfx/rainblade_spatial_fracture.png");
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1f, 0.85f, 0.88f, k * 0.9f);
            g.blit(tx, 0, 0, g.guiWidth(), g.guiHeight(), 0f, 0f, 256, 256, 256, 256);
            g.setColor(1f, 1f, 1f, 1f);
        }
        if (ClientFx.flash > 0.01f) {
            float fl = Math.min(1f, ClientFx.flash);
            if (net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                fl = Math.min(fl, 0.3f);   // never a blinding screen in first person
            }
            int a = (int) (fl * 255);
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
        if (Math.abs(ClientFx.roll) > 0.05f) {
            e.setRoll(e.getRoll() + ClientFx.roll);
        }
        if (Math.abs(ClientFx.pullYaw) > 0.05f || Math.abs(ClientFx.pullPitch) > 0.05f) {
            e.setYaw(e.getYaw() + ClientFx.pullYaw);
            e.setPitch(e.getPitch() + ClientFx.pullPitch);
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

    /** Particles drawn over the creatures carrying Crimson Knots, and the Slashed Dream petals around the caster. */
    @SubscribeEvent
    public static void marks(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) {
            return;
        }
        int t = v.tickCount;
        var rnd = v.level().getRandom();
        double w = Math.max(0.3, v.getBbWidth());
        double x = v.getX() + (rnd.nextDouble() - 0.5) * w * 1.3, y = v.getY() + rnd.nextDouble() * v.getBbHeight(), z = v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.3;
        var k = v.getEffect(ModEffects.KNOT.get());
        if (k != null && t % Math.max(1, 6 - k.getAmplifier() / 2) == 0) {
            v.level().addParticle(ModParticles.EMBER.get(), x, y, z, 0, 0.02, 0);
        }
        var d = v.getEffect(ModEffects.DREAM.get());
        if (d != null && t % Math.max(2, 10 - d.getAmplifier()) == 0) {
            v.level().addParticle(ModParticles.PETAL.get(), x, y + 0.3, z, 0, -0.01, 0);
        }
        if (t % 5 == 0 && v.hasEffect(ModEffects.SUPPRESS.get())) {
            v.level().addParticle(ModParticles.MIST.get(), x, y, z, 0, 0.01, 0);
        }
    }
}
