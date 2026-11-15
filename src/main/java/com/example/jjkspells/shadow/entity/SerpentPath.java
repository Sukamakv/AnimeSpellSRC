package com.example.jjkspells.shadow.entity;

/**
 * The route of the Black Serpent's head over time (seconds). Pure maths, so the server (hit detection) and the client (drawing)
 * agree. The body is not simulated: it is the same route walked backwards, which is what makes it look like one animal.
 *
 * 0-1 a pool of slime, 1-2.5 the serpent spirals up out of it, 2.5-3.0 it rears back, 3.0-3.35 it darts down onto the target
 * (the spear), 3.35-4.0 it stays embedded, 4.0-5.5 it circles the target, 5.5-7.0 it coils tighter and tighter, 7-8 it
 * collapses and flows back to its pool.
 */
public final class SerpentPath {
    public static final double RISE_A = 1.0, RISE_B = 2.5, REAR_B = 3.0, DART_B = 3.35, HOLD_B = 4.0, CIRCLE_B = 5.5, COIL_B = 7.0, END = 8.0;

    public final double bx, by, bz, tx, ty, tz, rad;
    public double cx, cy, cz;
    private final double ax, az, phase;

    /** b = the pool (ground, beside the caster), t = the target point on the ground, rad = the predator radius. */
    public SerpentPath(double bx, double by, double bz, double tx, double ty, double tz, double rad) {
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.rad = rad;
        double dx = tx - bx, dz = tz - bz, l = Math.sqrt(dx * dx + dz * dz);
        this.ax = l < 1e-6 ? 0 : dx / l;
        this.az = l < 1e-6 ? 1 : dz / l;
        this.phase = Math.atan2(az, ax) + Math.PI * 0.5;
        this.cx = tx;
        this.cy = ty;
        this.cz = tz;
    }

    public void setCoil(double x, double y, double z) {
        cx = x;
        cy = y;
        cz = z;
    }

    private static double sm(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double s) {
        return a + (b - a) * s;
    }

    public double ringRadius() {
        return Math.min(rad * 0.55, 8.0);
    }

    private double[] rise(double t) {
        double s = sm((t - RISE_A) / (RISE_B - RISE_A));
        double a = phase + 3.4 * s;
        double r = 3.0 * s;
        return new double[]{bx + Math.cos(a) * r, by + 11.0 * s, bz + Math.sin(a) * r};
    }

    private double[] rear(double t) {
        double[] h = rise(RISE_B);
        double w = sm((t - RISE_B) / (REAR_B - RISE_B));
        return new double[]{h[0] - ax * 3.5 * w, h[1] + 2.0 * w, h[2] - az * 3.5 * w};
    }

    /** Where the spear strikes the target (a little above the ground). */
    public double[] strikePoint() {
        return new double[]{tx, ty + 1.0, tz};
    }

    private double angle(double t) {
        if (t <= CIRCLE_B) {
            return 2.2 * (t - HOLD_B);
        }
        return 2.2 * (CIRCLE_B - HOLD_B) + 3.4 * (Math.min(t, COIL_B) - CIRCLE_B);
    }

    private double[] circle(double t) {
        double s = sm((t - HOLD_B) / 0.5);
        double a = angle(t) + phase;
        double r = ringRadius() * s;
        double h = lerp(1.0, 2.4 + 1.3 * Math.sin(2 * a), s);
        return new double[]{tx + Math.cos(a) * r, ty + h, tz + Math.sin(a) * r};
    }

    private double[] coil(double t) {
        double u = sm((t - CIRCLE_B) / 0.8);
        double a = angle(t) + phase;
        double r = lerp(ringRadius(), 1.5, u);
        double a5 = angle(CIRCLE_B) + phase;
        double h5 = 2.4 + 1.3 * Math.sin(2 * a5);
        double h = lerp(h5, 1.0 + 2.2 * sm((t - CIRCLE_B) / 1.3), u);
        return new double[]{cx + Math.cos(a) * r, cy + h, cz + Math.sin(a) * r};
    }

    /** The head centre at time t (seconds). */
    public double[] head(double t) {
        if (t < RISE_A) {
            return new double[]{bx, by, bz};
        }
        if (t < RISE_B) {
            return rise(t);
        }
        if (t < REAR_B) {
            return rear(t);
        }
        double[] h1 = rear(REAR_B);
        double[] d = strikePoint();
        if (t < DART_B) {
            double s = Math.pow((t - REAR_B) / (DART_B - REAR_B), 2.2);
            return new double[]{lerp(h1[0], d[0], s), lerp(h1[1], d[1], s), lerp(h1[2], d[2], s)};
        }
        if (t < HOLD_B) {
            double w = (t - DART_B) * 14;
            return new double[]{d[0] + Math.sin(w) * 0.12, d[1] + Math.cos(w * 1.3) * 0.1, d[2] + Math.cos(w) * 0.12};
        }
        if (t < CIRCLE_B) {
            return circle(t);
        }
        if (t < COIL_B) {
            double[] c = coil(t);
            if (t < CIRCLE_B + 0.4) {
                double[] p = circle(t);
                double s = sm((t - CIRCLE_B) / 0.4);
                return new double[]{lerp(p[0], c[0], s), lerp(p[1], c[1], s), lerp(p[2], c[2], s)};
            }
            return c;
        }
        double[] p7 = coil(COIL_B);
        double s = sm((t - COIL_B) / (END - COIL_B));
        double[] home = {bx, by + 0.25, bz};
        double lift = Math.sin(s * Math.PI) * 0.8;
        return new double[]{lerp(p7[0], home[0], s), lerp(p7[1], home[1], s) + lift, lerp(p7[2], home[2], s)};
    }

    /**
     * The body as n points, head first, spaced `spacing` along the route walked backwards from the head. Points the route has not yet
     * produced stay in the pool. out is n x 3.
     */
    public void spine(double t, int n, double spacing, double[][] out) {
        double[] prev = head(t);
        out[0][0] = prev[0];
        out[0][1] = prev[1];
        out[0][2] = prev[2];
        int i = 1;
        double need = spacing, step = 0.02;
        for (int k = 1; k < 900 && i < n; k++) {
            double[] p = head(t - k * step);
            double dx = p[0] - prev[0], dy = p[1] - prev[1], dz = p[2] - prev[2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            while (i < n && d >= need && d > 1e-9) {
                double f = need / d;
                prev = new double[]{prev[0] + dx * f, prev[1] + dy * f, prev[2] + dz * f};
                out[i][0] = prev[0];
                out[i][1] = prev[1];
                out[i][2] = prev[2];
                i++;
                dx = p[0] - prev[0];
                dy = p[1] - prev[1];
                dz = p[2] - prev[2];
                d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                need = spacing;
            }
            need -= d;
            prev = p;
        }
        for (; i < n; i++) {
            out[i][0] = bx;
            out[i][1] = by + 0.1;
            out[i][2] = bz;
        }
    }
}
