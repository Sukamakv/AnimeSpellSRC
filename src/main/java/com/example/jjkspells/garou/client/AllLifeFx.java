package com.example.jjkspells.garou.client;

import com.example.jjkspells.garou.entity.AllLifeEntity;
import com.example.jjkspells.garou.entity.GarouTimeline;
import com.example.jjkspells.garou.registry.NfParticles;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.garou.entity.AllLifeTimeline.*;

/**
 * Client side of All Life Eradication Fist: camera shots, screen effects and every particle. Everything is spawned from the
 * controller entity's client tick, from its age only, so the server sends nothing besides the entity itself.
 */
public final class AllLifeFx {
    /** The caster's own view follows the cinematic camera shots (face close-up, behind the fight, wide on the blast). */
    public static final boolean CINEMATIC = true;

    public static boolean locked;
    public static float flash, shake, fov, lines, dark;
    public static int flashColor = 0xFFFFFF;
    private static boolean wantLock;
    private static float wantFov, wantDark;
    private static CameraType wantCam, savedCam;

    private AllLifeFx() {
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        flash *= 0.87f;
        if (flash < 0.01f) {
            flash = 0f;
        }
        shake *= 0.9f;
        if (shake < 0.01f) {
            shake = 0f;
        }
        lines *= 0.86f;
        if (lines < 0.02f) {
            lines = 0f;
        }
        fov += (wantFov - fov) * 0.12f;
        wantFov = 0f;
        if (Math.abs(fov) < 0.002f) {
            fov = 0f;
        }
        dark += (wantDark - dark) * 0.2f;
        wantDark = 0f;
        if (dark < 0.005f) {
            dark = 0f;
        }
        if (wantCam != null && mc.player != null) {
            if (savedCam == null) {
                savedCam = mc.options.getCameraType();
            }
            if (mc.options.getCameraType() != wantCam) {
                mc.options.setCameraType(wantCam);
            }
        } else if (savedCam != null) {
            mc.options.setCameraType(savedCam);
            savedCam = null;
        }
        wantCam = null;
        locked = wantLock;
        wantLock = false;
        if (mc.level == null || mc.player == null) {
            flash = shake = fov = lines = dark = 0f;
            locked = false;
        }
    }

    // ------------------------------------------------------------------ shared geometry (the renderer uses it too)

    /** The point the strikes land on: the target's centre, or the fixed point when there is no target. */
    public static Vec3 strikePoint(AllLifeEntity e) {
        Entity t = e.targetId() >= 0 ? e.level().getEntity(e.targetId()) : null;
        if (t != null && t.isAlive()) {
            return t.getBoundingBox().getCenter();
        }
        return e.centerPos().add(0, 1.2, 0);
    }

    // ------------------------------------------------------------------ helpers

    private static Vec3 unit(RandomSource r) {
        double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(s * Math.cos(a), z, s * Math.sin(a));
    }

    private static void add(Level lv, ParticleOptions o, Vec3 p, Vec3 v) {
        lv.addParticle(o, p.x, p.y, p.z, v.x, v.y, v.z);
    }

    private static double groundY(Level lv, double x, double z, double fallback) {
        double gy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        return Math.abs(gy - fallback) > 14.0 ? fallback : gy;
    }

    private static BlockParticleOption chunk(Level lv, double x, double gy, double z) {
        BlockState st = lv.getBlockState(BlockPos.containing(x, gy - 1.0, z));
        if (st.isAir() || st.liquid()) {
            st = Blocks.DIRT.defaultBlockState();
        }
        return new BlockParticleOption(ParticleTypes.BLOCK, st);
    }

    /** A cone of sparks around dir. */
    private static void sparkCone(Level lv, Vec3 p, Vec3 dir, int count, double spread, double speed) {
        RandomSource r = lv.random;
        for (int i = 0; i < count; i++) {
            Vec3 d = dir.add(unit(r).scale(spread)).normalize();
            add(lv, NfParticles.SPARK.get(), p, d.scale(speed * (0.4 + r.nextDouble() * 0.9)));
        }
    }

    private static void sparkSphere(Level lv, Vec3 p, int count, double speed) {
        RandomSource r = lv.random;
        for (int i = 0; i < count; i++) {
            add(lv, NfParticles.SPARK.get(), p, unit(r).scale(speed * (0.3 + r.nextDouble())));
        }
    }

    // ------------------------------------------------------------------ the sequence

    public static void tick(AllLifeEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Level lv = e.level();
        RandomSource r = lv.random;
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        int A = e.assaultAge();
        boolean mine = owner != null && owner == mc.player;
        Vec3 c = A >= 0 ? e.centerPos() : Vec3.ZERO;
        Vec3 focus = A >= 0 ? c : (owner != null ? owner.position() : e.position());
        double dist = mc.player.position().distanceTo(focus);
        float n = (float) Mth.clamp(1.0 - (dist - 30.0) / 150.0, 0.0, 1.0);
        int a = A >= 0 ? age - A : Integer.MIN_VALUE / 2;
        int ti = A >= 0 ? a - NUKE : Integer.MIN_VALUE / 2;

        // ---- the caster's own view
        if (mine) {
            if (A < 0 || ti < FREE) {
                wantLock = true;
            }
            if (CINEMATIC) {
                if (age < STANCE) {
                    wantCam = CameraType.THIRD_PERSON_FRONT;
                    wantFov = Math.max(wantFov, 0.03f + 0.24f * GarouTimeline.smooth(age / (float) STANCE));
                } else if (A < 0 || ti < 12) {
                    wantCam = CameraType.THIRD_PERSON_BACK;
                    wantFov = Math.max(wantFov, 0.1f);
                } else if (ti < FREE) {
                    wantCam = CameraType.THIRD_PERSON_BACK;
                    wantFov = Math.min(wantFov, -0.3f);
                }
            } else if (age < STANCE) {
                wantFov = Math.max(wantFov, 0.03f + 0.24f * GarouTimeline.smooth(age / (float) STANCE));
            }
        }

        // ---- phase 1: the stance
        if (owner != null && age < STANCE) {
            Vec3 chest = owner.getPosition(1f).add(0, 1.2, 0);
            float k = age / (float) STANCE;
            int cnt = 1 + (int) (4 * k);
            for (int i = 0; i < cnt; i++) {
                Vec3 d = unit(r);
                double rad = 2.0 + r.nextDouble() * 2.5;
                add(lv, NfParticles.SPARK.get(), chest.add(d.scale(rad)), d.scale(-rad / 14.0));
            }
            if (age % 3 == 0) {
                for (int i = 0; i < 2; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    lv.addParticle(ParticleTypes.POOF, owner.getX() + Math.cos(ang) * 0.7, owner.getY() + 0.1, owner.getZ() + Math.sin(ang) * 0.7, Math.cos(ang) * 0.18, 0.03, Math.sin(ang) * 0.18);
                }
            }
            if (mine && age > 8) {
                shake = Math.max(shake, 0.25f * k);
            }
        }

        // ---- the launch and the dash
        if (age == STANCE && owner != null) {
            if (n > 0.02f) {
                flash = Math.max(flash, 0.5f * n);
                flashColor = 0xDCD2FF;
                shake = Math.max(shake, 3.0f * n);
            }
            Vec3 p = owner.position().add(0, 1.0, 0);
            sparkSphere(lv, p, 60, 1.6);
            for (int i = 0; i < 18; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                lv.addParticle(ParticleTypes.POOF, owner.getX(), owner.getY() + 0.1, owner.getZ(), Math.cos(ang) * 0.6, 0.05, Math.sin(ang) * 0.6);
            }
        }
        if (age > STANCE && A < 0 && owner != null) {
            Vec3 p = owner.position().add(0, 1.1, 0);
            Vec3 back = owner.getLookAngle().scale(-0.5);
            for (int i = 0; i < 8; i++) {
                Vec3 sp = unit(r).scale(0.25);
                add(lv, NfParticles.SPARK.get(), p.add(sp), back.add(sp));
            }
            add(lv, NfParticles.FIRE.get(), p, back.scale(0.2));
            wantFov = Math.max(wantFov, mine ? 0.2f : 0f);
        }

        if (A < 0) {
            return;
        }

        // ---- phase 2: the five strikes
        for (int i = 0; i < STRIKE.length; i++) {
            if (a == STRIKE[i] + HIT_DELAY) {
                strikeFx(e, lv, r, owner, i, n, mc);
            }
        }
        if (a >= 0 && a < NUKE) {
            wantFov = Math.max(wantFov, mine ? 0.1f : 0f);
        }

        if (ti < 0) {
            return;
        }

        // ---- phase 3: nuclear fission
        double gy = c.y;
        float R = (float) RADIUS;
        if (ti == 0) {
            if (n > 0.02f) {
                flash = Math.max(flash, 1.0f * n);
                flashColor = 0xFFFFFF;
                shake = Math.max(shake, 12f * n);
                lines = Math.max(lines, dist < 90 ? 1f : 0.5f * n);
            }
            Vec3 top = c.add(0, 1.5, 0);
            for (int i = 0; i < 220; i++) {
                Vec3 d = unit(r);
                if (d.y < -0.1) {
                    d = new Vec3(d.x, -d.y * 0.4, d.z);
                }
                add(lv, NfParticles.SPARK.get(), top, d.scale(1.4 + r.nextDouble() * 4.0));
            }
            for (int i = 0; i < 70; i++) {
                Vec3 d = unit(r);
                add(lv, NfParticles.FIRE.get(), top.add(d.scale(2.0)), d.scale(0.35 + r.nextDouble() * 1.0));
            }
            for (int i = 0; i < 110; i++) {
                double ang = r.nextDouble() * Math.PI * 2, s = 0.6 + r.nextDouble() * 1.7;
                add(lv, chunk(lv, c.x, gy, c.z), c.add(Math.cos(ang) * 2, 0.3, Math.sin(ang) * 2), new Vec3(Math.cos(ang) * s, 0.3 + r.nextDouble() * 1.3, Math.sin(ang) * s));
            }
            lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 0, 0, 0);
        }
        if (ti >= 0 && ti < 9) {
            wantFov = Math.max(wantFov, 0.25f * n * (1f - ti / 9f));
        }
        if (ti == 2 || ti == 4 || ti == 6) {
            lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x + (r.nextDouble() - .5) * 12, c.y + 1.0, c.z + (r.nextDouble() - .5) * 12, 0, 0, 0);
        }
        if (ti >= 0 && ti < 22) {
            float ringR = R * 1.1f * (1f - (float) Math.pow(1f - ti / 21f, 2));
            for (int i = 0; i < 34; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double x = c.x + Math.cos(ang) * ringR, z = c.z + Math.sin(ang) * ringR;
                double y = groundY(lv, x, z, gy);
                double s = 0.5 + r.nextDouble() * 0.8;
                add(lv, chunk(lv, x, y, z), new Vec3(x, y + 0.2, z), new Vec3(Math.cos(ang) * s, 0.4 + r.nextDouble() * 0.7, Math.sin(ang) * s));
                if (i % 3 == 0) {
                    add(lv, NfParticles.SPARK.get(), new Vec3(x, y + 0.5, z), new Vec3(Math.cos(ang) * s * 1.6, 0.2, Math.sin(ang) * s * 1.6));
                }
            }
        }
        Vec3 gc = c.add(0, GRAV_HEIGHT, 0);
        float pullF = GarouTimeline.smooth((ti - GRAV) / (float) GRAV_SPAN);

        // the main fireball and the mushroom cloud (they are pulled into the gravity sphere later)
        if (ti >= 2 && ti < 50) {
            float fr = fireRadius(ti, BIG);
            for (int i = 0; i < 8; i++) {
                Vec3 d = unit(r);
                Vec3 p = c.add(0, fr * 0.55, 0).add(d.scale(fr * (0.4 + r.nextDouble() * 0.5)));
                add(lv, NfParticles.FIRE.get(), p, d.scale(0.14 + r.nextDouble() * 0.25).add(0, 0.06, 0));
            }
        }
        if (ti >= 4 && ti < GRAV + 55) {
            float capY = GarouTimeline.capHeight(ti) * BIG, capR = GarouTimeline.capRadius(ti) * BIG, stemR = GarouTimeline.stemRadius(ti) * BIG;
            float k = 1f - 0.9f * pullF;
            for (int i = 0; i < 6; i++) {
                double th = r.nextDouble() * Math.PI * 2, el = r.nextDouble() * 1.5 - 0.35;
                double cr = Math.cos(el) * capR * 0.92 * k;
                Vec3 p = c.add(Math.cos(th) * cr, capY + Math.sin(el) * capR * 0.55 * k, Math.sin(th) * cr);
                p = p.add(gc.subtract(p).scale(pullF));
                add(lv, NfParticles.SMOKE.get(), p, new Vec3(Math.cos(th) * 0.06, 0.03 + r.nextDouble() * 0.04, Math.sin(th) * 0.06).scale(1f - pullF));
            }
            if (pullF < 0.5f) {
                for (int i = 0; i < 2; i++) {
                    double th = r.nextDouble() * Math.PI * 2;
                    double hh = r.nextDouble() * Math.max(1.0, capY - capR * 0.4);
                    double rr = stemR * (0.6 + r.nextDouble() * 0.5);
                    add(lv, NfParticles.SMOKE.get(), c.add(Math.cos(th) * rr, hh, Math.sin(th) * rr), new Vec3(Math.cos(th) * 0.03, 0.12, Math.sin(th) * 0.03));
                }
            }
        }

        // the several nuclear explosions around the target, each with its own small mushroom
        for (int i = 0; i < MED_COUNT; i++) {
            int lt = ti - medTick(i);
            Vec3 p = c.add(medOffset(i, e.getId()));
            double py = groundY(lv, p.x, p.z, gy);
            if (lt == 0) {
                lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, py + 1.0, p.z, 0, 0, 0);
                sparkSphere(lv, new Vec3(p.x, py + 2.0, p.z), 90, 2.2);
                for (int k = 0; k < 30; k++) {
                    Vec3 d = unit(r);
                    add(lv, NfParticles.FIRE.get(), new Vec3(p.x, py + 2.0, p.z).add(d.scale(1.5)), new Vec3(d.x * 0.5, Math.abs(d.y) * 0.6 + 0.1, d.z * 0.5));
                }
                if (n > 0.02f) {
                    shake = Math.max(shake, 3.5f * n);
                    flash = Math.max(flash, 0.25f * n);
                    flashColor = 0xFFE8C0;
                }
            }
            if (lt >= 0 && lt < 50 && pullF < 0.4f) {
                for (int k = 0; k < 2; k++) {
                    double th = r.nextDouble() * Math.PI * 2, rr = r.nextDouble() * 4.0;
                    add(lv, NfParticles.SMOKE.get(), new Vec3(p.x + Math.cos(th) * rr, py + 2 + lt * 0.35 + r.nextDouble() * 5, p.z + Math.sin(th) * rr), new Vec3(0, 0.1, 0));
                }
                if (lt < 20) {
                    add(lv, NfParticles.FIRE.get(), new Vec3(p.x, py + 2 + r.nextDouble() * 5, p.z), new Vec3((r.nextDouble() - .5) * 0.4, 0.15, (r.nextDouble() - .5) * 0.4));
                }
            }
        }
        // small bright blasts
        for (int i = 0; i < SAT_COUNT; i++) {
            if (ti == satTick(i)) {
                Vec3 p = c.add(satOffset(i, e.getId()));
                double py = groundY(lv, p.x, p.z, gy);
                lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, py + 1.0, p.z, 0, 0, 0);
                sparkSphere(lv, new Vec3(p.x, py + 1.5, p.z), 26, 1.6);
                for (int k = 0; k < 14; k++) {
                    Vec3 d = unit(r);
                    add(lv, NfParticles.FIRE.get(), new Vec3(p.x, py + 2.0, p.z), new Vec3(d.x * 0.35, Math.abs(d.y) * 0.5 + 0.1, d.z * 0.35));
                }
                if (dist < 90) {
                    shake = Math.max(shake, (float) (1.6 * (1 - dist / 90.0)));
                }
            }
        }
        if (ti >= 70 && ti < COLLAPSE - 20) {
            for (int i = 0; i < 6; i++) {
                double ang = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 36.0;
                double x = c.x + Math.cos(ang) * rr, z = c.z + Math.sin(ang) * rr;
                add(lv, NfParticles.EMBER.get(), new Vec3(x, groundY(lv, x, z, gy) + 2 + r.nextDouble() * 14, z), new Vec3((r.nextDouble() - .5) * 0.03, -0.02, (r.nextDouble() - .5) * 0.03));
            }
        }

        // ---- phase 4: the gravity sphere draws the explosion's energy inward
        if (ti >= GRAV && ti < COLLAPSE) {
            float f = (ti - GRAV) / (float) GRAV_SPAN;
            if (ti == GRAV && n > 0.02f) {
                shake = Math.max(shake, 4f * n);
                flash = Math.max(flash, 0.2f * n);
                flashColor = 0xC8B0FF;
            }
            int cnt = 8 + (int) (22 * f);
            for (int i = 0; i < cnt; i++) {
                Vec3 d = unit(r);
                double rad = 7.0 + r.nextDouble() * 32.0;
                Vec3 p = gc.add(d.scale(rad));
                Vec3 inward = gc.subtract(p).scale(0.045);
                Vec3 swirl = d.cross(new Vec3(0, 1, 0)).scale(0.25 + 0.3 * f);
                add(lv, i % 3 == 0 ? NfParticles.EMBER.get() : NfParticles.SPARK.get(), p, inward.add(swirl));
            }
            if (ti % 2 == 0) {
                Vec3 d = unit(r);
                add(lv, NfParticles.GLOW.get(), gc.add(d.scale(gravR(ti) * 0.8)), Vec3.ZERO);
            }
            if (n > 0.02f) {
                shake = Math.max(shake, (0.4f + 2.2f * f) * n);
                wantDark = Math.max(wantDark, 0.12f + 0.45f * f * n);
                if (mine) {
                    wantFov = Math.max(wantFov, 0f) - 0.0f;
                    fov += 0.012f * (float) Math.sin(ti * 0.9);
                }
            }
        }

        // ---- the collapse: a final white flash
        if (ti == COLLAPSE) {
            if (n > 0.02f) {
                flash = Math.max(flash, 1.0f * n);
                flashColor = 0xFFFFFF;
                shake = Math.max(shake, 16f * n);
                lines = Math.max(lines, n);
            }
            sparkSphere(lv, gc, 320, 4.0);
            for (int i = 0; i < 24; i++) {
                add(lv, NfParticles.GLOW.get(), gc.add(unit(r).scale(2.0)), Vec3.ZERO);
            }
            lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, gc.x, gc.y, gc.z, 0, 0, 0);
            lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 0, 0, 0);
        }

        // ---- phase 5: the final cosmic shockwave
        if (ti == SHOCK0 && n > 0.02f) {
            flash = Math.max(flash, 0.45f * n);
            flashColor = 0xDCCBFF;
            shake = Math.max(shake, 9f * n);
            lines = Math.max(lines, 0.6f * n);
        }
        if (ti >= SHOCK0 && ti < SHOCK0 + SHOCK_LEN + 2) {
            float f = (ti - SHOCK0) / (float) SHOCK_LEN;
            float rr = R * GarouTimeline.ease(f);
            for (int i = 0; i < 44; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double x = c.x + Math.cos(ang) * rr, z = c.z + Math.sin(ang) * rr;
                double y = groundY(lv, x, z, gy);
                add(lv, NfParticles.SPARK.get(), new Vec3(x, y + 0.4, z), new Vec3(Math.cos(ang) * 1.2, 0.1 + r.nextDouble() * 0.3, Math.sin(ang) * 1.2));
                if (i % 4 == 0) {
                    lv.addParticle(ParticleTypes.POOF, x, y + 0.3, z, Math.cos(ang) * 0.6, 0.18, Math.sin(ang) * 0.6);
                }
                if (i % 2 == 0) {
                    Vec3 d = unit(r);
                    Vec3 up = new Vec3(d.x, Math.abs(d.y), d.z);
                    add(lv, NfParticles.SPARK.get(), c.add(up.scale(rr)), up.scale(1.0));
                }
            }
        }
    }

    private static float gravR(int ti) {
        return gravRadius(ti - GRAV);
    }

    // ------------------------------------------------------------------ one strike's impact

    private static void strikeFx(AllLifeEntity e, Level lv, RandomSource r, Entity owner, int i, float n, Minecraft mc) {
        Vec3 p = strikePoint(e);
        Vec3 from = owner != null ? owner.getPosition(1f).add(0, 1.2, 0) : p.add(0, 0, -2);
        Vec3 dir = p.subtract(from);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        float[] sh = {2.0f, 2.5f, 3.2f, 5.0f, 8.0f};
        float[] fl = {0.08f, 0.1f, 0.12f, 0.2f, 0.5f};
        if (n > 0.02f) {
            shake = Math.max(shake, sh[i] * n);
            flash = Math.max(flash, fl[i] * n);
            flashColor = i % 2 == 0 ? 0xE6E0FF : 0xC8D8FF;
            if (i >= 3) {
                lines = Math.max(lines, (i == 4 ? 1f : 0.6f) * n);
            }
        }
        switch (i) {
            case 0, 1 -> {
                sparkCone(lv, p, dir, 40, 0.9, 1.6);
                sparkCone(lv, p, dir.scale(-1), 16, 0.8, 1.0);
            }
            case 2 -> {
                for (int k = 0; k < 60; k++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    add(lv, NfParticles.SPARK.get(), p, new Vec3(Math.cos(ang), (r.nextDouble() - .5) * 0.2, Math.sin(ang)).scale(1.0 + r.nextDouble() * 1.4));
                }
                for (int k = 0; k < 10; k++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    lv.addParticle(ParticleTypes.POOF, p.x, p.y - 0.6, p.z, Math.cos(ang) * 0.7, 0.1, Math.sin(ang) * 0.7);
                }
            }
            case 3 -> {
                sparkCone(lv, p, dir, 70, 0.25, 3.2);
                sparkCone(lv, p, dir.scale(-1), 20, 0.6, 1.2);
                for (int k = 0; k < 12; k++) {
                    add(lv, NfParticles.FIRE.get(), p.add(dir.scale(k * 0.8)), dir.scale(0.2));
                }
            }
            default -> {
                sparkSphere(lv, p, 140, 2.6);
                for (int k = 0; k < 24; k++) {
                    Vec3 d = unit(r);
                    add(lv, NfParticles.FIRE.get(), p.add(d.scale(1.2)), d.scale(0.4 + r.nextDouble() * 0.5));
                }
                lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 0, 0, 0);
            }
        }
        if (i >= 2) {
            double gy = groundY(lv, p.x, p.z, e.centerPos().y);
            int cnt = i == 4 ? 40 : 14;
            for (int k = 0; k < cnt; k++) {
                double ang = r.nextDouble() * Math.PI * 2, s = 0.4 + r.nextDouble() * 0.9;
                add(lv, chunk(lv, p.x, gy, p.z), new Vec3(p.x, gy + 0.3, p.z), new Vec3(Math.cos(ang) * s, 0.3 + r.nextDouble() * 0.9, Math.sin(ang) * s));
            }
        }
    }
}
