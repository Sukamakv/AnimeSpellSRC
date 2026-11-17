package com.example.jjkspells.vfx.sprite;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * One-liners for the standard effect kinds, so every spell gets the same look from the same few calls. Pick a {@link Palette} that
 * matches the spell, a scale, and go. Everything here is server side and cheap (queued, batched, one packet per tick per player).
 *
 * <pre>
 *   SpriteFx.burst(level, pos, Palette.CRIMSON, 3f);
 *   SpriteFx.slash(level, pos, lookDirection, 0f, Palette.AZURE, 4f);
 *   SpriteFx.beam(level, from, to, Palette.VIOLET, 1.4f, 6);
 * </pre>
 */
public final class SpriteFx {
    private SpriteFx() {}

    public enum Palette {
        FIRE("fire"), CRIMSON("crimson"), AZURE("azure"), VIOLET("violet"), GOLD("gold"), TOXIC("toxic"), WHITE("white"), ROT("rot"), WIND("wind");
        public final String key;

        Palette(String key) {
            this.key = key;
        }
    }

    public enum Smoke {
        ASH("ash"), VOID("void"), EMBER("ember");
        public final String key;

        Smoke(String key) {
            this.key = key;
        }
    }

    public static final String BURST = "fire_burst", FLAME = "flame_loop", ORB = "energy_orb", SLASH = "slash_arc", RING = "shockwave_ring",
            LIGHTNING = "lightning", IMPACT = "impact_star", CIRCLE = "magic_circle", VORTEX = "vortex", BEAM = "beam_segment",
            SPARKS = "sparkle_burst", GLOW = "glow_pulse", SMOKE_PUFF = "smoke_puff", STREAK = "slash_streak", CLAWS = "claw_rake",
            SHARDS = "shard_burst", CRACKS = "ground_crack", PETALS = "petal_swirl", FRACTURE = "fracture_web", TENDRILS = "tendril_burst";

    private static final String[] KINDS = {BURST, FLAME, ORB, SLASH, RING, LIGHTNING, IMPACT, CIRCLE, VORTEX, BEAM, SPARKS, GLOW,
            STREAK, CLAWS, SHARDS, CRACKS, PETALS, FRACTURE, TENDRILS};

    /** Every effect name the generator makes, for command completion. */
    public static List<String> allNames() {
        List<String> out = new ArrayList<>();
        for (String k : KINDS) for (Palette p : Palette.values()) out.add(k + "_" + p.key);
        for (Smoke s : Smoke.values()) out.add(SMOKE_PUFF + "_" + s.key);
        return out;
    }

    private static String n(String kind, Palette p) {
        return kind + "_" + p.key;
    }

    // ---------------------------------------------------------------------------- one shots

    /** Expanding fireball / energy bloom (billboard). scale 1 is about 3 blocks wide. */
    public static void burst(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(BURST, p), pos).scale(scale).send();
    }

    /** Star flash with sparks: use for hits and impacts. */
    public static void impact(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(IMPACT, p), pos).scale(scale).send();
    }

    /** Ground shockwave ring. scale 1 is about 6 blocks across. */
    public static void ring(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(RING, p), pos.x, pos.y + 0.05, pos.z).scale(scale).send();
    }

    /** Sweeping crescent on the plane with the given normal (a horizontal sweep uses normal = up). roll turns it in that plane. */
    public static void slash(ServerLevel l, Vec3 pos, Vec3 planeNormal, float rollDegrees, Palette p, float scale) {
        SpriteVfxApi.play(l, n(SLASH, p), pos).dir(planeNormal).roll(rollDegrees).scale(scale).send();
    }

    /** Spark shower. */
    public static void sparks(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(SPARKS, p), pos).scale(scale).decor().send();
    }

    /** Dark or ashen billow. */
    public static void smoke(ServerLevel l, Vec3 pos, Smoke s, float scale) {
        SpriteVfxApi.play(l, SMOKE_PUFF + "_" + s.key, pos).scale(scale).decor().send();
    }

    /** Jagged bolt from a to b. */
    public static void lightning(ServerLevel l, Vec3 a, Vec3 b, Palette p, float width) {
        Vec3 d = b.subtract(a);
        SpriteVfxApi.play(l, n(LIGHTNING, p), a).dir(d).length((float) d.length()).scale(width / 6f).send();
    }

    // ---------------------------------------------------------------------------- sharp, designed shapes (preferred over burst/glow)

    /** Thin tapered blade cut on the plane with the given normal. Sharp, almost no halo. scale 1 is about 4.5 blocks long. */
    public static void streak(ServerLevel l, Vec3 pos, Vec3 planeNormal, float rollDegrees, Palette p, float scale) {
        SpriteVfxApi.play(l, n(STREAK, p), pos).dir(planeNormal).roll(rollDegrees).scale(scale).send();
    }

    /** Three parallel claw cuts on the plane with the given normal. */
    public static void claws(ServerLevel l, Vec3 pos, Vec3 planeNormal, float rollDegrees, Palette p, float scale) {
        SpriteVfxApi.play(l, n(CLAWS, p), pos).dir(planeNormal).roll(rollDegrees).scale(scale).send();
    }

    /** Hard-edged fragments flying outward (billboard). */
    public static void shards(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(SHARDS, p), pos).scale(scale).send();
    }

    /** Cracks spreading over the ground from the point. scale 1 is about 7 blocks across. */
    public static void cracks(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(CRACKS, p), pos.x, pos.y + 0.05, pos.z).scale(scale).send();
    }

    /** Petals spiralling out and fading (billboard). */
    public static void petals(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(PETALS, p), pos).scale(scale).decor().send();
    }

    /** Shattered-glass web that appears on a point in the air (billboard). */
    public static void fracture(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(FRACTURE, p), pos).scale(scale).send();
    }

    /** Curling tendrils reaching out from a point (billboard). */
    public static void tendrils(ServerLevel l, Vec3 pos, Palette p, float scale) {
        SpriteVfxApi.play(l, n(TENDRILS, p), pos).scale(scale).send();
    }

    // ---------------------------------------------------------------------------- timed / looping

    /** Straight beam from a to b for 'ticks' ticks. width is in blocks. */
    public static void beam(ServerLevel l, Vec3 a, Vec3 b, Palette p, float width, int ticks) {
        Vec3 d = b.subtract(a);
        SpriteVfxApi.play(l, n(BEAM, p), a).dir(d).length((float) d.length()).scale(width / 1.2f).life(ticks).send();
    }

    /** Rotating rune circle lying flat on the ground. scale 1 is about 7 blocks across. */
    public static void circle(ServerLevel l, Vec3 pos, Palette p, float scale, int ticks) {
        SpriteVfxApi.play(l, n(CIRCLE, p), pos.x, pos.y + 0.04, pos.z).scale(scale).life(ticks).send();
    }

    /** Spiral on the ground. */
    public static void vortex(ServerLevel l, Vec3 pos, Palette p, float scale, int ticks) {
        SpriteVfxApi.play(l, n(VORTEX, p), pos.x, pos.y + 0.06, pos.z).scale(scale).life(ticks).send();
    }

    /** Fixed flame column (upright). */
    public static void flame(ServerLevel l, Vec3 pos, Palette p, float scale, int ticks) {
        SpriteVfxApi.play(l, n(FLAME, p), pos).scale(scale).life(ticks).decor().send();
    }

    /** Pulsing energy sphere that follows an entity. */
    public static void orbOn(ServerLevel l, Entity e, float yOffset, Palette p, float scale, int ticks) {
        SpriteVfxApi.play(l, n(ORB, p), e.getX(), e.getY() + yOffset, e.getZ()).attach(e, yOffset).scale(scale).life(ticks).send();
    }

    /** Soft glow that follows an entity (cheap aura). */
    public static void glowOn(ServerLevel l, Entity e, float yOffset, Palette p, float scale, int ticks) {
        SpriteVfxApi.play(l, n(GLOW, p), e.getX(), e.getY() + yOffset, e.getZ()).attach(e, yOffset).scale(scale).life(ticks).decor().send();
    }

    /** Flame that follows an entity. */
    public static void flameOn(ServerLevel l, Entity e, Palette p, float scale, int ticks) {
        SpriteVfxApi.play(l, n(FLAME, p), e.getX(), e.getY(), e.getZ()).attach(e, 0f).scale(scale).life(ticks).decor().send();
    }
}
