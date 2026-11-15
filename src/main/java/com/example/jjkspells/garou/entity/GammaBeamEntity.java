package com.example.jjkspells.garou.entity;

import com.example.jjkspells.garou.config.GarConfig;
import com.example.jjkspells.garou.registry.ModEntities;
import com.example.jjkspells.garou.registry.ModParticles;
import com.example.jjkspells.garou.util.GarUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the whole GAMMA RAY BURST sequence for one caster (server) and drives the visuals (client).
 *
 * <pre>
 *   age 0  .. 50   the caster is locked, both hands forward, a dark cosmic sphere forms and COMPRESSES
 *   age 50 .. 74   two white-blue beams burst out of the sphere and sweep together
 *   age 74 ..      they MERGE into one catastrophic beam (initial impact + continuous damage), for the configured duration
 *   then           the beam collapses (16 ticks of fade)
 * </pre>
 */
public class GammaBeamEntity extends Entity {
    public static final int CHARGE_END = 50;
    public static final int MERGE_END = 74;
    public static final int FADE_TICKS = 16;

    private static final UUID LOCK_ID = UUID.fromString("c7a2f815-9e31-4d6b-a0c4-6f8b20000071");

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GammaBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(GammaBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(GammaBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LEN = SynchedEntityData.defineId(GammaBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAXLEN = SynchedEntityData.defineId(GammaBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(GammaBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BEAM_TICKS = SynchedEntityData.defineId(GammaBeamEntity.class, EntityDataSerializers.INT);

    /** Casters that are inside the sequence right now (server). */
    private static final Set<UUID> CASTING = ConcurrentHashMap.newKeySet();
    /** Every live server-side sequence, so the light blocks can be cleaned up when the server stops. */
    private static final Set<GammaBeamEntity> LIVE = ConcurrentHashMap.newKeySet();

    /** Newest client game time at which one of these entities ticked (the camera effects skip their scan otherwise). */
    public static volatile long clientSeen = Long.MIN_VALUE;

    private static final class Hit {
        int count;
        long last = Long.MIN_VALUE;
    }

    // ----- server state
    private UUID ownerId;
    private boolean finished;
    private float yaw, pitch;
    private final Map<UUID, Hit> hits = new HashMap<>();
    private final Set<BlockPos> lights = new HashSet<>();
    private int beamTicks = 60;
    private int intervalTicks = 10;
    private int maxContinuous = 6;

    // ----- client interpolation
    private float oYaw, oPitch, oLen, curYaw, curPitch, curLen;

    public GammaBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static boolean isCasting(LivingEntity e) {
        return CASTING.contains(e.getUUID());
    }

    public static GammaBeamEntity create(Level level, LivingEntity caster) {
        GammaBeamEntity e = new GammaBeamEntity(ModEntities.GAMMA_BEAM.get(), level);
        e.ownerId = caster.getUUID();
        e.entityData.set(DATA_OWNER, caster.getId());
        e.yaw = caster.getYHeadRot();
        e.pitch = caster.getXRot();
        e.entityData.set(DATA_YAW, e.yaw);
        e.entityData.set(DATA_PITCH, e.pitch);
        var c = GarConfig.GAMMA;
        e.beamTicks = Math.max(1, (int) Math.round(c.durationSeconds.get() * 20.0));
        e.intervalTicks = Math.max(1, (int) Math.round(c.intervalSeconds.get() * 20.0));
        e.maxContinuous = Math.max(0, e.beamTicks / e.intervalTicks);
        e.entityData.set(DATA_BEAM_TICKS, e.beamTicks);
        e.entityData.set(DATA_MAXLEN, c.maxLength.get().floatValue());
        e.entityData.set(DATA_WIDTH, c.beamWidth.get().floatValue());
        e.entityData.set(DATA_LEN, c.maxLength.get().floatValue());
        Vec3 look = dirOf(e.yaw, e.pitch);
        Vec3 p = caster.getEyePosition().add(look.scale(1.25)).add(0, -0.30, 0);
        e.setPos(p.x, p.y, p.z);
        return e;
    }

    // ------------------------------------------------------------------ shared helpers (also used by the renderer)

    public static Vec3 dirOf(float yawDeg, float pitchDeg) {
        float yr = yawDeg * Mth.DEG_TO_RAD, pr = pitchDeg * Mth.DEG_TO_RAD;
        float cp = Mth.cos(pr);
        return new Vec3(-Mth.sin(yr) * cp, -Mth.sin(pr), Mth.cos(yr) * cp);
    }

    public Entity getOwnerEntity() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    public int ownerId() {
        return entityData.get(DATA_OWNER);
    }

    public int age() {
        return tickCount - 1;
    }

    public int beamTicks() {
        return entityData.get(DATA_BEAM_TICKS);
    }

    public float maxLength() {
        return entityData.get(DATA_MAXLEN);
    }

    public float beamWidth() {
        return entityData.get(DATA_WIDTH);
    }

    public int beamEndAge() {
        return MERGE_END + beamTicks();
    }

    /** Smoothed yaw / pitch / length for the renderer. */
    public float yawLerp(float pt) {
        return Mth.rotLerp(pt, oYaw, curYaw);
    }

    public float pitchLerp(float pt) {
        return Mth.lerp(pt, oPitch, curPitch);
    }

    public float lenLerp(float pt) {
        return Mth.lerp(pt, oLen, curLen);
    }

    public Vec3 dirLerp(float pt) {
        return dirOf(yawLerp(pt), pitchLerp(pt));
    }

    /** True while the damaging beam exists (also used for the screen effects). */
    public boolean beamActive(float age) {
        return age >= MERGE_END && age < beamEndAge() + FADE_TICKS;
    }

    /**
     * 0..1+ strength of the cinematic effects (shake, FOV warp) for the given age: a slow build while the sphere
     * compresses, a spike when the beams burst out, a roar while the beam is on and a quick fade.
     */
    public float cinematic(float age) {
        if (age < 0) {
            return 0;
        }
        if (age < CHARGE_END) {
            float p = age / CHARGE_END;
            return 0.10f + 0.45f * p * p;
        }
        if (age < MERGE_END) {
            return 0.65f + 0.35f * (age - CHARGE_END) / (MERGE_END - CHARGE_END);
        }
        float into = age - MERGE_END;
        float spike = Math.max(0f, 1f - into / 12f);
        float end = beamEndAge();
        if (age < end) {
            return 0.62f + 0.55f * spike;
        }
        return Math.max(0f, 0.62f * (1f - (age - end) / FADE_TICKS));
    }

    // ------------------------------------------------------------------ entity plumbing

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_OWNER, -1);
        entityData.define(DATA_YAW, 0f);
        entityData.define(DATA_PITCH, 0f);
        entityData.define(DATA_LEN, 80f);
        entityData.define(DATA_MAXLEN, 80f);
        entityData.define(DATA_WIDTH, 9f);
        entityData.define(DATA_BEAM_TICKS, 60);
    }

    private LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    private LivingEntity ownerAny() {
        if (ownerId != null && level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        LivingEntity o = owner();
        if (o == null) {
            discard();
            return;
        }
        int age = age();
        if (age > beamEndAge() + FADE_TICKS) {
            discard();
            return;
        }
        if (age < beamEndAge()) {
            CASTING.add(ownerId);
        }
        LIVE.add(this);

        // ---- aim: the beam follows the caster's look (limited turn rate once it has fired)
        float ty = o.getYHeadRot(), tp = o.getXRot();
        double follow = GarConfig.GAMMA.aimFollowDegPerTick.get();
        if (age < MERGE_END) {
            yaw = ty;
            pitch = tp;
        } else if (follow > 0 && age < beamEndAge()) {
            yaw += (float) Mth.clamp(Mth.wrapDegrees(ty - yaw), -follow, follow);
            pitch += (float) Mth.clamp(tp - pitch, -follow, follow);
        }
        entityData.set(DATA_YAW, yaw);
        entityData.set(DATA_PITCH, pitch);
        Vec3 dir = dirOf(yaw, pitch);
        Vec3 hand = o.getEyePosition().add(dir.scale(1.25)).add(0, -0.30, 0);
        setPos(hand.x, hand.y, hand.z);

        if (age < beamEndAge()) {
            lock(o);
        } else if (!finished) {
            releaseCaster();
        }

        sounds(age, hand);

        boolean beam = age >= MERGE_END && age < beamEndAge();
        double maxLen = GarConfig.GAMMA.maxLength.get();
        double len = maxLen;
        boolean blocked = false;
        if (age >= CHARGE_END - 6 && age < beamEndAge()) {
            BlockHitResult br = level().clip(new ClipContext(hand, hand.add(dir.scale(maxLen)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (br.getType() != HitResult.Type.MISS) {
                len = Math.max(2.0, br.getLocation().distanceTo(hand));
                blocked = true;
            }
        }
        entityData.set(DATA_LEN, (float) len);

        if (beam) {
            damage(o, age, hand, dir, len, blocked);
        }
        if ((age & 1) == 0 && GarConfig.G.lightBlocks.get()) {
            updateLights(age, hand, dir, len);
        }
        if (age == beamEndAge() && !lights.isEmpty()) {
            clearLights();
        }
    }

    // ------------------------------------------------------------------ server: lock, sounds, damage

    private void lock(LivingEntity o) {
        AttributeInstance sp = o.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null && sp.getModifier(LOCK_ID) == null) {
            sp.addTransientModifier(new AttributeModifier(LOCK_ID, "gamma_ray_burst_lock", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        // a huge negative jump boost (amplifier 128 wraps to -128 on the wire) = the caster cannot jump
        o.addEffect(new MobEffectInstance(MobEffects.JUMP, 4, 128, false, false, false));
        o.setSprinting(false);
        Vec3 m = o.getDeltaMovement();
        if (m.x * m.x + m.z * m.z > 1.0E-4) {
            o.setDeltaMovement(0, Math.min(m.y, 0), 0);
            o.hurtMarked = true;
        }
        o.fallDistance = 0;
    }

    private void releaseCaster() {
        if (finished) {
            return;
        }
        finished = true;
        LivingEntity o = ownerAny();
        if (ownerId != null) {
            CASTING.remove(ownerId);
        }
        if (o != null) {
            AttributeInstance sp = o.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) {
                sp.removeModifier(LOCK_ID);
            }
            MobEffectInstance j = o.getEffect(MobEffects.JUMP);
            if (j != null && j.getAmplifier() == 128) {
                o.removeEffect(MobEffects.JUMP);
            }
        }
    }

    private void sounds(int age, Vec3 p) {
        Level l = level();
        if (age == 1) {
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3.0f, 0.5f);
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.END_PORTAL_SPAWN, 1.2f, 1.6f);
        }
        if (age > 6 && age < CHARGE_END && age % 9 == 0) {
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.BEACON_AMBIENT, 3.0f, 0.5f + age / (float) CHARGE_END * 1.1f);
        }
        if (age == CHARGE_END) {
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.BEACON_POWER_SELECT, 3.5f, 0.5f);
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0f, 1.4f);
            if (l instanceof ServerLevel) {
                GarUtil.fx(l, ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        if (age == MERGE_END) {
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.WARDEN_SONIC_BOOM, 6.0f, 0.55f);
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_THUNDER, 8.0f, 0.5f);
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, 5.0f, 0.45f);
        }
        if (age > MERGE_END && age < beamEndAge() && age % 8 == 0) {
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.BEACON_AMBIENT, 5.0f, 0.5f);
            GarUtil.sound(l, p.x, p.y, p.z, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.5f);
        }
    }

    private void damage(LivingEntity o, int age, Vec3 hand, Vec3 dir, double len, boolean blocked) {
        var c = GarConfig.GAMMA;
        Level l = level();
        double r = c.beamWidth.get() * 0.5;
        Vec3 end = hand.add(dir.scale(len));
        long now = l.getGameTime();

        for (LivingEntity t : GarUtil.inBeam(l, o, hand, dir, len, r)) {
            Hit h = hits.computeIfAbsent(t.getUUID(), k -> new Hit());
            double pf = GarUtil.isBoss(t) ? c.bossPercentFactor.get() : 1.0;
            Vec3 at = t.getBoundingBox().getCenter();
            if (h.count == 0) {
                // initial beam impact (once per target per cast)
                h.count = 1;
                h.last = now;
                GarUtil.hurt(o, t, c.initialFlat.get(), c.initialPercent.get(), pf);
                GarUtil.push(t, dir, c.knockback.get() * 1.6, 0.35);
                GarUtil.explosion(l, o, at, 5.0, true);
            } else if (h.count - 1 < maxContinuous && now - h.last >= intervalTicks) {
                // continuous damage: never more often than the interval and never more than duration / interval times
                h.count++;
                h.last = now;
                GarUtil.hurt(o, t, c.tickFlat.get(), c.tickPercent.get(), pf);
                GarUtil.push(t, dir, c.knockback.get(), 0.15);
                GarUtil.explosion(l, o, at, 3.5, false);
            }
        }

        // secondary explosions where the beam meets the terrain
        if (blocked && (age - MERGE_END) % 4 == 0) {
            double j = 2.5;
            Vec3 p = end.add((l.random.nextDouble() - 0.5) * j, (l.random.nextDouble() - 0.5) * j * 0.6, (l.random.nextDouble() - 0.5) * j).subtract(dir.scale(0.6));
            GarUtil.explosion(l, o, p, 6.0, (age - MERGE_END) % 8 == 0);
        }
    }

    // ------------------------------------------------------------------ server: temporary light blocks

    private void updateLights(int age, Vec3 hand, Vec3 dir, double len) {
        Set<BlockPos> want = new HashSet<>();
        int lvl;
        if (age < CHARGE_END) {
            lvl = 4 + (int) (age / (float) CHARGE_END * 7f);
            want.add(BlockPos.containing(hand));
        } else if (age < beamEndAge()) {
            lvl = 15;
            want.add(BlockPos.containing(hand));
            double step = 4.0;
            double shown = age < MERGE_END ? 0 : len;
            for (double d = step; d <= shown; d += step) {
                want.add(BlockPos.containing(hand.add(dir.scale(d))));
            }
            if (shown > 0) {
                want.add(BlockPos.containing(hand.add(dir.scale(Math.max(0.5, shown - 1.5)))));
            }
        } else {
            clearLights();
            return;
        }
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Mth.clamp(lvl, 1, 15));
        // remove the lights that are no longer wanted
        for (BlockPos old : new HashSet<>(lights)) {
            if (!want.contains(old)) {
                removeLight(old);
            }
        }
        for (BlockPos p : want) {
            if (!level().isLoaded(p)) {
                continue;
            }
            BlockState cur = level().getBlockState(p);
            if (cur.isAir()) {
                level().setBlock(p, light, 2);
                lights.add(p);
            } else if (cur.is(Blocks.LIGHT) && lights.contains(p) && cur.getValue(LightBlock.LEVEL) != lvl) {
                level().setBlock(p, light, 2);
            }
        }
    }

    private void removeLight(BlockPos p) {
        lights.remove(p);
        if (level().isLoaded(p) && level().getBlockState(p).is(Blocks.LIGHT)) {
            level().setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private void clearLights() {
        for (BlockPos p : new HashSet<>(lights)) {
            removeLight(p);
        }
        lights.clear();
    }

    /** Called when the server is stopping so no light block is ever left behind. */
    public static void cleanupAll() {
        for (GammaBeamEntity e : LIVE) {
            try {
                e.clearLights();
                e.releaseCaster();
            } catch (Throwable ignored) {
                // best effort
            }
        }
        LIVE.clear();
        CASTING.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            clearLights();
            releaseCaster();
            LIVE.remove(this);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ client: particles

    private void clientTick() {
        clientSeen = level().getGameTime();
        oYaw = curYaw;
        oPitch = curPitch;
        oLen = curLen;
        if (tickCount <= 1) {
            curYaw = oYaw = entityData.get(DATA_YAW);
            curPitch = oPitch = entityData.get(DATA_PITCH);
            curLen = oLen = entityData.get(DATA_LEN);
        } else {
            curYaw = entityData.get(DATA_YAW);
            curPitch = entityData.get(DATA_PITCH);
            curLen = Mth.lerp(0.5f, curLen, entityData.get(DATA_LEN));
        }

        int age = age();
        var rnd = level().random;
        Vec3 c = position();
        Vec3 dir = dirOf(curYaw, curPitch);
        double dens = GarConfig.G.particleDensity.get();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(dir).normalize();

        if (age < CHARGE_END) {
            // everything is pulled into the sphere: dark dust and starlight stream inwards and speed up as it compresses
            int n = (int) Math.round((4 + age / 6.0) * dens);
            double rad = 5.5 - 3.0 * (age / (double) CHARGE_END);
            for (int i = 0; i < n; i++) {
                Vec3 off = randomUnit(rnd).scale(rad * (0.6 + rnd.nextDouble() * 0.6));
                Vec3 v = off.scale(-1.0 / 14.0);
                level().addParticle(i % 3 == 0 ? ModParticles.DUST.get() : (i % 3 == 1 ? ModParticles.STAR.get() : ModParticles.GLOW.get()),
                        c.x + off.x, c.y + off.y, c.z + off.z, v.x, v.y, v.z);
            }
            if (age > 20 && age % 3 == 0) {
                Vec3 off = randomUnit(rnd).scale(0.5);
                level().addParticle(ModParticles.SPARK.get(), c.x + off.x, c.y + off.y, c.z + off.z, off.x * 0.4, off.y * 0.4, off.z * 0.4);
            }
        } else if (age == CHARGE_END) {
            level().addParticle(ModParticles.BURST.get(), c.x, c.y, c.z, 0, 0, 0);
            level().addParticle(ModParticles.RING.get(), c.x, c.y, c.z, 0, 0, 0);
            for (int i = 0; i < 40 * dens; i++) {
                Vec3 v = randomUnit(rnd).scale(0.5 + rnd.nextDouble() * 0.7);
                level().addParticle(ModParticles.SPARK.get(), c.x, c.y, c.z, v.x, v.y, v.z);
            }
        } else if (age < MERGE_END) {
            // the two beams sweeping out: sparks and stars fly off both arcs
            for (int s = -1; s <= 1; s += 2) {
                for (int i = 0; i < 4 * dens; i++) {
                    double d = rnd.nextDouble() * 15.0;
                    double lat = s * 5.0 * Math.sin(Math.PI * d / 15.0);
                    Vec3 p = c.add(dir.scale(d)).add(right.scale(lat));
                    Vec3 v = dir.scale(0.6 + rnd.nextDouble()).add(right.scale(s * -0.1));
                    level().addParticle(rnd.nextBoolean() ? ModParticles.STAR.get() : ModParticles.SPARK.get(), p.x, p.y, p.z, v.x, v.y, v.z);
                }
            }
        } else if (age < beamEndAge() + FADE_TICKS) {
            float len = curLen;
            float w = beamWidth();
            float fade = age < beamEndAge() ? 1f : 1f - (age - beamEndAge()) / (float) FADE_TICKS;
            if (age == MERGE_END) {
                level().addParticle(ModParticles.BURST.get(), c.x, c.y, c.z, 0, 0, 0);
                level().addParticle(ModParticles.RING.get(), c.x, c.y, c.z, 0, 0, 0);
            }
            int n = (int) Math.round(16 * dens * fade);
            for (int i = 0; i < n; i++) {
                double d = rnd.nextDouble() * len;
                double ang = rnd.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(rnd.nextDouble()) * w * 0.5;
                Vec3 p = c.add(dir.scale(d)).add(right.scale(Math.cos(ang) * rr)).add(up.scale(Math.sin(ang) * rr));
                double sp = 1.2 + rnd.nextDouble() * 2.2;
                Vec3 v = dir.scale(sp).add(right.scale(-Math.sin(ang) * 0.12)).add(up.scale(Math.cos(ang) * 0.12));
                level().addParticle(i % 3 == 0 ? ModParticles.SPARK.get() : ModParticles.STAR.get(), p.x, p.y, p.z, v.x, v.y, v.z);
            }
            // the muzzle keeps spitting stars
            for (int i = 0; i < 3 * dens * fade; i++) {
                Vec3 v = dir.scale(0.8 + rnd.nextDouble() * 1.5).add(randomUnit(rnd).scale(0.35));
                level().addParticle(ModParticles.STAR.get(), c.x, c.y, c.z, v.x, v.y, v.z);
            }
            // impact on the terrain: fountain of sparks and starbursts
            if (len < maxLength() - 0.5f && age < beamEndAge()) {
                Vec3 e = c.add(dir.scale(len));
                if (age % 3 == 0) {
                    Vec3 j = e.add((rnd.nextDouble() - 0.5) * 3, (rnd.nextDouble() - 0.5) * 3, (rnd.nextDouble() - 0.5) * 3);
                    level().addParticle(ModParticles.BURST.get(), j.x, j.y, j.z, 0, 0, 0);
                }
                for (int i = 0; i < 10 * dens; i++) {
                    Vec3 v = randomUnit(rnd).scale(0.4 + rnd.nextDouble() * 0.9).subtract(dir.scale(0.5));
                    level().addParticle(ModParticles.SPARK.get(), e.x, e.y, e.z, v.x, v.y, v.z);
                }
                if (age % 5 == 0) {
                    level().addParticle(ModParticles.RING.get(), e.x - dir.x * 0.5, e.y - dir.y * 0.5, e.z - dir.z * 0.5, 0, 0, 0);
                }
            }
        }
    }

    private static Vec3 randomUnit(net.minecraft.util.RandomSource r) {
        double x, y, z, d;
        do {
            x = r.nextDouble() * 2 - 1;
            y = r.nextDouble() * 2 - 1;
            z = r.nextDouble() * 2 - 1;
            d = x * x + y * y + z * z;
        } while (d > 1.0 || d < 1.0E-4);
        double s = 1.0 / Math.sqrt(d);
        return new Vec3(x * s, y * s, z * s);
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
        return d < 320.0 * 320.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
