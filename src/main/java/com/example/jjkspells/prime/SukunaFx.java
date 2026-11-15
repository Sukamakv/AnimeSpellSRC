package com.example.jjkspells.prime;

import com.example.jjkspells.ModEffects;
import com.example.jjkspells.prime.fx.PrimeFxEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared helpers of the PRIME Sukuna spells. */
public final class SukunaFx {
    private SukunaFx() {
    }

    /** Palette: 0 cosmic, 1 red, 2 white. */
    public static void blast(ServerLevel l, Vec3 at, float size, int palette) {
        PrimeFxEntity.spawn(l, PrimeFxEntity.BLAST, at, null, null, size, 26, 0f, palette, -1);
    }

    public static void text(ServerLevel l, Vec3 at, float scale) {
        PrimeFxEntity.spawn(l, PrimeFxEntity.TEXT, at, null, null, scale, 30, 0f, 0f, -1);
    }

    public static void dismantle(LivingEntity target, LivingEntity caster, int seconds) {
        if (!target.isAlive()) return;
        target.addEffect(new MobEffectInstance(ModEffects.DISMANTLED.get(), seconds * 20, 0, false, true, true), caster);
    }

    /**
     * Is {@code rel} (a point relative to the centre of a crescent) inside the blade? The crescent faces {@code look}; its tall axis is
     * tilted by {@code tilt} from vertical; {@code size} is its height. Mirrors slash.png, so what you see is what hits.
     */
    public static boolean inBlade(Vec3 rel, Vec3 r, Vec3 u, double tilt, double size, double pad) {
        double x = rel.dot(r), y = rel.dot(u);
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        double along = x * st + y * ct;
        double perp = x * ct - y * st;
        double k = size / 20.0;
        double t = along / (10.0 * k);
        if (Math.abs(t) > 1.0 + pad / (10.0 * k)) return false;
        double tt = Math.min(1.0, t * t);
        double bulge = 5.7 * k * (1 - tt);
        double halfT = (1.5 + 0.5 * Math.sqrt(1 - tt)) * k + pad;
        return Math.abs(perp - bulge) <= halfT;
    }

    public static Vec3 rightOf(Vec3 look) {
        Vec3 r = look.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0);
        return r.normalize();
    }

    public static Vec3 upOf(Vec3 look, Vec3 r) {
        return r.cross(look).normalize();
    }

    /** Does the blade at {@code centre} touch this creature? Three points of its body are tried (feet, middle, head). */
    public static boolean touches(LivingEntity e, Vec3 centre, Vec3 look, Vec3 r, Vec3 u, double tilt, double size, double pad) {
        double w = e.getBbWidth() * 0.5;
        double h = e.getBbHeight();
        Vec3 p = e.position();
        for (double f : new double[]{0.5, 0.15, 0.9}) {
            Vec3 rel = new Vec3(p.x, p.y + h * f, p.z).subtract(centre);
            if (Math.abs(rel.dot(look)) > 1.3 + w) return false;
            if (inBlade(rel, r, u, tilt, size, pad + w)) return true;
        }
        return false;
    }
}
