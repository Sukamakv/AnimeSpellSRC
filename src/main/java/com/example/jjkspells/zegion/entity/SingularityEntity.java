package com.example.jjkspells.zegion.entity;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.registry.ModEffects;
import com.example.jjkspells.zegion.registry.ModEntities;
import com.example.jjkspells.zegion.registry.ModParticles;
import com.example.jjkspells.zegion.registry.ModSounds;
import com.example.jjkspells.zegion.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Void Carapace - The Devouring Singularity. The entity IS the singularity; one clock (age = tickCount - 1):
 * <pre>
 *  0..COMPRESS      the black sphere forms above Zegion's palm
 *  COMPRESS..THROW  it is compressed (caster locked)
 *  THROW..IMPACT    it flies to the target
 *  IMPACT           gravitational rupture
 *  IMPACT..COLLAPSE the devouring void: pull, slow and 45 damage per second
 *  COLLAPSE..HIT    it shrinks into a point
 *  HIT              the void collapse
 *  END
 * </pre>
 */
public class SingularityEntity extends ZegEntityBase {
    public static final int COMPRESS = 20, THROW = 40, IMPACT = 60, DEVOUR = 90, COLLAPSE = 140, HIT = 170, END = 182;

    private static final EntityDataAccessor<Float> DATA_TX = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TY = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TZ = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);

    private Vec3 target = Vec3.ZERO;

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level);
    }

    public static SingularityEntity create(Level level, LivingEntity caster) {
        var c = ZegConfig.SING;
        SingularityEntity e = new SingularityEntity(ModEntities.SINGULARITY.get(), level);
        e.setOwner(caster);
        Vec3 aim = Util.aimPoint(level, caster, c.range.get());
        Double g = Util.findGround(level, aim.x, aim.y, aim.z);
        double gy = g != null ? g : aim.y;
        e.target = new Vec3(aim.x, gy + 5.0, aim.z);
        e.entityData.set(DATA_TX, (float) e.target.x);
        e.entityData.set(DATA_TY, (float) e.target.y);
        e.entityData.set(DATA_TZ, (float) e.target.z);
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        Vec3 h = hand(caster);
        e.setPos(h.x, h.y, h.z);
        return e;
    }

    public static Vec3 hand(LivingEntity c) {
        Vec3 look = c.getLookAngle();
        return new Vec3(c.getX() + look.x * 1.2, c.getY() + 1.9, c.getZ() + look.z * 1.2);
    }

    public Vec3 getTarget() {
        return new Vec3(entityData.get(DATA_TX), entityData.get(DATA_TY), entityData.get(DATA_TZ));
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TX, 0f);
        entityData.define(DATA_TY, 0f);
        entityData.define(DATA_TZ, 0f);
        entityData.define(DATA_SEED, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickSingularity(this));
            return;
        }
        var c = ZegConfig.SING;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        double R = c.radius.get();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.VOID.get(), 1.6f, 1.2f);
        }
        if (age < THROW) {
            Vec3 h = hand(owner);
            setPos(h.x, h.y, h.z);
            if (age % 2 == 0) {
                Util.fx(level(), ModParticles.SPARK.get(), h.x, h.y, h.z, 2, 0.8, 0.8, 0.8, 0.0);
                Util.fx(level(), ModParticles.VOID.get(), h.x, h.y, h.z, 1, 0.3, 0.3, 0.3, 0.0);
            }
            if (age == COMPRESS) {
                Util.sound(level(), h.x, h.y, h.z, ModSounds.CHARGE.get(), 1.8f, 0.7f);
            }
        }
        if (age == THROW) {
            Util.swing(owner);
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.WHOOSH.get(), 2.0f, 0.5f);
        }
        if (age >= THROW && age < IMPACT) {
            double f = (age - THROW) / (double) (IMPACT - THROW);
            double e2 = f * f * (3 - 2 * f);
            Vec3 h = hand(owner);
            Vec3 p = h.lerp(target, e2).add(0, Math.sin(f * Math.PI) * 2.0, 0);
            setPos(p.x, p.y, p.z);
            Util.fx(level(), ModParticles.VOID.get(), p.x, p.y, p.z, 2, 0.4, 0.4, 0.4, 0.0);
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y, p.z, 3, 0.4, 0.4, 0.4, 0.05);
            if (age % 3 == 0) {
                Util.tear(level(), p.x, p.y, p.z, 0.5);
            }
        }
        if (age == IMPACT - 20) {
            Util.LOCKED.remove(owner.getUUID());
        }
        if (age == IMPACT) {
            setPos(target.x, target.y, target.z);
            Util.sound(level(), target.x, target.y, target.z, ModSounds.BOOM.get(), 3.0f, 0.6f);
            Util.sound(level(), target.x, target.y, target.z, ModSounds.VOID.get(), 3.0f, 0.8f);
            Util.blast(level(), target.x, target.y, target.z, 8, 100);
            Util.ring(level(), ModParticles.VOID.get(), target, 30, 1.0, 0.0);
            for (LivingEntity e : Util.enemies(level(), target, R, owner)) {
                Util.hurt(owner, e, c.openFlat.get(), c.openPercent.get());
            }
        }
        if (age >= IMPACT && age < HIT) {
            gravity(owner, age, R);
        }
        if (age == HIT) {
            collapse(owner);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private void gravity(LivingEntity owner, int age, double R) {
        var c = ZegConfig.SING;
        Vec3 at = position();
        boolean collapsing = age >= COLLAPSE;
        double reach = collapsing ? c.finalRadius.get() : R;
        double k = Math.min(1.0, (age - IMPACT) / 60.0);
        double strength = c.pullStrength.get() * (0.5 + k) * (collapsing ? 1.7 : 1.0);
        if (collapsing && age % 2 == 0) {
            double t = (age - COLLAPSE) / (double) (HIT - COLLAPSE);
            double r = R * (1.0 - t * 0.9);
            for (int i = 0; i < 12; i++) {
                double a = random.nextDouble() * Math.PI * 2, rr = r * (0.6 + random.nextDouble() * 0.6);
                Util.fxDir(level(), ModParticles.MIST.get(), at.x + Math.cos(a) * rr, at.y + (random.nextDouble() - 0.5) * 8, at.z + Math.sin(a) * rr, -Math.cos(a) * 0.5, 0, -Math.sin(a) * 0.5);
            }
        }
        List<LivingEntity> in = Util.enemies(level(), at, reach, owner);
        for (LivingEntity e : in) {
            double d = e.position().distanceTo(at);
            double edge = Math.max(0.35, d / reach);
            Util.pull(e, at, strength * (0.4 + edge));
            if (age % 10 == 0) {
                Util.slowPct(e, c.slowPercent.get(), 16);
                Util.put(Util.OUT_DOWN, e, c.outDownPercent.get() / 100.0, 16);
                e.addEffect(new MobEffectInstance(ModEffects.GRAVITY.get(), 24, 0, false, false, true));
            }
        }
        if (age % 20 == 0 && age < COLLAPSE + 10) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.VOID.get(), 2.0f, 0.9f);
            for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
                Util.hurt(owner, e, c.dps.get(), 0);
                Vec3 ep = e.position().add(0, e.getBbHeight() * 0.5, 0);
                Util.arc(level(), ModParticles.SPARK.get(), at, ep, 1.0);
            }
        }
        if (age % 4 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), at.x, at.y, at.z, 6, 6, 6, 6, 0.1);
            double a = random.nextDouble() * Math.PI * 2;
            Util.arc(level(), ModParticles.SPARK.get(), at, at.add(Math.cos(a) * R * 0.8, (random.nextDouble() - 0.5) * 12, Math.sin(a) * R * 0.8), 1.5);
        }
    }

    private void collapse(LivingEntity owner) {
        var c = ZegConfig.SING;
        Vec3 at = position();
        List<LivingEntity> in = Util.enemies(level(), at, c.radius.get(), owner);
        double bonus = Math.min(c.maxBonusPercent.get(), in.size() * c.perEnemyBonusPercent.get()) / 100.0;
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 3.2f, 0.5f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 2.6f, 0.5f);
        Util.blast(level(), at.x, at.y, at.z, c.finalRadius.get() * 0.4, 160);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, -2, 0), 48, 1.6, 0.05);
        Util.ring(level(), ModParticles.SHARD.get(), at, 40, 1.2, 0.1);
        for (LivingEntity e : Util.enemies(level(), at, c.finalRadius.get(), owner)) {
            Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get(), 1.0 + bonus, c.ignoreArmorPercent.get() / 100.0);
            if (e.isAlive()) {
                Util.knock(e, at, 10.0, 0.35);
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
