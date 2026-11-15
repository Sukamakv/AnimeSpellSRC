package com.example.jjkspells.garou.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.config.GarConfig;
import com.example.jjkspells.garou.entity.GammaBeamEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client only polish for the Gamma Ray Burst: camera shake, a field-of-view warp (the "space distortion"), a white flash
 * overlay, and both arms thrust forward while the caster is channelling.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class GarClientEvents {
    private GarClientEvents() {
    }

    private static boolean recent(Minecraft mc) {
        return mc.level != null && mc.level.getGameTime() - GammaBeamEntity.clientSeen < 4;
    }

    /** Strength (0..~1.2) of the effect at the camera, from the nearest active beam; also reports its flash level. */
    private static float strength(Minecraft mc, float pt, boolean flash) {
        if (!GarConfig.G.cameraEffects.get() || mc.level == null || mc.player == null || !recent(mc)) {
            return 0f;
        }
        float best = 0f;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof GammaBeamEntity b)) {
                continue;
            }
            float age = b.tickCount - 1 + pt;
            double d = Math.sqrt(mc.player.distanceToSqr(b));
            float fall = (float) Mth.clamp(1.0 - d / 70.0, 0.0, 1.0);
            if (b.ownerId() == mc.player.getId()) {
                fall = 1f;
            }
            float s;
            if (flash) {
                float f = 0f;
                float t = age - GammaBeamEntity.CHARGE_END;
                if (t >= 0 && t < 8) {
                    f = Math.max(f, 1f - t / 8f);
                }
                float t2 = age - (GammaBeamEntity.MERGE_END - 4);
                if (t2 >= 0 && t2 < 10) {
                    f = Math.max(f, 0.9f * (1f - t2 / 10f));
                }
                s = f * (0.35f + 0.65f * fall);
            } else {
                s = b.cinematic(age) * fall;
            }
            best = Math.max(best, s);
        }
        return best;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        float s = strength(mc, (float) e.getPartialTick(), false);
        if (s <= 0.01f) {
            return;
        }
        float t = mc.player.tickCount + (float) e.getPartialTick();
        float k = s * 1.15f;
        e.setYaw(e.getYaw() + Mth.sin(t * 2.3f) * k * 0.6f);
        e.setPitch(e.getPitch() + Mth.sin(t * 3.1f + 1.3f) * k * 0.5f);
        e.setRoll(e.getRoll() + Mth.sin(t * 1.7f + 0.4f) * k * 0.9f);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        Minecraft mc = Minecraft.getInstance();
        float s = strength(mc, (float) e.getPartialTick(), false);
        if (s <= 0.01f) {
            return;
        }
        float t = mc.player.tickCount + (float) e.getPartialTick();
        e.setFOV(e.getFOV() + Mth.sin(t * 0.9f) * 2.2f * s - 5.5f * s);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        float f = strength(mc, e.getPartialTick(), true);
        if (f <= 0.02f) {
            return;
        }
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        int a = (int) (Mth.clamp(f, 0f, 1f) * 200f);
        e.getGuiGraphics().fill(0, 0, w, h, (a << 24) | 0xE6F0FF);
    }

    /** Both arms forward while the caster channels the spell (the pose is applied to the model before it is drawn). */
    @SubscribeEvent
    public static void pose(RenderPlayerEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (!recent(mc) || !(e.getEntity() instanceof AbstractClientPlayer p)) {
            return;
        }
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof GammaBeamEntity b && b.ownerId() == p.getId() && b.tickCount < b.beamEndAge() + GammaBeamEntity.FADE_TICKS) {
                PlayerRenderer r = e.getRenderer();
                HumanoidModel.ArmPose pose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                r.getModel().rightArmPose = pose;
                r.getModel().leftArmPose = pose;
                return;
            }
        }
    }
}
