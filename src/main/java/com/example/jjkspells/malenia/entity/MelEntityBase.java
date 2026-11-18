package com.example.jjkspells.malenia.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.vfx.sprite.SpriteFx;
import net.minecraft.world.phys.Vec3;
import com.example.jjkspells.malenia.util.Util;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Invisible controller entities: one clock (age = tickCount - 1), a synched owner id for the client, an idempotent finish(). */
public abstract class MelEntityBase extends Entity {
    public static final UUID KB_ID = UUID.fromString("d1f6b8c4-5e97-4a43-8b1d-df7b50000010");

    protected static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(MelEntityBase.class, EntityDataSerializers.INT);

    protected UUID ownerId;
    protected boolean finished, casterDone;

    protected MelEntityBase(EntityType<?> type, Level level) {
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

    /** Total health this cast may still steal, and how much it has stolen. */
    protected double lsCap = 50, lsDone;

    /** Lifesteal: heals the caster (scaled by the lifesteal multiplier) until the per-cast cap is used up. */
    protected void steal(double amount) {
        LivingEntity c = owner();
        if (c == null || amount <= 0) {
            return;
        }
        double a = Math.min(amount * com.example.jjkspells.malenia.util.Util.lifestealMult(c), lsCap - lsDone);
        if (a > 0) {
            c.heal((float) a);
            lsDone += a;
        }
    }

    /** Holds the caster at a place in the air (no gravity), facing a direction, never pushing it into a wall. */
    protected void hover(LivingEntity o, Vec3 p, float yaw, float pitch) {
        o.setNoGravity(true);
        if (!Util.moveTo(o, p, yaw, pitch)) {
            o.setYRot(yaw);
            o.setYHeadRot(yaw);
            o.setXRot(pitch);
        }
        o.setDeltaMovement(Vec3.ZERO);
        o.hurtMarked = true;
        o.fallDistance = 0;
    }

    protected static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Server level for sprite effects (the sequences only ever run on the server). */
    protected net.minecraft.server.level.ServerLevel sv() {
        return (net.minecraft.server.level.ServerLevel) level();
    }

    /** Config numbers come as Double or Integer; sprite scales are floats. */
    protected static float f(Number n) {
        return n.floatValue();
    }

    protected static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** The caster as found by id on the server (also when it just died). */
    protected LivingEntity ownerAny() {
        if (ownerId != null && level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    /** Gives back everything the long sequences take from the caster (lock, hover, hiding, reductions that were set for the sequence). */
    protected void releaseCommon(boolean normal) {
        if (casterDone || ownerId == null) {
            return;
        }
        casterDone = true;
        Util.LOCKED.remove(ownerId);
        Util.HIDDEN.remove(ownerId);
        Util.UNTOUCHABLE.remove(ownerId);
        LivingEntity o = ownerAny();
        if (o != null) {
            o.setNoGravity(false);
            o.setInvisible(false);
            Util.FALL_IMMUNE.merge(ownerId, o.level().getGameTime() + 60, Math::max);
            AttributeInstance kb = o.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(KB_ID);
            }
            if (!normal) {
                Util.clearReduce(o);
                Util.clearProjReduce(o);
            }
        }
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
