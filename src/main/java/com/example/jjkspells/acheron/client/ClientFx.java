package com.example.jjkspells.acheron.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Client-only state driven by the controller entities: input lock, the drained (monochrome) world, darkening, flashes, shake and the
 * animations of the caster. Camera safety (config cameraMode) only scales or removes what is drawn on this client: 0 = full,
 * 1 = reduced (weaker, no full-screen flashes), 2 = minimal (nothing). The server never depends on any of it.
 */
public final class ClientFx {
    public static boolean locked, noJump;
    /** Entity ids not drawn this frame. */
    public static java.util.Set<Integer> hide = new java.util.HashSet<>();
    public static float darkness, flash, shake, fov, mono, crack, roll, pullYaw, pullPitch;
    public static int flashColor = 0xE8E8F0, darkColor = 0x05060A;
    private static float wantFov, wantDark, wantMono, wantPullYaw, wantPullPitch;
    private static boolean wantLock, shaderOn;

    private ClientFx() {
    }

    /** 1 = full, 0.4 = reduced, 0 = minimal. */
    public static float cm() {
        return switch (AcheronConfig.G.cameraMode.get()) {
            case 0 -> 1f;
            case 1 -> 0.4f;
            default -> 0f;
        };
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
        crack *= 0.955f;
        if (crack < 0.02f) {
            crack = 0f;
        }
        roll *= 0.8f;
        pullYaw += (wantPullYaw - pullYaw) * 0.15f;
        pullPitch += (wantPullPitch - pullPitch) * 0.15f;
        wantPullYaw = 0f;
        wantPullPitch = 0f;
        mono += (wantMono - mono) * 0.25f;
        wantMono = 0f;
        if (mono < 0.01f) {
            mono = 0f;
        }
        locked = wantLock;
        wantLock = false;
        noJump = locked;
        if (mc.level == null || mc.player == null) {
            darkness = 0f;
            fov = 0f;
            mono = 0f;
            locked = false;
            noJump = false;
        } else if (noJump) {
            mc.player.setSprinting(false);
        }
        boolean wantShader = mono > 0.3f && AcheronConfig.G.monochromeShader.get();
        if (wantShader && !shaderOn) {
            setShader(true);
        } else if (!wantShader && shaderOn && mono < 0.12f) {
            setShader(false);
        }
    }

    private static void setShader(boolean on) {
        shaderOn = on;
        try {
            GameRenderer gr = Minecraft.getInstance().gameRenderer;
            if (on) {
                gr.loadEffect(new ResourceLocation(JJKSpells.MODID, "shaders/post/ach_mono.json"));
            } else {
                gr.shutdownEffect();
            }
        } catch (Exception ignored) {
            // post-processing unavailable (some shader packs): the grey overlay still shows the essential look
        }
    }

    /** True when the black-and-white post chain is the one in use (the overlay then stays light). */
    public static boolean shaderActive() {
        return shaderOn && AcheronConfig.G.monochromeShader.get();
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

    private static void lockFor(Entity owner, boolean cond) {
        if (cond && owner != null && owner == Minecraft.getInstance().player) {
            wantLock = true;
        }
    }

    private static void bang(int age, double sec, float sh, float fl, int color, float n) {
        if (age == SeqEntity.tk(sec)) {
            shake = Math.max(shake, sh * n * cm());
            if (fl > 0 && cm() >= 0.99f) {
                flash = Math.max(flash, fl * n);
                flashColor = color;
            }
        }
    }

    private static void zoom(int age, double a, double b, float amount, float n) {
        if (age >= SeqEntity.tk(a) && age < SeqEntity.tk(b)) {
            wantFov = amount >= 0 ? Math.max(wantFov, amount * n * cm()) : Math.min(wantFov, amount * n * cm());
        }
    }

    private static void dark(int age, double a, double b, float amount, float n) {
        if (age >= SeqEntity.tk(a) && age < SeqEntity.tk(b)) {
            wantDark = Math.max(wantDark, amount * n * cm());
        }
    }

    /** 0..1 ramp in, hold, ramp out over seconds. */
    private static float env(int age, double in0, double in1, double out0, double out1) {
        float t = age / 20f;
        float a = Mth.clamp((t - (float) in0) / (float) Math.max(0.01, in1 - in0), 0f, 1f);
        float b = 1f - Mth.clamp((t - (float) out0) / (float) Math.max(0.01, out1 - out0), 0f, 1f);
        return Math.min(a, b);
    }

    private static void mono(float v) {
        if (cm() > 0f) {
            wantMono = Math.max(wantMono, v);
        }
    }

    public static void tickSeq(SeqEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        float n = near(e, 24, 70);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            if (e instanceof WiltEntity) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            } else {
                anim(ap, "ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
        }
        if (e instanceof WiltEntity) {
            bang(age, 0.45, 0.7f, 0, 0, n);
            bang(age, 0.85, 0.7f, 0, 0, n);
            bang(age, 1.25, 0.7f, 0, 0, n);
            bang(age, 1.7, 1.6f, 0.25f, 0xC9B4FF, n);
        } else if (e instanceof OctoEntity) {
            lockFor(owner, age < SeqEntity.tk(3.3));
            zoom(age, 0.0, 0.35, 0.1f, n);
            bang(age, 0.35, 1.8f, 0.3f, 0xC9B4FF, n);
            bang(age, 2.7, 2.6f, 0.4f, 0xFFD0D8, n);
        } else if (e instanceof QuadEntity) {
            lockFor(owner, age < SeqEntity.tk(3.4));
            for (int i = 0; i < 4; i++) {
                zoom(age, 0.5 + 0.55 * i, 0.7 + 0.55 * i, 0.08f, n);
            }
            dark(age, 2.6, 3.4, 0.2f, n);
            bang(age, 3.0, 3.0f, 0.5f, 0xF2F2F6, n);
        } else if (e instanceof RainleafEntity) {
            lockFor(owner, age < SeqEntity.tk(1.0));
            bang(age, 0.5, 0.8f, 0, 0, n);
        } else if (e instanceof BladeEntity) {
            lockFor(owner, age < SeqEntity.tk(6.2));
            mono(0.35f * env(age, 1.0, 2.0, 5.8, 7.0) * n);
            dark(age, 1.5, 6.2, 0.08f, n);
            bang(age, 2.2, 2.2f, 0.18f, 0xFFB0BC, n);
            bang(age, 3.8, 2.8f, 0.22f, 0xFFB0BC, n);
            bang(age, 5.4, 4.2f, 0.4f, 0xFFD6DC, n);
        } else if (e instanceof StygianEntity) {
            lockFor(owner, age < SeqEntity.tk(5.5));
            mono(env(age, 0.5, 3.2, 6.0, 7.8) * n);
            dark(age, 1.0, 4.2, 0.2f, n);
            zoom(age, 3.0, 4.2, 0.1f, n);
            bang(age, 4.2, 6.0f, 0.8f, 0xFFFFFF, n);
            bang(age, 4.6, 4.0f, 0.3f, 0xFFB0BC, n);
        } else if (e instanceof UltimateEntity ue) {
            float nn = near(e, 60, 110);
            boolean me = owner == Minecraft.getInstance().player;
            lockFor(owner, age < SeqEntity.tk(16.5));
            mono(env(age, 0.0, 2.0, 15.5, 18.5) * nn);
            dark(age, 1.0, UltimateEntity.FINAL_AT, 0.16f, nn);
            zoom(age, 0.0, 2.0, 0.06f, nn);
            // the pull: the world shakes harder as the black hole takes things in
            double t = age / 20.0;
            if (t >= UltimateEntity.OPEN && t < UltimateEntity.SLASH_AT) {
                float k = (float) ((t - UltimateEntity.OPEN) / (UltimateEntity.SLASH_AT - UltimateEntity.OPEN));
                shake = Math.max(shake, (0.3f + 1.6f * k) * nn * cm());
                zoom(age, UltimateEntity.OPEN, UltimateEntity.SLASH_AT, -0.07f * k, nn);
            }
            // anyone else standing in it has the view dragged towards the hole, the caster gets a kick and a roll with every slash
            Minecraft mc = Minecraft.getInstance();
            if (!me && mc.player != null && t >= UltimateEntity.OPEN && t < UltimateEntity.FINAL_AT + 0.4
                    && mc.player.distanceToSqr(e.getX(), e.getY() + UltimateEntity.HOLE_Y, e.getZ()) < 95.0 * 95.0) {
                var eye = mc.player.getEyePosition();
                double dx = e.getX() - eye.x, dy = e.getY() + UltimateEntity.HOLE_Y - eye.y, dz = e.getZ() - eye.z;
                float yawTo = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float pitchTo = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                float dyaw = Mth.wrapDegrees(yawTo - mc.player.getYRot()), dpit = pitchTo - mc.player.getXRot();
                float k = (float) Math.min(1.0, (t - UltimateEntity.OPEN) / 2.5) * cm() * 0.45f;
                wantPullYaw = Mth.clamp(dyaw * k, -40f, 40f);
                wantPullPitch = Mth.clamp(dpit * k, -25f, 25f);
            }
            for (int i = 0; i < UltimateEntity.SLASHES; i++) {
                double w = UltimateEntity.SLASH_AT + UltimateEntity.SLASH_GAP * i;
                if (age == SeqEntity.tk(w)) {
                    shake = Math.max(shake, 4.5f * nn * cm());
                    wantFov = Math.max(wantFov, 0.1f * nn * cm());
                    fov = Math.max(fov, 0.1f * nn * cm());
                    if (me || nn > 0.6f) {
                        roll = (i % 2 == 0 ? 1f : -1f) * 3.0f * nn * cm();
                    }
                    if (cm() >= 0.99f) {
                        flash = Math.max(flash, 0.16f * nn);
                        flashColor = 0xFFB0BC;
                    }
                }
            }
            zoom(age, 11.3, UltimateEntity.FINAL_AT, 0.14f, nn);
            if (age == SeqEntity.tk(UltimateEntity.FINAL_AT)) {
                shake = Math.max(shake, 13.0f * nn * cm());
                crack = Math.max(crack, 1.0f * nn * cm());
                if (cm() >= 0.99f) {
                    flash = Math.max(flash, 1.0f * nn);
                    flashColor = 0xFFFFFF;
                }
            }
            bang(age, UltimateEntity.FINAL_AT + 0.5, 6.0f, 0.4f, 0xFFB0BC, nn);
        }
    }
}
