package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.AzureColossus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Model of the Azure Colossus: an armoured spectral giant with a greatsword, built from boxes (about 102 model units
 * tall; the renderer scales it up to 25 blocks) and animated in {@link #setupAnim}.
 *
 * Pose vector layout (see POSE_* indices): the animation system blends keyframed poses for the slash, the barrage and
 * the ground smash on top of the idle / walk cycle.
 */
public class AzureColossusModel extends EntityModel<AzureColossus> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(JJKSpells.id("azure_colossus"), "main");
    /** Renderer scale: about 114 model units (flame hair and wings included) * SCALE / 16 = 25 blocks. */
    public static final float SCALE = 3.5f;

    private final ModelPart root;
    private final ModelPart body, head, hips;
    private final ModelPart armR, forearmR, handR, sword;
    private final ModelPart armL, forearmL;
    private final ModelPart legR, legL;
    private final ModelPart wingR, wingL;
    private final java.util.List<ModelPart> featherR = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> featherL = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> spikes = new java.util.ArrayList<>();
    private static final int FEATHERS = 11;
    private static final int SPIKES = 9;

    public AzureColossusModel(ModelPart root) {
        this.root = root;
        this.hips = root.getChild("hips");
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.armR = body.getChild("arm_r");
        this.forearmR = armR.getChild("forearm");
        this.handR = forearmR.getChild("hand");
        this.sword = handR.getChild("sword");
        this.armL = body.getChild("arm_l");
        this.forearmL = armL.getChild("forearm");
        this.legR = root.getChild("leg_r");
        this.legL = root.getChild("leg_l");
        this.wingR = body.getChild("wing_r");
        this.wingL = body.getChild("wing_l");
        for (int i = 0; i < FEATHERS; i++) {
            featherR.add(wingR.getChild("f" + i));
            featherL.add(wingL.getChild("f" + i));
        }
        for (int i = 0; i < SPIKES; i++) {
            spikes.add(head.getChild("spike" + i));
        }
    }

    // ---- texture regions of the 512x512 sheet ------------------------------------------------------
    private static CubeListBuilder ar(int slot) {   // engraved armour plating
        int[][] o = {{0, 0}, {128, 0}, {0, 128}, {128, 128}};
        return CubeListBuilder.create().texOffs(o[slot & 3][0], o[slot & 3][1]);
    }

    private static CubeListBuilder cloth(int slot) { // dark woven cloth
        int[][] o = {{256, 0}, {384, 0}, {256, 64}, {384, 64}};
        return CubeListBuilder.create().texOffs(o[slot & 3][0], o[slot & 3][1]);
    }

    private static CubeListBuilder flame(int slot) { // flame hair
        int[][] o = {{0, 256}, {128, 256}, {0, 384}, {128, 384}};
        return CubeListBuilder.create().texOffs(o[slot & 3][0], o[slot & 3][1]);
    }

    private static CubeListBuilder feather(int slot) {
        return CubeListBuilder.create().texOffs(256 + (slot % 8) * 0, 256 + (slot % 2) * 64);
    }

    private static CubeListBuilder blade(int slot) {
        return CubeListBuilder.create().texOffs(256 + (slot & 1) * 128, 384);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---- hips: hanging armour skirt (kusazuri) -------------------------------------------------
        CubeListBuilder skirt = ar(2);
        for (int i = 0; i < 5; i++) {
            float x = -17.5f + i * 7f;
            skirt.addBox(x, 0, -10, 6.4f, 17, 2).addBox(x, 0, 8, 6.4f, 15, 2);
        }
        skirt.texOffs(128, 0).addBox(-21, 0, -8, 3, 14, 16).addBox(18, 0, -8, 3, 14, 16);
        root.addOrReplaceChild("hips", skirt, PartPose.offset(0, -12, 0));

        // ---- body: kimono, chest armour, sash -------------------------------------------------------
        PartDefinition body = root.addOrReplaceChild("body", cloth(0)
                        .addBox(-13, -26, -7.5f, 26, 26, 15)                                   // robe
                        .texOffs(0, 0).addBox(-14, -26, -9.5f, 28, 13, 3)                        // chest plate
                        .texOffs(128, 0).addBox(-2, -25, -11, 4, 11, 2)                          // chest ridge
                        .texOffs(0, 128).addBox(-13.5f, -12, -9, 27, 9, 2)                       // lower ribs plate
                        .texOffs(0, 0).addBox(-14, -26, 7.5f, 28, 22, 3)                         // back plate
                        .texOffs(256, 64).addBox(-14.5f, -4, -9, 29, 8, 18)                      // obi sash
                        .texOffs(0, 128).addBox(-4, -4, -11, 8, 8, 2.5f)                         // sash knot
                        .texOffs(256, 0).addBox(-6, 4, -10.5f, 3, 14, 1.5f)                      // sash tails
                        .texOffs(256, 0).addBox(3, 4, -10.5f, 3, 12, 1.5f)
                        .texOffs(128, 128).addBox(-4.5f, -30, -4.5f, 9, 5, 9),                   // neck
                PartPose.offset(0, -16, 0));

        // collar of the crossed robe
        body.addOrReplaceChild("collar_r", cloth(2).addBox(-1.5f, -2, -1, 3, 14, 2.5f),
                PartPose.offsetAndRotation(-5, -26, -9.5f, 0, 0, 0.45f));
        body.addOrReplaceChild("collar_l", cloth(2).addBox(-1.5f, -2, -1, 3, 14, 2.5f),
                PartPose.offsetAndRotation(5, -26, -9.5f, 0, 0, -0.45f));

        // crossed sword straps on the back and a sheathed blade across it
        body.addOrReplaceChild("strap_a", cloth(1).addBox(-1.5f, -14, 0, 3, 28, 1),
                PartPose.offsetAndRotation(0, -14, 10.6f, 0, 0, 0.75f));
        body.addOrReplaceChild("strap_b", cloth(1).addBox(-1.5f, -14, 0, 3, 28, 1),
                PartPose.offsetAndRotation(0, -14, 10.6f, 0, 0, -0.75f));
        PartDefinition backSword = body.addOrReplaceChild("back_sheath", ar(3).addBox(-2, -26, -2, 4, 66, 4),
                PartPose.offsetAndRotation(0, -14, 12.5f, 0.12f, 0, 0.8f));
        backSword.addOrReplaceChild("back_hilt", cloth(3).addBox(-1.5f, -40, -1.5f, 3, 14, 3), PartPose.ZERO);
        backSword.addOrReplaceChild("back_guard", ar(1).addBox(-4.5f, -27, -1.5f, 9, 2, 3), PartPose.ZERO);

        // sheathed blade at the left hip
        PartDefinition hipSheath = body.addOrReplaceChild("hip_sheath", ar(3).addBox(-2, 0, -2, 4, 62, 4),
                PartPose.offsetAndRotation(16, 0, -1, 0.95f, 0, 0.12f));
        hipSheath.addOrReplaceChild("hip_hilt", cloth(3).addBox(-1.5f, -14, -1.5f, 3, 14, 3), PartPose.ZERO);
        hipSheath.addOrReplaceChild("hip_guard", ar(1).addBox(-4.5f, -1, -1.5f, 9, 2, 3), PartPose.ZERO);

        // ---- head: oni-style mask, three eyes, horns, flame hair -----------------------------------------
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(256, 128).addBox(-8, -16, -8, 16, 16, 16)
                        .texOffs(320, 128).addBox(-6.5f, -10, -10, 13, 9, 2)                        // mask plate
                        .texOffs(384, 128).addBox(-5, -2, -11.5f, 10, 4, 2)                         // jaw with fangs
                        .texOffs(0, 0).addBox(-9, -13, -9.5f, 18, 2.5f, 2.5f)                        // brow ridge
                        .texOffs(128, 0).addBox(-8.5f, -18, -8.5f, 17, 3, 17)                        // crown band
                        .texOffs(448, 128).addBox(-1.5f, -15, -10, 3, 3, 1.5f),                      // third eye
                PartPose.offset(0, -30, 0));
        for (int side = -1; side <= 1; side += 2) {
            PartDefinition horn = head.addOrReplaceChild(side < 0 ? "horn_r" : "horn_l", ar(1).addBox(-1.8f, -9, -1.8f, 3.6f, 9, 3.6f),
                    PartPose.offsetAndRotation(side * 8, -13, -3, -0.25f, 0, side * 0.7f));
            horn.addOrReplaceChild("tip", ar(1).addBox(-1.2f, -8, -1.2f, 2.4f, 8, 2.4f),
                    PartPose.offsetAndRotation(0, -9, 0, 0, 0, side * 0.55f));
            head.addOrReplaceChild(side < 0 ? "tusk_r" : "tusk_l", CubeListBuilder.create().texOffs(384, 128).addBox(-1, 0, -1, 2, 5, 2),
                    PartPose.offsetAndRotation(side * 4, 0, -10.5f, 0, 0, side * 0.2f));
        }
        // flame hair: a crown of tapering spikes (x, z, roll, pitch, size)
        float[][] hair = {
                {0, 0, 0, -0.05f, 1.15f}, {-4, 0, -0.3f, -0.05f, 1.0f}, {4, 0, 0.3f, -0.05f, 1.0f},
                {-8, 0, -0.65f, 0, 0.8f}, {8, 0, 0.65f, 0, 0.8f}, {0, 5, 0, 0.45f, 1.0f},
                {-5, 5, -0.3f, 0.4f, 0.85f}, {5, 5, 0.3f, 0.4f, 0.85f}, {0, -5, 0, -0.45f, 0.7f}};
        for (int i = 0; i < SPIKES; i++) {
            float[] h = hair[i];
            float k = h[4];
            PartDefinition sp = head.addOrReplaceChild("spike" + i, flame(i).addBox(-3.5f, -12 * k, -3.5f, 7, 12 * k, 7),
                    PartPose.offsetAndRotation(h[0], -17, h[1], h[3], 0, h[2]));
            PartDefinition mid = sp.addOrReplaceChild("mid", flame(i + 1).addBox(-2.5f, -10 * k, -2.5f, 5, 10 * k, 5),
                    PartPose.offsetAndRotation(0, -12 * k, 0, 0, 0, h[2] * 0.4f));
            mid.addOrReplaceChild("tip", flame(i + 2).addBox(-1.3f, -8 * k, -1.3f, 2.6f, 8 * k, 2.6f),
                    PartPose.offsetAndRotation(0, -10 * k, 0, 0, 0, h[2] * 0.5f));
        }

        // ---- shoulder armour: three stacked plates -------------------------------------------------------
        body.addOrReplaceChild("pad_r", ar(0).addBox(-14, -3, -10, 21, 4, 20).texOffs(128, 0).addBox(-15.5f, 1, -9.5f, 22, 4, 19).texOffs(0, 128).addBox(-17, 5, -9, 23, 4, 18),
                PartPose.offsetAndRotation(-17, -26, 0, 0, 0, -0.22f));
        body.addOrReplaceChild("pad_l", ar(0).addBox(-7, -3, -10, 21, 4, 20).texOffs(128, 0).addBox(-6.5f, 1, -9.5f, 22, 4, 19).texOffs(0, 128).addBox(-6, 5, -9, 23, 4, 18),
                PartPose.offsetAndRotation(17, -26, 0, 0, 0, 0.22f));

        // ---- wings of blue fire feathers ------------------------------------------------------------------
        for (int side = -1; side <= 1; side += 2) {
            PartDefinition wing = body.addOrReplaceChild(side < 0 ? "wing_r" : "wing_l", CubeListBuilder.create(),
                    PartPose.offsetAndRotation(side * 7, -22, 10, 0.3f, side * 0.25f, 0));
            for (int i = 0; i < FEATHERS; i++) {
                float len = i < 7 ? 38 + i * 4.5f : 22 + (i - 7) * 4f;       // long upper row, short lower row
                float ang = i < 7 ? 0.12f + i * 0.17f : 1.35f + (i - 7) * 0.2f;
                wing.addOrReplaceChild("f" + i, feather(i).addBox(-4.2f, -len, -1, 8.4f, len, 2),
                        PartPose.offsetAndRotation(0, i < 7 ? 0 : 6, i % 2 == 0 ? 0 : 1.2f, 0, 0, side * ang));
            }
        }

        // ---- arms: sleeves, bracers, katana -----------------------------------------------------------------
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            PartDefinition arm = body.addOrReplaceChild(right ? "arm_r" : "arm_l", cloth(0).addBox(-4.5f, -2, -4.5f, 9, 20, 9),
                    PartPose.offset(side * 18, -24, 0));
            PartDefinition fore = arm.addOrReplaceChild("forearm", cloth(1).addBox(-4, 0, -4, 8, 18, 8)
                            .texOffs(0, 128).addBox(-5, 2, -5, 10, 12, 10).texOffs(128, 0).addBox(-5.5f, 12, -5.5f, 11, 3, 11),
                    PartPose.offset(0, 18, 0));
            PartDefinition hand = fore.addOrReplaceChild("hand", ar(2).addBox(-3.5f, 0, -3.5f, 7, 7, 7), PartPose.offset(0, 18, 0));
            if (right) {
                PartDefinition sw = hand.addOrReplaceChild("sword", cloth(3).addBox(-1.6f, -9, -1.6f, 3.2f, 18, 3.2f)     // wrapped grip
                                .texOffs(128, 0).addBox(-5, 9, -5, 10, 2, 10),                                               // round guard
                        PartPose.offset(0, 3.5f, 0));
                PartDefinition seg = sw.addOrReplaceChild("b1", blade(0).addBox(-1, 0, -3.2f, 2, 24, 6.4f), PartPose.offset(0, 11, 0));
                seg = seg.addOrReplaceChild("b2", blade(1).addBox(-1, 0, -3.2f, 2, 24, 6.4f), PartPose.offsetAndRotation(0, 24, 0, -0.06f, 0, 0));
                seg = seg.addOrReplaceChild("b3", blade(0).addBox(-1, 0, -3f, 2, 24, 6f), PartPose.offsetAndRotation(0, 24, 0, -0.07f, 0, 0));
                seg.addOrReplaceChild("b4", blade(1).addBox(-0.9f, 0, -2.4f, 1.8f, 12, 4.8f), PartPose.offsetAndRotation(0, 24, 0, -0.1f, 0, 0));
            }
        }

        // ---- legs: baggy hakama, greaves, knee guards, boots -------------------------------------------------
        for (int side = -1; side <= 1; side += 2) {
            root.addOrReplaceChild(side < 0 ? "leg_r" : "leg_l", cloth(2).addBox(-7.5f, 0, -7.5f, 15, 16, 15)
                            .texOffs(256, 0).addBox(-5.5f, 16, -5.5f, 11, 12, 11)
                            .texOffs(0, 0).addBox(-6.5f, 11, -9.5f, 13, 8, 3.5f)                     // knee guard
                            .texOffs(128, 128).addBox(-6.5f, 19, -8, 13, 9, 2.5f)                     // greave
                            .texOffs(0, 128).addBox(-7, 28, -11.5f, 14, 8, 20),                       // boot with toe
                    PartPose.offset(side * 10, -12, 0));
        }
        return LayerDefinition.create(mesh, 512, 512);
    }

    // ================================================================================================
    // Animation
    // ================================================================================================

    // pose vector indices
    private static final int BODY_YAW = 0, BODY_PITCH = 1, BODY_ROLL = 2,
            R_SH_X = 3, R_SH_Y = 4, R_SH_Z = 5, R_ELBOW = 6, SWORD_X = 7,
            L_SH_X = 8, L_SH_Y = 9, L_SH_Z = 10, L_ELBOW = 11,
            HEAD_X = 12, CROUCH = 13, LEG_SPREAD = 14, N = 15;

    private static float[] pose(float... v) {
        float[] p = new float[N];
        System.arraycopy(v, 0, p, 0, Math.min(v.length, N));
        return p;
    }

    //                               bodyY  bodyX  bodyZ  rShX   rShY   rShZ  rElb  sword  lShX   lShY   lShZ   lElb   headX crouch spread
    private static final float[] IDLE = pose(0f, 0.00f, 0f, -0.30f, 0f, -0.10f, -0.55f, -0.30f, 0.12f, 0f, 0.10f, -0.30f, 0f, 0f, 0.05f);

    // Sword slash: wind back to one side, sweep across, follow through, recover (times in ticks)
    private static final float[] SLASH_T = {0, 5, 11, 14, 18, 26};
    private static final float[][] SLASH_K = {
            IDLE,
            pose(1.00f, -0.10f, 0f, -2.20f, -0.60f, -0.70f, -0.45f, -0.30f, -0.40f, 0f, 0.50f, -0.50f, 0f, 2f, 0.20f),
            pose(1.10f, -0.12f, 0f, -2.30f, -0.70f, -0.80f, -0.40f, -0.30f, -0.50f, 0f, 0.55f, -0.50f, 0f, 2f, 0.22f),
            pose(-0.35f, 0.18f, 0f, -1.55f, 0.55f, 0.10f, -0.15f, -0.05f, 0.30f, 0f, 0.40f, -0.30f, 0.1f, 4f, 0.25f),
            pose(-1.10f, 0.20f, 0f, -1.35f, 0.90f, 0.20f, -0.10f, 0.00f, 0.50f, 0f, 0.50f, -0.30f, 0.1f, 4f, 0.25f),
            IDLE
    };

    // Orb barrage: raise both hands, thrust forward three times
    private static final float[] BARRAGE_T = {0, 14, 22, 26, 30, 34, 38, 44, 52, 60};
    private static final float[][] BARRAGE_K = {
            IDLE,
            pose(0f, -0.18f, 0f, -1.95f, 0f, -0.30f, -0.35f, -0.30f, -1.95f, 0f, 0.30f, -0.35f, -0.1f, 0f, 0.10f),
            pose(0f, 0.10f, 0f, -1.45f, 0f, -0.15f, -0.10f, -0.20f, -1.45f, 0f, 0.15f, -0.10f, 0.05f, 1f, 0.10f),
            pose(0f, -0.14f, 0f, -1.95f, 0f, -0.30f, -0.35f, -0.30f, -1.95f, 0f, 0.30f, -0.35f, -0.1f, 0f, 0.10f),
            pose(0f, 0.10f, 0f, -1.45f, 0f, -0.15f, -0.10f, -0.20f, -1.45f, 0f, 0.15f, -0.10f, 0.05f, 1f, 0.10f),
            pose(0f, -0.14f, 0f, -1.95f, 0f, -0.30f, -0.35f, -0.30f, -1.95f, 0f, 0.30f, -0.35f, -0.1f, 0f, 0.10f),
            pose(0f, 0.12f, 0f, -1.45f, 0f, -0.15f, -0.10f, -0.20f, -1.45f, 0f, 0.15f, -0.10f, 0.05f, 1f, 0.10f),
            pose(0f, 0.00f, 0f, -1.60f, 0f, -0.20f, -0.20f, -0.25f, -1.60f, 0f, 0.20f, -0.20f, 0f, 0f, 0.08f),
            pose(0f, 0.00f, 0f, -0.90f, 0f, -0.15f, -0.40f, -0.30f, -0.60f, 0f, 0.15f, -0.30f, 0f, 0f, 0.06f),
            IDLE
    };

    // Ground smash: sword overhead, hold, slam down
    private static final float[] SMASH_T = {0, 16, 30, 34, 38, 52, 66};
    private static final float[][] SMASH_K = {
            IDLE,
            pose(0f, -0.35f, 0f, -3.00f, 0f, -0.05f, -0.30f, -0.10f, -2.70f, 0f, 0.20f, -0.30f, -0.2f, -3f, 0.25f),
            pose(0f, -0.45f, 0f, -3.15f, 0f, -0.05f, -0.25f, -0.05f, -2.85f, 0f, 0.20f, -0.30f, -0.3f, -4f, 0.28f),
            pose(0f, 0.55f, 0f, -0.55f, 0f, -0.05f, -0.10f, 0.20f, -0.45f, 0f, 0.20f, -0.20f, 0.3f, 8f, 0.30f),
            pose(0f, 0.50f, 0f, -0.50f, 0f, -0.05f, -0.10f, 0.20f, -0.45f, 0f, 0.20f, -0.20f, 0.3f, 7f, 0.30f),
            pose(0f, 0.40f, 0f, -0.55f, 0f, -0.05f, -0.15f, 0.10f, -0.40f, 0f, 0.20f, -0.20f, 0.2f, 5f, 0.25f),
            IDLE
    };

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float[] sample(float[] times, float[][] keys, float t) {
        if (t <= times[0]) return keys[0];
        int last = times.length - 1;
        if (t >= times[last]) return keys[last];
        int i = 0;
        while (i < last - 1 && t > times[i + 1]) i++;
        float f = smooth((t - times[i]) / (times[i + 1] - times[i]));
        float[] out = new float[N];
        for (int k = 0; k < N; k++) out[k] = Mth.lerp(f, keys[i][k], keys[i + 1][k]);
        return out;
    }

    @Override
    public void setupAnim(AzureColossus e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float t = e.getStateTime(ageInTicks);
        float[] p;
        switch (e.getState()) {
            case AzureColossus.STATE_SLASH -> p = sample(SLASH_T, SLASH_K, t);
            case AzureColossus.STATE_BARRAGE -> p = sample(BARRAGE_T, BARRAGE_K, t);
            case AzureColossus.STATE_SMASH -> p = sample(SMASH_T, SMASH_K, t);
            default -> p = IDLE;
        }
        float[] q = p.clone();

        // idle breathing and sway on top of the pose
        float breathe = Mth.sin(ageInTicks * 0.08f);
        q[BODY_PITCH] += 0.02f * breathe;
        q[R_SH_X] += 0.03f * Mth.sin(ageInTicks * 0.08f + 0.7f);
        q[L_SH_X] += 0.04f * Mth.sin(ageInTicks * 0.08f + 1.9f);
        q[L_SH_Z] += 0.02f * breathe;

        // walking: long strides, arms counter-swing
        boolean attacking = e.getState() >= AzureColossus.STATE_SLASH;
        float walk = attacking ? 0f : limbSwingAmount;
        float stride = Mth.cos(limbSwing * 0.35f) * 0.9f * Mth.clamp(walk * 1.4f, 0f, 1f);
        float lean = 0.10f * Mth.clamp(walk * 1.4f, 0f, 1f);
        q[BODY_PITCH] += lean;
        q[BODY_YAW] += Mth.cos(limbSwing * 0.35f) * 0.10f * walk;
        q[L_SH_X] += -stride * 0.6f;
        q[R_SH_X] += stride * 0.25f;
        q[CROUCH] += Mth.abs(Mth.sin(limbSwing * 0.35f)) * 2.0f * walk;

        // legs: swing while walking, plant wide while attacking
        legR.xRot = stride;
        legL.xRot = -stride;
        legR.zRot = q[LEG_SPREAD] * 0.5f;
        legL.zRot = -q[LEG_SPREAD] * 0.5f;
        if (attacking) {
            legR.xRot = -0.25f - q[BODY_PITCH] * 0.3f;
            legL.xRot = 0.35f - q[BODY_PITCH] * 0.3f;
        }

        // torso
        body.y = -22f + q[CROUCH];
        hips.y = -22f + q[CROUCH] * 0.5f;
        body.yRot = q[BODY_YAW];
        body.xRot = q[BODY_PITCH];
        body.zRot = q[BODY_ROLL];

        // head looks at the target a little
        head.xRot = q[HEAD_X] + Mth.clamp(headPitch * Mth.DEG_TO_RAD, -0.4f, 0.4f) - q[BODY_PITCH] * 0.6f;
        head.yRot = Mth.clamp(netHeadYaw * Mth.DEG_TO_RAD, -0.6f, 0.6f) - q[BODY_YAW] * 0.5f;

        // arms
        armR.xRot = q[R_SH_X];
        armR.yRot = q[R_SH_Y];
        armR.zRot = q[R_SH_Z];
        forearmR.xRot = q[R_ELBOW];
        sword.xRot = q[SWORD_X];
        armL.xRot = q[L_SH_X];
        armL.yRot = q[L_SH_Y];
        armL.zRot = q[L_SH_Z];
        forearmL.xRot = q[L_ELBOW];

        // wings: slow flap, feathers ripple; hair flames flicker
        float flap = Mth.sin(ageInTicks * 0.12f);
        wingR.zRot = -0.05f - 0.07f * flap - Math.abs(q[BODY_PITCH]) * 0.2f;
        wingL.zRot = 0.05f + 0.07f * flap + Math.abs(q[BODY_PITCH]) * 0.2f;
        wingR.xRot = 0.3f + q[BODY_PITCH] * -0.4f;
        wingL.xRot = wingR.xRot;
        for (int i = 0; i < FEATHERS; i++) {
            float rip = 0.03f * Mth.sin(ageInTicks * 0.2f + i * 0.7f);
            featherR.get(i).xRot = rip;
            featherL.get(i).xRot = -rip;
        }
        for (int i = 0; i < SPIKES; i++) {
            spikes.get(i).xRot += 0.04f * Mth.sin(ageInTicks * 0.3f + i * 1.3f) * 0.0f;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float r, float g, float b, float a) {
        root.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
    }
}
