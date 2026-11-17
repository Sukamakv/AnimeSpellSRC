package com.example.jjkspells.yone.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yone.YoneRegistry;
import com.example.jjkspells.yone.spell.FateSealedSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of "Final Sunder". The entity sits at the start of the line; the facing and the length of the line (up to where something solid blocks it)
 * are fixed at cast.
 * <pre>
 *   1 .. DASH            the line is drawn, Yone is rooted
 *   DASH                 he dashes along the line, cutting and launching everything on it; they are held in the air
 *   SUNDER = DASH + HOLD the line is severed: everyone sealed on it is hit again and slammed down
 * </pre>
 */
public class FateSealedEntity extends Entity {
    public static final int DASH = FateSealedSpell.WINDUP;
    public static final int SUNDER = DASH + FateSealedSpell.HOLD;
    public static final int END = SUNDER + 28;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FateSealedEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(FateSealedEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> LEN = SynchedEntityData.defineId(FateSealedEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(FateSealedEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final List<UUID> sealed = new ArrayList<>();
    private final List<Integer> sealedIds = new ArrayList<>();
    private final Map<UUID, Double> baseY = new HashMap<>();
    private final Map<UUID, Boolean> noGrav = new HashMap<>();
    private boolean released = false;
    private int[] clientSealed = new int[0];

    public FateSealedEntity(EntityType<? extends FateSealedEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static FateSealedEntity create(Level level, LivingEntity caster) {
        FateSealedEntity e = new FateSealedEntity(YoneRegistry.FATE, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : dir.normalize();
        e.entityData.set(DIR, new Vector3f((float) dir.x, 0f, (float) dir.z));
        // how far the line runs before something solid blocks it
        double len = 0;
        Vec3 o = caster.position();
        for (double f = 1.0; f <= FateSealedSpell.RANGE; f += 1.0) {
            Vec3 c = o.add(dir.scale(f));
            if (level.noCollision(caster, caster.getBoundingBox().move(c.subtract(o)))) {
                len = f;
            } else {
                break;
            }
        }
        e.entityData.set(LEN, (float) Math.max(len, 3.0));
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(DIR, new Vector3f(0, 0, 1));
        this.entityData.define(LEN, 10f);
        this.entityData.define(LOG, new CompoundTag());
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

    public Vec3 dir() {
        Vector3f v = this.entityData.get(DIR);
        return new Vec3(v.x(), 0, v.z());
    }

    public double length() {
        return this.entityData.get(LEN);
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: enemies sealed on the line. */
    public int[] sealedIds() {
        return clientSealed;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            release();
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ server

    private boolean isEnemy(LivingEntity t, LivingEntity owner) {
        if (t == owner || !t.isAlive() || t.isSpectator() || t instanceof ArmorStand) {
            return false;
        }
        if (owner != null) {
            if (owner.isAlliedTo(t)) {
                return false;
            }
            if (t instanceof TamableAnimal ta && ta.isOwnedBy(owner)) {
                return false;
            }
        }
        return true;
    }

    private void hit(LivingEntity t, LivingEntity owner, double flat, double percent) {
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        float flatDmg = (float) (flat);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, dsrc);
        }
    }

    private void release() {
        if (released || !(level() instanceof ServerLevel sl)) {
            return;
        }
        released = true;
        for (UUID id : sealed) {
            Entity e = sl.getEntity(id);
            if (e != null) {
                e.setNoGravity(noGrav.getOrDefault(id, false));
            }
        }
    }

    private void tp(LivingEntity e, Vec3 p, float yaw) {
        if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, yaw, e.getXRot());
        } else {
            e.moveTo(p.x, p.y, p.z, yaw, e.getXRot());
        }
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (owner == null) {
            release();
            discard();
            return;
        }
        Vec3 o = position();
        Vec3 dir = dir();
        double len = length();
        if (tickCount == 1) {
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 1.0f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.5f, 1.8f);
            com.example.jjkspells.vfx.sprite.SpriteFx.beam(sl, o.add(0, 0.2, 0), o.add(dir.scale(len)).add(0, 0.2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (FateSealedSpell.HALF_WIDTH * 2.0), DASH - 1);
        }
        if (tickCount < SUNDER) {
            owner.setDeltaMovement(0, Math.min(0.0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            owner.invulnerableTime = Math.max(owner.invulnerableTime, 6);
            owner.fallDistance = 0;
        }
        if (tickCount == DASH) {
            dash(sl, owner, o, dir, len);
        }
        if (tickCount > DASH && tickCount < SUNDER) {
            for (UUID id : sealed) {
                Entity e = sl.getEntity(id);
                if (!(e instanceof LivingEntity t) || !t.isAlive()) {
                    continue;
                }
                t.setNoGravity(true);
                t.fallDistance = 0;
                double want = baseY.get(id) + FateSealedSpell.LIFT;
                double vy = t.getY() < want - 0.05 ? Math.min(0.7, (want - t.getY()) * 0.4 + 0.1) : 0.0;
                t.setDeltaMovement(0, vy, 0);
                t.hurtMarked = true;
            }
        }
        if (tickCount == SUNDER) {
            release();
            for (UUID id : sealed) {
                Entity e = sl.getEntity(id);
                if (e instanceof LivingEntity t && t.isAlive()) {
                    hit(t, owner, FateSealedSpell.SUNDER_FLAT, FateSealedSpell.SUNDER_PERCENT);
                    com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2.2f);
                    t.setDeltaMovement(0, -1.6, 0);
                    t.hurtMarked = true;
                    t.fallDistance = 0;
                    level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 1.6f);
                }
            }
            Vec3 end = o.add(dir.scale(len));
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, o.add(dir.scale(len * 0.5)).add(0, 1.5, 0), new Vec3(-dir.z, 0, dir.x), 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) Math.max(4.0, len * 0.6));
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, end, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, end.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3f);
            level().playSound(null, end.x, end.y + 2, end.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.5f, 1.8f);
            level().playSound(null, o.x, o.y + 2, o.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.5f, 0.8f);
            level().playSound(null, end.x, end.y + 2, end.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 3.0f, 0.6f);
        }
    }

    private void dash(ServerLevel sl, LivingEntity owner, Vec3 o, Vec3 dir, double len) {
        double hw = FateSealedSpell.HALF_WIDTH;
        Vec3 end = o.add(dir.scale(len));
        AABB box = new AABB(Math.min(o.x, end.x), o.y - 4, Math.min(o.z, end.z), Math.max(o.x, end.x), o.y + 6, Math.max(o.z, end.z)).inflate(hw + 1.0);
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isEnemy(t, owner)) {
                continue;
            }
            Vec3 rel = new Vec3(t.getX() - o.x, 0, t.getZ() - o.z);
            double along = rel.dot(dir);
            double lat = rel.subtract(dir.scale(along)).length();
            if (along < -1.0 || along > len + 1.5 || lat > hw + t.getBbWidth() * 0.5 || Math.abs(t.getY() - o.y) > 4.5) {
                continue;
            }
            hit(t, owner, FateSealedSpell.DASH_FLAT, FateSealedSpell.DASH_PERCENT);
            t.setDeltaMovement(0, 0.9, 0);
            t.hurtMarked = true;
            sealed.add(t.getUUID());
            sealedIds.add(t.getId());
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.8f);
            baseY.put(t.getUUID(), t.getY());
            noGrav.put(t.getUUID(), t.isNoGravity());
            level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.5f, 1.3f);
            level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 1.6f);
        }
        int[] ids = new int[sealedIds.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = sealedIds.get(i);
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("t", ids);
        this.entityData.set(LOG, tag);
        com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, o.add(dir.scale(len * 0.5)).add(0, 1.2, 0), new Vec3(-dir.z, 0, dir.x), 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) Math.max(4.0, len * 0.6));
        com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, o.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.8f);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        tp(owner, end, yaw);
        owner.setDeltaMovement(0, 0, 0);
        owner.hurtMarked = true;
        level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 3.0f, 1.4f);
        level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 3.0f, 1.9f);
        level().playSound(null, end.x, end.y + 1, end.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 3.0f, 1.2f);
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        clientSealed = this.entityData.get(LOG).getIntArray("t");
        if (tickCount == DASH + 1) {
            Vec3 d = dir();
            double len = length();
            for (int i = 0; i < 30; i++) {
                double f = random.nextDouble() * len;
                level().addParticle(ParticleTypes.CLOUD, getX() + d.x * f, getY() + 0.3 + random.nextDouble() * 1.6, getZ() + d.z * f, (random.nextDouble() - 0.5) * 0.3, 0.1, (random.nextDouble() - 0.5) * 0.3);
            }
        }
    }
}
