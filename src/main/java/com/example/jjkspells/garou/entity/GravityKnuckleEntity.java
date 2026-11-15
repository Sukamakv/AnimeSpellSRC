package com.example.jjkspells.garou.entity;

import com.example.jjkspells.garou.GarouEvents;
import com.example.jjkspells.garou.GarouRegistry;
import com.example.jjkspells.garou.spell.GarouGravityKnuckleSpell;
import com.example.jjkspells.garou.util.GarouDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GRAVITY KNUCKLE controller (non-living, no physics). Before the punch it follows the caster; at the punch it jumps to the
 * impact point and stays there as the centre of the well. Server: timeline, dash, pull, damage. Client: reads the synced
 * values and draws everything in GravityKnuckleRenderer.
 *
 * Timeline (ticks since spawn):
 *   0 .. T_DASH            fist raised, rocks lift, sphere forms (caster rooted)
 *   T_DASH .. punch        dash (max MAX_DASH ticks) toward the target picked at T_DASH
 *   punch .. +WELL         well: enemies within 30 blocks are dragged in and immobilised
 *   punch+WELL             collapse: damage + strong knockback, slowness removed
 *   .. +T_AFTER            aftermath, then the entity disappears
 * No block is ever changed: the "black hole" is a client-side visual plus entity motion only.
 */
public class GravityKnuckleEntity extends Entity {
    public static final int T_RAISE = 10;
    public static final int T_DASH = 26;
    public static final int MAX_DASH = 16;
    public static final int WELL = GarouGravityKnuckleSpell.WELL_TICKS;
    public static final int T_AFTER = 18;
    private static final double DASH_SPEED = 2.1;
    private static final int IMMOBILIZE_AMP = 6;   // slowness 7 = no walking

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(GravityKnuckleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PUNCH_AT = SynchedEntityData.defineId(GravityKnuckleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TX = SynchedEntityData.defineId(GravityKnuckleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TY = SynchedEntityData.defineId(GravityKnuckleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TZ = SynchedEntityData.defineId(GravityKnuckleEntity.class, EntityDataSerializers.FLOAT);

    public int age;
    // server state
    private boolean dashing, released;
    private int dashStart = -1, targetId = -1;
    private Vec3 targetPos = Vec3.ZERO, lastPos = Vec3.ZERO;
    private final Set<Integer> punched = new HashSet<>(), collapsed = new HashSet<>(), pulled = new HashSet<>();
    // client state: recent caster positions during the dash (renderer draws the trail)
    public final List<Vec3> trail = new ArrayList<>();

    public GravityKnuckleEntity(EntityType<? extends GravityKnuckleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static GravityKnuckleEntity create(Level level, LivingEntity caster) {
        GravityKnuckleEntity e = new GravityKnuckleEntity(GarouRegistry.GRAVITY_KNUCKLE, level);
        e.entityData.set(CASTER, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(CASTER, -1);
        entityData.define(PUNCH_AT, -1);
        entityData.define(TX, 0f);
        entityData.define(TY, 0f);
        entityData.define(TZ, 0f);
    }

    public int getCasterId() { return entityData.get(CASTER); }
    /** Age (ticks) at which the punch landed, -1 before that. */
    public int punchAt() { return entityData.get(PUNCH_AT); }
    public Vec3 getImpact() { return new Vec3(entityData.get(TX), entityData.get(TY), entityData.get(TZ)); }
    /** Caster cannot move on his own (charge + dash + a few ticks after the punch). */
    public boolean holdsCaster() { int p = punchAt(); return p < 0 || age < p + 4; }
    public static float prog(float t, float a, float b) { return Mth.clamp((t - a) / (b - a), 0f, 1f); }

    @Override
    public void onAddedToWorld() { super.onAddedToWorld(); GarouEvents.ACTIVE_GK.add(this); }

    @Override
    public void onRemovedFromWorld() {
        GarouEvents.ACTIVE_GK.remove(this);
        if (!level().isClientSide && level() instanceof ServerLevel sl) release(sl);
        super.onRemovedFromWorld();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick(); else serverTick();
        age++;
    }

    // ------------------------------------------------------------------ server
    private static GarouGravityKnuckleSpell spellInstance;
    private static DamageSource damageSource(LivingEntity caster) {
        if (spellInstance == null) spellInstance = new GarouGravityKnuckleSpell();
        return spellInstance.getDamageSource(caster);
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        Entity ce = sl.getEntity(getCasterId());
        if (!(ce instanceof LivingEntity caster) || !caster.isAlive()) { discard(); return; }
        int pa = punchAt();
        if (pa < 0) {
            if (age < T_DASH) {
                Vec3 m = caster.getDeltaMovement();
                caster.setDeltaMovement(0, Math.min(m.y, 0), 0);
                caster.hurtMarked = true;
                setPos(caster.getX(), caster.getY(), caster.getZ());
                if (age == 2) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 3f, 0.6f);
                if (age == T_RAISE) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3f, 0.5f);
            } else {
                if (!dashing) startDash(sl, caster);
                dashTick(sl, caster);
            }
        } else {
            wellTick(sl, caster, age - pa);
        }
    }

    /** Picks the target: nearest enemy on the look ray (with a little aim assist), else the block / point ahead. */
    private void startDash(ServerLevel sl, LivingEntity caster) {
        dashing = true;
        dashStart = age;
        float range = GarouGravityKnuckleSpell.TARGET_RANGE;
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double maxD = bh.getType() == HitResult.Type.MISS ? range : eye.distanceTo(bh.getLocation());
        Vec3 segEnd = eye.add(look.scale(maxD));
        LivingEntity best = null;
        double bestD = maxD;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(eye, segEnd).inflate(2.0), e -> GarouDamage.isEnemy(caster, e))) {
            AABB bb = t.getBoundingBox().inflate(0.7);
            Optional<Vec3> hit = bb.clip(eye, segEnd);
            double d = hit.isPresent() ? eye.distanceTo(hit.get()) : (bb.contains(eye) ? 0 : Double.MAX_VALUE);
            if (d < bestD) { bestD = d; best = t; }
        }
        if (best != null) {
            targetId = best.getId();
            targetPos = best.getBoundingBox().getCenter();
        } else if (bh.getType() != HitResult.Type.MISS) {
            targetPos = bh.getLocation().subtract(look.scale(0.6));
        } else {
            targetPos = eye.add(look.scale(12));
        }
        lastPos = caster.position();
        sl.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 3f, 0.7f);
    }

    private void dashTick(ServerLevel sl, LivingEntity caster) {
        Entity te = targetId >= 0 ? sl.getEntity(targetId) : null;
        if (te instanceof LivingEntity tl && tl.isAlive()) targetPos = tl.getBoundingBox().getCenter();
        setPos(caster.getX(), caster.getY(), caster.getZ());
        Vec3 me = caster.getBoundingBox().getCenter();
        Vec3 to = targetPos.subtract(me);
        double dist = to.length();
        int n = age - dashStart;
        boolean stuck = n >= 3 && caster.position().distanceToSqr(lastPos) < 0.04;
        lastPos = caster.position();
        if (dist <= 2.4 || n >= MAX_DASH || stuck) { punch(sl, caster, te); return; }
        double step = Math.max(0.4, Math.min(DASH_SPEED, dist - 1.8));
        caster.setDeltaMovement(to.normalize().scale(step));
        caster.hurtMarked = true;
        caster.fallDistance = 0;
    }

    private void punch(ServerLevel sl, LivingEntity caster, Entity te) {
        Vec3 me = caster.getBoundingBox().getCenter();
        boolean hasT = te instanceof LivingEntity tl && tl.isAlive() && tl.getBoundingBox().getCenter().distanceTo(me) <= 5.0;
        Vec3 impact;
        if (hasT) impact = te.getBoundingBox().getCenter();
        else if (targetPos.distanceTo(me) <= 5.0) impact = targetPos;
        else impact = me.add(caster.getLookAngle().scale(2.0));
        setPos(impact.x, impact.y, impact.z);
        entityData.set(TX, (float) impact.x);
        entityData.set(TY, (float) impact.y);
        entityData.set(TZ, (float) impact.z);
        entityData.set(PUNCH_AT, age);
        caster.swing(InteractionHand.MAIN_HAND, true);
        caster.setDeltaMovement(caster.getDeltaMovement().scale(0.1));
        caster.hurtMarked = true;
        sl.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5f, 0.6f);
        sl.playSound(null, impact.x, impact.y, impact.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 0.8f);

        DamageSource src = damageSource(caster);
        List<LivingEntity> victims = new ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(3.2), e -> GarouDamage.isEnemy(caster, e) && e.getBoundingBox().getCenter().distanceTo(impact) <= 3.2));
        if (hasT && !victims.contains(te)) victims.add((LivingEntity) te);
        for (LivingEntity t : victims) {
            if (punched.add(t.getId())) {
                GarouDamage.hit(t, caster, src, GarouGravityKnuckleSpell.PUNCH_FLAT, GarouGravityKnuckleSpell.PUNCH_PERCENT);
            }
        }
    }

    private void wellTick(ServerLevel sl, LivingEntity caster, int a) {
        Vec3 c = getImpact();
        if (a < GarouGravityKnuckleSpell.WELL_TICKS) {
            double range = GarouGravityKnuckleSpell.PULL_RANGE;
            AABB box = new AABB(c, c).inflate(range);
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> GarouDamage.isEnemy(caster, e))) {
                Vec3 tc = t.getBoundingBox().getCenter();
                Vec3 d = c.subtract(tc);
                double dist = d.length();
                if (dist > range) continue;
                pulled.add(t.getId());
                // immobilise (no walking); the pull itself is done by setting the motion each tick
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, IMMOBILIZE_AMP, false, false, false));
                if (dist < 1.2) {
                    t.setDeltaMovement(d.scale(0.2));          // held at the centre
                } else {
                    double speed = Math.min(dist * 0.6, Mth.clamp(0.25 + dist * 0.08, 0.3, 1.5));
                    t.setDeltaMovement(d.normalize().scale(speed));
                }
                t.hurtMarked = true;
                t.fallDistance = 0;
            }
            if (a % 10 == 0) sl.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 2f, 0.5f + a * 0.02f);
        } else if (a == GarouGravityKnuckleSpell.WELL_TICKS) {
            collapse(sl, caster, c);
        } else if (a >= GarouGravityKnuckleSpell.WELL_TICKS + T_AFTER) {
            discard();
        }
    }

    private void collapse(ServerLevel sl, LivingEntity caster, Vec3 c) {
        release(sl);   // slowness off first so the knockback can actually move them
        double r = GarouGravityKnuckleSpell.COLLAPSE_RADIUS;
        DamageSource src = damageSource(caster);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8f, 0.5f);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.7f);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4f, 0.5f);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> GarouDamage.isEnemy(caster, e))) {
            Vec3 tc = t.getBoundingBox().getCenter();
            double dist = tc.distanceTo(c);
            if (dist > r) continue;
            if (collapsed.add(t.getId())) {
                GarouDamage.hit(t, caster, src, GarouGravityKnuckleSpell.COLLAPSE_FLAT, GarouGravityKnuckleSpell.COLLAPSE_PERCENT);
            }
            if (!t.isAlive()) continue;
            Vec3 away = new Vec3(tc.x - c.x, 0, tc.z - c.z);
            if (away.lengthSqr() < 0.04) away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            away = away.normalize();
            double fall = 1.0 - Mth.clamp(dist / r, 0, 1);
            double resist = t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double push = (1.6 + 1.8 * fall) * Math.max(0.0, 1.0 - resist);
            t.setDeltaMovement(away.x * push, (0.55 + 0.5 * fall) * Math.max(0.2, 1.0 - resist), away.z * push);
            t.hurtMarked = true;
        }
    }

    /** Removes the immobilise effect we applied from everyone we pulled (only our amplifier). */
    private void release(ServerLevel sl) {
        if (released) return;
        released = true;
        for (int id : pulled) {
            Entity e = sl.getEntity(id);
            if (e instanceof LivingEntity l) {
                MobEffectInstance inst = l.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (inst != null && inst.getAmplifier() == IMMOBILIZE_AMP) l.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
    }

    // ------------------------------------------------------------------ client
    private void clientTick() {
        int pa = punchAt();
        Entity ce = level().getEntity(getCasterId());
        if (pa < 0 && age >= T_DASH && ce != null) {
            trail.add(ce.getBoundingBox().getCenter());
        } else if (!trail.isEmpty()) {
            trail.remove(0);
        }
        while (trail.size() > 18) trail.remove(0);

        // supporting vanilla layer: ground particles lifting around the caster, then spiralling into the well
        if (pa < 0 && age < T_DASH && ce != null && age % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double ang = random.nextDouble() * Math.PI * 2, rad = 1.0 + random.nextDouble() * 4.0;
                double x = ce.getX() + Math.cos(ang) * rad, z = ce.getZ() + Math.sin(ang) * rad;
                spawnDebris(x, ce.getY(), z, 0, 0.12 + random.nextDouble() * 0.2, 0);
            }
        } else if (pa >= 0 && age - pa < WELL) {
            Vec3 c = getImpact();
            for (int i = 0; i < 3; i++) {
                double ang = random.nextDouble() * Math.PI * 2, rad = 5.0 + random.nextDouble() * 9.0;
                double x = c.x + Math.cos(ang) * rad, z = c.z + Math.sin(ang) * rad;
                Vec3 in = new Vec3(c.x - x, 0, c.z - z).normalize();
                Vec3 tan = new Vec3(-in.z, 0, in.x);
                spawnDebris(x, c.y - 1.0, z, in.x * 0.35 + tan.x * 0.25, 0.1, in.z * 0.35 + tan.z * 0.25);
            }
        }
    }

    private void spawnDebris(double x, double y, double z, double vx, double vy, double vz) {
        BlockPos bp = BlockPos.containing(x, y - 1, z);
        if (!level().hasChunkAt(bp)) return;
        BlockState st = level().getBlockState(bp);
        if (st.isAir()) return;
        level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), x, y + 0.1, z, vx, vy, vz);
    }

    // ------------------------------------------------------------------ boilerplate
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256.0 * 256.0; }
}
