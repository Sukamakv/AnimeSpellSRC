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
 * The visible half of Ea: Enuma Elish. It stands at the caster's eye, facing where they aim (its own yaw / pitch, the same
 * convention as an arrow), and EaFxRenderer draws everything from its age: the sword with three turning sections, the
 * vortex, the great wave and the explosion. EnumaElishManager moves it and does all the damage.
 */
public class EaFxEntity extends Entity {
    /** Ticks of invocation (the sword spins up) before the vortex starts to open. */
    public static final int VORTEX_START = 40;
    /** Tick on which the wave is released. */
    public static final int FIRE = 100;
    /** Ticks the wave takes to cross its whole length, whatever that length is. */
    public static final int TRAVEL = 40;
    /** Ticks the vortex takes to collapse after the explosion. */
    public static final int COLLAPSE = 40;

    private static final EntityDataAccessor<Float> DATA_LEN = SynchedEntityData.defineId(EaFxEntity.class, EntityDataSerializers.FLOAT);

    public EaFxEntity(EntityType<? extends EaFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public EaFxEntity(Level level) {
        this(ModEntities.EA_FX.get(), level);
    }

    /** Length of the wave in blocks; 0 until it is released. */
    public float getLen() {
        return this.entityData.get(DATA_LEN);
    }

    public void setLen(float len) {
        this.entityData.set(DATA_LEN, len);
    }

    /** Tick (entity age) on which the explosion happens, or -1 before the wave is released. */
    public int explosionTick() {
        float len = getLen();
        return len <= 0f ? -1 : FIRE + TRAVEL;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_LEN, 0f);
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
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > 400) {
            this.discard(); // safety net: the manager removes it long before
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.discard();
    }
}
