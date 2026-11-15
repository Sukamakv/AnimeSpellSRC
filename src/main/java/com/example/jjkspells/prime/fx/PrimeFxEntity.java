package com.example.jjkspells.prime.fx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * One visual of the PRIME Sukuna spells. It does nothing but exist for {@code life} ticks: the server managers own all the
 * gameplay, and the renderer draws the PNG animations from the synced numbers (kind, direction, size ...).
 * Moving things (slashes) are not moved: the renderer places them at {@code pos + dir * speed * age}.
 */
public class PrimeFxEntity extends Entity {
    public static final int SLASH = 0;      // Boundless Slash crescent (black and white)
    public static final int CSLASH = 1;     // cosmic crescent of Universe Severing Slash
    public static final int CHARGE = 2;     // World Disintegration arrow charging in front of the owner
    public static final int BEAM = 3;       // World Disintegration laser, pos -> p2
    public static final int PILLAR = 4;     // the dark red pillar of fire, size = radius
    public static final int LATTICE = 5;    // Universe Severing Slash lattice + black hole
    public static final int BLAST = 6;      // flash and rings, aux = palette (0 cosmic, 1 red, 2 white)
    public static final int TEXT = 7;       // "DISMANTLE!!"

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AUX = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> P2 = SynchedEntityData.defineId(PrimeFxEntity.class, EntityDataSerializers.VECTOR3);

    public PrimeFxEntity(EntityType<? extends PrimeFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Spawns a visual. Any argument that a kind does not use can be 0 / null. */
    public static PrimeFxEntity spawn(ServerLevel level, int kind, Vec3 pos, Vec3 dir, Vec3 p2, float size, int life, float speed, float aux, int ownerId) {
        PrimeFxEntity e = new PrimeFxEntity(PrimeFxRegistry.TYPE, level);
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(KIND, kind);
        e.entityData.set(LIFE, Math.max(1, life));
        e.entityData.set(OWNER, ownerId);
        e.entityData.set(SIZE, size);
        e.entityData.set(SPEED, speed);
        e.entityData.set(AUX, aux);
        if (dir != null) e.entityData.set(DIR, new Vector3f((float) dir.x, (float) dir.y, (float) dir.z));
        if (p2 != null) e.entityData.set(P2, new Vector3f((float) p2.x, (float) p2.y, (float) p2.z));
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, -1);
        this.entityData.define(LIFE, 20);
        this.entityData.define(OWNER, -1);
        this.entityData.define(SIZE, 1f);
        this.entityData.define(SPEED, 0f);
        this.entityData.define(AUX, 0f);
        this.entityData.define(DIR, new Vector3f(0, 0, 1));
        this.entityData.define(P2, new Vector3f());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > getLife()) discard();
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public int getLife() {
        return this.entityData.get(LIFE);
    }

    public int getOwnerId() {
        return this.entityData.get(OWNER);
    }

    public float getSize() {
        return this.entityData.get(SIZE);
    }

    public float getSpeed() {
        return this.entityData.get(SPEED);
    }

    public float getAux() {
        return this.entityData.get(AUX);
    }

    public Vec3 getFxDir() {
        Vector3f v = this.entityData.get(DIR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Vec3 getP2() {
        Vector3f v = this.entityData.get(P2);
        return new Vec3(v.x(), v.y(), v.z());
    }
}
