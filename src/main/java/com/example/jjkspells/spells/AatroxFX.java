package com.example.jjkspells.spells;

import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Helpers shared by Aatrox's spells: crimson-and-black visuals, particle wings, ground fissures, size scaling. */
public final class AatroxFX {
    private AatroxFX() {}

    /** While World Ender is active, Aatrox's sword attacks are about this much bigger. */
    public static final float WORLD_ENDER_SIZE = 1.2f;

    public static boolean empowered(LivingEntity e) {
        return e.hasEffect(ModEffects.WORLD_ENDER.get());
    }

    /** 1.2 under World Ender, otherwise 1. Multiplies the reach, width and radius of sword attacks and their visuals. */
    public static float sizeScale(LivingEntity e) {
        return empowered(e) ? WORLD_ENDER_SIZE : 1f;
    }

    /** The irradiated effect from Alex's Caves, or null if that mod is not installed. */
    public static MobEffect irradiated() {
        return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("alexscaves", "irradiated"));
    }

    /** Applies alexscaves:irradiated if it exists. */
    public static void irradiate(LivingEntity e, int ticks) {
        MobEffect fx = irradiated();
        if (fx != null) e.addEffect(new MobEffectInstance(fx, ticks, 0));
    }

    /** Horizontal unit vector the entity's body faces. */
    public static Vec3 forward(LivingEntity e) {
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    // ---- ribbons ------------------------------------------------------------------------------

    /** A nearly straight, slightly curved blade of light from {@code start} along {@code dir}, bending toward {@code bend}. */
    public static void line(ServerLevel level, Vec3 start, Vec3 dir, Vec3 bendDir, float length, float bendRadians,
                            float width, int life, int palette) {
        line(level, start, dir, bendDir, length, bendRadians, width, life, palette, false);
    }

    /** As {@link #line}; with {@code flat} the strip keeps its width inside its plane (for strips lying on the ground). */
    public static void line(ServerLevel level, Vec3 start, Vec3 dir, Vec3 bendDir, float length, float bendRadians,
                            float width, int life, int palette, boolean flat) {
        Vec3 f = dir.normalize();
        Vec3 b = bendDir.subtract(f.scale(bendDir.dot(f)));
        b = b.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : b.normalize();
        float r = length / bendRadians;
        Vec3 c = start.add(b.scale(r));
        // p(theta) = c + r(cos(theta)(-b) + sin(theta) f): theta = 0 is {@code start}, and it curves toward b
        if (flat) DariusFX.flatRibbon(level, c, b.scale(-1), f, r, r, 0f, bendRadians, width, life, palette);
        else DariusFX.ribbon(level, c, b.scale(-1), f, r, r, 0f, 0f, bendRadians, width, life, palette);
    }

    /** A tall curved spike bursting up out of the ground at {@code base}, leaning toward {@code lean}. */
    public static void spike(ServerLevel level, Vec3 base, Vec3 lean, float height, float width, int life, int palette) {
        Vec3 l = new Vec3(lean.x, 0, lean.z);
        l = l.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : l.normalize();
        float span = 0.9f;
        float r = height / Mth.sin(span);
        DariusFX.ribbon(level, base.add(l.scale(r)), l, new Vec3(0, 1, 0), r, r, 0f, (float) Math.PI, (float) Math.PI - span, width, life, palette);
    }

    /** A long red fissure torn across the ground, with jagged branches. */
    public static void fissure(ServerLevel level, Vec3 start, Vec3 dir, float length, float width) {
        RandomSource rng = level.random;
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = 0; i < 4; i++) {
            Vec3 s = start.add(side.scale((rng.nextDouble() - 0.5) * 0.5));
            float sign = (i & 1) == 0 ? 1f : -1f;
            line(level, s, dir, side.scale(sign), length * (0.85f + rng.nextFloat() * 0.2f), 0.12f + rng.nextFloat() * 0.3f,
                    width * (i == 0 ? 1f : 0.55f), 18, i == 0 ? MoonRibbonOptions.BRIGHT_RED : MoonRibbonOptions.BLOOD, true);
        }
    }

    // ---- wings --------------------------------------------------------------------------------

    /**
     * Huge spectral wings of crimson feathers attached to the back. {@code spread} runs from 0 (folded down the back) to
     * 1 (fully open); {@code scale} sizes the feathers. Feathers last {@code life} ticks, so call this every few ticks.
     */
    public static void wings(ServerLevel level, LivingEntity e, float spread, float scale, int life) {
        Vec3 fwd = forward(e);
        Vec3 back = fwd.scale(-1);
        Vec3 right = MartialFX.rightOf(e);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 shoulder = e.position().add(0, e.getBbHeight() * 0.72, 0).add(back.scale(0.25));
        int feathers = 6;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 out = right.scale(side);
            Vec3 root = shoulder.add(out.scale(0.2));
            for (int i = 0; i < feathers; i++) {
                double t = i / (double) (feathers - 1);
                double elev = Mth.lerp(t, -0.1, 1.25);
                Vec3 open = out.scale(Math.cos(elev)).add(up.scale(Math.sin(elev))).add(back.scale(0.55)).normalize();
                Vec3 folded = back.scale(0.65).add(up.scale(-0.85 + t * 0.35)).add(out.scale(0.12)).normalize();
                Vec3 dir = folded.scale(1 - spread).add(open.scale(spread)).normalize();
                float length = (float) ((2.3 + 1.9 * Math.sin(Math.PI * (0.25 + 0.75 * t))) * scale * Mth.lerp(spread, 0.55f, 1f));
                Vec3 bend = back.scale(0.6).add(up.scale(-0.4));
                line(level, root, dir, bend, length, 0.55f, 0.36f + (float) (0.14 * (1 - t)), life,
                        (i & 1) == 0 ? MoonRibbonOptions.BLOOD : MoonRibbonOptions.DARK_RED);
            }
        }
    }

    // ---- particles ----------------------------------------------------------------------------

    /** Blood-red droplets spraying out of {@code p}. */
    public static void blood(ServerLevel level, Vec3 p, int count, double speed) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.AT_BLOOD.get(), p, new Vec3(d.x * speed, Math.abs(d.y) * speed + 0.05, d.z * speed));
        }
    }

    /** Black smoke and red embers around {@code p}. */
    public static void smoke(ServerLevel level, Vec3 p, int count, double spread) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 off = new Vec3((rng.nextDouble() - 0.5) * 2 * spread, rng.nextDouble() * spread, (rng.nextDouble() - 0.5) * 2 * spread);
            FX.one(level, ModParticles.AT_SMOKE.get(), p.add(off), new Vec3(off.x * 0.02, 0.01 + rng.nextDouble() * 0.02, off.z * 0.02));
        }
    }

    /** Embers falling from above across a disc of {@code radius} around {@code center}. */
    public static void emberRain(ServerLevel level, Vec3 center, double radius, double height, int count) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(rng.nextDouble()) * radius;
            Vec3 p = center.add(Math.cos(a) * r, height * (0.4 + rng.nextDouble() * 0.6), Math.sin(a) * r);
            FX.one(level, ModParticles.AT_EMBER.get(), p, new Vec3((rng.nextDouble() - 0.5) * 0.04, -0.05 - rng.nextDouble() * 0.06, (rng.nextDouble() - 0.5) * 0.04));
            if ((i & 3) == 0) FX.one(level, ModParticles.AT_SMOKE.get(), p, new Vec3(0, -0.04, 0));
        }
    }

    /** A crimson afterimage of Aatrox, left behind while he moves fast. */
    public static void afterimage(ServerLevel level, LivingEntity e) {
        FX.one(level, ModParticles.AT_GHOST.get(), e.position().add(0, e.getBbHeight() * 0.5, 0), new Vec3(1.0, 0, 0));
    }

    /** Chains of black-red energy spiralling up around something standing at {@code base}. */
    public static void chainWrap(ServerLevel level, Vec3 base, float radius, float height, int count) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            double phi = i * Math.PI * 2 / count + rng.nextDouble() * 0.5;
            Vec3 u = new Vec3(Math.cos(phi), 0, Math.sin(phi));
            Vec3 v = new Vec3(-Math.sin(phi), 0, Math.cos(phi));
            DariusFX.ribbon(level, base, u, v, radius, radius * 0.6f, -height, 0f, (float) (Math.PI * (1.8 + rng.nextDouble() * 0.6)),
                    0.26f + rng.nextFloat() * 0.1f, 14, (i & 1) == 0 ? MoonRibbonOptions.BLOOD : MoonRibbonOptions.RED);
        }
    }
}
