package com.example.jjkspells.garou.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain-aware ground decals (Garou's own copy). A (div x div) grid whose vertices each sample their own ground height by
 * scanning down from the centre height (so caves / ceilings above are ignored and cliffs fade out). `rot` spins the grid.
 */
public final class GroundDecal {
    private GroundDecal() {}

    /** Top of the first collidable block found below startY, NaN if there is none within 14 blocks (pit / cliff). */
    public static double groundY(Level lv, double x, double startY, double z, double fallback) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), y0 = Mth.floor(startY);
        mp.set(bx, y0, bz);
        if (!lv.hasChunkAt(mp)) return fallback;
        for (int y = y0; y > y0 - 14; y--) {
            mp.set(bx, y, bz);
            BlockState st = lv.getBlockState(mp);
            if (!st.getCollisionShape(lv, mp).isEmpty()) return y + 1.0;
        }
        return Double.NaN;
    }

    /** ctrl = renderer origin in world space, centerW = decal centre in world space. */
    public static void draw(Pen p, Level lv, Vec3 ctrl, Vec3 centerW, float radius, float rot, int div, float alpha) {
        if (alpha <= 0.01f || radius <= 0.05f) return;
        Vec3[][] pts = new Vec3[div + 1][div + 1];
        float[][] al = new float[div + 1][div + 1];
        float cs = Mth.cos(rot), sn = Mth.sin(rot);
        for (int i = 0; i <= div; i++) {
            for (int j = 0; j <= div; j++) {
                double lx = (-1 + 2.0 * i / div) * radius, lz = (-1 + 2.0 * j / div) * radius;
                double wx = centerW.x + lx * cs - lz * sn, wz = centerW.z + lx * sn + lz * cs;
                double g = groundY(lv, wx, centerW.y + 2.0, wz, centerW.y - 1.0);
                float a = alpha;
                if (Double.isNaN(g)) { g = centerW.y - 3.0; a = 0f; }
                else a *= 1f - Mth.clamp((float) ((centerW.y - g - 2.0) / 8.0), 0f, 1f);
                pts[i][j] = new Vec3(wx - ctrl.x, g + 0.05 - ctrl.y, wz - ctrl.z);
                al[i][j] = a;
            }
        }
        for (int i = 0; i < div; i++) {
            for (int j = 0; j < div; j++) {
                float u0 = i / (float) div, u1 = (i + 1) / (float) div, v0 = j / (float) div, v1 = (j + 1) / (float) div;
                p.quad(pts[i][j], pts[i + 1][j], pts[i + 1][j + 1], pts[i][j + 1], u0, v0, u1, v1, 1, 1, 1,
                        al[i][j], al[i + 1][j], al[i + 1][j + 1], al[i][j + 1]);
            }
        }
    }
}
