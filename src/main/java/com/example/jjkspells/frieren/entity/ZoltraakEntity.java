package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.frieren.FrierenRegistry;
import com.example.jjkspells.frieren.spell.ZoltraakSpell;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
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

/**
 * Zoltraak controller. The whole shot is deterministic from four synced values (origin = entity position, direction, length
 * and a seed), so the client animates the formation, the head and the trail purely from {@code tickCount} with no
 * per-tick network traffic. The server sweeps the head along the line each tick and applies the damage.
 *
 * Timeline (ticks): 0..LAUNCH the seal opens | LAUNCH the laser's head leaves, moving SPEED blocks/tick, leaving a thick solid
 * beam behind it | head reaches the end (a wall or RANGE) and bursts | the beam holds HOLD ticks, then thins out over FADE.
 *
 * Hit events (entities struck) are synced as a compound tag: n = count, d{i} = distance along the beam, k{i} = 0 direct / 1 pierce.
 */
public class ZoltraakEntity extends Entity {
    public static final int LAUNCH = 4;
    public static final float SPEED = 8.0f;
    /** The laser body stays at full width this long after the head arrives, then thins out over FADE ticks. */
    public static final int HOLD = 6;
    public static final int FADE = 10;
    public static final float RANGE = 45f;

    /** Event kinds drawn by the renderer (0/1 come from the server, 2/3 are derived from the beam length). */
    public static final int K_DIRECT = 0, K_PIERCE = 1, K_WALL = 2, K_FIZZLE = 3;

    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(ZoltraakEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(ZoltraakEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(ZoltraakEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LEN = SynchedEntityData.defineId(ZoltraakEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(ZoltraakEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> EVENTS = SynchedEntityData.defineId(ZoltraakEntity.class, EntityDataSerializers.COMPOUND_TAG);

    // server
    private UUID ownerId;
    private final Set<Integer> hitIds = new HashSet<>();
    private int hitCount = 0;

    // client
    private int clientSeen = 0;
    private boolean clientTerminal = false;
    /** {distance, kind} of every entity hit, read from the synced events. */
    public final List<float[]> clientHits = new ArrayList<>();

    public ZoltraakEntity(EntityType<? extends ZoltraakEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ------------------------------------------------------------------------------------------------ creation
    public static ZoltraakEntity create(Level level, LivingEntity caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        double side = caster.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;

        // the formation opens just in front of the raised hand
        Vec3 origin = eye.add(right.scale(0.25 * side)).add(look.scale(1.9)).add(0, -0.15, 0);
        BlockHitResult toHand = level.clip(new ClipContext(eye, origin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (toHand.getType() != HitResult.Type.MISS) origin = toHand.getLocation().subtract(look.scale(0.1));

        // direct aim: find what the crosshair points at (nearest foe, else the block / far point)
        Vec3 far = eye.add(look.scale(RANGE + 2));
        BlockHitResult bh = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double maxD = bh.getType() == HitResult.Type.MISS ? RANGE + 2 : bh.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(maxD));
        Vec3 aim = end;
        double best = maxD;
        for (Entity e : level.getEntities(caster, new AABB(eye, end).inflate(1.0), t -> isFoe(caster, t))) {
            Optional<Vec3> p = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (p.isPresent()) {
                double d = p.get().distanceTo(eye);
                if (d < best) {
                    best = d;
                    aim = e.getBoundingBox().getCenter();
                }
            }
        }
        Vec3 dir = aim.subtract(origin);
        dir = (dir.lengthSqr() < 0.25 || dir.dot(look) < 0.2) ? look : dir.normalize();

        BlockHitResult beam = level.clip(new ClipContext(origin, origin.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        float len = beam.getType() == HitResult.Type.MISS ? RANGE : (float) beam.getLocation().distanceTo(origin);

        ZoltraakEntity z = new ZoltraakEntity(FrierenRegistry.ZOLTRAAK_ENTITY, level);
        z.setPos(origin.x, origin.y, origin.z);
        z.ownerId = caster.getUUID();
        z.entityData.set(DX, (float) dir.x);
        z.entityData.set(DY, (float) dir.y);
        z.entityData.set(DZ, (float) dir.z);
        z.entityData.set(LEN, len);
        z.entityData.set(SEED, level.random.nextInt());
        return z;
    }

    /** Who the beam may hurt: living, alive, not the caster, not an ally, not friendly fire. */
    public static boolean isFoe(LivingEntity owner, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator() || e == owner) return false;
        if (owner == null) return true;
        return !e.isAlliedTo(owner) && !DamageSources.isFriendlyFireBetween(owner, e);
    }

    // ------------------------------------------------------------------------------------------------ data
    @Override
    protected void defineSynchedData() {
        this.entityData.define(DX, 0f);
        this.entityData.define(DY, 0f);
        this.entityData.define(DZ, 1f);
        this.entityData.define(LEN, RANGE);
        this.entityData.define(SEED, 0);
        this.entityData.define(EVENTS, new CompoundTag());
    }

    public Vec3 dir() {
        return new Vec3(entityData.get(DX), entityData.get(DY), entityData.get(DZ));
    }

    public float length() {
        return entityData.get(LEN);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    /** Tick at which the head reaches the end of the beam. */
    public int endTick() {
        return LAUNCH + (int) Math.ceil(length() / SPEED);
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

    // ------------------------------------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    private void serverTick() {
        int age = tickCount;
        float len = length();
        Vec3 o = position();
        Vec3 dir = dir();
        ServerLevel sl = (ServerLevel) level();

        if (age == 0) {
            sl.playSound(null, o.x, o.y, o.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 1.7f);
            sl.playSound(null, o.x, o.y, o.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 2.0f);
        }
        if (age == LAUNCH) {
            sl.playSound(null, o.x, o.y, o.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.45f, 2.0f);
            sl.playSound(null, o.x, o.y, o.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 1.9f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.0f);
            com.example.jjkspells.vfx.sprite.SpriteFx.beam(sl, o, o.add(dir.scale(len)), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 1.2f, endTick() - LAUNCH + HOLD);
        }
        if (age > LAUNCH) {
            LivingEntity owner = resolveOwner(sl);
            if (owner == null) { // caster logged out / died: stop dealing damage
                discard();
                return;
            }
            float prev = Math.min(len, SPEED * (age - 1 - LAUNCH));
            float cur = Math.min(len, SPEED * (age - LAUNCH));
            if (cur > prev) sweep(sl, owner, o, dir, prev, cur);
        }
        if (age == endTick()) {
            Vec3 e = o.add(dir.scale(len));
            sl.playSound(null, e.x, e.y, e.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 2.0f);
            sl.playSound(null, e.x, e.y, e.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6f, 1.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, e, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 2.0f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, e, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f);
        }
        if (age > endTick() + HOLD + FADE + 4) discard();
    }

    private LivingEntity resolveOwner(ServerLevel sl) {
        if (ownerId == null) return null;
        Entity e = sl.getEntity(ownerId);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private record Hit(LivingEntity target, double dist) {
    }

    /** Sweeps the segment [prev,cur] of the beam and strikes everything on it, nearest first. */
    private void sweep(ServerLevel sl, LivingEntity owner, Vec3 o, Vec3 dir, float prev, float cur) {
        Vec3 a = o.add(dir.scale(prev));
        Vec3 b = o.add(dir.scale(cur));
        List<Hit> hits = new ArrayList<>();
        for (Entity e : sl.getEntities(this, new AABB(a, b).inflate(1.5), t -> isFoe(owner, t))) {
            if (hitIds.contains(e.getId())) continue;
            AABB bb = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> p = bb.clip(a, b);
            if (p.isPresent()) hits.add(new Hit((LivingEntity) e, prev + p.get().distanceTo(a)));
            else if (bb.contains(a)) hits.add(new Hit((LivingEntity) e, prev));
        }
        hits.sort(Comparator.comparingDouble(Hit::dist));
        for (Hit h : hits) {
            if (!hitIds.add(h.target().getId())) continue;
            boolean direct = hitCount == 0;
            hitCount++;
            strike(h.target(), owner, direct);
            pushEvent((float) h.dist(), direct ? K_DIRECT : K_PIERCE);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, o.add(dir.scale(h.dist())), direct ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, direct ? 1.6f : 1.2f);
        }
    }

    private void strike(LivingEntity target, LivingEntity owner, boolean direct) {
        float flat = direct ? ZoltraakSpell.DIRECT_FLAT : ZoltraakSpell.PIERCE_FLAT;
        float pct = direct ? ZoltraakSpell.DIRECT_PERCENT : ZoltraakSpell.PIERCE_PERCENT;
        float percentHp = target.getMaxHealth() * pct; // measured before the flat part lands
        DamageSource src = FrierenRegistry.ZOLTRAAK_SPELL.getDamageSource(this, owner);
        Vec3 motion = target.getDeltaMovement();
        target.invulnerableTime = 0;
        DamageSources.applyDamage(target, flat, src); // flat part: normal spell damage, not scaled by max_hp_damage
        if (target.isAlive()) {
            target.invulnerableTime = 0;
            // percent part: scaled by the caster's max_hp_damage, reduced by the target's max_hp_resist (SpellDamage handles both)
            SpellDamage.percent(target, percentHp, owner, src);
        }
        // a piercing lance, not a shove: cancel knockback
        target.setDeltaMovement(motion);
        target.hurtMarked = true;
    }

    private void pushEvent(float dist, int kind) {
        CompoundTag tag = entityData.get(EVENTS).copy();
        int n = tag.getInt("n");
        tag.putFloat("d" + n, dist);
        tag.putInt("k" + n, kind);
        tag.putInt("n", n + 1);
        entityData.set(EVENTS, tag);
    }

    // ------------------------------------------------------------------------------------------------ client
    private void clientTick() {
        CompoundTag tag = entityData.get(EVENTS);
        int n = tag.getInt("n");
        Vec3 o = position();
        Vec3 dir = dir();
        for (int i = clientSeen; i < n; i++) {
            float d = tag.getFloat("d" + i);
            clientHits.add(new float[]{d, tag.getInt("k" + i)});
            starBurst(o.add(dir.scale(d)), 6, 0.16);
        }
        clientSeen = n;

        float len = length();
        int age = tickCount;
        if (age > LAUNCH) {
            // a sparse supporting layer of star particles along the path the head just flew
            float prev = Math.min(len, SPEED * (age - 1 - LAUNCH));
            float cur = Math.min(len, SPEED * (age - LAUNCH));
            for (float d = prev; d < cur; d += 3.0f) {
                Vec3 p = o.add(dir.scale(d));
                level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0, 0);
            }
        }
        if (!clientTerminal && age >= endTick()) {
            clientTerminal = true;
            starBurst(o.add(dir.scale(len)), 12, 0.22);
        }
    }

    private void starBurst(Vec3 p, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(speed * (0.5 + random.nextDouble()));
            level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return true;
    }
}
