package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.PhainonAttack;
import com.example.jjkspells.irontomb.entity.PhainonEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The normal (wide-arm) player model, posed by Phainon's state: hovering, casting, slashing, staggered. */
public class PhainonModel extends PlayerModel<PhainonEntity> {
    public PhainonModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(PhainonEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(e, 0f, 0f, age, headYaw, headPitch);
        float[] p = pose(e, age - (int) age, age);
        rightArm.xRot = p[0];
        rightArm.zRot = p[1];
        leftArm.xRot = p[2];
        leftArm.zRot = p[3];
        rightArm.yRot = 0f;
        leftArm.yRot = 0f;
        rightLeg.xRot = p[4];
        leftLeg.xRot = p[4] * 0.8f;
        rightLeg.zRot = p[5];
        leftLeg.zRot = -p[5];
        rightLeg.yRot = 0f;
        leftLeg.yRot = 0f;
        head.xRot += p[6];
        hat.copyFrom(head);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
    }

    private static float L(float a, float b, float k) {
        return Mth.lerp(Mth.clamp(k, 0f, 1f), a, b);
    }

    /** {rightArmX, rightArmZ, leftArmX, leftArmZ, legX, legSpread, headPitchAdd} */
    static float[] pose(PhainonEntity e, float pt, float age) {
        int atk = e.getAttack(), ev = e.getEvent(), ph = e.getPhase();
        float t = atk == 0 ? 0f : e.getAtkT() + pt;
        float et = ev == 0 ? 0f : e.getEvT() + pt;
        float sw = Mth.sin(age * 0.09f), cw = Mth.cos(age * 0.07f);
        float rX = 0.12f + 0.05f * sw, rZ = 0.32f + 0.07f * cw, lX = 0.12f - 0.05f * sw, lZ = -(0.32f + 0.07f * cw);
        float legX = 0.16f + 0.04f * sw, legS = 0.05f, head = 0f;
        if (!e.isAwake()) {
            rX = lX = 0.03f;
            rZ = 0.07f;
            lZ = -0.07f;
            legX = 0f;
            legS = 0.02f;
        }
        switch (atk) {
            case PhainonAttack.LANCE -> {
                float k = Fx.smooth(Fx.ramp(t, 0, 20)) * (1f - Fx.smooth(Fx.ramp(t, 46, 64)));
                rX = L(rX, -1.5f, k);
                rZ = L(rZ, 0.05f, k);
                lX = L(lX, -0.45f, k);
                lZ = L(lZ, -0.95f, k);
                legX = L(legX, 0.5f, k);
            }
            case PhainonAttack.DASH -> {
                if (t < PhainonAttack.DASH_FIRST) {
                    float k = Fx.smooth(Fx.ramp(t, 0, 12));
                    rX = L(rX, 0.9f, k);
                    lX = L(lX, 0.9f, k);
                    legX = L(legX, 0.6f, k);
                } else {
                    float local = (t - PhainonAttack.DASH_FIRST) % PhainonAttack.DASH_STEP;
                    float s = Fx.smooth(Fx.ramp(local, 0, 5));
                    rX = L(-2.5f, 0.5f, s);
                    lX = L(0.8f, 0.4f, s);
                    legX = 0.6f;
                }
            }
            case PhainonAttack.FEATHER -> {
                float k = Fx.smooth(Fx.ramp(t, 0, 14)) * (1f - Fx.smooth(Fx.ramp(t, 86, 102)));
                rX = L(rX, -0.2f, k);
                lX = L(lX, -0.2f, k);
                rZ = L(rZ, 1.3f, k);
                lZ = L(lZ, -1.3f, k);
                head = -0.25f * k;
            }
            case PhainonAttack.SUNFALL -> {
                float up = Fx.smooth(Fx.ramp(t, 4, 40)) * (1f - Fx.smooth(Fx.ramp(t, 64, 70)));
                float thrown = Fx.smooth(Fx.ramp(t, 64, 70)) * (1f - Fx.smooth(Fx.ramp(t, 86, 104)));
                rX = L(rX, -3.0f, up);
                lX = L(lX, -3.0f, up);
                rX = L(rX, -1.1f, thrown);
                lX = L(lX, -1.1f, thrown);
                rZ = L(rZ, 0.45f, up);
                lZ = L(lZ, -0.45f, up);
                head = -0.3f * up;
            }
            case PhainonAttack.HALO -> {
                float up = Fx.smooth(Fx.ramp(t, 0, 18)) * (1f - Fx.smooth(Fx.ramp(t, 18, 23)));
                float down = Fx.smooth(Fx.ramp(t, 18, 23)) * (1f - Fx.smooth(Fx.ramp(t, 90, 106)));
                rX = L(rX, -3.0f, up);
                lX = L(lX, -3.0f, up);
                rX = L(rX, 0.35f, down);
                lX = L(lX, 0.35f, down);
                rZ = L(rZ, 0.85f, down);
                lZ = L(lZ, -0.85f, down);
            }
            case PhainonAttack.SWEEP -> {
                float k = Fx.smooth(Fx.ramp(t, 0, 20)) * (1f - Fx.smooth(Fx.ramp(t, 98, 112)));
                rX = L(rX, 0f, k);
                lX = L(lX, 0f, k);
                rZ = L(rZ, 1.5f, k);
                lZ = L(lZ, -1.5f, k);
                head = -0.15f * k;
            }
            case PhainonAttack.JUDGMENT -> {
                float up = Fx.smooth(Fx.ramp(t, 4, 34)) * (1f - Fx.smooth(Fx.ramp(t, 100, 106)));
                float thrown = Fx.smooth(Fx.ramp(t, 100, 106)) * (1f - Fx.smooth(Fx.ramp(t, 124, 150)));
                rX = L(rX, -3.1f, up);
                lX = L(lX, -3.1f, up);
                rX = L(rX, -0.7f, thrown);
                lX = L(lX, -0.7f, thrown);
                rZ = L(rZ, 0.7f, up);
                lZ = L(lZ, -0.7f, up);
                head = -0.35f * up;
            }
            case PhainonAttack.STAGGER -> {
                float k = Fx.smooth(Fx.ramp(t, 0, 10)) * (1f - Fx.smooth(Fx.ramp(t, 66, 80)));
                rX = L(rX, 0.7f, k);
                lX = L(lX, 0.7f, k);
                rZ = L(rZ, 0.12f, k);
                lZ = L(lZ, -0.12f, k);
                legX = L(legX, 0.55f, k);
                head = 0.6f * k;
            }
            default -> {
            }
        }
        if (ev == PhainonAttack.EV_INTRO) {
            float k = Fx.smooth(Fx.ramp(et, 16, 62));
            rX = L(-0.9f, 0f, k);
            lX = L(-0.9f, 0f, k);
            rZ = L(-0.5f, 1.25f, k);
            lZ = L(0.5f, -1.25f, k);
            head = L(0.35f, -0.2f, k);
        } else if (ev == PhainonAttack.EV_PHASE2 || ev == PhainonAttack.EV_PHASE3) {
            int len = PhainonAttack.EV_LENGTH[ev];
            float k = Fx.smooth(Fx.ramp(et, len * 0.35f, len * 0.6f));
            rX = L(-0.7f, 0f, k);
            lX = L(-0.7f, 0f, k);
            rZ = L(-0.4f, 1.3f, k);
            lZ = L(0.4f, -1.3f, k);
            head = L(0.3f, -0.25f, k);
        } else if (ev == PhainonAttack.EV_DEATH) {
            float k = Fx.smooth(Fx.ramp(et, 0, 30));
            rX = L(rX, 0.4f, k);
            lX = L(lX, 0.4f, k);
            rZ = L(rZ, 0.15f, k);
            lZ = L(lZ, -0.15f, k);
            head = 0.7f * k;
            legX = L(legX, 0.2f, k);
        }
        return new float[]{rX, rZ, lX, lZ, legX, legS, head};
    }
}
