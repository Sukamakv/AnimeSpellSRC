package com.example.jjkspells.pantheon.client;

import com.example.jjkspells.pantheon.entity.AegisEntity;
import com.example.jjkspells.pantheon.entity.GrandEntity;
import com.example.jjkspells.pantheon.entity.StarfallEntity;
import com.example.jjkspells.pantheon.entity.VaultEntity;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Client-only state driven by the controller entities: input lock, no jumping or sprinting during the Aegis, screen darkening,
 * flashes, camera shake and the animations of the caster.
 */
public final class ClientFx {
    public static boolean locked, noJump;
    public static float darkness, flash, shake;
    public static int flashColor = 0xFFE9A8;
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

    // ---------------------------------------------------------------- Shield Vault

    public static void tickVault(VaultEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        int bash = e.getBashAt();
        boolean over = bash >= 0 && age > bash + 6;
        if (owner != null && owner == mc.player && !over) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
            }
            if (bash >= 0 && age == bash + 4) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        float n = near(e, 10, 30);
        if (e.getCollisionAt() == age) {
            shake = Math.max(shake, 0.9f * n);
            flash = Math.max(flash, 0.25f * n);
            flashColor = 0xFFE9A8;
        }
        if (bash >= 0 && age == bash) {
            shake = Math.max(shake, 1.3f * n);
            flash = Math.max(flash, 0.35f * n);
        }
    }

    // ---------------------------------------------------------------- Aegis Storm

    public static void tickAegis(AegisEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        int fin = e.getDetonationAt();
        if (owner != null && owner == mc.player && fin < 0) {
            wantNoJump = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
            }
            if (fin >= 0 && age == fin + 4) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        float n = near(e, 10, 30);
        if (fin >= 0 && age == fin) {
            shake = Math.max(shake, 1.5f * n);
            flash = Math.max(flash, 0.4f * n);
            flashColor = 0xFFE9A8;
        }
    }

    // ---------------------------------------------------------------- Starfall Execution

    public static void tickStarfall(StarfallEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        int hit = e.getA() + e.getS() + e.getD();
        if (owner != null && owner == mc.player && age < hit + StarfallEntity.FREE_AFTER) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == hit) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        float n = near(e, 15, 45);
        if (age == hit) {
            shake = Math.max(shake, 2.4f * n);
            flash = Math.max(flash, 0.9f * n);
            flashColor = 0xFFE9A8;
        }
        if (age == e.getA()) {
            flash = Math.max(flash, 0.25f * n);
        }
    }

    // ---------------------------------------------------------------- Grand Starfall

    public static void tickGrand(GrandEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        if (owner != null && owner == mc.player && age < GrandEntity.T_END) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 2) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == GrandEntity.T_UP) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
            if (age == GrandEntity.T_IMPACT + 12) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (age == GrandEntity.T_RAISE + 14) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
            if (age == GrandEntity.T_DRIVE + 2) {
                anim(ap, "ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
            }
            if (age == GrandEntity.T_FINAL + 6) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        if (mc.player == null) {
            return;
        }
        float n = near(e, 30, 70);
        // "the surrounding area briefly darkens" during the mortal's defiance
        float d = 0f;
        if (age >= 4 && age < GrandEntity.T_P3) {
            d = 0.42f * Mth.clamp((age - 4) / 10f, 0f, 1f) * (1f - Mth.clamp((age - 40) / 20f, 0f, 1f));
        } else if (age >= GrandEntity.T_P3 && age < GrandEntity.T_IMPACT) {
            d = 0.16f;
        }
        wantDark = Math.max(wantDark, d * n);
        if (age == GrandEntity.T_THRUST) {
            flash = Math.max(flash, 0.5f * n);
            flashColor = 0xFFE9A8;
        }
        if (age == GrandEntity.T_HIDE) {
            flash = Math.max(flash, 1.0f * n);
            flashColor = 0xFFF4D0;
        }
        if (age == GrandEntity.T_IMPACT) {
            flash = Math.max(flash, 1.0f * n);
            flashColor = 0xFFD070;
            shake = Math.max(shake, 3.4f * n);
        }
        if (age > GrandEntity.T_IMPACT && age < GrandEntity.T_IMPACT + 24) {
            shake = Math.max(shake, 1.4f * n * (1f - (age - GrandEntity.T_IMPACT) / 24f));
        }
        for (int i = 0; i < e.getSpears(); i++) {
            if (age == GrandEntity.slamTime(i)) {
                shake = Math.max(shake, 1.0f * n);
            }
        }
        if (age == GrandEntity.T_WAVE) {
            shake = Math.max(shake, 1.6f * n);
        }
        if (age == GrandEntity.T_DRIVE) {
            shake = Math.max(shake, 1.2f * n);
            flash = Math.max(flash, 0.4f * n);
        }
        if (age == GrandEntity.T_FINAL) {
            flash = Math.max(flash, 1.0f * n);
            flashColor = 0xFFE9A8;
            shake = Math.max(shake, 3.0f * n);
        }
    }
}
