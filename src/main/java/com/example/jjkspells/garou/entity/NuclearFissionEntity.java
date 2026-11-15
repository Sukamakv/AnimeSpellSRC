package com.example.jjkspells.garou.entity;

import com.example.jjkspells.garou.registry.NfEntities;
import com.example.jjkspells.spells.SpellDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NUCLEAR FISSION (Garou, cosmic form). One invisible controller entity runs the whole sequence:
 * <pre>
 *   0.0-1.0 s   the arm is pulled back, a white cosmic sphere forms around the fist, rings rotate around the hand
 *   1.0 s       the caster launches forward
 *   dash        a streak of cosmic energy; the fist drives into the first enemy (or the end of the dash)
 *   impact      punch damage (once per target) -> white flash, shockwave, then the nuclear explosion
 *   impact+0.1  explosion damage (once per target) in the whole radius
 *   impact+5.0  a last shockwave pushes enemies away
 * </pre>
 * Everything the client draws comes from the age, the launch tick, the impact tick and the impact point.
 */
public class NuclearFissionEntity extends Entity {
    /** Casters that are in the middle of the sequence (they cannot cast it again). */
    public static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(NuclearFissionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LAUNCH = SynchedEntityData.defineId(NuclearFissionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_IMPACT = SynchedEntityData.defineId(NuclearFissionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_IX = SynchedEntityData.defineId(NuclearFissionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_IY = SynchedEntityData.defineId(NuclearFissionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_IZ = SynchedEntityData.defineId(NuclearFissionEntity.class, EntityDataSerializers.FLOAT);

    private static final double DASH_SPEED = 2.6;
    private static final double DASH_REACH = 18.0;
    private static final double PUNCH_RADIUS = 3.4;

    private UUID ownerId;
    private boolean finished, launched, impacted;
    private Vec3 dashDir = Vec3.ZERO, dashStart = Vec3.ZERO, lastPos = Vec3.ZERO;
    private double dashDist;
    private int dashTicks, stuck;
    private int impactAge = -1;
    private Vec3 center = Vec3.ZERO;
    private LivingEntity primary;

    /** Each damage event is registered only once per target. */
    private final Set<UUID> punchHit = new HashSet<>();
    private final Set<UUID> blastHit = new HashSet<>();
    private final Set<UUID> pushed = new HashSet<>();

    public NuclearFissionEntity(EntityType<? extends NuclearFissionEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static NuclearFissionEntity create(Level level, LivingEntity caster) {
        NuclearFissionEntity e = new NuclearFissionEntity(NfEntities.NUCLEAR_FISSION.get(), level);
        e.ownerId = caster.getUUID();
        e.entityData.set(DATA_OWNER, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    // ------------------------------------------------------------------ data the client reads

    public Entity getOwnerEntity() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    public int launchAge() {
        return entityData.get(DATA_LAUNCH);
    }

    public int impactAge() {
        return entityData.get(DATA_IMPACT);
    }

    public Vec3 impactPos() {
        return new Vec3(entityData.get(DATA_IX), entityData.get(DATA_IY), entityData.get(DATA_IZ));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_OWNER, -1);
        entityData.define(DATA_LAUNCH, -1);
        entityData.define(DATA_IMPACT, -1);
        entityData.define(DATA_IX, 0f);
        entityData.define(DATA_IY, 0f);
        entityData.define(DATA_IZ, 0f);
    }

    // ------------------------------------------------------------------ the sequence (server)

    private LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.garou.client.GarouFx.tick(this));
            return;
        }
        int age = tickCount - 1;
        LivingEntity o = owner();
        if (o == null) {
            finish();
            discard();
            return;
        }
        try {
            run(age, o);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("jjk_spells").error("nuclear_fission failed", t);
            finish();
            discard();
            return;
        }
        if (impacted && age >= impactAge + GarouTimeline.AFTER) {
            finish();
            discard();
        }
    }

    private void run(int age, LivingEntity o) {
        if (age == 0) {
            ACTIVE.add(ownerId);
            sound(o.position(), SoundEvents.BEACON_POWER_SELECT, 2.0f, 0.55f);
            sound(o.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1.2f, 0.5f);
        }
        if (!launched) {
            hold(o);
            if (age == 8 || age == 14) {
                sound(o.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0f, 1.2f + age * 0.02f);
            }
            if (age == 16) {
                sound(o.position(), SoundEvents.BEACON_AMBIENT, 2.5f, 1.8f);
            }
            if (age >= GarouTimeline.WIND) {
                launch(age, o);
            }
        } else if (!impacted) {
            dash(age, o);
        } else {
            afterImpact(age - impactAge, o);
        }
    }

    /** The caster stands firm while the energy gathers. */
    private void hold(LivingEntity o) {
        o.setSprinting(false);
        Vec3 m = o.getDeltaMovement();
        o.setDeltaMovement(0, Math.min(m.y, 0), 0);
        o.hurtMarked = true;
        o.fallDistance = 0;
    }

    private void launch(int age, LivingEntity o) {
        launched = true;
        entityData.set(DATA_LAUNCH, age);
        Vec3 eye = o.getEyePosition();
        Vec3 look = o.getLookAngle().normalize();
        dashDir = look;
        // how far is the first block, and the first enemy in front of the fist
        Vec3 far = eye.add(look.scale(DASH_REACH));
        BlockHitResult bh = level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o));
        double blockDist = bh.getType() == HitResult.Type.MISS ? DASH_REACH : eye.distanceTo(bh.getLocation());
        double best = blockDist;
        primary = null;
        AABB box = o.getBoundingBox().expandTowards(look.scale(blockDist)).inflate(1.4);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, x -> hittable(o, x))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.45).clip(eye, eye.add(look.scale(blockDist)));
            if (hit.isPresent()) {
                double d = eye.distanceTo(hit.get());
                if (d < best) {
                    best = d;
                    primary = e;
                }
            }
        }
        dashDist = Math.max(0.0, best - 1.7);
        dashStart = o.position();
        lastPos = dashStart;
        dashTicks = 0;
        stuck = 0;
        o.setNoGravity(true);
        sound(o.position(), SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 1.25f);
        sound(o.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0f, 0.6f);
        if (dashDist < 0.4) {
            impact(age, o);
        }
    }

    private void dash(int age, LivingEntity o) {
        dashTicks++;
        double traveled = o.position().distanceTo(dashStart);
        double remaining = dashDist - traveled;
        if (o.position().distanceToSqr(lastPos) < 0.0004) {
            stuck++;
        } else {
            stuck = 0;
        }
        lastPos = o.position();
        if (remaining <= 0.35 || dashTicks > GarouTimeline.MAX_DASH || stuck >= 2) {
            impact(age, o);
            return;
        }
        double step = Math.min(DASH_SPEED, remaining);
        o.setDeltaMovement(dashDir.scale(step));
        o.hurtMarked = true;
        o.fallDistance = 0;
        o.setSprinting(false);
    }

    private void impact(int age, LivingEntity o) {
        impacted = true;
        impactAge = age;
        o.setDeltaMovement(Vec3.ZERO);
        o.hurtMarked = true;
        o.setNoGravity(false);
        o.fallDistance = 0;
        Vec3 fist = o.getEyePosition().add(dashDir.scale(1.7)).add(0, -0.25, 0);
        // the blast stands on the ground below the fist
        BlockHitResult gh = level().clip(new ClipContext(fist.add(0, 1.0, 0), fist.add(0, -26.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o));
        center = gh.getType() == HitResult.Type.MISS ? fist : new Vec3(fist.x, gh.getLocation().y, fist.z);
        entityData.set(DATA_IMPACT, age);
        entityData.set(DATA_IX, (float) center.x);
        entityData.set(DATA_IY, (float) center.y);
        entityData.set(DATA_IZ, (float) center.z);

        // the direct punch: everything in front of the fist, once each
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, new AABB(fist, fist).inflate(PUNCH_RADIUS), x -> hittable(o, x));
        if (primary != null && primary.isAlive() && !near.contains(primary)) {
            near.add(primary);
        }
        DamageSource src = punchSource(o);
        for (LivingEntity e : near) {
            if (e != primary && e.getBoundingBox().distanceToSqr(fist) > PUNCH_RADIUS * PUNCH_RADIUS) {
                continue;
            }
            if (punchHit.add(e.getUUID())) {
                deal(o, e, GarouTimeline.PUNCH_FLAT, GarouTimeline.PUNCH_PERCENT, src);
            }
        }
        sound(fist, SoundEvents.GENERIC_EXPLODE, 6.0f, 0.55f);
        sound(fist, SoundEvents.ANVIL_LAND, 4.0f, 0.5f);
        sound(fist, SoundEvents.WARDEN_SONIC_BOOM, 6.0f, 0.7f);
    }

    private void afterImpact(int ti, LivingEntity o) {
        if (ti == 0) {
            sound(center, SoundEvents.DRAGON_FIREBALL_EXPLODE, 10.0f, 0.5f);
        }
        if (ti == GarouTimeline.BLAST_DELAY) {
            sound(center, SoundEvents.GENERIC_EXPLODE, 16.0f, 0.4f);
            sound(center, SoundEvents.GENERIC_EXPLODE, 16.0f, 0.65f);
            sound(center, SoundEvents.LIGHTNING_BOLT_THUNDER, 16.0f, 0.5f);
            blast(o);
        }
        if (ti == 6) {
            sound(center, SoundEvents.LIGHTNING_BOLT_THUNDER, 14.0f, 0.35f);
            sound(center, SoundEvents.WITHER_BREAK_BLOCK, 8.0f, 0.5f);
        }
        // the small explosions around the main blast (the client draws them at the same points)
        for (int i = 0; i < GarouTimeline.SAT_COUNT; i++) {
            if (ti == GarouTimeline.satTick(i)) {
                Vec3 p = satPoint(level(), center, i, getId());
                sound(p, SoundEvents.GENERIC_EXPLODE, 5.0f, 0.65f + 0.4f * GarouTimeline.hash(i, 3));
            }
        }
        if (ti == 60) {
            sound(center, SoundEvents.FIRE_EXTINGUISH, 6.0f, 0.5f);
        }
        if (ti == GarouTimeline.FINAL_SHOCK) {
            sound(center, SoundEvents.WARDEN_SONIC_BOOM, 12.0f, 0.5f);
            sound(center, SoundEvents.LIGHTNING_BOLT_THUNDER, 12.0f, 0.7f);
            finalShockwave(o);
        }
        if (ti > GarouTimeline.FINAL_SHOCK && ti % 14 == 0) {
            sound(center, SoundEvents.FIRE_AMBIENT, 5.0f, 0.5f);
        }
        o.fallDistance = 0;
    }

    /** The nuclear explosion: once per target, in the whole radius. */
    private void blast(LivingEntity o) {
        double r = GarouTimeline.RADIUS;
        DamageSource src = o.damageSources().explosion(this, o);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r + 3.0), x -> hittable(o, x))) {
            if (e.getBoundingBox().distanceToSqr(center) > r * r) {
                continue;
            }
            if (blastHit.add(e.getUUID())) {
                deal(o, e, GarouTimeline.BLAST_FLAT, GarouTimeline.BLAST_PERCENT, src);
            }
        }
    }

    /** The last shockwave only pushes. */
    private void finalShockwave(LivingEntity o) {
        double r = GarouTimeline.SHOCK_RADIUS;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r + 3.0), x -> hittable(o, x))) {
            double d = Math.sqrt(e.getBoundingBox().distanceToSqr(center));
            if (d > r || !pushed.add(e.getUUID())) {
                continue;
            }
            Vec3 away = e.position().subtract(center);
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1.0E-4) {
                double a = level().random.nextDouble() * Math.PI * 2;
                flat = new Vec3(Math.cos(a), 0, Math.sin(a));
            }
            double power = 3.4 * (1.0 - 0.55 * d / r);
            Vec3 v = flat.normalize().scale(power);
            e.setDeltaMovement(v.x, 0.75, v.z);
            e.hurtMarked = true;
        }
    }

    /** Flat damage + percent of the target's maximum health. */
    private void deal(LivingEntity o, LivingEntity t, double flat, double percent, DamageSource src) {
        if (!t.isAlive()) {
            return;
        }
        t.invulnerableTime = 0;
        if (flat > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, (float) flat, src);
        }
        if (t.isAlive()) {
            SpellDamage.percent(t, (float) (t.getMaxHealth() * percent / 100.0), o, src);   // respects Max Health Damage / Resistance
        }
        if (t.isAlive()) {
            SpellDamage.irradiate(t, 120);
        }
    }

    private static DamageSource punchSource(LivingEntity o) {
        return o instanceof Player p ? o.damageSources().playerAttack(p) : o.damageSources().mobAttack(o);
    }

    /** Anything the caster may hurt: not the caster, not allies, not the caster's companions. */
    private static boolean hittable(LivingEntity o, LivingEntity e) {
        if (e == o || !e.isAlive() || e.isSpectator() || e.isAlliedTo(o)) {
            return false;
        }
        if (e instanceof OwnableEntity oe && oe.getOwnerUUID() != null && oe.getOwnerUUID().equals(o.getUUID())) {
            return false;
        }
        return true;
    }

    /** Place of the i-th small explosion (same on both sides). */
    public static Vec3 satPoint(Level level, Vec3 center, int i, int seed) {
        Vec3 off = GarouTimeline.satOffset(i, seed);
        double x = center.x + off.x, z = center.z + off.z;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        if (Math.abs(y - center.y) > 12.0) {
            y = center.y;
        }
        return new Vec3(x, y, z);
    }

    private void sound(Vec3 p, SoundEvent s, float volume, float pitch) {
        level().playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            ACTIVE.remove(ownerId);
            if (level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) {
                le.setNoGravity(false);
                le.fallDistance = 0;
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            finish();
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
        return d < 400.0 * 400.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
