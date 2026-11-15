package com.example.jjkspells.malphite.entity;

import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.registry.ModEffects;
import com.example.jjkspells.malphite.registry.ModEntities;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thunderclap - Crushing Earth. Sits where the caster stood; one clock (age = tickCount - 1):
 * <pre>
 *  0..T1        gathering seismic pressure (the caster is locked)
 *  T1           first slam: radius R, damage, stagger, Crippling Tremor
 *  W0..W1       the shockwave grows from R to the outer radius and hurts what it reaches
 *  W1..T2       compression
 *  T2           the final eruption (stone pillars, knock-up) and the start of the Earth Fracture zone
 *  T_END        the caster is free again; the zone lasts on its own
 * </pre>
 */
public class ClapEntity extends MalEntityBase {
    public static final int T1 = 12, W0 = 22, W1 = 38, T2 = 48, T_END = 60, ERUPT_LIFE = 14;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(ClapEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RES = SynchedEntityData.defineId(ClapEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ZONE = SynchedEntityData.defineId(ClapEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EAGE = SynchedEntityData.defineId(ClapEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_EX = SynchedEntityData.defineId(ClapEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_EZ = SynchedEntityData.defineId(ClapEntity.class, EntityDataSerializers.FLOAT);

    private final Set<UUID> hit1 = new HashSet<>(), hit2 = new HashSet<>();
    private boolean resonant;
    private Vec3 anchor;

    public ClapEntity(EntityType<? extends ClapEntity> type, Level level) {
        super(type, level);
    }

    public static ClapEntity create(Level level, LivingEntity caster) {
        ClapEntity e = new ClapEntity(ModEntities.CLAP.get(), level);
        e.setOwner(caster);
        e.anchor = caster.position();
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        return e;
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    /** True once the first slam hit enough enemies for Resonating Impact. */
    public boolean isResonant() {
        return entityData.get(DATA_RES);
    }

    /** Ticks the zone lasts after the final eruption (0 until then). */
    public int getZoneTicks() {
        return entityData.get(DATA_ZONE);
    }

    public float getZoneRadius() {
        return ((Number) (isResonant() ? MalConfig.CLAP.resonanceZoneRadius.get() : MalConfig.CLAP.radius.get())).floatValue();
    }

    /** Age of the latest zone eruption or -1, and where it was (absolute coordinates). */
    public int getEruptAge() {
        return entityData.get(DATA_EAGE);
    }

    public Vec3 getEruptPos() {
        return new Vec3(entityData.get(DATA_EX), getY(), entityData.get(DATA_EZ));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_RES, false);
        entityData.define(DATA_ZONE, 0);
        entityData.define(DATA_EAGE, -1);
        entityData.define(DATA_EX, 0f);
        entityData.define(DATA_EZ, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malphite.client.ClientFx.tickClap(this));
            return;
        }
        var c = MalConfig.CLAP;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        Vec3 at = anchor != null ? anchor : position();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), at.x, at.y, at.z, ModSounds.RUMBLE.get(), 1.4f, 1.0f);
        }
        if (age < T1 && age % 2 == 0) {
            // stones rise around the fists, the ground shakes
            double a = age * 0.9;
            Util.fx(level(), ModParticles.ROCK.get(), at.x + Math.cos(a) * 1.1, at.y + 0.3, at.z + Math.sin(a) * 1.1, 2, 0.4, 0.2, 0.4, 0.05);
            Util.fx(level(), ModParticles.DUST.get(), at.x, at.y + 0.2, at.z, 2, 1.0, 0.05, 1.0, 0.02);
            Util.fx(level(), ModParticles.EMBER.get(), at.x, at.y + 1.0, at.z, 2, 0.6, 0.5, 0.6, 0.03);
        }
        if (age == T1) {
            slam1(owner, at);
        }
        if (age >= W0 && age <= W1) {
            wave(owner, at, age);
        }
        if (age > W1 && age < T2 && age % 2 == 0) {
            double a = age * 0.7;
            Util.fx(level(), ModParticles.ROCK.get(), at.x + Math.cos(a) * 1.6, at.y + 0.5, at.z + Math.sin(a) * 1.6, 2, 0.5, 0.6, 0.5, 0.04);
            Util.fx(level(), ModParticles.SPARK.get(), at.x, at.y + 0.6, at.z, 3, 0.9, 0.4, 0.9, 0.02);
        }
        if (age == T2) {
            eruption(owner, at);
        }
        if (age >= T2) {
            zone(owner, at, age);
        }
        if (age == T_END) {
            Util.LOCKED.remove(owner.getUUID());
        }
        if (age >= T2 + getZoneTicks() && age > T_END) {
            discard();
        }
    }

    private void slam1(LivingEntity owner, Vec3 at) {
        var c = MalConfig.CLAP;
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SLAM.get(), 1.8f, 0.85f);
        Util.blast(level(), at.x, at.y, at.z, c.radius.get(), 60);
        Util.fxDir(level(), ModParticles.RING.get(), at.x, at.y + 0.1, at.z, 0, 0, 0);
        Util.ring(level(), ModParticles.DUST.get(), at.add(0, 0.2, 0), 28, 0.55, 0.03);
        Util.ring(level(), ModParticles.GRAVEL.get(), at.add(0, 0.3, 0), 20, 0.4, 0.15);
        for (LivingEntity e : Util.enemies(level(), at, c.radius.get(), owner)) {
            hit1.add(e.getUUID());
            hit2.add(e.getUUID());
            Util.hurt(owner, e, c.firstFlat.get(), c.firstPercent.get());
            if (e.isAlive()) {
                Util.stagger(e, MalConfig.G.staggerTicks.get());
                Util.atkSlow(e, c.atkSlowPercent.get(), (int) (c.atkSlowSeconds.get() * 20));
                Util.slowPct(e, c.moveSlowPercent.get(), (int) (c.moveSlowSeconds.get() * 20));
                Vec3 p = e.position().add(0, e.getBbHeight() * 0.4, 0);
                Util.fx(level(), ModParticles.ROCK.get(), p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.15);
            }
        }
        if (hit1.size() >= c.resonanceCount.get()) {
            resonant = true;
            entityData.set(DATA_RES, true);
        }
    }

    private void wave(LivingEntity owner, Vec3 at, int age) {
        var c = MalConfig.CLAP;
        double k = (age - W0) / (double) (W1 - W0);
        double r0 = c.radius.get(), r1 = c.outerRadius.get();
        double r = r0 + (r1 - r0) * k;
        for (LivingEntity e : Util.enemies(level(), at, r, owner)) {
            if (hit2.add(e.getUUID())) {
                Util.hurt(owner, e, c.outerFlat.get(), 0);
            }
        }
        // a ring of stone fragments and small spikes travels outward
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + age * 0.2;
            double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
            Util.fxDir(level(), ModParticles.ROCK.get(), x, at.y + 0.2, z, 0, 0.18, 0);
            if (i % 2 == 0) {
                Util.fxDir(level(), ModParticles.DUST.get(), x, at.y + 0.1, z, Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1);
            }
        }
        if (age % 4 == 0) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.SPIKE.get(), 0.7f, 0.8f + (float) k * 0.4f);
        }
    }

    private void eruption(LivingEntity owner, Vec3 at) {
        var c = MalConfig.CLAP;
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SLAM.get(), 2.0f, 0.7f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SPIKE.get(), 1.4f, 0.8f);
        Util.blast(level(), at.x, at.y, at.z, c.radius.get() * 1.1, 80);
        Util.column(level(), at.x, at.y, at.z, 6, 24);
        Util.ring(level(), ModParticles.DUST.get(), at.add(0, 0.2, 0), 32, 0.6, 0.04);
        double mult = resonant ? 1.0 + c.resonanceBonusPercent.get() / 100.0 : 1.0;
        for (LivingEntity e : Util.enemies(level(), at, c.radius.get(), owner)) {
            Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get(), mult, 1.0);
            if (e.isAlive()) {
                Util.launchBlocks(e, c.knockUpBlocks.get());
            }
        }
        if (resonant) {
            Util.reduce(owner, c.resonanceReductionPercent.get() / 100.0, (int) (c.resonanceReductionSeconds.get() * 20));
        }
        int ticks = (int) (c.zoneSeconds.get() * 20);
        entityData.set(DATA_ZONE, ticks);
    }

    private void zone(LivingEntity owner, Vec3 at, int age) {
        var c = MalConfig.CLAP;
        int rel = age - T2;
        if (rel >= getZoneTicks()) {
            return;
        }
        double zr = getZoneRadius();
        if (rel % 10 == 0) {
            for (LivingEntity e : Util.enemies(level(), at, zr, owner)) {
                Util.slowPct(e, c.zoneSlowPercent.get(), 14);
                e.addEffect(new MobEffectInstance(ModEffects.QUAKE.get(), 14, 0, false, false, true));
            }
        }
        if (rel % 6 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * zr;
            Util.fxDir(level(), ModParticles.EMBER.get(), at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 0, 0.05, 0);
            Util.fxDir(level(), ModParticles.DUST.get(), at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, 0, 0.02, 0);
        }
        if (rel > 0 && rel % c.zoneInterval.get() == 0) {
            // an eruption under a random enemy in the zone (or at a random spot)
            List<LivingEntity> in = Util.enemies(level(), at, zr, owner);
            Vec3 p;
            if (!in.isEmpty() && random.nextInt(3) != 0) {
                LivingEntity t = in.get(random.nextInt(in.size()));
                p = new Vec3(t.getX(), at.y, t.getZ());
            } else {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * zr;
                p = new Vec3(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r);
            }
            entityData.set(DATA_EX, (float) p.x);
            entityData.set(DATA_EZ, (float) p.z);
            entityData.set(DATA_EAGE, age);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SPIKE.get(), 0.9f, 0.9f);
            Util.debris(level(), p.x, p.y, p.z, 0.8, 10);
            Util.column(level(), p.x, p.y, p.z, 3, 8);
            for (LivingEntity e : Util.enemies(level(), p, c.zoneEruptRadius.get(), owner)) {
                Util.hurt(owner, e, c.zoneFlat.get(), 0);
                if (e.isAlive()) {
                    Util.launchBlocks(e, 1.0);
                }
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
