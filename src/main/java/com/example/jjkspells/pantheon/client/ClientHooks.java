package com.example.jjkspells.pantheon.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.pantheon.registry.ModEffects;
import com.example.jjkspells.pantheon.registry.ModParticles;
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
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0x080400);
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

    /** Stun stars, celestial burning and Mortal Will drawn over their hosts. */
    @SubscribeEvent
    public static void marks(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) {
            return;
        }
        var rnd = v.level().getRandom();
        double top = v.getY() + v.getBbHeight();
        if (v.tickCount % 3 == 0 && v.hasEffect(ModEffects.STUN.get())) {
            double a = v.tickCount * 0.5;
            double r = Math.max(0.3, v.getBbWidth() * 0.55);
            for (int i = 0; i < 3; i++) {
                double aa = a + i * Math.PI * 2 / 3;
                v.level().addParticle(ModParticles.STAR.get(), v.getX() + Math.cos(aa) * r, top + 0.2, v.getZ() + Math.sin(aa) * r, 0, 0, 0);
            }
        }
        if (v.tickCount % 2 == 0 && v.hasEffect(ModEffects.BURN.get())) {
            v.level().addParticle(ModParticles.FLAME.get(), v.getX() + (rnd.nextDouble() - 0.5) * v.getBbWidth(), v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * v.getBbWidth(), 0, 0.04, 0);
            if (v.tickCount % 6 == 0) {
                v.level().addParticle(ModParticles.EMBER.get(), v.getX() + (rnd.nextDouble() - 0.5) * v.getBbWidth(), top, v.getZ() + (rnd.nextDouble() - 0.5) * v.getBbWidth(), 0, 0.05, 0);
            }
        }
        if (v.tickCount % 2 == 0 && v.hasEffect(ModEffects.UNBROKEN.get())) {
            v.level().addParticle(ModParticles.FLAME.get(), v.getX() + (rnd.nextDouble() - 0.5) * v.getBbWidth() * 1.3, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * v.getBbWidth() * 1.3, 0, 0.05, 0);
            if (v.tickCount % 4 == 0) {
                v.level().addParticle(ModParticles.SPARK.get(), v.getX() + (rnd.nextDouble() - 0.5) * v.getBbWidth() * 1.5, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * v.getBbWidth() * 1.5, 0, 0.03, 0);
            }
        }
    }
}
