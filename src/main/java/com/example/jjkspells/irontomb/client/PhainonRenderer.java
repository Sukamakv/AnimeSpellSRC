package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.PhainonAttack;
import com.example.jjkspells.irontomb.entity.PhainonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Draws Phainon: the player model with his skin, then every wing, halo and attack effect procedurally. */
public class PhainonRenderer extends MobRenderer<PhainonEntity, PhainonModel> {
    private static final ResourceLocation SKIN = new ResourceLocation(JJKSpells.MODID, "textures/entity/phainon.png");
    public static final float SCALE = 1.2f;

    public PhainonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PhainonModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0f);
    }

    @Override
    public ResourceLocation getTextureLocation(PhainonEntity e) {
        return SKIN;
    }

    @Override
    protected void scale(PhainonEntity e, PoseStack ps, float pt) {
        ps.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected boolean shouldShowName(PhainonEntity e) {
        return false;
    }

    @Override
    public void render(PhainonEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        boolean gone = e.getEvent() == PhainonAttack.EV_DEATH && e.getEvT() >= 108;
        if (!gone) {
            super.render(e, yaw, pt, ps, buf, e.isAwake() ? 0xF000F0 : light);
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(e.getPosition(pt));
        PhainonFx.draw(ps, buf, cam, e, pt);
    }
}
