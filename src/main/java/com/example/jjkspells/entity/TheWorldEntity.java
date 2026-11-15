package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The World, DIO's Stand. Moved and posed by the server (DioManager), drawn by TheWorldRenderer. */
public class TheWorldEntity extends Entity {
    public static final int POSE_IDLE = 0, POSE_BARRAGE = 1, POSE_HEAVY = 2, POSE_LIFT = 3, POSE_SMASH = 4, POSE_WINDUP = 5, POSE_SLAM = 6,
            POSE_GUARD = 7, POSE_FINGER = 8, POSE_DASH = 9, POSE_RECOVER = 10;
    public static final int VANISH_TICKS = 6;

    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(TheWorldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(TheWorldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_START = SynchedEntityData.defineId(TheWorldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VANISH = SynchedEntityData.defineId(TheWorldEntity.class, EntityDataSerializers.INT);

    public TheWorldEntity(EntityType<? extends TheWorldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static TheWorldEntity create(Level level) {
        return new TheWorldEntity(ModEntities.THE_WORLD.get(), level);
    }

    /** Star Platinum, Jotaro's Stand: the same body, a different skin, aura and a few extra poses. */
    public static TheWorldEntity createStar(Level level) {
        TheWorldEntity e = create(level);
        e.entityData.set(DATA_VARIANT, 1);
        return e;
    }

    public boolean isStar() { return this.entityData.get(DATA_VARIANT) == 1; }

    public int getPose2() { return this.entityData.get(DATA_POSE); }

    public void setPose2(int pose) {
        if (pose == getPose2()) return;
        this.entityData.set(DATA_POSE, pose);
        this.entityData.set(DATA_START, this.tickCount);
    }

    /** Ticks since the current pose began (client and server tickCount stay close enough for animation). */
    public float poseTicks(float partial) {
        return this.tickCount + partial - this.entityData.get(DATA_START);
    }

    public void vanish() {
        if (this.entityData.get(DATA_VANISH) < 0) this.entityData.set(DATA_VANISH, VANISH_TICKS);
    }

    /** 0..1, 1 fully visible. */
    public float alpha(float partial) {
        float in = Math.min(1f, (this.tickCount + partial) / 5f);
        int v = this.entityData.get(DATA_VANISH);
        float out = v < 0 ? 1f : Math.max(0f, (v - partial) / VANISH_TICKS);
        return in * out;
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
        this.entityData.define(DATA_POSE, 0);
        this.entityData.define(DATA_START, 0);
        this.entityData.define(DATA_VARIANT, 0);
        this.entityData.define(DATA_VANISH, -1);
    }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
