package com.example.jjkspells.malenia.client;

import com.example.jjkspells.malenia.entity.BladeEntity;
import com.example.jjkspells.malenia.entity.BloomEntity;
import com.example.jjkspells.malenia.entity.GoddessEntity;
import com.example.jjkspells.malenia.entity.PhantomEntity;
import com.example.jjkspells.malenia.entity.WaterfowlEntity;
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

    // ---------------------------------------------------------------- Waterfowl Dance

    public static void tickWaterfowl(WaterfowlEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < WaterfowlEntity.END);
        trailAdd(e.trail, owner, 12);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
        }
        float n = near(e, 14, 40);
        while (e.seen < e.getCount()) {
            e.seen++;
            Vec3 p = e.getSlashPos();
            e.fx.add(new double[]{p.x, p.y, p.z, e.getSlashYaw(), e.getSlashRoll(), age});
            if (owner instanceof AbstractClientPlayer ap) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
            shake = Math.max(shake, 0.35f * n);
        }
        e.fx.removeIf(f -> age - f[5] > 12);
        if (age == WaterfowlEntity.CRES) {
            shake = Math.max(shake, 2.4f * n);
            flash = Math.max(flash, 0.35f * n);
            flashColor = 0xF4F0E0;
            if (owner instanceof AbstractClientPlayer ap) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
    }

    // ---------------------------------------------------------------- Phantom Blossom

    public static void tickPhantom(PhantomEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < PhantomEntity.END);
        trailAdd(e.trail, owner, 14);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e, 14, 40);
        if (age == PhantomEntity.SHOW - 8) {
            flash = Math.max(flash, 0.2f * n);
            flashColor = 0xC02030;
        }
        for (int at : PhantomEntity.AT) {
            if (age == at) {
                shake = Math.max(shake, 0.5f * n);
            }
        }
        if (age == PhantomEntity.DIVE && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
        }
        if (age == PhantomEntity.HIT) {
            shake = Math.max(shake, 2.0f * n);
            flash = Math.max(flash, (e.isPerfect() ? 0.45f : 0.25f) * n);
            flashColor = 0xE03040;
        }
        if (age == PhantomEntity.LAND + 4 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
    }

    // ---------------------------------------------------------------- Scarlet Bloom

    public static void tickBloom(BloomEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < BloomEntity.CASTER_END);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e.getPoint(), 12, 45);
        if (age == BloomEntity.IMPACT) {
            shake = Math.max(shake, 2.6f * n);
            flash = Math.max(flash, 0.4f * n);
            flashColor = 0xE03040;
            if (owner instanceof AbstractClientPlayer ap) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        if (age > BloomEntity.IMPACT && age < BloomEntity.BLOOM1) {
            shake = Math.max(shake, 0.4f * n);
        }
        if (age == BloomEntity.BLOOM1) {
            shake = Math.max(shake, 3.0f * n);
            flash = Math.max(flash, 0.45f * n);
            flashColor = 0xFF5060;
        }
    }

    // ---------------------------------------------------------------- Miquella's Blade

    public static void tickBlade(BladeEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < BladeEntity.END);
        trailAdd(e.trail, owner, 12);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
        }
        float n = near(e, 14, 40);
        while (e.seen < e.getCount()) {
            e.seen++;
            Vec3 p = e.getSlashPos();
            e.fx.add(new double[]{p.x, p.y, p.z, e.getSlashYaw(), e.getKind(), age});
            if (owner instanceof AbstractClientPlayer ap) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
            shake = Math.max(shake, (e.getKind() == 5 ? 2.2f : 0.5f) * n);
            if (e.getKind() == 5) {
                flash = Math.max(flash, 0.3f * n);
                flashColor = 0xF4E8C0;
            }
        }
        e.fx.removeIf(f -> age - f[5] > 14);
        if (age == BladeEntity.EXEC + 6 && owner instanceof AbstractClientPlayer ap) {
            AnimationHelper.cancelPlayerAnimation(ap);
        }
    }

    // ---------------------------------------------------------------- Goddess of Rot

    public static void tickGoddess(GoddessEntity e) {
        int age = e.tickCount - 1;
        Minecraft mc = Minecraft.getInstance();
        Entity owner = e.getOwnerEntity();
        lockFor(owner, age < GoddessEntity.T_END);
        if (age == 1 && owner instanceof AbstractClientPlayer ap) {
            anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        }
        float n = near(e.getPoint(), 25, 70);
        float nc = near(e, 20, 60);
        if (age < GoddessEntity.T_WINGS) {
            shake = Math.max(shake, 0.3f * nc * age / GoddessEntity.T_WINGS);
        }
        if (age == GoddessEntity.T_WINGS) {
            flash = Math.max(flash, 0.9f * nc);
            flashColor = 0xFF2030;
            shake = Math.max(shake, 1.6f * nc);
        }
        if (age >= GoddessEntity.T_RISE && age < GoddessEntity.T_IMPACT && owner == mc.player) {
            wantDark = Math.max(wantDark, 0.3f);
        }
        if (age == GoddessEntity.T_IMPACT) {
            shake = Math.max(shake, 4.0f * n);
            flash = Math.max(flash, 0.8f * n);
            flashColor = 0xFF4050;
            if (owner instanceof AbstractClientPlayer ap) {
                AnimationHelper.cancelPlayerAnimation(ap);
            }
        }
        if (age > GoddessEntity.T_IMPACT && age < GoddessEntity.T_BLOOM) {
            shake = Math.max(shake, 0.8f * n);
        }
        if (age == GoddessEntity.T_BLOOM) {
            shake = Math.max(shake, 3.6f * n);
            flash = Math.max(flash, 0.6f * n);
            flashColor = 0xFF4050;
        }
        for (int k = 0; k < GoddessEntity.SWORDS; k++) {
            if (age == GoddessEntity.T_SWORD0 + k * GoddessEntity.SWORD_GAP) {
                shake = Math.max(shake, 1.3f * n);
            }
        }
        if (age == GoddessEntity.T_JUDG) {
            shake = Math.max(shake, 3.8f * n);
            flash = Math.max(flash, 0.7f * n);
            flashColor = 0xFFE0A0;
        }
        if (age > GoddessEntity.T_JUDG && age < GoddessEntity.T_FINAL) {
            shake = Math.max(shake, 0.4f * n + 1.0f * n * (age - GoddessEntity.T_JUDG) / (GoddessEntity.T_FINAL - GoddessEntity.T_JUDG));
        }
        if (age == GoddessEntity.T_FINAL) {
            shake = Math.max(shake, 5.0f * n);
            flash = Math.max(flash, 1.0f * n);
            flashColor = 0xFF8090;
        }
    }

    private static float near(Vec3 p, double start, double span) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0f;
        }
        double d = Math.sqrt(mc.player.distanceToSqr(p));
        return Mth.clamp(1f - (float) ((d - start) / span), 0f, 1f);
    }
}
