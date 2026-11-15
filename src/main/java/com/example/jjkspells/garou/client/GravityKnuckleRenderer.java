package com.example.jjkspells.garou.client;

import com.example.jjkspells.garou.entity.GravityKnuckleEntity;
import com.example.jjkspells.garou.spell.GarouGravityKnuckleSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

import static com.example.jjkspells.garou.entity.GravityKnuckleEntity.*;

/**
 * Draws Gravity Knuckle in the controller's local space (world - controller position). Before the punch the controller
 * follows the caster; after it, the local origin IS the impact point.
 *
 *  charge : fist rises, lens rings fold onto it, dark sphere grows, rocks lift off the (terrain-aware) ground and are
 *           sucked into the fist, a fracture decal spreads under the caster.
 *  dash   : sphere stays on the fist, speed-streak trail follows the caster.
 *  punch  : burst + shock rings + radial streaks + fracture decal at the impact.
 *  well   : dark sphere with tilted swirl discs, rotating inflow spiral on the ground, spiral streak arms, debris and rings
 *           contracting inward, then the sphere crushes to a point.
 *  collapse: white flash, shock waves, radial spokes, fading fracture.
 * Matter (rocks, dark sphere, fractures) uses the alpha render types; light uses additive. Glow stays low alpha.
 * Distortion is faked with lens rings and the FOV pinch from GarouClientHooks; nothing here touches the world.
 */
public class GravityKnuckleRenderer extends EntityRenderer<GravityKnuckleEntity> {
    private static ResourceLocation tex(String n) { return new ResourceLocation("jjk_spells", "textures/entity/garou/" + n + ".png"); }
    private static final ResourceLocation ROCK = tex("gk_rock"), SPIRAL = tex("gk_spiral"), CRACK = tex("gk_crack"), STREAK = tex("gk_streak");

    public GravityKnuckleRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override public ResourceLocation getTextureLocation(GravityKnuckleEntity e) { return CRACK; }
    @Override public boolean shouldRender(GravityKnuckleEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    private static float s(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static Pen add(MultiBufferSource b, ResourceLocation t, PoseStack ps, Vec3 cam) { return new Pen(b.getBuffer(GarouRenderTypes.additive(t)), ps, cam); }
    private static Pen alpha(MultiBufferSource b, ResourceLocation t, PoseStack ps, Vec3 cam) { return new Pen(b.getBuffer(GarouRenderTypes.alpha(t)), ps, cam); }

    private static void rock(Pen p, Vec3 c, float half, float roll, int cell, float a) {
        if (a <= 0.01f) return;
        float u0 = (cell % 4) / 4f, v0 = (cell / 4) / 4f;
        p.billboard(c, half, roll, u0, v0, u0 + 0.25f, v0 + 0.25f, 1, 1, 1, a);
    }

    private static void burst(Pen p, Vec3 c, float half, int frame, float a) {
        if (a <= 0.01f) return;
        int col = frame % 4, row = frame / 4;
        float u0 = col / 4f, v0 = row / 4f;
        p.billboard(c, half, 0f, u0, v0, u0 + 0.25f, v0 + 0.25f, 1, 1, 1, a);
    }

    /** Ring plane perpendicular to `axis` (basis u,v), spun and tilted. */
    private static void ring(Pen p, Vec3 c, Vec3 u, Vec3 v, Vec3 axis, float rad, float spin, float tilt, float a) {
        if (a <= 0.01f) return;
        Vec3 a1 = u.scale(Mth.cos(spin)).add(v.scale(Mth.sin(spin)));
        Vec3 a2 = u.scale(-Mth.sin(spin)).add(v.scale(Mth.cos(spin)));
        a2 = a2.scale(Mth.cos(tilt)).add(axis.scale(Mth.sin(tilt)));
        p.plane(c, a1.scale(rad), a2.scale(rad), 1, 1, 1, a);
    }

    @Override
    public void render(GravityKnuckleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.age + pt;
        Vec3 pos = e.getPosition(pt);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(pos);
        int pa = e.punchAt();
        if (pa < 0) renderCast(e, t, pt, pos, cam, ps, buf);
        else renderWell(e, t - pa, pos, cam, ps, buf);
    }

    // ===================================================================== charge + dash
    private void renderCast(GravityKnuckleEntity e, float t, float pt, Vec3 pos, Vec3 cam, PoseStack ps, MultiBufferSource buf) {
        Entity ce = e.level().getEntity(e.getCasterId());
        if (ce == null) return;
        Vec3 cpos = ce.getPosition(pt);                                  // caster feet, world
        Vec3 look = ce.getViewVector(pt).normalize();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        float raise = s(t / GravityKnuckleEntity.T_RAISE);
        Vec3 fistW = cpos.add(0, ce.getEyeHeight(), 0).add(look.scale(0.85)).add(right.scale(0.5)).add(0, -0.75 + 0.8 * raise, 0);
        Vec3 fist = fistW.subtract(pos);
        Vec3 feet = cpos.subtract(pos);

        float form = s(t / (T_DASH - 4f));
        float R = 0.95f * form * (1f + 0.05f * Mth.sin(t * 0.8f));
        float appear = s(t / 5f);
        float dashP = Math.max(0f, t - T_DASH);

        // --- rocks lifting off the ground (alpha / matter), pulled into the fist near the end of the charge
        if (t < T_DASH + 5) {
            Pen pr = alpha(buf, ROCK, ps, cam);
            Level lv = e.level();
            for (int i = 0; i < 36; i++) {
                Random r = new Random(i * 104729L + 7);
                float ang = r.nextFloat() * 6.2832f, rad = 1.3f + r.nextFloat() * 4.5f;
                float delay = r.nextFloat() * 12f, hmax = 0.6f + r.nextFloat() * 3.2f;
                float size = 0.11f + r.nextFloat() * 0.22f;
                double wx = cpos.x + Math.cos(ang + t * 0.02f) * rad, wz = cpos.z + Math.sin(ang + t * 0.02f) * rad;
                double gy = groundY(lv, wx, cpos.y + 1, wz, cpos.y);
                float rp = s(prog(t, delay, delay + 16f));
                Vec3 w = new Vec3(wx, gy + rp * hmax + 0.15 * Mth.sin(t * 0.2f + i), wz);
                float conv = s(prog(t, T_DASH - 9f, T_DASH + 2f));
                Vec3 lw = w.add(fistW.subtract(w).scale(conv)).subtract(pos);
                rock(pr, lw, size * (1f - 0.5f * conv), t * 0.07f * (i % 2 == 0 ? 1 : -1) + i, i % 16, rp * (1f - conv * 0.85f));
            }
            // fracture spreading under the caster (matter)
            float cr = 3.8f * s(prog(t, 3, 18));
            if (cr > 0.2f) decal(alpha(buf, CRACK, ps, cam), lv, pos, cpos, cr, 0f, 8, 0.9f * Math.min(1f, (T_DASH + 4 - t) / 6f));
        }

        // --- distortion + fist sphere
        Pen p = add(buf, GarouTex.DISC, ps, cam);
        p.billboard(fist, R * 2.3f, t * 0.12f, 1, 1, 1, 0.45f * appear);
        p = add(buf, GarouTex.RING, ps, cam);
        for (int i = 0; i < 3; i++) {
            float ph = (t * 0.055f + i / 3f) % 1f;
            float a = 0.5f * Mth.sin(ph * 3.1416f) * appear;
            p.billboard(fist, R * (4.4f * (1 - ph) + 1.15f), t * 0.05f * (i + 1), 0.75f, 0.85f, 1f, a);
        }
        p = alpha(buf, GarouTex.SPHERE, ps, cam);
        p.billboard(fist, R * 1.28f, t * 0.04f, 1, 1, 1, 0.97f * Math.min(1f, appear * 1.5f));
        p = add(buf, GarouTex.STAR, ps, cam);
        for (int i = 0; i < 26; i++) {
            Random r = new Random(i * 7727L + 3);
            float ph = (t * 0.045f * (0.6f + r.nextFloat()) + r.nextFloat()) % 1f;
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            p.billboard(fist.add(d.scale(3.2f * (1 - ph) + R)), 0.07f + 0.12f * r.nextFloat(), t * 0.1f, 1, 1, 1, Mth.sin(ph * 3.1416f) * 0.9f * appear);
        }
        p = add(buf, GarouTex.ARC, ps, cam);
        Random ra = new Random((long) (t / 2) * 17L + 1);
        for (int i = 0; i < 3; i++) {
            Vec3 d = new Vec3(ra.nextGaussian(), ra.nextGaussian(), ra.nextGaussian()).normalize();
            Vec3 a0 = fist.add(d.scale(R * 0.95)), a1 = fist.add(d.scale(R * 0.95 + 0.7)).add(ra.nextGaussian() * 0.15, ra.nextGaussian() * 0.15, 0), a2 = fist.add(d.scale(R * 0.95 + 1.4));
            float al = 0.8f * appear * (0.5f + 0.5f * ra.nextFloat());
            p.ribbonFacing(a0, a1, 0.14f, 0.1f, 0, 0.5f, 1, 1, 1, al, al);
            p.ribbonFacing(a1, a2, 0.1f, 0.03f, 0.5f, 1f, 1, 1, 1, al, al * 0.2f);
        }

        // --- dash trail (additive speed streaks following the caster)
        List<Vec3> tr = e.trail;
        if (tr.size() >= 2) {
            p = add(buf, STREAK, ps, cam);
            int n = tr.size();
            for (int i = 1; i < n; i++) {
                float f0 = (i - 1) / (float) (n - 1), f1 = i / (float) (n - 1);
                Vec3 a = tr.get(i - 1).subtract(pos), b = tr.get(i).subtract(pos);
                float w0 = 0.12f + 0.75f * f0, w1 = 0.12f + 0.75f * f1;
                p.ribbonFacing(a, b, w0, w1, -t * 0.3f + i * 0.5f, -t * 0.3f + (i + 1) * 0.5f, 1, 1, 1, 0.75f * f0, 0.75f * f1);
                p.ribbonFacing(a.add(0, 0.5, 0), b.add(0, 0.5, 0), w0 * 0.5f, w1 * 0.5f, t * 0.2f + i, t * 0.2f + i + 1f, 1, 1, 1, 0.5f * f0, 0.5f * f1);
            }
        }
    }

    // ===================================================================== well + collapse
    private void renderWell(GravityKnuckleEntity e, float a, Vec3 pos, Vec3 cam, PoseStack ps, MultiBufferSource buf) {
        Level lv = e.level();
        final int W = GravityKnuckleEntity.WELL;
        float ac = a - W;                                                // > 0 once collapsed
        float endFade = 1f - s(prog(ac, 6, T_AFTER));
        Vec3 up = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
        Vec3 impactW = pos;                                              // controller sits on the impact
        float ramp = s(a / 6f);
        float wp = prog(a, 0, W);

        // sphere radius: pops open, then slowly compresses, then crushes to a point
        float Rw = a < 10 ? 2.6f * easeOutBack(a / 10f) : 2.6f - 1.3f * s(prog(a, 10, W - 4));
        Rw *= 1f - 0.85f * s(prog(a, W - 5, W));
        if (ac > 0) Rw = 0f;

        // --- ground: fracture (matter) + rotating inflow spiral (light), both terrain-aware
        float crackR = 7.5f * s(prog(a, 0, 7));
        float crackA = ac < 0 ? 0.95f : 0.95f * endFade;
        if (crackR > 0.3f && crackA > 0.01f) decal(alpha(buf, CRACK, ps, cam), lv, pos, impactW, crackR, 0.3f, 10, crackA);
        if (ac < 0) {
            float sr = 16f * (1f - 0.6f * s(wp));
            decal(add(buf, SPIRAL, ps, cam), lv, pos, impactW, sr, -a * 0.16f, 10, 0.75f * ramp);
        }

        // --- debris spiralling in (matter)
        if (ac < 0) {
            Pen pr = alpha(buf, ROCK, ps, cam);
            for (int i = 0; i < 52; i++) {
                Random r = new Random(i * 15485863L + 11);
                float r0 = 4f + 11f * r.nextFloat(), th0 = r.nextFloat() * 6.2832f, y0 = (r.nextFloat() - 0.25f) * 4.5f;
                float p0 = r.nextFloat() * 5f / W;
                float p = prog(wp, p0, 1f);
                float rad = r0 * (float) Math.pow(1 - p, 1.5) + 0.35f;
                float th = th0 - p * 6.0f - a * 0.05f;
                Vec3 c = new Vec3(Math.cos(th) * rad, y0 * (float) Math.pow(1 - p, 1.3) - 0.2, Math.sin(th) * rad);
                float size = (0.16f + 0.3f * r.nextFloat()) * (1f - 0.55f * p);
                rock(pr, c, size, a * 0.18f * (i % 2 == 0 ? 1 : -1) + i, (i * 5) % 16, ramp * (1f - s(prog(p, 0.88f, 1f))));
            }
        }

        // --- dark distortion sphere with swirl discs (accretion) and lens rings
        if (ac < 0) {
            Pen p = add(buf, GarouTex.DISC, ps, cam);
            p.billboard(Vec3.ZERO, Rw * 3.4f, a * 0.14f, 1, 1, 1, 0.5f * ramp);
            // flattened accretion discs, one flat, one tilted
            ring(p, Vec3.ZERO, X, Z, up, Rw * 3.0f, a * 0.22f, 0.0f, 0.45f * ramp);
            ring(p, Vec3.ZERO, X, Z, up, Rw * 2.4f, -a * 0.3f, 0.5f, 0.35f * ramp);
            p = add(buf, GarouTex.RING, ps, cam);
            for (int i = 0; i < 3; i++) {                                // lens rings squeezing onto the sphere
                float ph = (a * 0.045f + i / 3f) % 1f;
                p.billboard(Vec3.ZERO, Rw * (1.4f + 3.2f * (1 - ph)), a * 0.03f * (i + 1), 0.7f, 0.85f, 1f, 0.3f * Mth.sin(ph * 3.1416f) * ramp);
            }
            p = alpha(buf, GarouTex.SPHERE, ps, cam);
            p.billboard(Vec3.ZERO, Rw * 1.3f, a * 0.03f, 1, 1, 1, 0.98f * ramp);

            // --- inflow rings + spiral streak arms (light)
            p = add(buf, GarouTex.RING, ps, cam);
            for (int i = 0; i < 6; i++) {
                float ph = (a * 0.055f + i / 6f) % 1f;
                float rad = 15f * (1 - ph) + 1.8f;
                Vec3 ax = new Vec3(Math.sin(i * 1.3), 1.0, Math.cos(i * 0.9)).normalize();
                Vec3 u = ax.cross(new Vec3(1, 0.3, 0)).normalize(), v = ax.cross(u).normalize();
                ring(p, Vec3.ZERO, u, v, ax, rad, a * 0.15f * (i % 2 == 0 ? 1 : -1) + i, 0.2f, 0.5f * Mth.sin(ph * 3.1416f) * ramp);
            }
            p = add(buf, STREAK, ps, cam);
            float rmax = 16f * (1f - 0.5f * s(wp));
            for (int j = 0; j < 9; j++) {
                float th0 = j / 9f * 6.2832f;
                Vec3 prev = null;
                for (int k = 0; k <= 12; k++) {
                    float sg = k / 12f;
                    float rad = rmax * (float) Math.pow(1 - sg, 1.25) + Rw * 0.9f;
                    float th = th0 - sg * 3.6f - a * 0.22f;
                    Vec3 c = new Vec3(Math.cos(th) * rad, (1 - sg) * 0.6f - 0.15f + 0.25f * Mth.sin(j + sg * 4), Math.sin(th) * rad);
                    if (prev != null) {
                        float w = 0.1f + 0.34f * sg;
                        float al = 0.5f * ramp * Mth.sin(sg * 3.1416f) + 0.1f * ramp;
                        p.ribbonFacing(prev, c, w * 0.8f, w, -a * 0.2f + k * 0.35f, -a * 0.2f + (k + 1) * 0.35f, 1, 1, 1, al, al);
                    }
                    prev = c;
                }
            }
            p = add(buf, GarouTex.STAR, ps, cam);
            for (int i = 0; i < 36; i++) {                                 // stars streaming into the singularity
                Random r = new Random(i * 6151L + 5);
                float ph = (a * 0.05f * (0.7f + r.nextFloat()) + r.nextFloat()) % 1f;
                float th = r.nextFloat() * 6.2832f - ph * 3f;
                float rad = 12f * (1 - ph) * (1 - ph) + Rw;
                Vec3 c = new Vec3(Math.cos(th) * rad, (r.nextFloat() - 0.5f) * 5f * (1 - ph), Math.sin(th) * rad);
                p.billboard(c, 0.1f + 0.2f * r.nextFloat(), a * 0.1f, 1, 1, 1, Mth.sin(ph * 3.1416f) * 0.9f * ramp);
            }
            p = add(buf, GarouTex.ARC, ps, cam);
            Random ra = new Random((long) (a / 2) * 13L + 9);
            for (int i = 0; i < 6; i++) {
                Vec3 d = new Vec3(ra.nextGaussian(), ra.nextGaussian(), ra.nextGaussian()).normalize();
                float l = 2f + ra.nextFloat() * 3.5f;
                Vec3 a0 = d.scale(Rw * 0.95), a1 = d.scale(Rw * 0.95 + l * 0.5).add(ra.nextGaussian() * 0.3, ra.nextGaussian() * 0.3, ra.nextGaussian() * 0.3), a2 = d.scale(Rw * 0.95 + l);
                float al = 0.8f * ramp * (0.5f + 0.5f * ra.nextFloat());
                p.ribbonFacing(a0, a1, 0.2f, 0.14f, 0, 0.5f, 1, 1, 1, al, al);
                p.ribbonFacing(a1, a2, 0.14f, 0.03f, 0.5f, 1f, 1, 1, 1, al, al * 0.2f);
            }
        }

        // --- punch impact: burst, shock rings, radial streaks (first ticks)
        if (a < 14) {
            Pen pb = add(buf, GarouTex.BURST, ps, cam);
            burst(pb, Vec3.ZERO, 5.5f + 3f * s(a / 5f), Math.min(15, (int) (a / 14f * 16f)), 1f - s(prog(a, 7, 14)));
            Pen pr = add(buf, GarouTex.RING, ps, cam);
            ring(pr, Vec3.ZERO.add(0, 0.2, 0), X, Z, up, 1.5f + a * 1.1f, a * 0.2f, 0f, 0.8f * (1f - prog(a, 0, 12)));
            pr.billboard(Vec3.ZERO, 1.5f + a * 0.9f, a * 0.1f, 0.8f, 0.9f, 1f, 0.6f * (1f - prog(a, 0, 9)));
            Pen ps2 = add(buf, STREAK, ps, cam);
            for (int i = 0; i < 14; i++) {
                float th = i / 14f * 6.2832f + 0.3f;
                Vec3 d = new Vec3(Math.cos(th), 0.25f * Mth.sin(i * 2.1f), Math.sin(th)).normalize();
                float r0 = 1.2f + a * 0.9f, r1 = r0 + 3.5f;
                float al = 0.8f * (1f - prog(a, 0, 11));
                ps2.ribbonFacing(d.scale(r0), d.scale(r1), 0.35f, 0.05f, 0, 1, 1, 1, 1, al, 0f);
            }
        }

        // --- collapse
        if (ac >= 0 && ac < 20) {
            Pen pb = add(buf, GarouTex.BURST, ps, cam);
            burst(pb, Vec3.ZERO, 12f + 10f * s(ac / 6f), Math.min(15, (int) (ac / 16f * 16f)), 1f - s(prog(ac, 8, 16)));
            Pen pr = add(buf, GarouTex.RING, ps, cam);
            ring(pr, new Vec3(0, 0.25, 0), X, Z, up, 2f + ac * 2.7f, ac * 0.1f, 0f, 0.85f * (1f - prog(ac, 0, 15)));
            ring(pr, new Vec3(0, 0.25, 0), X, Z, up, 1f + ac * 1.9f, -ac * 0.15f, 0f, 0.6f * (1f - prog(ac, 0, 12)));
            pr.billboard(Vec3.ZERO, 2f + ac * 2.5f, ac * 0.08f, 0.8f, 0.9f, 1f, 0.7f * (1f - prog(ac, 0, 12)));
            Pen ps2 = add(buf, STREAK, ps, cam);
            for (int i = 0; i < 24; i++) {
                Random r = new Random(i * 4409L + 2);
                Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()).normalize();
                float r0 = 1.5f + ac * (1.6f + r.nextFloat()), r1 = r0 + 4f + 4f * r.nextFloat();
                float al = 0.9f * (1f - prog(ac, 0, 12));
                ps2.ribbonFacing(d.scale(r0), d.scale(r1), 0.4f, 0.05f, 0, 1, 1, 1, 1, al, 0f);
            }
            Pen st = add(buf, GarouTex.STAR, ps, cam);
            st.billboard(Vec3.ZERO, 0.9f * (1f - s(prog(ac, 0, 5))) + 0.05f, 0f, 1, 1, 1, 1f - prog(ac, 0, 6));
        }
    }

    private static float easeOutBack(float x) {
        x = Mth.clamp(x, 0, 1);
        float c1 = 1.4f, c3 = c1 + 1f;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    // ===================================================================== terrain helpers
    /** Walks down from startY to the first block with a collision shape; returns the top of that block (or fallback). */
    private static double groundY(Level lv, double x, double startY, double z, double fallback) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        mp.set(bx, Mth.floor(startY), bz);
        if (!lv.hasChunkAt(mp)) return fallback;
        int y0 = Mth.floor(startY);
        for (int y = y0; y > y0 - 14; y--) {
            mp.set(bx, y, bz);
            BlockState st = lv.getBlockState(mp);
            if (!st.getCollisionShape(lv, mp).isEmpty()) return y + 1.0;
        }
        return Double.NaN;      // no ground within reach (a pit / cliff edge): callers fade the decal out
    }

    /**
     * Terrain-aware square decal: a (div x div) grid whose vertices each sample their own ground height (scanning down from
     * the centre height, so caves and ceilings are ignored and cliffs just fade out). `rot` spins the grid, uv stays fixed.
     */
    private void decal(Pen p, Level lv, Vec3 ctrl, Vec3 centerW, float radius, float rot, int div, float alpha) {
        if (alpha <= 0.01f) return;
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
                else a *= 1f - Mth.clamp((float) ((centerW.y - g - 2.0) / 8.0), 0f, 1f);   // far below the impact: fade
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

    /** Texture handles shared with the Gamma Ray Burst set (same folder, same look). */
    private static final class GarouTex {
        static final ResourceLocation DISC = tex("gamma_disc"), RING = tex("gamma_ring"), STAR = tex("gamma_star"),
                SPHERE = tex("gamma_sphere"), ARC = tex("gamma_arc"), BURST = tex("gamma_burst");
    }
}
