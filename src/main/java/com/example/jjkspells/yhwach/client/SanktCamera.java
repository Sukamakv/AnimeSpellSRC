package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yhwach.entity.SanktZwingerEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cinematic for the caster: close-up on the ground seal, dramatic pull-back while the architecture rises,
 * then a smooth return into the normal first-person view. Uses a dummy camera entity, restored when it ends.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SanktCamera {
    private static final float CLOSEUP_END = 34f;
    private static final float PULLBACK_END = 106f;
    private static final float RETURN_END = 134f;

    private static SanktZwingerEntity active;
    private static Entity savedCamera;
    private static CameraType savedType;
    private static Marker cam;
    private static double angle0;
    private static double fovMul = 1.0;

    private SanktCamera() {
    }

    public static void start(SanktZwingerEntity e) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || e.getOwnerId() != mc.player.getId()) {
                return;
            }
            if (active != null) {
                restore(mc);
            }
            active = e;
            savedCamera = mc.getCameraEntity();
            savedType = mc.options.getCameraType();
            cam = new Marker(EntityType.MARKER, mc.level);
            angle0 = Math.toRadians(mc.player.getYRot() + 90.0);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(cam);
        } catch (Throwable t) {
            active = null;
        }
    }

    public static void stop(SanktZwingerEntity e) {
        if (active == e) {
            restore(Minecraft.getInstance());
        }
    }

    private static void restore(Minecraft mc) {
        try {
            mc.setCameraEntity(savedCamera != null && !savedCamera.isRemoved() ? savedCamera : mc.player);
            if (savedType != null) {
                mc.options.setCameraType(savedType);
            }
        } catch (Throwable ignored) {
        }
        active = null;
        cam = null;
        savedCamera = null;
        fovMul = 1.0;
    }

    private static double smooth(double x) {
        x = Math.max(0.0, Math.min(1.0, x));
        return x * x * (3.0 - 2.0 * x);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || active == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null || active.isRemoved() || cam == null) {
                restore(mc);
                return;
            }
            float t = active.tickCount - 1 + event.renderTickTime;
            if (t >= RETURN_END) {
                restore(mc);
                return;
            }
            if (mc.getCameraEntity() != cam) {
                mc.setCameraEntity(cam);
            }
            Vec3 c = active.getPosition(1.0f);
            double u = smooth((t - CLOSEUP_END) / (PULLBACK_END - CLOSEUP_END));
            double a = angle0 + t * 0.018 + u * 0.55;
            double radius;
            double height;
            double lookY;
            if (t < CLOSEUP_END) {
                double s = t / CLOSEUP_END;
                radius = 5.5 + 1.8 * s;
                height = 1.2 + 1.1 * s;
                lookY = 0.1 + 0.7 * s;
            } else {
                radius = 7.3 + (46.0 - 7.3) * u;
                height = 2.3 + (21.0 - 2.3) * u;
                lookY = 0.8 + (10.0 - 0.8) * u;
            }
            Vec3 pos = new Vec3(c.x + Math.cos(a) * radius, c.y + height, c.z + Math.sin(a) * radius);
            if (t > 26 && t < 90) {
                double sh = 0.05 * Math.sin((t - 26) / 64.0 * Math.PI);
                pos = pos.add(Math.sin(t * 2.7) * sh, Math.sin(t * 3.3) * sh, Math.cos(t * 2.1) * sh);
            }
            Vec3 look = new Vec3(c.x, c.y + lookY, c.z);
            double dx = look.x - pos.x;
            double dy = look.y - pos.y;
            double dz = look.z - pos.z;
            double hz = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.toDegrees(-Math.atan2(dy, hz));
            fovMul = 1.0 + 0.30 * u;

            if (t > PULLBACK_END) {
                double v = smooth((t - PULLBACK_END) / (RETURN_END - PULLBACK_END));
                float pt = event.renderTickTime;
                Vec3 eye = mc.player.getEyePosition(pt);
                pos = pos.lerp(eye, v);
                yaw = yaw + Mth.wrapDegrees(mc.player.getViewYRot(pt) - yaw) * (float) v;
                pitch = Mth.lerp((float) v, pitch, mc.player.getViewXRot(pt));
                fovMul = 1.0 + 0.30 * (1.0 - v);
            }
            // the dummy has eye height 0, so its position is the camera position
            cam.setPos(pos.x, pos.y, pos.z);
            cam.xo = cam.xOld = pos.x;
            cam.yo = cam.yOld = pos.y;
            cam.zo = cam.zOld = pos.z;
            cam.setYRot(yaw);
            cam.setXRot(pitch);
            cam.yRotO = yaw;
            cam.xRotO = pitch;
        } catch (Throwable ex) {
            restore(mc);
        }
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (active != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (active != null) {
            event.setFOV(event.getFOV() * fovMul);
        }
    }
}
