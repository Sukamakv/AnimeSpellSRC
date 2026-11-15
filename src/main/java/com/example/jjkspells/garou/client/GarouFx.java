package com.example.jjkspells.garou.client;

import com.example.jjkspells.garou.entity.GarouTimeline;
import com.example.jjkspells.garou.entity.NuclearFissionEntity;
import com.example.jjkspells.garou.registry.NfParticles;
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

/**
 * Client-side effects of Nuclear Fission: camera shake, white-out, speed lines, and every particle (they are all spawned
 * here, from the controller entity's tick, so the server never has to send any).
 */
public final class GarouFx {
    public static boolean locked;
    public static float flash, shake, fov, lines;
    public static int flashColor = 0xFFFFFF;
    private static boolean wantLock;
    private static float wantFov;

    private GarouFx() {
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
        lines *= 0.84f;
        if (lines < 0.02f) {
            lines = 0f;
        }
        fov += (wantFov - fov) * 0.3f;
        wantFov = 0f;
        if (Math.abs(fov) < 0.002f) {
            fov = 0f;
        }
        locked = wantLock;
        wantLock = false;
        if (mc.level == null || mc.player == null) {
            flash = shake = fov = lines = 0f;
            locked = false;
        }
    }

    /** The caster's right fist in the world (before the punch it is drawn back, during it thrown forward). */
    public static Vec3 fist(Entity owner, boolean punching, float age, float pt) {
        Vec3 p = owner.getPosition(pt).add(0, 1.2, 0);
        Vec3 fwd = owner.getViewVector(pt);
        double yr = Math.toRadians(owner.getViewYRot(pt));
        Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
        if (!punching) {
            float pull = GarouTimeline.smooth(age / 10f);
            return p.add(right.scale(0.45)).add(fwd.scale(0.3 - 0.9 * pull));
        }
        return p.add(right.scale(0.3)).add(fwd.scale(1.15));
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
        return Math.abs(gy - fallback) > 12.0 ? fallback : gy;
    }

    private static BlockParticleOption chunk(Level lv, double x, double gy, double z) {
        BlockState st = lv.getBlockState(BlockPos.containing(x, gy - 1.0, z));
        if (st.isAir() || st.liquid()) {
            st = Blocks.DIRT.defaultBlockState();
        }
        return new BlockParticleOption(ParticleTypes.BLOCK, st);
    }

    // ------------------------------------------------------------------ the sequence

    public static void tick(NuclearFissionEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Level lv = e.level();
        RandomSource r = lv.random;
        int age = e.tickCount - 1;
        Entity owner = e.getOwnerEntity();
        int launch = e.launchAge(), imp = e.impactAge();
        boolean mine = owner != null && owner == mc.player;
        Vec3 focus = imp >= 0 ? e.impactPos() : (owner != null ? owner.position() : e.position());
        double dist = mc.player.position().distanceTo(focus);
        float n = (float) Mth.clamp(1.0 - (dist - 24.0) / 110.0, 0.0, 1.0);

        if (mine && (imp < 0 || age < imp + 4)) {
            wantLock = true;
        }

        // ---- 1. the arm is pulled back, the sphere forms
        if (owner != null && launch < 0) {
            Vec3 f = fist(owner, false, age, 1f);
            int cnt = 2 + age / 3;
            for (int i = 0; i < cnt; i++) {
                Vec3 d = unit(r);
                double rad = 2.0 + r.nextDouble() * 2.8;
                add(lv, NfParticles.SPARK.get(), f.add(d.scale(rad)), d.scale(-rad / 13.0));
            }
            if (age >= 6 && age % 2 == 0) {
                add(lv, NfParticles.GLOW.get(), f.add((r.nextDouble() - .5) * .2, (r.nextDouble() - .5) * .2, (r.nextDouble() - .5) * .2), Vec3.ZERO);
            }
            if (age % 2 == 0) {
                for (int k = 0; k < 3; k++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    lv.addParticle(ParticleTypes.POOF, owner.getX() + Math.cos(a) * 0.6, owner.getY() + 0.1, owner.getZ() + Math.sin(a) * 0.6, Math.cos(a) * 0.14, 0.03, Math.sin(a) * 0.14);
                }
            }
            if (mine && age > 4) {
                shake = Math.max(shake, 0.18f * age / GarouTimeline.WIND);
            }
        }

        // ---- 2. the launch and the dash
        if (launch >= 0 && age == launch && owner != null) {
            if (n > 0.02f) {
                flash = Math.max(flash, 0.45f * n);
                flashColor = 0xFFFFFF;
                shake = Math.max(shake, 3.0f * n);
            }
            Vec3 p = owner.position().add(0, 1.0, 0);
            for (int i = 0; i < 40; i++) {
                Vec3 d = unit(r);
                add(lv, NfParticles.SPARK.get(), p, d.scale(0.6 + r.nextDouble() * 1.4));
            }
            for (int i = 0; i < 16; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                lv.addParticle(ParticleTypes.POOF, owner.getX(), owner.getY() + 0.1, owner.getZ(), Math.cos(a) * 0.5, 0.05, Math.sin(a) * 0.5);
            }
        }
        if (launch >= 0 && imp < 0 && owner != null) {
            Vec3 p = owner.position().add(0, 1.1, 0);
            Vec3 back = owner.getLookAngle().scale(-0.45);
            for (int i = 0; i < 6; i++) {
                Vec3 spread = unit(r).scale(0.22);
                add(lv, NfParticles.SPARK.get(), p.add(spread), back.add(spread));
            }
            add(lv, NfParticles.FIRE.get(), p, back.scale(0.2));
            wantFov = Math.max(wantFov, 0.2f * (mine ? 1f : 0f));
        }

        if (imp < 0) {
            return;
        }

        // ---- 3. the impact and everything after it
        int ti = age - imp;
        Vec3 c = e.impactPos();
        float R = (float) GarouTimeline.RADIUS;
        double gy = c.y;

        if (ti == 0) {
            if (n > 0.02f) {
                flash = Math.max(flash, 1.0f * n);
                flashColor = 0xFFFFFF;
                shake = Math.max(shake, 10f * n);
                lines = Math.max(lines, dist < 70 ? 1f : 0.45f * n);
            }
            Vec3 top = c.add(0, 1.5, 0);
            for (int i = 0; i < 150; i++) {
                Vec3 d = unit(r);
                if (d.y < -0.1) {
                    d = new Vec3(d.x, -d.y * 0.4, d.z);
                }
                add(lv, NfParticles.SPARK.get(), top, d.scale(1.2 + r.nextDouble() * 3.4));
            }
            for (int i = 0; i < 50; i++) {
                Vec3 d = unit(r);
                add(lv, NfParticles.FIRE.get(), top.add(d.scale(1.5)), d.scale(0.3 + r.nextDouble() * 0.9));
            }
            for (int i = 0; i < 70; i++) {
                double a = r.nextDouble() * Math.PI * 2, s = 0.5 + r.nextDouble() * 1.4;
                add(lv, chunk(lv, c.x, gy, c.z), c.add(Math.cos(a) * 1.5, 0.3, Math.sin(a) * 1.5), new Vec3(Math.cos(a) * s, 0.3 + r.nextDouble() * 1.1, Math.sin(a) * s));
            }
            lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 0, 0, 0);
        }
        if (ti >= 0 && ti < 9) {
            wantFov = Math.max(wantFov, 0.3f * n * (1f - ti / 9f));
        }
        if (ti == 2 || ti == 4 || ti == 6) {
            lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x + (r.nextDouble() - .5) * 8, c.y + 1.0, c.z + (r.nextDouble() - .5) * 8, 0, 0, 0);
        }

        // ground debris riding the shockwave
        if (ti >= 0 && ti < 17) {
            float ringR = R * (1f - (float) Math.pow(1f - ti / 16f, 2));
            for (int i = 0; i < 26; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double x = c.x + Math.cos(a) * ringR, z = c.z + Math.sin(a) * ringR;
                double y = groundY(lv, x, z, gy);
                double s = 0.45 + r.nextDouble() * 0.7;
                add(lv, chunk(lv, x, y, z), new Vec3(x, y + 0.2, z), new Vec3(Math.cos(a) * s, 0.35 + r.nextDouble() * 0.6, Math.sin(a) * s));
                if (i % 3 == 0) {
                    add(lv, NfParticles.SPARK.get(), new Vec3(x, y + 0.5, z), new Vec3(Math.cos(a) * s * 1.5, 0.2, Math.sin(a) * s * 1.5));
                }
            }
        }

        // the fireball
        if (ti >= 2 && ti < 44) {
            float fr = GarouTimeline.fireRadius(ti);
            for (int i = 0; i < 6; i++) {
                Vec3 d = unit(r);
                Vec3 p = c.add(0, fr * 0.55, 0).add(d.scale(fr * (0.4 + r.nextDouble() * 0.5)));
                add(lv, NfParticles.FIRE.get(), p, d.scale(0.12 + r.nextDouble() * 0.2).add(0, 0.05, 0));
            }
        }

        // the mushroom cloud
        if (ti >= 4 && ti < 112) {
            float capY = GarouTimeline.capHeight(ti), capR = GarouTimeline.capRadius(ti), stemR = GarouTimeline.stemRadius(ti);
            for (int i = 0; i < 5; i++) {
                double th = r.nextDouble() * Math.PI * 2, el = (r.nextDouble() * 1.5 - 0.35);
                double cr = Math.cos(el) * capR * 0.92;
                Vec3 p = c.add(Math.cos(th) * cr, capY + Math.sin(el) * capR * 0.55, Math.sin(th) * cr);
                add(lv, NfParticles.SMOKE.get(), p, new Vec3(Math.cos(th) * 0.05, 0.02 + r.nextDouble() * 0.04, Math.sin(th) * 0.05));
            }
            for (int i = 0; i < 2; i++) {
                double th = r.nextDouble() * Math.PI * 2;
                double hh = r.nextDouble() * Math.max(1.0, capY - capR * 0.4);
                double rr = stemR * (0.6 + r.nextDouble() * 0.5);
                add(lv, NfParticles.SMOKE.get(), c.add(Math.cos(th) * rr, hh, Math.sin(th) * rr), new Vec3(Math.cos(th) * 0.03, 0.1, Math.sin(th) * 0.03));
            }
            if (ti < 60 && ti % 2 == 0) {
                double th = r.nextDouble() * Math.PI * 2;
                double rr = 8 + r.nextDouble() * 10;
                add(lv, NfParticles.FIRE.get(), c.add(Math.cos(th) * rr, 2 + r.nextDouble() * 6, Math.sin(th) * rr), new Vec3(Math.cos(th) * 0.1, 0.12, Math.sin(th) * 0.1));
            }
        }

        // the small explosions around the main blast
        for (int i = 0; i < GarouTimeline.SAT_COUNT; i++) {
            if (ti == GarouTimeline.satTick(i)) {
                Vec3 p = NuclearFissionEntity.satPoint(lv, c, i, e.getId());
                lv.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 1.0, p.z, 0, 0, 0);
                for (int k = 0; k < 22; k++) {
                    Vec3 d = unit(r);
                    add(lv, NfParticles.FIRE.get(), p.add(0, 1.5, 0).add(d.scale(1.2)), new Vec3(d.x * 0.35, Math.abs(d.y) * 0.5 + 0.1, d.z * 0.35));
                }
                for (int k = 0; k < 26; k++) {
                    Vec3 d = unit(r);
                    add(lv, NfParticles.SPARK.get(), p.add(0, 1.0, 0), new Vec3(d.x, Math.abs(d.y) + 0.2, d.z).scale(0.7 + r.nextDouble() * 1.6));
                }
                for (int k = 0; k < 10; k++) {
                    add(lv, NfParticles.SMOKE.get(), p.add((r.nextDouble() - .5) * 2, 0.5 + k * 0.7, (r.nextDouble() - .5) * 2), new Vec3(0, 0.1, 0));
                }
                double dd = mc.player.position().distanceTo(p);
                if (dd < 80) {
                    shake = Math.max(shake, (float) (1.4 * (1 - dd / 80.0)));
                }
            }
        }

        // aftermath: glowing embers fall, the ground stays lit
        if (ti >= 70 && ti < GarouTimeline.AFTER - 10) {
            for (int i = 0; i < 7; i++) {
                double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 26.0;
                double x = c.x + Math.cos(a) * rr, z = c.z + Math.sin(a) * rr;
                add(lv, NfParticles.EMBER.get(), new Vec3(x, groundY(lv, x, z, gy) + 2 + r.nextDouble() * 14, z), new Vec3((r.nextDouble() - .5) * 0.03, -0.02, (r.nextDouble() - .5) * 0.03));
            }
        }

        // the final shockwave
        if (ti == GarouTimeline.FINAL_SHOCK && n > 0.02f) {
            flash = Math.max(flash, 0.4f * n);
            flashColor = 0xCFE6FF;
            shake = Math.max(shake, 6f * n);
            lines = Math.max(lines, 0.5f * n);
        }
        if (ti >= GarouTimeline.FINAL_SHOCK && ti < GarouTimeline.FINAL_SHOCK + 17) {
            float f = (ti - GarouTimeline.FINAL_SHOCK) / 16f;
            float rr = (float) GarouTimeline.SHOCK_RADIUS * GarouTimeline.ease(f);
            for (int i = 0; i < 34; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double x = c.x + Math.cos(a) * rr, z = c.z + Math.sin(a) * rr;
                double y = groundY(lv, x, z, gy);
                add(lv, NfParticles.SPARK.get(), new Vec3(x, y + 0.4, z), new Vec3(Math.cos(a) * 1.1, 0.1 + r.nextDouble() * 0.2, Math.sin(a) * 1.1));
                if (i % 4 == 0) {
                    lv.addParticle(ParticleTypes.POOF, x, y + 0.3, z, Math.cos(a) * 0.5, 0.15, Math.sin(a) * 0.5);
                }
            }
        }
    }
}
