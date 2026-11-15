package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** DIO's road roller: a temporary, server-moved prop (no vehicle physics). */
public class RoadRollerEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_SHAKE = SynchedEntityData.defineId(RoadRollerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VANISH = SynchedEntityData.defineId(RoadRollerEntity.class, EntityDataSerializers.INT);

    public RoadRollerEntity(EntityType<? extends RoadRollerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static RoadRollerEntity create(Level level) {
        return new RoadRollerEntity(ModEntities.ROAD_ROLLER.get(), level);
    }

    /** 0 = calm, up to 10 = heavy shuddering (the crushing blows). */
    public int getShake() { return this.entityData.get(DATA_SHAKE); }
    public void setShake(int s) { this.entityData.set(DATA_SHAKE, s); }

    public void vanish() {
        if (this.entityData.get(DATA_VANISH) < 0) this.entityData.set(DATA_VANISH, 5);
    }

    public float alpha(float partial) {
        float in = Math.min(1f, (this.tickCount + partial) / 8f);
        int v = this.entityData.get(DATA_VANISH);
        return in * (v < 0 ? 1f : Math.max(0f, (v - partial) / 5f));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            int v = this.entityData.get(DATA_VANISH);
            if (v >= 0) {
                if (v == 0) { this.discard(); return; }
                this.entityData.set(DATA_VANISH, v - 1);
            }
        }
    }

    @Override protected void defineSynchedData() {
        this.entityData.define(DATA_SHAKE, 0);
        this.entityData.define(DATA_VANISH, -1);
    }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
