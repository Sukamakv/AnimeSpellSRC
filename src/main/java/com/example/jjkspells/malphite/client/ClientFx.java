package com.example.jjkspells.malphite.client;

import com.example.jjkspells.malphite.entity.ClapEntity;
import com.example.jjkspells.malphite.entity.ForceEntity;
import com.example.jjkspells.malphite.entity.GraniteEntity;
import com.example.jjkspells.malphite.entity.ShardEntity;
import com.example.jjkspells.malphite.entity.StrikesEntity;
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
    public static int flashColor = 0xE0B070;
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

    // ---------------------------------------------------------------- Clap

    public static void tickClap(ClapEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        if (owner != null && owner == mc.player && age < ClapEntity.T_END) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == ClapEntity.T1 + 4 || age == ClapEntity.T2 + 4) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
            if (age == ClapEntity.W1) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
        }
        float n = near(e, 12, 40);
        if (age < ClapEntity.T1) {
            shake = Math.max(shake, 0.25f * n * age / ClapEntity.T1);
        }
        if (age == ClapEntity.T1) {
            shake = Math.max(shake, 1.6f * n);
        }
        if (age >= ClapEntity.W0 && age <= ClapEntity.W1 && age % 4 == 0) {
            shake = Math.max(shake, 0.7f * n);
        }
        if (age == ClapEntity.T2) {
            shake = Math.max(shake, 2.2f * n);
            flash = Math.max(flash, 0.18f * n);
            flashColor = 0xE0B070;
        }
        int ez = e.getEruptAge();
        if (ez >= 0 && age == ez) {
            shake = Math.max(shake, 0.6f * n);
        }
    }

    // ---------------------------------------------------------------- Strikes

    public static void tickStrikes(StrikesEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        if (owner != null && owner == mc.player && age < StrikesEntity.END) {
            wantNoJump = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == StrikesEntity.S1 - 1 || age == StrikesEntity.S2 - 1) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
            if (age == StrikesEntity.S3 - 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
            }
            if (age == StrikesEntity.S3 + 6) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (age == StrikesEntity.SM + 6) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        float n = near(e, 10, 30);
        if (age == StrikesEntity.S1 || age == StrikesEntity.S2) {
            shake = Math.max(shake, 0.8f * n);
        }
        if (age == StrikesEntity.S3) {
            shake = Math.max(shake, 1.3f * n);
        }
        if (age == StrikesEntity.SM) {
            shake = Math.max(shake, 2.4f * n);
            flash = Math.max(flash, 0.2f * n);
            flashColor = 0xE0B070;
        }
    }

    // ---------------------------------------------------------------- Granite Shield

    public static void tickGranite(GraniteEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        int form = e.getFormTicks();
        if (owner != null && owner == mc.player && age < form) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == form + 4) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
            int fin = e.getDetonationAt();
            if (fin >= 0 && age == fin) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (fin >= 0 && age == fin + 8) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        float n = near(e, 10, 30);
        if (age == form) {
            shake = Math.max(shake, 1.2f * n);
        }
        int fin = e.getDetonationAt();
        if (fin >= 0 && age == fin) {
            shake = Math.max(shake, 2.0f * n);
            flash = Math.max(flash, 0.3f * n);
            flashColor = 0xE0B070;
        }
    }

    // ---------------------------------------------------------------- Unstoppable Force

    public static void tickForce(ForceEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        if (owner != null && owner == mc.player && age < ForceEntity.T_END) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == ForceEntity.T_IMPACT) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
            if (age == ForceEntity.T_RISE) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (age == ForceEntity.T_JUDG + 4) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        if (mc.player == null) {
            return;
        }
        float n = near(e, 30, 70);
        if (age < ForceEntity.T_CHARGE) {
            shake = Math.max(shake, 0.4f * n * Math.min(1f, age / 20f));
        }
        if (age >= ForceEntity.T_CHARGE && age < ForceEntity.T_IMPACT) {
            shake = Math.max(shake, 0.9f * n);
        }
        if (age == ForceEntity.T_IMPACT) {
            shake = Math.max(shake, 3.6f * n);
            flash = Math.max(flash, 0.8f * n);
            flashColor = 0xE8B878;
        }
        if (age > ForceEntity.T_IMPACT && age < ForceEntity.T_IMPACT + 20) {
            shake = Math.max(shake, 1.6f * n * (1f - (age - ForceEntity.T_IMPACT) / 20f));
        }
        for (int k = 0; k < ForceEntity.WAVES; k++) {
            if (age == ForceEntity.T_Q0 + k * ForceEntity.WAVE_GAP + ForceEntity.WAVE_LIFE) {
                shake = Math.max(shake, 1.3f * n);
            }
        }
        if (age >= ForceEntity.T_RISE && age < ForceEntity.T_JUDG) {
            shake = Math.max(shake, 0.5f * n);
        }
        if (age == ForceEntity.T_JUDG) {
            shake = Math.max(shake, 3.2f * n);
            flash = Math.max(flash, 0.7f * n);
            flashColor = 0xE8B878;
        }
        int ez = e.getEruptAge();
        if (ez >= 0 && age == ez) {
            shake = Math.max(shake, 0.8f * n);
        }
    }
}
