package com.example.jjkspells.yhwach.entity;

import net.minecraft.world.phys.Vec3;

/**
 * Orthonormal frame of the Quincy bow: {@code f} points along the aim, {@code u} is the (vertical) bow axis, {@code r} the
 * side axis. The bow limbs live in the f/u plane, so from the side it reads like the crescent in the anime.
 */
public final class BowFrame {
    public final Vec3 f;
    public final Vec3 u;
    public final Vec3 r;
    public final Vec3 center;

    private BowFrame(Vec3 f, Vec3 center) {
        this(f, center, new Vec3(0, 1, 0));
    }

    private BowFrame(Vec3 f, Vec3 center, Vec3 hint) {
        Vec3 uu = hint.subtract(f.scale(hint.dot(f)));
        if (uu.lengthSqr() < 1.0E-4) {
            uu = new Vec3(1, 0, 0).subtract(f.scale(f.x));
        }
        this.f = f;
        this.u = uu.normalize();
        this.r = f.cross(this.u).normalize();
        this.center = center;
    }

    /** Free-standing bow aimed along {@code dir}, limbs running along {@code limbHint} (used by the Qual Kreis bow ring). */
    public static BowFrame oriented(Vec3 center, Vec3 dir, Vec3 limbHint) {
        Vec3 f = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : dir.normalize();
        return new BowFrame(f, center, limbHint);
    }

    /** Charging pose: bow floats in front of the caster, slightly above head height. */
    public static BowFrame charging(Vec3 casterPos, Vec3 look) {
        Vec3 f = look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 c = casterPos.add(0, PfeilEntity.BOW_HEIGHT, 0).add(f.scale(PfeilEntity.BOW_FORWARD));
        return new BowFrame(f, c);
    }

    /** Pose frozen at the moment of release. */
    public static BowFrame fired(Vec3 origin, Vec3 impact) {
        Vec3 d = impact.subtract(origin);
        Vec3 f = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        return new BowFrame(f, origin);
    }

    /** Point on the bow curve. {@code t} in [-1, 1] runs from the lower to the upper tip. */
    public Vec3 bowPoint(double t, double scale) {
        double at = Math.abs(t);
        double along = (-0.95 * t * t + 0.45 * at * at * at * at * at) * scale;
        return center.add(u.scale(3.0 * t * scale)).add(f.scale(along));
    }

    /** Where the string is pinched by the drawing hand. */
    public Vec3 nock(double draw) {
        return center.add(f.scale(-0.5 - draw));
    }
}
