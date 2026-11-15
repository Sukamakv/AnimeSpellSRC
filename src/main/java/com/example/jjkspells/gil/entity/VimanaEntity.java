package com.example.jjkspells.gil.entity;

import com.example.jjkspells.gil.GilConfig;
import com.example.jjkspells.gil.GilEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Vimana, the king's golden flying vessel. A temporary, server-moved, rideable entity: the caster rides it (so the
 * caster's position stays in sync with the vessel on every client). VimanaRenderer draws the hull and the wings from
 * the synced "unfold" and "charge" values. VimanaManager moves it and runs the bombardment.
 */
public class VimanaEntity extends Entity {
    /** 0 = folded flat, 1 = fully open. */
    private static final EntityDataAccessor<Float> UNFOLD = SynchedEntityData.defineId(VimanaEntity.class, EntityDataSerializers.FLOAT);
    /** 0..1 energy gathered in the bow (the Divine Cannon). */
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(VimanaEntity.class, EntityDataSerializers.FLOAT);
    /** Ticks of stagger left (the vessel shudders and the bombardment pauses). */
    private static final EntityDataAccessor<Integer> STAGGER = SynchedEntityData.defineId(VimanaEntity.class, EntityDataSerializers.INT);

    public VimanaEntity(EntityType<? extends VimanaEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public VimanaEntity(Level level) {
        this(GilEntities.VIMANA.get(), level);
    }

    public float unfold() { return entityData.get(UNFOLD); }
    public void setUnfold(float v) { entityData.set(UNFOLD, v); }
    public float charge() { return entityData.get(CHARGE); }
    public void setCharge(float v) { entityData.set(CHARGE, v); }
    public int staggerTicks() { return entityData.get(STAGGER); }

    /** A strong magical hit on the rider: the vessel shudders and the bombardment pauses. */
    public void stagger(float damage) {
        if (damage >= GilConfig.VIMANA.staggerDamage.f() && staggerTicks() <= 0) {
            entityData.set(STAGGER, GilConfig.VIMANA.staggerTicks.i());
        }
    }

    /** Server tick of the manager: count the stagger down. Returns true while staggered. */
    public boolean staggered() {
        int s = staggerTicks();
        if (s > 0) {
            entityData.set(STAGGER, s - 1);
            return true;
        }
        return false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(UNFOLD, 0f);
        entityData.define(CHARGE, 0f);
        entityData.define(STAGGER, 0);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.55;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 20 * 60) discard(); // safety net
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }
}
