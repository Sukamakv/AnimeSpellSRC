package com.example.jjkspells.zegion.entity;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.registry.ModEntities;
import com.example.jjkspells.zegion.registry.ModParticles;
import com.example.jjkspells.zegion.registry.ModSounds;
import com.example.jjkspells.zegion.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Kaiser's Impact - The Fist That Shatters Heaven. Sits at the point of impact; one clock (age = tickCount - 1):
 * <pre>
 *  0..STANCE_END      the Kaiser stance (caster locked, energy gathers around the fist)
 *  COMPRESS..BLINK    absolute compression
 *  BLINK              Zegion vanishes and a violet afterimage stays behind
 *  PUNCH              Zegion is in front of the target: the direct punch
 *  PUNCH+2..SHOCK_END the heaven-shattering shockwave grows to its full radius
 *  SHOCK_END..COLLAPSE the spatial fractures hang and widen
 *  COLLAPSE           the rupture collapses
 *  END                Zegion is composed again
 * </pre>
 */
public class ImpactEntity extends ZegEntityBase {
    public static final int COMPRESS = 14, BLINK = 26, PUNCH = 32, SHOCK_END = 64, COLLAPSE = 90, END = 110;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(ImpactEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_OX = SynchedEntityData.defineId(ImpactEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OY = SynchedEntityData.defineId(ImpactEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OZ = SynchedEntityData.defineId(ImpactEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(ImpactEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BOSS = SynchedEntityData.defineId(ImpactEntity.class, EntityDataSerializers.BOOLEAN);

    private final Set<UUID> hitShock = new HashSet<>();
    private LivingEntity target;
    private Vec3 dest = Vec3.ZERO;
    private boolean bossHit;

    public ImpactEntity(EntityType<? extends ImpactEntity> type, Level level) {
        super(type, level);
    }

    public static ImpactEntity create(Level level, LivingEntity caster) {
        var c = ZegConfig.IMPACT;
        ImpactEntity e = new ImpactEntity(ModEntities.IMPACT.get(), level);
        e.setOwner(caster);
        double range = c.range.get();
        LivingEntity t = Util.aimedEnemy(level, caster, range);
        Vec3 aim = Util.aimPoint(level, caster, range);
        if (t == null) {
            t = Util.nearest(level, aim, 8.0, caster);
        }
        Vec3 at = t != null ? t.position() : aim;
        e.target = t;
        Vec3 from = caster.position();
        Vec3 dir = new Vec3(at.x - from.x, 0, at.z - from.z);
        dir = dir.lengthSqr() < 1.0E-4 ? caster.getLookAngle().multiply(1, 0, 1).normalize() : dir.normalize();
        double stand = t != null ? 1.8 + t.getBbWidth() * 0.5 : 1.8;
        e.dest = new Vec3(at.x - dir.x * stand, at.y, at.z - dir.z * stand);
        e.setPos(at.x, at.y, at.z);
        e.entityData.set(DATA_OX, (float) from.x);
        e.entityData.set(DATA_OY, (float) from.y);
        e.entityData.set(DATA_OZ, (float) from.z);
        e.entityData.set(DATA_YAW, Util.yawTo(from, at));
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        return e;
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    /** Where Zegion stood when the spell began (absolute). */
    public Vec3 getOrigin() {
        return new Vec3(entityData.get(DATA_OX), entityData.get(DATA_OY), entityData.get(DATA_OZ));
    }

    public float getPunchYaw() {
        return entityData.get(DATA_YAW);
    }

    public boolean isBossHit() {
        return entityData.get(DATA_BOSS);
    }

    public float getRuptureRadius() {
        return (float) (ZegConfig.IMPACT.ruptureRadius.get() + (isBossHit() ? ZegConfig.IMPACT.bossRuptureBonusBlocks.get() : 0.0));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_OX, 0f);
        entityData.define(DATA_OY, 0f);
        entityData.define(DATA_OZ, 0f);
        entityData.define(DATA_YAW, 0f);
        entityData.define(DATA_BOSS, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickImpact(this));
            return;
        }
        var c = ZegConfig.IMPACT;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        Vec3 at = position();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.HUM.get(), 1.4f, 0.9f);
        }
        if (age < BLINK) {
            // the stance: black particles and purple energy converge on the fist
            if (age % 2 == 0) {
                Vec3 p = owner.position();
                double a = age * 0.7;
                Util.fx(level(), ModParticles.SPARK.get(), p.x + Math.cos(a) * 1.4, p.y + 1.1, p.z + Math.sin(a) * 1.4, 2, 0.2, 0.3, 0.2, 0.0);
                Util.fx(level(), ModParticles.VOID.get(), p.x, p.y + 1.2, p.z, 1, 0.6, 0.6, 0.6, 0.01);
                Util.fx(level(), ModParticles.CRACK.get(), p.x, p.y + 0.1, p.z, 1, 0.9, 0.05, 0.9, 0.0);
            }
            if (age == COMPRESS) {
                Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.CHARGE.get(), 1.4f, 1.0f);
            }
            if (age >= COMPRESS && age % 2 == 0) {
                Vec3 p = owner.position();
                Util.fx(level(), ModParticles.STAR.get(), p.x, p.y + 1.2, p.z, 2, 1.2, 0.8, 1.2, 0.0);
                Util.ring(level(), ModParticles.RING.get(), p.add(0, 0.1, 0), 1, 0.0, 0.0);
            }
        }
        if (age >= 20 && age <= PUNCH + 10) {
            Util.reduce(owner, c.reductionPercent.get() / 100.0, 4);
            Util.kbImmune(owner, 4);
            Util.ccImmune(owner, 4);
        }
        if (age == BLINK) {
            blink(owner);
        }
        if (age == PUNCH) {
            punch(owner, at);
        }
        if (age > PUNCH && age <= SHOCK_END) {
            shock(owner, at, age);
        }
        if (age > SHOCK_END && age < COLLAPSE && age % 3 == 0) {
            double r = getRuptureRadius();
            double a = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * r;
            double x = at.x + Math.cos(a) * rr, z = at.z + Math.sin(a) * rr;
            Util.tear(level(), x, at.y + 1 + random.nextDouble() * 6, z, 0.8);
            Util.fx(level(), ModParticles.SPARK.get(), at.x, at.y + 0.5, at.z, 3, r * 0.3, 1.5, r * 0.3, 0.15);
            if (age % 6 == 0) {
                Util.sound(level(), at.x, at.y, at.z, ModSounds.RIFT.get(), 0.9f, 0.8f + random.nextFloat() * 0.5f);
            }
        }
        if (age == COLLAPSE) {
            collapse(owner, at);
        }
        if (age == COLLAPSE + 8) {
            Util.swing(owner);
        }
        if (age == END) {
            finish(true);
            discard();
        }
    }

    private void blink(LivingEntity owner) {
        Vec3 from = owner.position();
        Util.sound(level(), from.x, from.y, from.z, ModSounds.BLINK.get(), 1.6f, 0.9f);
        Util.blast(level(), from.x, from.y + 0.2, from.z, 2.0, 24);
        Util.arc(level(), ModParticles.VOID.get(), from.add(0, 1, 0), dest.add(0, 1, 0), 0.3);
        Util.arc(level(), ModParticles.SPARK.get(), from.add(0, 1, 0), dest.add(0, 1, 0), 0.5);
        Vec3 at = position();
        float yaw = Util.yawTo(dest, at);
        if (!Util.moveTo(owner, dest, yaw, 0f)) {
            Vec3 alt = Util.safeNear(level(), owner, at, 2.2);
            if (alt != null) {
                Util.moveTo(owner, alt, Util.yawTo(alt, at), 0f);
            }
        }
        Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.BLINK.get(), 1.6f, 1.3f);
    }

    private void punch(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.IMPACT;
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.PUNCH.get(), 2.2f, 0.8f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 1.8f, 0.8f);
        Util.blast(level(), at.x, at.y + 0.5, at.z, 4.0, 70);
        Util.fx(level(), ModParticles.CRACK.get(), at.x, at.y + 1.2, at.z, 10, 2.5, 2.0, 2.5, 0.0);
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 e = at.add(Math.cos(a) * (5 + random.nextDouble() * 6), 2 + random.nextDouble() * 6, Math.sin(a) * (5 + random.nextDouble() * 6));
            Util.arc(level(), ModParticles.SPARK.get(), at.add(0, 1.2, 0), e, 1.2);
        }
        for (LivingEntity e : Util.enemies(level(), at, 3.2, owner)) {
            if (Util.isBoss(e)) {
                bossHit = true;
            }
            Util.hurt(owner, e, c.punchFlat.get(), c.punchPercent.get(), 1.0, c.armorIgnorePercent.get() / 100.0);
            if (e.isAlive()) {
                Util.fracture(e, c.fractureSeconds.get());
                Util.stagger(e, ZegConfig.G.staggerTicks.get());
            }
        }
        entityData.set(DATA_BOSS, bossHit);
    }

    private void shock(LivingEntity owner, Vec3 at, int age) {
        var c = ZegConfig.IMPACT;
        double k = (age - PUNCH) / (double) (SHOCK_END - PUNCH);
        double r = 2.0 + (c.shockRadius.get() - 2.0) * k;
        double mult = bossHit ? 1.0 + c.bossShockBonusPercent.get() / 100.0 : 1.0;
        for (LivingEntity e : Util.enemies(level(), at, r, owner)) {
            if (hitShock.add(e.getUUID())) {
                Util.hurt(owner, e, c.shockFlat.get(), c.shockPercent.get(), mult, 0.0);
                if (e.isAlive()) {
                    Util.knock(e, at, 9.0, 0.35);
                    Util.stagger(e, ZegConfig.G.staggerTicks.get());
                }
            }
        }
        int n = 20 + (int) (r);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + age * 0.15;
            double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
            Util.fxDir(level(), ModParticles.SHARD.get(), x, at.y + 0.3, z, Math.cos(a) * 0.12, 0.22, Math.sin(a) * 0.12);
            if (i % 3 == 0) {
                Util.fxDir(level(), ModParticles.MIRROR.get(), x, at.y + 0.6, z, Math.cos(a) * 0.08, 0.25, Math.sin(a) * 0.08);
            }
        }
        if (age % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            Util.arc(level(), ModParticles.SPARK.get(), at.add(0, 0.5, 0), at.add(Math.cos(a) * r, 0.5 + random.nextDouble() * 3, Math.sin(a) * r), 1.5);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 0.8f, 0.7f + (float) k * 0.5f);
        }
    }

    private void collapse(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.IMPACT;
        double r = getRuptureRadius();
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 2.4f, 0.8f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 2.0f, 0.6f);
        Util.blast(level(), at.x, at.y + 1.0, at.z, r * 0.5, 110);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.4, 0), 36, 0.8, 0.04);
        Util.ring(level(), ModParticles.SHARD.get(), at.add(0, 0.6, 0), 30, 0.7, 0.2);
        for (LivingEntity e : Util.enemies(level(), at, r, owner)) {
            Util.hurt(owner, e, c.ruptureFlat.get(), c.rupturePercent.get());
            if (e.isAlive()) {
                Util.launchBlocks(e, 2.0);
            }
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
    }
}
