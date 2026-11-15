package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.IrontombAttack;
import com.example.jjkspells.irontomb.IrontombCut;
import com.example.jjkspells.irontomb.entity.IrontombEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Takes the camera over during the Irontomb cutscenes: a client-side dummy entity becomes the camera entity and is moved along
 * hand-authored keyframes, the vanilla HUD and the held item are hidden, input is frozen. Also adds distance-scaled screen shake.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class CutsceneCamera {
    /** One keyframe: camera position and look-at point in the boss basis (f = toward the player, r = sideways, h = up), plus fov. */
    private record Key(float t, float f, float r, float h, float lf, float lr, float lh, float fov) { }

    private static Key k(float t, float f, float r, float h, float lh, float fov) {
        return new Key(t, f, r, h, 0, 0, lh, fov);
    }

    // the cosmic form is ~60 blocks tall (face at y=33, core at y=6) and ~290 wide, so the camera works at that scale
    private static final Key[] INTRO = {
            k(0, 120, 40, 14, 20, 70), k(50, 95, 25, 18, 30, 66), k(95, 60, -10, 22, 38, 55),
            k(125, 34, -6, 33, 36, 42), k(150, 55, 0, 38, 34, 55), k(170, 130, 0, 50, 30, 75)};
    private static final Key[] PHASE2 = {
            k(0, 70, 28, 22, 30, 70), k(40, 50, 16, 18, 32, 55), k(62, 38, -10, 36, 36, 45),
            k(90, 64, 0, 30, 32, 62), k(120, 100, 0, 40, 32, 75)};
    private static final Key[] PHASE3 = {
            k(0, 40, 0, 10, 34, 75), k(60, 70, 20, 22, 34, 55), k(75, 52, -14, 38, 36, 40),
            k(110, 90, 0, 60, 32, 70), k(140, 150, 0, 40, 32, 80)};
    private static final Key[] DEATH = {
            k(0, 90, 34, 22, 32, 62), k(60, 66, -26, 18, 33, 52), k(100, 50, 0, 28, 33, 40),
            k(130, 90, 20, 50, 33, 70), k(160, 160, 0, 70, 30, 80)};
    private static final Key[] IMMOLATE = {
            k(0, 80, 20, 12, 30, 70), k(55, 90, 0, 6, 60, 80), k(110, 70, -14, 4, 120, 95),
            k(150, 76, 0, 6, 110, 100), k(165, 130, 0, 40, 30, 80), k(210, 180, 0, 60, 30, 75)};
    private static final Key[] GENESIS = {
            k(0, 80, 0, 16, 30, 70), k(60, 120, 0, 34, 56, 75), k(130, 170, 0, 70, 100, 85),
            k(200, 220, 0, 100, 120, 95), k(232, 240, 0, 110, 40, 100), k(300, 220, 0, 100, 40, 85)};

    private static Key kk(float t, float f, float r, float h, float lf, float lr, float lh, float fov) {
        return new Key(t, f, r, h, lf, lr, lh, fov);
    }

    /** The phase 2 special: the sphere hangs 72 blocks in front of the boss at height 31. */
    private static final Key[] SINGULARITY = {
            kk(0, 150, 60, 22, 0, 0, 30, 70), kk(60, 130, 70, 40, 30, 0, 34, 66), kk(130, 175, -95, 34, 72, 0, 31, 62),
            kk(215, 160, -80, 30, 72, 0, 31, 52), kk(295, 128, -10, 34, 72, 0, 31, 42), kk(326, 108, 10, 33, 72, 0, 31, 30),
            kk(333, 150, 40, 44, 72, 0, 31, 85), kk(400, 270, 0, 90, 40, 0, 40, 80)};
    /** The phase 3 special: the whole sword (300+ blocks tall) in frame, then a rush at the tip. */
    private static final Key[] ERASURE = {
            k(0, 170, 50, 16, 28, 70), k(70, 215, 20, 40, 60, 72), k(150, 300, 0, 110, 150, 75), k(260, 335, 0, 130, 170, 72),
            k(380, 300, 0, 95, 115, 70), k(394, 160, 0, 40, 25, 58), k(402, 135, 0, 34, 22, 100), k(470, 260, 0, 60, 40, 85),
            k(540, 310, 0, 80, 60, 80)};

    private static ArmorStand rig;
    private static boolean active;
    private static int activeCut;
    private static Vec3 basisF = new Vec3(0, 0, 1);
    private static IrontombEntity boss;      // nearest awake boss, refreshed every tick
    private static float fov = -1f;
    private static boolean wasActive;

    private CutsceneCamera() {
    }

    public static boolean isActive() {
        return active;
    }

    public static IrontombEntity boss() {
        return boss;
    }

    // ------------------------------------------------------------------ tracking
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            boss = null;
            release(mc);
            return;
        }
        IrontombEntity best = null;
        double bd = 190 * 190;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof IrontombEntity b && b.isAwake() && b.isAlive() | b.getCut() == IrontombCut.DEATH) {
                double d = b.distanceToSqr(mc.player);
                if (d < bd) {
                    bd = d;
                    best = b;
                }
            }
        }
        boss = best;
        boolean global = best != null && (best.getCut() == IrontombCut.SINGULARITY || best.getCut() == IrontombCut.ERASURE);
        boolean want = IrontombPrefs.cutscenes && best != null && best.getCut() != 0 && Math.sqrt(bd) < (global ? 900 : 150);
        if (want && !active) {
            Vec3 d = mc.player.position().subtract(best.position());
            Vec3 h = new Vec3(d.x, 0, d.z);
            basisF = h.lengthSqr() < 1.0 ? new Vec3(0, 0, 1) : h.normalize();
            activeCut = best.getCut();
        }
        if (want && global) {
            // these two take the whole level: the camera follows the boss' own facing, so the sphere / sword stay in front of it
            double yw = -Math.toRadians(best.getYRot());
            basisF = new Vec3(Math.sin(yw), 0, Math.cos(yw));
        }
        if (!want && active) {
            release(mc);
        }
        active = want;
        if (active) {
            activeCut = best.getCut();
            if (rig == null || rig.level() != mc.level) {
                rig = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
                rig.setInvisible(true);
            }
            if (mc.getCameraEntity() != rig) {
                mc.setCameraEntity(rig);
            }
            if (mc.options.getCameraType().isMirrored()) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
            wasActive = true;
        }
    }

    private static void release(Minecraft mc) {
        active = false;
        fov = -1f;
        if (wasActive) {
            wasActive = false;
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
        }
    }

    // ------------------------------------------------------------------ camera path
    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !active || boss == null || rig == null) {
            return;
        }
        Key[] keys = switch (activeCut) {
            case IrontombCut.INTRO -> INTRO;
            case IrontombCut.PHASE2 -> PHASE2;
            case IrontombCut.PHASE3 -> PHASE3;
            case IrontombCut.DEATH -> DEATH;
            case IrontombCut.IMMOLATE -> IMMOLATE;
            case IrontombCut.GENESIS -> GENESIS;
            case IrontombCut.SINGULARITY -> SINGULARITY;
            case IrontombCut.ERASURE -> ERASURE;
            default -> null;
        };
        if (keys == null) {
            return;
        }
        float t = (float) ((boss.getCutT() + e.renderTickTime) * IrontombCut.scale(boss.getCut()));
        float[] v = sample(keys, t);
        Vec3 F = basisF, R = new Vec3(-F.z, 0, F.x);
        Vec3 bp = boss.getPosition(e.renderTickTime);
        Vec3 pos = bp.add(F.scale(v[0])).add(R.scale(v[1])).add(0, v[2], 0);
        Vec3 look = bp.add(F.scale(v[3])).add(R.scale(v[4])).add(0, v[5], 0);
        Vec3 d = look.subtract(pos);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * 180.0 / Math.PI);
        float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI);
        double eye = 1.68;
        rig.setPos(pos.x, pos.y - eye, pos.z);
        rig.xo = rig.xOld = pos.x;
        rig.yo = rig.yOld = pos.y - eye;
        rig.zo = rig.zOld = pos.z;
        rig.setYRot(yaw);
        rig.yRotO = yaw;
        rig.setXRot(pitch);
        rig.xRotO = pitch;
        rig.yHeadRot = yaw;
        rig.yHeadRotO = yaw;
        rig.yBodyRot = yaw;
        rig.yBodyRotO = yaw;
        fov = v[6];
    }

    /** Catmull-Rom through the keyframes. Returns {f, r, h, lf, lr, lh, fov}. */
    private static float[] sample(Key[] ks, float t) {
        t = Mth.clamp(t, ks[0].t, ks[ks.length - 1].t);
        int i = 0;
        while (i < ks.length - 2 && t > ks[i + 1].t) {
            i++;
        }
        Key p1 = ks[i], p2 = ks[i + 1];
        Key p0 = ks[Math.max(0, i - 1)], p3 = ks[Math.min(ks.length - 1, i + 2)];
        float u = (t - p1.t) / Math.max(1e-3f, p2.t - p1.t);
        return new float[]{
                cr(p0.f, p1.f, p2.f, p3.f, u), cr(p0.r, p1.r, p2.r, p3.r, u), cr(p0.h, p1.h, p2.h, p3.h, u),
                cr(p0.lf, p1.lf, p2.lf, p3.lf, u), cr(p0.lr, p1.lr, p2.lr, p3.lr, u), cr(p0.lh, p1.lh, p2.lh, p3.lh, u),
                cr(p0.fov, p1.fov, p2.fov, p3.fov, u)};
    }

    private static float cr(float a, float b, float c, float d, float u) {
        float u2 = u * u, u3 = u2 * u;
        return 0.5f * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (active && fov > 0f) {
            e.setFOV(fov);
        }
    }

    // ------------------------------------------------------------------ shake
    /** Screen shake strength 0..~4 for the nearest boss at its current attack tick. */
    public static float shake(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (boss == null || mc.player == null) {
            return 0f;
        }
        float t = (float) ((boss.getAtkT() + pt) * IrontombAttack.scale(boss.getAttack())), ct = (float) ((boss.getCutT() + pt) * IrontombCut.scale(boss.getCut()));
        float s = 0f;
        switch (boss.getAttack()) {
            case IrontombAttack.CAST_DOWN -> s = Fx.window(t, 33, 35, 36, 50) * 1.2f;
            case IrontombAttack.WEAVE -> {
                for (int i = 0; i < 3; i++) {
                    s = Math.max(s, Fx.window(t, IrontombAttack.WEAVE_TICKS[i] - 1, IrontombAttack.WEAVE_TICKS[i] + 1, IrontombAttack.WEAVE_TICKS[i] + 2, IrontombAttack.WEAVE_TICKS[i] + 12) * 0.9f);
                }
            }
            case IrontombAttack.YIELD -> s = Fx.window(t, 43, 45, 46, 60) * 0.8f;
            case IrontombAttack.CROWN -> s = Math.max(Fx.window(t, 20, 70, 84, 86) * 0.5f, Fx.window(t, 83, 85, 86, 110) * 2.0f);
            case IrontombAttack.PRISON -> s = Fx.window(t, 59, 61, 62, 80) * 1.4f;
            case IrontombAttack.IMMOLATE -> s = Math.max(Fx.window(t, 40, 140, 150, 151) * 1.0f, Fx.window(t, 149, 151, 152, 195) * 3.2f);
            case IrontombAttack.GENESIS -> s = Math.max(Fx.window(t, 30, 200, 232, 233) * 1.4f, Fx.window(t, 231, 233, 234, 290) * 3.8f);
            case IrontombAttack.SINGULARITY -> s = Math.max(Fx.ramp(t, 60, 320) * (t < 330f ? 1.6f : 0f), Fx.window(t, 329, 331, 332, 395) * 4.5f);
            case IrontombAttack.ERASURE -> s = Math.max(Fx.ramp(t, 100, 390) * (t < 396f ? 1.4f : 0f), Fx.window(t, 395, 397, 398, 480) * 5f);
            case IrontombAttack.STAGGER -> s = Fx.window(t, 0, 3, 4, 14) * 1.5f;
            default -> { }
        }
        if (boss.getCut() == IrontombCut.INTRO) {
            s = Math.max(s, Fx.window(ct, 5, 20, 90, 105) * 1.2f);
        } else if (boss.getCut() == IrontombCut.PHASE2 || boss.getCut() == IrontombCut.PHASE3) {
            s = Math.max(s, Fx.window(ct, 55, 60, 62, 100) * 2.2f);
        } else if (boss.getCut() == IrontombCut.DEATH) {
            s = Math.max(s, Fx.ramp(ct, 0, 100) * 1.8f);
        }
        double dist = Math.sqrt(boss.distanceToSqr(mc.player));
        float fall = (float) Mth.clamp(1.0 - dist / 130.0, 0.0, 1.0);
        return s * Math.max(fall, active ? 1f : 0f);
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles e) {
        float s = shake((float) e.getPartialTick());
        if (s < 0.02f) {
            return;
        }
        float tm = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + (float) e.getPartialTick();
        e.setYaw(e.getYaw() + Mth.sin(tm * 2.1f) * 0.9f * s);
        e.setPitch(e.getPitch() + Mth.sin(tm * 2.9f + 1f) * 0.8f * s);
        e.setRoll(e.getRoll() + Mth.sin(tm * 3.7f + 2f) * 1.2f * s);
    }

    // ------------------------------------------------------------------ keep the player out of the way
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent e) {
        if (active || lockedLocal()) {
            var in = e.getInput();
            in.leftImpulse = 0;
            in.forwardImpulse = 0;
            in.jumping = false;
            in.shiftKeyDown = false;
            in.up = in.down = in.left = in.right = false;
        }
    }

    private static boolean lockedLocal() {
        Minecraft mc = Minecraft.getInstance();
        return boss != null && mc.player != null && boss.getLockLeft() > 0 && boss.getLockId() == mc.player.getId();
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (active || lockedLocal()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        if (active) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void overlays(RenderGuiOverlayEvent.Pre e) {
        if (active && !IrontombHud.OVERLAY_ID.equals(e.getOverlay().id())) {
            e.setCanceled(true);
        }
    }
}
