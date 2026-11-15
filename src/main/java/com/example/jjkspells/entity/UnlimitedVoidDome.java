package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The visible shell of Unlimited Void: a dark starry sphere. It only exists to be drawn
 * (see UnlimitedVoidDomeRenderer); all the gameplay is in UnlimitedVoidManager.
 */
public class UnlimitedVoidDome extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(UnlimitedVoidDome.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(UnlimitedVoidDome.class, EntityDataSerializers.INT);

    public UnlimitedVoidDome(EntityType<? extends UnlimitedVoidDome> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public UnlimitedVoidDome(Level level, float radius, int durationTicks) {
        this(ModEntities.UNLIMITED_VOID_DOME.get(), level);
        setup(radius, durationTicks);
    }

    public void setup(float radius, int durationTicks) {
        this.entityData.set(DATA_RADIUS, radius);
        this.entityData.set(DATA_DURATION, durationTicks);
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    /** Ticks the dome stays up (after its opening animation starts). */
    public int getDuration() {
        return this.entityData.get(DATA_DURATION);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 12f);
        this.entityData.define(DATA_DURATION, 120);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > getDuration() + 4) {
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.discard(); // never survives a reload
    }
}
