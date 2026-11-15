package com.example.jjkspells.overlord.entity;

import com.example.jjkspells.overlord.config.OverlordConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** The great Black Goat with a Thousand Young: huge, many-limbed, heavy melee with knockback. */
public class BlackGoatEntity extends GoatBase {
    public BlackGoatEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    public void configure(LivingEntity owner, float scale, int lifeTicks) {
        var c = OverlordConfig.GOAT;
        setupSummon(owner, c.goatHealth.get() * scale * HEALTH_MULT, lifeTicks);
        guardOwner(owner);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(c.goatAttackDamage.get() * scale * DAMAGE_MULT);
        this.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(c.goatKnockback.get());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(c.goatSpeed.get() * SPEED_MULT);
    }

    @Override
    protected double sweepRadius() {
        return 3.0;
    }
}
