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
 * The big set-piece visuals of Tengai Shinsei, drawn by TengaiFxRenderer:
 * KIND_IMPACT = the dust ring and shock dome of the first meteor landing,
 * KIND_NUKE   = the flash, fireball, stem and mushroom cloud of the final explosion.
 * It sits on the ground at the centre of the effect and removes itself after its lifetime.
 */
public class TengaiFxEntity extends Entity {
    public static final int KIND_IMPACT = 0;
    public static final int KIND_NUKE = 1;

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(TengaiFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(TengaiFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(TengaiFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AUX = SynchedEntityData.defineId(TengaiFxEntity.class, EntityDataSerializers.FLOAT);

    public TengaiFxEntity(EntityType<? extends TengaiFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    /**
     * @param aux for the nuke: height of the blast point (where the meteors touch) above the ground
     */
    public TengaiFxEntity(Level level, int kind, int lifeTicks, float scale, float aux) {
        this(ModEntities.TENGAI_FX.get(), level);
        this.entityData.set(DATA_KIND, kind);
        this.entityData.set(DATA_LIFE, lifeTicks);
        this.entityData.set(DATA_SCALE, scale);
        this.entityData.set(DATA_AUX, aux);
    }

    public int getKind() {
        return this.entityData.get(DATA_KIND);
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public float getAux() {
        return this.entityData.get(DATA_AUX);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, KIND_IMPACT);
        this.entityData.define(DATA_LIFE, 80);
        this.entityData.define(DATA_SCALE, 1f);
        this.entityData.define(DATA_AUX, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > getLife() + 2) {
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
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.discard();
    }
}
