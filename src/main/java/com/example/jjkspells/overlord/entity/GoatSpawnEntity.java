package com.example.jjkspells.overlord.entity;

import com.example.jjkspells.overlord.config.OverlordConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** A Young of the Black Goat: fast, fragile, and every strike hits everything around the target. */
public class GoatSpawnEntity extends GoatBase {
    public GoatSpawnEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.40)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public void configure(LivingEntity owner, float scale, int lifeTicks) {
        var c = OverlordConfig.GOAT;
        setupSummon(owner, c.offspringHealth.get() * scale * HEALTH_MULT, lifeTicks);
        guardOwner(owner);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(c.offspringAttackDamage.get() * scale * DAMAGE_MULT);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(c.offspringSpeed.get() * SPEED_MULT);
    }

    @Override
    protected double sweepRadius() {
        return OverlordConfig.GOAT.sweepRadius.get();
    }
}
