package com.example.jjkspells.diablo.client.render;

import com.example.jjkspells.diablo.entity.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

import static com.example.jjkspells.diablo.client.render.Gfx.*;

/** The seven timeline renderers. Everything is a function of the entity age (t, in ticks) and the synched radii. */
public final class SeqRenderers {
    private SeqRenderers() {
    }

    // ------------------------------------------------------------------ helpers

    static void blackOrb(MultiBufferSource buf, PoseStack ps, Vec3 p, float r, float alpha) {
        if (r < 0.05f || alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        Draw.sphere(trans(buf, BLACK), ps, r, 10, 16, 0f, WHITE, alpha);
        ps.popPose();
    }

    /** A black sphere with a burning rim: the black sun / the stars / the extinction star. */
    static void blackSun(MultiBufferSource buf, PoseStack ps, Vec3 p, float r, float t, float alpha, float[] rim) {
        if (r < 0.05f || alpha <= 0.01f) {
            return;
        }
        blackOrb(buf, ps, p, r, alpha);
        Fx.orb(buf, ps, p, r * 1.06f, t * 0.02f, rim, 0.28f * alpha);
        Fx.glowTex(buf, ps, CORONA, p, r * 2.6f, t * 1.5f, rim, 0.9f * alpha);
        Fx.glowTex(buf, ps, GLOW, p, r * 3.4f, 0f, rim, 0.35f * alpha);
    }

    /** A flat texture standing in a vertical plane facing 'yaw'. */
    static void upright(MultiBufferSource buf, PoseStack ps, net.minecraft.resources.ResourceLocation tex, Vec3 p, float yaw, float half, float rot, float[] c, float alpha) {
        if (alpha <= 0.01f || half <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(rot));
        Draw.xy(add(buf, tex), ps, half, half, c, alpha);
        ps.popPose();
    }

    static Vec3 polar(double ang, double r, double y) {
        return new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
    }

    static float dur(float sec, float a, float b) {
        return c01((sec - a) / (b - a));
    }

    /** Fade in over 'in' seconds from a, hold until b, fade out over 'out'. */
    static float vis(float sec, float a, float in, float b, float out) {
        return Math.min(smooth((sec - a) / Math.max(0.01f, in)), 1f - smooth((sec - b) / Math.max(0.01f, out)));
    }

    abstract static class Base<T extends SeqEntity> extends DiaRenderer<T> {
        Base(EntityRendererProvider.Context ctx) {
            super(ctx);
        }
    }

    // ------------------------------------------------------------------ Nether Apocalypse

    public static class Nether extends Base<NetherApocalypseEntity> {
        public Nether(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(NetherApocalypseEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            Vec3 g = Vec3.ZERO;
            if (s < 0.8f) {
                float f = s / 0.8f;
                Fx.glow(buf, ps, new Vec3(0, 2 + f * 2, 0), 0.4f + f * 1.6f, CRIM, 0.4f + 0.5f * f);
                Fx.star(buf, ps, new Vec3(0, 2 + f * 2, 0), 0.8f + f * 2.4f, t * 6, SCAR, f);
                Fx.disc(buf, ps, CIRCLE, new Vec3(0, 0.07, 0), 3f + 3f * f, t * 3, CRIM, 0.5f * f);
            }
            float rad = 0, y = 0, a = 1f;
            if (s >= 0.8f && s < 2f) {
                float f = smooth((s - 0.8f) / 1.2f);
                rad = 5f * f;
                y = 2 + 13 * f;
            } else if (s >= 2f && s < 3f) {
                float f = smooth(s - 2f);
                rad = 5f + 4f * f;
                y = 15;
            } else if (s >= 3f && s < 3.5f) {
                float f = smooth((s - 3f) / 0.5f);
                rad = 9f * (1f - 0.35f * f);
                y = 15 * (1f - f * f) + 3 * f * f * 0;
            } else if (s >= 3.5f && s < 3.8f) {
                float f = (s - 3.5f) / 0.3f;
                rad = 5.85f * (1f + 1.5f * f);
                y = 0;
                a = 1f - f;
            }
            if (rad > 0) {
                blackSun(buf, ps, new Vec3(0, y + rad * 0.2f, 0), rad, t, a, SCAR);
                if (s < 3.2f) {
                    for (int i = 0; i < 6; i++) {
                        double ang = t * 0.05 + i * Math.PI / 3;
                        Fx.bolt(buf, ps, new Vec3(0, y + rad * 0.2, 0), polar(ang, rad * 1.8, y + rad * 0.2 + Math.sin(t * 0.3 + i) * 2), cam, 0.25f, BLOODL, 0.6f * a, (long) (t / 3) * 7 + i);
                    }
                }
            }
            if (s >= 2f && s < 4.5f) {
                float f = vis(s, 2f, 0.6f, 3.6f, 0.9f);
                Fx.disc(buf, ps, RUNE, new Vec3(0, 0.09, 0), R1 * 0.9f * smooth((s - 2f) / 0.8f), t * 2f, CRIM, 0.8f * f);
                Fx.disc(buf, ps, CIRCLE, new Vec3(0, 0.1, 0), R1 * smooth((s - 2f) / 0.8f), -t * 1.4f, SCAR, 0.6f * f);
                Fx.beam(buf, ps, BEAM, new Vec3(0, 0.2, 0), new Vec3(0, y + 4, 0), cam, 0.6f + 1.4f * f, 1f, CRIM, 0.35f * f);
            }
            if (s >= 3.5f && s < 4.6f) {
                float f = (s - 3.5f) / 1.1f;
                Fx.wave(buf, ps, new Vec3(0, 0.2, 0), R1 * smooth(Math.min(1f, f * 1.5f)), SCAR, 1f - f);
                Fx.crack(buf, ps, new Vec3(0, 0.12, 0), R1 * 0.95f, 0f, CRIM, 1f - f * 0.5f);
                Fx.beam(buf, ps, BEAM, g, new Vec3(0, 60, 0), cam, 7f * (1f - f), 1f, SCAR, 0.9f * (1f - f));
                Fx.glow(buf, ps, new Vec3(0, 3, 0), R1 * 0.5f * (1f - f * 0.5f), SCAR, 0.9f * (1f - f));
            }
            if (s >= 4.5f && s < 5.9f) {
                float f = (s - 4.5f) / 1.3f;
                float r = R2 * smooth(Math.min(1f, f * 1.1f));
                Vec3 c = new Vec3(0, 5, 0);
                Fx.orb(buf, ps, c, r, t * 0.03f, SCAR, 0.5f * (1f - f * 0.7f));
                Fx.orb(buf, ps, c, r * 0.8f, -t * 0.05f, CRIM, 0.45f * (1f - f * 0.6f));
                Fx.shell(buf, ps, c, r, t * 0.02f, BLOODL, 0.5f * (1f - f * 0.6f));
                blackOrb(buf, ps, c, r * 0.3f * (1f - f), 0.9f);
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), r, SCAR, 0.9f * (1f - f));
                Fx.glow(buf, ps, c, r * 0.8f, WHITE, 0.5f * (1f - f));
            }
            if (s >= 5.8f) {
                float f = (s - 5.8f) / 1.2f;
                float r = R2 * smooth(Math.min(1f, f * 1.4f));
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), r, CRIM, 1f - f);
                Fx.wave(buf, ps, new Vec3(0, 0.5, 0), r * 0.8f, SCAR, 0.7f * (1f - f));
                Fx.glow(buf, ps, new Vec3(0, 2, 0), R2 * 0.5f * (1f - f), SCAR, 0.4f * (1f - f));
            }
        }
    }

    // ------------------------------------------------------------------ Primordial Dominion

    public static class Dominion extends Base<PrimordialDominionEntity> {
        public Dominion(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(PrimordialDominionEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R = e.r1();
            if (s < 2f) {
                Fx.discT(buf, ps, BLACK, new Vec3(0, 0.06, 0), R * smooth(s / 1f), 0f, WHITE, 0.7f * vis(s, 0f, 0.5f, 6.5f, 0.5f));
            }
            float sil = vis(s, 1f, 1f, 5.8f, 1.2f);
            float h = 20f;
            if (sil > 0.01f) {
                Vec3 c = new Vec3(0, h, 0);
                Fx.blob(buf, ps, DEMON, c, 20f, 0f, WHITE, 0.92f * sil);
                Fx.glowTex(buf, ps, GLOW, c.add(0, 8, 0), 26f, 0f, CRIM, 0.18f * sil);
                float blink = 0.75f + 0.25f * (float) Math.sin(t * 0.3);
                Fx.glowTex(buf, ps, EYE, new Vec3(-2.2, h + 13, 0), 2.6f, 0f, BLOODL, blink * sil);
                Fx.glowTex(buf, ps, EYE, new Vec3(2.2, h + 13, 0), 2.6f, 0f, BLOODL, blink * sil);
            }
            if (s >= 2f && s < 4.5f) {
                float f = (s - 2f) % 1f;
                Fx.wave(buf, ps, new Vec3(0, 0.2, 0), R * smooth(Math.min(1f, (s - 2f) / 1.4f)), CRIM, 0.9f * (1f - Math.min(1f, (s - 2f) / 2.5f)));
                Fx.wave(buf, ps, new Vec3(0, 0.25, 0), R * f, SCAR, 0.35f);
            }
            if (s >= 2f && s < 6.3f) {
                float f = vis(s, 2f, 0.5f, 5.6f, 0.7f);
                Random r = new Random(e.getId());
                for (int i = 0; i < 8; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, d = R * (0.3 + 0.55 * r.nextDouble());
                    Vec3 hand = new Vec3(Math.cos(i * 0.8) * 6, h + 12, 0);
                    Vec3 ground = polar(ang, d, 0.2);
                    Vec3 mid = hand.lerp(ground, 0.5).add(0, -3 + Math.sin(t * 0.1 + i) * 1.2, 0);
                    Fx.beam(buf, ps, CHAIN, hand, mid, cam, 0.5f, 3f, CRIM, 0.85f * f);
                    Fx.beam(buf, ps, CHAIN, mid, ground, cam, 0.5f, 3f, CRIM, 0.85f * f);
                }
            }
            if (s >= 4.5f && s < 5.9f) {
                float f = vis(s, 4.5f, 0.2f, 5.4f, 0.5f);
                Random r = new Random(e.getId() * 3 + (long) (t / 6));
                for (int i = 0; i < 5; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, d = R * (0.2 + 0.6 * r.nextDouble());
                    Vec3 g = polar(ang, d, 0.1);
                    Fx.beam(buf, ps, BEAM, new Vec3(i % 2 == 0 ? -2.2 : 2.2, h + 13, 0), g, cam, 0.5f, 1f, BLOODL, 0.8f * f);
                    Fx.crack(buf, ps, g, 5f + i, i * 70f, CRIM, 0.9f * f);
                    Fx.glow(buf, ps, g.add(0, 0.5, 0), 2.5f, SCAR, 0.7f * f);
                }
            }
            if (s >= 5.8f) {
                float f = (s - 5.8f) / 1.2f;
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), R * smooth(Math.min(1f, f * 1.5f)), CRIM, 1f - f);
                Fx.glow(buf, ps, new Vec3(0, 3, 0), R * 0.4f * (1f - f), SCAR, 0.5f * (1f - f));
                Fx.crack(buf, ps, new Vec3(0, 0.15, 0), R * 0.9f, 0f, CRIM, 0.8f * (1f - f));
            }
        }
    }

    // ------------------------------------------------------------------ Dimensional Funeral

    public static class Funeral extends Base<DimensionalFuneralEntity> {
        public Funeral(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(DimensionalFuneralEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, L = e.r1(), R = e.r2(), yaw = e.yaw();
            double yr = Math.toRadians(yaw);
            Vec3 ax = new Vec3(Math.cos(yr), 0, Math.sin(yr));
            if (s < 0.8f) {
                Fx.star(buf, ps, new Vec3(0, 12, 0), 1f + s * 3f, t * 8, CRIM, s / 0.8f);
            }
            float open = vis(s, 0.7f, 1.1f, 5.6f, 0.9f);
            if (open > 0.01f) {
                float w = 1.0f + 6f * smooth((s - 0.7f) / 1.1f);
                upright(buf, ps, RIFT, new Vec3(0, 17.5, 0), yaw, 17.5f, 0f, CRIM, 0.95f * open);
                Fx.glowTex(buf, ps, GLOW, new Vec3(0, 17.5, 0), w * 3f, 0f, CRIM, 0.35f * open);
            }
            if (s >= 1.8f && s < 3.6f) {
                float f = smooth((s - 1.8f) / 1f);
                float fade = 1f - c01((s - 2.8f) / 0.8f);
                Vec3 a = ax.scale(-L / 2).add(0, 3, 0), b = ax.scale(-L / 2 + L * f).add(0, 3, 0);
                Fx.beam(buf, ps, BEAM, a, b, cam, e.r1() * 0.07f, Math.max(1f, L / 10f), WHITE, 0.95f * fade);
                Fx.beam(buf, ps, SLASH, a, b, cam, e.r1() * 0.14f, Math.max(1f, L / 10f), CRIM, 0.9f * fade);
                if (s < 2.9f) {
                    Fx.slash(buf, ps, b, yaw, 0f, 90f, 7f, SCAR, 0.9f);
                    Fx.glow(buf, ps, b, 5f, WHITE, 0.8f);
                }
            }
            List<double[]> fx = e.fx();
            for (double[] d : fx) {
                float age = t - (float) d[1];
                if (d[0] != 1 || age < 0 || age > 30) {
                    continue;
                }
                Vec3 q = v(d[2], d[3], d[4]).subtract(base);
                float f = 1f - age / 30f;
                Fx.crack(buf, ps, q.add(0, 0.12, 0), 5f, (float) d[1] * 37f, CRIM, f);
                upright(buf, ps, RIFT, q.add(0, 6, 0), yaw + (float) d[1] * 13f, 6f, 0f, SCAR, f * 0.9f);
                Fx.glow(buf, ps, q.add(0, 2, 0), 4f * f + 1f, WHITE, f * 0.6f);
            }
            if (s >= 4.5f && s < 6.0f) {
                float f = dur(s, 4.5f, 5.8f);
                Fx.disc(buf, ps, VORTEX, new Vec3(0, 0.15, 0), R * (1f - 0.55f * f), t * 5f, CRIM, 0.6f * vis(s, 4.5f, 0.3f, 5.6f, 0.3f));
                blackSun(buf, ps, new Vec3(0, 6, 0), 3.5f * smooth(f), t, vis(s, 4.5f, 0.4f, 5.7f, 0.1f), CRIM);
            }
            if (s >= 5.8f) {
                float f = dur(s, 5.8f, 6.5f);
                blackSun(buf, ps, new Vec3(0, 6, 0), 3.5f + R * 0.4f * smooth(f), t, 1f - f, SCAR);
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), R * smooth(f), SCAR, 1f - f);
                Fx.glow(buf, ps, new Vec3(0, 6, 0), R * 0.8f * (1f - f), WHITE, 0.6f * (1f - f));
            }
        }
    }

    // ------------------------------------------------------------------ Despair Time

    public static class Despair extends Base<DespairTimeEntity> {
        public Despair(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(DespairTimeEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R = e.r1(), yaw = e.yaw();
            boolean stopped = s >= 5.5f && s < 7.5f;
            float clock = vis(s, 0f, 0.9f, 7.4f, 0.4f);
            Vec3 cc = new Vec3(0, 14, 0);
            if (clock > 0.01f) {
                float size = 5f + 5f * smooth(s / 1f);
                float[] col = stopped ? WHITE : GOLD;
                upright(buf, ps, CLOCK, cc, yaw, size, 0f, col, 0.95f * clock);
                float hand = stopped ? 5.5f * 20f : t;
                double ah = hand * 0.12, am = hand * 0.012;
                double yr = Math.toRadians(yaw);
                Vec3 right = new Vec3(Math.cos(yr), 0, Math.sin(yr));
                Vec3 tipL = cc.add(right.scale(Math.sin(ah) * size * 0.85)).add(0, Math.cos(ah) * size * 0.85, 0);
                Vec3 tipS = cc.add(right.scale(Math.sin(am) * size * 0.55)).add(0, Math.cos(am) * size * 0.55, 0);
                Fx.beam(buf, ps, BEAM, cc, tipL, cam, 0.18f * size / 5f, 1f, col, 0.95f * clock);
                Fx.beam(buf, ps, BEAM, cc, tipS, cam, 0.3f * size / 5f, 1f, col, 0.95f * clock);
                Fx.glow(buf, ps, cc, size * 1.6f, CRIM, 0.25f * clock);
            }
            if (s >= 1f && s < 9f) {
                float open = smooth((s - 1f) / 1.5f);
                float closing = s >= 7.5f ? 1f - 0.9f * smooth((s - 7.5f) / 1.5f) : 1f;
                float f = vis(s, 1f, 1.2f, 8.4f, 0.6f);
                Fx.shell(buf, ps, Vec3.ZERO, R * open * closing, t * 0.01f, DVIO, 0.28f * f);
                Fx.disc(buf, ps, RUNE, new Vec3(0, 0.08, 0), R * 0.95f * open, t * 0.8f, DVIO, 0.7f * f);
                Fx.disc(buf, ps, CIRCLE, new Vec3(0, 0.1, 0), R * open, -t * 0.5f, CRIM, 0.5f * f);
                if (s < 1.6f) {
                    Fx.wave(buf, ps, new Vec3(0, 0.2, 0), R * smooth((s - 1f) / 0.6f), CRIM, 1f - (s - 1f) / 0.6f);
                }
            }
            if (s >= 2.5f && s < 7.6f) {
                float f = vis(s, 2.5f, 0.6f, 7.0f, 0.5f) * (stopped ? 1.2f : 1f);
                for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI / 6 + 0.2;
                    Vec3 a = polar(ang, R * 0.9, 0.2);
                    Vec3 mid = a.lerp(cc, 0.5).add(0, -3 + Math.sin(t * 0.08 + i) * 1.0 * (stopped ? 0.1 : 1), 0);
                    Fx.beam(buf, ps, CHAIN, a, mid, cam, 0.45f, 3f, stopped ? WHITE : DVIO, 0.8f * f);
                    Fx.beam(buf, ps, CHAIN, mid, cc, cam, 0.45f, 3f, stopped ? WHITE : DVIO, 0.8f * f);
                }
            }
            if (s >= 7.5f) {
                float f = dur(s, 7.5f, 9f);
                blackSun(buf, ps, cc.scale(0.5), R * 0.35f * smooth(Math.min(1f, f * 1.5f)), t, 1f - f, CRIM);
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), R * smooth(Math.min(1f, f * 1.5f)), DVIO, 1f - f);
                Fx.glow(buf, ps, cc, R * 0.6f * (1f - f), WHITE, 0.5f * (1f - f));
            }
        }
    }

    // ------------------------------------------------------------------ Temptation World

    public static class World extends Base<TemptationWorldEntity> {
        public World(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(TemptationWorldEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R = e.r1();
            if (s < 2.5f) {
                Fx.disc(buf, ps, RUNE, new Vec3(0, 0.08, 0), (6f + 4f * s) * vis(s, 0f, 0.5f, 2.2f, 0.3f), t * 2f, CRIM, 0.9f);
            }
            if (s >= 1f && s < 12f) {
                float open = smooth((s - 1f) / 1.5f);
                float closing = s >= 10f ? 1f - 0.9f * smooth((s - 10f) / 2f) : 1f;
                float f = vis(s, 1f, 1.0f, 11f, 1f);
                Fx.shell(buf, ps, Vec3.ZERO, R * open * closing, t * 0.01f, CRIM, 0.3f * f);
                blackOrb(buf, ps, Vec3.ZERO, R * 0.995f * open * closing, 0.45f * f);
                Fx.disc(buf, ps, RUNE, new Vec3(0, 0.09, 0), R * 0.95f * open, t * 0.6f, CRIM, 0.8f * f);
                Fx.disc(buf, ps, CIRCLE, new Vec3(0, 0.11, 0), R * 0.5f * open, -t * 1.2f, GOLD, 0.6f * f);
                if (s < 2f) {
                    Fx.wave(buf, ps, new Vec3(0, 0.2, 0), R * smooth((s - 1f) / 0.8f), SCAR, 1f - (s - 1f));
                }
            }
            if (s >= 2.5f && s < 11f) {
                float f = vis(s, 2.5f, 1.5f, 10f, 1f);
                Fx.strip(buf, ps, BLACK, false, Vec3.ZERO, new Vec3(0, 16, 0), cam, 3.2f, 0.7f, WHITE, 0.95f * f);
                Fx.glow(buf, ps, new Vec3(0, 16.5, 0), 4f, CRIM, 0.8f * f);
                Fx.glowTex(buf, ps, EYE, new Vec3(0, 12, 0), 3.2f, 0f, BLOODL, 0.9f * f);
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4 + 0.4;
                    Vec3 b = polar(ang, R * 0.8, 0);
                    Fx.strip(buf, ps, BLACK, false, b, b.add(0, 18, 0), cam, 1.4f, 0.5f, WHITE, 0.9f * f);
                    Fx.glow(buf, ps, b.add(0, 18.4, 0), 2.4f, CRIM, 0.8f * f);
                }
                Fx.glow(buf, ps, new Vec3(0, R * 0.8f, -R * 0.4f), 9f, BLOODL, 0.5f * f);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] != 1 || age < 0 || age > 24) {
                    continue;
                }
                Vec3 q = v(d[2], d[3], d[4]).subtract(base);
                float fall = c01(age / 6f);
                Vec3 tip = q.add(0, 30 * (1f - fall), 0);
                float f = age < 6 ? 1f : 1f - (age - 6f) / 18f;
                Fx.beam(buf, ps, SPEAR, tip.add(0, 10, 0), tip, cam, 0.9f, 1f, CRIM, f);
                Fx.beam(buf, ps, BEAM, tip.add(0, 10, 0), tip, cam, 0.25f, 1f, WHITE, f * 0.8f);
                if (age >= 6) {
                    Fx.wave(buf, ps, q.add(0, 0.2, 0), 4f * smooth((age - 6f) / 8f), SCAR, f);
                    Fx.glow(buf, ps, q.add(0, 1, 0), 3.5f * f, WHITE, f * 0.7f);
                }
            }
            if (s >= 8f && s < 10.1f) {
                float f = vis(s, 8f, 0.5f, 9.6f, 0.5f);
                Fx.tendrils(buf, ps, new Vec3(0, 0.1, 0), R * 0.9f, 14, t * 3f, 1.1f, cam, DVIO, 0.85f * f);
                Fx.disc(buf, ps, TARGET, new Vec3(0, 0.12, 0), R * 0.7f, t, CRIM, 0.5f * f);
            }
            if (s >= 10f) {
                float f = dur(s, 10f, 12f);
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), R * smooth(Math.min(1f, f * 1.6f)), SCAR, 1f - f);
                Fx.glow(buf, ps, new Vec3(0, 4, 0), R * 0.6f * (1f - f), WHITE, 0.55f * (1f - f));
                blackSun(buf, ps, new Vec3(0, 6, 0), R * 0.25f * smooth(Math.min(1f, f * 2f)), t, 1f - f, CRIM);
            }
        }
    }

    // ------------------------------------------------------------------ Abyssal Genesis

    public static class Genesis extends Base<AbyssalGenesisEntity> {
        public Genesis(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(AbyssalGenesisEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            int step = 5, start = 90;
            if (s < 8.6f) {
                float f = vis(s, 0f, 1f, 7.6f, 1f);
                Fx.disc(buf, ps, RUNE, new Vec3(0, 0.08, 0), R1 * 0.9f * smooth(s / 2f), t * 0.9f, CRIM, 0.7f * f);
                Fx.disc(buf, ps, CIRCLE, new Vec3(0, 0.1, 0), R1 * smooth(s / 2f), -t * 0.6f, DVIO, 0.5f * f);
            }
            for (int i = 0; i < 7; i++) {
                float fallAt = (start + i * step);
                if (t >= fallAt + 11f) {
                    continue;
                }
                double ang = i * Math.PI * 2 / 7 + 0.3;
                float rise = smooth((s - 1.5f) / 1.5f);
                float born = smooth(s / 1.5f);
                Vec3 pos = polar(ang, 14, 1.5 + 28.5 * rise);
                float r = 1.6f * born;
                if (t >= fallAt) {
                    float k = (t - fallAt) / 10f;
                    Vec3 target = null;
                    for (double[] d : e.fx()) {
                        if (d[0] == 1 && (int) d[2] == i) {
                            target = v(d[3], d[4], d[5]).subtract(base);
                        }
                    }
                    if (target == null) {
                        continue;
                    }
                    pos = pos.lerp(target.add(0, 1, 0), k * k);
                    r *= 1f + k * 0.8f;
                    Fx.beam(buf, ps, BEAM, pos, pos.lerp(polar(ang, 14, 30), 0.35), cam, r * 0.8f, 1f, SCAR, 0.7f);
                }
                blackSun(buf, ps, pos, r, t + i * 11, 1f, i % 2 == 0 ? CRIM : SCAR);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1] - 10f;
                if (d[0] != 1 || age < 0 || age > 20) {
                    continue;
                }
                Vec3 q = v(d[3], d[4], d[5]).subtract(base);
                float f = 1f - age / 20f;
                Fx.wave(buf, ps, q.add(0, 0.25, 0), 12f * smooth(Math.min(1f, age / 8f)), SCAR, f);
                Fx.crack(buf, ps, q.add(0, 0.12, 0), 8f, (float) d[2] * 50f, CRIM, f);
                Fx.glow(buf, ps, q.add(0, 2, 0), 7f * f, WHITE, f * 0.8f);
                Fx.beam(buf, ps, BEAM, q, q.add(0, 40, 0), cam, 2.5f * f, 1f, SCAR, 0.6f * f);
            }
            if (s >= 3f && s < 8.6f) {
                float f = vis(s, 3f, 1f, 7.6f, 1f);
                Random r = new Random(77);
                for (int i = 0; i < 70; i++) {
                    double a = r.nextDouble() * Math.PI * 2, el = 0.15 + r.nextDouble() * 1.2, d = 45 + r.nextDouble() * 10;
                    Vec3 p = new Vec3(Math.cos(a) * Math.cos(el) * d, Math.sin(el) * d, Math.sin(a) * Math.cos(el) * d);
                    float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.2 + i);
                    Fx.glow(buf, ps, p, 0.7f + (i % 4) * 0.4f, i % 3 == 0 ? SCAR : WHITE, 0.8f * f * tw);
                }
                Fx.shell(buf, ps, new Vec3(0, 0, 0), 55f, t * 0.003f, NIGHT, 0.4f * f);
            }
            if (s >= 7.0f && s < 9f) {
                float f = dur(s, 7.0f, 8.6f);
                float r = 12.5f * smooth(Math.min(1f, f * 1.8f));
                Vec3 c = new Vec3(0, 12, 0);
                blackSun(buf, ps, c, r, t, 1f - 0.5f * f, CRIM);
                Fx.orb(buf, ps, c, r * 1.3f, t * 0.04f, SCAR, 0.5f * (1f - f));
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), R1 * smooth(f), SCAR, 1f - f);
            }
            if (s >= 8.5f) {
                float f = dur(s, 8.5f, 11f);
                float r = R2 * smooth(Math.min(1f, f * 1.5f));
                Fx.wave(buf, ps, new Vec3(0, 0.3, 0), r, WHITE, 0.9f * (1f - f));
                Fx.wave(buf, ps, new Vec3(0, 0.5, 0), r * 0.85f, CRIM, 1f - f);
                Fx.glow(buf, ps, new Vec3(0, 6, 0), R2 * 0.5f * (1f - f), WHITE, 0.55f * (1f - f));
                Fx.orb(buf, ps, new Vec3(0, 6, 0), r * 0.7f, t * 0.03f, SCAR, 0.35f * (1f - f));
            }
        }
    }

    // ------------------------------------------------------------------ Temptation King Azazel

    public static class Azazel extends Base<TemptationKingAzazelEntity> {
        public Azazel(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(TemptationKingAzazelEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, D = e.r1(), F = e.r2(), yaw = e.yaw();
            Vec3 o = ownerP;
            float aura = vis(s, 0f, 1.5f, 19f, 1f);
            if (aura > 0.01f) {
                blackOrb(buf, ps, o.add(0, 1, 0), 2.2f * aura, 0.35f);
                float wing = smooth(s / 1.8f) * (s > 18f ? 1f - (s - 18f) / 2f : 1f);
                Fx.blob(buf, ps, WING, o.add(-3.2 * Math.cos(Math.toRadians(yaw)), 2.6, -3.2 * Math.sin(Math.toRadians(yaw))), 4.6f * wing, 0f, WHITE, 0.95f * aura);
                Fx.blob(buf, ps, WING_L, o.add(3.2 * Math.cos(Math.toRadians(yaw)), 2.6, 3.2 * Math.sin(Math.toRadians(yaw))), 4.6f * wing, 0f, WHITE, 0.95f * aura);
                Fx.glow(buf, ps, o.add(0, 1, 0), 5f, CRIM, 0.35f * aura);
                Fx.glowTex(buf, ps, EYE, o.add(0, 1.65, 0), 0.6f, 0f, GOLD, 0.9f * aura);
                Fx.disc(buf, ps, RUNE, o.add(0, 0.09, 0), 8f * aura, t * 1.5f, CRIM, 0.8f * aura);
            }
            if (s >= 2f && s < 18.5f) {
                float f = vis(s, 2f, 2f, 17.5f, 1f);
                float open = smooth((s - 2f) / 2f);
                Fx.shell(buf, ps, Vec3.ZERO, 0.45f * D * open, t * 0.006f, DEEP, 0.28f * f);
                Fx.disc(buf, ps, RUNE, new Vec3(0, 0.08, 0), D * 0.6f * open, t * 0.5f, CRIM, 0.6f * f);
                Fx.disc(buf, ps, CIRCLE, new Vec3(0, 0.1, 0), D * 0.9f * open, -t * 0.3f, DVIO, 0.4f * f);
                Vec3 th = o.add(-4 * Math.sin(Math.toRadians(yaw)) * -1, 0, 4 * Math.cos(Math.toRadians(yaw)) * -1);
                Fx.strip(buf, ps, BLACK, false, th, th.add(0, 9, 0), cam, 2.4f, 1.2f, WHITE, 0.9f * f * smooth((s - 2.5f) / 1.5f));
            }
            if (s >= 4f && s < 16.2f) {
                float grow = smooth((s - 4f) / 2f);
                float shrink = s >= 13f ? 1f - 0.8f * smooth((s - 13f) / 3f) : 1f;
                float r = 15f * grow * shrink;
                Vec3 c = new Vec3(0, 48, 0);
                blackSun(buf, ps, c, r, t, 1f, SCAR);
                if (s >= 6f && s < 8f) {
                    for (int i = 0; i < 10; i++) {
                        double ang = i * Math.PI / 5 + t * 0.02;
                        Fx.bolt(buf, ps, c, polar(ang, 40, 0), cam, 0.7f, BLOODL, 0.9f * vis(s, 6f, 0.1f, 7.6f, 0.4f), (long) (t / 2) * 31 + i);
                    }
                    Fx.wave(buf, ps, new Vec3(0, 0.3, 0), D * 0.64f * smooth((s - 6f) / 1f), SCAR, 1f - (s - 6f) / 2f);
                }
            }
            if (s >= 8f && s < 11f) {
                float f = vis(s, 8f, 0.6f, 10.2f, 0.8f);
                Random r = new Random(e.getId());
                int shown = (int) Math.min(14, (s - 8f) * 6);
                for (int i = 0; i < shown; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 12 + r.nextDouble() * 32;
                    Vec3 q = polar(a, d, 0.1);
                    upright(buf, ps, RIFT, q.add(0, 9, 0), yaw + i * 41f, 9f, 0f, i % 2 == 0 ? CRIM : SCAR, 0.85f * f);
                    Fx.crack(buf, ps, q, 6f, i * 30f, CRIM, 0.8f * f);
                }
                if (s >= 10f) {
                    Fx.wave(buf, ps, new Vec3(0, 0.3, 0), 45f * smooth(Math.min(1f, (s - 10f) / 0.8f)), CRIM, 1f - (s - 10f));
                }
            }
            if (s >= 10.5f && s < 13.5f) {
                float f = vis(s, 10.5f, 0.6f, 12.8f, 0.7f);
                Fx.disc(buf, ps, VORTEX, new Vec3(0, 0.14, 0), D * smooth((s - 10.5f) / 1f), t * 3f, DVIO, 0.55f * f);
                Random r = new Random(5);
                for (int i = 0; i < 24; i++) {
                    double a = r.nextDouble() * Math.PI * 2 + t * 0.03;
                    double d = D * (1.0 - ((t * 0.02 + r.nextDouble()) % 1.0));
                    Fx.glowTex(buf, ps, SOULWISP, polar(a, d, 0.8 + (i % 5)), 1.4f, 0f, WHITE, 0.8f * f);
                }
            }
            if (s >= 13f && s < 17f) {
                float f = dur(s, 13f, 16.5f);
                blackOrb(buf, ps, o.add(0, 1.2, 0), 1f + 3.5f * f, 0.6f * vis(s, 13f, 0.5f, 16.3f, 0.2f));
                Fx.shell(buf, ps, Vec3.ZERO, D * 0.5f * (1f - 0.4f * f), t * 0.01f, CRIM, 0.3f * vis(s, 13f, 0.5f, 16.3f, 0.2f));
            }
            if (s >= 16f && s < 20f) {
                float f = dur(s, 16f, 18f);
                if (s < 17f) {
                    Fx.glow(buf, ps, o.add(0, 1.2, 0), 6f * (1f - (s - 16f)), WHITE, 0.9f);
                } else {
                    float k = dur(s, 17f, 18.5f);
                    float r = F * smooth(Math.min(1f, k * 1.2f));
                    Fx.orb(buf, ps, o.add(0, 2, 0), r * 0.6f, t * 0.05f, WHITE, 0.55f * (1f - k));
                    Fx.orb(buf, ps, o.add(0, 2, 0), r * 0.8f, -t * 0.03f, CRIM, 0.4f * (1f - k));
                    Fx.wave(buf, ps, new Vec3(0, 0.3, 0), r, WHITE, 1f - k);
                    Fx.wave(buf, ps, new Vec3(0, 0.6, 0), r * 0.9f, SCAR, 0.9f * (1f - k));
                    Fx.glow(buf, ps, o.add(0, 3, 0), F * 0.35f * (1f - k), WHITE, 0.7f * (1f - k));
                }
            }
        }
    }
}
