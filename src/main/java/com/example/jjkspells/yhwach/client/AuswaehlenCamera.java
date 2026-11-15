package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yhwach.entity.AuswaehlenEntity;
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
 * Cinematic for the caster of Auswählen: low-angle sky reveal, a wide shot of the pillars descending over the battlefield,
 * a close frontal shot of the energy converging into the halo and the caster, then a smooth return to first person.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AuswaehlenCamera {
    private static AuswaehlenEntity active;
    private static Entity savedCamera;
    private static CameraType savedType;
    private static Marker cam;
    private static double fovMul = 1.0;

    private AuswaehlenCamera() {
    }

    public static void start(AuswaehlenEntity e) {
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
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(cam);
        } catch (Throwable t) {
            active = null;
        }
    }

    public static void stop(AuswaehlenEntity e) {
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

    private static float yawOf(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    private static float pitchOf(Vec3 d) {
        return (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    private static float lerpYaw(float a, float b, double w) {
        return a + Mth.wrapDegrees(b - a) * (float) w;
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
            float pt = event.renderTickTime;
            float t = active.tickCount - 1 + pt;
            if (t >= AuswaehlenEntity.CAMERA_END) {
                restore(mc);
                return;
            }
            if (mc.getCameraEntity() != cam) {
                mc.setCameraEntity(cam);
            }
            Vec3 D = active.castDir != null ? active.castDir : new Vec3(0, 0, 1);
            Vec3 C = active.ownerPos(pt);

            // A: lying low next to the caster, looking up into the opening sky
            double wa = smooth(t / 60.0);
            Vec3 posA = C.add(0, 0.35, 0).subtract(D.scale(1.2));
            float yawA = yawOf(D) + (float) (14.0 * wa);
            float pitchA = (float) (-26.0 - 56.0 * wa);

            // B: wide battlefield shot from behind and above, slowly orbiting
            int n = active.targetCount();
            Vec3 mid = C.add(D.scale(18));
            if (n > 0) {
                Vec3 s = Vec3.ZERO;
                for (int i = 0; i < n; i++) {
                    s = s.add(active.targetPos(i, pt));
                }
                mid = s.scale(1.0 / n);
            }
            double orb = 0.45 * smooth((t - 56.0) / 100.0);
            Vec3 back = rotY(D.scale(-30.0), orb);
            Vec3 posB = C.add(back).add(0, 15.0, 0);
            Vec3 dirB = mid.add(0, 16.0, 0).subtract(posB);
            float yawB = yawOf(dirB);
            float pitchB = pitchOf(dirB);

            // C: in front of the caster, dolly in, halo behind them
            double dolly = 16.0 - 5.0 * smooth((t - 150.0) / 85.0);
            Vec3 posC = C.add(D.scale(dolly)).add(0, 3.2, 0);
            Vec3 dirC = C.add(0, 9.0, 0).subtract(D.scale(3.0)).subtract(posC);
            float yawC = yawOf(dirC);
            float pitchC = pitchOf(dirC);

            double wb = smooth((t - 54.0) / 34.0);
            double wc = smooth((t - 148.0) / 26.0);
            Vec3 pos = posA.lerp(posB, wb);
            float yaw = lerpYaw(yawA, yawB, wb);
            float pitch = Mth.lerp((float) wb, pitchA, pitchB);
            pos = pos.lerp(posC, wc);
            yaw = lerpYaw(yaw, yawC, wc);
            pitch = Mth.lerp((float) wc, pitch, pitchC);
            double fov = 1.1 + 0.25 * wb - 0.2 * wc;

            double wr = smooth((t - 236.0) / 24.0);
            if (wr > 0.0) {
                Vec3 eye = mc.player.getEyePosition(pt);
                pos = pos.lerp(eye, wr);
                yaw = lerpYaw(yaw, mc.player.getViewYRot(pt), wr);
                pitch = Mth.lerp((float) wr, pitch, mc.player.getViewXRot(pt));
                fov = fov + (1.0 - fov) * wr;
            }
            fovMul = fov;
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

    private static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
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
