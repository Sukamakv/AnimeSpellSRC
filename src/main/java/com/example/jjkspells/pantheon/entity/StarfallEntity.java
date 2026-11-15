package com.example.jjkspells.pantheon.entity;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.registry.ModEntities;
import com.example.jjkspells.pantheon.registry.ModParticles;
import com.example.jjkspells.pantheon.registry.ModSounds;
import com.example.jjkspells.pantheon.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Starfall Execution - The Spear of War. Controller sitting on the landing point; one clock (age = tickCount - 1):
 * <pre>
 *  ascend    0..A          crouch, rune, pillar of light, vertical launch ~15 blocks
 *  suspend   A..A+S        hang in the air, halo behind, the landing circle appears
 *  dive      ..+D          straight at the landing point, accelerating (fire cone)
 *  impact    +0            central 100 + 10%, outer 55 + 5%, spearpoint stun and burn, centre launched upward
 *  aftershock +8 ..        five small explosions; the caster is free again
 *  flames    ..            celestial flames on the ground (visual)
 * </pre>
 */
public class StarfallEntity extends PanEntityBase {
    private static final EntityDataAccessor<BlockPos> DATA_START = SynchedEntityData.defineId(StarfallEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_A = SynchedEntityData.defineId(StarfallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_S = SynchedEntityData.defineId(StarfallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_D = SynchedEntityData.defineId(StarfallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_R = SynchedEntityData.defineId(StarfallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FLAME = SynchedEntityData.defineId(StarfallEntity.class, EntityDataSerializers.INT);

    /** Ticks from the impact until the caster is free again. */
    public static final int FREE_AFTER = 8;
    public static final int AFTERSHOCK_GAP = 3;

    private Vec3 landing = Vec3.ZERO, p0 = Vec3.ZERO, takeoff = Vec3.ZERO;
    private float flyYaw;
    private boolean impacted, released;
    private int impactAt = -1, shocksDone;
    private final Set<UUID> hitSet = new HashSet<>();

    public StarfallEntity(EntityType<? extends StarfallEntity> type, Level level) {
        super(type, level);
    }

    public static StarfallEntity create(Level level, LivingEntity caster) {
        var c = PanConfig.STAR;
        StarfallEntity e = new StarfallEntity(ModEntities.STARFALL.get(), level);
        e.setOwner(caster);
        Vec3 aim = Util.aimPoint(level, caster, c.range.get());
        Double gy = Util.findGround(level, aim.x, aim.y, aim.z);
        e.landing = gy != null ? new Vec3(aim.x, gy, aim.z) : aim;
        e.takeoff = caster.position();
        e.setPos(e.landing.x, e.landing.y, e.landing.z);
        e.entityData.set(DATA_START, BlockPos.containing(caster.position()));
        e.entityData.set(DATA_A, c.ascendTicks.get());
        e.entityData.set(DATA_S, c.suspendTicks.get());
        e.entityData.set(DATA_D, c.diveTicks.get());
        e.entityData.set(DATA_R, c.radius.get().floatValue());
        e.entityData.set(DATA_FLAME, c.flameTicks.get());
        return e;
    }

    public Vec3 getTakeoff() {
        BlockPos p = entityData.get(DATA_START);
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    public int getA() {
        return entityData.get(DATA_A);
    }

    public int getS() {
        return entityData.get(DATA_S);
    }

    public int getD() {
        return entityData.get(DATA_D);
    }

    public float getR() {
        return entityData.get(DATA_R);
    }

    public int getFlameTicks() {
        return entityData.get(DATA_FLAME);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_START, BlockPos.ZERO);
        entityData.define(DATA_A, 16);
        entityData.define(DATA_S, 14);
        entityData.define(DATA_D, 16);
        entityData.define(DATA_R, 9f);
        entityData.define(DATA_FLAME, 80);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.pantheon.client.ClientFx.tickStarfall(this));
            return;
        }
        LivingEntity owner = owner();
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        var c = PanConfig.STAR;
        int age = tickCount - 1;
        int A = c.ascendTicks.get(), S = c.suspendTicks.get(), D = c.diveTicks.get();
        int tDive = A + S, tHit = A + S + D;

        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.reduce(owner, c.diveReduction.get() / 100.0, tHit + 40);
            Util.FALL_IMMUNE.put(owner.getUUID(), level().getGameTime() + tHit + 200);
            AttributeInstance kb = owner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(KB_ID);
                kb.addTransientModifier(new AttributeModifier(KB_ID, "pantheon_star_kb", 1.0, AttributeModifier.Operation.ADDITION));
            }
            owner.setNoGravity(true);
            takeoff = owner.position();
            flyYaw = Util.yawTo(takeoff, landing);
            Util.sound(level(), takeoff.x, takeoff.y, takeoff.z, ModSounds.BEAM.get(), 1.0f, 1.3f);
            Util.sound(level(), takeoff.x, takeoff.y, takeoff.z, ModSounds.FLAME.get(), 1.0f, 1.0f);
            Util.fxDir(level(), ModParticles.RING.get(), takeoff.x, takeoff.y + 0.1, takeoff.z, 0, 0, 0);
            Util.column(level(), takeoff.x, takeoff.y, takeoff.z, 12, 40);
        }

        if (age < A) {
            // vertical launch along a golden pillar
            double f = (age + 1) / (double) A;
            double e = 1.0 - (1.0 - f) * (1.0 - f);
            Vec3 p = takeoff.add(0, c.riseBlocks.get() * e, 0);
            Util.moveTo(owner, p, owner.getYRot(), -20f);
            for (int i = 0; i < 4; i++) {
                double a = (age * 0.6 + i * Math.PI / 2);
                Util.fxDir(level(), ModParticles.FLAME.get(), owner.getX() + Math.cos(a) * 0.9, owner.getY() + 0.2 + i * 0.3, owner.getZ() + Math.sin(a) * 0.9, 0, 0.08, 0);
            }
            Util.fx(level(), ModParticles.SPARK.get(), owner.getX(), owner.getY() + 0.5, owner.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
        } else if (age < tDive) {
            if (age == A) {
                p0 = owner.position();
                Util.sound(level(), p0.x, p0.y, p0.z, ModSounds.STAR.get(), 1.0f, 1.0f);
                Util.fx(level(), ModParticles.FLASH.get(), p0.x, p0.y + 1, p0.z, 1, 0, 0, 0, 0);
            }
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
            float yaw = Util.yawTo(owner.position(), landing);
            if (age % 2 == 0) {
                Util.moveTo(owner, owner.position(), yaw, 35f);
            }
            Util.fx(level(), ModParticles.FLAME.get(), owner.getX(), owner.getY() + 0.3, owner.getZ(), 2, 0.25, 0.1, 0.25, 0.01);
            Util.fx(level(), ModParticles.SPARK.get(), owner.getX(), owner.getY() + 1.2, owner.getZ(), 2, 0.5, 0.6, 0.5, 0.03);
            if (age == tDive - 4) {
                Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.DIVE.get(), 1.4f, 1.0f);
            }
        } else if (age < tHit) {
            if (age == tDive) {
                p0 = owner.position();
            }
            double f = (age - tDive + 1) / (double) D;
            double e = f * f * (0.4 + 0.6 * f);
            Vec3 p = p0.lerp(landing.add(0, 0.05, 0), Math.min(1.0, e));
            Vec3 dir = landing.subtract(p0);
            float yaw = Util.yawTo(p0, landing);
            float pitch = (float) (-Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG);
            if (!Util.moveTo(owner, p, yaw, Mth.clamp(pitch, -80f, 80f))) {
                // something in the way: go straight to the landing point at the end instead
                if (age == tHit - 1) {
                    Util.snapTo(owner, landing.add(0, 0.05, 0), yaw, 60f);
                }
            }
            for (int i = 0; i < 6; i++) {
                Vec3 q = owner.position().add(-dir.normalize().x * i * 0.5, 1.0 - dir.normalize().y * i * 0.5, -dir.normalize().z * i * 0.5);
                Util.fx(level(), ModParticles.FLAME.get(), q.x, q.y, q.z, 2, 0.3 + i * 0.06, 0.3 + i * 0.06, 0.3 + i * 0.06, 0.02);
                Util.fx(level(), ModParticles.SPARK.get(), q.x, q.y, q.z, 1, 0.3, 0.3, 0.3, 0.1);
            }
        }
        if (age == tHit) {
            impactAt = age;
            impact(owner);
        }
        if (impacted) {
            int since = age - impactAt;
            if (since < 8) {
                Util.column(level(), landing.x, landing.y, landing.z, 14 - since, 18);
            }
            if (!released && since >= FREE_AFTER) {
                release(owner);
            }
            int shock0 = FREE_AFTER;
            if (since >= shock0 && shocksDone < c.aftershocks.get() && (since - shock0) % AFTERSHOCK_GAP == 0) {
                aftershock(owner, shocksDone);
                shocksDone++;
            }
            if (since >= shock0 + c.aftershocks.get() * AFTERSHOCK_GAP + c.flameTicks.get()) {
                finish(true);
                discard();
            } else if (since > 14 && since % 4 == 0 && since < shock0 + c.aftershocks.get() * AFTERSHOCK_GAP + c.flameTicks.get() - 10) {
                // the flames on the ground
                double r = c.radius.get() * 0.8;
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                    Util.fxDir(level(), ModParticles.FLAME.get(), landing.x + Math.cos(a) * d, landing.y + 0.1, landing.z + Math.sin(a) * d, 0, 0.03 + random.nextDouble() * 0.04, 0);
                }
            }
        }
    }

    private void impact(LivingEntity owner) {
        var c = PanConfig.STAR;
        impacted = true;
        Util.snapTo(owner, landing.add(0, 0.02, 0), owner.getYRot(), 20f);
        owner.setNoGravity(false);
        Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.BOOM.get(), 2.2f, 0.9f);
        Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.DIVINE.get(), 1.6f, 1.0f);
        Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.BEAM.get(), 1.2f, 1.0f);
        Util.blast(level(), landing.x, landing.y, landing.z, 6.0, 60);
        Util.fxDir(level(), ModParticles.RING.get(), landing.x, landing.y + 0.1, landing.z, 0, 0, 0);
        Util.ring(level(), ModParticles.SPARK.get(), landing.add(0, 0.2, 0), 48, 0.9, 0.05);
        Util.ring(level(), ModParticles.FLAME.get(), landing.add(0, 0.2, 0), 36, 0.55, 0.04);
        Util.ring(level(), ModParticles.DUST.get(), landing.add(0, 0.2, 0), 24, 0.7, 0.03);
        for (int i = 0; i < 14; i++) {
            Util.fxDir(level(), ModParticles.SHARD.get(), landing.x + (random.nextDouble() - 0.5) * 3, landing.y + 0.2, landing.z + (random.nextDouble() - 0.5) * 3,
                    (random.nextDouble() - 0.5) * 0.5, 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.5);
        }
        Util.column(level(), landing.x, landing.y, landing.z, 14, 120);
        for (LivingEntity e : Util.enemies(level(), landing, c.radius.get(), owner)) {
            double d = Math.hypot(e.getX() - landing.x, e.getZ() - landing.z);
            if (!hitSet.add(e.getUUID())) {
                continue;
            }
            if (d <= c.coreRadius.get()) {
                boolean spearpoint = d <= c.spearpointRadius.get();
                double mult = spearpoint ? 1.0 + c.spearpointBonusPercent.get() / 100.0 : 1.0;
                Util.hurt(owner, e, c.coreFlat.get(), c.corePercent.get(), mult, 1.0);
                if (e.isAlive()) {
                    Util.launch(e, c.launchSpeed.get());
                    if (spearpoint) {
                        Util.stun(e, c.stunTicks.get());
                        Util.burn(owner, e, c.burnSeconds.get(), c.burnDamage.get());
                    }
                }
            } else {
                Util.hurt(owner, e, c.outerFlat.get(), c.outerPercent.get());
                if (e.isAlive()) {
                    Util.knock(e, landing, 3.0, 0.2);
                }
            }
        }
    }

    private void aftershock(LivingEntity owner, int i) {
        var c = PanConfig.STAR;
        double a = (i / (double) Math.max(1, c.aftershocks.get())) * Math.PI * 2 + random.nextDouble() * 0.6;
        double d = 3.0 + random.nextDouble() * 3.5;
        Vec3 p = new Vec3(landing.x + Math.cos(a) * d, landing.y, landing.z + Math.sin(a) * d);
        Double g = Util.findGround(level(), p.x, p.y + 1, p.z);
        if (g != null) {
            p = new Vec3(p.x, g, p.z);
        }
        Util.blast(level(), p.x, p.y, p.z, c.afterRadius.get() * 1.6, 22);
        Util.fxDir(level(), ModParticles.RING.get(), p.x, p.y + 0.1, p.z, 0, 0, 0);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 0.9f, 1.4f + random.nextFloat() * 0.2f);
        for (LivingEntity e : Util.enemies(level(), p, c.afterRadius.get(), owner)) {
            Util.hurt(owner, e, c.afterFlat.get(), 0);
        }
    }

    private void release(LivingEntity owner) {
        released = true;
        Util.LOCKED.remove(owner.getUUID());
        owner.setNoGravity(false);
        Util.clearReduce(owner);
        AttributeInstance kb = owner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(KB_ID);
        }
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            owner.setNoGravity(false);
            Util.clearReduce(owner);
            AttributeInstance kb = owner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(KB_ID);
            }
            if (!normal && !impacted && !owner.level().noCollision(owner, owner.getBoundingBox())) {
                // interrupted in the air: never leave the caster inside a wall
                Vec3 safe = Util.safeNear(owner.level(), owner, owner.position(), 1.0);
                if (safe != null) {
                    Util.snapTo(owner, safe, owner.getYRot(), owner.getXRot());
                }
            }
        }
    }
}
