package com.example.jjkspells.malenia.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malenia.registry.ModEffects;
import com.example.jjkspells.malenia.registry.ModParticles;
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

    /** Rot spores, wound drops and butterflies drawn over the creatures carrying Malenia's effects. */
    @SubscribeEvent
    public static void marks(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) {
            return;
        }
        var rnd = v.level().getRandom();
        double w = Math.max(0.3, v.getBbWidth());
        if (v.tickCount % 3 == 0 && v.hasEffect(ModEffects.ROT.get())) {
            v.level().addParticle(ModParticles.ROT.get(), v.getX() + (rnd.nextDouble() - 0.5) * w * 1.3, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.3, 0, 0.02, 0);
            if (v.tickCount % 9 == 0) {
                v.level().addParticle(ModParticles.SPORE.get(), v.getX() + (rnd.nextDouble() - 0.5) * w, v.getY() + v.getBbHeight() * 0.8, v.getZ() + (rnd.nextDouble() - 0.5) * w, 0, 0.03, 0);
            }
        }
        if (v.tickCount % 4 == 0 && v.hasEffect(ModEffects.WOUND.get())) {
            v.level().addParticle(ModParticles.PETAL.get(), v.getX() + (rnd.nextDouble() - 0.5) * w, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * w, 0, -0.03, 0);
        }
        if (v.tickCount % 2 == 0 && v.hasEffect(ModEffects.GODDESS.get())) {
            v.level().addParticle(ModParticles.BUTTERFLY.get(), v.getX() + (rnd.nextDouble() - 0.5) * w * 2.0, v.getY() + rnd.nextDouble() * v.getBbHeight(), v.getZ() + (rnd.nextDouble() - 0.5) * w * 2.0, (rnd.nextDouble() - 0.5) * 0.04, 0.03, (rnd.nextDouble() - 0.5) * 0.04);
            if (v.tickCount % 6 == 0) {
                v.level().addParticle(ModParticles.PETAL.get(), v.getX() + (rnd.nextDouble() - 0.5) * w * 1.4, v.getY() + v.getBbHeight() * 0.9, v.getZ() + (rnd.nextDouble() - 0.5) * w * 1.4, 0, -0.02, 0);
            }
        }
        if (v.tickCount % 3 == 0 && v.hasEffect(ModEffects.SWIFT.get())) {
            v.level().addParticle(ModParticles.SILVER.get(), v.getX() + (rnd.nextDouble() - 0.5) * w, v.getY() + 0.1 + rnd.nextDouble() * 0.4, v.getZ() + (rnd.nextDouble() - 0.5) * w, 0, 0.01, 0);
        }
    }
}
