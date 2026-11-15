package com.example.jjkspells.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One configurable particle class behind all of Fuga's effects: flames, fireballs, embers, glows,
 * shock rings (flat on the ground or facing the camera) and soot smoke. What each one looks like
 * is decided by its {@link Style}, which is attached when the particle type is registered.
 *
 * Velocity fields sent by the server are the particle's velocity, except for styles with
 * sizeFromVx (rings and glows), where the X value is the particle's final radius instead.
 */
public class FireParticle extends TextureSheetParticle {

    public static class Style {
        float minSize = 1, maxSize = 1, endScale = 1;
        int minLife = 20, maxLife = 20;
        float gravity = 0, friction = 0.96f, alpha = 1f, fadeIn = 0.08f, fadeOut = 0.55f, spin = 0.03f;
        float[] c0 = {1, 1, 1}, c1 = {1, 1, 1};
        boolean additive = true, flat = false, sizeFromVx = false, expand = false, fullbright = true, twinkle = false, noRoll = false;

        public Style size(float min, float max, float endScale) { this.minSize = min; this.maxSize = max; this.endScale = endScale; return this; }
        public Style life(int min, int max) { this.minLife = min; this.maxLife = max; return this; }
        public Style physics(float gravity, float friction) { this.gravity = gravity; this.friction = friction; return this; }
        public Style fade(float alpha, float fadeIn, float fadeOut) { this.alpha = alpha; this.fadeIn = fadeIn; this.fadeOut = fadeOut; return this; }
        public Style colors(float r0, float g0, float b0, float r1, float g1, float b1) { c0 = new float[]{r0, g0, b0}; c1 = new float[]{r1, g1, b1}; return this; }
        public Style spin(float s) { this.spin = s; return this; }
        public Style flat() { this.flat = true; return this; }
        public Style radiusFromVx() { this.sizeFromVx = true; return this; }
        public Style expanding() { this.expand = true; return this; }
        public Style twinkle() { this.twinkle = true; return this; }
        /** Keep the sprite axis-aligned (no random rotation): used for the cross-shaped glint. */
        public Style noRoll() { this.noRoll = true; return this; }
        public Style translucent() { this.additive = false; this.fullbright = false; return this; }
    }

    // ---- The looks -----------------------------------------------------------------------------
    /** Medium tongue of fire: starts white-yellow, cools to deep red. */
    public static final Style FLAME = new Style().size(0.5f, 1.2f, 0.35f).life(12, 26).physics(-0.15f, 0.92f)
            .fade(0.95f, 0.06f, 0.45f).colors(1f, 0.92f, 0.62f, 0.85f, 0.12f, 0.0f).spin(0.05f);
    /** Huge billowing fireball used for the blast front and the mushroom column. */
    public static final Style BLAST = new Style().size(2.4f, 4.6f, 1.5f).life(16, 30).physics(-0.05f, 0.9f)
            .fade(0.8f, 0.10f, 0.35f).colors(1f, 0.85f, 0.5f, 0.75f, 0.10f, 0.0f).spin(0.02f);
    /** Tiny hot spark that arcs and falls. */
    public static final Style EMBER = new Style().size(0.07f, 0.18f, 0.2f).life(30, 70).physics(0.35f, 0.97f)
            .fade(1f, 0.02f, 0.5f).colors(1f, 0.85f, 0.4f, 1f, 0.2f, 0.0f).spin(0f);
    /** Short, sharp flash. Radius comes from the velocity X value. */
    public static final Style GLOW = new Style().size(1, 1, 1.6f).life(7, 9).radiusFromVx()
            .fade(1f, 0.15f, 0.2f).colors(1f, 0.95f, 0.8f, 1f, 0.45f, 0.1f).spin(0f);
    /** Expanding ring lying on the ground. Final radius comes from the velocity X value. */
    // ---- Viego (spectral green flames and the Black Mist) -------------------------------------------
    public static final Style VG_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(12, 12)
            .fade(1f, 0.02f, 0.5f).colors(0.75f, 1f, 0.88f, 0.1f, 0.7f, 0.35f).spin(0f);
    public static final Style VG_SHOCK = new Style().expanding().radiusFromVx().life(8, 10)
            .fade(1f, 0.02f, 0.4f).colors(0.8f, 1f, 0.9f, 0.12f, 0.75f, 0.4f).spin(0f);
    public static final Style VG_GLOW = new Style().size(1, 1, 1.5f).life(5, 7).radiusFromVx()
            .fade(1f, 0.1f, 0.3f).colors(0.85f, 1f, 0.92f, 0.15f, 0.8f, 0.4f).spin(0f);
    public static final Style VG_HIT = new Style().size(1, 1, 1.3f).life(5, 7).radiusFromVx()
            .fade(1f, 0.06f, 0.35f).colors(0.9f, 1f, 0.95f, 0.15f, 0.85f, 0.45f).spin(0f);
    public static final Style VG_SPARK = new Style().size(0.07f, 0.17f, 0.2f).life(10, 18).physics(0.3f, 0.92f)
            .fade(1f, 0.02f, 0.4f).colors(0.85f, 1f, 0.9f, 0.1f, 0.8f, 0.4f).spin(0f);
    public static final Style VG_GATHER = new Style().size(0.06f, 0.13f, 0.3f).life(8, 10).physics(0f, 1f)
            .fade(1f, 0.1f, 0.4f).colors(0.7f, 1f, 0.85f, 0.1f, 0.7f, 0.35f).spin(0f);
    /** Rises and flickers out. */
    public static final Style VG_FLAME = new Style().size(0.25f, 0.5f, 0.35f).life(10, 16).physics(-0.03f, 0.94f)
            .fade(0.9f, 0.2f, 0.5f).colors(0.8f, 1f, 0.85f, 0.08f, 0.7f, 0.32f).spin(0.05f);
    /** Dark translucent mist: normal blending so it shows against bright backgrounds. */
    public static final Style VG_MIST = new Style().size(1.2f, 2.4f, 2.6f).life(30, 50).physics(-0.003f, 0.93f)
            .fade(0.5f, 0.15f, 0.5f).colors(0.10f, 0.36f, 0.28f, 0.03f, 0.16f, 0.12f).spin(0.015f).translucent();
    /** The runic circle on the ground. It lives 4 ticks; spawn it again to keep it up. */
    public static final Style VG_CIRCLE = new Style().flat().size(1, 1, 1f).radiusFromVx().life(4, 4)
            .fade(0.9f, 0.2f, 0.5f).colors(0.4f, 1f, 0.6f, 0.2f, 0.9f, 0.5f).spin(0f).noRoll();
    /** The soul silhouette. Radius from velocity X. */
    public static final Style VG_SOUL = new Style().size(1, 1, 1f).radiusFromVx().life(3, 3)
            .fade(0.75f, 0.3f, 0.4f).colors(0.55f, 1f, 0.75f, 0.2f, 0.9f, 0.5f).spin(0f).noRoll();
    public static final Style VG_SHARD = new Style().size(0.1f, 0.22f, 0.3f).life(14, 22).physics(0.5f, 0.92f)
            .fade(1f, 0.05f, 0.4f).colors(0.85f, 1f, 0.92f, 0.15f, 0.85f, 0.45f).spin(0.25f);

    public static final Style RING_FLAT = new Style().flat().expanding().radiusFromVx().life(14, 18)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.9f, 0.6f, 1f, 0.3f, 0.05f).spin(0f);
    /** Expanding ring facing the camera (reads as a spherical shock shell). */
    public static final Style RING = new Style().expanding().radiusFromVx().life(10, 13)
            .fade(1f, 0.02f, 0.35f).colors(1f, 0.95f, 0.8f, 1f, 0.4f, 0.1f).spin(0f);
    /** Heavy soot smoke that climbs slowly and spreads. Normal (non-glowing) blending. */
    public static final Style SMOKE = new Style().size(2.6f, 5.0f, 1.9f).life(70, 120).physics(-0.04f, 0.96f)
            .fade(0.5f, 0.12f, 0.45f).colors(0.16f, 0.12f, 0.11f, 0.03f, 0.03f, 0.03f).spin(0.012f).translucent();

    // ---- Hollow Purple -------------------------------------------------------------------------
    /** White-hot core, re-emitted every tick so it looks solid. Radius from velocity X. */
    public static final Style HP_CORE = new Style().size(1, 1, 0.9f).life(2, 3).radiusFromVx()
            .fade(1f, 0.05f, 0.5f).colors(1f, 0.98f, 1f, 0.9f, 0.75f, 1f).spin(0f);
    public static final Style HP_GLOW = new Style().size(1, 1, 1.15f).life(2, 3).radiusFromVx()
            .fade(0.55f, 0.1f, 0.4f).colors(0.7f, 0.25f, 1f, 0.55f, 0.1f, 0.95f).spin(0f);
    public static final Style HP_GLOW_BLUE = new Style().size(1, 1, 1.1f).life(2, 3).radiusFromVx()
            .fade(0.8f, 0.1f, 0.4f).colors(0.45f, 0.7f, 1f, 0.2f, 0.4f, 1f).spin(0f);
    public static final Style HP_GLOW_RED = new Style().size(1, 1, 1.1f).life(2, 3).radiusFromVx()
            .fade(0.8f, 0.1f, 0.4f).colors(1f, 0.45f, 0.4f, 1f, 0.12f, 0.12f).spin(0f);
    public static final Style HP_WISP = new Style().size(0.35f, 0.8f, 0.2f).life(10, 20).physics(0f, 0.9f)
            .fade(0.95f, 0.06f, 0.4f).colors(1f, 0.85f, 1f, 0.5f, 0.1f, 0.95f).spin(0.08f);
    public static final Style HP_WISP_BLUE = new Style().size(0.3f, 0.7f, 0.2f).life(8, 16).physics(0f, 0.9f)
            .fade(0.95f, 0.06f, 0.4f).colors(0.8f, 0.95f, 1f, 0.2f, 0.4f, 1f).spin(0.08f);
    public static final Style HP_WISP_RED = new Style().size(0.3f, 0.7f, 0.2f).life(8, 16).physics(0f, 0.9f)
            .fade(0.95f, 0.06f, 0.4f).colors(1f, 0.85f, 0.8f, 1f, 0.1f, 0.1f).spin(0.08f);
    public static final Style HP_RING = new Style().expanding().radiusFromVx().life(9, 14)
            .fade(1f, 0.02f, 0.35f).colors(0.95f, 0.8f, 1f, 0.55f, 0.15f, 1f).spin(0f);
    public static final Style HP_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(14, 20)
            .fade(1f, 0.02f, 0.4f).colors(0.9f, 0.7f, 1f, 0.5f, 0.1f, 0.95f).spin(0f);
    /** Dark violet smudge left behind the orb: the "distortion". Normal blending so it darkens the view. */
    public static final Style HP_VOID = new Style().size(1.4f, 2.6f, 1.8f).life(18, 34).physics(0f, 0.9f)
            .fade(0.6f, 0.12f, 0.35f).colors(0.25f, 0.05f, 0.45f, 0.04f, 0.0f, 0.1f).spin(0.03f).translucent();

    // ---- Unlimited Void / Simple Domain ----------------------------------------------------------
    /** Drifting twinkling star. */
    public static final Style UV_STAR = new Style().size(0.12f, 0.3f, 0.4f).life(50, 90).physics(0f, 0.99f)
            .fade(1f, 0.15f, 0.5f).colors(1f, 1f, 1f, 1f, 1f, 1f).spin(0.02f).twinkle();
    /** Star that rushes inward during the charge (no twinkle, short life). */
    public static final Style UV_STAR_IN = new Style().size(0.1f, 0.22f, 0.3f).life(16, 26).physics(0f, 1.0f)
            .fade(1f, 0.1f, 0.6f).colors(1f, 1f, 1f, 1f, 1f, 1f).spin(0.05f);
    /** Star lying on the floor. */
    public static final Style UV_FLOOR = new Style().flat().size(0.25f, 0.55f, 0.4f).life(40, 80).physics(0f, 0.98f)
            .fade(1f, 0.15f, 0.5f).colors(1f, 1f, 1f, 1f, 1f, 1f).spin(0.02f).twinkle();
    public static final Style UV_GLOW = new Style().size(1, 1, 1.1f).life(2, 3).radiusFromVx()
            .fade(0.6f, 0.1f, 0.4f).colors(0.75f, 0.88f, 1f, 0.5f, 0.7f, 1f).spin(0f);
    /** Solid black disc: the heart of the "void" seed. */
    public static final Style UV_CORE = new Style().size(1, 1, 1f).life(2, 3).radiusFromVx().translucent()
            .fade(1f, 0.05f, 0.5f).colors(0f, 0f, 0.03f, 0f, 0f, 0.03f).spin(0f);
    public static final Style UV_RING = new Style().expanding().radiusFromVx().life(9, 14)
            .fade(1f, 0.02f, 0.35f).colors(0.9f, 0.95f, 1f, 0.4f, 0.6f, 1f).spin(0f);
    public static final Style UV_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(16, 24)
            .fade(1f, 0.02f, 0.4f).colors(0.85f, 0.92f, 1f, 0.35f, 0.55f, 1f).spin(0f);
    public static final Style SD_STAR = new Style().size(0.1f, 0.26f, 0.4f).life(30, 60).physics(0f, 0.98f)
            .fade(1f, 0.15f, 0.5f).colors(1f, 0.97f, 0.8f, 1f, 0.85f, 0.5f).spin(0.02f).twinkle();
    public static final Style SD_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(14, 22)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.95f, 0.7f, 1f, 0.8f, 0.35f).spin(0f);

    // ---- The Ruler of Death's Crimson Wrath -------------------------------------------------------
    /** Quick cross-shaped flash, like a star glint or a sniper-scope glint. Radius from velocity X. */
    public static final Style CW_GLINT = new Style().size(1, 1, 0.55f).life(5, 8).radiusFromVx()
            .fade(1f, 0.12f, 0.3f).colors(1f, 0.88f, 0.86f, 1f, 0.2f, 0.2f).spin(0f).noRoll();

    // ---- Death's Wraiths -------------------------------------------------------------------------
    public static final Style GHOST_GLOW = new Style().size(1, 1, 0.9f).life(8, 14).radiusFromVx()
            .fade(0.8f, 0.05f, 0.5f).colors(0.8f, 0.82f, 1f, 0.25f, 0.2f, 0.4f).spin(0f);

    // ---- World Cutting Slash ---------------------------------------------------------------------
    public static final Style WC_GLOW = new Style().size(1, 1, 1.2f).life(4, 6).radiusFromVx()
            .fade(1f, 0.1f, 0.3f).colors(1f, 1f, 1f, 1f, 0.75f, 0.7f).spin(0f);
    public static final Style WC_RING = new Style().expanding().radiusFromVx().life(9, 13)
            .fade(1f, 0.02f, 0.35f).colors(1f, 1f, 1f, 0.9f, 0.15f, 0.12f).spin(0f);
    public static final Style WC_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(14, 20)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.95f, 0.9f, 0.85f, 0.1f, 0.08f).spin(0f);


    // ---- Za Warudo: Time Stop ------------------------------------------------------------------
    public static final Style TS_GLOW = new Style().size(1, 1, 1.4f).life(6, 9).radiusFromVx()
            .fade(1f, 0.15f, 0.4f).colors(1f, 0.95f, 0.7f, 1f, 0.6f, 0.15f).spin(0f);
    public static final Style TS_MOTE = new Style().size(0.25f, 0.6f, 0.1f).life(30, 60).physics(-0.01f, 0.97f)
            .fade(0.95f, 0.1f, 0.5f).colors(1f, 0.95f, 0.6f, 1f, 0.55f, 0.1f).spin(0f).twinkle();
    public static final Style TS_STAR = new Style().size(1, 1, 0.4f).life(8, 12).radiusFromVx()
            .fade(1f, 0.1f, 0.5f).colors(1f, 1f, 0.85f, 1f, 0.7f, 0.2f).spin(0f).noRoll().twinkle();
    public static final Style TS_GEAR = new Style().size(0.5f, 1.1f, 0.7f).life(35, 70).physics(-0.012f, 0.97f)
            .fade(0.9f, 0.08f, 0.5f).colors(1f, 0.85f, 0.4f, 1f, 0.6f, 0.15f).spin(0.1f);
    public static final Style TS_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(20, 24)
            .fade(1f, 0.03f, 0.5f).colors(1f, 0.95f, 0.7f, 1f, 0.6f, 0.15f).spin(0f);
    public static final Style TS_RING = new Style().expanding().radiusFromVx().life(14, 18)
            .fade(1f, 0.03f, 0.4f).colors(1f, 0.95f, 0.8f, 1f, 0.6f, 0.15f).spin(0f);
    public static final Style TS_NUMERAL = new Style().size(0.8f, 1.5f, 0.9f).life(50, 90).physics(-0.006f, 0.98f)
            .fade(0.95f, 0.15f, 0.4f).colors(1f, 0.95f, 0.7f, 1f, 0.7f, 0.25f).spin(0f).noRoll();

    // ---- Azure Colossus / Azure Orb Barrage (blue chakra fire) -----------------------------------
    /** Medium tongue of blue fire: white-cyan core cooling to deep blue. */
    public static final Style AZURE_FLAME = new Style().size(0.6f, 1.5f, 0.35f).life(14, 30).physics(-0.12f, 0.92f)
            .fade(0.65f, 0.06f, 0.45f).colors(0.7f, 0.9f, 1f, 0.06f, 0.22f, 0.9f).spin(0.05f);
    /** Huge billowing blue fireball for the orb explosions. */
    public static final Style AZURE_BLAST = new Style().size(2.8f, 5.2f, 1.5f).life(16, 32).physics(-0.05f, 0.9f)
            .fade(0.85f, 0.10f, 0.35f).colors(0.8f, 0.95f, 1f, 0.08f, 0.22f, 0.9f).spin(0.02f);
    /** Giant slow flame that licks around the colossus' body. */
    public static final Style AZURE_AURA = new Style().size(2.6f, 5.5f, 0.5f).life(20, 38).physics(-0.2f, 0.94f)
            .fade(0.4f, 0.15f, 0.4f).colors(0.5f, 0.8f, 1f, 0.04f, 0.15f, 0.8f).spin(0.03f);
    public static final Style AZURE_EMBER = new Style().size(0.08f, 0.22f, 0.2f).life(30, 70).physics(0.25f, 0.97f)
            .fade(1f, 0.02f, 0.5f).colors(0.75f, 0.92f, 1f, 0.15f, 0.4f, 1f).spin(0f);
    public static final Style AZURE_GLOW = new Style().size(1, 1, 1.6f).life(7, 9).radiusFromVx()
            .fade(1f, 0.15f, 0.2f).colors(0.92f, 0.98f, 1f, 0.3f, 0.6f, 1f).spin(0f);
    public static final Style AZURE_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(14, 22)
            .fade(1f, 0.02f, 0.4f).colors(0.85f, 0.95f, 1f, 0.15f, 0.4f, 1f).spin(0f);
    public static final Style AZURE_RING = new Style().expanding().radiusFromVx().life(10, 14)
            .fade(1f, 0.02f, 0.35f).colors(0.9f, 0.97f, 1f, 0.2f, 0.45f, 1f).spin(0f);

    // ---- Martial arts (Barrage of Punches, Ground Smash, Hard Strike) ----------------------------
    /** Comic-style impact starburst: white-hot, cooling to orange. Radius from velocity X. */
    public static final Style MA_IMPACT = new Style().size(1, 1, 1.3f).life(5, 7).radiusFromVx()
            .fade(1f, 0.06f, 0.35f).colors(1f, 0.97f, 0.82f, 1f, 0.55f, 0.15f).spin(0f);
    /** Short bright flash behind an impact. Radius from velocity X. */
    public static final Style MA_GLOW = new Style().size(1, 1, 1.5f).life(4, 6).radiusFromVx()
            .fade(1f, 0.1f, 0.3f).colors(1f, 0.97f, 0.88f, 1f, 0.6f, 0.2f).spin(0f);
    /** A glowing clenched fist that flashes in and fades. Stays upright. Radius from velocity X. */
    public static final Style MA_FIST = new Style().size(1, 1, 1.25f).life(4, 5).radiusFromVx()
            .fade(1f, 0.05f, 0.45f).colors(1f, 0.95f, 0.8f, 1f, 0.5f, 0.15f).spin(0f).noRoll();
    /** Shock ring facing the camera. Final radius from velocity X. */
    public static final Style MA_SHOCK_RING = new Style().expanding().radiusFromVx().life(7, 9)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.95f, 0.8f, 1f, 0.55f, 0.2f).spin(0f);
    /** Shock ring on the ground. Lasts exactly as long as Ground Smash's front takes to reach full radius. */
    public static final Style MA_SHOCK_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(10, 10)
            .fade(1f, 0.02f, 0.45f).colors(1f, 0.96f, 0.82f, 0.95f, 0.55f, 0.2f).spin(0f);
    /** Cracks in the ground (keeps the texture's own dark colours). Radius from velocity X. */
    public static final Style MA_CRACKS = new Style().flat().size(1, 1, 1f).radiusFromVx().life(50, 64).translucent()
            .fade(1f, 0.04f, 0.65f).colors(1f, 1f, 1f, 1f, 1f, 1f).spin(0f);
    /** Soft dust puff that rises, spreads and fades. */
    public static final Style MA_DUST = new Style().size(0.8f, 1.5f, 2.3f).life(16, 28).physics(-0.012f, 0.9f)
            .fade(0.6f, 0.1f, 0.4f).colors(0.8f, 0.72f, 0.6f, 0.55f, 0.5f, 0.45f).spin(0.03f).translucent();
    /** Twinkling yellow star for stunned targets. */
    public static final Style MA_STAR = new Style().size(0.1f, 0.18f, 0.5f).life(8, 11).physics(0f, 0.98f)
            .fade(1f, 0.1f, 0.4f).colors(1f, 0.95f, 0.45f, 1f, 0.8f, 0.2f).spin(0.1f).twinkle();
    /** Tiny hot spark that arcs and falls. */
    public static final Style MA_SPARK = new Style().size(0.07f, 0.16f, 0.2f).life(8, 16).physics(0.5f, 0.9f)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.95f, 0.7f, 1f, 0.5f, 0.1f).spin(0f);

    // ---- Moon Breathing ---------------------------------------------------------------------------
    /** Spiky star on the ground (keeps its own lavender colours). Radius from velocity X. */
    public static final Style MOON_BURST = new Style().flat().size(1, 1, 1.12f).radiusFromVx().life(14, 16).translucent()
            .fade(1f, 0.08f, 0.5f).colors(1f, 1f, 1f, 1f, 1f, 1f).spin(0f);
    /** Violet-white flash. Radius from velocity X. */
    public static final Style MOON_FLASH = new Style().size(1, 1, 1.5f).life(5, 7).radiusFromVx()
            .fade(1f, 0.1f, 0.3f).colors(0.93f, 0.88f, 1f, 0.5f, 0.55f, 1f).spin(0f);
    /** Violet starburst at a cut. Radius from velocity X. */
    public static final Style MOON_HIT = new Style().size(1, 1, 1.3f).life(5, 7).radiusFromVx()
            .fade(1f, 0.06f, 0.35f).colors(0.97f, 0.93f, 1f, 0.55f, 0.45f, 1f).spin(0f);
    /** Tiny violet-blue spark that arcs and falls. */
    public static final Style MOON_SPARK = new Style().size(0.07f, 0.16f, 0.2f).life(10, 18).physics(0.3f, 0.92f)
            .fade(1f, 0.02f, 0.4f).colors(0.92f, 0.86f, 1f, 0.45f, 0.65f, 1f).spin(0f);

    // ---- Jotaro / Star Platinum: royal purple, indigo, electric blue, cyan, white --------------------------------
    public static final Style SP_GLOW = new Style().size(1, 1, 1.5f).life(6, 9).radiusFromVx()
            .fade(1f, 0.12f, 0.3f).colors(0.85f, 0.7f, 1f, 0.38f, 0.16f, 0.85f).spin(0f);
    public static final Style SP_HIT = new Style().size(1, 1, 1.3f).life(5, 7).radiusFromVx()
            .fade(1f, 0.05f, 0.4f).colors(1f, 1f, 1f, 0f, 0.8f, 1f).spin(0f);
    public static final Style SP_SPARK = new Style().size(0.07f, 0.17f, 0.2f).life(10, 20).physics(0.1f, 0.92f)
            .fade(1f, 0.03f, 0.45f).colors(0.7f, 1f, 1f, 0.55f, 0.17f, 0.89f).spin(0f);
    public static final Style SP_RING = new Style().expanding().radiusFromVx().life(10, 13)
            .fade(1f, 0.03f, 0.4f).colors(0.8f, 1f, 1f, 0.25f, 0.41f, 0.9f).spin(0f);
    public static final Style SP_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(16, 22)
            .fade(1f, 0.03f, 0.45f).colors(0.7f, 0.95f, 1f, 0.54f, 0.17f, 0.89f).spin(0f);
    /** A clenched fist: the afterimage of Star Platinum's punches. */
    public static final Style SP_FIST = new Style().size(1, 1, 1.15f).life(4, 6).radiusFromVx()
            .fade(0.9f, 0.1f, 0.6f).colors(0.7f, 0.5f, 1f, 0.19f, 0.12f, 0.5f).spin(0f).noRoll();
    public static final Style SP_STAR = new Style().size(1, 1, 0.4f).life(5, 9).radiusFromVx()
            .fade(1f, 0.05f, 0.5f).colors(1f, 1f, 1f, 0.2f, 0.9f, 1f).spin(0f);

    // ---- Aatrox (crimson, black shadow, blood) ------------------------------------------------------
    /** Blood droplet: normal blending so the dark red shows against bright backgrounds. */
    public static final Style AT_BLOOD = new Style().size(0.08f, 0.18f, 0.3f).life(14, 26).physics(0.9f, 0.96f)
            .fade(1f, 0.02f, 0.3f).colors(0.75f, 0.02f, 0.05f, 0.35f, 0.0f, 0.02f).spin(0.1f).translucent();
    /** Black-red ember falling slowly. */
    public static final Style AT_EMBER = new Style().size(0.07f, 0.2f, 0.15f).life(30, 50).physics(0.12f, 0.98f)
            .fade(1f, 0.05f, 0.6f).colors(1f, 0.35f, 0.2f, 0.25f, 0.02f, 0.02f).spin(0.1f);
    /** Near-black smoke with a red tinge. */
    public static final Style AT_SMOKE = new Style().size(0.9f, 1.8f, 2.4f).life(20, 36).physics(-0.004f, 0.93f)
            .fade(0.6f, 0.1f, 0.5f).colors(0.10f, 0.01f, 0.02f, 0.05f, 0.0f, 0.01f).spin(0.02f).translucent();
    /** Crimson afterimage of a body. Radius from velocity X. */
    public static final Style AT_GHOST = new Style().size(1, 1, 1.1f).radiusFromVx().life(5, 5)
            .fade(0.6f, 0.2f, 0.6f).colors(1f, 0.25f, 0.2f, 0.6f, 0.02f, 0.05f).spin(0f).noRoll();
    /** The runic circle on the ground, in crimson. It lives 4 ticks; spawn it again to keep it up. */
    public static final Style AT_CIRCLE = new Style().flat().size(1, 1, 1f).radiusFromVx().life(4, 4)
            .fade(0.9f, 0.2f, 0.5f).colors(1f, 0.3f, 0.25f, 0.8f, 0.05f, 0.05f).spin(0f).noRoll();

    // ---- Gilgamesh --------------------------------------------------------------------------------
    /** Expanding ripple on the ground (golden runic ring). */
    public static final Style GOLD_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(18, 24)
            .fade(1f, 0.03f, 0.4f).colors(1f, 0.95f, 0.7f, 1f, 0.65f, 0.15f).spin(0f);
    /** Expanding ripple facing the camera. */
    public static final Style GOLD_RING = new Style().expanding().radiusFromVx().life(12, 16)
            .fade(1f, 0.03f, 0.4f).colors(1f, 0.97f, 0.8f, 1f, 0.6f, 0.15f).spin(0f);
    /** Crimson-gold star glint (the King's eyes). */
    public static final Style GOLD_GLINT = new Style().size(1, 1, 0.6f).life(6, 10).radiusFromVx()
            .fade(1f, 0.1f, 0.35f).colors(1f, 0.9f, 0.6f, 1f, 0.15f, 0.1f).spin(0f).noRoll();
    /** Tiny gold spark that arcs and falls. */
    public static final Style GOLD_SPARK = new Style().size(0.08f, 0.2f, 0.2f).life(12, 26).physics(0.12f, 0.92f)
            .fade(1f, 0.03f, 0.45f).colors(1f, 0.95f, 0.65f, 1f, 0.55f, 0.1f).spin(0f);
    /** Soft golden flash. Radius from velocity X. */
    public static final Style GOLD_GLOW = new Style().size(1, 1, 1.5f).life(6, 9).radiusFromVx()
            .fade(1f, 0.12f, 0.3f).colors(1f, 0.95f, 0.7f, 1f, 0.55f, 0.1f).spin(0f);
    /** Sharp geometric fragment that tumbles and falls. */
    public static final Style GOLD_SHARD = new Style().size(0.16f, 0.36f, 0.5f).life(22, 42).physics(0.06f, 0.94f)
            .fade(1f, 0.04f, 0.45f).colors(1f, 0.95f, 0.65f, 1f, 0.55f, 0.1f).spin(0.35f);
    /** Glowing glyph that drifts upward. */
    public static final Style GOLD_RUNE = new Style().size(0.22f, 0.4f, 0.8f).life(24, 42).physics(-0.02f, 0.95f)
            .fade(1f, 0.15f, 0.5f).colors(1f, 0.95f, 0.7f, 1f, 0.6f, 0.15f).spin(0.02f).twinkle();
    /** Red-gold flash for Ea. Radius from velocity X. */
    public static final Style EA_GLOW = new Style().size(1, 1, 1.4f).life(8, 12).radiusFromVx()
            .fade(1f, 0.1f, 0.35f).colors(1f, 0.85f, 0.55f, 1f, 0.1f, 0.05f).spin(0f);

    // ---- Sun Breathing -----------------------------------------------------------------------------
    public static final Style SUN_SPARK = new Style().size(0.08f, 0.2f, 0.2f).life(14, 30).physics(0.2f, 0.93f)
            .fade(1f, 0.03f, 0.45f).colors(1f, 0.95f, 0.6f, 1f, 0.35f, 0.05f).spin(0f);
    public static final Style SUN_FLARE = new Style().size(1, 1, 0.55f).life(5, 8).radiusFromVx()
            .fade(1f, 0.1f, 0.3f).colors(1f, 0.97f, 0.75f, 1f, 0.4f, 0.05f).spin(0f).noRoll();
    public static final Style SUN_RING = new Style().expanding().radiusFromVx().life(10, 14)
            .fade(1f, 0.03f, 0.4f).colors(1f, 0.95f, 0.7f, 1f, 0.35f, 0.05f).spin(0f);
    public static final Style SUN_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(14, 20)
            .fade(1f, 0.03f, 0.4f).colors(1f, 0.9f, 0.55f, 1f, 0.3f, 0.04f).spin(0f);
    public static final Style SUN_EMBER = new Style().size(0.5f, 1.1f, 0.3f).life(12, 24).physics(-0.12f, 0.92f)
            .fade(0.9f, 0.06f, 0.45f).colors(1f, 0.9f, 0.45f, 0.85f, 0.15f, 0.0f).spin(0.05f);
    public static final Style SUN_CIRCLE = new Style().flat().size(1, 1, 1f).radiusFromVx().life(80, 80)
            .fade(1f, 0.08f, 0.6f).colors(1f, 0.9f, 0.55f, 1f, 0.6f, 0.15f).spin(0f);
    public static final Style SUN_FIGURE = new Style().size(1, 1, 1f).life(8, 10).radiusFromVx()
            .fade(0.85f, 0.05f, 0.1f).colors(1f, 0.85f, 0.45f, 1f, 0.3f, 0.05f).spin(0f).noRoll();
    public static final Style SUN_GLOW = new Style().size(1, 1, 1.5f).life(6, 10).radiusFromVx()
            .fade(1f, 0.12f, 0.3f).colors(1f, 0.92f, 0.6f, 1f, 0.4f, 0.05f).spin(0f);

    // ---- Darius (crimson) ---------------------------------------------------------------------
    /** Crimson ring of force on the ground. Lasts as long as Decimate / Guillotine rings take to expand. */
    public static final Style DR_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(12, 12)
            .fade(1f, 0.02f, 0.5f).colors(1f, 0.55f, 0.45f, 0.75f, 0.05f, 0.05f).spin(0f);
    /** Crimson ring of force facing the camera. */
    public static final Style DR_SHOCK = new Style().expanding().radiusFromVx().life(8, 10)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.6f, 0.5f, 0.85f, 0.08f, 0.06f).spin(0f);
    /** Red flash. */
    public static final Style DR_GLOW = new Style().size(1, 1, 1.5f).life(5, 7).radiusFromVx()
            .fade(1f, 0.1f, 0.3f).colors(1f, 0.65f, 0.55f, 0.8f, 0.08f, 0.08f).spin(0f);
    /** Red starburst at a hit. */
    public static final Style DR_HIT = new Style().size(1, 1, 1.3f).life(5, 7).radiusFromVx()
            .fade(1f, 0.06f, 0.35f).colors(1f, 0.85f, 0.8f, 0.9f, 0.1f, 0.08f).spin(0f);
    /** Hot red spark that arcs and falls. */
    public static final Style DR_SPARK = new Style().size(0.07f, 0.17f, 0.2f).life(10, 18).physics(0.4f, 0.9f)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.7f, 0.5f, 0.9f, 0.08f, 0.04f).spin(0f);
    /** Red mote that drifts straight, no gravity: energy streaming into the axe. */
    public static final Style DR_GATHER = new Style().size(0.06f, 0.13f, 0.3f).life(8, 10).physics(0f, 1f)
            .fade(1f, 0.1f, 0.4f).colors(1f, 0.55f, 0.45f, 0.8f, 0.05f, 0.05f).spin(0f);
    /** Steel spark. */
    public static final Style DR_METAL = new Style().size(0.07f, 0.16f, 0.2f).life(8, 14).physics(0.5f, 0.9f)
            .fade(1f, 0.02f, 0.4f).colors(1f, 1f, 1f, 0.6f, 0.65f, 0.72f).spin(0f);
    /** The crimson mark over a head. Re-spawned every tick by DariusMarkManager, so it lives only 3 ticks. */
    public static final Style DR_MARK = new Style().size(0.42f, 0.42f, 1.0f).life(3, 3)
            .fade(1f, 0.3f, 0.5f).colors(1f, 0.3f, 0.25f, 1f, 0.12f, 0.1f).spin(0f).noRoll();
    /** Reddish dust. */
    public static final Style DR_DUST = new Style().size(0.8f, 1.5f, 2.3f).life(16, 28).physics(-0.012f, 0.9f)
            .fade(0.6f, 0.1f, 0.4f).colors(0.72f, 0.42f, 0.38f, 0.45f, 0.25f, 0.22f).spin(0.03f).translucent();

    // ---- Garen: steel + gold ---------------------------------------------------------------------
    public static final Style GR_SPARK = new Style().size(0.08f, 0.2f, 0.2f).life(10, 22).physics(0.3f, 0.95f)
            .fade(1f, 0.02f, 0.5f).colors(1f, 1f, 1f, 0.55f, 0.75f, 1f).spin(0f);
    public static final Style GR_MOTE = new Style().size(0.15f, 0.4f, 0.1f).life(14, 26).physics(0f, 0.9f)
            .fade(0.9f, 0.1f, 0.5f).colors(0.95f, 0.98f, 1f, 0.5f, 0.7f, 1f).spin(0f).twinkle();
    public static final Style GR_GLOW = new Style().size(1, 1, 1.4f).life(5, 8).radiusFromVx()
            .fade(0.9f, 0.15f, 0.3f).colors(1f, 1f, 1f, 0.6f, 0.78f, 1f).spin(0f);
    public static final Style GR_RING = new Style().expanding().radiusFromVx().life(8, 12)
            .fade(1f, 0.02f, 0.35f).colors(1f, 1f, 1f, 0.6f, 0.78f, 1f).spin(0f);
    public static final Style GR_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(12, 18)
            .fade(1f, 0.02f, 0.4f).colors(1f, 1f, 1f, 0.55f, 0.72f, 1f).spin(0f);
    public static final Style GG_GLOW = new Style().size(1, 1, 1.5f).life(7, 10).radiusFromVx()
            .fade(1f, 0.15f, 0.3f).colors(1f, 0.97f, 0.8f, 1f, 0.72f, 0.2f).spin(0f);
    public static final Style GG_RING = new Style().expanding().radiusFromVx().life(10, 14)
            .fade(1f, 0.02f, 0.35f).colors(1f, 0.97f, 0.8f, 1f, 0.7f, 0.18f).spin(0f);
    public static final Style GG_RING_FLAT = new Style().flat().expanding().radiusFromVx().life(14, 22)
            .fade(1f, 0.02f, 0.4f).colors(1f, 0.95f, 0.7f, 1f, 0.68f, 0.15f).spin(0f);
    public static final Style GOLD_MOTE = new Style().size(0.15f, 0.4f, 0.1f).life(20, 40).physics(-0.01f, 0.95f)
            .fade(0.9f, 0.1f, 0.5f).colors(1f, 0.95f, 0.65f, 1f, 0.6f, 0.12f).spin(0f).twinkle();
    public static final Style GG_SHARD = new Style().size(0.12f, 0.3f, 0.2f).life(26, 50).physics(0.04f, 0.96f)
            .fade(1f, 0.04f, 0.5f).colors(1f, 0.95f, 0.6f, 1f, 0.55f, 0.1f).spin(0.15f);
    public static final Style GOLD_CREST = new Style().size(1, 1, 1.15f).life(16, 18).radiusFromVx()
            .fade(1f, 0.15f, 0.45f).colors(1f, 0.95f, 0.75f, 1f, 0.75f, 0.25f).spin(0f).noRoll();

    // ---- Instance --------------------------------------------------------------------------------
    private final Style style;
    private final float baseSize;
    private final float spinSpeed;
    private final float phase;

    protected FireParticle(ClientLevel level, double x, double y, double z,
                           double xd, double yd, double zd, SpriteSet sprites, Style style) {
        super(level, x, y, z, xd, yd, zd);
        this.style = style;
        this.pickSprite(sprites);
        this.lifetime = Mth.nextInt(this.random, style.minLife, style.maxLife);
        if (style.sizeFromVx) {
            this.baseSize = (float) Math.max(0.05, Math.abs(xd));
            this.xd = this.yd = this.zd = 0;
        } else {
            this.baseSize = style.minSize + this.random.nextFloat() * (style.maxSize - style.minSize);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
        }
        this.quadSize = this.baseSize;
        this.gravity = style.gravity;
        this.friction = style.friction;
        this.hasPhysics = false;
        this.roll = this.oRoll = style.noRoll || (style.flat && style.expand) || (style.spin == 0 && style.expand) ? 0f : this.random.nextFloat() * Mth.TWO_PI;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.spinSpeed = (this.random.nextFloat() - 0.5f) * 2f * style.spin;
        this.rCol = style.c0[0];
        this.gCol = style.c0[1];
        this.bCol = style.c0[2];
        this.alpha = 0f;
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.spinSpeed;

        float t = (float) this.age / (float) this.lifetime;
        this.rCol = Mth.lerp(t, style.c0[0], style.c1[0]);
        this.gCol = Mth.lerp(t, style.c0[1], style.c1[1]);
        this.bCol = Mth.lerp(t, style.c0[2], style.c1[2]);

        float a = style.alpha;
        if (t < style.fadeIn) a *= t / style.fadeIn;
        if (t > style.fadeOut) a *= Math.max(0f, 1f - (t - style.fadeOut) / (1f - style.fadeOut));
        if (style.twinkle) a *= 0.55f + 0.45f * Mth.sin(this.age * 0.45f + this.phase);
        this.alpha = Mth.clamp(a, 0f, 1f);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float t = Mth.clamp(((float) this.age + partialTicks) / (float) this.lifetime, 0f, 1f);
        if (style.expand) {
            float inv = 1f - t;
            float ease = 1f - inv * inv * inv; // fast start, slow finish
            return this.baseSize * (0.08f + 0.92f * ease);
        }
        return this.baseSize * Mth.lerp(t, 1f, style.endScale);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (!style.flat) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        // Flat styles: draw on the horizontal plane, visible from above and below
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        float size = this.getQuadSize(partialTicks);
        float r = Mth.lerp(partialTicks, this.oRoll, this.roll);
        float cos = Mth.cos(r) * size, sin = Mth.sin(r) * size;

        float[] px = {-cos + sin, -cos - sin, cos - sin, cos + sin};
        float[] pz = {-sin - cos, -sin + cos, sin + cos, sin - cos};
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        float[] us = {u1, u1, u0, u0};
        float[] vs = {v1, v0, v0, v1};
        int light = this.getLightColor(partialTicks);

        for (int i = 0; i < 4; i++) {
            buffer.vertex(x + px[i], y, z + pz[i]).uv(us[i], vs[i])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        }
        for (int i = 3; i >= 0; i--) {
            buffer.vertex(x + px[i], y, z + pz[i]).uv(us[i], vs[i])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return style.fullbright ? 15728880 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return style.additive ? FugaRenderTypes.ADDITIVE : SoftRender.PARTICLE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xd, double yd, double zd) {
            return new FireParticle(level, x, y, z, xd, yd, zd, sprites, style);
        }
    }
}
