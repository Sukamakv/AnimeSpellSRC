package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.frieren.FrierenRegistry;
import com.example.jjkspells.frieren.spell.AncientReleaseSpell;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Controller of Zoltraak: Ancient Release. Its position follows the caster's FEET until the release (the seal lies on the ground
 * beneath them; the sphere and backdrop circle are placed relative to it from the synced look direction and eye height).
 *
 * Timeline (ticks since the cast began): 0..CAST charge (caster rooted) | RELEASE beam head leaves the sphere point at BEAM_SPEED
 * blocks/tick and then the beam holds BEAM_TICKS | at the end of the beam the final explosion front expands to the blast radius,
 * the pillar of light rises, everything fades over AFTER ticks.
 *
 * Per-target damage rules (server): the first contact with the beam deals the initial hit; after that a target takes beam-tick
 * damage at most once per INTERVAL_TICKS and at most MAX_TICKS times in total. The explosion hits each target once.
 */
public class AncientReleaseEntity extends Entity {
    public static final int CAST = AncientReleaseSpell.CAST_TICKS;
    public static final int BEAM_TICKS = AncientReleaseSpell.BEAM_TICKS;
    public static final float BEAM_SPEED = 25f;
    public static final float BLAST_SPEED = 3f;
    public static final int AFTER = 70;
    /** Distance in front of the caster's eyes where the sphere forms and the beam starts. */
    public static final float ORIGIN_DIST = 6f;
    public static final double BEAM_RADIUS = AncientReleaseSpell.BEAM_WIDTH / 2.0;
    public static final int MAX_TICKS = BEAM_TICKS / AncientReleaseSpell.INTERVAL_TICKS;

    public static final UUID ROOT_UUID = UUID.fromString("7a1f2b6e-3c4d-4e5f-8a9b-0c1d2e3f4a5b");
    public static final List<AncientReleaseEntity> ACTIVE = new CopyOnWriteArrayList<>();

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> EYE_H = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> RELEASE = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CANCEL = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> OX = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OY = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OZ = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LEN = SynchedEntityData.defineId(AncientReleaseEntity.class, EntityDataSerializers.FLOAT);

    // server
    private UUID ownerId;
    /** entity id -> {tick of last hit, number of beam-tick hits so far} */
    private final Map<Integer, int[]> beamState = new HashMap<>();
    private final Set<Integer> blastHit = new HashSet<>();
    private boolean blasted = false;

    // client: smoothed look direction
    public Vec3 lookPrev = new Vec3(0, 0, 1), lookCur = new Vec3(0, 0, 1);
    private boolean lookInit = false;

    public AncientReleaseEntity(EntityType<? extends AncientReleaseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ------------------------------------------------------------------------------------------------ root (caster stands still)
    private static void root(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null && a.getModifier(ROOT_UUID) == null) {
            a.addTransientModifier(new AttributeModifier(ROOT_UUID, "jjk_frieren_ancient_release_root", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    public static void unroot(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null && a.getModifier(ROOT_UUID) != null) a.removeModifier(ROOT_UUID);
    }

    /** True while a live controller still wants this caster rooted (used by the safety net in {@link AncientReleaseEvents}). */
    public static boolean isRooting(LivingEntity caster) {
        for (AncientReleaseEntity e : ACTIVE) {
            if (!e.isRemoved() && caster.getUUID().equals(e.ownerId) && e.rootWanted) return true;
        }
        return false;
    }

    private boolean rootWanted = true;

    // ------------------------------------------------------------------------------------------------ creation / control
    public static AncientReleaseEntity create(Level level, LivingEntity caster) {
        for (AncientReleaseEntity old : ACTIVE) {
            if (!old.isRemoved() && caster.getUUID().equals(old.ownerId)) old.finish();
        }
        AncientReleaseEntity e = new AncientReleaseEntity(FrierenRegistry.ANCIENT_ENTITY, level);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.ownerId = caster.getUUID();
        Vec3 look = caster.getLookAngle().normalize();
        e.entityData.set(OWNER_ID, caster.getId());
        e.entityData.set(SEED, level.random.nextInt());
        e.entityData.set(EYE_H, caster.getEyeHeight());
        e.entityData.set(DX, (float) look.x);
        e.entityData.set(DY, (float) look.y);
        e.entityData.set(DZ, (float) look.z);
        root(caster);
        ACTIVE.add(e); // registered now so the root safety net never sees a rooted caster without a controller
        return e;
    }

    private static AncientReleaseEntity find(LivingEntity caster) {
        for (AncientReleaseEntity e : ACTIVE) {
            if (!e.isRemoved() && caster.getUUID().equals(e.ownerId)) return e;
        }
        return null;
    }

    /** The charge finished: lock the line the caster is aiming along and fire. */
    public static void release(Level level, LivingEntity caster) {
        AncientReleaseEntity c = find(caster);
        if (c == null) return;
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 s = eye.add(look.scale(ORIGIN_DIST));
        BlockHitResult toS = level.clip(new ClipContext(eye, s, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (toS.getType() != HitResult.Type.MISS) s = toS.getLocation().subtract(look.scale(0.2));
        double maxLen = Math.max(1.0, AncientReleaseSpell.RANGE - eye.distanceTo(s)); // total reach stays within RANGE of the caster
        BlockHitResult bh = level.clip(new ClipContext(s, s.add(look.scale(maxLen)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        float len = bh.getType() == HitResult.Type.MISS ? (float) maxLen : (float) bh.getLocation().distanceTo(s);
        c.doRelease(s, look, len);
    }

    public static void cancelFor(LivingEntity caster) {
        AncientReleaseEntity c = find(caster);
        if (c != null) c.cancel(caster);
    }

    private void doRelease(Vec3 origin, Vec3 dir, float len) {
        if (release() >= 0 || cancelTick() >= 0) return;
        entityData.set(DX, (float) dir.x);
        entityData.set(DY, (float) dir.y);
        entityData.set(DZ, (float) dir.z);
        entityData.set(OX, (float) origin.x);
        entityData.set(OY, (float) origin.y);
        entityData.set(OZ, (float) origin.z);
        entityData.set(LEN, len);
        entityData.set(RELEASE, tickCount);
        level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0f, 0.5f);
        level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 1.6f);
        level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 3.0f, 0.7f);
        if (level() instanceof net.minecraft.server.level.ServerLevel fsl) {
            Vec3 stop = origin.add(dir.scale(len));
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(fsl, origin, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 5.0f);
            com.example.jjkspells.vfx.sprite.SpriteFx.beam(fsl, origin, stop, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (BEAM_RADIUS * 2.0), BEAM_TICKS);
            // telegraph: the blast zone at the end of the beam, shown until the explosion
        }
    }

    private void cancel(LivingEntity caster) {
        if (release() >= 0 || cancelTick() >= 0) return;
        entityData.set(CANCEL, tickCount);
        rootWanted = false;
        if (caster != null) unroot(caster);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 1.4f);
    }

    public void finish() {
        rootWanted = false;
        if (level() instanceof ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity le) unroot(le);
        ACTIVE.remove(this);
        discard();
    }

    // ------------------------------------------------------------------------------------------------ data
    @Override
    protected void defineSynchedData() {
        entityData.define(SEED, 0);
        entityData.define(OWNER_ID, -1);
        entityData.define(EYE_H, 1.62f);
        entityData.define(DX, 0f);
        entityData.define(DY, 0f);
        entityData.define(DZ, 1f);
        entityData.define(RELEASE, -1);
        entityData.define(CANCEL, -1);
        entityData.define(OX, 0f);
        entityData.define(OY, 0f);
        entityData.define(OZ, 0f);
        entityData.define(LEN, 0f);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public int ownerEntityId() {
        return entityData.get(OWNER_ID);
    }

    public float eyeHeight() {
        return entityData.get(EYE_H);
    }

    /** Caster's look direction (locked at the release). */
    public Vec3 look() {
        return new Vec3(entityData.get(DX), entityData.get(DY), entityData.get(DZ));
    }

    public int release() {
        return entityData.get(RELEASE);
    }

    public int cancelTick() {
        return entityData.get(CANCEL);
    }

    /** World position where the beam starts (the point the sphere compressed into). */
    public Vec3 origin() {
        return new Vec3(entityData.get(OX), entityData.get(OY), entityData.get(OZ));
    }

    public float beamLength() {
        return entityData.get(LEN);
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
        if (level().isClientSide) {
            Vec3 l = look();
            if (!lookInit) {
                lookInit = true;
                lookPrev = l;
            } else {
                lookPrev = lookCur;
            }
            lookCur = l;
        } else {
            serverTick();
        }
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        int age = tickCount;
        Entity o = ownerId == null ? null : sl.getEntity(ownerId);
        LivingEntity owner = o instanceof LivingEntity le && le.isAlive() ? le : null;
        int rel = release();

        if (rel < 0) { // charging
            if (cancelTick() >= 0) {
                if (age > cancelTick() + 14) finish();
                return;
            }
            if (owner == null) {
                cancel(null);
                return;
            }
            setPos(owner.getX(), owner.getY(), owner.getZ());
            Vec3 look = owner.getLookAngle().normalize();
            entityData.set(DX, (float) look.x);
            entityData.set(DY, (float) look.y);
            entityData.set(DZ, (float) look.z);
            if (age == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.5f, 0.5f);
            if (age > 0 && age % 20 == 0) { // a rising hum
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.5f, 0.5f + age / 120f);
            }
            if (age > CAST + 60) finish(); // never released
            return;
        }

        if (owner == null) { // caster gone
            finish();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        int k = age - rel;
        Vec3 origin = origin();
        Vec3 dir = look();
        float len = beamLength();

        if (k >= 0 && k <= BEAM_TICKS) {
            if (k > 0 && k % 10 == 0) {
                sl.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 3.0f, 0.6f);
            }
            beamTick(sl, owner, origin, dir, len, k, age);
        }
        if (k == BEAM_TICKS) {
            rootWanted = false;
            unroot(owner);
        }
        Vec3 end = origin.add(dir.scale(len)).subtract(dir.scale(0.5));
        if (k >= BEAM_TICKS) {
            if (!blasted) {
                blasted = true;
                sl.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 7.0f, 0.45f);
                sl.playSound(null, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 1.1f);
                sl.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 6.0f, 0.6f);
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, end, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 7.0f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, end, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 5.0f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, end, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, AncientReleaseSpell.EXPLOSION_RADIUS * 2f / 6f);
            }
            blastTick(sl, owner, end, k - BEAM_TICKS);
        }
        if (k > BEAM_TICKS + AFTER) finish();
    }

    /** One tick of the beam: initial contact, then interval-limited damage, for everyone inside the 8-block corridor. */
    private void beamTick(ServerLevel sl, LivingEntity owner, Vec3 origin, Vec3 dir, float len, int k, int age) {
        float head = Math.min(len, BEAM_SPEED * k); // the head sweeps out to the end in a few ticks
        if (head <= 0f) return;
        Vec3 a = origin;
        Vec3 b = origin.add(dir.scale(head));
        for (Entity e : sl.getEntities(this, new AABB(a, b).inflate(BEAM_RADIUS + 1), x -> ZoltraakEntity.isFoe(owner, x))) {
            AABB bb = e.getBoundingBox().inflate(BEAM_RADIUS);
            if (!(bb.clip(a, b).isPresent() || bb.contains(a))) continue;
            LivingEntity target = (LivingEntity) e;
            int[] st = beamState.get(e.getId());
            if (st == null) {
                beamState.put(e.getId(), new int[]{age, 0});
                strike(target, owner, AncientReleaseSpell.INITIAL_FLAT, AncientReleaseSpell.INITIAL_PERCENT);
            } else if (st[1] < MAX_TICKS && age - st[0] >= AncientReleaseSpell.INTERVAL_TICKS) { // respect the damage interval and the per-target cap
                st[0] = age;
                st[1]++;
                strike(target, owner, AncientReleaseSpell.TICK_FLAT, AncientReleaseSpell.TICK_PERCENT);
            }
        }
    }

    /** The final explosion: a front expanding from the end of the beam, striking each target once. */
    private void blastTick(ServerLevel sl, LivingEntity owner, Vec3 center, int kb) {
        double r = Math.min(AncientReleaseSpell.EXPLOSION_RADIUS, BLAST_SPEED * (kb + 1));
        for (Entity e : sl.getEntities(this, new AABB(center, center).inflate(r + 2), x -> ZoltraakEntity.isFoe(owner, x))) {
            if (blastHit.contains(e.getId())) continue;
            if (e.getBoundingBox().getCenter().distanceTo(center) > r) continue;
            blastHit.add(e.getId());
            strike((LivingEntity) e, owner, AncientReleaseSpell.BLAST_FLAT, AncientReleaseSpell.BLAST_PERCENT);
        }
    }

    /** Flat part unscaled; max-HP part through SpellDamage (max_hp_damage / max_hp_resist). No knockback. */
    private void strike(LivingEntity target, LivingEntity owner, float flat, float pct) {
        float percentHp = target.getMaxHealth() * pct;
        DamageSource src = FrierenRegistry.ANCIENT_SPELL.getDamageSource(this, owner);
        Vec3 motion = target.getDeltaMovement();
        target.invulnerableTime = 0;
        DamageSources.applyDamage(target, flat, src);
        if (target.isAlive()) {
            target.invulnerableTime = 0;
            SpellDamage.percent(target, percentHp, owner, src);
        }
        target.setDeltaMovement(motion);
        target.hurtMarked = true;
    }
}
