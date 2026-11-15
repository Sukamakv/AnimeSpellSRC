package com.example.jjkspells.malphite.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malphite.registry.ModEffects;
import com.example.jjkspells.malphite.registry.ModParticles;
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

    /** Dust and sparks drawn over the creatures carrying Malphite's effects. */
    @SubscribeEvent
    public static void marks(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) {
            return;
        }
        var rnd = v.level().getRandom();
        double w = Math.max(0.3, v.getBbWidth());
        if (v.tickCount % 3 == 0 && v.hasEffect(ModEffects.QUAKE.get())) {
            v.level().addParticle(ModParticles.DUST.get(), v.getX() + (rnd.nextDouble() - 0.5) * w * 1.4, v.getY() + 0.1, v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.4, 0, 0.02, 0);
            v.level().addParticle(ModParticles.GRAVEL.get(), v.getX() + (rnd.nextDouble() - 0.5) * w, v.getY() + 0.2, v.getZ() + (rnd.nextDouble() - 0.5) * w, 0, 0.12, 0);
        }
        if (v.tickCount % 4 == 0 && v.hasEffect(ModEffects.SHATTERED.get())) {
            v.level().addParticle(ModParticles.SHARD.get(), v.getX() + (rnd.nextDouble() - 0.5) * w, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * w, 0, -0.02, 0);
        }
        if (v.tickCount % 5 == 0 && v.hasEffect(ModEffects.TREMOR.get())) {
            v.level().addParticle(ModParticles.DUST.get(), v.getX() + (rnd.nextDouble() - 0.5) * w, v.getY() + 0.9, v.getZ() + (rnd.nextDouble() - 0.5) * w, 0, 0.01, 0);
        }
        if (v.tickCount % 4 == 0 && v.hasEffect(ModEffects.GRANITE.get())) {
            v.level().addParticle(ModParticles.GRAVEL.get(), v.getX() + (rnd.nextDouble() - 0.5) * w * 1.3, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.3, 0, 0.0, 0);
            if (v.tickCount % 8 == 0) {
                v.level().addParticle(ModParticles.EMBER.get(), v.getX() + (rnd.nextDouble() - 0.5) * w, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * w, 0, 0.03, 0);
            }
        }
        if (v.tickCount % 2 == 0 && v.hasEffect(ModEffects.RESILIENCE.get())) {
            v.level().addParticle(ModParticles.EMBER.get(), v.getX() + (rnd.nextDouble() - 0.5) * w * 1.3, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.3, 0, 0.05, 0);
            if (v.tickCount % 6 == 0) {
                v.level().addParticle(ModParticles.ROCK.get(), v.getX() + (rnd.nextDouble() - 0.5) * w * 1.4, v.getY() + v.getBbHeight() * 0.9, v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.4, 0, -0.01, 0);
            }
        }
    }
}
