package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.entity.UnlimitedVoidDome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Client side of Overload (frozen camera and input, tint) and of the Unlimited Void dome
 * (darkness and fog while the player stands inside it).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public class OverloadClient {
    private static boolean locked = false;
    private static float lockedYaw, lockedPitch;

    private static float domeFade = 0f;     // 0..1 how "inside the void" the player is
    private static float domeRadius = 20f;

    private static boolean overloaded(LocalPlayer p) {
        return p != null && p.hasEffect(ModEffects.OVERLOAD.get());
    }

    private static void applyLock(LocalPlayer p) {
        p.setYRot(lockedYaw);
        p.setXRot(lockedPitch);
        p.yRotO = lockedYaw;
        p.xRotO = lockedPitch;
        p.yHeadRot = lockedYaw;
        p.yHeadRotO = lockedYaw;
    }

    private static void updateLock(LocalPlayer p) {
        if (overloaded(p)) {
            if (!locked) {
                locked = true;
                lockedYaw = p.getYRot();
                lockedPitch = p.getXRot();
            }
            applyLock(p);
        } else {
            locked = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            locked = false;
            domeFade = 0f;
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            updateLock(p);
            return;
        }
        // END: find out whether we are inside a void dome
        float target = 0f;
        List<UnlimitedVoidDome> domes = mc.level.getEntitiesOfClass(UnlimitedVoidDome.class, p.getBoundingBox().inflate(40));
        for (UnlimitedVoidDome d : domes) {
            Vec3 c = d.position();
            if (c.distanceTo(p.getEyePosition()) < d.getRadius() * 0.98f) {
                target = 1f;
                domeRadius = d.getRadius();
                break;
            }
        }
        domeFade += (target - domeFade) * 0.12f;
        if (domeFade < 0.01f) domeFade = 0f;
    }

    /** Mouse movement from the last frame is thrown away before the next one is drawn. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (locked && overloaded(p)) applyLock(p);
    }

    /** The mouse is applied after the previous hook, so the picture itself is pinned to the locked angles too. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!locked || !overloaded(p)) return;
        float pt = (float) event.getPartialTick();
        float sign = Minecraft.getInstance().options.getCameraType().isMirrored() ? -1f : 1f;
        event.setYaw(event.getYaw() + Mth.wrapDegrees(lockedYaw - p.getViewYRot(pt)));
        event.setPitch(event.getPitch() + sign * (lockedPitch - p.getViewXRot(pt)));
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!overloaded((LocalPlayer) event.getEntity())) return;
        var in = event.getInput();
        in.leftImpulse = 0;
        in.forwardImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** Attack / use / pick-block keys do nothing. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (overloaded(Minecraft.getInstance().player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** A pulsing blue-violet haze over the whole screen. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!overloaded(p)) return;
        float t = (p.tickCount + event.getPartialTick()) * 0.12f;
        int a = (int) (60 + 28 * Mth.sin(t) + 14 * Mth.sin(t * 2.7f));
        int color = (a << 24) | 0x0A0630;
        event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), color);
    }

    // ---- darkness inside the dome -----------------------------------------------------------------
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (domeFade <= 0f) return;
        event.setNearPlaneDistance(Mth.lerp(domeFade, event.getNearPlaneDistance(), domeRadius * 0.15f));
        event.setFarPlaneDistance(Mth.lerp(domeFade, event.getFarPlaneDistance(), domeRadius * 2.4f));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (domeFade <= 0f) return;
        event.setRed(Mth.lerp(domeFade, event.getRed(), 0.01f));
        event.setGreen(Mth.lerp(domeFade, event.getGreen(), 0.0f));
        event.setBlue(Mth.lerp(domeFade, event.getBlue(), 0.05f));
    }
}
