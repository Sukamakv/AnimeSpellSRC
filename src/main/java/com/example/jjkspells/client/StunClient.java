package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** While the local player is Stunned: no walking, no clicking, and the camera is frozen where it was. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class StunClient {
    private static boolean locked;
    private static float lockYaw, lockPitch;

    private StunClient() {}

    private static boolean stunned(LocalPlayer p) {
        return p != null && p.isAlive() && p.hasEffect(ModEffects.STUN.get());
    }

    /** Resets any mouse turning before the tick's position packet is built. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (!stunned(p)) {
            locked = false;
            return;
        }
        if (!locked) {
            locked = true;
            lockYaw = p.getYRot();
            lockPitch = p.getXRot();
        }
        p.setYRot(lockYaw);
        p.setXRot(lockPitch);
        p.yRotO = lockYaw;
        p.xRotO = lockPitch;
        p.setYHeadRot(lockYaw);
    }

    /** The mouse still turns the player between ticks; the picture is held in place here (any third person offset is kept). */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!locked || !stunned(p)) return;
        double pt = event.getPartialTick();
        float dYaw = event.getYaw() - p.getViewYRot((float) pt);
        float dPitch = event.getPitch() - p.getViewXRot((float) pt);
        event.setYaw(lockYaw + dYaw);
        event.setPitch(lockPitch + dPitch);
        p.setYRot(lockYaw);
        p.setXRot(lockPitch);
        p.yRotO = lockYaw;
        p.xRotO = lockPitch;
    }

    @SubscribeEvent
    public static void onMove(MovementInputUpdateEvent event) {
        if (!stunned(Minecraft.getInstance().player)) return;
        var in = event.getInput();
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (stunned(Minecraft.getInstance().player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
