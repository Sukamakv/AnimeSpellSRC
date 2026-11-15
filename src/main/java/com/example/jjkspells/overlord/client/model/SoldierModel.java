package com.example.jjkspells.overlord.client.model;

import com.example.jjkspells.overlord.entity.UndeadSoldierEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.AnimationUtils;

/** Skeleton body with the arms raised when the soldier is chasing a target. */
public class SoldierModel extends HumanoidModel<UndeadSoldierEntity> {
    public SoldierModel(ModelPart part) {
        super(part);
    }

    @Override
    public void setupAnim(UndeadSoldierEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
        if (e.isAggressive()) {
            AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, true, this.attackTime, age);
        }
    }
}
