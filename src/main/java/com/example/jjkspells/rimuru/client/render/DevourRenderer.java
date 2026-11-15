package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.DevourBase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.rimuru.client.render.Gfx.*;

/** Predator (a slime sphere with tendrils) and Beelzebuth (a black vortex of souls): one sphere that travels, grows, devours and compresses. */
abstract class DevourRenderer<T extends DevourBase> extends RimRenderer<T> {
    private final float[] main, deep;
    private final boolean dark;

    protected DevourRenderer(EntityRendererProvider.Context ctx, float[] main, float[] deep, boolean dark) {
        super(ctx);
        this.main = main;
        this.deep = deep;
        this.dark = dark;
    }

    @Override
    protected void draw(T e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        float R = e.getRadius();
        float big = R * 0.75f;
        Vec3 hand = owner == null ? v(0, 1.2, 0) : handRel(owner, pt, base);
        float launch = smooth((t - e.tL0) / Math.max(1f, e.tL1 - e.tL0));
        float grow = smooth((t - e.tL1) / Math.max(1f, (e.tCapture - e.tL1) * 0.7f));
        float comp = smooth((t - e.tConsumeEnd) / Math.max(1f, e.tFinal + 6 - e.tConsumeEnd));
        float back = smooth((t - (e.tFinal + 4)) / Math.max(1f, e.tEnd - e.tFinal - 4));
        Vec3 centre = v(0, 0.9 + big * 0.35, 0);
        Vec3 pos = hand.lerp(centre, launch).lerp(hand, back);
        float orbR = lerp(lerp(0.3f, big, grow), 0.7f, comp) * (1f - back * 0.75f);
        float alpha = 1f - back * 0.6f;
        float pulse = 1f + 0.06f * (float) Math.sin(t * 0.9f);
        // ground circle and rings
        float ground = grow * (1f - back);
        Fx.disc(buf, ps, CIRCLE, v(0, 0.05, 0), R * ground * (1f - comp * 0.5f), t * 3f, main, 0.8f * ground);
        Fx.disc(buf, ps, RUNE, v(0, 0.07, 0), R * 0.8f * ground, -t * 2f, deep == null ? main : PALE, 0.6f * ground);
        if (dark) {
            Fx.disc(buf, ps, VORTEX, v(0, 0.09, 0), R * ground, t * 6f, VIOLET, 0.55f * ground);
            Fx.disc(buf, ps, VORTEX, v(0, 0.2 + big * 0.9, 0), R * 0.7f * ground, -t * 5f, PURPLE, 0.35f * ground);
        }
        // tendrils
        if (grow > 0.15f && back < 0.9f) {
            Fx.tendrils(buf, ps, pos, R * (0.5f + 0.5f * grow) * (1f - comp * 0.6f), dark ? 10 : 8, t * 2.2f, dark ? 0.5f : 0.38f, cam, dark ? PURPLE : main, 0.8f * alpha);
        }
        // the sphere
        float[] body = dark ? NIGHT : main;
        Fx.blob(buf, ps, dark ? BLACK : BLOB, pos, orbR * 1.25f * pulse, t * 2f, dark ? WHITE : body, 0.9f * alpha);
        Fx.orb(buf, ps, pos, orbR * 0.95f * pulse, t * 0.02f, dark ? VIOLET : main, 0.45f * alpha);
        Fx.glowTex(buf, ps, dark ? GLOW_VIOLET : GLOW_BLUE, pos, orbR * (dark ? 2.6f : 2.2f), 0f, dark ? VIOLET : main, 0.7f * alpha);
        if (dark) {
            Fx.glowTex(buf, ps, CORONA, pos, orbR * 2.2f, t * 3f, PURPLE, 0.6f * alpha);
        }
        // energy streams from the devoured enemies to the caster
        List<double[]> fx = e.fx();
        for (double[] d : fx) {
            if (d[0] != 1) {
                continue;
            }
            float life = 1f - (t - (float) d[1]) / 10f;
            if (life <= 0f) {
                continue;
            }
            Vec3 from = v(d[2], d[3], d[4]).subtract(base);
            Fx.beam(buf, ps, SOULWISP, from, hand, cam, 0.18f, 3f, dark ? VIOLET : CYAN, 0.7f * life);
            Vec3 mid = from.lerp(hand, 1f - life);
            Fx.glow(buf, ps, mid, 0.5f, WHITE, 0.8f * life);
        }
        // the flash of the final compression
        float fl = 1f - Math.abs(t - e.tFinal) / 7f;
        if (fl > 0f) {
            Fx.star(buf, ps, pos, orbR * 4f, t * 6f, WHITE, fl);
            Fx.wave(buf, ps, v(0, 0.3, 0), R * 1.5f * (1f - fl) + 1f, main, fl * 0.8f);
        }
    }
}
