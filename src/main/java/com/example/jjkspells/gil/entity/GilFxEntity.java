package com.example.jjkspells.gil.entity;

import com.example.jjkspells.ModParticles;
import com.example.jjkspells.gil.GilEntities;
import com.example.jjkspells.spells.FX;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The visible half of several Gilgamesh effects. The server only places it and sets what it is; GilFxRenderer draws it
 * from its age. Kinds:
 *   GIANT   a big weapon that emerges from a portal point, hangs, then plunges to a ground point
 *   SLASH   Merodach's golden crescent flying from start to end
 *   HELD    Merodach in the hand of its owner (follows the owner)
 *   CIRCLE  a rotating magic circle (gold or crimson) facing a normal, or lying flat
 *   MARK    a glowing golden impact scar on the ground
 */
public class GilFxEntity extends Entity {
    public static final int GIANT = 0, SLASH = 1, HELD = 2, CIRCLE = 3, MARK = 4;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DUR = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMERGE = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SX = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SY = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SZ = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EX = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EY = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EZ = SynchedEntityData.defineId(GilFxEntity.class, EntityDataSerializers.FLOAT);

    /** Server only: burst of gold when it ends. */
    private boolean burstAtEnd;

    public GilFxEntity(EntityType<? extends GilFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static GilFxEntity create(Level level, int kind, int type, int life, float scale, Vec3 pos) {
        GilFxEntity e = new GilFxEntity(GilEntities.FX.get(), level);
        e.entityData.set(KIND, kind);
        e.entityData.set(TYPE, type);
        e.entityData.set(LIFE, life);
        e.entityData.set(SCALE, scale);
        e.setPos(pos.x, pos.y, pos.z);
        return e;
    }

    public GilFxEntity path(Vec3 start, Vec3 end, int emerge, int dur) {
        entityData.set(SX, (float) start.x);
        entityData.set(SY, (float) start.y);
        entityData.set(SZ, (float) start.z);
        entityData.set(EX, (float) end.x);
        entityData.set(EY, (float) end.y);
        entityData.set(EZ, (float) end.z);
        entityData.set(EMERGE, emerge);
        entityData.set(DUR, dur);
        return this;
    }

    public GilFxEntity owner(Entity o) {
        entityData.set(OWNER, o.getId());
        return this;
    }

    public GilFxEntity burstAtEnd() {
        this.burstAtEnd = true;
        return this;
    }

    public int kind() { return entityData.get(KIND); }
    public int type() { return entityData.get(TYPE); }
    public int life() { return entityData.get(LIFE); }
    public int dur() { return entityData.get(DUR); }
    public int emerge() { return entityData.get(EMERGE); }
    public int ownerId() { return entityData.get(OWNER); }
    public float scale() { return entityData.get(SCALE); }
    public Vec3 start() { return new Vec3(entityData.get(SX), entityData.get(SY), entityData.get(SZ)); }
    public Vec3 end() { return new Vec3(entityData.get(EX), entityData.get(EY), entityData.get(EZ)); }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
        entityData.define(TYPE, 0);
        entityData.define(LIFE, 40);
        entityData.define(DUR, 10);
        entityData.define(EMERGE, 0);
        entityData.define(OWNER, -1);
        entityData.define(SCALE, 1f);
        entityData.define(SX, 0f);
        entityData.define(SY, 0f);
        entityData.define(SZ, 0f);
        entityData.define(EX, 0f);
        entityData.define(EY, 0f);
        entityData.define(EZ, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int k = kind();
        if (k == HELD) {
            Entity o = level().getEntity(ownerId());
            if (o == null || !o.isAlive()) {
                discard();
                return;
            }
            setPos(o.getX(), o.getY(), o.getZ());
        } else if (k == SLASH) {
            Vec3 s = start(), e = end();
            float f = Math.min(1f, tickCount / (float) Math.max(1, dur()));
            Vec3 p = s.lerp(e, f);
            setPos(p.x, p.y, p.z);
        } else {
            Vec3 e = end();
            if (k == GIANT || k == CIRCLE || k == MARK) {
                // placed by the manager at creation; nothing to move
                if (position().distanceToSqr(e) > 1e-6 && k == MARK) setPos(e.x, e.y, e.z);
            }
        }
        if (tickCount >= life()) {
            if (burstAtEnd && level() instanceof ServerLevel sl) {
                FX.one(sl, ModParticles.GOLD_GLOW.get(), end(), new Vec3(2.0 + scale() * 0.2, 0, 0));
                for (int i = 0; i < 10; i++) {
                    FX.one(sl, ModParticles.GOLD_SHARD.get(), end().add(0, 0.5, 0),
                            new Vec3((random.nextDouble() - 0.5) * 0.5, 0.15 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.5));
                }
            }
            discard();
        }
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
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }
}
