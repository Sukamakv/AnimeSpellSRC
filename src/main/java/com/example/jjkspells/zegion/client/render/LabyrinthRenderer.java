package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.LabyrinthEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/** Mephisto's Labyrinth: the black cube over Zegion's palm, towering mirrored walls that shift, dimensional blades, the closing sphere and the judgment. */
public class LabyrinthRenderer extends ZegRenderer<LabyrinthEntity> {
    public LabyrinthRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(LabyrinthEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        int seed = e.getSeed();
        float R = e.getRadius();
        int J = LabyrinthEntity.JUDGE;

        // ---- the cube above the palm
        if (t < LabyrinthEntity.GROW + 6) {
            Entity o = e.getOwnerEntity();
            if (o != null) {
                Vec3 op = new Vec3(Mth.lerp(pt, o.xo, o.getX()), Mth.lerp(pt, o.yo, o.getY()), Mth.lerp(pt, o.zo, o.getZ())).subtract(base);
                float k = smooth(t / LabyrinthEntity.CUBE);
                float grow = c01((t - LabyrinthEntity.CUBE) / (LabyrinthEntity.GROW - LabyrinthEntity.CUBE));
                float fade = 1f - c01((t - LabyrinthEntity.GROW) / 6f);
                ps.pushPose();
                ps.translate(op.x, op.y + 2.5f, op.z);
                ps.mulPose(Axis.YP.rotationDegrees(t * 6f));
                ps.mulPose(Axis.XP.rotationDegrees(35f));
                ps.mulPose(Axis.ZP.rotationDegrees(t * 3f));
                float s = (0.35f + 0.25f * Mth.sin(t * 0.2f)) * k * (1f + 5f * grow);
                cube(buf, ps, s, 0.95f * fade * k);
                ps.popPose();
                ps.pushPose();
                ps.translate(op.x, op.y + 2.5f, op.z);
                Gfx.billboard(ps);
                Draw.xy(add(buf, GLOW), ps, 1.6f * k * (1f + 2f * grow), 1.6f * k * (1f + 2f * grow), VIOLET, 0.7f * fade);
                ps.popPose();
            }
        }
        float form = smooth((t - LabyrinthEntity.CUBE) / (float) (LabyrinthEntity.GROW - LabyrinthEntity.CUBE));
        float conv = c01((t - LabyrinthEntity.CONVERGE) / (J - LabyrinthEntity.CONVERGE));
        float vis = form * (1f - c01((t - J - 4f) / 10f));
        if (form <= 0.01f) {
            return;
        }
        float rad = R * (1f - 0.82f * smooth(conv));

        // ---- the dark violet void overhead and the ground of the fabricated space
        if (vis > 0.01f) {
            Draw.sphere(trans(buf, SKY), ps, rad * 1.02f, 14, 28, t * 0.0008f, WHITE, 0.82f * vis);
            Draw.disc(add(buf, CIRCLE), ps, rad, -t * 0.9f, 0.07f, VIOLET, 0.6f * vis);
            Draw.disc(add(buf, TARGET), ps, rad, t * 0.7f, 0.09f, DEEP, 0.5f * vis);
            Draw.disc(trans(buf, MIST), ps, rad, t * 0.2f, 0.05f, WHITE, 0.55f * vis);
        }

        // ---- the walls: an impossible grid of mirrored planes that slide, appear and vanish
        float shift = c01((t - LabyrinthEntity.GROW) / 40f);
        int walls = 44;
        for (int i = 0; i < walls; i++) {
            float ang = (i + rnd(seed, i) * 0.8f) * Mth.TWO_PI / walls;
            float rr = rad * (0.18f + 0.82f * (float) Math.sqrt(rnd(seed, i + 30)));
            float slide = Mth.sin(t * 0.03f * (0.5f + rnd(seed, i + 60)) + i) * 3f * shift;
            float h = 12f + rnd(seed, i + 90) * 16f;
            float wd = 7f + rnd(seed, i + 120) * 9f;
            // each wall rises as the labyrinth unfolds, in order of its distance
            float rise = smooth(((t - LabyrinthEntity.CUBE) - rr / R * 22f) / 14f);
            float blink = 0.55f + 0.45f * Mth.sin(t * 0.07f + i * 1.9f);
            float a = vis * rise * (shift > 0f ? blink : 1f);
            if (a <= 0.02f) {
                continue;
            }
            ps.pushPose();
            ps.translate(Mth.cos(ang) * rr, h * 0.5f * rise, Mth.sin(ang) * rr);
            float yawW = (i % 2 == 0 ? 0f : 90f) + (rnd(seed, i + 150) < 0.35f ? 45f : 0f) + shift * 90f * (rnd(seed, i + 180) - 0.5f) * Mth.sin(t * 0.02f + i);
            ps.mulPose(Axis.YP.rotationDegrees(yawW));
            ps.translate(slide, 0, 0);
            float shatter = t >= LabyrinthEntity.CONVERGE ? 1f - conv * 0.3f : 1f;
            Draw.xy(trans(buf, WALL), ps, wd * 0.5f, h * 0.5f * rise, WHITE, 0.82f * a * shatter);
            ps.translate(0, 0, 0.04f);
            Draw.xy(add(buf, LATTICE), ps, wd * 0.5f, h * 0.5f * rise, i % 5 == 0 ? PALE : VIOLET, 0.75f * a);
            Draw.xy(add(buf, MIRROR_FRAME), ps, wd * 0.5f, h * 0.5f * rise, VIOLET, 0.6f * a);
            ps.popPose();
        }

        // ---- spectral Kaisers watching from behind the mirrors
        if (t >= LabyrinthEntity.GROW + 10) {
            for (int i = 0; i < 6; i++) {
                float ang = i * Mth.TWO_PI / 6 + rnd(seed, i + 300);
                float rr = rad * 0.9f;
                float a = vis * smooth((t - LabyrinthEntity.GROW - 10f - i * 4f) / 16f) * (0.5f + 0.5f * Mth.sin(t * 0.05f + i));
                phantom(buf, ps, base, Mth.cos(ang) * rr, 0f, Mth.sin(ang) * rr, 22f, 0.55f * a, 0.9f * a, VIOLET);
            }
        }

        // ---- the cutting waves
        int cnt = ZegConfig.LABYRINTH.waves.get();
        Vec3 cam = cam(base);
        for (int k = 0; k < cnt; k++) {
            float st = LabyrinthEntity.waveStart(k, cnt);
            float f = (t - st) / LabyrinthEntity.WAVE_LIFE;
            if (f < -0.3f || f > 1.3f) {
                continue;
            }
            double[] l = LabyrinthEntity.waveLine(seed, k, R);
            float fc = c01(f);
            float x = (float) (l[0] + (l[2] - l[0]) * fc), z = (float) (l[1] + (l[3] - l[1]) * fc);
            float dx = (float) (l[2] - l[0]), dz = (float) (l[3] - l[1]);
            float len = Mth.sqrt(dx * dx + dz * dz);
            float ang = (float) Mth.atan2(dz, dx);
            float a = Mth.sin(c01((f + 0.3f) / 1.6f) * Mth.PI);
            // a blade wall: crescent standing across the chord, a long lit line along the ground behind it
            ps.pushPose();
            ps.translate(x, 9f, z);
            ps.mulPose(Axis.YP.rotation(-ang));
            ps.mulPose(Axis.YP.rotationDegrees(90));
            Draw.xyAt(add(buf, CRESCENT), ps, 14f, 8f, 0f, PALE, a);
            Draw.xyAt(add(buf, ARC), ps, 16f, 5f, 0f, VIOLET, a * 0.8f);
            ps.translate(0, 0, 0.1f);
            Draw.xyAt(trans(buf, CRESCENT), ps, 13.6f, 7.8f, 0f, BLACK, 0.7f * a);
            ps.popPose();
            Vec3 a0 = new Vec3(l[0] + dx * Math.max(0f, fc - 0.25f), 0.3, l[1] + dz * Math.max(0f, fc - 0.25f));
            Vec3 b0 = new Vec3(x, 0.3, z);
            Draw.ribbon(add(buf, TRAIL), ps, a0, b0, cam, 5f, 5f, 0f, 1f, VIOLET, 0.6f * a, 0.6f * a);
        }

        // ---- the corridors close in: a geometric sphere rotates at the centre
        if (conv > 0f) {
            float s = rad * 0.6f;
            ps.pushPose();
            ps.translate(0, 8f, 0);
            ps.mulPose(Axis.YP.rotationDegrees(t * 5f));
            ps.mulPose(Axis.XP.rotationDegrees(t * 3f));
            Draw.sphere(trans(buf, SPHERE), ps, s, 12, 24, 0f, WHITE, 0.75f * vis);
            Draw.sphere(add(buf, HEX), ps, s * 1.02f, 10, 20, t * 0.01f, VIOLET, 0.7f * vis);
            ps.popPose();
            // Zegion appears above the structure
            float ap = smooth((t - LabyrinthEntity.CONVERGE) / 20f) * vis;
            phantom(buf, ps, base, 0f, rad * 0.9f + 10f, 0f, 24f, 0.8f * ap, ap, PALE);
        }

        // ---- the judgment
        float tj = t - J;
        if (tj >= 0f && tj < 26f) {
            float k = tj / 26f;
            float r = R * (0.25f + 0.85f * smooth(Math.min(1f, tj / 16f)));
            Draw.disc(add(buf, WAVE), ps, r, tj * 3f, 0.14f, PALE, 0.95f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 3f), r + 1f, 0.18f, 112, 1f, 0f, PALE, 1f - k);
            Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 9f, 80, 10f, tj * 0.06f, VIOLET, 0.9f * (1f - k), 0f);
            ps.pushPose();
            ps.translate(0, 8f, 0);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, R * 0.8f * (1f - k * 0.4f), R * 0.8f * (1f - k * 0.4f), PALE, 1f - k);
            Draw.xy(add(buf, GLOW), ps, R * 0.5f, R * 0.5f, VIOLET, 0.9f * (1f - k));
            ps.popPose();
        }
    }

    /** A rotating wireframe cube built from six runic faces. */
    private void cube(MultiBufferSource buf, PoseStack ps, float s, float a) {
        if (a <= 0.01f) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            ps.pushPose();
            switch (i) {
                case 0 -> ps.translate(0, 0, s);
                case 1 -> ps.translate(0, 0, -s);
                case 2 -> {
                    ps.mulPose(Axis.YP.rotationDegrees(90));
                    ps.translate(0, 0, s);
                }
                case 3 -> {
                    ps.mulPose(Axis.YP.rotationDegrees(90));
                    ps.translate(0, 0, -s);
                }
                case 4 -> {
                    ps.mulPose(Axis.XP.rotationDegrees(90));
                    ps.translate(0, 0, s);
                }
                default -> {
                    ps.mulPose(Axis.XP.rotationDegrees(90));
                    ps.translate(0, 0, -s);
                }
            }
            Draw.xy(trans(buf, WALL), ps, s, s, WHITE, 0.9f * a);
            Draw.xy(add(buf, LATTICE), ps, s, s, VIOLET, a);
            Draw.xy(add(buf, MIRROR_FRAME), ps, s, s, PALE, a);
            ps.popPose();
        }
    }
}
