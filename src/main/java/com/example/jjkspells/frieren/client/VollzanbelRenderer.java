package com.example.jjkspells.frieren.client;

import com.example.jjkspells.frieren.entity.FlammeMagicEntity;
import com.example.jjkspells.frieren.entity.VollzanbelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.example.jjkspells.frieren.spell.FrierenVollzanbelSpell.*;

/**
 * Vollzanbel. Palette: deep red, orange, yellow, white-hot cores; soot and smoke are alpha-blended matter, not additive.
 *
 *  alpha:    screen tint/flash (camera-centred shell), ground scorch, smoke, debris chunks
 *  additive: front circle (3 layers), flame puffs (stateless sprite stream that follows the aim with a history), core ribbon,
 *            ball + explosion shells, light columns, ground fire pool + ground glow, shockwaves, burst flip-book, embers
 * The "environment lit up" effect is glow on the terrain + a client-only tint around the camera. No weather, world or light-engine changes.
 */
public class VollzanbelRenderer extends EntityRenderer<VollzanbelEntity> {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float PERIOD = 26f;   // ticks a flame puff needs to travel from the circle to the tip
    private static final float EXPL_R = (float) EXPLOSION_RADIUS;

    public VollzanbelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override public ResourceLocation getTextureLocation(VollzanbelEntity e) { return VolRenderTypes.EMBER; }
    @Override public boolean shouldRender(VollzanbelEntity e, Frustum f, double x, double y, double z) { return true; }

    // ---- per-frame state ----
    private Level level;
    private Vec3 ePos;
    private float age, te, conv, act, flick, L;
    private VollzanbelFx fx;
    private float[] cam, q;
    private double[] cur;
    private boolean flames;

    @Override
    public void render(VollzanbelEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Minecraft mc = Minecraft.getInstance();
        level = e.level();
        age = e.tickCount + pt;
        fx = VollzanbelFx.of(e);
        ePos = e.getPosition(pt);
        int phase = e.clientPhase;
        Camera c = mc.gameRenderer.getMainCamera();
        Vec3 cp = c.getPosition().subtract(ePos);
        cam = new float[]{(float) cp.x, (float) cp.y, (float) cp.z};
        Quaternionf rot = c.rotation();
        Vector3f right = new Vector3f(1f, 0f, 0f).rotate(rot), up = new Vector3f(0f, 1f, 0f).rotate(rot);

        te = phase >= 2 ? (fx.tExp >= 0 ? age - fx.tExp : 999f) : -1f;
        conv = phase >= 1 ? (fx.tConv >= 0 ? VolPen.smooth((age - fx.tConv) / CONVERGE) : 1f) : 0f;
        act = VolPen.smooth((age - START) / 6f) * (te >= 0f ? 0f : 1f - 0.75f * conv);
        flick = 0.9f + 0.1f * Mth.sin(age * 0.9f) * Mth.cos(age * 0.37f);
        q = e.clientPoint != null ? new float[]{(float) (e.clientPoint[0] - ePos.x), (float) (e.clientPoint[1] - ePos.y), (float) (e.clientPoint[2] - ePos.z)} : null;
        flames = fx.hasHistory();
        Entity owner = level.getEntity(e.getOwnerId());
        if (flames) {
            if (phase == 0 && owner != null) {
                Vec3 look = owner.getViewVector(pt);
                Vec3 o = VollzanbelEntity.origin(owner.getEyePosition(pt), look);
                cur = new double[]{o.x, o.y, o.z, look.x, look.y, look.z};
            } else {
                cur = fx.get(0);
            }
        }
        float grow = VolPen.smooth((age - START) / 9f);
        L = Math.min((float) RANGE, fx.reach + (fx.hitSurface ? 3f : 0f)) * Math.max(grow, 0.02f);

        // ================================================================= ALPHA-BLENDED (matter)
        tint(new VolPen(buf.getBuffer(VolRenderTypes.alpha(VolRenderTypes.FLAT)), ps, right, up), phase);
        if (te >= 0f && q != null && te < 400f) {
            scorch(new VolPen(buf.getBuffer(VolRenderTypes.alpha(VolRenderTypes.SCORCH)), ps, right, up));
        }
        if (flames || q != null) smoke(new VolPen(buf.getBuffer(VolRenderTypes.alpha(VolRenderTypes.SMOKE)), ps, right, up));
        if (!fx.debris.isEmpty()) debris(new VolPen(buf.getBuffer(VolRenderTypes.alpha(VolRenderTypes.DEBRIS)), ps, right, up));

        // ================================================================= ADDITIVE (light)
        float ca = VolPen.smooth(age / 18f) * (phase >= 1 ? 1f - VolPen.smooth((age - Math.max(fx.tConv, 0)) / 10f) : 1f);
        if (flames && ca > 0.01f) {
            circle(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FIRE_CIRCLE_A)), ps, right, up), 0, ca);
            circle(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FIRE_CIRCLE_B)), ps, right, up), 1, ca);
            circle(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FIRE_CIRCLE_C)), ps, right, up), 2, ca);
        }
        puffs(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FLAME_PUFF)), ps, right, up), e, ca);
        if (flames && act > 0.01f) beam(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FLAME_BEAM)), ps, right, up));
        shells(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FIRE_SWIRL)), ps, right, up));
        if (te >= 0f && q != null) columns(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FIRE_STREAKS)), ps, right, up));
        if (te >= 0f && q != null) shocks(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FIRE_SHOCK)), ps, right, up));
        pools(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.GROUND_FIRE)), ps, right, up));
        if (te >= 0f && te < 26f && q != null) {
            VolPen bp = new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.FIRE_BURST)), ps, right, up);
            bp.flipbook(q[0], q[1] + 4f, q[2], 58f, 0.4f, (int) (te / 26f * 16f), 1f);
        }
        glows(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.GLOW)), ps, right, up));
        if (!fx.embers.isEmpty()) embers(new VolPen(buf.getBuffer(VolRenderTypes.additive(VolRenderTypes.EMBER)), ps, right, up));
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static float h(int i, int salt) {
        double s = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
        return (float) (s - Math.floor(s));
    }

    private static float frac(float x) { return x - (float) Math.floor(x); }

    /** Flame state lag ticks ago: lag 0 = the caster right now (smooth), then the recorded history. */
    private double[] sample(float lag) {
        int i = (int) Math.floor(lag);
        float f = lag - i;
        double[] a = i <= 0 ? cur : fx.get(i - 1);
        double[] b = fx.get(Math.max(i, 0));
        double[] r = new double[6];
        for (int k = 0; k < 6; k++) r[k] = a[k] + (b[k] - a[k]) * f;
        return r;
    }

    private float[] at(double[] H, float d, float rise) {
        return new float[]{(float) (H[0] - ePos.x + H[3] * d), (float) (H[1] - ePos.y + H[4] * d) + rise, (float) (H[2] - ePos.z + H[5] * d)};
    }

    /** Two unit vectors perpendicular to (dx,dy,dz). */
    private static float[][] basis(double dx, double dy, double dz) {
        float p1x = (float) -dz, p1y = 0f, p1z = (float) dx;
        float l = Mth.sqrt(p1x * p1x + p1z * p1z);
        if (l < 0.05f) { p1x = 1f; p1z = 0f; l = 1f; }
        p1x /= l;
        p1z /= l;
        float p2x = (float) (dy * p1z - dz * p1y), p2y = (float) (dz * p1x - dx * p1z), p2z = (float) (dx * p1y - dy * p1x);
        return new float[][]{{p1x, p1y, p1z}, {p2x, p2y, p2z}};
    }

    private static float[] lerp3(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    // ------------------------------------------------------------------------------------------ alpha pass

    /** Camera-centred translucent shell: warm haze while the inferno burns near you, hot flash at the explosion. Client only. */
    private void tint(VolPen pen, int phase) {
        float[] focus = null;
        if (phase >= 1 && q != null) focus = q;
        else if (flames) focus = new float[]{(float) (cur[0] - ePos.x), (float) (cur[1] - ePos.y), (float) (cur[2] - ePos.z)};
        if (focus == null) return;
        float dx = cam[0] - focus[0], dy = cam[1] - focus[1], dz = cam[2] - focus[2];
        float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float prox = 1f - VolPen.smooth((dist - 22f) / 55f);
        float haze = te < 0f ? VolPen.smooth((age - (START - 10f)) / 30f) : 1f - VolPen.smooth((te - 40f) / 130f);
        float a = 0.15f * haze * prox;
        if (a > 0.004f) pen.shellFlat(cam[0], cam[1], cam[2], 2.6f, 8, 16, 0.55f, 0.06f, 0.02f, a);
        if (te >= 0f && te < 10f) {
            float fp = 1f - VolPen.smooth((dist - 40f) / 70f);
            pen.shellFlat(cam[0], cam[1], cam[2], 2.5f, 8, 16, 1f, 0.78f, 0.45f, 0.6f * (1f - te / 10f) * fp);
        }
    }

    private void scorch(VolPen pen) {
        float a = 0.8f * VolPen.smooth(te / 6f) * (1f - VolPen.smooth((te - 90f) / 80f));
        if (a > 0.01f) groundDisc(pen, q[0], q[2], groundBase(), 34f, 20, 0.7f, 0.03f, 1f, 1f, 1f, a);
    }

    private void smoke(VolPen pen) {
        // soot along the flame: dark billows rising off its edges
        if (flames && act > 0.01f) {
            for (int i = 0; i < 46; i++) {
                float s = frac(age / 70f + h(i, 1));
                float pos = h(i, 2);
                double[] H = sample(pos * PERIOD * 0.85f);
                float d = pos * L;
                float R = (float) (CONE_BASE + CONE_SPREAD * d);
                float phi = h(i, 3) * TAU;
                float[][] b = basis(H[3], H[4], H[5]);
                float[] p = at(H, d, s * 8f);
                float rr = R * 1.05f;
                float x = p[0] + (b[0][0] * Mth.cos(phi) + b[1][0] * Mth.sin(phi)) * rr;
                float y = p[1] + (b[0][1] * Mth.cos(phi) + b[1][1] * Mth.sin(phi)) * rr;
                float z = p[2] + (b[0][2] * Mth.cos(phi) + b[1][2] * Mth.sin(phi)) * rr;
                float size = 3f + 7f * s;
                pen.billboard(x, y, z, size, size, h(i, 4) * TAU + age * 0.01f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, (float) Math.sin(s * Math.PI) * 0.38f * act);
            }
        }
        // the explosion's rising plume
        if (te >= 0f && q != null) {
            for (int i = 0; i < 70; i++) {
                float ts = te - h(i, 11) * 30f;
                if (ts < 0f || ts > 120f) continue;
                float p = ts / 120f, ease = 1f - (1f - p) * (1f - p);
                float phi = h(i, 12) * TAU, rr = (3f + 26f * p) * h(i, 13);
                float size = 9f + 16f * p;
                pen.billboard(q[0] + Mth.cos(phi) * rr, q[1] + 3f + 58f * ease * (0.4f + 0.6f * h(i, 14)), q[2] + Mth.sin(phi) * rr, size, size,
                        h(i, 15) * TAU + age * 0.004f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, (float) Math.sin(p * Math.PI) * 0.6f);
            }
        }
    }

    private void debris(VolPen pen) {
        float frac = age - (float) Math.floor(age);
        for (VollzanbelFx.Debris d : fx.debris) {
            float t = age - d.born;
            float a = 1f - VolPen.smooth((t - d.life * 0.7f) / (d.life * 0.3f + 0.01f));
            if (a <= 0.01f) continue;
            float u0 = (d.cell % 4) * 0.25f, v0 = (d.cell / 4) * 0.25f;
            float x = (float) (d.x - ePos.x) + (d.stuck ? 0f : d.vx * frac), y = (float) (d.y - ePos.y) + (d.stuck ? 0f : d.vy * frac), z = (float) (d.z - ePos.z) + (d.stuck ? 0f : d.vz * frac);
            pen.billboard(x, y, z, d.size, d.size, d.rot + d.spin * frac, u0, v0, u0 + 0.25f, v0 + 0.25f, 1f, 1f, 1f, a);
        }
    }

    // ------------------------------------------------------------------------------------------ additive passes

    private void circle(VolPen pen, int tex, float ca) {
        double[] H = cur;
        float[] c = at(H, 0f, 0f);
        float[][] b = basis(H[3], H[4], H[5]);
        float sc = 0.45f + 0.55f * VolPen.smooth(age / 18f);
        float a = ca * flick;
        if (tex == 0) pen.disc(c[0], c[1], c[2], b[0], b[1], 3.5f * sc, age * 0.03f, 0.95f * a);
        if (tex == 1) pen.disc(c[0], c[1], c[2], b[0], b[1], 2.3f * sc, -age * 0.055f, 0.9f * a);
        if (tex == 2) pen.disc(c[0], c[1], c[2], b[0], b[1], 5.0f * sc, age * 0.022f, 0.9f * a);
    }

    private void puffs(VolPen pen, VollzanbelEntity e, float ca) {
        // 1-3. the small flame that forms in the middle of the circle
        if (flames && age < START + 12f) {
            double[] H = cur;
            float[] c = at(H, 0.3f, 0f);
            float size = 2.4f * VolPen.smooth((age - 10f) / 26f) * (1f - VolPen.smooth((age - START - 2f) / 10f));
            if (size > 0.02f) pen.flipbookTint(c[0], c[1] + size * 0.25f, c[2], size, 0f, (int) (age * 0.8f) % 16, 1f, 1f, 1f, 0.95f * ca);
        }
        // 4. the torrent: a stateless sprite stream, each puff travels circle -> tip along the recorded flame path
        if (flames && act > 0.01f) {
            float tf = Math.min(age, (float) FLAME_END);
            for (int i = 0; i < 190; i++) {
                float s = frac(tf / PERIOD + h(i, 1));
                double[] H = sample(s * PERIOD * 0.85f);
                float d = s * L;
                float R = (float) (CONE_BASE + CONE_SPREAD * d);
                boolean core = h(i, 4) < 0.3f;
                float phi = h(i, 2) * TAU + age * 0.08f * (h(i, 5) - 0.5f);
                float rr = Mth.sqrt(h(i, 3)) * R * (core ? 0.3f : 0.85f);
                float[][] b = basis(H[3], H[4], H[5]);
                float[] p = at(H, d, s * s * 2.5f);
                p[0] += (b[0][0] * Mth.cos(phi) + b[1][0] * Mth.sin(phi)) * rr;
                p[1] += (b[0][1] * Mth.cos(phi) + b[1][1] * Mth.sin(phi)) * rr;
                p[2] += (b[0][2] * Mth.cos(phi) + b[1][2] * Mth.sin(phi)) * rr;
                if (q != null && conv > 0f) p = lerp3(p, q, conv * s);
                float size = (0.9f + 0.55f * R) * (0.7f + 0.6f * h(i, 6)) * (core ? 0.6f : 1f) * (1f - 0.55f * conv);
                float a = VolPen.smooth(s / 0.06f) * (1f - VolPen.smooth((s - 0.55f) / 0.45f)) * 0.5f * act * (core ? 1.3f : 1f);
                pen.flipbookTint(p[0], p[1], p[2], size, h(i, 7) * 6f + age * 0.03f * (h(i, 3) - 0.5f), (int) (age * 0.7f + h(i, 5) * 16f) % 16,
                        1f, 1f - 0.45f * s, 0.85f - 0.6f * s, a);
            }
        }
        // burning targets: tongues of fire licking up their bodies
        for (int id : e.clientBurning) {
            Entity t = level.getEntity(id);
            if (t == null) continue;
            Vec3 tp = t.position();
            float tx = (float) (tp.x - ePos.x), ty = (float) (tp.y - ePos.y), tz = (float) (tp.z - ePos.z);
            float bh = t.getBbHeight(), bw = t.getBbWidth();
            for (int j = 0; j < 5; j++) {
                float s = frac(age / 16f + h(id * 7 + j, 1));
                float x = tx + (h(id * 7 + j, 2) - 0.5f) * bw * 1.2f, z = tz + (h(id * 7 + j, 3) - 0.5f) * bw * 1.2f;
                float size = 0.6f + bw * 0.7f;
                pen.flipbookTint(x, ty + s * bh * 1.1f + size * 0.3f, z, size, 0f, (int) (age * 0.7f + j * 3) % 16, 1f, 1f - 0.3f * s, 0.8f - 0.5f * s, 0.75f * (1f - s * 0.7f));
            }
        }
    }

    /** The bright core of the stream: a polyline ribbon that bends with the recorded aim. */
    private void beam(VolPen pen) {
        int n = 14;
        float[][] pts = new float[n][];
        float[] w = new float[n], v = new float[n], al = new float[n];
        for (int j = 0; j < n; j++) {
            float s = j / (float) (n - 1);
            double[] H = sample(s * PERIOD * 0.6f);
            float d = s * L * 0.92f;
            float[] p = at(H, d, s * s * 1.2f);
            if (q != null && conv > 0f) p = lerp3(p, q, conv * s);
            pts[j] = p;
            w[j] = (0.8f + 0.11f * d) * (1f - 0.5f * conv);
            v[j] = d * 0.06f - age * 0.35f;
            al[j] = 0.85f * (1f - 0.6f * s) * act * flick;
        }
        pen.strip(pts, w, v, al, cam, 1f, 0.95f, 0.85f);
    }

    /** Fire ball while converging; two expanding fire shells at the explosion. */
    private void shells(VolPen pen) {
        if (q != null && te < 0f && conv > 0f) {
            float rb = 0.5f + 6f * (float) Math.pow(conv, 1.6);
            pen.sphereShell(q[0], q[1], q[2], rb, rb, rb, 12, 24, 2f, 1.2f, age * 0.03f, -age * 0.05f, 0.85f * VolPen.smooth(conv * 3f), 0.06f, age * 0.5f);
            pen.sphereShell(q[0], q[1], q[2], rb * 1.25f, rb * 1.25f, rb * 1.25f, 10, 20, 2f, 1.2f, -age * 0.02f, age * 0.03f, 0.3f * VolPen.smooth(conv * 3f), 0.08f, age * 0.4f + 2f);
        }
        if (q != null && te >= 0f && te < 30f) {
            float t = te / 22f, ease = 1f - (1f - Math.min(t, 1f)) * (1f - Math.min(t, 1f)) * (1f - Math.min(t, 1f));
            float r = EXPL_R * ease + 1f, a = (float) Math.pow(1f - Math.min(t, 1f), 1.2) * 0.7f;
            if (a > 0.005f) pen.sphereShell(q[0], q[1], q[2], r, r * 0.9f, r, 20, 40, 4f, 2f, age * 0.012f, -t * 0.4f, a, 0.025f, age * 0.2f);
            float t2 = te / 30f;
            if (t2 < 1f) {
                float e2 = 1f - (1f - t2) * (1f - t2), r2 = EXPL_R * 0.72f * e2 + 1f;
                pen.sphereShell(q[0], q[1], q[2], r2, r2, r2, 16, 32, 3f, 1.5f, -age * 0.02f, t2 * 0.5f, 0.5f * (1f - t2), 0.04f, age * 0.3f);
            }
        }
    }

    private void columns(VolPen pen) {
        if (te < 44f) {
            float t = te / 44f;
            pen.cylinder(q[0], q[1] - 2f, q[2], 6f + 4f * t, 105f, 28, 8, 5f, 2.5f, age * 0.01f, age * 0.08f, 0.85f * (1f - t) * VolPen.smooth(te / 3f), 0.05f, 0.5f);
        }
        if (te < 30f) {
            float t = te / 30f;
            pen.cylinder(q[0], q[1] - 2f, q[2], 3f + 1.5f * t, 135f, 22, 8, 4f, 3f, -age * 0.012f, age * 0.1f, 0.9f * (1f - t) * VolPen.smooth(te / 2f), 0.04f, 0.55f);
        }
    }

    private void shocks(VolPen pen) {
        for (int i = 0; i < 3; i++) {
            float t = (te - i * 7f) / 38f;
            if (t < 0f || t >= 1f) continue;
            float ease = 1f - (1f - t) * (1f - t) * (1f - t);
            float rOut = 1f + ease * (EXPL_R + 6f);
            float rIn = Math.max(0.05f, rOut - (3f + 4f * t));
            float a = (float) Math.pow(1f - t, 1.1) * VolPen.smooth((te - i * 7f) / 3f) * 0.95f;
            groundRing(pen, q[0], q[2], groundBase(), rIn, rOut, 110, a, i * 1.7f);
        }
    }

    /** Terrain-following ground fire: a lava-like pool where the flame lands, a big one after the explosion. */
    private void pools(VolPen pen) {
        if (flames && act > 0.01f && fx.hitSurface && te < 0f) {
            float hx = (float) (fx.hit[0] - ePos.x), hz = (float) (fx.hit[2] - ePos.z);
            groundDisc(pen, hx, hz, fx.hit[1], 8f * VolPen.smooth((age - START - 6f) / 14f), 12, age * 0.015f, 0.08f, 1f, 1f, 1f, 0.6f * act * flick);
        }
        if (q != null && te >= 0f) {
            float a = VolPen.smooth(te / 5f) * (1f - VolPen.smooth((te - 40f) / 110f)) * 0.8f;
            if (a > 0.01f) groundDisc(pen, q[0], q[2], groundBase(), EXPL_R * (0.4f + 0.6f * VolPen.smooth(te / 14f)), 26, age * 0.006f, 0.09f, 1f, 1f, 1f, a * flick);
        }
    }

    /** Soft light: glow along the flame, on the ground around the caster and at the target, the ball core, the blast flash. */
    private void glows(VolPen pen) {
        if (flames && act > 0.01f && cur != null) {
            for (int j = 0; j < 7; j++) {
                float s = (j + 0.5f) / 7f;
                double[] H = sample(s * PERIOD * 0.7f);
                float[] p = at(H, s * L, 0.5f);
                if (q != null && conv > 0f) p = lerp3(p, q, conv * s);
                float size = 7f + s * L * 0.28f;
                pen.billboard(p[0], p[1], p[2], size, size, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, (0.11f + 0.03f * Mth.sin(age * 0.7f + j)) * act);
            }
            float[] o = at(cur, 0f, 0f);
            pen.billboard(o[0], o[1], o[2], 6f, 6f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 0.28f * act * flick);
            // light on the terrain around the caster
            groundDisc(pen, (float) (cur[0] - ePos.x), (float) (cur[2] - ePos.z), cur[1] - 1.5, 9f, 10, 0f, 0.05f, 1f, 1f, 1f, 0.35f * act * flick);
        }
        if (q != null && te < 0f && conv > 0f) {
            float rb = 0.5f + 6f * (float) Math.pow(conv, 1.6);
            pen.billboard(q[0], q[1], q[2], rb * 2.8f, rb * 2.8f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 0.85f * VolPen.smooth(conv * 2f));
            groundDisc(pen, q[0], q[2], groundBase(), 16f * conv, 12, 0f, 0.06f, 1f, 1f, 1f, 0.5f * conv);
        }
        if (q != null && te >= 0f) {
            if (te < 10f) pen.billboard(q[0], q[1] + 3f, q[2], 120f, 120f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 0.6f * (1f - te / 10f));
            float a = 0.5f * (1f - VolPen.smooth(te / 70f));
            if (a > 0.01f) groundDisc(pen, q[0], q[2], groundBase(), EXPL_R + 6f, 20, 0f, 0.05f, 1f, 1f, 1f, a);
        }
    }

    private void embers(VolPen pen) {
        float frac = age - (float) Math.floor(age);
        for (VollzanbelFx.Ember m : fx.embers) {
            float p = (age - m.born) / m.life;
            if (p < 0f || p > 1f) continue;
            float al = (float) Math.pow(Math.sin(p * Math.PI), 0.6);
            float s = m.size * (0.8f + 0.2f * Mth.sin(age * 0.8f + m.born));
            pen.billboard((float) (m.x - ePos.x) + m.vx * frac, (float) (m.y - ePos.y) + m.vy * frac, (float) (m.z - ePos.z) + m.vz * frac, s, s, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, al);
        }
    }

    // ------------------------------------------------------------------------------------------ terrain-aware ground

    /** World y of the standing surface under the explosion point (the point itself may be in the air). */
    private double groundBase() {
        double wx = ePos.x + q[0], wz = ePos.z + q[2], wy = ePos.y + q[1];
        return FlammeMagicEntity.groundAt(level, wx, wz, wy + 2.0, 64);
    }

    /** n x n mesh following the heightmap (the texture's own transparency makes it a disc), rotated by rot, tinted. */
    private void groundDisc(VolPen pen, float cx, float cz, double baseY, float radius, int n, float rot, float yoff,
                            float r, float g, float b, float alpha) {
        if (radius < 0.05f || alpha < 0.004f) return;
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float[][] hx = new float[n + 1][n + 1], hy = new float[n + 1][n + 1], hz = new float[n + 1][n + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                float x = (i / (float) n * 2f - 1f) * radius, z = (j / (float) n * 2f - 1f) * radius;
                hx[i][j] = cx + c * x - s * z;
                hz[i][j] = cz + s * x + c * z;
                hy[i][j] = (float) (groundY(hx[i][j] + ePos.x, hz[i][j] + ePos.z, baseY) - ePos.y) + yoff;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float u0 = i / (float) n, u1 = (i + 1) / (float) n, v0 = j / (float) n, v1 = (j + 1) / (float) n;
                pen.vtx(hx[i][j], hy[i][j], hz[i][j], u0, v0, r, g, b, alpha);
                pen.vtx(hx[i + 1][j], hy[i + 1][j], hz[i + 1][j], u1, v0, r, g, b, alpha);
                pen.vtx(hx[i + 1][j + 1], hy[i + 1][j + 1], hz[i + 1][j + 1], u1, v1, r, g, b, alpha);
                pen.vtx(hx[i][j + 1], hy[i][j + 1], hz[i][j + 1], u0, v1, r, g, b, alpha);
            }
        }
    }

    private void groundRing(VolPen pen, float cx, float cz, double baseY, float rIn, float rOut, int seg, float alpha, float vOff) {
        float[] yi = new float[seg + 1], yo = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float a = i * TAU / seg, c = Mth.cos(a), s = Mth.sin(a);
            yi[i] = (float) (groundY(ePos.x + cx + c * rIn, ePos.z + cz + s * rIn, baseY) - ePos.y) + 0.1f;
            yo[i] = (float) (groundY(ePos.x + cx + c * rOut, ePos.z + cz + s * rOut, baseY) - ePos.y) + 0.1f;
        }
        float vRep = Math.max(4f, rOut * 0.6f);
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float v0 = vRep * i / seg + vOff, v1 = vRep * (i + 1) / seg + vOff;
            pen.vtx(cx + c0 * rIn, yi[i], cz + s0 * rIn, 0f, v0, 1f, 1f, 1f, alpha);
            pen.vtx(cx + c1 * rIn, yi[i + 1], cz + s1 * rIn, 0f, v1, 1f, 1f, 1f, alpha);
            pen.vtx(cx + c1 * rOut, yo[i + 1], cz + s1 * rOut, 1f, v1, 1f, 1f, 1f, alpha);
            pen.vtx(cx + c0 * rOut, yo[i], cz + s0 * rOut, 1f, v0, 1f, 1f, 1f, alpha);
        }
    }

    /** Standing surface at world (x, z) within 4 blocks above / 8 below baseY; baseY (+ a hair) when there is none (caves, cliffs, void). */
    private double groundY(double x, double z, double baseY) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(baseY);
        if (!level.hasChunkAt(new BlockPos(bx, by, bz))) return baseY + 0.05;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 4; dy >= -8; dy--) {
            p.set(bx, by + dy, bz);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
            p.set(bx, by + dy - 1, bz);
            var shape = level.getBlockState(p).getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            return p.getY() + shape.max(Direction.Axis.Y) + 0.05;
        }
        return baseY + 0.05;
    }
}
