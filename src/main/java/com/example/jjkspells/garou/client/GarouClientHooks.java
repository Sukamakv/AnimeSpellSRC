package com.example.jjkspells.garou.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.GarouEvents;
import com.example.jjkspells.garou.entity.CosmicPortalEntity;
import com.example.jjkspells.garou.entity.GammaRayBurstEntity;
import com.example.jjkspells.garou.entity.GravityKnuckleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client hooks (FORGE bus) for all Garou spells: roots the casting player's input, adds camera shake and an FOV
 * "pinch" that sells the gravitational distortion. Purely visual / input; no world or weather state is touched.
 */
@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class GarouClientHooks {
    private GarouClientHooks() {}

    private static float prog(float t, float a, float b) { return Mth.clamp((t - a) / (b - a), 0f, 1f); }

    /** What the camera should feel right now: shake strength and an FOV multiplier, for one controller entity. */
    private record Fx(float shake, float fov) {}

    private static Fx gammaFx(GammaRayBurstEntity g, float pt) {
        float t = g.age + pt;
        float fadeOut = 1f - prog(t, GammaRayBurstEntity.T_BEAM_END, GammaRayBurstEntity.T_END);
        float shake;
        if (t < GammaRayBurstEntity.T_FORM) shake = 0.08f * prog(t, 0, GammaRayBurstEntity.T_FORM);
        else if (t < GammaRayBurstEntity.T_TWIN) shake = 0.1f + 0.5f * prog(t, GammaRayBurstEntity.T_FORM, GammaRayBurstEntity.T_TWIN);
        else shake = 0.55f * (1f + 1.5f * (1f - prog(t, GammaRayBurstEntity.T_TWIN, GammaRayBurstEntity.T_TWIN + 10))) * fadeOut;
        // pinch in while the sphere compresses, snap wide on ignition, relax back
        float mod = 1f - 0.14f * prog(t, GammaRayBurstEntity.T_FORM, GammaRayBurstEntity.T_COMPRESS);
        if (t >= GammaRayBurstEntity.T_COMPRESS) mod = Mth.lerp(prog(t, GammaRayBurstEntity.T_COMPRESS, GammaRayBurstEntity.T_TWIN + 4), mod, 1.08f);
        if (t >= GammaRayBurstEntity.T_TWIN + 4) mod = Mth.lerp(prog(t, GammaRayBurstEntity.T_BEAM_END, GammaRayBurstEntity.T_END), 1.08f, 1f);
        return new Fx(shake, mod);
    }

    private static Fx knuckleFx(GravityKnuckleEntity g, float pt) {
        float t = g.age + pt;
        int pa = g.punchAt();
        if (pa < 0) {
            if (t < GravityKnuckleEntity.T_DASH) {   // charge: faint rumble, slight pinch toward the fist
                return new Fx(0.05f + 0.25f * prog(t, 0, GravityKnuckleEntity.T_DASH), 1f - 0.07f * prog(t, 4, GravityKnuckleEntity.T_DASH));
            }
            return new Fx(0.3f, 1.12f);                // dash: wide, fast
        }
        float a = t - pa;
        int W = GravityKnuckleEntity.WELL;
        if (a < W) {
            float hit = 2.0f * (1f - prog(a, 0, 10));  // punch jolt
            float well = 0.25f + 0.55f * prog(a, 4, W);
            float fov = Mth.lerp(prog(a, 0, 6), 1.12f, 1f) - 0.12f * prog(a, 6, W);   // the well squeezes the view
            return new Fx(hit + well, fov);
        }
        float ac = a - W;
        return new Fx(2.4f * (1f - prog(ac, 0, 16)), Mth.lerp(prog(ac, 0, 14), 1.14f, 1f));
    }

    private static Fx portalFx(CosmicPortalEntity g, float pt) {
        float t = g.age + pt;
        float emerge = t - CosmicPortalEntity.T_EMERGE;
        if (emerge < 0) {   // entering: the view narrows as he is pulled in
            return new Fx(0.12f * prog(t, 0, CosmicPortalEntity.T_VANISH), 1f - 0.1f * prog(t, 2, CosmicPortalEntity.T_EMERGE));
        }
        float k = 1f - prog(emerge, 0, 9);
        return new Fx((g.wasHit() ? 1.5f : 0.5f) * k, 1f + 0.16f * k);   // emerging: a quick wide snap
    }

    /** Strongest effect among the live controllers near the camera. */
    private static Fx best(Vec3 cam, float pt) {
        float shake = 0f, fovOff = 0f, bestW = 0f;
        for (GammaRayBurstEntity g : GarouEvents.ACTIVE) {
            if (!g.level().isClientSide || !g.isAlive()) continue;
            float near = 1f - Mth.clamp((float) (g.position().distanceTo(cam) / 90.0), 0f, 1f);
            Fx f = gammaFx(g, pt);
            shake = Math.max(shake, f.shake() * near * near);
            if (near > bestW) { bestW = near; fovOff = f.fov() - 1f; }
        }
        for (GravityKnuckleEntity g : GarouEvents.ACTIVE_GK) {
            if (!g.level().isClientSide || !g.isAlive()) continue;
            float near = 1f - Mth.clamp((float) (g.position().distanceTo(cam) / 70.0), 0f, 1f);
            Fx f = knuckleFx(g, pt);
            shake = Math.max(shake, f.shake() * near * near);
            if (near > bestW) { bestW = near; fovOff = f.fov() - 1f; }
        }
        for (CosmicPortalEntity g : GarouEvents.ACTIVE_CP) {
            if (!g.level().isClientSide || !g.isAlive()) continue;
            float near = 1f - Mth.clamp((float) (g.position().distanceTo(cam) / 50.0), 0f, 1f);
            Fx f = portalFx(g, pt);
            shake = Math.max(shake, f.shake() * near * near);
            if (near > bestW) { bestW = near; fovOff = f.fov() - 1f; }
        }
        return new Fx(shake, 1f + fovOff * Math.min(1f, bestW * 1.5f));
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        boolean root = false;
        for (GammaRayBurstEntity g : GarouEvents.ACTIVE) {
            if (g.level().isClientSide && g.isAlive() && g.getCasterId() == p.getId() && g.holdsCaster()) { root = true; break; }
        }
        if (!root) {
            for (GravityKnuckleEntity g : GarouEvents.ACTIVE_GK) {
                if (g.level().isClientSide && g.isAlive() && g.getCasterId() == p.getId() && g.holdsCaster()) { root = true; break; }
            }
        }
        if (!root) {
            for (CosmicPortalEntity g : GarouEvents.ACTIVE_CP) {
                if (g.level().isClientSide && g.isAlive() && g.getCasterId() == p.getId() && g.holdsCaster()) { root = true; break; }
            }
        }
        if (root) {
            var in = event.getInput();
            in.leftImpulse = 0; in.forwardImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false; in.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (GarouEvents.ACTIVE.isEmpty() && GarouEvents.ACTIVE_GK.isEmpty() && GarouEvents.ACTIVE_CP.isEmpty()) return;
        float pt = (float) event.getPartialTick();
        float k = best(event.getCamera().getPosition(), pt).shake();
        if (k <= 0.002f) return;
        double tm = System.nanoTime() / 5.0e7;   // ~20 Hz clock, independent of any one entity
        event.setPitch(event.getPitch() + (float) (Math.sin(tm * 2.3) * 1.4 * k));
        event.setYaw(event.getYaw() + (float) (Math.cos(tm * 1.9) * 1.4 * k));
        event.setRoll(event.getRoll() + (float) (Math.sin(tm * 1.3) * 1.8 * k));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (GarouEvents.ACTIVE.isEmpty() && GarouEvents.ACTIVE_GK.isEmpty() && GarouEvents.ACTIVE_CP.isEmpty()) return;
        float f = best(event.getCamera().getPosition(), (float) event.getPartialTick()).fov();
        if (Math.abs(f - 1f) > 0.001f) event.setFOV(event.getFOV() * f);
    }
}
