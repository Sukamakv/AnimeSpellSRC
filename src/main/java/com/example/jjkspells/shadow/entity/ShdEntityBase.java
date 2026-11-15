package com.example.jjkspells.shadow.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.example.jjkspells.shadow.util.Util;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Invisible controller entities: one clock (age = tickCount - 1), a synched owner id for the client, an idempotent finish(). */
public abstract class ShdEntityBase extends Entity {
    public static final UUID KB_ID = UUID.fromString("c7a2f815-9e31-4d6b-a0c4-6f8b20000010");

    protected static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(ShdEntityBase.class, EntityDataSerializers.INT);

    /** Short-lived visual events for the client: each entry is {type, age, values...}. */
    protected static final EntityDataAccessor<CompoundTag> DATA_FX = SynchedEntityData.defineId(ShdEntityBase.class, EntityDataSerializers.COMPOUND_TAG);

    private final List<double[]> fxServer = new ArrayList<>();
    private boolean fxDirty;
    private CompoundTag fxSeen;
    private List<double[]> fxCache = List.of();
    protected int fxKeep = 50;

    protected UUID ownerId;
    protected boolean finished, casterDone;

    protected ShdEntityBase(EntityType<?> type, Level level) {
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
        entityData.define(DATA_FX, new CompoundTag());
    }

    /** Adds a visual event for the clients: {type, age, values...}. */
    protected void pushFx(double type, double... v) {
        double[] e = new double[v.length + 2];
        e[0] = type;
        e[1] = tickCount - 1;
        System.arraycopy(v, 0, e, 2, v.length);
        fxServer.add(e);
        fxDirty = true;
    }

    /** The visual events as seen by the client (parsed only when they change). */
    public List<double[]> fx() {
        CompoundTag t = entityData.get(DATA_FX);
        if (t != fxSeen) {
            fxSeen = t;
            List<double[]> out = new ArrayList<>();
            for (Tag x : t.getList("l", Tag.TAG_LIST)) {
                ListTag l = (ListTag) x;
                double[] d = new double[l.size()];
                for (int i = 0; i < d.length; i++) {
                    d[i] = l.getDouble(i);
                }
                out.add(d);
            }
            fxCache = out;
        }
        return fxCache;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (fxDirty || !fxServer.isEmpty())) {
            int now = tickCount - 1;
            boolean pruned = fxServer.removeIf(e -> now - e[1] > fxKeep);
            if (fxDirty || pruned) {
                ListTag l = new ListTag();
                for (double[] e : fxServer) {
                    ListTag one = new ListTag();
                    for (double d : e) {
                        one.add(DoubleTag.valueOf(d));
                    }
                    l.add(one);
                }
                CompoundTag t = new CompoundTag();
                t.put("l", l);
                entityData.set(DATA_FX, t);
                fxDirty = false;
            }
        }
    }

    /** Keeps a locked caster standing still (the client input is locked too). */
    protected void freeze(LivingEntity o) {
        o.setSprinting(false);
        if (!o.isNoGravity()) {
            Vec3 m = o.getDeltaMovement();
            o.setDeltaMovement(0, Math.min(m.y, 0), 0);
            o.hurtMarked = true;
        }
    }

    /** Looks an enemy up by entity id (server). */
    protected LivingEntity byId(int id) {
        return level().getEntity(id) instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    /** Health this cast may still give the caster, and how much it has given. */
    protected double healCap = 40, healDone;

    /** Gives the caster health from a consumed enemy until the per-cast cap is used up. */
    protected void absorb(double amount) {
        LivingEntity c = owner();
        if (c == null || amount <= 0) {
            return;
        }
        double a = Math.min(amount, healCap - healDone);
        if (a > 0) {
            healDone += Util.heal(c, a);
        }
    }

    /** The enemy the caster looks at (within range, 3 degrees of leeway), else the nearest one; null if none. */
    public static LivingEntity pickTarget(LivingEntity caster, double range) {
        var hit = io.redspace.ironsspellbooks.api.util.Utils.raycastForEntity(caster.level(), caster, (float) range, true, 0.3f);
        if (hit instanceof net.minecraft.world.phys.EntityHitResult er && er.getEntity() instanceof LivingEntity le && Util.isEnemy(caster, le)) {
            return le;
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Util.enemies(caster.level(), caster.position(), range, caster)) {
            double d = e.distanceToSqr(caster);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
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
