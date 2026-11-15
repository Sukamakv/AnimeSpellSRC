package com.example.jjkspells.rimuru.client;

import com.example.jjkspells.rimuru.entity.AzathothEntity;
import com.example.jjkspells.rimuru.entity.BlackLightningEntity;
import com.example.jjkspells.rimuru.entity.CollapseEntity;
import com.example.jjkspells.rimuru.entity.DefenseEntity;
import com.example.jjkspells.rimuru.entity.DevourBase;
import com.example.jjkspells.rimuru.entity.MegiddoEntity;
import com.example.jjkspells.rimuru.entity.SpatialEntity;
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

    // ---------------------------------------------------------------- Predator and Beelzebuth

    public static void tickDevour(DevourBase e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < e.tEnd);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e, 14, 40);
        if (age == e.tCapture) {
            shake = Math.max(shake, 0.8f * n);
            flash = Math.max(flash, 0.15f * n);
            flashColor = 0x4A70FF;
        }
        if (age == e.tFinal) {
            shake = Math.max(shake, 2.0f * n);
            flash = Math.max(flash, 0.4f * n);
            flashColor = 0x6080FF;
        }
        if (age == e.tEnd - 2 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
    }

    // ---------------------------------------------------------------- Black Lightning

    public static void tickLightning(BlackLightningEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < BlackLightningEntity.END);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e, 16, 50);
        if (age == BlackLightningEntity.RELEASE) {
            shake = Math.max(shake, 1.4f * n);
            flash = Math.max(flash, 0.3f * n);
            flashColor = 0x9A60FF;
            if (owner instanceof AbstractClientPlayer ap) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
        }
        if (age >= BlackLightningEntity.CHAIN0 && age < BlackLightningEntity.BLAST && age % 2 == 0) {
            shake = Math.max(shake, 0.4f * n);
        }
        if (age == BlackLightningEntity.BLAST) {
            shake = Math.max(shake, 2.2f * n);
            flash = Math.max(flash, 0.4f * n);
            flashColor = 0xB890FF;
        }
        if (age == BlackLightningEntity.END - 2 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
    }

    // ---------------------------------------------------------------- Megiddo

    public static void tickMegiddo(MegiddoEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < MegiddoEntity.END);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e, 25, 60);
        if (age >= MegiddoEntity.BARRAGE && age < MegiddoEntity.FOCUS_LENS && age % 2 == 0) {
            shake = Math.max(shake, 0.35f * n);
        }
        if (age == MegiddoEntity.FOCUS) {
            shake = Math.max(shake, 1.6f * n);
            flash = Math.max(flash, 0.3f * n);
            flashColor = 0xFFF0C0;
        }
        if (age == MegiddoEntity.RAIN_HIT) {
            shake = Math.max(shake, 2.4f * n);
            flash = Math.max(flash, 0.5f * n);
            flashColor = 0xF0F8FF;
        }
        if (age == MegiddoEntity.END - 2 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
    }

    // ---------------------------------------------------------------- Absolute Defense

    public static void tickDefense(DefenseEntity e) {
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
            // no screen flash: the barrier must never blind its own caster
        }
    }

    // ---------------------------------------------------------------- Spatial Domination

    public static void tickSpatial(SpatialEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < SpatialEntity.END);
        float n = near(e, 20, 50);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        if (age == SpatialEntity.APPEAR) {
            flash = Math.max(flash, 0.25f * n);
            flashColor = 0x30C0F0;
        }
        if (age == SpatialEntity.SLASH) {
            shake = Math.max(shake, 1.4f * n);
            if (owner instanceof AbstractClientPlayer ap) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
        }
        if (age >= SpatialEntity.CRACK0 && age < SpatialEntity.CRACK0 + 16 && (age - SpatialEntity.CRACK0) % 4 == 0) {
            shake = Math.max(shake, 0.7f * n);
        }
        if (age == SpatialEntity.RUPTURE) {
            shake = Math.max(shake, 2.2f * n);
            flash = Math.max(flash, 0.45f * n);
            flashColor = 0x60A0FF;
        }
        if (age == SpatialEntity.RUPTURE + 3 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
    }

    // ---------------------------------------------------------------- Imaginary Collapse

    public static void tickCollapse(CollapseEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < CollapseEntity.CASTER_END);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e, 30, 70);
        if (age == CollapseEntity.RELEASE && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
        }
        if (age == CollapseEntity.CASTER_END && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
        if (age == CollapseEntity.IMPACT) {
            shake = Math.max(shake, 2.4f * n);
            flash = Math.max(flash, 0.4f * n);
            flashColor = 0x7050FF;
        }
        if (age >= CollapseEntity.PULL0 && age < CollapseEntity.BLAST2) {
            shake = Math.max(shake, 0.45f * n);
            darkness = Math.max(darkness, 0f);
        }
        if (age == CollapseEntity.BLAST2) {
            shake = Math.max(shake, 3.0f * n);
            flash = Math.max(flash, 0.5f * n);
            flashColor = 0x9070FF;
        }
        if (age == CollapseEntity.RUPTURE) {
            shake = Math.max(shake, 3.4f * n);
            flash = Math.max(flash, 0.6f * n);
            flashColor = 0xC0B0FF;
        }
    }

    // ---------------------------------------------------------------- Void God Azathoth

    public static void tickAzathoth(AzathothEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < AzathothEntity.END);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e, 40, 90);
        if (age >= AzathothEntity.SUN && age < AzathothEntity.BEAM) {
            wantDark = Math.max(wantDark, 0.35f * smooth01((age - AzathothEntity.SUN) / 20f) * n);
        }
        if (age >= AzathothEntity.BEAM && age < AzathothEntity.COMPRESS) {
            wantDark = Math.max(wantDark, 0.4f * n);
        }
        if (age == AzathothEntity.BEAM_HIT) {
            shake = Math.max(shake, 2.4f * n);
            flash = Math.max(flash, 0.4f * n);
            flashColor = 0xA070FF;
        }
        if (age >= AzathothEntity.DESCEND && age < AzathothEntity.FALL) {
            shake = Math.max(shake, 0.5f * n);
        }
        if (age == AzathothEntity.FALL) {
            shake = Math.max(shake, 3.5f * n);
            flash = Math.max(flash, 0.6f * n);
            flashColor = 0x8060FF;
        }
        if (age == AzathothEntity.WAVE) {
            shake = Math.max(shake, 3.0f * n);
            flash = Math.max(flash, 0.5f * n);
            flashColor = 0x6050FF;
        }
        if (age == AzathothEntity.FINAL) {
            shake = Math.max(shake, 4.0f * n);
            flash = Math.max(flash, 0.9f * n);
            flashColor = 0xE8E0FF;
        }
        if (age == AzathothEntity.END - 2 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
    }

    private static float smooth01(float v) {
        v = Mth.clamp(v, 0f, 1f);
        return v * v * (3f - 2f * v);
    }
}
