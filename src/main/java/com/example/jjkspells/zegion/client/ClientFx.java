package com.example.jjkspells.zegion.client;

import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Client-only state driven by the controller entities: input lock, no jumping, screen flashes, the violet veil of the
 * illusion domains, camera shake and the animations of the caster.
 */
public final class ClientFx {
    public static boolean locked, noJump;
    public static float flash, shake, veil;
    public static int flashColor = 0xB070FF, veilColor = 0x14082C;
    private static boolean wantLock, wantNoJump;
    private static float wantVeil;

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
        veil += (wantVeil - veil) * 0.2f;
        wantVeil = 0f;
        if (veil < 0.004f) {
            veil = 0f;
        }
        locked = wantLock;
        wantLock = false;
        noJump = wantNoJump;
        wantNoJump = false;
        if (mc.level == null || mc.player == null) {
            veil = 0f;
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

    private static void stopAnim(AbstractClientPlayer ap) {
        AnimationHelper.cancelPlayerAnimation(ap);
    }

    private static float near(Entity e, double start, double span) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0f;
        }
        double d = Math.sqrt(mc.player.distanceToSqr(e));
        return Mth.clamp(1f - (float) ((d - start) / span), 0f, 1f);
    }

    private static boolean mine(Entity owner) {
        return owner != null && owner == Minecraft.getInstance().player;
    }

    /** Violet veil while the local player stands inside a domain of the given radius around the entity. */
    private static void veilInside(Entity e, double radius, float amount, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double dx = mc.player.getX() - e.getX(), dz = mc.player.getZ() - e.getZ();
        if (dx * dx + dz * dz < radius * radius) {
            wantVeil = Math.max(wantVeil, amount);
            veilColor = color;
        }
    }

    private static void burst(float s, float f, int color, float n) {
        shake = Math.max(shake, s * n);
        if (f > 0f) {
            flash = Math.max(flash, f * n);
            flashColor = color;
        }
    }

    // ---------------------------------------------------------------- Kaiser's Impact

    public static void tickImpact(ImpactEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        if (mine(owner) && age < ImpactEntity.END - 12) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == ImpactEntity.PUNCH + 6) {
                stopAnim(ap);
            }
            if (age == ImpactEntity.PUNCH - 1) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
            if (age == ImpactEntity.COLLAPSE + 8) {
                stopAnim(ap);
            }
        }
        float n = near(e, 14, 50);
        if (age < ImpactEntity.BLINK) {
            shake = Math.max(shake, 0.25f * n * age / ImpactEntity.BLINK);
        }
        if (age == ImpactEntity.PUNCH) {
            burst(2.8f, 0.45f, 0xC090FF, n);
        }
        if (age > ImpactEntity.PUNCH && age < ImpactEntity.SHOCK_END && age % 4 == 0) {
            shake = Math.max(shake, 0.8f * n);
        }
        if (age == ImpactEntity.COLLAPSE) {
            burst(3.4f, 0.55f, 0xA070F0, n);
        }
    }

    // ---------------------------------------------------------------- Mist Lord's Domain

    public static void tickDomain(DomainEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        if (mine(owner) && (age < DomainEntity.DOME || (age >= DomainEntity.FREEZE - 8 && age < DomainEntity.END))) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == DomainEntity.DOME + 4) {
                stopAnim(ap);
            }
            if (age == DomainEntity.FREEZE) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (age == DomainEntity.HIT + 8) {
                stopAnim(ap);
            }
        }
        float R = e.getRadius();
        if (age >= DomainEntity.DOME - 10 && age < DomainEntity.END - 6) {
            veilInside(e, R, 0.38f, 0x1A0838);
        }
        float n = near(e, R * 0.5, R * 1.4);
        if (age == DomainEntity.DOME) {
            burst(1.6f, 0.3f, 0x8050D0, n);
        }
        if (e.getStrikeAge() == age) {
            shake = Math.max(shake, 0.6f * n);
        }
        if (age == DomainEntity.LAUNCH) {
            burst(2.4f, 0.4f, 0xC090FF, n);
        }
        if (age == DomainEntity.HIT) {
            burst(3.6f, 0.7f, 0xE0C8FF, n);
        }
    }

    // ---------------------------------------------------------------- Obsidian Kaiser

    public static void tickObsidian(ObsidianEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        int w = e.getWindupAt();
        int p = e.getPunchAt();
        if (mine(owner) && (age < ObsidianEntity.FORM || (w >= 0 && p < 0))) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == ObsidianEntity.FORM + 4) {
                stopAnim(ap);
            }
            if (w >= 0 && age == w) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (p >= 0 && age == p + 8) {
                stopAnim(ap);
            }
        }
        float n = near(e, 10, 40);
        if (age == ObsidianEntity.FORM) {
            burst(1.4f, 0.25f, 0x9060E0, n);
        }
        if (e.getHitAge() == age) {
            shake = Math.max(shake, 0.5f * n);
        }
        if (p >= 0 && age == p) {
            burst(3.0f, 0.5f, 0xC8A0FF, n);
        }
    }

    // ---------------------------------------------------------------- Phantom Kaiser

    public static void tickAssault(AssaultEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        if (mine(owner) && age < AssaultEntity.END) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == AssaultEntity.VANISH + 2) {
                stopAnim(ap);
            }
            if (age == AssaultEntity.DROP - 1) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
            if (age == AssaultEntity.BLAST + 6) {
                stopAnim(ap);
            }
        }
        float n = near(e, 10, 40);
        if (e.getStrikeAge() == age) {
            shake = Math.max(shake, 0.45f * n);
        }
        if (age == AssaultEntity.DROP) {
            burst(3.0f, 0.5f, 0xD0A8FF, n);
        }
        if (age == AssaultEntity.BLAST) {
            burst(3.4f, 0.65f, 0xB080FF, n);
        }
    }

    // ---------------------------------------------------------------- Mephisto's Labyrinth

    public static void tickLabyrinth(LabyrinthEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        if (mine(owner) && (age < 40 || (age >= LabyrinthEntity.JUDGE - 10 && age < LabyrinthEntity.END))) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == 42) {
                stopAnim(ap);
            }
            if (age == LabyrinthEntity.JUDGE - 10) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (age == LabyrinthEntity.JUDGE + 10) {
                stopAnim(ap);
            }
        }
        float R = e.getRadius();
        if (age >= LabyrinthEntity.CUBE + 20 && age < LabyrinthEntity.JUDGE + 8) {
            veilInside(e, R, 0.42f, 0x10082A);
        }
        float n = near(e, R * 0.5, R * 1.6);
        if (age == LabyrinthEntity.GROW) {
            burst(2.2f, 0.35f, 0x9060E0, n);
        }
        int cnt = ZegConfig.LABYRINTH.waves.get();
        for (int k = 0; k < cnt; k++) {
            if (age == LabyrinthEntity.waveStart(k, cnt)) {
                shake = Math.max(shake, 0.9f * n);
            }
        }
        if (age >= LabyrinthEntity.CONVERGE && age < LabyrinthEntity.JUDGE) {
            shake = Math.max(shake, 0.5f * n);
        }
        if (age == LabyrinthEntity.JUDGE) {
            burst(4.0f, 0.8f, 0xE8D0FF, n);
        }
    }

    // ---------------------------------------------------------------- Void Carapace

    public static void tickSingularity(SingularityEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        if (mine(owner) && age < SingularityEntity.IMPACT - 20) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == SingularityEntity.THROW) {
                anim(ap, "SLASH_ANIMATION", "ANIMATION_INSTANT_CAST");
            }
            if (age == SingularityEntity.THROW + 10) {
                stopAnim(ap);
            }
            if (age == SingularityEntity.HIT) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (age == SingularityEntity.HIT + 10) {
                stopAnim(ap);
            }
        }
        float n = near(e, 20, 80);
        if (age == SingularityEntity.IMPACT) {
            burst(3.2f, 0.5f, 0x8040D0, n);
        }
        if (age > SingularityEntity.IMPACT && age < SingularityEntity.HIT) {
            shake = Math.max(shake, 0.55f * n);
        }
        if (age == SingularityEntity.HIT) {
            burst(4.0f, 0.85f, 0xE0C0FF, n);
        }
    }

    // ---------------------------------------------------------------- Kaiser's Final Form

    public static void tickSovereign(SovereignEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        int fold = e.getFoldAt();
        if (mine(owner) && (age < SovereignEntity.ACTIVE || (fold >= 0 && age < fold + SovereignEntity.FOLD + 8))) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
            }
            if (age == SovereignEntity.ACTIVE + 2) {
                stopAnim(ap);
            }
            if (fold >= 0 && age == fold) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (fold >= 0 && age == fold + SovereignEntity.FOLD + 10) {
                stopAnim(ap);
            }
        }
        float n = near(e, 14, 50);
        if (age == SovereignEntity.WINGS) {
            burst(1.8f, 0.3f, 0xA070F0, n);
        }
        if (age == SovereignEntity.SHOCK) {
            burst(3.2f, 0.55f, 0xC8A0FF, n);
        }
        if (e.getBlastAge() == age) {
            burst(1.4f, 0.12f, 0xB080FF, n);
        }
        if (fold >= 0 && age == fold + SovereignEntity.FOLD) {
            burst(4.0f, 0.8f, 0xE8D0FF, n);
        }
    }

    // ---------------------------------------------------------------- Illusion King Mephisto

    public static void tickMephisto(MephistoEntity e) {
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        if (mine(owner) && age < MephistoEntity.END) {
            wantLock = true;
        }
        if (owner instanceof AbstractClientPlayer ap) {
            if (age == 1) {
                anim(ap, "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
            }
            if (age == MephistoEntity.END - 6) {
                stopAnim(ap);
            }
        }
        double R = ZegConfig.MEPHISTO.domainRadius.get();
        if (age >= MephistoEntity.DOME - 20 && age < MephistoEntity.END - 10) {
            veilInside(e, R, 0.55f, 0x0C0420);
        }
        float n = near(e, R * 0.6, R * 1.6);
        if (age < MephistoEntity.GIANTS) {
            shake = Math.max(shake, 0.35f * n * Math.min(1f, age / 40f));
        }
        if (age == MephistoEntity.FIRST) {
            burst(3.4f, 0.55f, 0xC090FF, n);
        }
        if (age == MephistoEntity.EXEC) {
            burst(3.8f, 0.6f, 0xB080FF, n);
        }
        if (age >= MephistoEntity.SPHERE && age < MephistoEntity.COLLAPSE) {
            shake = Math.max(shake, 0.6f * n);
        }
        if (age == MephistoEntity.COLLAPSE) {
            burst(4.4f, 0.8f, 0xE0C0FF, n);
        }
        if (age >= MephistoEntity.RISE && age < MephistoEntity.FINAL) {
            shake = Math.max(shake, 0.5f * n);
        }
        if (age == MephistoEntity.FINAL) {
            burst(5.5f, 1.0f, 0xF0E0FF, n);
        }
        if (age > MephistoEntity.FINAL && age < MephistoEntity.FINAL + 24) {
            shake = Math.max(shake, 2.0f * n * (1f - (age - MephistoEntity.FINAL) / 24f));
        }
    }
}
