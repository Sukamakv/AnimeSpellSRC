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
 * DIO's yellow road roller: a twelve-slat steel drum, two big rear wheels, hood with grille and exhaust, an open
 * cab with a roof, seat and steering wheel, and a rear counterweight. Front of the roller is -Z.
 *
 * Texture sheet 512x256: yellow (0,0), grey steel (256,0), dark (0,128), red-orange (256,128); each zone is 256x128.
 */
public class RoadRollerModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(JJKSpells.id("road_roller"), "main");

    private static final int[] YELLOW = {0, 0}, GREY = {256, 0}, DARK = {0, 128}, RED = {256, 128};

    private final ModelPart root, drum, wheelL, wheelR;

    public RoadRollerModel(ModelPart root) {
        this.root = root;
        this.drum = root.getChild("drum");
        this.wheelL = root.getChild("wheel_l");
        this.wheelR = root.getChild("wheel_r");
    }

    private static PartDefinition box(PartDefinition parent, String name, int[] zone, float x, float y, float z,
                                      float w, float h, float d) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox(x, y, z, w, h, d), PartPose.ZERO);
    }

    private static PartDefinition boxRot(PartDefinition parent, String name, int[] zone, float px, float py, float pz,
                                         float x, float y, float z, float w, float h, float d, float rx) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox(x, y, z, w, h, d),
                PartPose.offsetAndRotation(px, py, pz, rx, 0, 0));
    }

    /** A round disc seen side-on: twelve overlapping spokes, thickness {@code t} along X, radius {@code r}. */
    private static void disc(PartDefinition g, String name, int[] zone, float cx, float t, float r) {
        float w = 0.55f * r;
        for (int k = 0; k < 12; k++) {
            float jitter = (k % 3) * 0.02f;   // keeps overlapping faces from fighting over the same plane
            boxRot(g, name + k, zone, 0, 0, 0, cx - t / 2f + jitter, -r, -w / 2f, t - jitter * 2f, r, w, k * Mth.PI / 6f);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition m = mesh.getRoot();

        // ---- front drum: twelve steel slats around an axle ----
        float dr = 20f, slatW = 2f * dr * Mth.sin(Mth.PI / 12f) + 0.6f;
        PartDefinition drum = m.addOrReplaceChild("drum", CubeListBuilder.create(), PartPose.offset(0, 24 - 20, -30));
        for (int k = 0; k < 12; k++) {
            boxRot(drum, "slat" + k, GREY, 0, 0, 0, -22, -dr, -slatW / 2f, 44, 4, slatW, k * Mth.PI / 6f);
        }
        disc(drum, "capl", GREY, -22.5f, 3f, dr - 1f);
        disc(drum, "capr", GREY, 22.5f, 3f, dr - 1f);
        disc(drum, "hubl", YELLOW, -24.5f, 2f, 9f);
        disc(drum, "hubr", YELLOW, 24.5f, 2f, 9f);
        box(drum, "axle", DARK, -28, -2.5f, -2.5f, 56, 5, 5);

        // ---- rear wheels ----
        for (int s = -1; s <= 1; s += 2) {
            PartDefinition w = m.addOrReplaceChild(s < 0 ? "wheel_l" : "wheel_r", CubeListBuilder.create(), PartPose.offset(28f * s, 24 - 26, 22));
            disc(w, "tire", DARK, 0, 10f, 26f);
            disc(w, "rim", YELLOW, 0, 12f, 13f);
            box(w, "nut", GREY, -7, -2, -2, 14, 4, 4);
        }

        // ---- body: everything below is fixed to the root at ground level (y = 24 in model space) ----
        PartDefinition b = m.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        // fork and frame over the drum
        box(b, "fork_l", YELLOW, -28, -38, -37, 4, 24, 14);
        box(b, "fork_r", YELLOW, 24, -38, -37, 4, 24, 14);
        box(b, "beam", YELLOW, -28, -44, -37, 56, 7, 14);
        box(b, "neck", YELLOW, -14, -46, -34, 28, 16, 14);
        // chassis and underside engine
        box(b, "chassis", YELLOW, -20, -46, -22, 40, 16, 52);
        box(b, "belly", DARK, -12, -30, -20, 24, 6, 40);
        box(b, "chassis_stripe", RED, -20.5f, -40, -22, 41, 3, 52);
        // hood, grille, lamps, exhaust
        box(b, "hood", YELLOW, -15, -60, -24, 30, 14, 24);
        box(b, "hood_stripe", RED, -15.5f, -53, -24, 31, 2, 24);
        box(b, "grille", DARK, -11, -58, -25, 22, 8, 1);
        box(b, "lamp_l", RED, -13, -57, -25.6f, 4, 4, 1);
        box(b, "lamp_r", RED, 9, -57, -25.6f, 4, 4, 1);
        box(b, "exhaust", DARK, 9, -86, -10, 4, 26, 4);
        box(b, "exhaust_cap", DARK, 8, -89, -11, 6, 3, 6);
        // cab: posts, roof, seat, steering
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = 0; sz < 2; sz++) {
                box(b, "post" + sx + sz, YELLOW, sx * 19 - 1.5f, -92, sz == 0 ? 6 : 30, 3, 46, 3);
            }
        }
        box(b, "roof", YELLOW, -23, -97, 2, 46, 5, 36);
        box(b, "roof_trim", RED, -23.5f, -98, 1, 47, 2, 2);
        box(b, "seat", DARK, -8, -54, 14, 16, 8, 12);
        box(b, "seat_back", DARK, -8, -72, 24, 16, 18, 3);
        box(b, "column", DARK, -1.5f, -64, 4, 3, 16, 3);
        boxRot(b, "steer", DARK, 0, -66, 6, -7, -1, -7, 14, 2, 14, -0.9f);
        // fenders over the rear wheels and the counterweight
        box(b, "fender_l", YELLOW, -37, -57, 2, 18, 3, 46);
        box(b, "fender_r", YELLOW, 19, -57, 2, 18, 3, 46);
        box(b, "counter", YELLOW, -18, -44, 32, 36, 16, 10);
        box(b, "counter_stripe", RED, -18.5f, -38, 32, 37, 3, 10);
        return LayerDefinition.create(mesh, 512, 256);
    }

    /** Spins the drum and rear wheels. */
    public void animate(float spin) {
        drum.xRot = spin * 1.3f;
        wheelL.xRot = spin;
        wheelR.xRot = spin;
    }

    public void render(PoseStack stack, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
        root.render(stack, buf, light, overlay, r, g, b, a);
    }
}
