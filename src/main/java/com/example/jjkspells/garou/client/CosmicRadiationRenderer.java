package com.example.jjkspells.garou.client;

import com.example.jjkspells.garou.entity.CosmicRadiationEntity;
import com.example.jjkspells.garou.spell.GarouCosmicRadiationSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Cosmic Radiation visuals. Palette: starlight white, soft lilac, dark violet matter. Glow is a seasoning:
 * every additive layer is low alpha so texture linework stays readable; the dark waves are alpha-blended
 * (never additive) and only get thin bright filaments from their texture.
 *
 *  1. dark purple waves   (alpha)    - expanding ellipsoid shell + terrain-following ground ring, one per damage pulse
 *  2. aura                (additive) - faint halo + two layers of upward-flowing streaks around the caster
 *  3. enemy distortion    (additive) - three faintly warping shells in slightly different tints (shimmer / refraction look)
 *  4. stars and motes     (additive) - 16-frame twinkle flip-book, motes spiralling from the caster to the irradiated
 */
public class CosmicRadiationRenderer extends EntityRenderer<CosmicRadiationEntity> {
    public CosmicRadiationRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(CosmicRadiationEntity e) {
        return RadRenderTypes.MOTE;
    }

    @Override
    public boolean shouldRender(CosmicRadiationEntity e, Frustum frustum, double x, double y, double z) {
        return true; // the effect is centred on a moving caster and spans 8+ blocks: never cull it
    }

    @Override
    public void render(CosmicRadiationEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Minecraft mc = Minecraft.getInstance();
        Level level = e.level();
        float age = e.tickCount + pt;
        float master = CosmicRadiationFx.master(age);
        CosmicRadiationFx fx = CosmicRadiationFx.of(e);

        Entity owner = level.getEntity(e.getOwnerId());
        Vec3 ePos = e.getPosition(pt);
        Vec3 oPos = owner != null ? owner.getPosition(pt) : ePos;
        float ox = (float) (oPos.x - ePos.x), oy = (float) (oPos.y - ePos.y), oz = (float) (oPos.z - ePos.z);
        float bbh = owner != null ? owner.getBbHeight() : 1.8f;
        float bbw = owner != null ? owner.getBbWidth() : 0.6f;
        boolean selfFirstPerson = owner != null && owner == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();

        Camera cam = mc.gameRenderer.getMainCamera();
        Quaternionf q = cam.rotation();
        Vector3f right = new Vector3f(1f, 0f, 0f).rotate(q);
        Vector3f up = new Vector3f(0f, 1f, 0f).rotate(q);

        // ---------------------------------------------------------------- 1. dark waves (alpha blended)
        if (!fx.waves.isEmpty()) {
            RadPen shell = new RadPen(buf.getBuffer(RadRenderTypes.alpha(RadRenderTypes.WAVE)), ps, right, up);
            for (float start : fx.waves) {
                float t = (age - start) / CosmicRadiationFx.WAVE_LIFE;
                if (t < 0f || t > 1f) continue;
                float ease = 1f - (1f - t) * (1f - t) * (1f - t);
                float radius = 1.1f + ease * (float) GarouCosmicRadiationSpell.RADIUS;
                float a = (float) Math.pow(1f - t, 1.4) * RadPen.smooth(t / 0.08f) * RadPen.smooth((radius - 1.3f) / 1.6f) * 0.36f;
                shell.sphereShell(ox, oy + bbh * 0.55f, oz, radius, radius * 0.85f, radius, 14, 30,
                        3f, 1.5f, age * 0.01f + start * 0.07f, -t * 0.35f,
                        1f, 1f, 1f, a, 0.025f, age * 0.2f);
            }
            RadPen ring = new RadPen(buf.getBuffer(RadRenderTypes.alpha(RadRenderTypes.RING)), ps, right, up);
            for (float start : fx.waves) {
                float t = (age - start) / CosmicRadiationFx.WAVE_LIFE;
                if (t < 0f || t > 1f) continue;
                float ease = 1f - (1f - t) * (1f - t) * (1f - t);
                float rOut = 0.6f + ease * (float) GarouCosmicRadiationSpell.RADIUS * 1.05f;
                float rIn = Math.max(0.05f, rOut - (1.0f + 1.7f * t));
                float a = (float) Math.pow(1f - t, 1.1) * RadPen.smooth(t / 0.06f) * 0.9f;
                groundRing(ring, level, ePos, ox, oz, oPos.y, rIn, rOut, 72, a, start * 0.13f);
            }
        }

        // ---------------------------------------------------------------- 2. aura around the caster (additive)
        if (owner != null && master > 0.01f && !selfFirstPerson) {
            RadPen halo = new RadPen(buf.getBuffer(RadRenderTypes.additive(RadRenderTypes.HALO)), ps, right, up);
            float pulse = 0.9f + 0.1f * Mth.sin(age * 0.15f);
            halo.billboard(ox, oy + bbh * 0.55f, oz, bbw * 1.6f + 0.7f, bbh * 1.1f, 0f, 0f, 0f, 1f, 1f,
                    0.9f, 0.84f, 1f, 0.15f * master * pulse);

            RadPen st = new RadPen(buf.getBuffer(RadRenderTypes.additive(RadRenderTypes.STREAKS)), ps, right, up);
            st.cylinder(ox, oy - 0.05f, oz, bbw * 0.5f + 0.32f, bbh * 1.3f, 16, 8, 3f, 1.1f, age * 0.004f, age * 0.02f,
                    0.86f, 0.80f, 1f, 0.30f * master, 0.25f, 0.55f, age * 0.05f);
            st.cylinder(ox, oy - 0.05f, oz, bbw * 0.5f + 0.54f, bbh * 1.55f, 16, 8, 2f, 1.3f, -age * 0.003f + 0.3f, age * 0.032f + 0.5f,
                    0.74f, 0.62f, 1f, 0.18f * master, 0.3f, 0.6f, age * 0.04f + 2f);
        }

        // ---------------------------------------------------------------- 3. enemy distortion (additive)
        if (e.clientIn.length + e.clientOut.length > 0) {
            RadPen d = new RadPen(buf.getBuffer(RadRenderTypes.additive(RadRenderTypes.DISTORT)), ps, right, up);
            int drawn = 0;
            for (int pass = 0; pass < 2 && drawn < 16; pass++) {
                int[] ids = pass == 0 ? e.clientIn : e.clientOut;
                float strength = (pass == 0 ? 1f : 0.55f) * RadPen.smooth(age / 12f);
                for (int id : ids) {
                    Entity t = level.getEntity(id);
                    if (t == null || drawn >= 16) continue;
                    if (t == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
                    drawn++;
                    Vec3 tp = t.getPosition(pt);
                    float tx = (float) (tp.x - ePos.x), ty = (float) (tp.y - ePos.y), tz = (float) (tp.z - ePos.z);
                    float rw = t.getBbWidth() * 0.72f + 0.28f, rh = t.getBbHeight() * 0.6f + 0.22f;
                    float cy = ty + t.getBbHeight() * 0.5f;
                    d.sphereShell(tx, cy, tz, rw, rh, rw, 10, 18, 2.5f, 1.5f, age * 0.006f, -age * 0.012f,
                            0.88f, 0.82f, 1f, 0.15f * strength, 0.05f, age * 0.25f);
                    d.sphereShell(tx, cy, tz, rw * 1.08f, rh * 1.06f, rw * 1.08f, 10, 18, 2f, 1.2f, -age * 0.005f + 0.4f, age * 0.01f,
                            0.62f, 0.42f, 1f, 0.12f * strength, 0.06f, age * 0.31f + 1.7f);
                    d.sphereShell(tx, cy, tz, rw * 1.16f, rh * 1.12f, rw * 1.16f, 10, 18, 3f, 1.8f, age * 0.004f + 0.8f, age * 0.016f,
                            0.45f, 0.85f, 1f, 0.06f * strength, 0.07f, age * 0.19f + 3.1f);
                }
            }
        }

        // ---------------------------------------------------------------- 4a. twinkling stars (additive flip-book)
        if (!fx.stars.isEmpty()) {
            RadPen sp = new RadPen(buf.getBuffer(RadRenderTypes.additive(RadRenderTypes.STARS)), ps, right, up);
            for (CosmicRadiationFx.Star s : fx.stars) {
                float dt = age - s.born;
                float p = dt / s.life;
                if (p < 0f || p >= 1f) continue;
                float x = s.x + s.vx * dt, y = s.y + s.vy * dt, z = s.z + s.vz * dt;
                if (s.follow >= 0) {
                    Entity t = level.getEntity(s.follow);
                    if (t == null) continue;
                    Vec3 tp = t.getPosition(pt);
                    x += (float) (tp.x - ePos.x);
                    y += (float) (tp.y - ePos.y);
                    z += (float) (tp.z - ePos.z);
                } else {
                    x += ox;
                    y += oy;
                    z += oz;
                }
                sp.flipbook(x, y, z, s.size, s.rot + s.spin * dt, (int) (p * 16f), 1f, 1f, 1f, 0.95f);
            }
        }

        // ---------------------------------------------------------------- 4b. motes drifting from the caster to the irradiated
        if (!fx.motes.isEmpty()) {
            RadPen mp = new RadPen(buf.getBuffer(RadRenderTypes.additive(RadRenderTypes.MOTE)), ps, right, up);
            for (CosmicRadiationFx.Mote mo : fx.motes) {
                float p = (age - mo.born) / mo.life;
                if (p < 0f || p > 1f) continue;
                Entity t = level.getEntity(mo.target);
                if (t == null) continue;
                Vec3 tp = t.getPosition(pt);
                float ex = (float) (tp.x - ePos.x), ey = (float) (tp.y - ePos.y) + t.getBbHeight() * 0.5f, ez = (float) (tp.z - ePos.z);
                float k = RadPen.smooth(p);
                float x = Mth.lerp(k, mo.sx + ox, ex), y = Mth.lerp(k, mo.sy + oy, ey), z = Mth.lerp(k, mo.sz + oz, ez);
                // swirl around the flight line
                float dx = ex - mo.sx - ox, dy = ey - mo.sy - oy, dz = ez - mo.sz - oz;
                float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                if (len > 1.0e-3f) {
                    dx /= len;
                    dy /= len;
                    dz /= len;
                    float p1x = -dz, p1y = 0f, p1z = dx; // d x up(0,1,0)
                    float pl = Mth.sqrt(p1x * p1x + p1z * p1z);
                    if (pl < 1.0e-3f) {
                        p1x = 1f;
                        p1z = 0f;
                        pl = 1f;
                    }
                    p1x /= pl;
                    p1z /= pl;
                    float p2x = dy * p1z - dz * p1y, p2y = dz * p1x - dx * p1z, p2z = dx * p1y - dy * p1x;
                    float ang = mo.phase + p * CosmicRadiationFx.TAU * 1.5f;
                    float amp = mo.amp * Mth.sin(p * (float) Math.PI);
                    float c = Mth.cos(ang) * amp, s = Mth.sin(ang) * amp;
                    x += p1x * c + p2x * s;
                    y += p1y * c + p2y * s;
                    z += p1z * c + p2z * s;
                }
                float al = (float) Math.pow(Mth.sin(p * (float) Math.PI), 0.7);
                float size = mo.size * (0.75f + 0.25f * Mth.sin(age * 0.5f + mo.phase));
                mp.billboard(x, y, z, size, size, 0f, 0f, 0f, 1f, 1f, 0.86f, 0.80f, 1f, 0.9f * al);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ terrain-aware ground ring

    /** A ring on the ground that follows the heightmap (steps, slopes) and never floats over caves / cliffs. */
    private static void groundRing(RadPen pen, Level level, Vec3 ePos, float ox, float oz, double baseY,
                                   float rIn, float rOut, int seg, float alpha, float vOff) {
        float[] yIn = new float[seg + 1], yOut = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float a = i * CosmicRadiationFx.TAU / seg;
            float c = Mth.cos(a), s = Mth.sin(a);
            yIn[i] = (float) (groundY(level, ePos.x + ox + c * rIn, ePos.z + oz + s * rIn, baseY) - ePos.y);
            yOut[i] = (float) (groundY(level, ePos.x + ox + c * rOut, ePos.z + oz + s * rOut, baseY) - ePos.y);
        }
        float vRep = 6f;
        for (int i = 0; i < seg; i++) {
            float a0 = i * CosmicRadiationFx.TAU / seg, a1 = (i + 1) * CosmicRadiationFx.TAU / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float v0 = vRep * i / seg + vOff, v1 = vRep * (i + 1) / seg + vOff;
            pen.vtx(ox + c0 * rIn, yIn[i], oz + s0 * rIn, 0f, v0, 1f, 1f, 1f, alpha);
            pen.vtx(ox + c1 * rIn, yIn[i + 1], oz + s1 * rIn, 0f, v1, 1f, 1f, 1f, alpha);
            pen.vtx(ox + c1 * rOut, yOut[i + 1], oz + s1 * rOut, 1f, v1, 1f, 1f, 1f, alpha);
            pen.vtx(ox + c0 * rOut, yOut[i], oz + s0 * rOut, 1f, v0, 1f, 1f, 1f, alpha);
        }
    }

    /**
     * Standing surface height at (x, z) within a small window around the caster's feet: looks from 1 block above to
     * 3 blocks below for the first solid top with free space over it. Falls back to the caster's own floor level, so
     * the ring neither climbs walls / ceilings nor sinks into holes.
     */
    private static double groundY(Level level, double x, double z, double baseY) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(baseY);
        if (!level.hasChunkAt(new BlockPos(bx, by, bz))) return baseY + 0.04;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy >= -3; dy--) {
            p.set(bx, by + dy, bz);
            BlockState here = level.getBlockState(p);
            if (!here.getCollisionShape(level, p).isEmpty()) continue;
            p.set(bx, by + dy - 1, bz);
            BlockState below = level.getBlockState(p);
            if (below.getCollisionShape(level, p).isEmpty()) continue;
            return p.getY() + below.getCollisionShape(level, p).max(Direction.Axis.Y) + 0.04;
        }
        return baseY + 0.04;
    }
}
