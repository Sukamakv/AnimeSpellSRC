package com.example.jjkspells.veldora.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.veldora.registry.ModEffects;
import com.example.jjkspells.veldora.registry.ModParticles;
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
        if (t % 2 == 0 && v.hasEffect(ModEffects.PARALYSIS.get())) {
            v.level().addParticle(ModParticles.SPARK.get(), x, y, z, (rnd.nextDouble() - 0.5) * 0.1, (rnd.nextDouble() - 0.5) * 0.1, (rnd.nextDouble() - 0.5) * 0.1);
        }
        if (t % 5 == 0 && v.hasEffect(ModEffects.OVERLOAD.get())) {
            v.level().addParticle(ModParticles.FRAGMENT.get(), x, y, z, 0, 0.01, 0);
        }
        if (t % 3 == 0 && v.hasEffect(ModEffects.MARK.get())) {
            v.level().addParticle(ModParticles.SOUL.get(), x, y, z, 0, 0.03, 0);
        }
        if (t % 3 == 0 && v.hasEffect(ModEffects.BLESSING.get())) {
            v.level().addParticle(ModParticles.GLOW.get(), x, y, z, 0, 0.03, 0);
        }
        if (t % 2 == 0 && v.hasEffect(ModEffects.ASCEND.get())) {
            v.level().addParticle(ModParticles.SPARK.get(), x, y, z, (rnd.nextDouble() - 0.5) * 0.2, 0.05, (rnd.nextDouble() - 0.5) * 0.2);
        }
        if (t % 3 == 0 && v.hasEffect(ModEffects.CHAOS.get())) {
            v.level().addParticle(ModParticles.RUNE.get(), v.getX() + Math.cos(t * 0.2) * 1.4, v.getY() + 0.2 + rnd.nextDouble() * 1.6, v.getZ() + Math.sin(t * 0.2) * 1.4, 0, 0.02, 0);
        }
    }
}
