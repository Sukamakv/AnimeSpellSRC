package com.example.jjkspells.garou.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.client.SoftRender;
import com.example.jjkspells.garou.entity.GammaBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws the Gamma Ray Burst: the dark cosmic sphere that compresses, the twin beams, the merged beam (violet aura, blue-white
 * plasma, white-hot core, spiralling filaments, orbiting stars), the rotating energy rings and the impact explosions. All
 * animation comes from PNG sheets in textures/vfx (see tools/gen_gar_tex.py).
 */
public class GammaBeamRenderer extends EntityRenderer<GammaBeamEntity> {
    private static ResourceLocation tex(String n) {
        return new ResourceLocation(JJKSpells.MODID, "textures/vfx/" + n + ".png");
    }

    private static final ResourceLocation OUTER = tex("gar_beam_outer"), MID = tex("gar_beam_mid"), CORE = tex("gar_beam_core"), ARC = tex("gar_arc"),
            RING = tex("gar_ring"), BURST = tex("gar_burst"), VOID = tex("gar_void"), RIM = tex("gar_rim"),
            GLOW = tex("gar_glow"), GLOWV = tex("gar_glow_violet"), STAR = tex("gar_star");

    private static final float[] W = GarDraw.WHITE;
    private static final float[] ICE = {0.80f, 0.92f, 1f};
    private static final float[] VIO = {0.78f, 0.66f, 1f};

    public GammaBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(GammaBeamEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GammaBeamEntity e) {
        return GLOW;
    }

    private static VertexConsumer add(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(LinTypes.additive(t));
    }

    private static Vec3 at(Vec3 f, Vec3 r, Vec3 u, double z, double x, double y) {
        return f.scale(z).add(r.scale(x)).add(u.scale(y));
    }

    @Override
    public void render(GammaBeamEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float age = e.tickCount - 1 + pt;
        if (age < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera cm = mc.gameRenderer.getMainCamera();
        Vec3 base = e.getPosition(pt);
        Vec3 cam = cm.getPosition().subtract(base);
        Vector3f l = cm.getLeftVector(), cu = cm.getUpVector();
        Vec3 camR = new Vec3(-l.x(), -l.y(), -l.z()), camU = new Vec3(cu.x(), cu.y(), cu.z());

        Vec3 f = e.dirLerp(pt);
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        r = r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
        Vec3 u = r.cross(f).normalize();

        float W_ = e.beamWidth();
        float half = W_ * 0.5f;
        float maxLen = e.maxLength();
        float len = e.lenLerp(pt);
        boolean blocked = len < maxLen - 0.5f;
        int merge = GammaBeamEntity.MERGE_END, charge = GammaBeamEntity.CHARGE_END, fadeT = GammaBeamEntity.FADE_TICKS;
        int endAge = e.beamEndAge();

        // ---------------------------------------------------------------- the dark cosmic sphere
        float sphereFade = 1f - GarDraw.smooth((age - merge) / 6f);
        if (sphereFade > 0.01f) {
            float grow = GarDraw.smooth(age / 22f);
            float comp = GarDraw.smooth((age - 22f) / (charge - 22f));
            float R = 0.62f * grow * (1f - 0.62f * comp);
            if (age > charge) {
                R = 0.235f * (1f + 0.12f * Mth.sin(age * 1.7f));
            }
            Vec3 c = Vec3.ZERO;
            if (comp > 0f && age < charge) {
                c = new Vec3((Math.sin(age * 5.1) * 0.02) * comp, (Math.sin(age * 6.3) * 0.02) * comp, (Math.cos(age * 4.7) * 0.02) * comp);
            }
            if (R > 0.01f) {
                int fr = (int) (age * 0.6f);
                GarDraw.billboard(buf.getBuffer(SoftRender.entity(VOID)), ps, c, camR, camU, R * 1.02f, 0f, fr, 8, W, sphereFade);
                VertexConsumer vc = add(buf, RIM);
                GarDraw.billboard(vc, ps, c, camR, camU, R * 2.0f, age * 0.03f, (int) age, 8, W, (0.45f + 0.55f * comp) * sphereFade);
                vc = add(buf, GLOWV);
                GarDraw.billboard(vc, ps, c, camR, camU, R * (5f + 5f * comp), 0f, 0, 1, W, 0.30f * grow * sphereFade);
                vc = add(buf, GLOW);
                GarDraw.billboard(vc, ps, c, camR, camU, R * (2.5f + 3.5f * comp), 0f, 0, 1, ICE, 0.35f * comp * sphereFade);
                // accretion discs, rotating around the sphere
                vc = add(buf, RING);
                Vec3 tiltA = r.scale(0.7071).add(u.scale(0.7071));
                Vec3 tiltB = r.scale(0.7071).subtract(u.scale(0.7071));
                GarDraw.plane(vc, ps, c, r, f, R * 3.4f, age * 0.22f, (int) age, 8, W, 0.75f * grow * sphereFade);
                GarDraw.plane(vc, ps, c, tiltA, f, R * 2.7f, -age * 0.31f, (int) age + 3, 8, ICE, 0.65f * grow * sphereFade);
                GarDraw.plane(vc, ps, c, tiltB, f, R * 2.2f, age * 0.41f, (int) age + 5, 8, VIO, 0.55f * comp * sphereFade);
                // lens star while it collapses
                vc = add(buf, STAR);
                GarDraw.billboard(vc, ps, c, camR, camU, R * (3f + 7f * comp), age * 0.04f, 0, 1, W, 0.85f * comp * sphereFade);
            }
        }

        // ---------------------------------------------------------------- flashes: the sphere collapsing, the beam being fired
        float tFlash = age - charge;
        if (tFlash >= 0 && tFlash < 14) {
            GarDraw.billboard(add(buf, BURST), ps, Vec3.ZERO, camR, camU, 3.2f + tFlash * 0.18f, tFlash * 0.05f, Math.min(11, (int) (tFlash * 0.85f)), 12, W, 1f - tFlash / 18f);
        }
        float tFire = age - (merge - 4);
        if (tFire >= 0 && tFire < 18) {
            GarDraw.billboard(add(buf, BURST), ps, Vec3.ZERO, camR, camU, 9f + tFire * 0.35f, tFire * 0.03f, Math.min(11, (int) (tFire * 0.66f)), 12, W, 1f - tFire / 22f);
            GarDraw.billboard(add(buf, STAR), ps, Vec3.ZERO, camR, camU, 14f * (1f - tFire / 22f), 0.4f, 0, 1, W, 1f - tFire / 20f);
        }

        // ---------------------------------------------------------------- the twin beams bursting out of the sphere
        float tt = (age - charge) / (float) (merge - charge);
        float arcFade = 1f - GarDraw.smooth((age - merge) / 5f);
        if (tt >= 0f && arcFade > 0.01f) {
            float ttc = Math.min(1f, tt);
            float Dm = 19f * GarDraw.smooth(ttc * 1.35f) + 0.5f;
            float A = 6.2f * (float) Math.pow(1f - ttc, 1.25) + 0.15f;
            int N = 30;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3[] pts = new Vec3[N];
                float[] hw = new float[N], al = new float[N];
                for (int i = 0; i < N; i++) {
                    float s = i / (float) (N - 1);
                    double lat = sgn * A * Math.pow(Math.sin(Math.PI * s), 0.9);
                    double wob = 0.35 * A * Math.sin(s * 6.0 + age * 0.5 + sgn);
                    pts[i] = at(f, r, u, Dm * s, lat, wob);
                    hw[i] = (0.35f + 0.95f * (float) Math.sin(Math.PI * Math.min(1f, s * 1.1f))) * (0.45f + 0.9f * ttc);
                    al[i] = (0.35f + 0.65f * GarDraw.smooth(s * 5f)) * (1f - 0.55f * GarDraw.smooth((s - 0.7f) * 3f)) * arcFade;
                }
                strip(buf, ps, OUTER, pts, scale(hw, 2.3f), al, cam, 0.5f, age, 8, ICE, 0.55f);
                strip(buf, ps, MID, pts, scale(hw, 1.3f), al, cam, 0.8f, age + 2, 8, W, 0.95f);
                strip(buf, ps, CORE, pts, scale(hw, 0.55f), al, cam, 1.3f, age + 5, 8, W, 1f);
            }
        }

        // ---------------------------------------------------------------- the merged beam
        float ext = GarDraw.smooth((age - (merge - 6f)) / 5f);
        float collapse = age > endAge ? GarDraw.smooth((age - endAge) / (float) fadeT) : 0f;
        float widthScale = (0.32f + 0.68f * GarDraw.smooth((age - (merge - 6f)) / 10f)) * (float) Math.pow(1f - collapse, 1.6);
        float aScale = 1f - collapse;
        float Lcur = len * ext;
        if (Lcur > 0.6f && widthScale > 0.01f) {
            drawBeam(buf, ps, e, f, r, u, cam, age, half * widthScale, Lcur, blocked, aScale);
            drawRingsAndStars(buf, ps, f, r, u, camR, camU, age, half * widthScale, Lcur, aScale);
            // muzzle bloom
            VertexConsumer vc = add(buf, GLOWV);
            GarDraw.billboard(vc, ps, Vec3.ZERO, camR, camU, half * 2.1f * widthScale, 0f, 0, 1, W, 0.85f * aScale);
            vc = add(buf, GLOW);
            GarDraw.billboard(vc, ps, Vec3.ZERO, camR, camU, half * 1.2f * widthScale, 0f, 0, 1, W, aScale);
            vc = add(buf, STAR);
            GarDraw.billboard(vc, ps, Vec3.ZERO, camR, camU, half * 3.2f * widthScale * (0.85f + 0.15f * Mth.sin(age * 0.9f)), age * 0.035f, 0, 1, W, 0.9f * aScale);
            if (blocked) {
                drawImpact(buf, ps, f, r, u, camR, camU, age, half * widthScale, Lcur, aScale);
            }
        }
    }

    private static float[] scale(float[] a, float k) {
        float[] o = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            o[i] = a[i] * k;
        }
        return o;
    }

    private static void strip(MultiBufferSource buf, PoseStack ps, ResourceLocation t, Vec3[] pts, float[] hw, float[] al, Vec3 cam, float uSpeed, float age, int frames, float[] col, float alphaMul) {
        float[] a = new float[al.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = al[i] * alphaMul;
        }
        GarDraw.strip(add(buf, t), ps, pts, hw, a, cam, -age * uSpeed * 0.35f, 1f / 11f, (int) age, frames, col);
    }

    private void drawBeam(MultiBufferSource buf, PoseStack ps, GammaBeamEntity e, Vec3 f, Vec3 r, Vec3 u, Vec3 cam, float age, float half, float L, boolean blocked, float aScale) {
        int N = Mth.clamp((int) (L / 2.5f) + 3, 4, 36);
        Vec3[] pts = new Vec3[N];
        float[] prof = new float[N], al = new float[N];
        for (int i = 0; i < N; i++) {
            float s = i / (float) (N - 1);
            float z = L * s;
            pts[i] = f.scale(z);
            float nozzle = 0.38f + 0.62f * GarDraw.smooth(z / 6f);
            float pulse = 1f + 0.05f * Mth.sin(age * 1.7f + z * 0.15f) + 0.03f * Mth.sin(age * 3.1f - z * 0.4f);
            prof[i] = half * nozzle * pulse;
            float tail = blocked ? 1f : 1f - GarDraw.smooth((s - 0.82f) / 0.18f);
            al[i] = tail * aScale;
        }
        // soft bloom first (a single radial glow stretched along the beam)
        float[] bloomW = scale(prof, 1.45f);
        float[] bloomA = new float[N];
        for (int i = 0; i < N; i++) {
            bloomA[i] = al[i] * 0.30f;
        }
        GarDraw.strip(add(buf, GLOWV), ps, pts, bloomW, bloomA, cam, 0.5f, 0f, 0, 1, W);
        strip(buf, ps, OUTER, pts, scale(prof, 1.0f), al, cam, 0.55f, age, 8, W, 0.62f);
        strip(buf, ps, OUTER, pts, scale(prof, 0.80f), al, cam, -0.85f, age + 3, 8, VIO, 0.60f);
        strip(buf, ps, MID, pts, scale(prof, 0.58f), al, cam, 1.1f, age, 8, W, 0.95f);
        strip(buf, ps, CORE, pts, scale(prof, 0.30f), al, cam, 1.7f, age + 2, 8, W, 1f);
        strip(buf, ps, CORE, pts, scale(prof, 0.15f), al, cam, 2.4f, age + 5, 8, W, 1f);

        // energy filaments spiralling around the beam
        int M = Mth.clamp((int) (L * 0.9f) + 6, 8, 70);
        for (int k = 0; k < 3; k++) {
            Vec3[] hp = new Vec3[M];
            float[] hw = new float[M], ha = new float[M];
            for (int i = 0; i < M; i++) {
                float s = i / (float) (M - 1);
                float z = L * s;
                double rho = half * (0.35f + 0.30f * (0.5f + 0.5f * Math.sin(z * 0.21 + k * 2.0 - age * 0.3))) * (0.45f + 0.55f * GarDraw.smooth(z / 6f));
                double ang = z * 0.55 + age * 0.55 * (k % 2 == 0 ? 1 : -1) + k * Math.PI * 2.0 / 3.0;
                hp[i] = at(f, r, u, z, Math.cos(ang) * rho, Math.sin(ang) * rho);
                hw[i] = 0.34f * (0.5f + 0.5f * GarDraw.smooth(z / 4f));
                ha[i] = al[Math.min(N - 1, (int) (s * (N - 1)))] * 0.95f;
            }
            GarDraw.strip(add(buf, ARC), ps, hp, hw, ha, cam, -age * 0.12f, 1f / 6f, (int) age + k * 3, 8, ICE);
        }
    }

    private void drawRingsAndStars(MultiBufferSource buf, PoseStack ps, Vec3 f, Vec3 r, Vec3 u, Vec3 camR, Vec3 camU, float age, float half, float L, float aScale) {
        VertexConsumer vc = add(buf, RING);
        // five rotating rings around the origin of the beam
        for (int k = 0; k < 5; k++) {
            double z = 0.5 + k * 1.55;
            if (z > L) {
                break;
            }
            float rad = half * (0.52f + 0.17f * k) * (1f + 0.04f * Mth.sin(age * 0.8f + k));
            float rot = age * (0.16f + 0.05f * k) * (k % 2 == 0 ? 1f : -1f);
            GarDraw.plane(vc, ps, f.scale(z), r, u, rad, rot, (int) age + k * 2, 8, k % 2 == 0 ? W : ICE, (0.95f - 0.1f * k) * aScale);
        }
        // shock rings racing down the beam: the visible "ripples" of the distortion
        for (int j = 0; j < 6; j++) {
            float ph = ((age * 1.0f + j * 6f) % 36f) / 36f;
            double z = ph * L;
            float rad = half * (0.85f + 0.55f * ph);
            float a = (float) Math.pow(1f - ph, 0.6) * aScale;
            GarDraw.plane(vc, ps, f.scale(z), r, u, rad, age * 0.12f + j, (int) age + j, 8, ICE, 0.8f * a);
            GarDraw.plane(vc, ps, f.scale(z), r, u, rad * 2.1f, -age * 0.05f + j, (int) age + j + 4, 8, VIO, 0.22f * a);
        }
        // animated stars flying down the beam
        vc = add(buf, STAR);
        for (int i = 0; i < 70; i++) {
            float ph = (GarDraw.hash(i, 1) + age * 0.028f * (0.6f + GarDraw.hash(i, 2))) % 1f;
            double z = ph * L;
            double ang = GarDraw.hash(i, 3) * Math.PI * 2 + age * 0.09 * (GarDraw.hash(i, 4) - 0.5) * 4;
            double rad = Math.sqrt(GarDraw.hash(i, 5)) * half * 0.95;
            Vec3 p = at(f, r, u, z, Math.cos(ang) * rad, Math.sin(ang) * rad);
            float sz = 0.28f + 0.55f * GarDraw.hash(i, 6);
            float tw = 0.5f + 0.5f * Mth.sin(age * (0.4f + GarDraw.hash(i, 7)) + i);
            GarDraw.billboard(vc, ps, p, camR, camU, sz * (0.6f + 0.6f * tw), GarDraw.hash(i, 8) * 6.28f, 0, 1, W, (0.35f + 0.65f * tw) * aScale);
        }
    }

    private void drawImpact(MultiBufferSource buf, PoseStack ps, Vec3 f, Vec3 r, Vec3 u, Vec3 camR, Vec3 camU, float age, float half, float L, float aScale) {
        Vec3 e = f.scale(L);
        VertexConsumer vc = add(buf, BURST);
        for (int k = 0; k < 3; k++) {
            float t = (age * 0.9f + k * 4f) % 12f;
            int seed = (int) ((age * 0.9f + k * 4f) / 12f) * 7 + k;
            Vec3 j = r.scale((GarDraw.hash(seed, 11) - 0.5) * 3.2).add(u.scale((GarDraw.hash(seed, 12) - 0.5) * 3.2)).add(f.scale(-0.8));
            GarDraw.billboard(vc, ps, e.add(j), camR, camU, 3.6f + 2.4f * GarDraw.hash(seed, 13), GarDraw.hash(seed, 14) * 6.28f, Math.min(11, (int) t), 12, W, aScale);
        }
        vc = add(buf, GLOW);
        GarDraw.billboard(vc, ps, e.subtract(f.scale(0.6)), camR, camU, half * 1.7f, 0f, 0, 1, W, 0.9f * aScale);
        vc = add(buf, GLOWV);
        GarDraw.billboard(vc, ps, e.subtract(f.scale(0.6)), camR, camU, half * 3.4f, 0f, 0, 1, W, 0.55f * aScale);
        vc = add(buf, STAR);
        GarDraw.billboard(vc, ps, e.subtract(f.scale(0.6)), camR, camU, half * 2.6f, age * 0.05f, 0, 1, W, 0.8f * aScale);
        // shock ring spreading over the surface that was hit
        vc = add(buf, RING);
        for (int k = 0; k < 2; k++) {
            float ph = ((age + k * 5f) % 10f) / 10f;
            GarDraw.plane(vc, ps, e.subtract(f.scale(0.4)), r, u, 1.5f + ph * half * 1.6f, age * 0.2f, (int) age + k, 8, ICE, (1f - ph) * 0.85f * aScale);
        }
    }
}
