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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.jjkspells.garou.entity.AllLifeTimeline.*;

/**
 * ALL LIFE ERADICATION FIST. One invisible controller entity runs the whole sequence (see {@link AllLifeTimeline}).
 *
 * Damage events, each with its OWN per-target hit set, so no target can be hit twice by the same event however many
 * hitboxes overlap, and one event never blocks another:
 * <pre>
 *   strike 1..5   100 + 3% max health   melee reach around the strike point
 *   nuclear       400 + 20% max health   40 blocks
 *   collapse      350 + 15% max health   40 blocks
 *   final shock   200 + 20% max health   40 blocks, when the expanding wave reaches the target
 * </pre>
 */
public class AllLifeEntity extends Entity {
    /** Casters that are in the middle of the sequence (they cannot cast again). */
    public static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(AllLifeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ASSAULT = SynchedEntityData.defineId(AllLifeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(AllLifeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_CX = SynchedEntityData.defineId(AllLifeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CY = SynchedEntityData.defineId(AllLifeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CZ = SynchedEntityData.defineId(AllLifeEntity.class, EntityDataSerializers.FLOAT);

    private static final double DASH_STEP = 3.2, PICK_RANGE = 35.0, STAND_OFF = 2.4;

    private UUID ownerId;
    private boolean finished, launched, assaulted, freed;
    private LivingEntity primary;
    private Vec3 dashDir = Vec3.ZERO, dashStart = Vec3.ZERO, lastPos = Vec3.ZERO, fallback = Vec3.ZERO;
    private double dashDist;
    private int dashTicks, stuck, assaultAge = -1;
    private Vec3 center = Vec3.ZERO;

    /** One hit set per strike and one for each of the other damage events. */
    private final List<Set<UUID>> strikeHit = new ArrayList<>();
    private final Set<UUID> nukeHit = new HashSet<>(), gravHit = new HashSet<>(), shockHit = new HashSet<>();
    private final Set<UUID> held = new HashSet<>();

    public AllLifeEntity(EntityType<? extends AllLifeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        for (int i = 0; i < STRIKE.length; i++) {
            strikeHit.add(new HashSet<>());
        }
    }

    public static AllLifeEntity create(Level level, LivingEntity caster) {
        AllLifeEntity e = new AllLifeEntity(NfEntities.ALL_LIFE.get(), level);
        e.ownerId = caster.getUUID();
        e.entityData.set(DATA_OWNER, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    // ------------------------------------------------------------------ what the client reads

    public Entity getOwnerEntity() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    /** Age at which the first strike starts; -1 while the caster is still in the stance or dashing. */
    public int assaultAge() {
        return entityData.get(DATA_ASSAULT);
    }

    public int targetId() {
        return entityData.get(DATA_TARGET);
    }

    /** The ground point under the target: the centre of every blast. */
    public Vec3 centerPos() {
        return new Vec3(entityData.get(DATA_CX), entityData.get(DATA_CY), entityData.get(DATA_CZ));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_OWNER, -1);
        entityData.define(DATA_ASSAULT, -1);
        entityData.define(DATA_TARGET, -1);
        entityData.define(DATA_CX, 0f);
        entityData.define(DATA_CY, 0f);
        entityData.define(DATA_CZ, 0f);
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
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.garou.client.AllLifeFx.tick(this));
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
            org.slf4j.LoggerFactory.getLogger("jjk_spells").error("all_life_eradication_fist failed", t);
            finish();
            discard();
            return;
        }
        if (assaulted && age >= assaultAge + NUKE + AFTER) {
            finish();
            discard();
        }
    }

    private void run(int age, LivingEntity o) {
        if (age == 0) {
            ACTIVE.add(ownerId);
            sound(o.position(), SoundEvents.BEACON_POWER_SELECT, 3.0f, 0.5f);
            sound(o.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 2.0f, 0.4f);
        }
        if (!freed) {
            // the caster is untouchable for the whole sequence
            if (age % 10 == 0) {
                o.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 14, 4, false, false, false));
            }
            o.fallDistance = 0;
        }
        if (!launched) {
            hold(o);
            if (age == 10 || age == 22 || age == 32) {
                sound(o.position(), SoundEvents.WARDEN_HEARTBEAT, 3.0f, 0.8f + age * 0.01f);
            }
            if (age == 20) {
                sound(o.position(), SoundEvents.BEACON_AMBIENT, 3.0f, 0.6f);
            }
            if (age >= STANCE) {
                launch(age, o);
            }
        } else if (!assaulted) {
            dash(age, o);
        } else {
            assault(age - assaultAge, o);
        }
    }

    /** The caster stands firm: no movement, no gravity. */
    private void hold(LivingEntity o) {
        o.setSprinting(false);
        o.setNoGravity(true);
        o.setDeltaMovement(Vec3.ZERO);
        o.hurtMarked = true;
        o.fallDistance = 0;
    }

    // ------------------------------------------------------------------ dash

    /** The enemy the caster looks at, else the nearest one in range. */
    private LivingEntity pickTarget(LivingEntity o) {
        Vec3 eye = o.getEyePosition();
        Vec3 look = o.getLookAngle();
        LivingEntity best = null;
        double bs = -1.0E9;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, o.getBoundingBox().inflate(PICK_RANGE), x -> hittable(o, x))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double dist = d.length();
            if (dist < 0.5 || dist > PICK_RANGE) {
                continue;
            }
            double dot = d.scale(1.0 / dist).dot(look);
            double score = dot > 0.82 ? dot * 10.0 - dist * 0.05 : -dist * 0.05 - 5.0;
            if (score > bs) {
                bs = score;
                best = e;
            }
        }
        return best;
    }

    private void launch(int age, LivingEntity o) {
        launched = true;
        primary = pickTarget(o);
        Vec3 from = o.getBoundingBox().getCenter();
        Vec3 to;
        if (primary != null) {
            to = primary.getBoundingBox().getCenter();
        } else {
            Vec3 eye = o.getEyePosition();
            Vec3 look = o.getLookAngle().normalize();
            BlockHitResult bh = level().clip(new ClipContext(eye, eye.add(look.scale(14.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o));
            to = bh.getType() == HitResult.Type.MISS ? eye.add(look.scale(14.0)) : bh.getLocation();
            fallback = to;
        }
        Vec3 d = to.subtract(from);
        double dist = d.length();
        dashDir = dist < 1.0E-4 ? o.getLookAngle().normalize() : d.scale(1.0 / dist);
        dashDist = Math.max(0.0, dist - STAND_OFF);
        dashStart = o.position();
        lastPos = dashStart;
        dashTicks = 0;
        stuck = 0;
        faceTowards(o, to);
        sound(o.position(), SoundEvents.WARDEN_SONIC_BOOM, 4.0f, 1.2f);
        sound(o.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0f, 0.5f);
        if (dashDist < 0.4) {
            beginAssault(age, o);
        }
    }

    private void dash(int age, LivingEntity o) {
        dashTicks++;
        double remaining = dashDist - o.position().distanceTo(dashStart);
        stuck = o.position().distanceToSqr(lastPos) < 0.0004 ? stuck + 1 : 0;
        lastPos = o.position();
        if (remaining <= 0.35 || dashTicks > DASH_MAX || stuck >= 2) {
            beginAssault(age, o);
            return;
        }
        o.setNoGravity(true);
        o.setDeltaMovement(dashDir.scale(Math.min(DASH_STEP, remaining)));
        o.hurtMarked = true;
        o.fallDistance = 0;
    }

    private void beginAssault(int age, LivingEntity o) {
        assaulted = true;
        assaultAge = age;
        hold(o);
        Vec3 p = primary != null ? primary.position() : fallback;
        BlockHitResult gh = level().clip(new ClipContext(p.add(0, 1.0, 0), p.add(0, -30.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o));
        center = gh.getType() == HitResult.Type.MISS ? p : new Vec3(p.x, gh.getLocation().y, p.z);
        entityData.set(DATA_ASSAULT, age);
        entityData.set(DATA_TARGET, primary != null ? primary.getId() : -1);
        entityData.set(DATA_CX, (float) center.x);
        entityData.set(DATA_CY, (float) center.y);
        entityData.set(DATA_CZ, (float) center.z);
        faceTowards(o, strikePoint());
    }

    // ------------------------------------------------------------------ the assault

    private Vec3 strikePoint() {
        return primary != null && primary.isAlive() ? primary.getBoundingBox().getCenter() : center.add(0, 1.2, 0);
    }

    private void faceTowards(LivingEntity o, Vec3 p) {
        Vec3 d = p.subtract(o.getEyePosition());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * 180.0 / Math.PI);
        float pitch = (float) (-(Mth.atan2(d.y, h) * 180.0 / Math.PI));
        if (o instanceof ServerPlayer sp && sp.connection != null) {
            sp.connection.teleport(o.getX(), o.getY(), o.getZ(), yaw, pitch);
        } else {
            o.setYRot(yaw);
            o.setYHeadRot(yaw);
            o.setXRot(pitch);
        }
    }

    private void assault(int a, LivingEntity o) {
        hold(o);
        holdTargets();
        for (int i = 0; i < STRIKE.length; i++) {
            if (a == STRIKE[i]) {
                faceTowards(o, strikePoint());
                o.swing(i == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
                windup(i, o);
            }
            if (a == STRIKE[i] + HIT_DELAY) {
                strike(i, o);
            }
        }
        int ti = a - NUKE;
        if (ti >= 0) {
            afterNuke(ti, o);
        }
    }

    private void windup(int i, LivingEntity o) {
        float[] pitch = {1.0f, 1.15f, 0.8f, 0.7f, 0.5f};
        sound(o.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 3.0f, pitch[i]);
        sound(o.position(), SoundEvents.WARDEN_ATTACK_IMPACT, 1.5f, 1.4f - 0.15f * i);
    }

    /** Anything the caster may hurt: not the caster, not allies, not the caster's companions. */
    private static boolean hittable(LivingEntity o, LivingEntity e) {
        if (e == o || !e.isAlive() || e.isSpectator() || e.isAlliedTo(o)) {
            return false;
        }
        return !(e instanceof OwnableEntity oe && oe.getOwnerUUID() != null && oe.getOwnerUUID().equals(o.getUUID()));
    }

    /** One martial arts strike: every enemy inside the reach, once for this strike. */
    private void strike(int i, LivingEntity o) {
        Vec3 p = strikePoint();
        DamageSource src = o instanceof Player pl ? o.damageSources().playerAttack(pl) : o.damageSources().mobAttack(o);
        List<LivingEntity> near = new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(MELEE + 1.0), x -> hittable(o, x)));
        if (primary != null && primary.isAlive() && !near.contains(primary)) {
            near.add(primary);
        }
        Set<UUID> done = strikeHit.get(i);
        for (LivingEntity e : near) {
            if (e != primary && Math.sqrt(e.getBoundingBox().distanceToSqr(p)) > MELEE) {
                continue;
            }
            if (done.add(e.getUUID())) {
                deal(e, STRIKE_FLAT, STRIKE_PERCENT, src, o);
                held.add(e.getUUID());
            }
        }
        SoundEvent[] hits = {SoundEvents.PLAYER_ATTACK_STRONG, SoundEvents.PLAYER_ATTACK_CRIT, SoundEvents.IRON_GOLEM_ATTACK, SoundEvents.ANVIL_LAND, SoundEvents.GENERIC_EXPLODE};
        float[] vol = {3.0f, 3.5f, 4.0f, 5.0f, 8.0f};
        sound(p, hits[i], vol[i], 0.7f + 0.08f * i);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 2.0f + i, 1.6f - 0.18f * i);
    }

    /** The struck enemies hang in the air for the whole combo. */
    private void holdTargets() {
        if (held.isEmpty() || !(level() instanceof ServerLevel sl)) {
            return;
        }
        for (UUID id : held) {
            if (sl.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
                le.setDeltaMovement(Vec3.ZERO);
                le.hurtMarked = true;
                le.fallDistance = 0;
            }
        }
    }

    // ------------------------------------------------------------------ nuclear fission, gravity collapse, shockwave

    private void afterNuke(int ti, LivingEntity o) {
        Vec3 c = center;
        if (ti == 0) {
            sound(c, SoundEvents.DRAGON_FIREBALL_EXPLODE, 14.0f, 0.45f);
            sound(c, SoundEvents.WARDEN_SONIC_BOOM, 14.0f, 0.6f);
        }
        if (ti == BLAST_DELAY) {
            held.clear();
            sound(c, SoundEvents.GENERIC_EXPLODE, 20.0f, 0.35f);
            sound(c, SoundEvents.GENERIC_EXPLODE, 20.0f, 0.6f);
            sound(c, SoundEvents.LIGHTNING_BOLT_THUNDER, 20.0f, 0.45f);
            nuclear(o);
        }
        for (int i = 0; i < MED_COUNT; i++) {
            if (ti == medTick(i)) {
                Vec3 p = c.add(medOffset(i, getId()));
                sound(p, SoundEvents.GENERIC_EXPLODE, 10.0f, 0.5f + 0.1f * i);
                sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 8.0f, 0.6f);
            }
        }
        for (int i = 0; i < SAT_COUNT; i++) {
            if (ti == satTick(i)) {
                sound(c.add(satOffset(i, getId())), SoundEvents.GENERIC_EXPLODE, 6.0f, 0.7f + 0.3f * GarouTimeline.hash(i, 9));
            }
        }
        if (ti == 40) {
            sound(c, SoundEvents.FIRE_EXTINGUISH, 8.0f, 0.5f);
        }
        // gravity: the sphere forms, the explosion is dragged into it, everything in range is drawn inward
        if (ti == GRAV) {
            sound(c, SoundEvents.BEACON_ACTIVATE, 16.0f, 0.5f);
            sound(c, SoundEvents.PORTAL_TRAVEL, 4.0f, 0.5f);
        }
        if (ti >= GRAV && ti < COLLAPSE) {
            if (ti % 16 == 0) {
                sound(c.add(0, GRAV_HEIGHT, 0), SoundEvents.BEACON_AMBIENT, 12.0f, 0.4f + 1.2f * (ti - GRAV) / (float) GRAV_SPAN);
            }
            if (ti % 24 == 0) {
                sound(c.add(0, GRAV_HEIGHT, 0), SoundEvents.WARDEN_HEARTBEAT, 12.0f, 0.5f + 0.02f * (ti - GRAV));
            }
            pull(ti, o);
        }
        if (ti == COLLAPSE) {
            sound(c, SoundEvents.GENERIC_EXPLODE, 24.0f, 0.3f);
            sound(c, SoundEvents.WARDEN_SONIC_BOOM, 24.0f, 0.4f);
            sound(c, SoundEvents.END_PORTAL_SPAWN, 20.0f, 0.5f);
            collapse(o);
        }
        if (ti == SHOCK0) {
            sound(c, SoundEvents.WARDEN_SONIC_BOOM, 24.0f, 0.3f);
            sound(c, SoundEvents.LIGHTNING_BOLT_THUNDER, 24.0f, 0.4f);
            sound(c, SoundEvents.ENDER_DRAGON_GROWL, 14.0f, 0.5f);
        }
        if (ti >= SHOCK0 && ti <= SHOCK0 + SHOCK_LEN) {
            shock(ti, o);
        }
        // the caster stays standing in the middle as everything fades
        if (ti >= FREE && !freed) {
            freed = true;
            o.setNoGravity(false);
            o.fallDistance = 0;
        } else if (!freed) {
            hold(o);
        }
    }

    /** Nuclear Fission finisher: once per target, 40 blocks. */
    private void nuclear(LivingEntity o) {
        DamageSource src = o.damageSources().explosion(this, o);
        for (LivingEntity e : inRange(o, RADIUS)) {
            if (nukeHit.add(e.getUUID())) {
                deal(e, NUKE_FLAT, NUKE_PERCENT, src, o);
                if (e.isAlive()) {
                    Vec3 away = e.position().subtract(center);
                    Vec3 flat = new Vec3(away.x, 0, away.z);
                    flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
                    double fall = Math.max(0.0, 1.0 - Math.sqrt(away.lengthSqr()) / RADIUS);
                    e.setDeltaMovement(flat.x * (0.6 + 1.4 * fall), 0.5 + 0.9 * fall, flat.z * (0.6 + 1.4 * fall));
                    e.hurtMarked = true;
                }
            }
        }
    }

    /** Draws every enemy in range towards the gravitational sphere. */
    private void pull(int ti, LivingEntity o) {
        Vec3 gc = center.add(0, GRAV_HEIGHT, 0);
        double f = GarouTimeline.smooth((ti - GRAV) / (float) GRAV_SPAN);
        for (LivingEntity e : inRange(o, RADIUS)) {
            Vec3 d = gc.subtract(e.getBoundingBox().getCenter());
            double dist = d.length();
            Vec3 v;
            if (dist < 1.8) {
                v = e.getDeltaMovement().scale(0.4);
            } else {
                v = e.getDeltaMovement().scale(0.55).add(d.scale(1.0 / dist).scale(0.12 + 0.75 * f));
                double sp = v.length();
                if (sp > 1.4) {
                    v = v.scale(1.4 / sp);
                }
            }
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
    }

    /** Gravitational collapse: once per target, 40 blocks. */
    private void collapse(LivingEntity o) {
        DamageSource src = o.damageSources().indirectMagic(this, o);
        for (LivingEntity e : inRange(o, RADIUS)) {
            if (gravHit.add(e.getUUID())) {
                deal(e, COLLAPSE_FLAT, COLLAPSE_PERCENT, src, o);
            }
        }
    }

    /** The final cosmic shockwave: each target is hit once, when the front reaches it. */
    private void shock(int ti, LivingEntity o) {
        float f = (ti - SHOCK0) / (float) SHOCK_LEN;
        double front = RADIUS * GarouTimeline.ease(f);
        DamageSource src = o.damageSources().indirectMagic(this, o);
        for (LivingEntity e : inRange(o, RADIUS)) {
            double d = Math.sqrt(e.getBoundingBox().distanceToSqr(center));
            if (d > front || !shockHit.add(e.getUUID())) {
                continue;
            }
            deal(e, SHOCK_FLAT, SHOCK_PERCENT, src, o);
            if (e.isAlive()) {
                Vec3 away = e.position().subtract(center);
                Vec3 flat = new Vec3(away.x, 0, away.z);
                flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
                e.setDeltaMovement(flat.x * 2.6, 0.7, flat.z * 2.6);
                e.hurtMarked = true;
            }
        }
    }

    private List<LivingEntity> inRange(LivingEntity o, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r + 3.0), x -> hittable(o, x))) {
            if (e.getBoundingBox().distanceToSqr(center) <= r * r) {
                out.add(e);
            }
        }
        return out;
    }

    /** Flat damage + percent of the target's maximum health. */
    private void deal(LivingEntity t, double flat, double percent, DamageSource src, LivingEntity o) {
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
        return d < 500.0 * 500.0;
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
