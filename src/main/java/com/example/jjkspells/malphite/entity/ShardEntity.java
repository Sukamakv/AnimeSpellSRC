package com.example.jjkspells.malphite.entity;

import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.registry.ModEntities;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.util.Util;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seismic Shard - a spinning stone that curves towards its target. When it hits (or meets a wall, or runs out of range) it
 * shatters: the primary target is hurt, slowed and has its armor shattered, everything near takes explosion damage and a ring
 * of fragments flies out along a fixed ballistic path (the same maths run on the server for hits and on the client for drawing).
 */
public class ShardEntity extends MalEntityBase {
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(ShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(ShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAGS = SynchedEntityData.defineId(ShardEntity.class, EntityDataSerializers.INT);

    public static final int FRAG_TICKS = 22;

    private Vec3 dir = new Vec3(0, 0, 1);
    private UUID targetId;
    private double travelled;
    private int shattered;
    private final Map<UUID, Integer> fragHits = new HashMap<>();
    private final Set<Long> fragPairs = new HashSet<>();

    public ShardEntity(EntityType<? extends ShardEntity> type, Level level) {
        super(type, level);
    }

    /** Hurls a shard from the caster at whatever they are looking at (a living enemy is tracked). */
    public static ShardEntity launch(Level level, LivingEntity caster) {
        var c = MalConfig.SHARD;
        ShardEntity s = new ShardEntity(ModEntities.SHARD.get(), level);
        s.setOwner(caster);
        Vec3 look = caster.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
        Vec3 start = caster.getEyePosition().add(right.scale(0.45)).add(0, -0.35, 0).add(look.scale(0.9));
        s.setPos(start.x, start.y, start.z);
        s.dir = look.normalize();
        var hr = Utils.raycastForEntity(level, caster, c.range.get().floatValue(), true, 1.2f);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && Util.isEnemy(caster, le)) {
            s.targetId = le.getUUID();
        }
        s.entityData.set(DATA_SEED, level.random.nextInt(1000));
        s.entityData.set(DATA_FRAGS, c.fragments.get());
        s.updateRot();
        return s;
    }

    public int getState() {
        return entityData.get(DATA_STATE);
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    public int getFrags() {
        return entityData.get(DATA_FRAGS);
    }

    public Vec3 getDir() {
        float yaw = getYRot() * Mth.DEG_TO_RAD, pitch = getXRot() * Mth.DEG_TO_RAD;
        double cp = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }

    /** Where fragment i of n is t ticks after the shatter (the same path on both sides). */
    public static Vec3 fragPos(Vec3 at, int i, int n, int seed, int t) {
        double a = (i + (seed % 7) / 7.0) * Math.PI * 2 / Math.max(1, n);
        double h = 0.6 * (1.0 - Math.pow(0.93, t)) / 0.07;
        double y = 0.6 + 0.42 * t - 0.5 * 0.05 * t * t;
        return new Vec3(at.x + Math.cos(a) * h, at.y + Math.max(0.15, y), at.z + Math.sin(a) * h);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, 0);
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_FRAGS, 6);
    }

    private void updateRot() {
        setRot((float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG), (float) (-Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTrail();
            return;
        }
        var c = MalConfig.SHARD;
        if (shattered > 0) {
            fragments(c);
            shattered++;
            entityData.set(DATA_STATE, shattered);
            if (shattered > FRAG_TICKS + 8) {
                discard();
            }
            return;
        }
        LivingEntity owner = owner();
        if (owner == null) {
            discard();
            return;
        }
        // homing
        LivingEntity tgt = targetId != null && ((ServerLevel) level()).getEntity(targetId) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (tgt != null && tgt.position().distanceTo(position()) > c.trackBreak.get()) {
            tgt = null;
            targetId = null;
        }
        if (tgt == null) {
            targetId = null;
        } else {
            Vec3 want = tgt.getBoundingBox().getCenter().subtract(position());
            if (want.lengthSqr() > 1.0E-6) {
                dir = dir.lerp(want.normalize(), c.turnRate.get()).normalize();
            }
        }
        updateRot();
        double speed = c.blocksPerTick.get();
        Vec3 from = position();
        Vec3 to = from.add(dir.scale(speed));
        BlockHitResult bhr = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = bhr.getType() != HitResult.Type.MISS;
        if (blocked) {
            to = bhr.getLocation().subtract(dir.scale(0.2));
        }
        LivingEntity primary = null;
        double best = Double.MAX_VALUE;
        AABB box = new AABB(from, to).inflate(1.0);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, x -> Util.isEnemy(owner, x))) {
            AABB bb = e.getBoundingBox().inflate(0.55);
            double d;
            if (bb.contains(from)) {
                d = 0;
            } else {
                var cl = bb.clip(from, to);
                if (cl.isEmpty()) {
                    continue;
                }
                d = cl.get().distanceTo(from);
            }
            if (d < best) {
                best = d;
                primary = e;
            }
        }
        if (primary != null) {
            to = from.add(dir.scale(Math.max(0.0, best)));
        }
        // the dusty trail of the flight
        int n = Math.max(2, (int) (from.distanceTo(to) * 1.2));
        for (int k = 0; k < n; k++) {
            Vec3 p = from.lerp(to, k / (double) n);
            if (k % 2 == 0) {
                Util.fxDir(level(), ModParticles.DUST.get(), p.x, p.y, p.z, 0, 0.01, 0);
            } else {
                Util.fxDir(level(), ModParticles.GRAVEL.get(), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.1, -0.02, (random.nextDouble() - 0.5) * 0.1);
            }
        }
        travelled += from.distanceTo(to);
        setPos(to.x, to.y, to.z);
        if (primary != null || blocked || travelled >= Math.max(c.range.get(), c.trackBreak.get()) * 1.3) {
            shatter(owner, primary, to);
        }
    }

    private void shatter(LivingEntity owner, LivingEntity primary, Vec3 at) {
        var c = MalConfig.SHARD;
        shattered = 1;
        entityData.set(DATA_STATE, 1);
        if (primary != null) {
            Util.hurt(owner, primary, c.flat.get(), c.percent.get());
            if (primary.isAlive()) {
                Util.slowPct(primary, c.slowPercent.get(), (int) (c.slowSeconds.get() * 20));
                Util.armorDown(primary, c.armorPercent.get(), (int) (c.armorSeconds.get() * 20));
                Util.earthVuln(primary, c.earthVulnPercent.get(), (int) (c.armorSeconds.get() * 20));
            }
            // the stolen speed (it does not stack with itself)
            Util.hastePct(owner, c.hastePercent.get(), (int) (c.hasteSeconds.get() * 20));
        }
        for (LivingEntity e : Util.enemies(level(), at, c.aoeRadius.get(), owner)) {
            if (e != primary) {
                Util.hurt(owner, e, c.aoeFlat.get(), 0);
            }
        }
        Util.sound(level(), at.x, at.y, at.z, ModSounds.CRACK.get(), 1.4f, 0.9f);
        Util.blast(level(), at.x, at.y, at.z, c.aoeRadius.get(), 26);
        Util.fxDir(level(), ModParticles.RING.get(), at.x, at.y, at.z, 0, 0, 0);
        Util.ring(level(), ModParticles.DUST.get(), at, 16, 0.3, 0.02);
    }

    private void fragments(MalConfig.Shard c) {
        LivingEntity owner = owner();
        if (owner == null) {
            return;
        }
        int n = getFrags();
        int t = shattered;
        if (t > FRAG_TICKS) {
            return;
        }
        Vec3 at = position();
        int seed = getSeed();
        for (int i = 0; i < n; i++) {
            Vec3 p = fragPos(at, i, n, seed, t);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(0.8), x -> Util.isEnemy(owner, x))) {
                long key = (long) i * 1000003L + e.getId();
                int used = fragHits.getOrDefault(e.getUUID(), 0);
                if (used >= c.fragMaxHits.get() || !fragPairs.add(key)) {
                    continue;
                }
                fragHits.put(e.getUUID(), used + 1);
                Util.hurt(owner, e, c.fragFlat.get(), 0);
                Util.fx(level(), ModParticles.ROCK.get(), p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.15);
            }
            if (t % 3 == 0) {
                Util.fxDir(level(), ModParticles.GRAVEL.get(), p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }

    /** Dust and gravel behind the flying stone. */
    private void clientTrail() {
        if (getState() > 0) {
            return;
        }
        Vec3 now = position(), old = new Vec3(xo, yo, zo);
        int n = Math.max(1, (int) (now.distanceTo(old) * 1.2));
        for (int i = 0; i < n; i++) {
            Vec3 p = old.lerp(now, i / (double) n);
            level().addParticle(ModParticles.DUST.get(), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.03, 0.01, (random.nextDouble() - 0.5) * 0.03);
            if (random.nextInt(2) == 0) {
                level().addParticle(ModParticles.GRAVEL.get(), p.x + (random.nextDouble() - 0.5) * 0.4, p.y + (random.nextDouble() - 0.5) * 0.4, p.z + (random.nextDouble() - 0.5) * 0.4, 0, -0.03, 0);
            }
            if (random.nextInt(4) == 0) {
                level().addParticle(ModParticles.EMBER.get(), p.x, p.y, p.z, 0, 0.02, 0);
            }
        }
    }

    @Override
    protected void finish(boolean normal) {
        finished = true;
    }
}
