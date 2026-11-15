package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.LaevatainFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import static com.example.jjkspells.client.render.FxDraw.FULL_BRIGHT;
import static com.example.jjkspells.client.render.FxDraw.c;
import static com.example.jjkspells.client.render.FxDraw.ease;
import static com.example.jjkspells.client.render.FxDraw.lerp;

/**
 * Draws everything Laevatain conjures, entirely from procedural geometry and her own textures: jagged magma rocks,
 * eruption pillars, the crescent slash waves, the greatsword Sviga Lævi, explosions, ground cracks and flame walls.
 */
public class LaevatainFxRenderer extends EntityRenderer<LaevatainFxEntity> {
    private static final ResourceLocation ROCK = JJKSpells.id("textures/entity/lae_rock.png");
    private static final ResourceLocation BLADE = JJKSpells.id("textures/entity/lae_blade.png");
    private static final ResourceLocation CRACKS = JJKSpells.id("textures/entity/lae_cracks.png");
    private static final ResourceLocation FIRE = JJKSpells.id("textures/entity/fx_fire.png");
    private static final ResourceLocation SMOKE = JJKSpells.id("textures/entity/fx_smoke.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");
    private static final ResourceLocation SHOCK = JJKSpells.id("textures/entity/fx_shock.png");

    public LaevatainFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(LaevatainFxEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(LaevatainFxEntity entity) {
        return ROCK;
    }

    private static VertexConsumer additive(MultiBufferSource b, ResourceLocation t) { return b.getBuffer(RenderType.eyes(t)); }
    private static VertexConsumer emissive(MultiBufferSource b, ResourceLocation t) { return b.getBuffer(RenderType.entityTranslucentEmissive(t)); }
    private static VertexConsumer lit(MultiBufferSource b, ResourceLocation t) { return b.getBuffer(com.example.jjkspells.client.SoftRender.entity(t)); }
    private static VertexConsumer solid(MultiBufferSource b, ResourceLocation t) { return b.getBuffer(RenderType.entityCutoutNoCull(t)); }

    @Override
    public void render(LaevatainFxEntity fx, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float t = fx.tickCount + pt;
        float s = Mth.lerp(pt, fx.oScale, fx.curScale);
        float yaw = Mth.rotLerp(pt, fx.yRotO, fx.getYRot());
        int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), 8), Math.max(LightTexture.sky(packedLight), 12));

        ps.pushPose();
        switch (fx.getKind()) {
            case LaevatainFxEntity.FRAGMENT -> fragment(fx, t, s, ps, buf, light);
            case LaevatainFxEntity.PILLAR -> pillar(fx, t, s, ps, buf, light);
            case LaevatainFxEntity.CRESCENT -> {
                ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                crescent(fx, t, s, ps, buf);
            }
            case LaevatainFxEntity.SWORD -> sword(fx, t, s, yaw, pt, ps, buf, light);
            case LaevatainFxEntity.BURST -> burst(fx, t, s, ps, buf, light);
            case LaevatainFxEntity.CRACKS -> cracks(fx, t, s, yaw, ps, buf);
            case LaevatainFxEntity.WALL -> {
                ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                wall(fx, t, s, ps, buf);
            }
            default -> {
            }
        }
        ps.popPose();
        super.render(fx, entityYaw, pt, ps, buf, packedLight);
    }

    // =================================================================================================================
    //  Magma fragment
    // =================================================================================================================
    private void fragment(LaevatainFxEntity fx, float t, float s, PoseStack ps, MultiBufferSource buf, int light) {
        boolean big = fx.getAux() >= 1f;
        float fade = Mth.clamp((fx.getLife() - t) / 6f, 0f, 1f);

        // flame and smoke trail pointing back along the direction of travel
        double mx = fx.getX() - fx.xo, my = fx.getY() - fx.yo, mz = fx.getZ() - fx.zo;
        double speed = Math.sqrt(mx * mx + my * my + mz * mz);
        if (speed > 0.15) {
            float len = (float) Math.min(speed * 5.0, big ? 11.0 : 6.0) * Math.max(0.6f, s * 0.5f);
            ps.pushPose();
            ps.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) (-mx / speed), (float) (-my / speed), (float) (-mz / speed)));
            final float ss = s;
            FxDraw.tube(ps.last(), lit(buf, SMOKE), 0f, len * 1.2f, k -> ss * (0.9f + 1.0f * k), 12, 5, 2f, 3f, t * 0.01f, -t * 0.05f,
                    60, 50, 46, 140f * fade, 0f, light);
            FxDraw.tube(ps.last(), additive(buf, FIRE), 0f, len, k -> ss * (0.85f - 0.7f * k), 12, 5, 2f, 3f, t * 0.01f, -t * 0.12f,
                    255, 150, 55, 230f * fade, 0f, FULL_BRIGHT);
            ps.popPose();
        }

        // the rock(s)
        ps.pushPose();
        ps.scale(s, s, s);
        ps.mulPose(Axis.YP.rotationDegrees(t * 19f));
        ps.mulPose(Axis.XP.rotationDegrees(t * 11f));
        LaevatainDraw.rock(ps, solid(buf, ROCK), RockMesh.get(fx.getId()), 1f, light, 255);
        if (big) {
            for (int i = 0; i < 5; i++) {
                ps.pushPose();
                float a = t * 0.45f + i * 1.2566f;
                ps.translate(Mth.cos(a) * 1.9f, Mth.sin(t * 0.3f + i * 2f) * 0.8f, Mth.sin(a) * 1.9f);
                ps.scale(0.38f, 0.38f, 0.38f);
                ps.mulPose(Axis.YP.rotationDegrees(t * 29f + i * 40f));
                LaevatainDraw.rock(ps, solid(buf, ROCK), RockMesh.get(fx.getId() + 1 + i), 1f, light, 255);
                ps.popPose();
            }
        }
        ps.popPose();

        FxDraw.billboard(ps, additive(buf, GLOW), this.entityRenderDispatcher.cameraOrientation(), s * (big ? 3.4f : 2.6f), t * 4f,
                255, 130, 45, c(200f * fade), FULL_BRIGHT);
    }

    // =================================================================================================================
    //  Magma pillar
    // =================================================================================================================
    private void pillar(LaevatainFxEntity fx, float t, float s, PoseStack ps, MultiBufferSource buf, int light) {
        final float life = fx.getLife();
        final float H = fx.getAux();
        final float collapseLen = Math.max(1f, fx.getAux2());
        final float c = collapseLen > 1f ? FxDraw.smooth((t - (life - collapseLen)) / collapseLen) : 0f;
        final float rise = ease(t / 5f);
        final float h = Math.max(0.05f, H * rise * (1f - 0.88f * c));
        final float vis = 1f - c;
        final int id = fx.getId();
        PoseStack.Pose pose = ps.last();

        // ground glow
        FxDraw.annulus(pose, additive(buf, SHOCK), s * 0.4f, s * 2.4f, 0.12f, 20, 4f, 0f, 255, 140, 40, c(210f * vis * rise), FULL_BRIGHT);

        // rock body: jagged, tapering, with molten seams
        FxDraw.tube(pose, solid(buf, ROCK), -1.0f, h, k -> s * (1.18f - 0.55f * k) * (1f + 0.16f * Mth.sin(k * 21f + id)), 7, 7,
                2f, Math.max(1f, H / 4f), id * 0.13f, 0f, 255, 255, 255, 255f, 255f, light);

        // molten core and the fire above
        FxDraw.tube(pose, additive(buf, FIRE), -0.5f, h * 1.02f, k -> s * 0.58f * (1f - 0.55f * k), 10, 6, 2f, Math.max(1f, H / 4f), 0f, -t * 0.06f,
                255, 150, 50, 230f * vis, 230f * vis, FULL_BRIGHT);
        FxDraw.tube(pose, additive(buf, FIRE), h * 0.55f, h * 1.55f + 1f, k -> s * (0.9f - 0.78f * k), 10, 6, 2f, 2f, t * 0.01f, -t * 0.09f,
                255, 110, 35, 200f * vis, 0f, FULL_BRIGHT);

        // heat shimmer around it
        FxDraw.tube(pose, emissive(buf, SHOCK), 0f, h * 1.7f, k -> s * (1.9f - 1.0f * k), 14, 4, 3f, 1f, t * 0.01f, -t * 0.04f,
                255, 235, 215, 30f * vis, 6f * vis, FULL_BRIGHT);

        FxDraw.billboard(ps, additive(buf, GLOW), this.entityRenderDispatcher.cameraOrientation(), s * 2.6f, t * 3f,
                255, 140, 50, c(170f * vis * rise), FULL_BRIGHT);

        // the eruption flings rocks out and up...
        if (t < 16f) {
            for (int i = 0; i < 6; i++) {
                float a = i * 1.0472f + id;
                float out = s * (0.9f + t * 0.33f), up = H * 0.25f + t * 1.1f - 0.1f * t * t;
                if (up < -0.5f) continue;
                ps.pushPose();
                ps.translate(Mth.cos(a) * out, up, Mth.sin(a) * out);
                ps.scale(s * 0.32f, s * 0.32f, s * 0.32f);
                ps.mulPose(Axis.YP.rotationDegrees(t * 33f + i * 50f));
                LaevatainDraw.rock(ps, solid(buf, ROCK), RockMesh.get(i + id), 1f, light, 255);
                ps.popPose();
            }
        }
        // ...and when it collapses it breaks into big falling pieces and sinks into smoke
        if (c > 0f) {
            for (int i = 0; i < 7; i++) {
                float a = i * 0.8976f + id * 0.7f;
                float out = s * (0.8f + 2.8f * c) * (0.7f + 0.3f * (i % 3));
                float up = H * (0.85f - 0.8f * c * c) * (1f - i * 0.09f);
                ps.pushPose();
                ps.translate(Mth.cos(a) * out, Math.max(0.1f, up), Mth.sin(a) * out);
                float sc = s * 0.55f * (1f - 0.55f * c);
                ps.scale(sc, sc, sc);
                ps.mulPose(Axis.YP.rotationDegrees(t * 21f + i * 60f));
                ps.mulPose(Axis.XP.rotationDegrees(c * 220f + i * 30f));
                LaevatainDraw.rock(ps, solid(buf, ROCK), RockMesh.get(i + 3), 1f - c, light, 255);
                ps.popPose();
            }
            ps.pushPose();
            ps.translate(0f, s * (0.8f + 1.5f * c), 0f);
            FxDraw.ellipsoid(ps.last(), lit(buf, SMOKE), s * (1.2f + 2.4f * c), s * (0.9f + 1.2f * c), s * (1.2f + 2.4f * c), 10, 18, 2f, 1.5f,
                    t * 0.003f, -t * 0.006f, 55, 48, 45, c(150f * Mth.sin(c * 3.14159f)), light);
            ps.popPose();
        }
    }

    // =================================================================================================================
    //  Crescent slash
    // =================================================================================================================
    private void crescent(LaevatainFxEntity fx, float t, float s, PoseStack ps, MultiBufferSource buf) {
        float fade = Mth.clamp((fx.getLife() - t) / 6f, 0f, 1f) * ease(t / 2f);
        float H = Math.max(1f, fx.getAux());
        PoseStack.Pose pose = ps.last();
        LaevatainDraw.crescent(pose, additive(buf, FIRE), s, H, s * 0.62f, 9, -t * 0.08f, 255, 240, 200, 235, 70, 25, 235f * fade, FULL_BRIGHT);
        LaevatainDraw.crescent(pose, additive(buf, FIRE), s * 0.99f, H * 1.25f, s * 1.5f, 5, t * 0.05f, 255, 160, 70, 150, 20, 18, 120f * fade, FULL_BRIGHT);
        FxDraw.billboard(ps, additive(buf, GLOW), this.entityRenderDispatcher.cameraOrientation(), s * 1.1f, t * 2f, 255, 120, 40, c(110f * fade), FULL_BRIGHT);
    }

    // =================================================================================================================
    //  Sviga Lævi, the spectral greatsword
    // =================================================================================================================
    private void sword(LaevatainFxEntity fx, float t, float L, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        boolean dark = fx.getAux() >= 1f;
        float fade = Mth.clamp((fx.getLife() - t) / 12f, 0f, 1f) * Mth.clamp(t / 3f, 0f, 1f);
        float pitch = Mth.lerp(pt, fx.oAngle, fx.curAngle);
        float roll = Mth.lerp(pt, fx.oRoll, fx.curRoll);
        if (L < 0.05f) {
            return;
        }
        int fr = dark ? 170 : 255, fg = dark ? 30 : 120, fb = dark ? 25 : 40;   // flame colour

        // glow sprites are drawn un-rotated, at where the blade actually is
        double th = Math.toRadians(-yaw), pr = Math.toRadians(pitch);
        float dx = (float) (Math.sin(pr) * Math.sin(th)), dy = (float) Math.cos(pr), dz = (float) (Math.sin(pr) * Math.cos(th));

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(roll));
        PoseStack.Pose pose = ps.last();

        // the steel
        LaevatainDraw.sword(pose, emissive(buf, BLADE), L, dark ? 215 : 255, dark ? 190 : 255, dark ? 190 : 255, c(238f * fade), FULL_BRIGHT);

        // white-hot cutting edges
        VertexConsumer glow = additive(buf, GLOW);
        LaevatainDraw.edgeGlow(pose, glow, L, 1f, 0.02f * L, 255, dark ? 170 : 245, dark ? 130 : 205, c(255f * fade), FULL_BRIGHT);
        LaevatainDraw.edgeGlow(pose, glow, L, -1f, 0.02f * L, 255, dark ? 170 : 245, dark ? 130 : 205, c(255f * fade), FULL_BRIGHT);

        // flames licking up the blade: ribbons at three angles so they never go edge-on
        VertexConsumer flames = additive(buf, FIRE);
        for (int k = 0; k < 3; k++) {
            float a = k * 1.0472f + t * 0.03f;
            FxDraw.strip(pose, flames, Mth.cos(a), Mth.sin(a), 0.2f * L, 0.05f * L, 0.05f * L, 1.12f * L, -t * 0.045f, 1f - t * 0.045f,
                    fr, fg, fb, c(170f * fade), 0, FULL_BRIGHT);
        }
        final float LL = L;
        FxDraw.tube(pose, additive(buf, FIRE), 0.03f * L, 1.02f * L, k -> LL * (0.17f - 0.14f * k), 12, 8, 2f, 4f, 0f, -t * 0.07f,
                fr, fg, fb, 120f * fade, 20f * fade, FULL_BRIGHT);

        // embers rising off the steel
        for (int i = 0; i < 10; i++) {
            float k = ((t * 0.018f * (1f + (i % 3) * 0.3f) + i * 0.173f) % 1f);
            float a = i * 2.399f + t * 0.05f;
            ps.pushPose();
            ps.translate(Mth.cos(a) * 0.17f * L, k * 1.1f * L, Mth.sin(a) * 0.05f * L);
            float sc = 0.028f * L * (1f - k * 0.6f);
            ps.scale(sc, sc, sc);
            ps.mulPose(Axis.YP.rotationDegrees(t * 30f + i * 40f));
            LaevatainDraw.rock(ps, solid(buf, ROCK), RockMesh.get(i), 1f, light, c(255f * fade));
            ps.popPose();
        }
        ps.popPose();

        // halos along the blade
        VertexConsumer halo = additive(buf, GLOW);
        Quaternionf cam = this.entityRenderDispatcher.cameraOrientation();
        for (int i = 0; i < 3; i++) {
            float at = 0.3f + 0.35f * i;
            ps.pushPose();
            ps.translate(dx * L * at, dy * L * at, dz * L * at);
            FxDraw.billboard(ps, halo, cam, L * (0.32f + 0.05f * i), t * 2f + i * 40f, fr, fg + 30, fb + 20, c(150f * fade), FULL_BRIGHT);
            ps.popPose();
        }
    }

    // =================================================================================================================
    //  Burst (explosion)
    // =================================================================================================================
    private void burst(LaevatainFxEntity fx, float t, float s, PoseStack ps, MultiBufferSource buf, int light) {
        float life = Math.max(8f, fx.getLife());
        float p = Mth.clamp(t / life, 0f, 1f);
        int v = (int) fx.getAux();
        int r = 255, g = v == 0 ? 150 : v == 1 ? 80 : 240, b = v == 0 ? 55 : v == 1 ? 45 : 215;
        PoseStack.Pose pose = ps.last();
        Quaternionf cam = this.entityRenderDispatcher.cameraOrientation();

        if (t < 6f) {
            FxDraw.billboard(ps, additive(buf, GLOW), cam, s * 1.9f * ease(t / 3f), t * 9f, 255, 240, 210, c(255f * (1f - t / 6f)), FULL_BRIGHT);
        }
        float dome = s * ease(t / 11f);
        if (p < 0.8f) {
            FxDraw.ellipsoid(pose, additive(buf, SHOCK), dome, dome * 0.8f, dome, 12, 32, 6f, 1f, 0f, 0f, r, g, b, c(140f * (1f - ease(t / (life * 0.7f)))), FULL_BRIGHT);
        }
        float ring = s * ease(t / 13f);
        FxDraw.annulus(pose, additive(buf, SHOCK), Math.max(0f, ring - s * 0.22f), ring + 0.1f, 0.25f, 40, Math.max(3f, ring * 0.3f), 0f,
                r, g, b, c(210f * (1f - p)), FULL_BRIGHT);

        float core = s * 0.58f * ease(t / 8f);
        FxDraw.ellipsoid(pose, additive(buf, FIRE), core, core * 0.9f, core, 14, 28, 3f, 2f, t * 0.004f, -t * 0.012f, r, g - 20, b - 20,
                c(255f * (1f - ease((t - 3f) / (life - 3f)))), FULL_BRIGHT);

        if (s >= 3f) {
            ps.pushPose();
            ps.translate(0f, s * (0.25f + 0.35f * ease(t / life)), 0f);
            float sm = s * (0.45f + 0.5f * ease(t / (life * 0.8f)));
            FxDraw.ellipsoid(ps.last(), lit(buf, SMOKE), sm, sm * 0.75f, sm, 12, 24, 3f, 2f, t * 0.002f, -t * 0.006f, 58, 50, 48,
                    c(170f * ease(t / 8f) * (1f - ease((t - life * 0.4f) / (life * 0.6f)))), light);
            ps.popPose();
        }
    }

    // =================================================================================================================
    //  Ground cracks
    // =================================================================================================================
    private void cracks(LaevatainFxEntity fx, float t, float s, float yaw, PoseStack ps, MultiBufferSource buf) {
        float spread = Math.max(1f, fx.getAux2() > 0 ? fx.getAux2() : 10f);
        float r = s * ease(t / spread);
        float a = Math.min(1f, t / 4f) * Mth.clamp((fx.getLife() - t) / 24f, 0f, 1f);
        float pulse = 0.82f + 0.18f * Mth.sin(t * 0.35f);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(fx.getId() * 47f));
        LaevatainDraw.groundQuad(ps.last(), additive(buf, CRACKS), Math.max(0.05f, r * 1.12f), 0.07f, 255, 255, 255, c(255f * a * pulse), FULL_BRIGHT);
        ps.popPose();

        if (fx.getAux() >= 1f && r > 1f) {
            PoseStack.Pose pose = ps.last();
            for (int i = 0; i < 3; i++) {
                final float rr = r * (0.3f + 0.3f * i);
                FxDraw.tube(pose, additive(buf, FIRE), 0f, 1.6f + 0.5f * Mth.sin(t * 0.3f + i), k -> rr * (1f - 0.12f * k), 22, 2, 5f, 1f, i * 0.2f, -t * 0.06f,
                        255, 120, 40, 120f * a, 0f, FULL_BRIGHT);
            }
        }
    }

    // =================================================================================================================
    //  Wall of flames + scorch marks along the path of the great slash
    // =================================================================================================================
    private void wall(LaevatainFxEntity fx, float t, float s, PoseStack ps, MultiBufferSource buf) {
        float len = Math.max(1f, fx.getAux());
        PoseStack.Pose pose = ps.last();
        // scorch marks stay a long while and fade slowly
        float scorch = Mth.clamp((fx.getLife() - t) / 60f, 0f, 1f) * ease(t / 6f);
        LaevatainDraw.groundStrip(pose, lit(buf, SMOKE), len, s * 1.3f, 0.08f, 6f, 0f, 22, 18, 16, c(215f * scorch), FULL_BRIGHT);
        LaevatainDraw.groundStrip(pose, additive(buf, GLOW), len, s * 1.1f, 0.06f, 20f, 0f, 255, 90, 30, c(110f * scorch), FULL_BRIGHT);

        // the flames themselves only burn at the start
        float f = ease(t / 4f) * (1f - ease((t - 18f) / 9f));
        if (f > 0.01f && fx.getAux2() < 0.5f) {   // aux2 = 1 means scorch marks only
            float h = 5.5f * f;
            for (int i = -1; i <= 1; i++) {
                LaevatainDraw.sheet(pose, additive(buf, FIRE), len, h, i * 0.9f, 6f, -t * 0.12f + i * 0.3f, -t * 0.05f, 255, 140, 50, c(230f * f), c(20f * f), FULL_BRIGHT);
            }
        }
    }
}
