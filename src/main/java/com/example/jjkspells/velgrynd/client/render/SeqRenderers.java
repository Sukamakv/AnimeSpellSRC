package com.example.jjkspells.velgrynd.client.render;

import com.example.jjkspells.velgrynd.entity.*;
import com.example.jjkspells.vfx.MeshRender;
import com.example.jjkspells.vfx.MeshRender.Mode;
import com.example.jjkspells.vfx.Prim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.velgrynd.client.render.VgrGfx.*;

/**
 * The eight timeline renderers of the Velgrynd set: stellar fire and sacred geometry. Real 3D meshes (granulated suns, gold armillary
 * rings, lances, crystals, a faceted fire dragon) lit by the engine; additive light is kept to thin accents.
 */
public final class SeqRenderers {
    private SeqRenderers() {
    }

    abstract static class Base<T extends SeqEntity> extends VgrRenderer<T> {
        Base(EntityRendererProvider.Context ctx) {
            super(ctx);
        }
    }

    // ------------------------------------------------------------------ shared pieces

    /** Pre-cast gathering: a small sun over the caster inside a turning gold armillary. */
    static void gather(MultiBufferSource buf, PoseStack ps, float s, float until, Vec3 at, float t) {
        if (s >= until) {
            return;
        }
        float f = s / until;
        Vec3 c = at.add(0, 2.2 + f * 1.2, 0);
        sunBody(buf, ps, c, 0.25f + f * 0.8f, t, 1f);
        armillary(buf, ps, c, 1.6f + 1.8f * f, t, f, 3, 5f);
        sigil(buf, ps, at.add(0, 0.05, 0), 2.5f + 3f * f, t * 2f, f);
    }

    /** A ring around an axis (torus turned so its axis points along dir). */
    static void ringAlong(MultiBufferSource buf, PoseStack ps, Vec3 at, Vec3 dir, float R, float spin, float alpha) {
        MeshRender.pushAlong(ps, at, at.add(dir));
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        ps.scale(R, R, R);
        MeshRender.draw(buf, ps, torusFat(), "gold_plate", Mode.SOLID, GOLD, alpha, 0.9f, 0f, 0f);
        ps.popPose();
    }

    /** Solar prominences: bright arches of plasma standing on the surface of a sun. */
    static void prominences(MultiBufferSource buf, PoseStack ps, Vec3 c, float r, float t, int n, float alpha, int seed) {
        if (alpha <= 0.01f || r < 0.1f) {
            return;
        }
        float k = r * 1.4f, off = r * 0.714f;
        for (int i = 0; i < n; i++) {
            double az = i * 2.4 + seed + t * 0.008 * (i % 2 == 0 ? 1 : -1), el = 0.55 * Math.sin(i * 1.7 + seed) + 0.25;
            Vec3 nrm = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
            float grow = 0.75f + 0.25f * (float) Math.sin(t * 0.06 + i * 2.0);
            MeshRender.pushAlong(ps, c, c.add(nrm));
            ps.translate(0.0, off, 0.0);
            ps.mulPose(Axis.YP.rotationDegrees(i * 53f + t * 0.4f));
            ps.scale(k, k * grow, k);
            MeshRender.draw(buf, ps, prom(i + seed), "plasma", Mode.SOLID, EMBER, alpha, 1f, t * 0.003f, -t * 0.02f);
            ps.popPose();
        }
    }

    /** Beam end rings: gold hoops travelling along a beam. */
    static void hoops(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, float R, float t, float alpha, float spacing) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 2 || alpha <= 0.01f) {
            return;
        }
        Vec3 dn = d.scale(1.0 / len);
        int n = (int) Math.min(12, len / spacing);
        for (int i = 0; i < n; i++) {
            double u = (i * spacing + t * 2.4) % len;
            float fade = (float) Math.min(1.0, Math.min(u / 4.0, (len - u) / 6.0));
            ringAlong(buf, ps, a.add(dn.scale(u)), dn, R * (0.9f + 0.1f * (i % 2)), t * (i % 2 == 0 ? 6f : -6f), alpha * fade);
        }
    }

    // ------------------------------------------------------------------ Scorch Dragon's Breath

    public static class Breath extends Base<BreathEntity> {
        public Breath(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(BreathEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R2 = e.r2();
            if (owner == null) {
                return;
            }
            Vec3 look = owner.getViewVector(pt);
            Vec3 mouth = owner.getEyePosition(pt).add(look.scale(1.2)).add(0, -0.35, 0).subtract(base);
            Vec3 a = mouth, b = mouth.add(look.scale(e.r1()));
            for (double[] d : e.fx()) {
                if (d[0] == 1) {
                    a = new Vec3(d[2], d[3], d[4]).subtract(base);
                    b = new Vec3(d[5], d[6], d[7]).subtract(base);
                }
            }
            gather(buf, ps, s, 0.5f, ownerP, t);
            if (s < 6.9f) {
                float f = vis(s, 0.3f, 0.9f, 6.2f, 0.7f);
                Vec3 flat = new Vec3(look.x, 0, look.z);
                flat = flat.length() < 1e-3 ? new Vec3(0, 0, 1) : flat.normalize();
                // the scorch dragon manifests behind the caster, facing the beam
                dragon(buf, ps, ownerP.add(0, 5.5, 0).subtract(flat.scale(7.5)), look, 3.2f, t, f, 0.16f);
                sigil(buf, ps, ownerP.add(0, 0.05, 0), 6f, t * 1.5f, f);
                armillary(buf, ps, ownerP.add(0, 1.4, 0), 3.4f, t, 0.9f * f, 3, 4f);
            }
            if (s >= 0.5f && s < 2.6f) {
                float f = sm((s - 0.5f) / 2.0f);
                sunBody(buf, ps, a, 0.3f + 1.5f * f, t, 1f);
                armillary(buf, ps, a, 1.4f + 1.8f * (1f - f), t, 1f, 3, 9f);
            }
            if (s >= 2.5f && s < 6.6f) {
                float f = Math.min(sm((s - 2.5f) / 0.3f), 1f - sm((s - 6.2f) / 0.4f));
                float lockF = s > 5.5f ? sm((s - 5.5f) / 1.0f) : 0f;
                float W = 6.5f * (1f + 0.07f * (float) Math.sin(t * 1.3f)) * (1f - 0.3f * lockF);
                column(buf, ps, a, b, W, t, f);
                hoops(buf, ps, a, b, W * 0.8f, t, f, 15f);
                armillary(buf, ps, a, 2.2f, t, f, 3, 9f);
                float grow = 1.6f + 1.4f * sm((s - 2.5f) / 3f) + 7f * sm((s - 5.5f) / 1.0f);
                sunBody(buf, ps, b.add(0, grow * 0.6f, 0), grow, t, f);
                wire(buf, ps, Prim.Poly.DODECA, b.add(0, grow * 0.6f, 0), grow * 1.7f, grow * 1.7f, grow * 1.7f, t * 2f, t, 0f, f * sm((s - 4.5f) / 1f));
                float R = 4f + 10f * sm((s - 2.5f) / 3f);
                magma(buf, ps, b, R, t, f);
                if (s > 3.0f) {
                    Vec3 dn = b.subtract(a).normalize();
                    prominences(buf, ps, b.add(0, grow * 0.6f, 0), grow, t, 4, f, 3);
                }
            }
            if (s >= 6.5f && s < 7.9f) {
                float f = (s - 6.5f) / 1.4f;
                burst(buf, ps, b, R2 * 0.9f, f, t);
                sunShell(buf, ps, b.add(0, R2 * 0.15f, 0), R2 * 0.55f * sm(Math.min(1f, f * 1.2f)), t, 1f - f);
            }
        }
    }

    // ------------------------------------------------------------------ Cardinal Inferno

    public static class Inferno extends Base<InfernoEntity> {
        public Inferno(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        private static Vec3 sunPos(int i, float s, float R1, float conv) {
            double ang = i * Math.PI * 2 / 7 + s * 0.15;
            Vec3 orbit = polar(ang, R1 * 0.55 * (1f - conv), 34 - 14 * conv + 1.5 * Math.sin(s * 1.6 + i));
            return orbit.lerp(new Vec3(0, 22, 0), conv * 0.9);
        }

        @Override
        protected void draw(InfernoEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 1.5f, ownerP, t);
            float f = vis(s, 1.5f, 1.0f, 9.5f, 1.0f);
            if (f > 0.01f) {
                float open = sm((s - 1.5f) / 1.0f);
                sigil(buf, ps, new Vec3(0, 0.05, 0), R1 * open, t * 0.5f, f);
                magma(buf, ps, Vec3.ZERO, R1 * 0.95f * open, t, 0.55f * f);
            }
            float conv = s > 6.5f ? sm((s - 6.5f) / 1.0f) : 0f;
            if (s >= 2.0f && s < 7.6f) {
                float a = sm((s - 2.0f) / 0.8f) * (1f - sm((s - 7.2f) / 0.4f));
                for (int i = 0; i < 7; i++) {
                    float ti = 3.0f + i * 0.5f;
                    float pulse = 1f + 0.35f * Math.max(0f, 1f - Math.abs(s - ti) / 0.6f);
                    Vec3 p = sunPos(i, s, R1, conv);
                    float r = 2.6f * pulse;
                    sunBody(buf, ps, p, r, t + i * 11f, a);
                    prominences(buf, ps, p, r, t, 2, a, i * 3);
                    armillary(buf, ps, p, r * 1.9f, t + i * 20f, 0.8f * a, 2, 4f);
                }
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] == 1 && age >= 0 && age <= 24) {
                    Vec3 q = new Vec3(d[2], d[3], d[4]).subtract(base);
                    int i = (int) d[5];
                    float k = 1f - age / 24f;
                    Vec3 from = sunPos(i, (float) d[1] / 20f, R1, 0f);
                    lance(buf, ps, i, from, q, 0.3f, k);
                    burst(buf, ps, q, 11f, age / 24f, t);
                }
            }
            if (s >= 7.5f && s < 9.0f) {
                burst(buf, ps, Vec3.ZERO, R1 * 0.7f, (s - 7.5f) / 1.5f, t);
            }
            if (s >= 7.3f && s < 10.1f) {
                float g = sm((s - 7.3f) / 2.2f);
                float shrink = 1f - 0.4f * sm((s - 9.0f) / 1.0f);
                float r = (3.5f + 11f * g) * shrink;
                Vec3 c = new Vec3(0, 18, 0);
                float a = Math.min(sm((s - 7.3f) / 0.4f), 1f - sm((s - 10.0f) / 0.12f));
                sunBody(buf, ps, c, r, t, a);
                prominences(buf, ps, c, r, t, 6, a, 11);
                wire(buf, ps, Prim.Poly.DODECA, c, r * 1.7f, r * 1.7f, r * 1.7f, t * 1.3f, t * 0.7f, 0f, a * g);
                armillary(buf, ps, c, r * 2.3f, t, a * g, 4, 5f);
            }
            if (s >= 10.0f && s < 11.0f) {
                float ff = s - 10.0f;
                Vec3 c = new Vec3(0, 14, 0);
                sunShell(buf, ps, c, R2 * 0.8f * sm(Math.min(1f, ff * 1.2f)), t, 1f - ff);
                burst(buf, ps, Vec3.ZERO, R2, ff, t);
                crownBlades(buf, ps, new Vec3(0, 0.4, 0), R2 * sm(ff), 14f, t, 0f, 1f - ff);
            }
        }
    }

    // ------------------------------------------------------------------ Cardinal Acceleration

    public static class Accel extends Base<AccelEntity> {
        public Accel(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(AccelEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R = e.r1();
            if (s < 0.8f) {
                float f = sm(s / 0.8f);
                Vec3 o = ownerP.add(0, 1.2, 0);
                crystal(buf, ps, Prim.Poly.OCTA, o, 0.4f + 1.0f * f, t * 8f, t * 5f, 1f);
                armillary(buf, ps, o, 2.4f * f, t, f, 3, 12f);
                sigil(buf, ps, ownerP.add(0, 0.05, 0), 3f + 3f * f, t * 3f, f);
            }
            if (s >= 1.0f && s < 5.2f) {
                float f = vis(s, 1.0f, 0.5f, 4.6f, 0.6f);
                sigil(buf, ps, new Vec3(0, 0.05, 0), R * f, t, f);
                magma(buf, ps, Vec3.ZERO, R * 0.9f * f, t, 0.4f * f);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (age < 0) {
                    continue;
                }
                if (d[0] == 2 && age <= 22) {
                    Vec3 q = new Vec3(d[2], d[3], d[4]).subtract(base).add(0, 1, 0);
                    float k = 1f - age / 22f;
                    crystal(buf, ps, Prim.Poly.OCTA, q, 1.6f * k, t * 9f, t * 6f, k);
                    wire(buf, ps, Prim.Poly.OCTA, q, 3f * k, 3f * k, 3f * k, -t * 6f, t * 3f, 0f, k);
                }
                if (d[0] == 1 && age <= 20) {
                    Vec3 q = new Vec3(d[2], d[3], d[4]).subtract(base);
                    int i = (int) d[5];
                    if (i < 0) {
                        float k = 1f - age / 20f;
                        lance(buf, ps, 2, q.add(0, 52, 0), q, 0.5f, k);
                        burst(buf, ps, q, R * 0.9f, age / 20f, t);
                        armillary(buf, ps, q.add(0, 9, 0), 9f * (1f - age / 20f), t, k, 3, 6f);
                    } else if (age <= 14) {
                        float k = 1f - age / 14f;
                        Vec3 from = new Vec3(d[6], d[7], d[8]).subtract(base).add(0, 1, 0);
                        lance(buf, ps, i, from, q.add(0, 1, 0), 0.35f, k);
                        burst(buf, ps, q, 6f, age / 14f, t);
                        crystal(buf, ps, Prim.Poly.OCTA, q.add(0, 1.2, 0), 1.1f * k, t * 10f + i * 40f, t * 7f, k);
                    }
                }
            }
            if (s >= 4.5f && s < 5.9f) {
                float f = vis(s, 4.5f, 0.4f, 5.4f, 0.5f);
                Vec3 o = ownerP.add(0, 1.4, 0);
                lance(buf, ps, 1, o.add(0, 6, 0), Vec3.ZERO, 0.12f, f);
                armillary(buf, ps, o, 3.2f, t, f, 4, 10f);
                sunBody(buf, ps, o, 0.9f, t, f);
            }
            if (s >= 5.8f && s < 6.9f) {
                burst(buf, ps, Vec3.ZERO, R * 1.2f, s - 5.8f, t);
            }
        }
    }

    // ------------------------------------------------------------------ Scorch Dragon's Dominion

    public static class Dominion extends Base<DominionEntity> {
        public Dominion(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(DominionEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R = e.r1();
            gather(buf, ps, s, 1.0f, ownerP, t);
            float f = vis(s, 1.0f, 1.0f, 12.2f, 1.6f);
            if (f > 0.01f) {
                float open = sm((s - 1.0f) / 1.0f);
                float press = s > 3.0f && s < 11.0f ? 1f : 0.6f;
                magma(buf, ps, Vec3.ZERO, R * open, t, f);
                sigil(buf, ps, new Vec3(0, 0.1, 0), R * 0.99f * open, t * 0.3f, f);
                sigil(buf, ps, new Vec3(0, 0.12, 0), R * 0.55f * open, -t * 0.5f, f);
                // armillary dome: great circles around the domain
                for (int i = 0; i < 4; i++) {
                    float rr = R * 0.97f * open;
                    put(buf, ps, torus(), "gold_plate", Mode.SOLID, new Vec3(0, 0.4, 0), rr, rr, rr, t * 0.25f * (i % 2 == 0 ? 1f : -1f) + i * 45f, 90f, 0f, GOLD, f, 0.85f, 0f, 0f);
                }
                put(buf, ps, band(), "gold_plate", Mode.SOLID, new Vec3(0, 0.3, 0), R * open, R * open * 0.4f, R * open, 0f, 0f, 0f, GOLD, f, 0.85f, 0f, 0f);
                // the sun of the zenith
                float r = (R * 0.07f + R * 0.1f * sm((s - 10.5f) / 1.5f)) * press * open;
                Vec3 c = new Vec3(0, R * 0.8f, 0);
                sunBody(buf, ps, c, r, t, f);
                prominences(buf, ps, c, r, t, 5, f, 5);
                crownBlades(buf, ps, c, r * 1.25f, r * 1.1f, t * 0.4f, 0f, f);
                armillary(buf, ps, c, r * 2.1f, t, f, 3, 3f);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] == 1 && age >= 0 && age <= 20) {
                    Vec3 q = new Vec3(d[2], d[3], d[4]).subtract(base);
                    float k = 1f - age / 20f;
                    pillar(buf, ps, q, 38f * sm(age / 4f), 1.5f + 1.2f * sm(age / 6f), t, k);
                }
            }
            if (s >= 2.0f && s < 3.2f) {
                burst(buf, ps, Vec3.ZERO, R * 0.7f, (s - 2.0f) / 1.2f, t);
            }
            if (s >= 12.0f && s < 13.5f) {
                float ff = (s - 12.0f) / 1.5f;
                sunShell(buf, ps, new Vec3(0, R * 0.25f, 0), R * 0.95f * sm(Math.min(1f, ff * 1.2f)), t, 1f - ff);
                burst(buf, ps, Vec3.ZERO, R * 1.1f, ff, t);
            }
        }
    }

    // ------------------------------------------------------------------ Parallel Existence

    public static class Parallel extends Base<ParallelEntity> {
        public Parallel(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(ParallelEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, dist = e.r1(), R = e.r2(), yaw = e.yaw();
            gather(buf, ps, s, 1.5f, ownerP, t);
            float f = vis(s, 0.8f, 1.0f, 10.4f, 1.0f);
            if (f > 0.01f) {
                sigil(buf, ps, new Vec3(0, 0.05, 0), dist * 1.2f * f, t, f);
                magma(buf, ps, Vec3.ZERO, R * 0.8f * sm((s - 6f) / 1f) * f, t, 0.7f * f);
                float cage = vis(s, 6.0f, 1.0f, 9.5f, 0.8f);
                if (cage > 0.01f) {
                    wire(buf, ps, Prim.Poly.OCTA, new Vec3(0, 14, 0), dist * cage, dist * 0.9f * cage, dist * cage, -(yaw + 45f), 0f, 0f, cage);
                    sigil(buf, ps, new Vec3(0, 0.12, 0), R * cage, -t, cage);
                }
            }
            if (s >= 1.5f && s < 11.5f) {
                float bf = vis(s, 1.5f, 0.8f, 10.5f, 1.0f);
                for (int k = 0; k < 4; k++) {
                    Vec3 bp = ParallelEntity.body(k, dist, yaw);
                    Vec3 toC = new Vec3(-bp.x, 0, -bp.z).normalize();
                    float fy = (float) Math.toDegrees(Math.atan2(toC.x, toC.z));
                    // the crystal body, its gold halos and a faceted dragon head above it
                    ps.pushPose();
                    ps.translate(bp.x, 10.5, bp.z);
                    ps.mulPose(Axis.YP.rotationDegrees(fy));
                    ps.scale(6f, 10f, 1f);
                    MeshRender.draw(buf, ps, plate(), Gfx.FIGURE, Mode.ADD, HOT, 0.8f * bf, 1f, 0f, 0f);
                    ps.popPose();
                    Vec3 hc = bp.add(0, 12.5, 0).subtract(toC.scale(2.5));
                    ringAlong(buf, ps, hc, toC, 9f, t * 3f, bf);
                    ringAlong(buf, ps, hc.add(toC.scale(-0.8)), toC, 6.5f, -t * 4f, bf);
                    Vec3 hp = bp.add(0, 27, 0);
                    Vec3 hd = new Vec3(-bp.x, 8.0 - 27.0, -bp.z).normalize();
                    dragonHead(buf, ps, hp, hd, 5.5f, bf);
                    Vec3 mouth = hp.add(hd.scale(3.2));
                    if (s >= 2.2f && s < 3.1f) {
                        sunBody(buf, ps, mouth, 0.4f + 1.6f * sm((s - 2.2f) / 0.9f), t, bf);
                    }
                    if (s >= 3.0f && s < 6.8f) {
                        float g = Math.min(sm((s - 3.0f) / 0.3f), 1f - sm((s - 6.4f) / 0.4f));
                        column(buf, ps, mouth, new Vec3(0, 2.5, 0), 2.6f * (1f + 0.08f * (float) Math.sin(t + k)), t, g * bf);
                        hoops(buf, ps, mouth, new Vec3(0, 2.5, 0), 2.6f, t, g * bf, 12f);
                    }
                }
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] == 1 && age >= 0 && age <= 12) {
                    burst(buf, ps, new Vec3(d[2], d[3], d[4]).subtract(base), 4f, age / 12f, t);
                }
            }
            if (s >= 7.5f && s < 8.7f) {
                burst(buf, ps, Vec3.ZERO, R * 0.8f, (s - 7.5f) / 1.2f, t);
            }
            if (s >= 9.0f && s < 10.0f) {
                float g = sm((s - 9.0f) / 1.0f);
                sunBody(buf, ps, new Vec3(0, 8, 0), 2f + 7f * g, t, 1f);
            }
            if (s >= 10.0f && s < 11.4f) {
                float ff = (s - 10.0f) / 1.4f;
                sunShell(buf, ps, new Vec3(0, 6, 0), R * 1.1f * sm(Math.min(1f, ff * 1.2f)), t, 1f - ff);
                burst(buf, ps, Vec3.ZERO, R * 1.3f, ff, t);
            }
        }
    }

    // ------------------------------------------------------------------ Dimensional Scorch

    public static class Pursuit extends Base<PursuitEntity> {
        public Pursuit(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        private static Vec3 portalPos(int i, float s, Vec3 tp, float R) {
            return tp.add(polar((i & 7) * Math.PI / 4 + s * 0.25, R * 0.6, 18 + 2 * Math.sin(s * 1.5 + i)));
        }

        @Override
        protected void draw(PursuitEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 1.0f, ownerP, t);
            Vec3 tp = Vec3.ZERO;
            for (double[] d : e.fx()) {
                if (d[0] == 3) {
                    tp = new Vec3(d[2], d[3], d[4]).subtract(base);
                }
            }
            if (s >= 0.8f && s < 8.4f) {
                float f = vis(s, 0.8f, 0.6f, 7.6f, 0.8f);
                sigil(buf, ps, tp.add(0, 0.05, 0), R * 0.7f * f, t, f);
                magma(buf, ps, tp, R * 0.5f * f, t, 0.5f * f);
                wire(buf, ps, Prim.Poly.OCTA, tp.add(0, 3.5, 0), 3.2f, 3.2f, 3.2f, t * 3f, t * 2f, 0f, f);
                float speed = s > 6f ? 1.4f : 1f;
                for (int i = 0; i < 8; i++) {
                    float pf = f * sm((s - 1.0f - i * 0.08f) / 0.6f);
                    if (pf <= 0.01f) {
                        continue;
                    }
                    Vec3 pp = portalPos(i, s, tp, R);
                    MeshRender.pushAlong(ps, pp, tp);
                    ps.pushPose();
                    ps.mulPose(Axis.YP.rotationDegrees(t * 2f * (i % 2 == 0 ? 1f : -1f)));
                    ps.scale(5.2f * pf, 5.2f * pf, 5.2f * pf);
                    MeshRender.draw(buf, ps, torusFat(), "gold_plate", Mode.SOLID, GOLD, pf, 0.9f, 0f, 0f);
                    MeshRender.draw(buf, ps, notched(16), "gold_plate", Mode.SOLID, GOLD, pf, 0.9f, 0f, 0f);
                    ps.popPose();
                    ps.pushPose();
                    ps.scale(4.7f * pf, 4.7f * pf, 4.7f * pf);
                    MeshRender.draw(buf, ps, disc(), "magma", Mode.SOLID, PURE, pf, 0.9f * speed, t * 0.004f, 0f);
                    ps.popPose();
                    ps.popPose();
                }
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] == 1 && age >= 0 && age <= 12) {
                    Vec3 q = new Vec3(d[2], d[3], d[4]).subtract(base);
                    int portal = (int) d[5];
                    float k = 1f - age / 12f;
                    if (portal >= 0) {
                        lance(buf, ps, (int) d[6], portalPos(portal, (float) d[1] / 20f, q, R), q, 0.3f, k);
                        burst(buf, ps, q, 7f, age / 12f, t);
                    }
                }
            }
            if (s >= 6.0f && s < 8.05f) {
                float c = sm((s - 6.0f) / 1.9f);
                float f = Math.min(sm((s - 6.0f) / 0.4f), 1f);
                Vec3 tip = tp.add(0, 70f * (1f - c), 0);
                Vec3 butt = tip.add(0, 60, 0);
                lance(buf, ps, 3, butt, tip, 0.8f, f);
                armillary(buf, ps, tip.add(0, 18, 0), 11f, t, f, 3, 8f);
            }
            if (s >= 8.0f && s < 9.5f) {
                float ff = (s - 8.0f) / 1.5f;
                sunShell(buf, ps, tp.add(0, 4, 0), R2 * 0.7f * sm(Math.min(1f, ff * 1.2f)), t, 1f - ff);
                burst(buf, ps, tp, R2, ff, t);
                crownBlades(buf, ps, tp.add(0, 0.4, 0), R2 * sm(ff), 12f, t, 0f, 1f - ff);
            }
        }
    }

    // ------------------------------------------------------------------ Cardinal Cataclysm

    public static class Star extends Base<StarEntity> {
        public Star(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        private static float h(int i, int k) {
            return (float) Math.abs((Math.sin(i * 12.9898 + k * 78.233) * 43758.5453) % 1.0);
        }

        @Override
        protected void draw(StarEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 1.5f, ownerP, t);
            if (s >= 1.5f && s < 6.7f) {
                float f = vis(s, 1.5f, 1.0f, 6.3f, 0.4f);
                sigil(buf, ps, new Vec3(0, 0.05, 0), R1 * f, t * 0.8f, f);
                // a thousand stars, falling one by one onto the aimed point
                for (int i = 0; i < 48; i++) {
                    float t0 = 4.3f + h(i, 1) * 1.4f;
                    if (s >= t0 + (6.5f - t0)) {
                        continue;
                    }
                    float ap = sm((s - 1.5f - h(i, 2) * 1.5f) / 0.7f);
                    float fall = s > t0 ? sm((s - t0) / (6.5f - t0)) : 0f;
                    double ang = i * 2.399 + s * 0.05;
                    double dd = Math.sqrt(h(i, 3)) * R1 * 1.15 * (1.0 - 0.35 * fall);
                    float h0 = 38f + h(i, 4) * 45f;
                    Vec3 p = polar(ang, dd, h0 * (1f - fall) + 1.2f * fall);
                    float sz = 1.5f + 1.5f * h(i, 5);
                    put(buf, ps, star(), "crystal_red", Mode.SOLID, p, sz, sz, sz, t * (2f + 4f * h(i, 6)), t * 1.5f, 0f, PURE, ap, 0.8f, 0f, 0f);
                    if (fall > 0.02f) {
                        column(buf, ps, p, p.add(0, 14f * (0.4f + fall), 0), 0.5f, t, 0.6f * ap);
                    }
                }
                if (s > 3.0f) {
                    float c = sm((s - 3.0f) / 3.5f);
                    Vec3 sp = new Vec3(0, 58f - 48f * c, 0);
                    float r = 2f + 9f * c;
                    sunBody(buf, ps, sp, r, t, f);
                    wire(buf, ps, Prim.Poly.ICOSA, sp, r * 1.6f, r * 1.6f, r * 1.6f, t * 1.5f, t, 0f, f);
                }
            }
            if (s >= 6.5f && s < 7.8f) {
                burst(buf, ps, Vec3.ZERO, R1, (s - 6.5f) / 1.3f, t);
            }
            if (s >= 7.0f && s < 12.1f) {
                float g = sm((s - 7.0f) / 2.0f);
                float shrink = s > 9.5f ? 1f - 0.62f * sm((s - 9.5f) / 2.3f) : 1f;
                float r = (2f + 12f * g) * shrink;
                float a = Math.min(sm((s - 7.0f) / 0.3f), 1f - sm((s - 12.0f) / 0.1f));
                Vec3 c = new Vec3(0, 7, 0);
                sunBody(buf, ps, c, r, t, a);
                prominences(buf, ps, c, r, t, 6, a, 17);
                armillary(buf, ps, c, r * 1.9f, t, a * g, 4, 6f);
                wire(buf, ps, Prim.Poly.DODECA, c, r * 1.4f, r * 1.4f, r * 1.4f, -t * 2f, t * 1.2f, 0f, a * (s > 9.3f ? sm((s - 9.3f) / 0.7f) : 0f));
                shock(buf, ps, Vec3.ZERO, R1 * (1f + 0.9f * sm((s - 7.0f) / 1.8f)), a * (1f - sm((s - 7.0f) / 2.5f)));
            }
            if (s >= 9.5f && s < 12.0f) {
                float f = vis(s, 9.5f, 0.4f, 11.5f, 0.5f);
                float pull = sm((s - 9.5f) / 2.0f);
                for (int i = 0; i < 8; i++) {
                    Vec3 far = polar(i * Math.PI / 4 + s * 0.4, R1 * (1f - 0.75f * pull), 4 + 2 * Math.sin(t * 0.2 + i));
                    lance(buf, ps, i, far, new Vec3(0, 7, 0), 0.4f, f);
                }
            }
            if (s >= 12.0f && s < 13.2f) {
                float ff = (s - 12.0f) / 1.2f;
                sunShell(buf, ps, new Vec3(0, 6, 0), R2 * 0.8f * sm(Math.min(1f, ff * 1.2f)), t, 1f - ff);
                burst(buf, ps, Vec3.ZERO, R2, ff, t);
                shock(buf, ps, Vec3.ZERO, R2 * 1.1f * sm(ff), 1f - ff);
            }
        }
    }

    // ------------------------------------------------------------------ Flame God Cthugha

    public static class Cthugha extends Base<CthughaEntity> {
        public Cthugha(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(CthughaEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, D = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 2.0f, ownerP, t);
            if (s >= 2.0f && s < 23.5f) {
                float f = vis(s, 2.0f, 2.0f, 20.5f, 3.0f);
                float open = sm((s - 2.0f) / 2.5f);
                magma(buf, ps, Vec3.ZERO, D * open, t, f);
                sigil(buf, ps, new Vec3(0, 0.1, 0), D * 0.99f * open, t * 0.2f, f);
                sigil(buf, ps, new Vec3(0, 0.12, 0), D * 0.6f * open, -t * 0.3f, f);
                put(buf, ps, band(), "gold_plate", Mode.SOLID, new Vec3(0, 0.3, 0), D * open, D * open * 0.5f, D * open, 0f, 0f, 0f, GOLD, f, 0.85f, 0f, 0f);
                // the colossal sun and its armillary
                float pulse = 1f + 0.12f * Math.max(0f, 1f - Math.abs(s - 8.0f) / 0.6f) + 0.12f * Math.max(0f, 1f - Math.abs(s - 10.5f) / 0.6f) + 0.18f * Math.max(0f, 1f - Math.abs(s - 14.0f) / 0.6f);
                float r = (12f * sm((s - 2.0f) / 2.5f) + 8f * sm((s - 14.0f) / 3f) - 7f * sm((s - 18.5f) / 2f)) * pulse;
                Vec3 c = new Vec3(0, 70, 0);
                sunBody(buf, ps, c, r, t, f);
                prominences(buf, ps, c, r, t, 8, f, 21);
                crownBlades(buf, ps, c, r * 1.15f, r * 1.1f, t * 0.3f, 0f, f);
                armillary(buf, ps, c, r * 1.9f, t, f, 3, 3f);
                armillary(buf, ps, c, r * 2.7f, -t, f, 4, 2f);
                armillary(buf, ps, c, r * 3.5f, t * 0.6f, f * 0.9f, 3, 1.5f);
                wire(buf, ps, Prim.Poly.ICOSA, c, r * 1.6f, r * 1.6f, r * 1.6f, t * 0.8f, t * 0.5f, 0f, f);
                // two faceted fire dragons coil the domain
                float df = vis(s, 3.0f, 1.5f, 21.0f, 1.5f);
                for (int k = 0; k < 2; k++) {
                    double th = s * 0.14 + k * Math.PI;
                    Vec3 p = polar(th, D * 0.55, 45 + 8 * Math.sin(s * 0.8 + k));
                    Vec3 dir = new Vec3(-Math.sin(th), 0.05, Math.cos(th));
                    dragon(buf, ps, p, dir, 14f, t, df, 0.12f);
                }
                // lances of light rain along the first strike and feed the sun during the collapse
                if (s >= 9.5f && s < 11.3f) {
                    float lf = vis(s, 9.5f, 0.3f, 10.8f, 0.5f);
                    for (int i = 0; i < 12; i++) {
                        lance(buf, ps, i, c, polar(i * Math.PI / 6 + 0.3, D * 0.5, 0), 0.25f, lf);
                    }
                }
                if (s >= 15.0f && s < 20.5f) {
                    float lf = vis(s, 15.0f, 0.5f, 19.8f, 0.7f);
                    float pull = sm((s - 15.0f) / 4.0f);
                    for (int i = 0; i < 10; i++) {
                        lance(buf, ps, i, polar(i * Math.PI / 5 + s * 0.3, D * (0.95f - 0.7f * pull), 0), c.add(0, -r * 0.5f, 0), 0.2f, lf);
                    }
                }
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] == 1 && age >= 0 && age <= 20) {
                    Vec3 q = new Vec3(d[2], d[3], d[4]).subtract(base);
                    pillar(buf, ps, q, 70f * sm(age / 5f), 3f + 2f * sm(age / 6f), t, 1f - age / 20f);
                }
            }
            if (s >= 8.0f && s < 9.6f) {
                burst(buf, ps, Vec3.ZERO, D * 0.75f, (s - 8.0f) / 1.6f, t);
            }
            if (s >= 10.5f && s < 12.1f) {
                burst(buf, ps, Vec3.ZERO, D * 0.9f, (s - 10.5f) / 1.6f, t);
            }
            if (s >= 14.0f && s < 15.8f) {
                burst(buf, ps, Vec3.ZERO, D, (s - 14.0f) / 1.8f, t);
            }
            if (s >= 20.5f && s < 23.5f) {
                float ff = (s - 20.5f) / 3.0f;
                sunShell(buf, ps, new Vec3(0, 20, 0), R2 * 0.85f * sm(Math.min(1f, ff * 1.3f)), t, 1f - ff);
                burst(buf, ps, Vec3.ZERO, R2, ff, t);
                shock(buf, ps, Vec3.ZERO, R2 * 1.15f * sm(ff), 1f - ff);
                if (ff < 0.6f) {
                    column(buf, ps, new Vec3(0, 70, 0), Vec3.ZERO, 20f * (1f - ff / 0.6f), t, 1f);
                }
            }
        }
    }
}
