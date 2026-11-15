package com.example.jjkspells.overlord.client.model;

import com.example.jjkspells.JJKSpells;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * Hand-built model of the Black Goat with a Thousand Young: a lumpy mass on eight hoofed legs, a horned head,
 * six writhing tentacles and a scatter of eyes. Texture sheet is 256x256; the region (0,200)-(40,216) is reserved
 * for the glowing eyes and (80,120)-(144,140) for the teeth.
 */
public class BlackGoatModel<T extends Mob> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(JJKSpells.id("black_goat"), "main");
    private static final int TENTACLES = 6, LEGS = 8;

    private final ModelPart root, head;
    private final ModelPart[] legs = new ModelPart[LEGS];
    private final ModelPart[][] tentacle = new ModelPart[TENTACLES][3];

    public BlackGoatModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        for (int i = 0; i < LEGS; i++) {
            legs[i] = root.getChild("leg" + i);
        }
        for (int i = 0; i < TENTACLES; i++) {
            ModelPart a = root.getChild("tent" + i);
            ModelPart b = a.getChild("seg1");
            ModelPart c = b.getChild("seg2");
            tentacle[i] = new ModelPart[]{a, b, c};
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // main mass (y is up = negative; ground is y=24)
        r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-20, -22, -22, 40, 24, 46)
                .texOffs(0, 70).addBox(-14, -34, -14, 28, 14, 30), PartPose.offset(0, 2, 0));
        // lumpy extra growths
        r.addOrReplaceChild("lumps", CubeListBuilder.create().texOffs(0, 0).addBox(-24, -16, -6, 8, 14, 14)
                .texOffs(0, 0).addBox(16, -18, 6, 8, 14, 14).texOffs(0, 70).addBox(-8, -38, 4, 16, 8, 16), PartPose.offset(0, 2, 0));
        // head, horns, maw
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 120).addBox(-10, -9, -18, 20, 18, 20), PartPose.offset(0, 8, -22));
        head.addOrReplaceChild("hornL", CubeListBuilder.create().texOffs(180, 0).addBox(0, -4, -2, 5, 5, 16).texOffs(180, 0).addBox(3, -12, 8, 4, 12, 4),
                PartPose.offsetAndRotation(8, -8, -8, -0.3f, 0.4f, 0.2f));
        head.addOrReplaceChild("hornR", CubeListBuilder.create().texOffs(180, 0).mirror().addBox(-5, -4, -2, 5, 5, 16).texOffs(180, 0).mirror().addBox(-7, -12, 8, 4, 12, 4),
                PartPose.offsetAndRotation(-8, -8, -8, -0.3f, -0.4f, -0.2f));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(80, 120).addBox(-8, 0, -17, 16, 4, 16)
                .texOffs(80, 120).addBox(-7, -3, -17, 2, 3, 2).texOffs(80, 120).addBox(5, -3, -17, 2, 3, 2)
                .texOffs(80, 120).addBox(-2, -3, -17, 2, 3, 2), PartPose.offset(0, 9, 0));
        // eyes: tiny emissive cubes scattered over the head, mass and hump
        CubeListBuilder eyes = CubeListBuilder.create().texOffs(0, 200)
                .addBox(-7, -5, -19, 4, 3, 1).addBox(3, -5, -19, 4, 3, 1).addBox(-3, -1, -19, 3, 3, 1)
                .addBox(-9, -8, -17, 2, 2, 1).addBox(7, -8, -17, 2, 2, 1);
        head.addOrReplaceChild("eyes", eyes, PartPose.ZERO);
        r.addOrReplaceChild("bodyEyes", CubeListBuilder.create().texOffs(0, 200)
                .addBox(-21, -12, 4, 1, 4, 4).addBox(20, -14, -8, 1, 4, 4).addBox(-6, -35, -4, 4, 2, 3).addBox(3, -35, 6, 4, 2, 3)
                .addBox(-12, -20, -23, 4, 3, 1).addBox(8, -22, -23, 4, 3, 1).addBox(-4, -35, 12, 3, 3, 1).addBox(10, -8, 25, 4, 4, 1)
                .addBox(-14, -8, 25, 4, 4, 1), PartPose.offset(0, 2, 0));
        // eight legs, four pairs, with hooves
        float[] zs = {-17f, -6f, 6f, 17f};
        for (int i = 0; i < LEGS; i++) {
            boolean left = (i % 2) == 0;
            float z = zs[i / 2];
            float x = left ? 17f : -17f;
            r.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(150, 70).addBox(-4, 0, -4, 8, 14, 8)
                    .texOffs(150, 95).addBox(-5, 14, -5, 10, 6, 10), PartPose.offset(x + (left ? 2f : -2f), 4, z));
        }
        // six tentacles: three segments each, chained
        for (int i = 0; i < TENTACLES; i++) {
            float ang = (float) (Math.PI * 2 * i / TENTACLES);
            float tx = (float) Math.cos(ang) * 14f, tz = (float) Math.sin(ang) * 16f;
            PartDefinition a = r.addOrReplaceChild("tent" + i, CubeListBuilder.create().texOffs(0, 170).addBox(-3, -16, -3, 6, 16, 6),
                    PartPose.offsetAndRotation(tx, -10, tz + 2, 0.25f * Mth.cos(ang), 0, -0.25f * Mth.sin(ang)));
            PartDefinition b = a.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(0, 170).addBox(-2.5f, -14, -2.5f, 5, 14, 5), PartPose.offset(0, -16, 0));
            b.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 170).addBox(-2, -12, -2, 4, 12, 4)
                    .texOffs(80, 120).addBox(-3, -16, -3, 6, 4, 6), PartPose.offset(0, -14, 0));
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        head.yRot = headYaw * Mth.DEG_TO_RAD * 0.6f;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.6f;
        for (int i = 0; i < LEGS; i++) {
            float phase = (i % 2 == 0 ? 0f : Mth.PI) + (i / 2) * Mth.HALF_PI;
            legs[i].xRot = Mth.cos(limbSwing * 0.6f + phase) * 0.8f * limbSwingAmount;
        }
        for (int i = 0; i < TENTACLES; i++) {
            float ph = i * 1.3f;
            tentacle[i][0].zRot = 0.35f * Mth.sin(age * 0.07f + ph) + (i % 2 == 0 ? 0.4f : -0.4f);
            tentacle[i][0].xRot = 0.35f * Mth.cos(age * 0.06f + ph);
            tentacle[i][1].zRot = 0.5f * Mth.sin(age * 0.09f + ph + 1f);
            tentacle[i][1].xRot = 0.4f * Mth.cos(age * 0.08f + ph + 0.5f);
            tentacle[i][2].zRot = 0.6f * Mth.sin(age * 0.11f + ph + 2f);
            tentacle[i][2].xRot = 0.5f * Mth.cos(age * 0.1f + ph + 1.5f);
        }
    }
}
