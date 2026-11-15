package com.example.jjkspells.velgrynd.client;

import com.example.jjkspells.velgrynd.entity.*;
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
    public static float darkness, flash, shake;
    public static int flashColor = 0xE8E8F0;
    private static boolean wantLock, wantNoJump;
    private static float wantDark;

    private ClientFx() {
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        flash *= 0.88f;
        if (flash < 0.01f) {
            flash = 0f;
        }
        shake *= 0.86f;
        if (shake < 0.01f) {
            shake = 0f;
        }
        darkness += (wantDark - darkness) * 0.3f;
        wantDark = 0f;
        if (darkness < 0.005f) {
            darkness = 0f;
        }
        locked = wantLock;
        wantLock = false;
        noJump = wantNoJump;
        wantNoJump = false;
        if (mc.level == null || mc.player == null) {
            darkness = 0f;
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
        if (e instanceof BreathEntity) {
            dark(age, 1.0, 7.0, 0.12f, n);
            bang(age, 2.5, 4.0f, 0.6f, 0xFFB070, n);
            bang(age, 5.5, 2.0f, 0.2f, 0xFFE0A0, n);
            bang(age, 6.5, 6.0f, 1.0f, 0xFFF0D0, n);
        } else if (e instanceof InfernoEntity) {
            dark(age, 1.5, 10.0, 0.3f, n);
            bang(age, 1.5, 2.0f, 0.3f, 0xFF8040, n);
            bang(age, 7.5, 4.5f, 0.7f, 0xFFD090, n);
            bang(age, 10.0, 6.5f, 1.0f, 0xFFFFFF, n);
            for (double[] d : e.fx()) {
                if (d[0] == 1 && age == (int) d[1] + 1) {
                    shake = Math.max(shake, 2.5f * n);
                }
            }
        } else if (e instanceof AccelEntity) {
            lockFor(owner, age < SeqEntity.tk(6.0));
            bang(age, 0.8, 1.5f, 0.4f, 0xFFB070, n);
            bang(age, 1.5, 3.0f, 0.5f, 0xFFE0C0, n);
            bang(age, 5.8, 6.0f, 1.0f, 0xFFFFFF, n);
        } else if (e instanceof DominionEntity) {
            dark(age, 1.0, 12.5, 0.25f, n);
            bang(age, 1.0, 2.5f, 0.3f, 0xFF9050, n);
            bang(age, 2.0, 4.0f, 0.6f, 0xFFE0B0, n);
            bang(age, 12.0, 6.0f, 1.0f, 0xFFFFFF, n);
        } else if (e instanceof ParallelEntity) {
            dark(age, 1.5, 10.5, 0.25f, n);
            bang(age, 1.5, 2.0f, 0.3f, 0xFF8040, n);
            bang(age, 3.5, 3.0f, 0.5f, 0xFFD090, n);
            bang(age, 7.5, 4.5f, 0.7f, 0xFFE0B0, n);
            bang(age, 10.0, 6.0f, 1.0f, 0xFFFFFF, n);
        } else if (e instanceof PursuitEntity) {
            dark(age, 1.0, 9.0, 0.25f, n);
            bang(age, 1.0, 2.0f, 0.3f, 0xFF8040, n);
            bang(age, 7.0, 3.0f, 0.4f, 0xFFD090, n);
            bang(age, 8.0, 6.0f, 1.0f, 0xFFFFFF, n);
        } else if (e instanceof StarEntity) {
            dark(age, 1.5, 12.0, 0.4f, n);
            bang(age, 3.5, 1.5f, 0.2f, 0xFF9050, n);
            bang(age, 6.5, 4.5f, 0.7f, 0xFFE0B0, n);
            bang(age, 7.0, 5.5f, 0.9f, 0xFFFFFF, n);
            bang(age, 12.0, 7.5f, 1.0f, 0xFFFFFF, n);
        } else if (e instanceof CthughaEntity) {
            dark(age, 2.0, 22.0, 0.5f, n);
            lockFor(owner, age >= SeqEntity.tk(8.0) && age < SeqEntity.tk(24.0));
            bang(age, 2.0, 3.0f, 0.4f, 0xFF7030, n);
            bang(age, 8.0, 5.0f, 0.8f, 0xFFD090, n);
            bang(age, 10.5, 5.0f, 0.7f, 0xFFFFFF, n);
            bang(age, 14.0, 6.5f, 0.9f, 0xFFFFFF, n);
            bang(age, 20.5, 9.0f, 1.0f, 0xFFFFFF, n);
        }
    }
}
