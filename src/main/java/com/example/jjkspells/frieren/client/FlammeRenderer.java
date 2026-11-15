package com.example.jjkspells.frieren.client;

import com.example.jjkspells.frieren.entity.FlammeMagicEntity;
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

import static com.example.jjkspells.frieren.spell.FrierenFlammesUltimateMagicSpell.*;

/**
 * Flamme's Ultimate Magic. Palette: ice-blue linework with white-hot cores. Layers (all additive, low alpha, linework stays readable):
 *   circles    - big layered circle under the caster (terrain-following), a gate circle per lance in the sky, rings around the sphere
 *   sphere     - forms above the target, pulses on every launch, collapses after the final lance
 *   lances     - camera-facing ribbons from the sphere through their gates to the ground
 *   impacts    - burst flip-book, ground sigils, shockwave rings, light columns; the final one adds a swirling blast shell, a huge sigil and flash
 * Everything is derived from entity age + the synced lance events; nothing touches world or weather.
 */
public class FlammeRenderer extends EntityRenderer<FlammeMagicEntity> {
    private static final float LANCE_LEN = 24f, FINAL_LEN = 40f;

    public FlammeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override public ResourceLocation getTextureLocation(FlammeMagicEntity e) { return FlameRenderTypes.MOTE; }
    @Override public boolean shouldRender(FlammeMagicEntity e, Frustum f, double x, double y, double z) { return true; }

    // per-frame scene values
    private Level level;
    private Vec3 ePos;
    private float age, ox, oy, oz;
    private double ownerY;
    private boolean hasOwner;
    private float[] cam;
    private float[][] P = new float[LANCES][3];
    private final float[] S = {0f, SPHERE_HEIGHT, 0f};
    private float circleA, sphereAlpha, sphereR;
    private FlammeFx fx;

    @Override
    public void render(FlammeMagicEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Minecraft mc = Minecraft.getInstance();
        level = e.level();
        age = e.tickCount + pt;
        fx = FlammeFx.of(e);
        ePos = e.getPosition(pt);
        Entity owner = level.getEntity(e.getOwnerId());
        hasOwner = owner != null;
        Vec3 oPos = hasOwner ? owner.getPosition(pt) : ePos;
        ox = (float) (oPos.x - ePos.x);
        oy = (float) (oPos.y - ePos.y);
        oz = (float) (oPos.z - ePos.z);
        ownerY = oPos.y;
        Camera c = mc.gameRenderer.getMainCamera();
        Vec3 cp = c.getPosition().subtract(ePos);
        cam = new float[]{(float) cp.x, (float) cp.y, (float) cp.z};
        Quaternionf q = c.rotation();
        Vector3f right = new Vector3f(1f, 0f, 0f).rotate(q), up = new Vector3f(0f, 1f, 0f).rotate(q);

        int seed = e.getSeed();
        for (int k = 0; k < LANCES; k++) {
            if (fx.impact[k] != null) P[k] = fx.impact[k];
            else {
                double[] o = FlammeMagicEntity.offsetOf(seed, k);
                P[k] = new float[]{(float) o[0], 0f, (float) o[1]};
            }
        }
        int lastLand = FlammeMagicEntity.launchTick(LANCES - 1) + FLIGHT;
        circleA = FlamePen.smooth(age / 16f) * (1f - FlamePen.smooth((age - (lastLand + 4)) / 30f));
        float grow = FlamePen.smooth((age - 18f) / 32f);
        float collapse = 1f - FlamePen.smooth((age - (FlammeMagicEntity.launchTick(LANCES - 1) + 2)) / 22f);
        float flash = 0f;
        for (int k = 0; k < LANCES; k++) {
            float d = age - FlammeMagicEntity.launchTick(k);
            if (d >= 0f) flash = Math.max(flash, Math.max(0f, 1f - d / 6f));
        }
        sphereAlpha = FlamePen.smooth((age - 14f) / 12f) * collapse;
        sphereR = (0.2f + 3.8f * grow) * (0.55f + 0.45f * collapse) * (1f + 0.05f * Mth.sin(age * 0.5f) + 0.22f * flash);

        // ---- circles: one pass per texture (a buffer can only be open for one render type at a time)
        circles(new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.CIRCLE_A)), ps, right, up), 0);
        circles(new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.CIRCLE_B)), ps, right, up), 1);
        circles(new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.CIRCLE_C)), ps, right, up), 2);

        // ---- swirling energy shells (sphere skin, final blast)
        swirl(new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.SWIRL)), ps, right, up));
        // ---- light columns
        columns(new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.STREAKS)), ps, right, up));
        // ---- lances
        lances(new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.LANCE)), ps, right, up));
        // ---- shock rings
        shocks(new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.SHOCK)), ps, right, up));
        // ---- sphere core
        if (sphereAlpha > 0.01f) {
            FlamePen sp = new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.SPHERE)), ps, right, up);
            float r = sphereR * 1.35f;
            sp.billboard(S[0], S[1], S[2], r, r, age * 0.01f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 0.95f * sphereAlpha);
        }
        // ---- impact bursts (flip-book)
        FlamePen bp = new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.BURST)), ps, right, up);
        for (int k = 0; k < LANCES; k++) {
            float tI = age - (FlammeMagicEntity.launchTick(k) + FLIGHT);
            boolean last = k == LANCES - 1;
            float dur = last ? 22f : 16f;
            if (tI < 0f || tI >= dur) continue;
            bp.flipbook(P[k][0], P[k][1] + (last ? 3f : 1f), P[k][2], last ? 26f : 5.5f, k * 0.7f, (int) (tI / dur * 16f), 1f);
        }
        // ---- flashes + motes
        FlamePen mp = new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.MOTE)), ps, right, up);
        for (int k = 0; k < LANCES; k++) {
            float tI = age - (FlammeMagicEntity.launchTick(k) + FLIGHT);
            boolean last = k == LANCES - 1;
            float dur = last ? 10f : 6f;
            if (tI < 0f || tI >= dur) continue;
            float s = last ? 70f : 9f;
            mp.billboard(P[k][0], P[k][1] + (last ? 3f : 1f), P[k][2], s, s, 0f, 0f, 0f, 1f, 1f, 0.9f, 0.96f, 1f, (last ? 0.55f : 0.5f) * (1f - tI / dur));
        }
        float frac = age - e.tickCount;
        for (FlammeFx.Mote m : fx.motes) {
            float dt = age - m.born;
            float p = dt / m.life;
            if (p < 0f || p > 1f) continue;
            float al = (float) Math.pow(Mth.sin(p * (float) Math.PI), 0.7);
            float s = m.size * (0.8f + 0.2f * Mth.sin(age * 0.6f + m.born));
            mp.billboard(m.x + m.vx * frac, m.y + m.vy * frac, m.z + m.vz * frac, s, s, 0f, 0f, 0f, 1f, 1f, 0.9f, 0.96f, 1f, 0.9f * al);
        }
        // ---- twinkling stars around the sphere while it gathers
        if (sphereAlpha > 0.01f) {
            FlamePen st = new FlamePen(buf.getBuffer(FlameRenderTypes.additive(FlameRenderTypes.STARS)), ps, right, up);
            for (int i = 0; i < 44; i++) {
                float h1 = frac01(i * 12.9898f), h2 = frac01(i * 78.233f), h3 = frac01(i * 39.425f);
                float t = ((age + i * 7f) % 26f) / 26f;
                float a = h1 * FlammeFx.TAU, rr = 5f + h2 * 11f, hy = (h3 - 0.5f) * 12f;
                st.flipbook(S[0] + Mth.cos(a) * rr, S[1] + hy, S[2] + Mth.sin(a) * rr, 0.35f + h2 * 0.5f, h3 * 6f, (int) (t * 16f), sphereAlpha);
            }
        }
    }

    private static float frac01(float x) {
        double s = Math.sin(x) * 43758.5453;
        return (float) (s - Math.floor(s));
    }

    // ------------------------------------------------------------------------------------------ circles

    /** Gate circle for lance k (k = 5: the final one, straight below the sphere): {cx,cy,cz, ux,uy,uz, vx,vy,vz}. */
    private float[] gate(int k) {
        float dx, dy, dz;
        if (k == LANCES - 1) {
            dx = 0f;
            dy = -1f;
            dz = 0f;
        } else {
            dx = P[k][0] - S[0];
            dy = P[k][1] - S[1];
            dz = P[k][2] - S[2];
            float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l;
            dy /= l;
            dz /= l;
        }
        float ux = dz, uy = 0f, uz = -dx;                       // dir x up(0,1,0) = (dz, 0, -dx) with sign
        float ul = Mth.sqrt(ux * ux + uz * uz);
        if (ul < 0.05f) {
            ux = 1f;
            uz = 0f;
            ul = 1f;
        }
        ux /= ul;
        uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        float dist = k == LANCES - 1 ? 9f : 10f;
        return new float[]{S[0] + dx * dist, S[1] + dy * dist, S[2] + dz * dist, ux, uy, uz, vx, vy, vz};
    }

    private float gateAlpha(int k) {
        float start = 12f + k * 4f;
        float in = FlamePen.smooth((age - start) / 14f);
        float d = age - FlammeMagicEntity.launchTick(k);
        float out = 1f - FlamePen.smooth((d - 6f) / 24f);
        float flash = d >= 0f ? 1f + 0.9f * Math.max(0f, 1f - d / 8f) : 1f;
        return in * out * flash * 0.9f;
    }

    private float gateScale(int k) {
        return 0.3f + 0.7f * FlamePen.smooth((age - (12f + k * 4f)) / 14f);
    }

    private void circles(FlamePen pen, int tex) {
        // under the caster: a = main (r 6.8), b = inner (r 4.2, fast, counter), c = outer (r 10.5, counter)
        if (hasOwner && circleA > 0.01f) {
            if (tex == 0) groundDisc(pen, ox, oz, ownerY, 6.8f, 14, age * 0.012f, 0.06f, 0.95f * circleA);
            if (tex == 1) groundDisc(pen, ox, oz, ownerY, 4.2f, 12, -age * 0.024f, 0.09f, 0.9f * circleA);
            if (tex == 2) groundDisc(pen, ox, oz, ownerY, 10.5f, 18, -age * 0.007f, 0.03f, 0.85f * circleA * FlamePen.smooth((age - 4f) / 14f));
        }
        // gates in the sky
        for (int k = 0; k < LANCES; k++) {
            float a = gateAlpha(k);
            if (a <= 0.01f) continue;
            float[] g = gate(k);
            float[] u = {g[3], g[4], g[5]}, v = {g[6], g[7], g[8]};
            boolean fin = k == LANCES - 1;
            float sc = gateScale(k);
            if (tex == 0 && fin) pen.disc(g[0], g[1], g[2], u, v, 7.2f * sc, age * 0.03f, a);
            if (tex == 1) pen.disc(g[0], g[1], g[2], u, v, (fin ? 5.2f : 4.2f) * sc, -age * 0.05f, a);
            if (tex == 2) pen.disc(g[0], g[1], g[2], u, v, (fin ? 9.8f : 5.8f) * sc, age * (fin ? -0.02f : 0.035f), a * 0.9f);
        }
        // rings around the sphere
        if (sphereAlpha > 0.01f) {
            float[] ux = {1f, 0f, 0f}, uz = {0f, 0f, 1f}, tilt = {0f, 0.57f, 0.82f};
            if (tex == 2) pen.disc(S[0], S[1], S[2], ux, uz, sphereR * 3.0f, age * 0.045f, 0.55f * sphereAlpha);
            if (tex == 1) pen.disc(S[0], S[1], S[2], ux, tilt, sphereR * 2.3f, -age * 0.06f, 0.5f * sphereAlpha);
        }
        // impact sigils on the ground (lances) / the huge sigil (final)
        for (int k = 0; k < LANCES; k++) {
            float tI = age - (FlammeMagicEntity.launchTick(k) + FLIGHT);
            if (tI < 0f) continue;
            boolean fin = k == LANCES - 1;
            if (!fin && tex == 1 && tI < 55f) {
                float a = (1f - FlamePen.smooth(tI / 55f)) * (tI < 3f ? tI / 3f : 1f) * 0.9f;
                groundDisc(pen, P[k][0], P[k][2], ePos.y + P[k][1], 3.9f, 8, age * 0.03f + k, 0.07f, a);
            }
            if (fin && tI < 90f) {
                float grow = 0.3f + 0.7f * FlamePen.smooth(tI / 14f);
                float a = FlamePen.smooth(tI / 4f) * (1f - FlamePen.smooth((tI - 30f) / 60f)) * 0.85f;
                if (tex == 2) groundDisc(pen, P[k][0], P[k][2], ePos.y + P[k][1], 29f * grow, 26, age * 0.012f, 0.05f, a);
                if (tex == 0) groundDisc(pen, P[k][0], P[k][2], ePos.y + P[k][1], 15f * grow, 20, -age * 0.02f, 0.08f, a);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ shells, columns, lances, shocks

    private void swirl(FlamePen pen) {
        if (sphereAlpha > 0.01f) {
            pen.sphereShell(S[0], S[1], S[2], sphereR, sphereR, sphereR, 12, 24, 2f, 1.2f, age * 0.02f, -age * 0.03f, 0.35f * sphereAlpha, 0.03f, age * 0.3f);
            pen.sphereShell(S[0], S[1], S[2], sphereR * 1.18f, sphereR * 1.18f, sphereR * 1.18f, 10, 20, 2f, 1.2f, -age * 0.015f, age * 0.02f, 0.16f * sphereAlpha, 0.05f, age * 0.2f + 2f);
        }
        int k = LANCES - 1;
        float tF = age - (FlammeMagicEntity.launchTick(k) + FLIGHT);
        float dur = 26f;
        if (tF >= 0f && tF < dur) {
            float t = tF / dur, ease = 1f - (1f - t) * (1f - t) * (1f - t);
            float r = (float) AREA_RADIUS * ease + 1f;
            float a = (float) (Math.pow(1f - t, 1.3) * 0.5);
            pen.sphereShell(P[k][0], P[k][1] + 1f, P[k][2], r, r * 0.85f, r, 18, 36, 4f, 2f, age * 0.01f, -t * 0.4f, a, 0.02f, age * 0.2f);
        }
    }

    private void columns(FlamePen pen) {
        for (int k = 0; k < LANCES; k++) {
            float tI = age - (FlammeMagicEntity.launchTick(k) + FLIGHT);
            boolean last = k == LANCES - 1;
            float dur = last ? 30f : 14f;
            if (tI < 0f || tI >= dur) continue;
            float t = tI / dur;
            float radius = last ? 5f + 3f * t : 0.9f + 0.7f * t;
            float a = (last ? 0.75f : 0.7f) * (1f - t) * FlamePen.smooth(tI / 2f);
            pen.cylinder(P[k][0], P[k][1], P[k][2], radius, last ? 70f : 18f, last ? 28 : 18, 8, last ? 5f : 2f, last ? 2.5f : 1.5f, age * 0.01f, age * 0.07f, a, 0.06f, 0.5f);
        }
    }

    private void lances(FlamePen pen) {
        for (int k = 0; k < LANCES; k++) {
            float tL = age - FlammeMagicEntity.launchTick(k);
            if (tL < 0f) continue;
            boolean last = k == LANCES - 1;
            float dx = P[k][0] - S[0], dy = P[k][1] - S[1], dz = P[k][2] - S[2];
            float D = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= D;
            dy /= D;
            dz /= D;
            float p = tL / FLIGHT;
            float alpha = 1f, width = last ? 4.2f : 1.5f;
            float h = (float) Math.pow(Mth.clamp(p, 0f, 1f), 1.6);
            float len = last ? FINAL_LEN : LANCE_LEN;
            float[] head = {S[0] + dx * D * h, S[1] + dy * D * h, S[2] + dz * D * h};
            float tl = Math.min(len, D * h + 0.1f);
            if (p >= 1f) {
                float f = (tL - FLIGHT) / (last ? 16f : 9f);
                if (f >= 1f) continue;
                alpha = 1f - FlamePen.smooth(f);
                width *= 1f - 0.5f * f;
                tl = len * (1f - 0.6f * f);
            }
            float[] tail = {head[0] - dx * tl, head[1] - dy * tl, head[2] - dz * tl};
            pen.ribbon(head, tail, width * 2.4f, width * 2.4f, cam, 0.20f * alpha);   // soft halo ribbon
            pen.ribbon(head, tail, width, width * 0.8f, cam, alpha);                   // the lance itself
        }
    }

    private void shocks(FlamePen pen) {
        for (int k = 0; k < LANCES; k++) {
            boolean last = k == LANCES - 1;
            int rings = last ? 3 : 1;
            for (int i = 0; i < rings; i++) {
                float tI = age - (FlammeMagicEntity.launchTick(k) + FLIGHT) - i * 6f;
                float dur = last ? 34f : 20f;
                if (tI < 0f || tI >= dur) continue;
                float t = tI / dur, ease = 1f - (1f - t) * (1f - t) * (1f - t);
                float rOut = 1f + ease * (last ? 34f : 11f);
                float rIn = Math.max(0.05f, rOut - (last ? 3f + 3f * t : 1.2f + 1.8f * t));
                float a = (float) Math.pow(1f - t, 1.1) * FlamePen.smooth(tI / 3f) * 0.9f;
                groundRing(pen, P[k][0], P[k][2], ePos.y + P[k][1], rIn, rOut, last ? 96 : 56, a, k * 0.13f + i);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ terrain-aware ground

    /** Square mesh (n x n cells) that follows the heightmap; the texture's own transparency makes it a disc. Rotated by rot. */
    private void groundDisc(FlamePen pen, float cx, float cz, double baseY, float radius, int n, float rot, float yoff, float alpha) {
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float[][] hy = new float[n + 1][n + 1], hx = new float[n + 1][n + 1], hz = new float[n + 1][n + 1];
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
                pen.vtx(hx[i][j], hy[i][j], hz[i][j], u0, v0, 1f, 1f, 1f, alpha);
                pen.vtx(hx[i + 1][j], hy[i + 1][j], hz[i + 1][j], u1, v0, 1f, 1f, 1f, alpha);
                pen.vtx(hx[i + 1][j + 1], hy[i + 1][j + 1], hz[i + 1][j + 1], u1, v1, 1f, 1f, 1f, alpha);
                pen.vtx(hx[i][j + 1], hy[i][j + 1], hz[i][j + 1], u0, v1, 1f, 1f, 1f, alpha);
            }
        }
    }

    /** Annulus on the ground (u = 0 inner .. 1 outer) following the terrain. */
    private void groundRing(FlamePen pen, float cx, float cz, double baseY, float rIn, float rOut, int seg, float alpha, float vOff) {
        float[] yi = new float[seg + 1], yo = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float a = i * FlammeFx.TAU / seg, c = Mth.cos(a), s = Mth.sin(a);
            yi[i] = (float) (groundY(ePos.x + cx + c * rIn, ePos.z + cz + s * rIn, baseY) - ePos.y) + 0.1f;
            yo[i] = (float) (groundY(ePos.x + cx + c * rOut, ePos.z + cz + s * rOut, baseY) - ePos.y) + 0.1f;
        }
        float vRep = Math.max(4f, rOut * 0.6f);
        for (int i = 0; i < seg; i++) {
            float a0 = i * FlammeFx.TAU / seg, a1 = (i + 1) * FlammeFx.TAU / seg;
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
