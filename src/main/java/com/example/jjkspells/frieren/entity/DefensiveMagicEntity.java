package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.frieren.FrierenRegistry;
import com.example.jjkspells.frieren.spell.DefensiveMagicSpell;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Frieren's barrier. A controller that follows the caster and applies a plain damage-reduction + durability model:
 * while active, every living ally standing inside the sphere takes {@link DefensiveMagicSpell#REDUCTION} less damage and the
 * damage removed is subtracted from the barrier's durability. At zero the barrier shatters into a pulse.
 *
 * Timeline (ticks): 0..ACTIVE_FROM the seal opens in front of the caster and the barrier expands | ACTIVE_FROM..EXPIRE_TICK
 * protecting | EXPIRE_TICK..+FADE_OUT fades out. If shattered, SHATTER_LIFE ticks of fragments instead.
 *
 * The damage hook lives in {@link DefensiveMagicEvents}, which reads {@link #ACTIVE}.
 * Synced data: SEED, DIR (where the seal opened, relative to the caster), DUR (durability left), SHATTER (tick the barrier
 * broke, -1 if not), EVENTS (hit list: n, x/y/z{i} unit direction of the attack from the centre, s{i} strength 0..1).
 */
public class DefensiveMagicEntity extends Entity {
    public static final int ACTIVE_FROM = 8;
    public static final int EXPIRE_TICK = ACTIVE_FROM + DefensiveMagicSpell.DURATION_TICKS;
    public static final int FADE_OUT = 12;
    public static final int SHATTER_LIFE = 18;
    /** Height of the barrier centre above the caster's feet. */
    public static final double CENTER_Y = 0.9;

    /** Live server-side barriers (read by the damage hook). */
    public static final List<DefensiveMagicEntity> ACTIVE = new CopyOnWriteArrayList<>();

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(DefensiveMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(DefensiveMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(DefensiveMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(DefensiveMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DUR = SynchedEntityData.defineId(DefensiveMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHATTER = SynchedEntityData.defineId(DefensiveMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> EVENTS = SynchedEntityData.defineId(DefensiveMagicEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // server
    private UUID ownerId;
    private float durability = DefensiveMagicSpell.DURABILITY;
    private boolean pendingShatter = false;
    private boolean shattered = false;

    // client: {dx,dy,dz,strength,bornTick}
    private int clientSeen = 0;
    public final List<float[]> clientHits = new ArrayList<>();

    public DefensiveMagicEntity(EntityType<? extends DefensiveMagicEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static DefensiveMagicEntity create(Level level, LivingEntity caster) {
        for (DefensiveMagicEntity old : ACTIVE) { // one barrier per caster
            if (!old.isRemoved() && caster.getUUID().equals(old.ownerId)) old.finish();
        }
        DefensiveMagicEntity b = new DefensiveMagicEntity(FrierenRegistry.DEFENSIVE_ENTITY, level);
        b.setPos(caster.getX(), caster.getY() + CENTER_Y, caster.getZ());
        b.ownerId = caster.getUUID();
        Vec3 look = caster.getLookAngle().normalize();
        b.entityData.set(DX, (float) look.x);
        b.entityData.set(DY, (float) look.y);
        b.entityData.set(DZ, (float) look.z);
        b.entityData.set(SEED, level.random.nextInt());
        b.entityData.set(DUR, (int) DefensiveMagicSpell.DURABILITY);
        return b;
    }

    // ------------------------------------------------------------------------------------------------ data
    @Override
    protected void defineSynchedData() {
        entityData.define(SEED, 0);
        entityData.define(DX, 0f);
        entityData.define(DY, 0f);
        entityData.define(DZ, 1f);
        entityData.define(DUR, (int) DefensiveMagicSpell.DURABILITY);
        entityData.define(SHATTER, -1);
        entityData.define(EVENTS, new CompoundTag());
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public Vec3 sealDir() {
        return new Vec3(entityData.get(DX), entityData.get(DY), entityData.get(DZ));
    }

    public float durabilityFraction() {
        return Mth.clamp(entityData.get(DUR) / DefensiveMagicSpell.DURABILITY, 0f, 1f);
    }

    /** Tick the barrier broke, or -1. */
    public int shatterTick() {
        return entityData.get(SHATTER);
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return true;
    }

    // ------------------------------------------------------------------------------------------------ rules
    /** True while the barrier can still absorb damage. */
    public boolean isProtecting() {
        return !level().isClientSide && tickCount >= ACTIVE_FROM && tickCount < EXPIRE_TICK && !shattered && !pendingShatter && durability > 0f && !isRemoved();
    }

    public Vec3 center() {
        return position();
    }

    /** Ally = the caster, anyone allied with the caster, or a friendly-fire partner. */
    public boolean isAlly(LivingEntity owner, Entity e) {
        return e == owner || e.isAlliedTo(owner) || DamageSources.isFriendlyFireBetween(owner, e);
    }

    /** Is this entity inside the sphere, and an ally of the caster? */
    public boolean protects(LivingEntity victim) {
        if (!(level() instanceof ServerLevel sl)) return false;
        Entity o = ownerId == null ? null : sl.getEntity(ownerId);
        if (!(o instanceof LivingEntity owner) || !owner.isAlive()) return false;
        Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        double r = DefensiveMagicSpell.RADIUS;
        return c.distanceToSqr(center()) <= r * r && isAlly(owner, victim);
    }

    /**
     * Applies the reduction to one incoming hit. Returns the damage that still gets through. The blocked part is taken from
     * the durability; when the durability runs out the barrier shatters (the pulse itself is fired on the next tick).
     */
    public float absorb(float amount, LivingEntity victim, DamageSource src) {
        float blocked = Math.min(amount * DefensiveMagicSpell.REDUCTION, durability);
        if (blocked <= 0f) return amount;
        durability -= blocked;
        entityData.set(DUR, Math.max(0, (int) Math.ceil(durability)));

        Vec3 from = null;
        Entity direct = src.getDirectEntity();
        Entity attacker = src.getEntity();
        if (direct != null) from = direct.position().add(0, direct.getBbHeight() * 0.5, 0);
        else if (attacker != null) from = attacker.getEyePosition();
        if (from == null) from = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        Vec3 dir = from.subtract(center());
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize();
        pushEvent(dir, Mth.clamp(blocked / 150f, 0.35f, 1f));

        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.9f + random.nextFloat() * 0.4f);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f + random.nextFloat() * 0.4f);

        if (durability <= 0.01f) pendingShatter = true;
        return amount - blocked;
    }

    private void pushEvent(Vec3 dir, float strength) {
        CompoundTag tag = entityData.get(EVENTS).copy();
        int n = tag.getInt("n");
        if (n >= 60) return; // purely visual list; cap it
        tag.putFloat("x" + n, (float) dir.x);
        tag.putFloat("y" + n, (float) dir.y);
        tag.putFloat("z" + n, (float) dir.z);
        tag.putFloat("s" + n, strength);
        tag.putInt("n", n + 1);
        entityData.set(EVENTS, tag);
    }

    // ------------------------------------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    /** Removes the barrier (server). */
    public void finish() {
        ACTIVE.remove(this);
        discard();
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        if (tickCount == 0) {
            ACTIVE.add(this);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.9f);
        }
        Entity o = ownerId == null ? null : sl.getEntity(ownerId);
        if (!(o instanceof LivingEntity owner) || !owner.isAlive()) {
            finish();
            return;
        }
        setPos(owner.getX(), owner.getY() + CENTER_Y, owner.getZ()); // follows the caster

        if (tickCount == ACTIVE_FROM) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
        }
        if (pendingShatter && !shattered) {
            shattered = true;
            pendingShatter = false;
            entityData.set(SHATTER, tickCount);
            pulse(sl, owner);
        }
        if (shattered) {
            if (tickCount > shatterTick() + SHATTER_LIFE) finish();
            return;
        }
        if (tickCount == EXPIRE_TICK) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
        }
        if (tickCount > EXPIRE_TICK + FADE_OUT) finish();
    }

    /** The defensive pulse released when the barrier breaks. */
    private void pulse(ServerLevel sl, LivingEntity owner) {
        Vec3 c = center();
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.4f, 1.3f);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.6f, 1.2f);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.8f);
        double r = DefensiveMagicSpell.PULSE_RADIUS;
        DamageSource src = FrierenRegistry.DEFENSIVE_SPELL.getDamageSource(this, owner);
        for (Entity e : sl.getEntities(this, new AABB(c, c).inflate(r + 1), t -> ZoltraakEntity.isFoe(owner, t))) {
            LivingEntity le = (LivingEntity) e;
            Vec3 mid = le.position().add(0, le.getBbHeight() * 0.5, 0);
            if (mid.distanceToSqr(c) > r * r) continue;
            float percentHp = le.getMaxHealth() * DefensiveMagicSpell.PULSE_PERCENT;
            le.invulnerableTime = 0;
            DamageSources.applyDamage(le, DefensiveMagicSpell.PULSE_FLAT, src);
            if (le.isAlive()) {
                le.invulnerableTime = 0;
                SpellDamage.percent(le, percentHp, owner, src);
            }
            // a defensive shove: pushes enemies clear of the caster
            Vec3 away = mid.subtract(c);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : new Vec3(away.x, 0, away.z).normalize().scale(0.7);
            le.setDeltaMovement(le.getDeltaMovement().add(away.x, 0.25, away.z));
            le.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ client
    private void clientTick() {
        CompoundTag tag = entityData.get(EVENTS);
        int n = tag.getInt("n");
        for (int i = clientSeen; i < n; i++) {
            clientHits.add(new float[]{tag.getFloat("x" + i), tag.getFloat("y" + i), tag.getFloat("z" + i), tag.getFloat("s" + i), tickCount});
        }
        clientSeen = n;
    }
}
