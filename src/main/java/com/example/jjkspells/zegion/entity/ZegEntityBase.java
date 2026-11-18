package com.example.jjkspells.zegion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Invisible controller entities: one clock (age = tickCount - 1), a synched owner id for the client, an idempotent finish(). */
public abstract class ZegEntityBase extends Entity {
    public static final UUID KB_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700010");
    public static final UUID PLATE_ARMOR_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700011");
    public static final UUID FORM_SPEED_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700012");

    protected static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(ZegEntityBase.class, EntityDataSerializers.INT);

    protected UUID ownerId;
    protected boolean finished;

    /** Server level for sprite effects (the sequences only ever run on the server). */
    protected ServerLevel sv() {
        return (ServerLevel) level();
    }

    /** Config numbers come as Double or Integer; sprite scales are floats. */
    protected static float f(Number n) {
        return n.floatValue();
    }

    protected ZegEntityBase(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    protected void setOwner(LivingEntity caster) {
        this.ownerId = caster.getUUID();
        this.entityData.set(DATA_OWNER, caster.getId());
    }

    /** The caster as seen from either side (client: looked up by the synched id). */
    public Entity getOwnerEntity() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    protected LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_OWNER, -1);
    }

    /** Releases everything the sequence holds on the caster. Must be safe to call more than once. */
    protected abstract void finish(boolean normal);

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            finish(false);
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256.0 * 256.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
