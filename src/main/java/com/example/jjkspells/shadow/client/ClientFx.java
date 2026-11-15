package com.example.jjkspells.shadow.client;

import com.example.jjkspells.shadow.entity.*;
import net.minecraft.world.phys.Vec3;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Client-only state driven by the controller entities: input lock, no jumping or sprinting, screen flashes, camera shake and
 * the animations of the caster.
 */
public final class ClientFx {
    public static boolean locked, noJump;
    /** Entity ids not drawn this frame (a caster who has vanished); filled by the sequences during the tick. */
    public static java.util.Set<Integer> hide = new java.util.HashSet<>();
    private static java.util.Set<Integer> hideNext = new java.util.HashSet<>();
    public static float darkness, flash, shake, fov;
    private static float wantFov;
    public static int flashColor = 0xE8E8F0;
    private static boolean wantLock, wantNoJump;
    private static float wantDark;
    private static final double RELEASE_FOV = 7.0;
    private static net.minecraft.client.CameraType wantCam, savedCam;

    private ClientFx() {
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        hide = hideNext;
        hideNext = new java.util.HashSet<>();
        flash *= 0.88f;
        if (flash < 0.01f) {
            flash = 0f;
        }
        shake *= 0.86f;
        if (shake < 0.01f) {
            shake = 0f;
        }
        fov += (wantFov - fov) * 0.2f;
        wantFov = 0f;
        if (Math.abs(fov) < 0.002f) {
            fov = 0f;
        }
        darkness += (wantDark - darkness) * 0.3f;
        wantDark = 0f;
        if (darkness < 0.005f) {
            darkness = 0f;
        }
        if (wantCam != null && mc.player != null) {
            if (savedCam == null) {
                savedCam = mc.options.getCameraType();
            }
            if (mc.options.getCameraType() != wantCam) {
                mc.options.setCameraType(wantCam);
            }
        } else if (savedCam != null) {
            mc.options.setCameraType(savedCam);
            savedCam = null;
        }
        wantCam = null;
        locked = wantLock;
        wantLock = false;
        noJump = wantNoJump;
        wantNoJump = false;
        if (mc.level == null || mc.player == null) {
            darkness = 0f;
            fov = 0f;
            locked = false;
            noJump = false;
        } else if (noJump) {
            mc.player.setSprinting(false);
        }
    }

    private static void anim(AbstractClientPlayer ap, String... names) {
        AnimationHolder h = SpellAnimLookup.find(names);
        if (h != null) {
            h.getForPlayer().ifPresent(id -> AnimationHelper.animatePlayerStart(ap, id));
        }
    }

    private static float near(Entity e, double start, double span) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0f;
        }
        double d = Math.sqrt(mc.player.distanceToSqr(e));
        return Mth.clamp(1f - (float) ((d - start) / span), 0f, 1f);
    }

    private static void trailAdd(java.util.List<Vec3> trail, Entity owner, int max) {
        if (owner != null) {
            trail.add(owner.position().add(0, owner.getBbHeight() * 0.6, 0));
            while (trail.size() > max) {
                trail.remove(0);
            }
        }
    }

    private static void hideFor(Entity owner, SeqEntity e, int age, double a, double b) {
        if (owner != null && age >= SeqEntity.tk(a) && age < SeqEntity.tk(b)) {
            hideNext.add(owner.getId());
        }
    }

    private static void lockFor(Entity owner, boolean cond) {
        if (cond && owner != null && owner == Minecraft.getInstance().player) {
            wantLock = true;
        }
    }

    // ---------------------------------------------------------------- the sequences

    private static void bang(int age, double sec, float sh, float fl, int color, float n) {
        if (age == SeqEntity.tk(sec)) {
            shake = Math.max(shake, sh * n);
            if (fl > 0) {
                flash = Math.max(flash, fl * n);
                flashColor = color;
            }
        }
    }

    private static void zoom(int age, double a, double b, float amount, float n) {
        if (age >= SeqEntity.tk(a) && age < SeqEntity.tk(b)) {
            if (amount >= 0) {
                wantFov = Math.max(wantFov, amount * n);
            } else {
                wantFov = Math.min(wantFov, amount * n);
            }
        }
    }

    private static void dark(int age, double a, double b, float amount, float n) {
        if (age >= SeqEntity.tk(a) && age < SeqEntity.tk(b)) {
            wantDark = Math.max(wantDark, amount * n);
        }
    }

    public static void tickSeq(SeqEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        float n = near(e, 24, 70);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        if (e instanceof GardenEntity) {
            hideFor(owner, e, age, 0.6, 2.5);
            zoom(age, 0.0, 0.6, 0.07f, n);
            bang(age, 4.5, 1.2f, 0f, 0, n);
            dark(age, 0.3, 5.0, 0.2f, n);
        } else if (e instanceof ThroneEntity) {
            hideFor(owner, e, age, 1.5, 7.95);
            zoom(age, 0.0, 1.5, -0.10f, n);
            dark(age, 0.5, 9.5, 0.28f, n);
            bang(age, 1.5, 1.5f, 0f, 0, n);
            bang(age, 8.5, 3.0f, 0.35f, 0xB69AFF, n);
        } else if (e instanceof SerpentEntity) {
            zoom(age, 0.0, 1.0, 0.1f, n);
            bang(age, 1.5, 1.6f, 0f, 0, n);
            bang(age, 2.5, 2.0f, 0.1f, 0xB26AFF, n);
            bang(age, 6.5, 3.5f, 0.3f, 0xB26AFF, n);
        } else if (e instanceof CounterEntity) {
            zoom(age, 0.0, 0.8, 0.08f, n);
            for (double[] d : e.fx()) {
                if (d[0] == 1) {
                    int at = (int) d[1];
                    boolean perfect = d[2] > 0.5;
                    if (age >= at && age < at + 8) {
                        wantFov = Math.max(wantFov, perfect ? 0.28f : 0.14f);
                        wantDark = Math.max(wantDark, perfect ? 0.45f : 0.2f);
                    }
                    if (age == at + 6) {
                        shake = Math.max(shake, (perfect ? 2.5f : 1.2f) * n);
                        flash = Math.max(flash, (perfect ? 0.5f : 0.2f) * n);
                        flashColor = 0xD3B6FF;
                    }
                }
            }
        } else if (e instanceof AtomicEntity) {
            boolean mine = owner != null && owner == Minecraft.getInstance().player;
            lockFor(owner, age <= SeqEntity.tk(AtomicEntity.RELEASE + 0.6));
            if (mine && com.example.jjkspells.shadow.config.ShdConfig.ATOMIC.cinematicCamera.get()) {
                double sec = age / 20.0;
                net.minecraft.client.CameraType ct = sec < 2.0 ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                        : sec < 4.5 ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                        : sec < 7.0 ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                        : sec < 12.5 ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                        : net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                wantCam = ct;
                // shot 1: closer to the face; shot 3: the declaration close-up; release: a rapid pull back; then wide
                if (sec < 2.0) {
                    wantFov = Math.max(wantFov, 0.05f + 0.12f * (float) (sec / 2.0));
                } else if (sec < 4.5) {
                    wantFov = Math.min(wantFov, -0.10f);
                } else if (sec < 7.0) {
                    wantFov = Math.max(wantFov, 0.12f + 0.18f * (float) ((sec - 4.5) / 2.5));
                } else if (sec < 8.5) {
                    wantFov = Math.min(wantFov, -0.45f * (float) Math.min(1.0, (sec - 7.0) / 0.4));
                } else if (sec < 12.5) {
                    wantFov = Math.min(wantFov, -0.30f);
                }
            } else if (age < SeqEntity.tk(RELEASE_FOV)) {
                wantFov = Math.max(wantFov, 0.03f + 0.1f * Math.min(1f, age / (float) SeqEntity.tk(RELEASE_FOV)));
            }
            dark(age, 4.5, 7.0, 0.22f, n);
            bang(age, AtomicEntity.RELEASE, 3.0f, 0.55f, 0xD9B8FF, n);
            bang(age, AtomicEntity.BLAST, 6.0f, 0.0f, 0xAE4DFF, n);
            bang(age, AtomicEntity.SHOCK_A, 3.0f, 0.0f, 0xAE4DFF, n);
        }
    }
}
