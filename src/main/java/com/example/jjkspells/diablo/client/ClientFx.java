package com.example.jjkspells.diablo.client;

import com.example.jjkspells.diablo.entity.*;
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
            anim(ap, e instanceof DimensionalFuneralEntity ? new String[]{"SLASH_ANIMATION", "CHARGE_RAISED_HAND"} : new String[]{"CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST"});
        }
        if (e instanceof NetherApocalypseEntity) {
            dark(age, 0.8, 3.5, 0.3f, n);
            bang(age, 3.0, 0.8f, 0f, 0, n);
            bang(age, 3.5, 2.0f, 0.5f, 0xFF3010, n);
            bang(age, 4.5, 3.0f, 0.6f, 0xFF5020, n);
            bang(age, 5.8, 2.5f, 0.35f, 0xB01020, n);
        } else if (e instanceof PrimordialDominionEntity) {
            dark(age, 0.0, 2.0, 0.5f, n);
            dark(age, 2.0, 7.0, 0.15f, n);
            bang(age, 2.0, 3.0f, 0.4f, 0x300010, n);
            bang(age, 4.5, 1.5f, 0f, 0, n);
            bang(age, 5.8, 3.5f, 0.5f, 0xA00018, n);
        } else if (e instanceof DimensionalFuneralEntity) {
            bang(age, 0.7, 1.2f, 0f, 0, n);
            bang(age, 1.8, 2.0f, 0.5f, 0xFFE0E0, n);
            bang(age, 4.5, 1.0f, 0f, 0, n);
            bang(age, 5.8, 3.5f, 0.6f, 0xFF2030, n);
        } else if (e instanceof DespairTimeEntity) {
            dark(age, 1.0, 7.5, 0.25f, n);
            bang(age, 1.0, 2.0f, 0.3f, 0x600030, n);
            bang(age, 5.5, 1.5f, 0.3f, 0xFFFFFF, n);
            bang(age, 7.5, 3.5f, 0.5f, 0x300050, n);
        } else if (e instanceof TemptationWorldEntity) {
            dark(age, 1.0, 10.0, 0.3f, n);
            bang(age, 2.0, 3.0f, 0.45f, 0xC00020, n);
            bang(age, 10.0, 4.0f, 0.55f, 0xFF3030, n);
        } else if (e instanceof AbyssalGenesisEntity) {
            dark(age, 1.5, 8.5, 0.4f, n);
            bang(age, 6.5, 1.5f, 0f, 0, n);
            bang(age, 7.0, 4.0f, 0.6f, 0xFF4030, n);
            bang(age, 8.5, 4.5f, 0.6f, 0xFFFFFF, n);
        } else if (e instanceof TemptationKingAzazelEntity) {
            dark(age, 0.0, 18.0, 0.45f, n);
            lockFor(owner, age >= SeqEntity.tk(10.0));
            bang(age, 6.0, 3.0f, 0.5f, 0xFF3030, n);
            bang(age, 10.0, 3.5f, 0.5f, 0x800020, n);
            bang(age, 12.5, 3.0f, 0.4f, 0x300030, n);
            bang(age, 17.0, 6.0f, 1.0f, 0xFFFFFF, n);
        }
    }

    public static void tickNegation(NegationEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        if (age == 20 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
        float n = near(e, 20, 50);
        if (e.getReleaseAge() >= 0 && age == e.getReleaseAge() + 1) {
            shake = Math.max(shake, 2.0f * n);
            flash = Math.max(flash, 0.35f * n);
            flashColor = 0xFF6060;
        }
    }
}
