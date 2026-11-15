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
import net.minecraft.world.phys.Vec3;

/**
 * Every visible thing of Laevatain's spells: magma fragments, eruption pillars, crescent slashes, the spectral
 * greatswords, explosions, ground cracks and flame walls. It is drawn by LaevatainFxRenderer and only ever moved,
 * rotated and resized by the server (LaevatainManager); it does no damage itself and removes itself when its
 * lifetime runs out. The smoothed copies of the synced values exist so a 20-per-second server update looks fluid.
 */
public class LaevatainFxEntity extends Entity {
    public static final int FRAGMENT = 0;   // a floating magma rock. aux 1 = the big projectile (orbiting chunks + fire trail)
    public static final int PILLAR = 1;     // erupting magma pillar. scale = radius, aux = height, aux2 = collapse ticks
    public static final int CRESCENT = 2;   // flaming crescent slash. scale = half width, aux = height
    public static final int SWORD = 3;      // spectral greatsword. scale = blade length, angle = pitch from straight up, roll = twist; aux 1 = dark crimson (empowered)
    public static final int BURST = 4;      // explosion. scale = radius, aux 0 orange / 1 crimson / 2 white-hot
    public static final int CRACKS = 5;     // glowing ground cracks. scale = radius, aux 1 = burning ground, aux2 = ticks to spread
    public static final int WALL = 6;       // line of flames + scorch marks along +Z of the entity. aux = length, scale = half width

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(LaevatainFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(LaevatainFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(LaevatainFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AUX = SynchedEntityData.defineId(LaevatainFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AUX2 = SynchedEntityData.defineId(LaevatainFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANGLE = SynchedEntityData.defineId(LaevatainFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(LaevatainFxEntity.class, EntityDataSerializers.FLOAT);

    /** Client side: previous and current value of the things that change every tick, for smooth drawing. */
    public float oScale, curScale, oAngle, curAngle, oRoll, curRoll;
    private Vec3 drift = Vec3.ZERO;

    public LaevatainFxEntity(EntityType<? extends LaevatainFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public LaevatainFxEntity(Level level, int kind, int life, float scale) {
        this(ModEntities.LAEVATAIN_FX.get(), level);
        this.entityData.set(DATA_KIND, kind);
        this.entityData.set(DATA_LIFE, life);
        this.entityData.set(DATA_SCALE, scale);
        this.oScale = this.curScale = scale;
    }

    // ---- accessors -------------------------------------------------------------------------------
    public int getKind() { return this.entityData.get(DATA_KIND); }
    public int getLife() { return this.entityData.get(DATA_LIFE); }
    public float getScale() { return this.entityData.get(DATA_SCALE); }
    public float getAux() { return this.entityData.get(DATA_AUX); }
    public float getAux2() { return this.entityData.get(DATA_AUX2); }
    public float getAngle() { return this.entityData.get(DATA_ANGLE); }
    public float getRoll() { return this.entityData.get(DATA_ROLL); }

    public LaevatainFxEntity scale(float v) { this.entityData.set(DATA_SCALE, v); return this; }
    public LaevatainFxEntity aux(float v) { this.entityData.set(DATA_AUX, v); return this; }
    public LaevatainFxEntity aux2(float v) { this.entityData.set(DATA_AUX2, v); return this; }
    public LaevatainFxEntity angle(float v) { this.entityData.set(DATA_ANGLE, v); return this; }
    public LaevatainFxEntity roll(float v) { this.entityData.set(DATA_ROLL, v); return this; }
    public LaevatainFxEntity heading(float yawDegrees) { this.setYRot(yawDegrees); this.yRotO = yawDegrees; return this; }
    public LaevatainFxEntity life(int ticks) { this.entityData.set(DATA_LIFE, ticks); return this; }

    /** Server side: let the entity glide on its own (for visual-only crescents), blocks per tick. */
    public LaevatainFxEntity drift(Vec3 perTick) { this.drift = perTick; return this; }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, FRAGMENT);
        this.entityData.define(DATA_LIFE, 40);
        this.entityData.define(DATA_SCALE, 1f);
        this.entityData.define(DATA_AUX, 0f);
        this.entityData.define(DATA_AUX2, 0f);
        this.entityData.define(DATA_ANGLE, 0f);
        this.entityData.define(DATA_ROLL, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.tickCount <= 1) {
                this.oScale = this.curScale = getScale();
                this.oAngle = this.curAngle = getAngle();
                this.oRoll = this.curRoll = getRoll();
            } else {
                this.oScale = this.curScale;
                this.curScale = getScale();
                this.oAngle = this.curAngle;
                this.curAngle = getAngle();
                this.oRoll = this.curRoll;
                this.curRoll = getRoll();
            }
        } else {
            if (this.drift.lengthSqr() > 0) {
                this.setPos(this.getX() + drift.x, this.getY() + drift.y, this.getZ() + drift.z);
            }
            if (this.tickCount > getLife() + 2) {
                this.discard();
            }
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean displayFireAnimation() { return false; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { this.discard(); }
}
