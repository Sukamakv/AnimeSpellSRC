package com.example.jjkspells.irontomb.client;

import net.minecraft.world.phys.Vec3;

/** Replaces the sky with deep space: a tinted dark shell and a star dome, both centred on the camera. */
public final class SkyFx {
    private SkyFx() {
    }

    public static void dome(FxCtx c, float alpha, float r, float g, float b) {
        if (alpha < 0.01f) {
            return;
        }
        Vec3 p = c.cam;
        c.ps.pushPose();
        c.ps.translate(p.x, p.y, p.z);
        Fx.sphere(c.alpha("white"), c.ps, 118f, 10, 20, 0f, r, g, b, Math.min(1f, alpha));
        c.ps.popPose();
        c.ps.pushPose();
        c.ps.translate(p.x, p.y, p.z);
        Fx.sphere(c.alpha("stars"), c.ps, 120f, 16, 32, c.age * 0.0004f, 1f, 1f, 1f, Math.min(1f, alpha * 0.95f));
        c.ps.popPose();
    }
}
