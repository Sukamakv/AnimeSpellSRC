package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.frieren.FrierenRegistry;
import com.example.jjkspells.frieren.spell.HeightOfMagicSpell;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controller of The Height of Magic. Its position is the STAR: it floats {@link #STAR_H} blocks above the caster until the
 * release, then stays put as the beam's origin. Like Zoltraak the whole show is deterministic from a few synced values
 * (RELEASE tick, target point TX/TY/TZ, SEED), so the client animates everything from tickCount alone.
 *
 * Timeline (ticks since the cast began): 0..CAST preparation (seal on the ground, star growing) | RELEASE the beam leaves the star at
 * BEAM_SPEED blocks/tick | impact when the head arrives: the shockwave front expands at WAVE_SPEED blocks/tick to the explosion
 * radius | the aftermath (columns, embers) fades over TAIL ticks.
 *
 * Damage (all enemies, once each): the beam strikes everything along its width; the shockwave strikes everything it reaches.
 */
public class HeightOfMagicEntity extends Entity {
    public static final int CAST = HeightOfMagicSpell.CAST_TICKS;
    public static final float STAR_H = 9f;
    public static final float BEAM_SPEED = 12f;
    public static final int HOLD = 14;
    public static final int FADE = 14;
    public static final float WAVE_SPEED = 2f;
    public static final int WAVE_TICKS = (int) Math.ceil(HeightOfMagicSpell.EXPLOSION_RADIUS / WAVE_SPEED);
    public static final int TAIL = 45;
    /** Half-width (blocks) of the struck corridor round the beam axis. */
    public static final double BEAM_REACH = 2.5;

    public static final List<HeightOfMagicEntity> ACTIVE = new CopyOnWriteArrayList<>();

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(HeightOfMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HeightOfMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RELEASE = SynchedEntityData.defineId(HeightOfMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CANCEL = SynchedEntityData.defineId(HeightOfMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TX = SynchedEntityData.defineId(HeightOfMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TY = SynchedEntityData.defineId(HeightOfMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TZ = SynchedEntityData.defineId(HeightOfMagicEntity.class, EntityDataSerializers.FLOAT);

    // server
    private UUID ownerId;
    private final Set<Integer> hitIds = new HashSet<>();
    private boolean impacted = false;

    // client
    private boolean clientImpactFx = false;

    public HeightOfMagicEntity(EntityType<? extends HeightOfMagicEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ------------------------------------------------------------------------------------------------ creation / control
    public static HeightOfMagicEntity create(Level level, LivingEntity caster) {
        for (HeightOfMagicEntity old : ACTIVE) {
            if (!old.isRemoved() && caster.getUUID().equals(old.ownerId)) old.finish();
        }
        HeightOfMagicEntity e = new HeightOfMagicEntity(FrierenRegistry.HEIGHT_ENTITY, level);
        e.setPos(caster.getX(), caster.getY() + STAR_H, caster.getZ());
        e.ownerId = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.entityData.set(SEED, level.random.nextInt());
        return e;
    }

    private static HeightOfMagicEntity find(LivingEntity caster) {
        for (HeightOfMagicEntity e : ACTIVE) {
            if (!e.isRemoved() && caster.getUUID().equals(e.ownerId)) return e;
        }
        return null;
    }

    /** The cast finished: pick the target point (crosshair: nearest enemy, else block, else 45 blocks out) and fire. */
    public static void release(Level level, LivingEntity caster) {
        HeightOfMagicEntity c = find(caster);
        if (c == null) return;
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        double range = HeightOfMagicSpell.RANGE;
        Vec3 far = eye.add(look.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double maxD = bh.getType() == HitResult.Type.MISS ? range : bh.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(maxD));
        Vec3 target = end;
        double best = maxD;
        for (Entity e : level.getEntities(caster, new AABB(eye, end).inflate(1.0), t -> ZoltraakEntity.isFoe(caster, t))) {
            Optional<Vec3> p = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (p.isPresent()) {
                double d = p.get().distanceTo(eye);
                if (d < best) {
                    best = d;
                    target = e.getBoundingBox().getCenter();
                }
            }
        }
        c.doRelease(target);
    }

    public static void cancelFor(LivingEntity caster) {
        HeightOfMagicEntity c = find(caster);
        if (c != null) c.cancel();
    }

    private void doRelease(Vec3 target) {
        if (release() >= 0 || cancelTick() >= 0) return;
        entityData.set(TX, (float) target.x);
        entityData.set(TY, (float) target.y);
        entityData.set(TZ, (float) target.z);
        entityData.set(RELEASE, tickCount);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.6f);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 1.8f);
    }

    private void cancel() {
        if (release() >= 0 || cancelTick() >= 0) return;
        entityData.set(CANCEL, tickCount);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 1.4f);
    }

    public void finish() {
        ACTIVE.remove(this);
        discard();
    }

    // ------------------------------------------------------------------------------------------------ data
    @Override
    protected void defineSynchedData() {
        entityData.define(SEED, 0);
        entityData.define(OWNER_ID, -1);
        entityData.define(RELEASE, -1);
        entityData.define(CANCEL, -1);
        entityData.define(TX, 0f);
        entityData.define(TY, 0f);
        entityData.define(TZ, 0f);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public int ownerEntityId() {
        return entityData.get(OWNER_ID);
    }

    /** Tick the beam was released, or -1. */
    public int release() {
        return entityData.get(RELEASE);
    }

    /** Tick the cast was interrupted, or -1. */
    public int cancelTick() {
        return entityData.get(CANCEL);
    }

    public Vec3 target() {
        return new Vec3(entityData.get(TX), entityData.get(TY), entityData.get(TZ));
    }

    /** Distance star -> target. */
    public float beamLength() {
        return (float) target().distanceTo(position());
    }

    public int impactTick() {
        return release() + (int) Math.ceil(beamLength() / BEAM_SPEED);
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

    // ------------------------------------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        int age = tickCount;
        if (age == 0) {
            ACTIVE.add(this);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.6f);
        }
        Entity o = ownerId == null ? null : sl.getEntity(ownerId);
        LivingEntity owner = o instanceof LivingEntity le && le.isAlive() ? le : null;
        int rel = release();

        if (rel < 0) { // still gathering
            if (cancelTick() >= 0) {
                if (age > cancelTick() + 14) finish();
                return;
            }
            if (owner == null) {
                cancel();
                return;
            }
            setPos(owner.getX(), owner.getY() + STAR_H, owner.getZ()); // the star and the seal follow the caster
            if (age > 0 && age % 20 == 0) { // a rising hum
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.6f + age / 100f);
            }
            if (age > CAST + 60) finish(); // never released
            return;
        }

        if (owner == null) { // caster gone: nothing left to credit the damage to
            finish();
            return;
        }
        Vec3 s = position();
        Vec3 t = target();
        float len = (float) t.distanceTo(s);
        Vec3 dir = len < 1.0E-4f ? new Vec3(0, -1, 0) : t.subtract(s).scale(1.0 / len);
        int k = age - rel;

        // the beam: sweeps its corridor, striking every enemy it passes
        float prev = Math.min(len, BEAM_SPEED * (k - 1));
        float cur = Math.min(len, BEAM_SPEED * k);
        if (cur > prev) beamSweep(sl, owner, s, dir, prev, cur);

        int impactTick = rel + (int) Math.ceil(len / BEAM_SPEED);
        if (!impacted && age >= impactTick) {
            impacted = true;
            sl.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0f, 0.5f);
            sl.playSound(null, t.x, t.y, t.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 1.3f);
            sl.playSound(null, t.x, t.y, t.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 5.0f, 0.7f);
        }
        if (impacted) {
            // the shockwave front: everything it reaches takes the hit (once)
            double r = Math.min(HeightOfMagicSpell.EXPLOSION_RADIUS, WAVE_SPEED * (age - impactTick + 1));
            for (Entity e : sl.getEntities(this, new AABB(t, t).inflate(r + 2), x -> ZoltraakEntity.isFoe(owner, x))) {
                if (hitIds.contains(e.getId())) continue;
                double d = e.getBoundingBox().getCenter().distanceTo(t);
                if (d > r) continue;
                hitIds.add(e.getId());
                float mult = Mth.lerp((float) (d / HeightOfMagicSpell.EXPLOSION_RADIUS), 1f, HeightOfMagicSpell.EDGE_DAMAGE_MULT);
                strike((LivingEntity) e, owner, mult);
            }
            if (age > impactTick + WAVE_TICKS + TAIL) finish();
        } else if (age > rel + 200) {
            finish();
        }
    }

    private record Hit(LivingEntity target, double dist) {
    }

    private void beamSweep(ServerLevel sl, LivingEntity owner, Vec3 s, Vec3 dir, float prev, float cur) {
        Vec3 a = s.add(dir.scale(prev));
        Vec3 b = s.add(dir.scale(cur));
        List<Hit> hits = new ArrayList<>();
        for (Entity e : sl.getEntities(this, new AABB(a, b).inflate(BEAM_REACH + 1), x -> ZoltraakEntity.isFoe(owner, x))) {
            if (hitIds.contains(e.getId())) continue;
            AABB bb = e.getBoundingBox().inflate(BEAM_REACH);
            Optional<Vec3> p = bb.clip(a, b);
            if (p.isPresent()) hits.add(new Hit((LivingEntity) e, prev + p.get().distanceTo(a)));
            else if (bb.contains(a)) hits.add(new Hit((LivingEntity) e, prev));
        }
        hits.sort(Comparator.comparingDouble(Hit::dist));
        for (Hit h : hits) {
            if (hitIds.add(h.target().getId())) strike(h.target(), owner, 1f);
        }
    }

    /** The ultimate damage: flat part unscaled, max-HP part through SpellDamage (max_hp_damage / max_hp_resist). */
    private void strike(LivingEntity target, LivingEntity owner, float mult) {
        float percentHp = target.getMaxHealth() * HeightOfMagicSpell.DAMAGE_PERCENT * mult;
        DamageSource src = FrierenRegistry.HEIGHT_SPELL.getDamageSource(this, owner);
        Vec3 motion = target.getDeltaMovement();
        target.invulnerableTime = 0;
        DamageSources.applyDamage(target, HeightOfMagicSpell.DAMAGE_FLAT * mult, src);
        if (target.isAlive()) {
            target.invulnerableTime = 0;
            SpellDamage.percent(target, percentHp, owner, src);
        }
        target.setDeltaMovement(motion); // no knockback: the damage is the point
        target.hurtMarked = true;
    }

    // ------------------------------------------------------------------------------------------------ client
    private void clientTick() {
        int rel = release();
        int age = tickCount;
        Vec3 s = position();
        if (rel < 0 && cancelTick() < 0 && age > 4) {
            // motes lifting off the seal
            for (int i = 0; i < 2; i++) {
                double ang = random.nextDouble() * Math.PI * 2, rad = 1.5 + random.nextDouble() * 6.0;
                level().addParticle(ParticleTypes.END_ROD, s.x + Math.cos(ang) * rad, s.y - STAR_H + 0.2, s.z + Math.sin(ang) * rad, 0, 0.05 + random.nextDouble() * 0.08, 0);
            }
        }
        if (rel >= 0 && !clientImpactFx && age >= impactTick()) {
            clientImpactFx = true;
            Vec3 t = target();
            for (int i = 0; i < 70; i++) {
                Vec3 v = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.6, random.nextGaussian()).normalize().scale(0.4 + random.nextDouble() * 1.4);
                level().addParticle(ParticleTypes.END_ROD, t.x, t.y, t.z, v.x, v.y, v.z);
            }
        }
    }
}
