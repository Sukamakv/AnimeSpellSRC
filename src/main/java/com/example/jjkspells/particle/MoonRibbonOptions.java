package com.example.jjkspells.particle;

import com.example.jjkspells.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Describes one curved, tapering ribbon: a Moon Breathing slash or one of the small blue crescents riding on it.
 *
 * The ribbon follows a spiral drawn in a plane spanned by two unit vectors u and v (plus an optional sideways
 * "lift" along u x v, which turns the arc into a helix):
 *
 *     point(p) = u * cos(a) * r + v * sin(a) * r + (u x v) * lift * p     for p = 0..1
 *     where a = lerp(th0, th1, p) and r = lerp(r0, r1, p)
 *
 * It sweeps on from its start, lingers, then its tail shrinks away over {@code life} ticks.
 *
 * mode RIBBON:   the plane is fixed in the world (u, v), the ribbon is drawn facing the camera. Used for slashes.
 * mode CRESCENT: u, v are ignored; the plane always faces the camera, rotated by {@code roll}. Used for crescents.
 */
public class MoonRibbonOptions implements ParticleOptions {
    public static final int RIBBON = 0;
    public static final int CRESCENT = 1;
    /** Like RIBBON (fixed plane u, v) but the width runs along the plane instead of facing the camera: lies flat on the ground. */
    public static final int FLAT = 2;
    public static final int PURPLE = 0;
    public static final int BLUE = 1;
    public static final int SUN = 2;
    public static final int CRIMSON = 3;
    /** Darius palettes: crimson, bright red with a white-hot core, brushed steel, dark blood red. */
    public static final int RED = 4;
    public static final int BRIGHT_RED = 5;
    public static final int STEEL = 6;
    public static final int DARK_RED = 7;
    /** Viego palettes: spectral emerald, and Black Mist (near-black teal with a sickly green core). */
    public static final int GREEN = 8;
    public static final int DARK_GREEN = 9;
    /** Aatrox palette: near-black edge, dark blood crimson body, hot red core. */
    public static final int BLOOD = 10;

    private static final int FIELDS = 16;

    public static final Codec<MoonRibbonOptions> CODEC =
            Codec.FLOAT.listOf().xmap(MoonRibbonOptions::fromList, MoonRibbonOptions::toList);

    public static final ParticleOptions.Deserializer<MoonRibbonOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public MoonRibbonOptions fromCommand(ParticleType<MoonRibbonOptions> type, StringReader reader) throws CommandSyntaxException {
            return crescent(0.6f, 3.6f, 0f, 0.25f, 12);   // /particle jjk_spells:moon_ribbon  -> a small crescent
        }

        @Override
        public MoonRibbonOptions fromNetwork(ParticleType<MoonRibbonOptions> type, FriendlyByteBuf buf) {
            return read(buf);
        }
    };

    public final int mode, palette, life;
    public final float ux, uy, uz, vx, vy, vz;
    public final float r0, r1, lift, th0, th1, width, roll;

    public MoonRibbonOptions(int mode, int palette, int life, float ux, float uy, float uz, float vx, float vy, float vz,
                             float r0, float r1, float lift, float th0, float th1, float width, float roll) {
        this.mode = mode;
        this.palette = palette;
        this.life = life;
        this.ux = ux; this.uy = uy; this.uz = uz;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.r0 = r0; this.r1 = r1; this.lift = lift;
        this.th0 = th0; this.th1 = th1;
        this.width = width;
        this.roll = roll;
    }

    /** A purple slash. th0 -> th1 is the direction it sweeps in. {@code width} is its full width at the thickest point. */
    public static MoonRibbonOptions ribbon(Vec3 u, Vec3 v, float r0, float r1, float lift, float th0, float th1,
                                           float width, int life) {
        return new MoonRibbonOptions(RIBBON, PURPLE, life, (float) u.x, (float) u.y, (float) u.z,
                (float) v.x, (float) v.y, (float) v.z, r0, r1, lift, th0, th1, width, 0f);
    }

    /** A slash ribbon in any palette (PURPLE, RED, BRIGHT_RED, STEEL, DARK_RED, ...). */
    public static MoonRibbonOptions ribbon(Vec3 u, Vec3 v, float r0, float r1, float lift, float th0, float th1,
                                           float width, int life, int palette) {
        return new MoonRibbonOptions(RIBBON, palette, life, (float) u.x, (float) u.y, (float) u.z,
                (float) v.x, (float) v.y, (float) v.z, r0, r1, lift, th0, th1, width, 0f);
    }

    /** A ribbon that keeps its width inside its plane, so a horizontal one lies flat on the ground and is seen as a strip from above. */
    public static MoonRibbonOptions flat(Vec3 u, Vec3 v, float r0, float r1, float th0, float th1, float width, int life, int palette) {
        return new MoonRibbonOptions(FLAT, palette, life, (float) u.x, (float) u.y, (float) u.z,
                (float) v.x, (float) v.y, (float) v.z, r0, r1, 0f, th0, th1, width, 0f);
    }

    /** A flame slash of Sun Breathing. palette is SUN (orange-gold) or CRIMSON (red-white). */
    public static MoonRibbonOptions flame(int palette, Vec3 u, Vec3 v, float r0, float r1, float lift, float th0, float th1,
                                          float width, int life) {
        return new MoonRibbonOptions(RIBBON, palette, life, (float) u.x, (float) u.y, (float) u.z,
                (float) v.x, (float) v.y, (float) v.z, r0, r1, lift, th0, th1, width, 0f);
    }

    /** A blue crescent that faces the camera. {@code span} is how many radians of circle it covers (about 3.6 looks like a moon). */
    public static MoonRibbonOptions crescent(float radius, float span, float roll, float width, int life) {
        return new MoonRibbonOptions(CRESCENT, BLUE, life, 1f, 0f, 0f, 0f, 1f, 0f,
                radius, radius, 0f, -span * 0.5f, span * 0.5f, width, roll);
    }

    // ---- ParticleOptions ----------------------------------------------------------------------

    @Override
    public ParticleType<?> getType() {
        return ModParticles.MOON_RIBBON.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeVarInt(palette);
        buf.writeVarInt(life);
        for (float f : new float[]{ux, uy, uz, vx, vy, vz, r0, r1, lift, th0, th1, width, roll}) buf.writeFloat(f);
    }

    @Override
    public String writeToString() {
        return String.valueOf(ForgeRegistries.PARTICLE_TYPES.getKey(getType()));
    }

    private static MoonRibbonOptions read(FriendlyByteBuf buf) {
        int mode = buf.readVarInt();
        int palette = buf.readVarInt();
        int life = buf.readVarInt();
        float[] f = new float[13];
        for (int i = 0; i < f.length; i++) f[i] = buf.readFloat();
        return new MoonRibbonOptions(mode, palette, life, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], f[11], f[12]);
    }

    // ---- codec (only used if the options are ever stored, e.g. in an area effect cloud) --------

    private List<Float> toList() {
        return List.of((float) mode, (float) palette, (float) life, ux, uy, uz, vx, vy, vz, r0, r1, lift, th0, th1, width, roll);
    }

    private static MoonRibbonOptions fromList(List<Float> l) {
        if (l.size() < FIELDS) return crescent(0.6f, 3.6f, 0f, 0.25f, 12);
        return new MoonRibbonOptions(Math.round(l.get(0)), Math.round(l.get(1)), Math.round(l.get(2)),
                l.get(3), l.get(4), l.get(5), l.get(6), l.get(7), l.get(8),
                l.get(9), l.get(10), l.get(11), l.get(12), l.get(13), l.get(14), l.get(15));
    }
}
