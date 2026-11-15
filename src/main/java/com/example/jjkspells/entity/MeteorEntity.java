package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * One of the two giant meteors of Tengai Shinsei. It is only a drawn shape (see MeteorRenderer): the server-side
 * TengaiShinseiManager moves it every tick and does all the damage. Because it is moved by position packets it
 * has no hitbox worth speaking of and nothing can collide with it.
 */
public class MeteorEntity extends Entity {
    public static final int STATE_FALLING = 0;
    public static final int STATE_LANDED = 1;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_START_Y = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LAND_Y = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);

    /** Client side: ticks since this meteor was seen to land (drives the cooling glow). */
    public int landedTicks;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public MeteorEntity(Level level, int variant, float radius, float startY, float landY) {
        this(ModEntities.METEOR.get(), level);
        this.entityData.set(DATA_VARIANT, variant);
        this.entityData.set(DATA_RADIUS, radius);
        this.entityData.set(DATA_START_Y, startY);
        this.entityData.set(DATA_LAND_Y, landY);
    }

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    public boolean isFalling() {
        return getState() == STATE_FALLING;
    }

    public void setLanded() {
        this.entityData.set(DATA_STATE, STATE_LANDED);
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    /** 0 at the top of the fall, 1 when it touches down (measured by height, so it speeds up as it falls). */
    public float getFallFraction(double y) {
        float start = this.entityData.get(DATA_START_Y);
        float land = this.entityData.get(DATA_LAND_Y);
        if (start <= land + 0.001f) {
            return 1f;
        }
        return Mth.clamp((float) ((start - y) / (start - land)), 0f, 1f);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, STATE_FALLING);
        this.entityData.define(DATA_VARIANT, 0);
        this.entityData.define(DATA_RADIUS, 20f);
        this.entityData.define(DATA_START_Y, 300f);
        this.entityData.define(DATA_LAND_Y, 64f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (getState() == STATE_LANDED) {
                this.landedTicks++;
            }
        } else if (this.tickCount > 20 * 90) {
            this.discard(); // safety net: the manager always removes it long before this
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
    public boolean displayFireAnimation() {
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
