package com.example.jjkspells.pantheon.entity;

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
public abstract class PanEntityBase extends Entity {
    public static final UUID KB_ID = UUID.fromString("b8d4f6a2-3c75-4e21-8f9b-bd5f30000010");
    public static final UUID AEGIS_SPEED_ID = UUID.fromString("b8d4f6a2-3c75-4e21-8f9b-bd5f30000011");

    protected static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(PanEntityBase.class, EntityDataSerializers.INT);

    protected UUID ownerId;
    protected boolean finished;

    protected PanEntityBase(EntityType<?> type, Level level) {
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
