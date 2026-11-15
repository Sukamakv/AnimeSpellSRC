package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The World. A heavily built golden Stand (about 2.7 blocks tall) assembled from separate boxes: helmet visor and
 * crest, layered shoulder guards with raised hearts, bracers, fists with gold knuckle plates, thigh and shin armour
 * and heart-shaped knee pads. Animated per pose in {@link #animate}.
 *
 * Texture sheet 256x256: gold (0,0), olive shade (128,0), grey skin (128,64), green (0,128), orange (128,128),
 * white (0,192), dark (128,192).
 */
public class TheWorldModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(JJKSpells.id("the_world"), "main");
    public static final ModelLayerLocation LAYER_STAR = new ModelLayerLocation(JJKSpells.id("star_platinum"), "main");

    private static final int[] GOLD = {0, 0}, OLIVE = {128, 0}, GREY = {128, 64}, GREEN = {0, 128}, ORANGE = {128, 128}, WHITE = {0, 192}, DARK = {128, 192};

    private final ModelPart root, body, head, armR, armL, foreR, foreL, legR, legL, kneeR, kneeL;

    public TheWorldModel(ModelPart bakedRoot) {
        ModelPart root = bakedRoot.getChild("root");
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.armR = body.getChild("arm_r");
        this.armL = body.getChild("arm_l");
        this.foreR = armR.getChild("fore");
        this.foreL = armL.getChild("fore");
        this.legR = root.getChild("leg_r");
        this.legL = root.getChild("leg_l");
        this.kneeR = legR.getChild("knee");
        this.kneeL = legL.getChild("knee");
    }

    private static PartDefinition add(PartDefinition parent, String name, int[] zone, float px, float py, float pz,
                                      float x, float y, float z, float w, float h, float d, float rx, float ry, float rz) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox(x, y, z, w, h, d),
                PartPose.offsetAndRotation(px, py, pz, rx, ry, rz));
    }

    private static PartDefinition add(PartDefinition parent, String name, int[] zone, float px, float py, float pz,
                                      float x, float y, float z, float w, float h, float d) {
        return add(parent, name, zone, px, py, pz, x, y, z, w, h, d, 0, 0, 0);
    }

    /** A raised heart facing -Z: two lobes and a tilted tip. s = size scale. */
    private static void heart(PartDefinition parent, String name, float x, float y, float z, float s) {
        PartDefinition h = parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
        add(h, "tip", GREEN, 0, 0.8f * s, 0, -1.2f * s, -1.2f * s, -0.9f, 2.4f * s, 2.4f * s, 1.8f, 0, 0, Mth.PI / 4f);
        add(h, "lobe_l", GREEN, -1.0f * s, -0.7f * s, 0, -1.0f * s, -1.0f * s, -0.9f, 2.0f * s, 2.0f * s, 1.8f);
        add(h, "lobe_r", GREEN, 1.0f * s, -0.7f * s, 0, -1.0f * s, -1.0f * s, -0.9f, 2.0f * s, 2.0f * s, 1.8f);
    }

    public static LayerDefinition createBodyLayer() {
        return createBodyLayer(false);
    }

    /** Star Platinum: same build, but the abs are muscle shading (not armour) and the crest is the green headband. */
    public static LayerDefinition createStarLayer() {
        return createStarBody();
    }

    // ---- Star Platinum: its own sculpt (flat colour zones, 4x4 grid of 64px squares in star_platinum.png) ----
    private static final int[] S_BLUE = z(0), S_BLUE_D = z(1), S_PURP = z(2), S_PURP_D = z(3), S_GOLD = z(4), S_GOLD_D = z(5),
            S_CREAM = z(6), S_CREAM_D = z(7), S_WHITE = z(8), S_WHITE_D = z(9), S_HAIR = z(10), S_GREEN = z(11),
            S_ORANGE = z(12), S_DARK = z(13), S_SILVER = z(14), S_EYE = z(15);

    private static int[] z(int i) {
        return new int[]{(i % 4) * 64, (i / 4) * 64};
    }

    /** One pointed hair spike: three stacked boxes narrowing to a tip, tilted about its base. */
    private static void spike(PartDefinition head, String name, float x, float y, float zz, float len, float rx, float rz) {
        PartDefinition p = head.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offsetAndRotation(x, y, zz, rx, 0, rz));
        add(p, "a", S_HAIR, 0, 0, 0, -1.2f, -len * 0.45f, -1.2f, 2.4f, len * 0.45f + 0.4f, 2.4f);
        add(p, "b", S_HAIR, 0, -len * 0.45f, 0, -0.85f, -len * 0.3f, -0.85f, 1.7f, len * 0.3f + 0.3f, 1.7f);
        add(p, "c", S_HAIR, 0, -len * 0.75f, 0, -0.45f, -len * 0.25f, -0.45f, 0.9f, len * 0.25f + 0.2f, 0.9f);
    }

    private static LayerDefinition createStarBody() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));

        // ---- legs: purple thighs with a gold wave, cream knee guards, tall cream boots with an orange stripe ----
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1 : 1;
            String sfx = side == 0 ? "r" : "l";
            PartDefinition leg = add(root, "leg_" + sfx, S_PURP, sx * 4.0f, -18, 0, -3.9f, 0, -3.9f, 7.8f, 9.0f, 7.8f);
            add(leg, "thigh_wave1", S_GOLD, sx * 2.4f, 1.2f, -4.1f, -0.35f, 0, 0, 0.7f, 3.4f, 0.5f, 0, 0, sx * 0.3f);
            add(leg, "thigh_wave2", S_GOLD, sx * 2.9f, 4.3f, -4.1f, -0.35f, 0, 0, 0.7f, 3.4f, 0.5f, 0, 0, -sx * 0.3f);
            add(leg, "thigh_muscle", S_PURP_D, 0, 0, 0, -2.4f, 5.2f, -4.4f, 4.8f, 2.8f, 0.7f);
            PartDefinition knee = leg.addOrReplaceChild("knee", CubeListBuilder.create(), PartPose.offset(0, 9.0f, 0));
            add(knee, "shin", S_BLUE, 0, 0, 0, -3.1f, -0.4f, -3.1f, 6.2f, 5.0f, 6.2f);
            add(knee, "knee_plate", S_CREAM, 0, 0, 0, -3.9f, -2.8f, -4.4f, 7.8f, 4.6f, 1.4f);
            add(knee, "knee_trim", S_SILVER, 0, 0, 0, -3.9f, -3.1f, -4.6f, 7.8f, 0.7f, 1.6f);
            add(knee, "knee_gold", S_GOLD, 0, 0, 0, -1.7f, -2.1f, -5.2f, 3.4f, 2.8f, 0.9f);
            add(knee, "boot_cuff", S_CREAM_D, 0, 0, 0, -4.3f, 2.6f, -4.3f, 8.6f, 1.3f, 8.6f);
            add(knee, "boot", S_CREAM, 0, 0, 0, -3.8f, 3.2f, -3.8f, 7.6f, 5.2f, 7.6f);
            add(knee, "boot_stripe", S_ORANGE, 0, 0, 0, -1.0f, 3.6f, -4.4f, 2.0f, 4.6f, 0.7f);
            add(knee, "foot", S_CREAM, 0, 0, 0, -3.8f, 7.4f, -7.2f, 7.6f, 1.8f, 11.0f);
            add(knee, "foot_stripe", S_ORANGE, 0, 0, 0, -1.0f, 7.0f, -7.3f, 2.0f, 0.6f, 4.0f);
        }

        // ---- hips, silver rope belt with gold studs, white apron ----
        add(root, "hips", S_PURP, 0, -18, 0, -6.8f, -3.0f, -4.0f, 13.6f, 6.4f, 8.0f);
        add(root, "belt", S_SILVER, 0, -18, 0, -7.3f, -4.9f, -4.7f, 14.6f, 2.0f, 9.4f);
        for (int i = 0; i < 4; i++) add(root, "belt_rib" + i, S_CREAM_D, 0, -18, 0, -7.5f, -4.6f + i * 0.5f, -4.9f, 15.0f, 0.2f, 9.8f);
        add(root, "stud_l", S_GOLD, 0, -18, 0, 3.0f, -5.2f, -5.8f, 2.2f, 2.4f, 1.6f);
        add(root, "stud_r", S_GOLD, 0, -18, 0, -5.2f, -5.2f, -5.8f, 2.2f, 2.4f, 1.6f);
        add(root, "apron", S_WHITE, 0, -18, 0, -4.7f, -3.0f, -5.4f, 9.4f, 12.5f, 0.9f);
        add(root, "apron_fold_l", S_WHITE_D, 0, -18, 0, 1.6f, -2.0f, -5.7f, 0.6f, 10.5f, 0.5f);
        add(root, "apron_fold_r", S_WHITE_D, 0, -18, 0, -2.2f, -2.0f, -5.7f, 0.6f, 9.5f, 0.5f);
        add(root, "apron_hem", S_WHITE_D, 0, -18, 0, -4.7f, 8.8f, -5.5f, 9.4f, 0.7f, 0.9f);

        // ---- torso: V-shaped, blue flanks, lilac pecs and abs, gold wave lines, white scarf ----
        PartDefinition body = add(root, "body", S_BLUE, 0, -18, 0, -5.7f, -7.6f, -3.7f, 11.4f, 7.8f, 7.4f);
        add(body, "chest", S_BLUE, 0, 0, 0, -8.9f, -15.0f, -4.5f, 17.8f, 8.0f, 9.0f);
        add(body, "lat_l", S_BLUE_D, 0, 0, 0, 5.4f, -12.0f, -4.2f, 3.8f, 6.0f, 8.4f);
        add(body, "lat_r", S_BLUE_D, 0, 0, 0, -9.2f, -12.0f, -4.2f, 3.8f, 6.0f, 8.4f);
        add(body, "pec_l", S_PURP, 0, 0, 0, 0.3f, -14.4f, -6.3f, 8.2f, 5.8f, 2.0f);
        add(body, "pec_r", S_PURP, 0, 0, 0, -8.5f, -14.4f, -6.3f, 8.2f, 5.8f, 2.0f);
        add(body, "pec_under_l", S_PURP_D, 0, 0, 0, 0.8f, -8.9f, -6.1f, 7.4f, 0.9f, 1.8f);
        add(body, "pec_under_r", S_PURP_D, 0, 0, 0, -8.2f, -8.9f, -6.1f, 7.4f, 0.9f, 1.8f);
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1 : 1;
            String sf = side == 0 ? "r" : "l";
            add(body, "wave_a" + sf, S_GOLD, sx * 1.1f, -13.8f, -6.6f, -0.35f, 0, 0, 0.7f, 3.2f, 0.5f, 0, 0, sx * 0.28f);
            add(body, "wave_b" + sf, S_GOLD, sx * 2.0f, -10.6f, -6.6f, -0.35f, 0, 0, 0.7f, 2.6f, 0.5f, 0, 0, -sx * 0.28f);
            add(body, "strap" + sf, S_GOLD, sx * 6.0f, -14.6f, -6.4f, -2.2f, 0, 0, 4.4f, 0.7f, 0.5f, 0, 0, sx * 0.3f);
            for (int i = 0; i < 3; i++) {
                float ax = sx > 0 ? 0.7f : -4.9f;
                add(body, "abs_" + sf + i, S_PURP, 0, 0, 0, ax, -7.3f + i * 2.5f, -5.0f, 4.2f, 2.2f, 1.5f);
            }
            add(body, "abs_line" + sf, S_GOLD, 0, 0, 0, sx * 5.4f - 0.3f, -7.4f, -4.4f, 0.6f, 7.0f, 0.5f);
        }
        add(body, "abs_mid", S_GOLD, 0, 0, 0, -0.35f, -8.3f, -5.4f, 0.7f, 8.4f, 0.6f);
        add(body, "back", S_BLUE_D, 0, 0, 0, -6.6f, -13.5f, 4.4f, 13.2f, 11f, 1.4f);
        add(body, "spine", S_PURP_D, 0, 0, 0, -0.9f, -13.0f, 5.6f, 1.8f, 10f, 0.8f);
        add(body, "scarf", S_WHITE, 0, 0, 0, -5.9f, -17.2f, -4.8f, 11.8f, 3.4f, 9.6f);
        add(body, "scarf_top", S_WHITE_D, 0, 0, 0, -5.4f, -17.7f, -4.3f, 10.8f, 0.8f, 8.6f);
        add(body, "drape_l", S_WHITE, 1.4f, -14.8f, -5.6f, -1.4f, 0, -0.6f, 2.8f, 5.0f, 1.2f, -0.12f, 0, 0.35f);
        add(body, "drape_r", S_WHITE, -1.4f, -14.8f, -5.6f, -1.4f, 0, -0.6f, 2.8f, 5.0f, 1.2f, -0.12f, 0, -0.35f);
        add(body, "drape_shade", S_WHITE_D, 0, -14.4f, -6.0f, -0.3f, 0, 0, 0.6f, 4.2f, 0.5f);

        // ---- head: angular blue face with a lilac centre, brow ridge, gold diadem, tall black spikes ----
        PartDefinition head = add(body, "head", S_BLUE, 0, -16.8f, 0, -3.0f, -6.4f, -3.0f, 6.0f, 6.4f, 6.0f);
        add(head, "face_mid", S_PURP, 0, 0, 0, -1.5f, -4.4f, -3.6f, 3.0f, 4.1f, 0.7f);
        add(head, "nose", S_PURP_D, 0, 0, 0, -0.55f, -3.3f, -4.3f, 1.1f, 1.8f, 0.9f);
        add(head, "lip", S_PURP_D, 0, 0, 0, -1.1f, -1.3f, -3.9f, 2.2f, 0.6f, 0.5f);
        add(head, "chin_stud", S_GOLD, 0, 0, 0, -0.5f, -0.5f, -4.0f, 1.0f, 0.9f, 0.7f);
        add(head, "jaw", S_BLUE_D, 0, 0, 0, -2.6f, -1.2f, -3.3f, 5.2f, 1.3f, 0.5f);
        add(head, "cheek_l", S_BLUE_D, 0, 0, 0, 1.9f, -3.8f, -3.5f, 1.3f, 1.8f, 0.6f);
        add(head, "cheek_r", S_BLUE_D, 0, 0, 0, -3.2f, -3.8f, -3.5f, 1.3f, 1.8f, 0.6f);
        add(head, "brow_l", S_BLUE_D, 0.2f, -5.1f, -3.2f, 0f, 0f, -0.9f, 3.0f, 0.9f, 1.0f, 0, 0, 0.16f);
        add(head, "brow_r", S_BLUE_D, -0.2f, -5.1f, -3.2f, -3.0f, 0f, -0.9f, 3.0f, 0.9f, 1.0f, 0, 0, -0.16f);
        add(head, "eye_l", S_EYE, 0, 0, 0, 0.5f, -4.4f, -3.5f, 1.9f, 0.9f, 0.5f);
        add(head, "eye_r", S_EYE, 0, 0, 0, -2.4f, -4.4f, -3.5f, 1.9f, 0.9f, 0.5f);
        add(head, "pupil_l", S_DARK, 0, 0, 0, 1.1f, -4.3f, -3.8f, 0.8f, 0.8f, 0.4f);
        add(head, "pupil_r", S_DARK, 0, 0, 0, -1.9f, -4.3f, -3.8f, 0.8f, 0.8f, 0.4f);
        add(head, "diadem", S_GOLD, 0, 0, 0, -2.7f, -6.9f, -3.5f, 5.4f, 1.1f, 0.7f);
        add(head, "diadem_green", S_GREEN, 0, 0, 0, -2.2f, -6.4f, -3.6f, 4.4f, 0.4f, 0.4f);
        add(head, "wing_l", S_GOLD, 2.6f, -6.5f, -3.4f, 0f, -0.5f, 0f, 2.6f, 0.9f, 0.7f, 0, 0, -0.4f);
        add(head, "wing_r", S_GOLD, -2.6f, -6.5f, -3.4f, -2.6f, -0.5f, 0f, 2.6f, 0.9f, 0.7f, 0, 0, 0.4f);
        add(head, "gem", S_WHITE, 0, 0, 0, -0.55f, -7.6f, -3.8f, 1.1f, 1.5f, 0.6f);
        add(head, "ear_l", S_BLUE_D, 0, 0, 0, 3.0f, -4.6f, -1.6f, 1.0f, 2.8f, 2.4f);
        add(head, "ear_r", S_BLUE_D, 0, 0, 0, -4.0f, -4.6f, -1.6f, 1.0f, 2.8f, 2.4f);
        add(head, "earcap_l", S_SILVER, 0, 0, 0, 3.9f, -4.0f, -0.9f, 0.6f, 1.5f, 1.2f);
        add(head, "earcap_r", S_SILVER, 0, 0, 0, -4.5f, -4.0f, -0.9f, 0.6f, 1.5f, 1.2f);
        add(head, "hair_top", S_HAIR, 0, 0, 0, -3.5f, -8.6f, -3.2f, 7.0f, 2.8f, 7.4f);
        add(head, "hair_back", S_HAIR, 0, 0, 0, -3.3f, -7.8f, 2.6f, 6.6f, 7.2f, 1.6f);
        add(head, "burn_l", S_HAIR, 0, 0, 0, 3.0f, -6.6f, -2.0f, 0.9f, 2.4f, 4.2f);
        add(head, "burn_r", S_HAIR, 0, 0, 0, -3.9f, -6.6f, -2.0f, 0.9f, 2.4f, 4.2f);
        spike(head, "sp0", 0.0f, -8.4f, -2.2f, 7.0f, -0.35f, 0.0f);
        spike(head, "sp1", 1.9f, -8.4f, -1.0f, 8.2f, -0.1f, 0.22f);
        spike(head, "sp2", -1.9f, -8.4f, -1.0f, 8.0f, -0.1f, -0.22f);
        spike(head, "sp3", 3.0f, -8.0f, 0.4f, 7.0f, 0.05f, 0.62f);
        spike(head, "sp4", -3.0f, -8.0f, 0.4f, 7.2f, 0.05f, -0.62f);
        spike(head, "sp5", 0.0f, -8.4f, 0.8f, 9.0f, 0.2f, 0.0f);
        spike(head, "sp6", 1.5f, -8.2f, 2.4f, 7.5f, 0.55f, 0.2f);
        spike(head, "sp7", -1.5f, -8.2f, 2.4f, 7.5f, 0.55f, -0.2f);
        spike(head, "sp8", 0.0f, -6.8f, 3.6f, 8.0f, 1.0f, 0.0f);
        spike(head, "sp9", 2.6f, -7.0f, 3.0f, 6.0f, 0.7f, 0.5f);
        spike(head, "sp10", -2.6f, -7.0f, 3.0f, 6.0f, 0.7f, -0.5f);

        // ---- arms: massive; gold pauldrons, blue/lilac biceps, cream wraps, big fists with a gold stud cluster ----
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1 : 1;
            String sfx = side == 0 ? "r" : "l";
            PartDefinition arm = add(body, "arm_" + sfx, S_BLUE, sx * 12.2f, -12.8f, 0, -3.7f, 0.2f, -3.7f, 7.4f, 9.4f, 7.4f);
            add(arm, "delt", S_BLUE, 0, 0, 0, -4.4f, -2.2f, -4.4f, 8.8f, 5.6f, 8.8f);
            add(arm, "bicep", S_PURP, 0, 0, 0, -4.0f, 4.2f, -4.0f, 8.0f, 4.8f, 8.0f);
            add(arm, "bicep_ring", S_GOLD, 0, 0, 0, -4.3f, 3.9f, -4.3f, 8.6f, 0.7f, 8.6f);
            add(arm, "arm_line", S_GOLD, 0, 0, 0, sx * 2.4f - 0.3f, 0.4f, -4.1f, 0.6f, 3.4f, 0.5f);
            // gold pauldron: layered curved plates drooping outward
            PartDefinition pad = arm.addOrReplaceChild("pad", CubeListBuilder.create(), PartPose.offsetAndRotation(sx * 1.2f, -3.4f, 0, 0, 0, sx * 0.27f));
            add(pad, "plate", S_GOLD, 0, 0, 0, -5.6f, -1.3f, -5.2f, 11.2f, 2.6f, 10.4f);
            add(pad, "ridge", S_GOLD_D, 0, 0, 0, -4.6f, -2.3f, -3.4f, 9.2f, 1.2f, 6.8f);
            add(pad, "rim", S_GOLD_D, 0, 0, 0, -5.9f, 1.1f, -5.5f, 11.8f, 1.0f, 11.0f);
            add(pad, "groove_a", S_SILVER, 0, 0, 0, -4.4f, -2.5f, -1.8f, 8.8f, 0.3f, 0.5f);
            add(pad, "groove_b", S_SILVER, 0, 0, 0, -4.4f, -2.5f, 1.4f, 8.8f, 0.3f, 0.5f);
            add(pad, "rivet", S_SILVER, 0, 0, 0, sx * 2.6f - 0.9f, -2.0f, -5.6f, 1.8f, 1.8f, 1.2f);
            PartDefinition fore = arm.addOrReplaceChild("fore", CubeListBuilder.create(), PartPose.offset(0, 9.2f, 0));
            add(fore, "elbow_wrap", S_CREAM, 0, 0, 0, -4.4f, -1.4f, -4.4f, 8.8f, 3.2f, 8.8f);
            add(fore, "forearm", S_PURP, 0, 0, 0, -3.5f, 1.6f, -3.5f, 7.0f, 4.6f, 7.0f);
            add(fore, "cuff", S_CREAM, 0, 0, 0, -4.7f, 5.8f, -4.7f, 9.4f, 5.8f, 9.4f);
            add(fore, "cuff_ridge", S_CREAM_D, 0, 0, 0, -5.0f, 8.0f, -5.0f, 10.0f, 0.9f, 10.0f);
            add(fore, "cuff_ridge2", S_CREAM_D, 0, 0, 0, -5.0f, 10.0f, -5.0f, 10.0f, 0.7f, 10.0f);
            add(fore, "cuff_gold", S_GOLD, 0, 0, 0, -4.9f, 11.4f, -4.9f, 9.8f, 1.0f, 9.8f);
            PartDefinition fist = fore.addOrReplaceChild("fist", CubeListBuilder.create(), PartPose.offset(0, 12.2f, 0));
            add(fist, "palm", S_PURP, 0, 0, 0, -3.8f, 0, -3.8f, 7.6f, 4.8f, 7.6f);
            for (int k = 0; k < 4; k++) add(fist, "finger" + k, S_PURP_D, 0, 0, 0, -3.6f + k * 1.85f, 0.4f, -4.5f, 1.65f, 4.2f, 0.9f);
            add(fist, "thumb", S_PURP, 0, 0, 0, sx * -3.6f - 0.6f, 0.4f, -2.4f, 1.2f, 3.2f, 3.4f);
            // gold stud cluster on the finger ends: rows of 4, 3, 2, 1
            int[] counts = {4, 3, 2, 1};
            for (int row = 0; row < 4; row++) {
                int n = counts[row];
                for (int c = 0; c < n; c++) {
                    float x = (c - (n - 1) / 2f) * 1.8f - 0.7f;
                    add(fist, "stud" + row + "_" + c, S_GOLD, 0, 0, 0, x, 4.7f, -3.4f + row * 1.7f, 1.4f, 1.3f, 1.4f);
                }
            }
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    private static LayerDefinition createBodyLayer(boolean star) {
        final int[] ABS = star ? OLIVE : ORANGE, CREST = GOLD, CREST_EDGE = star ? GREEN : ORANGE, BROW = star ? GREEN : ORANGE;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));

        // ---- legs (pivot at the hip) ----
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1 : 1;
            String sfx = side == 0 ? "r" : "l";
            PartDefinition leg = add(root, "leg_" + sfx, GOLD, sx * 3.8f, -18, 0, -3.5f, 0, -3.6f, 7f, 9.5f, 7.2f);
            add(leg, "thigh_band", ORANGE, 0, 0, 0, -3.8f, 6.2f, -3.8f, 7.6f, 1.6f, 7.6f);
            PartDefinition knee = leg.addOrReplaceChild("knee", CubeListBuilder.create(), PartPose.offset(0, 9.5f, 0));
            add(knee, "shin", GOLD, 0, 0, 0, -3.0f, 0, -3.0f, 6f, 6.5f, 6f);
            add(knee, "knee_pad", ORANGE, 0, 0, 0, -3.3f, -1.8f, -3.6f, 6.6f, 3.4f, 1.4f);
            heart(knee, "knee_heart", 0, 0.1f, -4.2f, 0.95f);
            add(knee, "greave", OLIVE, 0, 0, 0, -3.2f, 3.2f, -3.2f, 6.4f, 1.4f, 6.4f);
            add(knee, "boot", GOLD, 0, 6.5f, 0, -3.4f, 0, -4.2f, 6.8f, 3.2f, 8.2f);
            add(knee, "toe", ORANGE, 0, 6.5f, 0, -3.0f, 1.2f, -5.0f, 6.0f, 2.0f, 1.0f);
        }
        add(root, "hips", GOLD, 0, -18, 0, -7.2f, -2.6f, -3.9f, 14.4f, 5.2f, 7.8f);
        heart(root, "belt_heart", 0, -17.2f, -4.4f, 1.15f);
        add(root, "belt", ORANGE, 0, -18, 0, -7.4f, -3.3f, -4.1f, 14.8f, 1.5f, 8.2f);

        // ---- torso ----
        PartDefinition body = add(root, "body", GOLD, 0, -18, 0, -7.8f, -14.5f, -4.3f, 15.6f, 14.5f, 8.6f);
        add(body, "pec_l", GOLD, 0, 0, 0, 0.1f, -13.6f, -5.6f, 7.4f, 5.4f, 1.7f);
        add(body, "pec_r", GOLD, 0, 0, 0, -7.5f, -13.6f, -5.6f, 7.4f, 5.4f, 1.7f);
        add(body, "sternum", OLIVE, 0, 0, 0, -0.5f, -13f, -5.5f, 1f, 11f, 1f);
        for (int i = 0; i < 3; i++) {
            add(body, "abs_l" + i, ABS, 0, 0, 0, 0.5f, -7.3f + i * 2.5f, -4.9f, 4.6f, 2.0f, 1.0f);
            add(body, "abs_r" + i, ABS, 0, 0, 0, -5.1f, -7.3f + i * 2.5f, -4.9f, 4.6f, 2.0f, 1.0f);
        }
        add(body, "back_plate", OLIVE, 0, 0, 0, -6.5f, -13f, 4.2f, 13f, 11f, 1.4f);
        add(body, "collar", OLIVE, 0, 0, 0, -5.4f, -16.0f, -3.6f, 10.8f, 2.4f, 7.2f);
        heart(body, "chest_heart", 0, -11.2f, -6.0f, 0.9f);

        // ---- head ----
        PartDefinition head = add(body, "head", GREY, 0, -15.5f, 0, -2.7f, -6.0f, -2.7f, 5.4f, 6.2f, 5.4f);
        add(head, "visor", GOLD, 0, 0, 0, -3.3f, -7.4f, -3.9f, 6.6f, 4.2f, 7.0f);
        add(head, "brow", BROW, 0, 0, 0, -3.5f, -3.4f, -4.2f, 7.0f, 1.0f, 1.0f);
        add(head, "mask", GOLD, 0, -2.0f, -3.5f, -2.3f, 0f, -0.9f, 4.6f, 3.0f, 1.3f, 0.35f, 0, 0);
        add(head, "eye_l", WHITE, 0, 0, 0, 0.4f, -4.9f, -4.3f, 2.0f, 0.9f, 0.5f);
        add(head, "eye_r", WHITE, 0, 0, 0, -2.4f, -4.9f, -4.3f, 2.0f, 0.9f, 0.5f);
        add(head, "crest", CREST, 0, -6.0f, -0.5f, -2.5f, -9.0f, -0.5f, 5.0f, 9.0f, 3.2f, 0.55f, 0, 0);
        add(head, "crest_edge", CREST_EDGE, 0, -6.0f, -0.5f, -2.7f, -9.2f, -0.7f, 5.4f, 1.2f, 3.6f, 0.55f, 0, 0);
        add(head, "ear_l", OLIVE, 0, 0, 0, 3.2f, -5.6f, -1.2f, 1.6f, 3.0f, 2.4f);
        add(head, "ear_r", OLIVE, 0, 0, 0, -4.8f, -5.6f, -1.2f, 1.6f, 3.0f, 2.4f);
        heart(head, "chin_heart", 0, -0.6f, -3.3f, 0.55f);

        // ---- arms ----
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1 : 1;
            String sfx = side == 0 ? "r" : "l";
            PartDefinition arm = add(body, "arm_" + sfx, GOLD, sx * 10.6f, -12.6f, 0, -3.3f, 0.5f, -3.3f, 6.6f, 8.5f, 6.6f);
            // layered shoulder guard with a raised heart
            add(arm, "guard", GOLD, 0, 0, 0, -4.8f, -4.2f, -4.8f, 9.6f, 6.0f, 9.6f);
            add(arm, "guard_cap", OLIVE, 0, 0, 0, -3.6f, -5.6f, -3.6f, 7.2f, 1.8f, 7.2f);
            add(arm, "guard_rim", ORANGE, 0, 0, 0, -5.0f, 1.4f, -5.0f, 10.0f, 1.0f, 10.0f);
            heart(arm, "guard_heart", sx * 4.9f, -1.2f, 0, 1.45f);
            add(arm, "guard_heart_base", OLIVE, 0, 0, 0, sx * 4.2f - 0.7f, -3.0f, -1.9f, 1.4f, 3.6f, 3.8f);
            PartDefinition fore = arm.addOrReplaceChild("fore", CubeListBuilder.create(), PartPose.offset(0, 9.0f, 0));
            add(fore, "bracer", ORANGE, 0, 0, 0, -3.8f, -1.0f, -3.8f, 7.6f, 5.0f, 7.6f);
            add(fore, "bracer_ring", OLIVE, 0, 0, 0, -4.0f, 3.4f, -4.0f, 8.0f, 1.0f, 8.0f);
            add(fore, "forearm", GREY, 0, 0, 0, -3.1f, 4.0f, -3.1f, 6.2f, 4.2f, 6.2f);
            PartDefinition fist = fore.addOrReplaceChild("fist", CubeListBuilder.create(), PartPose.offset(0, 8.2f, 0));
            add(fist, "palm", GREY, 0, 0, 0, -3.4f, 0, -3.4f, 6.8f, 5.0f, 6.8f);
            for (int k = 0; k < 4; k++) add(fist, "knuckle" + k, GOLD, 0, 0, 0, -3.2f + k * 1.65f, 2.6f, -4.1f, 1.5f, 2.6f, 0.9f);
            add(fist, "thumb", GREY, 0, 0, 0, sx * -3.6f - 0.6f, 0.4f, -2.4f, 1.2f, 2.4f, 3.0f);
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    // ---- animation ---------------------------------------------------------------------------------

    private static float ease(float x) { x = Mth.clamp(x, 0f, 1f); return x * x * (3f - 2f * x); }

    /** Sets every joint for {@code pose} at {@code pt} ticks into it (fractional) and {@code age} ticks of life. */
    public void animate(int pose, float pt, float age) {
        body.xRot = body.yRot = body.zRot = 0;
        head.xRot = head.yRot = 0;
        float breathe = Mth.sin(age * 0.18f) * 0.03f;
        float rArm = -0.55f, rFore = -1.55f, lArm = -0.35f, lFore = -1.50f, rSplay = 0.12f, lSplay = -0.12f;
        float lean = 0f, twist = 0f, legSpread = 0.10f, bend = 0.08f;

        switch (pose) {
            case 1 -> {   // barrage: alternating straight punches, ~17 a second
                float s = Mth.sin(pt * (float) Math.PI / 1.7f);
                float er = Math.max(0f, s), el = Math.max(0f, -s);
                rArm = -0.35f - 1.15f * er; rFore = -1.45f + 1.35f * er;
                lArm = -0.35f - 1.15f * el; lFore = -1.45f + 1.35f * el;
                rSplay = 0.05f; lSplay = -0.05f;
                lean = 0.22f; twist = -0.28f * s; legSpread = 0.38f; bend = 0.38f;
            }
            case 2 -> {   // heavy punch: wind up the right fist, then a lunging straight
                float wind = ease(pt / 9f), hit = ease((pt - 10f) / 2.5f);
                rArm = (0.7f * wind - 0.35f) - 1.55f * hit - 0.4f * hit; rFore = -1.2f * wind - 0.2f + (-1.45f + 1.45f) * hit;
                rFore = -1.5f + 1.4f * hit - 0.6f * wind * (1f - hit);
                lArm = -0.9f; lFore = -1.4f;
                twist = (0.65f * wind) - 1.15f * hit;
                lean = 0.1f + 0.3f * hit; legSpread = 0.3f + 0.2f * hit; bend = 0.3f;
            }
            case 3 -> {   // lifting the roller overhead
                float up = ease(pt / 10f);
                rArm = -0.6f - 2.45f * up; lArm = -0.6f - 2.45f * up; rFore = -1.5f + 1.45f * up; lFore = rFore;
                rSplay = 0.12f + 0.12f * up; lSplay = -rSplay; lean = -0.12f * up; legSpread = 0.3f; bend = 0.2f;
            }
            case 4 -> {   // crushing: both fists hammer down in turn
                float s = Mth.sin(pt * (float) Math.PI / 1.5f);
                float er = Math.max(0f, s), el = Math.max(0f, -s);
                rArm = -2.7f + 1.9f * er; lArm = -2.7f + 1.9f * el; rFore = -0.2f; lFore = -0.2f;
                rSplay = 0.2f; lSplay = -0.2f; lean = 0.3f + 0.1f * Math.abs(s); legSpread = 0.4f; bend = 0.45f;
            }
            case 5 -> {   // winding up for the last blow: arms high and trembling
                float shake = Mth.sin(pt * 2.4f) * 0.05f;
                rArm = -3.05f + shake; lArm = -3.05f - shake; rFore = -0.1f; lFore = -0.1f;
                rSplay = 0.28f; lSplay = -0.28f; lean = -0.2f; legSpread = 0.35f; bend = 0.3f;
            }
            case 6 -> {   // the final slam
                float hit = ease(pt / 3f);
                rArm = -3.05f + 2.2f * hit; lArm = -3.05f + 2.2f * hit; rFore = -0.1f; lFore = -0.1f;
                rSplay = 0.15f; lSplay = -0.15f; lean = -0.2f + 0.65f * hit; legSpread = 0.45f; bend = 0.55f;
            }
            case 7 -> {   // guard: both forearms raised and crossed in front, planted wide
                float brace = ease(pt / 3f);
                rArm = -0.35f - 0.75f * brace; lArm = -0.35f - 0.75f * brace;
                rFore = -1.55f - 0.15f * brace; lFore = rFore;
                rSplay = 0.12f - 0.42f * brace; lSplay = -rSplay;   // forearms cross over the chest
                lean = 0.12f; legSpread = 0.45f; bend = 0.5f;
            }
            case 8 -> {   // star finger: right arm shoots straight out, fingers pointing
                float out = ease(pt / 2.5f);
                rArm = -0.4f - 1.17f * out; rFore = -1.5f + 1.5f * out;
                lArm = -0.5f; lFore = -1.5f; rSplay = 0.05f; lSplay = -0.2f;
                twist = -0.5f * out; lean = 0.08f; legSpread = 0.38f; bend = 0.3f;
            }
            case 9 -> {   // onslaught: sprinting lean, one fist thrust forward, the other drawn back
                float s = Mth.sin(pt * 1.7f);
                rArm = -1.45f; rFore = -0.05f; lArm = 0.5f + 0.15f * s; lFore = -1.2f;
                rSplay = 0.04f; lSplay = -0.1f; twist = -0.25f; lean = 0.55f; legSpread = 0.9f; bend = 0.6f;
            }
            case 10 -> {  // recovering after the counter: standing tall, fist still out
                rArm = -1.45f; rFore = -0.05f; lArm = -0.2f; lFore = -0.9f; rSplay = 0.05f; lSplay = -0.1f;
                twist = -0.2f; lean = 0.05f; legSpread = 0.25f; bend = 0.15f;
            }
            default -> {  // idle / declaring: guard up, chest out
                rArm = -0.55f + breathe; lArm = -0.35f - breathe;
            }
        }
        body.xRot = lean;
        body.yRot = twist;
        head.xRot = -lean * 0.8f;
        head.yRot = -twist * 0.5f;
        armR.xRot = rArm; armR.zRot = rSplay; foreR.xRot = rFore;
        armL.xRot = lArm; armL.zRot = lSplay; foreL.xRot = lFore;
        legR.xRot = -legSpread * 0.5f - 0.1f; legL.xRot = legSpread * 0.6f + 0.1f;
        legR.zRot = 0.05f; legL.zRot = -0.05f;
        kneeR.xRot = bend; kneeL.xRot = bend * 0.7f;
        root.y = 24 + (bend * 4.5f);  // sink into the stance
    }

    public void render(PoseStack stack, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
        root.render(stack, buf, light, overlay, r, g, b, a);
    }
}
