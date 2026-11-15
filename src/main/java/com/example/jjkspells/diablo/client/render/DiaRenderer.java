package com.example.jjkspells.diablo.client.render;

import com.example.jjkspells.diablo.entity.DiaEntityBase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Common shell of the eight controller renderers: always drawn, time in ticks, owner and camera in the local space of the entity. */
abstract class DiaRenderer<T extends DiaEntityBase> extends EntityRenderer<T> {
    protected DiaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(T e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return Gfx.GLOW;
    }

    @Override
    public void render(T e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        Vec3 base = e.getPosition(pt);
        Entity owner = e.getOwnerEntity();
        Vec3 ownerP = owner != null ? owner.getPosition(pt).subtract(base) : Vec3.ZERO;
        draw(e, t, pt, ps, buf, base, Gfx.cam(base), owner, ownerP);
    }

    protected abstract void draw(T e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP);

    /** The caster's hand relative to the entity base. */
    protected static Vec3 handRel(Entity owner, float pt, Vec3 base) {
        return owner == null ? Vec3.ZERO : Gfx.hand(owner, pt).subtract(base);
    }
}
