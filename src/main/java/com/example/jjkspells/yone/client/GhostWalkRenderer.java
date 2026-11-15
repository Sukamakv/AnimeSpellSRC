package com.example.jjkspells.yone.client;

import com.example.jjkspells.yone.entity.GhostWalkEntity;
import com.example.jjkspells.yone.spell.GhostWalkSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws "Ghost Walk": the spirit is a red flat silhouette of Yone in a running lunge with fading afterimages stepping off its trail; a wavy blue and red soul
 * thread ties it to the body, which carries a rotating ring under its feet. The spirit leaves a ribbon of wind with wisps, cuts show crossed slashes and rings,
 * and marked enemies wear a rotating mark with a chain back to the body. On the snap the chains yank tight, every marked enemy is ripped in crossed cuts and a
 * blue-and-red shock ring, pillar and X burst out of the body.
 */
public class GhostWalkRenderer extends EntityRenderer<GhostWalkEntity> {
    private static final int GO = GhostWalkEntity.GO;
    private static final int SNAP = GhostWalkEntity.SNAP;
    private static final int END = GhostWalkEntity.END;
    private static final double SNAP_R = GhostWalkSpell.SNAP_RADIUS;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 O;
    private PoseStack ps;
    private MultiBufferSource buf;
    private GhostWalkEntity ent;
    private Entity own;
    private double env;

    public GhostWalkRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(GhostWalkEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GhostWalkEntity e) {
        return YoneRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(ResourceLocation tex) {
        p.vc = buf.getBuffer(YoneRender.additive(tex));
    }

    private static float[] body(int colour) {
        return colour == 0 ? Pen.BLUE : Pen.RED;
    }

    private static float[] edge(int colour) {
        return colour == 0 ? Pen.ICE : Pen.ROSE;
    }

    private void line(Vec3 a, Vec3 b, double w, float al, int colour) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, body(colour));
        p.ribbon(a, b, w, al, 0f, 1f, edge(colour));
    }

    private Vec3 camRight() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f l = c.getLeftVector();
        return new Vec3(-l.x(), -l.y(), -l.z());
    }

    private Vec3 markPos(int id) {
        Entity e = ent.level().getEntity(id);
        return e == null ? null : e.getPosition(1.0f).add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Facing of the spirit: where it is heading (out) or where it is flying back to (in). */
    private Vec3 heading() {
        List<Vec3> tr = ent.trail();
        if (t > GO) {
            Vec3 to = O.subtract(A);
            to = new Vec3(to.x, 0, to.z);
            return to.lengthSqr() < 1.0E-4 ? Z : to.normalize();
        }
        if (tr.size() >= 4) {
            Vec3 d = A.subtract(tr.get(tr.size() - 4));
            d = new Vec3(d.x, 0, d.z);
            if (d.lengthSqr() > 1.0E-4) {
                return d.normalize();
            }
        }
        return Z;
    }

    @Override
    public void render(GhostWalkEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        ps = ps0;
        buf = buf0;
        own = e.owner();
        A = e.getPosition(pt);
        O = e.origin();
        env = ease(t / 4.0) * (1.0 - c01((t - SNAP - 8) / 16.0));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        ghosts();
        add(YoneRender.WIND);
        trailRibbon();
        add(YoneRender.MIST);
        mist();
        add(YoneRender.RING);
        rings();
        add(YoneRender.LINE);
        lines();
        add(YoneRender.GLINT);
        glints();
    }

    // ------------------------------------------------------------------ passes

    private void ghosts() {
        if (own == null) {
            return;
        }
        boolean slim = PlayerGhost.slimOf(own);
        if (t < SNAP) {
            Vec3 h = heading();
            double yawDeg = PlayerGhost.yawOf(h);
            float stride = ((int) (t / 3)) % 2 == 0 ? 1f : -1f;
            PlayerGhost.Pose pose = PlayerGhost.Pose.lunge(stride);
            ghost.silhouette(ps, buf, slim, A.subtract(A), yawDeg, pose, Pen.RED, (float) (0.85 * env));
            List<Vec3> tr = ent.trail();
            for (int k = 1; k <= 3; k++) {
                int idx = tr.size() - 1 - 4 * k;
                if (idx < 0) {
                    break;
                }
                PlayerGhost.Pose pk = PlayerGhost.Pose.lunge(((int) ((t - 4 * k) / 3)) % 2 == 0 ? 1f : -1f);
                ghost.silhouette(ps, buf, slim, tr.get(idx).subtract(A), yawDeg, pk, k % 2 == 0 ? Pen.CRIMSON : Pen.ROSE, (float) (0.5 * env / k));
            }
        }
        // body-side echo as the spirit leaves and as it merges back
        if (t < 5) {
            ghost.silhouette(ps, buf, slim, O.subtract(A), PlayerGhost.yawOf(heading()), PlayerGhost.Pose.lunge(1f), Pen.RED, (float) (0.6 * (1.0 - t / 5.0)));
        }
        double fa = t - SNAP;
        if (fa >= -3 && fa < 4) {
            ghost.silhouette(ps, buf, slim, O.subtract(A), PlayerGhost.yawOf(heading()), PlayerGhost.Pose.finish(), Pen.ROSE, (float) (0.6 * c01(1.0 - Math.abs(fa) / 4.0)));
        }
    }

    private void trailRibbon() {
        List<Vec3> tr = ent.trail();
        int n = tr.size();
        for (int i = 1; i < n; i++) {
            double f = (double) i / n;
            float al = (float) (0.45 * f * env * (t > SNAP ? 0.0 : 1.0));
            Vec3 a = tr.get(i - 1).add(0, 0.3, 0);
            Vec3 b = tr.get(i).add(0, 0.3, 0);
            p.ribbon(a, b, 0.9 * f, 1.2 * f, al * 0.6f, al, 0f, 1f, i % 2 == 0 ? Pen.RED : Pen.BLUE);
        }
    }

    private void mist() {
        List<Vec3> tr = ent.trail();
        for (int i = 4; i < tr.size(); i += 6) {
            double f = (double) i / tr.size();
            p.billboard(tr.get(i).add(0, 0.7, 0), 1.2, i * 0.3, i % 12 == 4 ? Pen.ROSE : Pen.ICE, (float) (0.2 * f * env * (t > SNAP ? 0.0 : 1.0)));
        }
        double fa = t - SNAP;
        if (fa >= 0 && fa < 16) {
            p.billboard(O.add(0, 1.0, 0), 3.0 + 6.0 * ease(fa / 8.0), fa * 0.1, Pen.PALE, (float) (0.3 * (1.0 - fa / 16.0)));
        }
    }

    private void rings() {
        // body ring
        if (t < SNAP + 6) {
            double e2 = ease(t / 4.0);
            p.plane(O.add(0, 0.12, 0), X, Z, 1.5 * e2, t * 0.1, Pen.BLUE, (float) (0.7 * e2));
            p.plane(O.add(0, 0.14, 0), X, Z, 1.0 * e2, -t * 0.15, Pen.RED, (float) (0.6 * e2));
        }
        // cut pulses at the spirit
        if (t < SNAP) {
            int phase = ent.tickCount % GhostWalkSpell.CUT_INTERVAL;
            double age = phase + (t - (ent.tickCount - 1));
            if (age < 3.5) {
                double k = ease(age / 2.0);
                p.plane(A.add(0, 0.2, 0), X, Z, 0.6 + GhostWalkSpell.CUT_RADIUS * k, 0, Pen.RED, (float) (0.7 * (1.0 - age / 3.5)));
            }
        }
        // mark rings
        if (t < SNAP + 2) {
            for (int id : ent.markedIds()) {
                Entity e = ent.level().getEntity(id);
                if (e == null) {
                    continue;
                }
                Vec3 m = e.getPosition(1.0f).add(0, e.getBbHeight() + 0.8, 0);
                p.plane(m, X, Z, 0.7, t * 0.15, Pen.RED, (float) (0.9 * env));
                p.plane(m.add(0, 0.2, 0), X, Z, 0.45, -t * 0.2, Pen.WHITE, (float) (0.6 * env));
            }
        }
        // snap shock
        double fa = t - SNAP;
        if (fa >= 0 && fa < 18) {
            double k = ease(fa / 10.0);
            float al = (float) (1.0 - fa / 18.0);
            p.plane(O.add(0, 0.25, 0), X, Z, 1.0 + (SNAP_R + 3.0) * k, 0, Pen.BLUE, al * 0.85f);
            p.plane(O.add(0, 0.3, 0), X, Z, 1.0 + (SNAP_R + 1.0) * k, fa * 0.25, Pen.RED, al * 0.85f);
            p.plane(O.add(0, 4.0 * k, 0), X, Z, 1.0 + 5.0 * k, 0, Pen.PALE, al * 0.5f);
            p.plane(O.add(0, 1.3, 0), X, Y, 0.8 + 4.0 * k, 0, Pen.WHITE, al * 0.5f);
        }
    }

    private void lines() {
        double fa = t - SNAP;
        Vec3 hi = O.add(0, 1.4, 0);
        // soul thread between the body and the spirit
        if (t < SNAP) {
            Vec3 s = A.add(0, 1.2, 0);
            int n = 18;
            for (int col = 0; col < 2; col++) {
                Vec3 prev = hi;
                for (int i = 1; i <= n; i++) {
                    double f = (double) i / n;
                    double amp = 0.4 * Math.sin(Math.PI * f);
                    double ph = t * 0.5 + f * 9.0 + col * Math.PI;
                    Vec3 base = hi.add(s.subtract(hi).scale(f));
                    Vec3 cur = base.add(0, amp * Math.sin(ph), 0).add(camRight().scale(amp * Math.cos(ph)));
                    line(prev, cur, 0.035, (float) (0.7 * env), col);
                    prev = cur;
                }
            }
        }
        // wisps rising from the trail
        List<Vec3> tr = ent.trail();
        for (int i = 3; i < tr.size(); i += 4) {
            double f = (double) i / tr.size();
            Vec3 b = tr.get(i);
            double h = 1.0 + 0.8 * Math.sin(t * 0.3 + i);
            line(b.add(0, 0.2, 0), b.add(0, 0.2 + h, 0), 0.04, (float) (0.5 * f * env * (t > SNAP ? 0.0 : 1.0)), i % 8 == 3 ? 1 : 0);
        }
        // cut X at the spirit
        if (t < SNAP) {
            int phase = ent.tickCount % GhostWalkSpell.CUT_INTERVAL;
            double age = phase + (t - (ent.tickCount - 1));
            if (age < 3.5) {
                double k = ease(age / 1.5);
                float al = (float) (1.0 - age / 3.5);
                Vec3 c = A.add(0, 1.2, 0);
                for (int i = 0; i < 2; i++) {
                    double an = i == 0 ? 0.7 : -0.7;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(2.6 * k);
                    line(c.subtract(v), c.add(v), 0.1, al, i);
                }
            }
        }
        // chains from marked enemies to the body
        for (int id : ent.markedIds()) {
            Vec3 m = markPos(id);
            if (m == null) {
                continue;
            }
            if (t < SNAP) {
                float al = (float) (0.35 * env * ease((t - GO * 0.3) / 10.0));
                int n = 10;
                Vec3 prev = m;
                for (int i = 1; i <= n; i++) {
                    double f = (double) i / n;
                    Vec3 cur = m.add(hi.subtract(m).scale(f)).add(0, 0.2 * Math.sin(t * 0.4 + f * 8), 0);
                    if (i % 2 == 1) {
                        line(prev, cur, 0.03, al, 1);
                    }
                    prev = cur;
                }
            } else if (fa >= 0 && fa < 14) {
                double k = ease(fa / 3.0);
                float al = (float) (1.0 - fa / 14.0);
                line(m, m.add(hi.subtract(m).scale(0.4 + 0.6 * k)), 0.1, al, 1);
                for (int i = 0; i < 3; i++) {
                    double an = 0.5 + i * 1.05 + id * 0.4;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(3.6 * k);
                    line(m.subtract(v), m.add(v), 0.1, al, i % 2);
                }
            }
        }
        // snap burst at the body
        if (fa >= 0 && fa < 16) {
            double k = ease(fa / 4.0);
            float al = (float) (1.0 - fa / 16.0);
            line(O, O.add(0, 12.0 * k, 0), 0.3 * (1.0 - fa / 20.0), al * 0.9f, 0);
            line(O.add(0.4, 0, 0), O.add(0.4, 9.0 * k, 0), 0.15, al * 0.8f, 1);
            for (int i = 0; i < 2; i++) {
                double an = i == 0 ? 0.7 : -0.7;
                Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(4.5 * k);
                line(hi.subtract(v), hi.add(v), 0.12, al, i);
            }
            for (int i = 0; i < 14; i++) {
                double an = i * Math.PI * 2 / 14;
                Vec3 r = new Vec3(Math.cos(an), 0, Math.sin(an));
                line(O.add(r.scale(1.5 + 3.0 * k)).add(0, 0.4, 0), O.add(r.scale(3.0 + 6.0 * k)).add(0, 0.4 + 0.6 * k, 0), 0.05, al * 0.8f, i % 2);
            }
        }
        // body leave burst
        if (t < 8) {
            float al = (float) (1.0 - t / 8.0);
            Vec3 h = heading();
            for (int i = 0; i < 8; i++) {
                double side = (i - 3.5) * 0.35;
                Vec3 s = new Vec3(h.z, 0, -h.x);
                Vec3 st = O.add(0, 0.8 + 0.2 * i % 3, 0).add(s.scale(side));
                line(st, st.add(h.scale(2.0 + 4.0 * ease(t / 4.0))), 0.05, al * 0.8f, i % 2);
            }
        }
    }

    private void glints() {
        double fa = t - SNAP;
        if (t < SNAP) {
            p.billboard(A.add(0, 1.3, 0), 0.5 + 0.2 * Math.sin(t), t * 0.2, Pen.ROSE, (float) (0.7 * env));
            List<Vec3> tr = ent.trail();
            for (int i = 0; i < tr.size(); i += 3) {
                float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.5 + i * 2.1));
                p.billboard(tr.get(i).add(0, 0.5 + 0.6 * ((i * 0.37) % 1.0), 0), 0.14, t * 0.1 + i, Pen.WHITE, (float) (0.6 * env * tw * i / Math.max(1, tr.size())));
            }
        }
        if (fa >= 0 && fa < 10) {
            p.billboard(O.add(0, 1.3, 0), 2.0 + 3.0 * ease(fa / 4.0), fa * 0.2, Pen.WHITE, (float) (1.0 - fa / 10.0));
            for (int id : ent.markedIds()) {
                Vec3 m = markPos(id);
                if (m != null) {
                    p.billboard(m, 1.2 + 1.2 * ease(fa / 3.0), fa * 0.2, Pen.ROSE, (float) (1.0 - fa / 10.0));
                }
            }
        }
    }
}
