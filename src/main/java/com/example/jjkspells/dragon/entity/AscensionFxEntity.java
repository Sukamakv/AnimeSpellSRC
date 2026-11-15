package com.example.jjkspells.dragon.entity;

import com.example.jjkspells.dragon.config.DragonConfig;
import com.example.jjkspells.dragon.effect.AuthorityEffect;
import com.example.jjkspells.dragon.registry.ModEffects;
import com.example.jjkspells.dragon.registry.ModEntities;
import com.example.jjkspells.dragon.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of True Dragon Ascension. Sits on the ground under the caster. Timeline (ticks):
 * 0..rise: caster is lifted straight up and frozen; golden circles; at rise: the beam fires and the buff is granted;
 * until rise+beam: the caster stays frozen; then they are released hovering (creative flight from the buff).
 */
public class AscensionFxEntity extends Entity {
    /** Server side: casters currently frozen by the ascension. */
    public static final Set<UUID> ASCENDING = Collections.synchronizedSet(new HashSet<>());

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(AscensionFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RISE = SynchedEntityData.defineId(AscensionFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM = SynchedEntityData.defineId(AscensionFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(AscensionFxEntity.class, EntityDataSerializers.FLOAT);

    private double startY;

    public AscensionFxEntity(EntityType<? extends AscensionFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static AscensionFxEntity create(Level level, LivingEntity owner, double groundY) {
        var c = DragonConfig.SPELL;
        AscensionFxEntity e = new AscensionFxEntity(ModEntities.FX.get(), level);
        e.setPos(owner.getX(), groundY, owner.getZ());
        e.entityData.set(DATA_OWNER, owner.getId());
        e.entityData.set(DATA_RISE, Math.max(10, (int) Math.round(c.riseSeconds.get() * 20)));
        e.entityData.set(DATA_BEAM, Math.max(10, (int) Math.round(c.beamSeconds.get() * 20)));
        e.entityData.set(DATA_RADIUS, c.circleRadius.get().floatValue());
        e.startY = owner.getY();
        return e;
    }

    public int getRise() {
        return entityData.get(DATA_RISE);
    }

    public int getBeam() {
        return entityData.get(DATA_BEAM);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    /** Whole timeline including the beam's fade-out. */
    public int getDuration() {
        return getRise() + getBeam() + 24;
    }

    public int lockEnd() {
        return getRise() + getBeam();
    }

    public boolean isLocking() {
        return tickCount < lockEnd();
    }

    public Entity getOwnerEntity() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_OWNER, -1);
        entityData.define(DATA_RISE, 60);
        entityData.define(DATA_BEAM, 60);
        entityData.define(DATA_RADIUS, 5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.dragon.client.ClientFx.tick(this));
            return;
        }
        if (!(getOwnerEntity() instanceof LivingEntity owner) || !owner.isAlive()) {
            discard();
            return;
        }
        int age = tickCount;
        int rise = getRise();
        int lockEnd = lockEnd();
        if (age == 0) {
            ASCENDING.add(owner.getUUID());
            owner.setNoGravity(true);
            level().playSound(null, getX(), getY(), getZ(), ModSounds.ASCEND.get(), SoundSource.PLAYERS, 3.0f, 1.0f);
        }
        if (age < lockEnd) {
            double h = DragonConfig.SPELL.riseHeight.get();
            double f = Math.min(1.0, age / (double) rise);
            double ease = f * f * (3 - 2 * f); // smoothstep
            double targetY = startY + h * ease;
            double dy = Mth.clamp(targetY - owner.getY(), -0.6, 0.6);
            owner.setDeltaMovement(0, dy, 0);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
            double dx = owner.getX() - getX(), dz = owner.getZ() - getZ();
            if (dx * dx + dz * dz > 0.1) {
                if (owner instanceof ServerPlayer sp) {
                    sp.connection.teleport(getX(), owner.getY(), getZ(), sp.getYRot(), sp.getXRot());
                } else {
                    owner.setPos(getX(), owner.getY(), getZ());
                }
            }
        }
        if (age == rise) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.BEAM.get(), SoundSource.PLAYERS, 5.0f, 1.0f);
            int ticks = (int) Math.round(DragonConfig.BUFF.durationSeconds.get() * 20);
            owner.addEffect(new MobEffectInstance(ModEffects.AUTHORITY.get(), ticks, 0, false, true, true));
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.AUTHORITY.get(), SoundSource.PLAYERS, 2.5f, 1.0f);
        }
        if (age == lockEnd) {
            release(owner);
        }
    }

    private void release(LivingEntity owner) {
        ASCENDING.remove(owner.getUUID());
        owner.setNoGravity(false);
        owner.setDeltaMovement(0, 0, 0);
        owner.hurtMarked = true;
        if (owner instanceof Player p && p.getAbilities().mayfly) {
            AuthorityEffect.grantFlight(p);
            p.getAbilities().flying = true; // hover where the ascension left them
            p.onUpdateAbilities();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && getOwnerEntity() instanceof LivingEntity owner && ASCENDING.contains(owner.getUUID())) {
            release(owner); // interrupted (death, dimension change...): never leave anyone stuck without gravity
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
        return d < 512.0 * 512.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
