package com.example.jjkspells.vfx.sprite;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns every live sprite effect on this client: spawn (pooled, capped), tick, and one batched draw per render type.
 * <ul>
 * <li>No allocation per frame or per effect after warm-up (instances are pooled, vectors are scratch floats).</li>
 * <li>Effects are bucketed by render type, so a whole sheet is one draw call whatever the effect count.</li>
 * <li>Hard cap, distance cull, behind-the-camera cull and optional decor thinning keep the cost bounded.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class SpriteVfxManager {
    private SpriteVfxManager() {}

    private static final class Bucket {
        final RenderType type;
        final boolean sorted;
        final ArrayList<SpriteVfx> list = new ArrayList<>(64);

        Bucket(RenderType type, boolean sorted) {
            this.type = type;
            this.sorted = sorted;
        }
    }

    private static final Map<RenderType, Bucket> BUCKET_MAP = new HashMap<>();
    private static final ArrayList<Bucket> BUCKETS = new ArrayList<>();
    private static final ArrayDeque<SpriteVfx> POOL = new ArrayDeque<>();
    private static final int POOL_MAX = 1024;
    private static final Comparator<SpriteVfx> FAR_FIRST = (a, b) -> Float.compare(b.distSq, a.distSq);
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();
    private static int active;

    // ---------------------------------------------------------------------------------------- spawn

    @Nullable
    public static SpriteVfx spawn(ResourceLocation id, double x, double y, double z) {
        SpriteEffectDef d = SpriteEffectRegistry.get(id);
        return d == null ? null : spawn(d, x, y, z, false);
    }

    /**
     * Starts an effect, or returns null if it was dropped (disabled, cap reached, too far away, or thinned decor).
     * Callers must null check.
     */
    @Nullable
    public static SpriteVfx spawn(SpriteEffectDef d, double x, double y, double z, boolean decor) {
        if (!SpriteVfxConfig.ENABLED.get() || active >= SpriteVfxConfig.MAX_ACTIVE.get()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (decor) {
            double dens = SpriteVfxConfig.DENSITY.get();
            if (dens < 0.999 && ThreadLocalRandom.current().nextDouble() > dens) return null;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam != null && cam.isInitialized()) {
            Vec3 cp = cam.getPosition();
            double lim = SpriteVfxConfig.CULL_DISTANCE.get() * 1.25;
            double dx = x - cp.x, dy = y - cp.y, dz = z - cp.z;
            if (dx * dx + dy * dy + dz * dz > lim * lim) return null;
        }
        RenderType type = SpriteRenderTypes.get(d);
        Bucket b = BUCKET_MAP.get(type);
        if (b == null) {
            b = new Bucket(type, d.blend == SpriteEffectDef.Blend.ALPHA);
            BUCKET_MAP.put(type, b);
            BUCKETS.add(b);
        }
        SpriteVfx v = POOL.pollLast();
        if (v == null) v = new SpriteVfx();
        v.reset(d, type, x, y, z);
        b.list.add(v);
        active++;
        return v;
    }

    /** Applies a network entry. */
    public static void receive(List<SpriteVfxPacket.Entry> entries) {
        for (int i = 0, n = entries.size(); i < n; i++) {
            SpriteVfxPacket.Entry e = entries.get(i);
            SpriteEffectDef d = SpriteEffectRegistry.get(e.id);
            if (d == null) continue;
            SpriteVfx v = spawn(d, e.x, e.y, e.z, (e.flags & SpriteVfxPacket.F_DECOR) != 0);
            if (v == null) continue;
            if ((e.flags & SpriteVfxPacket.F_SCALE) != 0) v.scale = e.scale;
            if ((e.flags & SpriteVfxPacket.F_VEL) != 0) v.velocity(e.vx, e.vy, e.vz);
            if ((e.flags & SpriteVfxPacket.F_DIR) != 0) v.dir(e.dx, e.dy, e.dz);
            if ((e.flags & SpriteVfxPacket.F_LENGTH) != 0) v.length = e.length;
            if ((e.flags & SpriteVfxPacket.F_LIFE) != 0) v.life(e.life);
            if ((e.flags & SpriteVfxPacket.F_TINT) != 0) v.tint(e.tr, e.tg, e.tb);
            if ((e.flags & SpriteVfxPacket.F_ROLL) != 0) v.roll(e.roll);
            if ((e.flags & SpriteVfxPacket.F_ATTACH) != 0) v.attach(e.attachId, e.attachYOff);
        }
    }

    public static void clear() {
        for (int i = 0; i < BUCKETS.size(); i++) {
            List<SpriteVfx> l = BUCKETS.get(i).list;
            for (int k = 0; k < l.size(); k++) release(l.get(k));
            l.clear();
        }
        BUCKET_MAP.clear();
        BUCKETS.clear();
        active = 0;
    }

    private static void release(SpriteVfx v) {
        v.def = null;
        v.type = null;
        if (POOL.size() < POOL_MAX) POOL.addLast(v);
    }

    public static int activeCount() {
        return active;
    }

    // ---------------------------------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || active == 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        for (int bi = 0; bi < BUCKETS.size(); bi++) {
            ArrayList<SpriteVfx> list = BUCKETS.get(bi).list;
            for (int i = list.size() - 1; i >= 0; i--) {
                SpriteVfx v = list.get(i);
                if (!tickOne(v, level)) {
                    int last = list.size() - 1;
                    list.set(i, list.get(last)); // swap-remove, order does not matter
                    list.remove(last);
                    release(v);
                    active--;
                }
            }
        }
    }

    private static boolean tickOne(SpriteVfx v, ClientLevel level) {
        SpriteEffectDef d = v.def;
        v.px = v.x;
        v.py = v.y;
        v.pz = v.z;
        v.prevRoll = v.roll;
        if (v.attachId >= 0) {
            Entity en = level.getEntity(v.attachId);
            if (en == null || en.isRemoved()) return false;
            v.x = en.getX();
            v.y = en.getY() + v.attachYOff;
            v.z = en.getZ();
        } else {
            v.x += v.vx;
            v.y += v.vy;
            v.z += v.vz;
            v.vx *= d.drag;
            v.vy = v.vy * d.drag - d.gravity;
            v.vz *= d.drag;
        }
        v.roll += d.spin;
        if (!d.fullbright && (v.age & 3) == 0) {
            v.light = LevelRenderer.getLightColor(level, MUT.set(v.x, v.y, v.z));
        }
        return ++v.age < v.life;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        clear();
    }

    // ---------------------------------------------------------------------------------------- render

    // scratch values, render thread only
    private static float lookX, lookY, lookZ, rightX, rightY, rightZ, upX, upY, upZ;
    private static double camX, camY, camZ;
    private static float cullSq;
    private static boolean crossfade;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || active == 0) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        camX = cp.x;
        camY = cp.y;
        camZ = cp.z;
        Vector3f look = cam.getLookVector(), left = cam.getLeftVector(), up = cam.getUpVector();
        lookX = look.x;
        lookY = look.y;
        lookZ = look.z;
        rightX = -left.x;
        rightY = -left.y;
        rightZ = -left.z;
        upX = up.x;
        upY = up.y;
        upZ = up.z;
        float cull = SpriteVfxConfig.CULL_DISTANCE.get().floatValue();
        cullSq = cull * cull;
        crossfade = SpriteVfxConfig.CROSSFADE.get();

        Matrix4f m = e.getPoseStack().last().pose();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (int bi = 0; bi < BUCKETS.size(); bi++) {
            Bucket b = BUCKETS.get(bi);
            ArrayList<SpriteVfx> list = b.list;
            int n = list.size();
            if (n == 0) continue;
            if (b.sorted && n > 1) {
                for (int i = 0; i < n; i++) {
                    SpriteVfx v = list.get(i);
                    double dx = Mth.lerp(pt, v.px, v.x) - camX, dy = Mth.lerp(pt, v.py, v.y) - camY, dz = Mth.lerp(pt, v.pz, v.z) - camZ;
                    v.distSq = (float) (dx * dx + dy * dy + dz * dz);
                }
                list.sort(FAR_FIRST);
            }
            VertexConsumer vc = buf.getBuffer(b.type);
            for (int i = 0; i < n; i++) drawOne(vc, m, list.get(i), pt);
            buf.endBatch(b.type);
        }
    }

    private static void drawOne(VertexConsumer vc, Matrix4f m, SpriteVfx v, float pt) {
        final SpriteEffectDef d = v.def;
        final float age = v.age + pt;
        final float p = Math.min(1f, age / v.life);

        float cx = (float) (Mth.lerp(pt, v.px, v.x) - camX);
        float cy = (float) (Mth.lerp(pt, v.py, v.y) - camY);
        float cz = (float) (Mth.lerp(pt, v.pz, v.z) - camZ);
        float dist2 = cx * cx + cy * cy + cz * cz;
        if (dist2 > cullSq) return;

        float sc = Mth.lerp(p, d.scaleStart, d.scaleEnd) * v.scale;
        float half = d.size * sc * 0.5f;
        boolean axis = d.orientation == SpriteEffectDef.Orientation.AXIS;
        // behind the camera (cheap; beams are long so they skip this test)
        if (!axis && cx * lookX + cy * lookY + cz * lookZ < -half * 1.5f) return;

        float a = v.a * d.alpha;
        if (d.fadeIn > 0) a *= Math.min(1f, age / d.fadeIn);
        if (d.fadeOut > 0) a *= Math.min(1f, (v.life - age) / d.fadeOut);
        if (a <= 0.004f) return;
        a = Math.min(a, 1f);

        // frame selection
        final int frames = d.frames;
        float f;
        boolean loops = d.playback == SpriteEffectDef.Playback.LOOP;
        switch (d.playback) {
            case LOOP -> f = (age * d.fps / 20f) % frames;
            case HOLD -> f = Math.min(age * d.fps / 20f, frames - 0.001f);
            default -> f = Math.min(p * frames, frames - 0.001f);
        }
        int i0 = (int) f;
        float frac = f - i0;
        int i1 = loops ? (i0 + 1) % frames : Math.min(i0 + 1, frames - 1);
        boolean blendFrames = crossfade && d.crossfade && i1 != i0 && frac > 0.02f;

        int cr = (int) (Mth.clamp(v.r, 0f, 1f) * 255f), cg = (int) (Mth.clamp(v.g, 0f, 1f) * 255f), cb = (int) (Mth.clamp(v.b, 0f, 1f) * 255f);
        int light = d.fullbright ? LightTexture.FULL_BRIGHT : v.light;
        float a0 = blendFrames ? a * (1f - frac) : a;
        int ia0 = (int) (a0 * 255f);
        int ia1 = blendFrames ? (int) (a * frac * 255f) : 0;

        switch (d.orientation) {
            case AXIS -> drawAxis(vc, m, v, d, cx, cy, cz, half, i0, i1, blendFrames, cr, cg, cb, ia0, ia1, light);
            default -> {
                float rx, ry, rz, ux, uy, uz;
                float roll = (float) Math.toRadians(Mth.lerp(pt, v.prevRoll, v.roll));
                float cs = Mth.cos(roll), sn = Mth.sin(roll);
                float bRx, bRy, bRz, bUx, bUy, bUz;
                switch (d.orientation) {
                    case BILLBOARD_Y -> {
                        float l = (float) Math.sqrt(cx * cx + cz * cz);
                        if (l < 1e-4f) {
                            bRx = rightX;
                            bRy = 0f;
                            bRz = rightZ;
                        } else {
                            bRx = -cz / l;
                            bRy = 0f;
                            bRz = cx / l;
                        }
                        bUx = 0f;
                        bUy = 1f;
                        bUz = 0f;
                        cs = 1f;
                        sn = 0f;
                    }
                    case FLAT -> {
                        bRx = 1f;
                        bRy = 0f;
                        bRz = 0f;
                        bUx = 0f;
                        bUy = 0f;
                        bUz = 1f;
                    }
                    case NORMAL -> {
                        float nx = v.dx, ny = v.dy, nz = v.dz;
                        float refX = 0f, refY = 1f, refZ = 0f;
                        if (Math.abs(ny) > 0.99f) {
                            refX = 1f;
                            refY = 0f;
                        }
                        // right = normalize(ref x n)
                        float qx = refY * nz - refZ * ny, qy = refZ * nx - refX * nz, qz = refX * ny - refY * nx;
                        float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
                        qx /= ql;
                        qy /= ql;
                        qz /= ql;
                        bRx = qx;
                        bRy = qy;
                        bRz = qz;
                        // up = n x right
                        bUx = ny * qz - nz * qy;
                        bUy = nz * qx - nx * qz;
                        bUz = nx * qy - ny * qx;
                    }
                    default -> {
                        bRx = rightX;
                        bRy = rightY;
                        bRz = rightZ;
                        bUx = upX;
                        bUy = upY;
                        bUz = upZ;
                    }
                }
                rx = (bRx * cs + bUx * sn) * half;
                ry = (bRy * cs + bUy * sn) * half;
                rz = (bRz * cs + bUz * sn) * half;
                ux = (-bRx * sn + bUx * cs) * half;
                uy = (-bRy * sn + bUy * cs) * half;
                uz = (-bRz * sn + bUz * cs) * half;
                emit(vc, m, d, i0, cx, cy, cz, rx, ry, rz, ux, uy, uz, cr, cg, cb, ia0, light);
                if (blendFrames) emit(vc, m, d, i1, cx, cy, cz, rx, ry, rz, ux, uy, uz, cr, cg, cb, ia1, light);
            }
        }
    }

    /** Beams and lightning: from the effect position along its direction, 'length' blocks long. */
    private static void drawAxis(VertexConsumer vc, Matrix4f m, SpriteVfx v, SpriteEffectDef d, float sx, float sy, float sz, float halfW,
                                 int i0, int i1, boolean blendFrames, int cr, int cg, int cb, int ia0, int ia1, int light) {
        float dx = v.dx, dy = v.dy, dz = v.dz;
        float len = Math.max(0.01f, v.length);
        int segs = 1;
        if (d.tileLength > 0f && d.axisAlongU) segs = Mth.clamp((int) Math.ceil(len / d.tileLength), 1, 48);
        float segLen = len / segs;
        float segHalf = segLen * 0.5f;
        for (int s = 0; s < segs; s++) {
            float off = segLen * (s + 0.5f);
            float mx = sx + dx * off, my = sy + dy * off, mz = sz + dz * off;
            // width direction = d x (camera -> segment), so the strip faces the camera
            float px = dy * mz - dz * my, py = dz * mx - dx * mz, pz = dx * my - dy * mx;
            float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
            if (pl < 1e-5f) {
                px = upX;
                py = upY;
                pz = upZ;
            } else {
                px /= pl;
                py /= pl;
                pz /= pl;
            }
            float rx, ry, rz, ux, uy, uz;
            if (d.axisAlongU) {
                rx = dx * segHalf;
                ry = dy * segHalf;
                rz = dz * segHalf;
                ux = px * halfW;
                uy = py * halfW;
                uz = pz * halfW;
            } else {
                ux = -dx * segHalf;
                uy = -dy * segHalf;
                uz = -dz * segHalf;
                rx = px * halfW;
                ry = py * halfW;
                rz = pz * halfW;
            }
            emit(vc, m, d, i0, mx, my, mz, rx, ry, rz, ux, uy, uz, cr, cg, cb, ia0, light);
            if (blendFrames) emit(vc, m, d, i1, mx, my, mz, rx, ry, rz, ux, uy, uz, cr, cg, cb, ia1, light);
        }
    }

    private static final float INSET = 0.004f;

    private static void emit(VertexConsumer vc, Matrix4f m, SpriteEffectDef d, int frame, float cx, float cy, float cz,
                             float rx, float ry, float rz, float ux, float uy, float uz, int r, int g, int b, int a, int light) {
        if (a <= 0) return;
        int col = frame % d.cols, row = frame / d.cols;
        float u0 = (col + INSET) / d.cols, u1 = (col + 1 - INSET) / d.cols;
        float v0 = (row + INSET) / d.rows, v1 = (row + 1 - INSET) / d.rows;
        vc.vertex(m, cx - rx + ux, cy - ry + uy, cz - rz + uz).uv(u0, v0).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, cx + rx + ux, cy + ry + uy, cz + rz + uz).uv(u1, v0).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, cx + rx - ux, cy + ry - uy, cz + rz - uz).uv(u1, v1).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, cx - rx - ux, cy - ry - uy, cz - rz - uz).uv(u0, v1).color(r, g, b, a).uv2(light).endVertex();
    }
}
