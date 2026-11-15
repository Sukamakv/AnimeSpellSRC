package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.enchant.FlameSlashConfig;
import com.example.jjkspells.enchant.FlameSlashEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.example.jjkspells.client.SoftRender;

import static com.example.jjkspells.client.render.FxDraw.FULL_BRIGHT;

/** Draws the flame slash: two crossed flame streaks, 2 wide and 6 long, with a bright head. */
public class FlameSlashRenderer extends EntityRenderer<FlameSlashEntity> {
    private static final ResourceLocation TEX = JJKSpells.id("textures/entity/flame_slash.png");

    public FlameSlashRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(FlameSlashEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(FlameSlashEntity e) {
        return TEX;
    }

    @Override
    public void render(FlameSlashEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Vec3 dir = e.dir();
        Vec3 chord = FlameSlashEntity.chord(dir);
        double depth = FlameSlashConfig.length() * 0.5 * (170.0 / 297.0); // keep the sprite's proportions
        double half = FlameSlashConfig.length() * 0.5;
        // smooth flight: computed per frame from the tick count, not from (rounded, per-tick) entity position packets
        double d = e.travelled(e.tickCount + pt);
        ps.pushPose();
        ps.translate(dir.x * d, dir.y * d + 0.25, dir.z * d);
        // texture: convex edge (right) leads the flight, tall axis is the long edge
        GilDraw.quad(ps, buf.getBuffer(SoftRender.entity(TEX)), Vec3.ZERO, dir.scale(depth), chord.scale(half), 255, 255, 255, 255, FULL_BRIGHT);
        ps.popPose();
    }
}
