package com.example.jjkspells.malenia.entity;

import com.example.jjkspells.malenia.config.MelConfig;
import com.example.jjkspells.malenia.registry.ModEntities;
import com.example.jjkspells.malenia.registry.ModParticles;
import com.example.jjkspells.malenia.registry.ModSounds;
import com.example.jjkspells.malenia.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scarlet Bloom - Flower of Decay. One clock (age = tickCount - 1):
 * <pre>
 *  0..ARMS      raise the arms, the targeting circle appears
 *  ARMS..TOP    ascend
 *  TOP..IMPACT  dive onto the marked point
 *  IMPACT       explosion, Scarlet Rot, Aeonian Regeneration
 *  BLOOM0..BLOOM1  the giant flower opens
 *  BLOOM1       second explosion, the Rotting Garden begins (the caster is free from CASTER_END)
 *  garden       lasts gardenSeconds
 * </pre>
 */
public class BloomEntity extends MelEntityBase {
    public static final int ARMS = 8, TOP = 28, IMPACT = 36, BLOOM0 = 38, BLOOM1 = 62, CASTER_END = 68;

    private static final EntityDataAccessor<Float> DATA_PX = SynchedEntityData.defineId(BloomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PY = SynchedEntityData.defineId(BloomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PZ = SynchedEntityData.defineId(BloomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_R = SynchedEntityData.defineId(BloomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GARDEN = SynchedEntityData.defineId(BloomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CATA = SynchedEntityData.defineId(BloomEntity.class, EntityDataSerializers.BOOLEAN);

    private Vec3 start, top, point;
    private int hitCount;
    private double regen;
    private final Map<UUID, Integer> exposure = new HashMap<>();

    public BloomEntity(EntityType<? extends BloomEntity> type, Level level) {
        super(type, level);
    }

    public static BloomEntity create(Level level, LivingEntity caster, Vec3 point) {
        BloomEntity e = new BloomEntity(ModEntities.BLOOM.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.point = point;
        e.entityData.set(DATA_PX, (float) point.x);
        e.entityData.set(DATA_PY, (float) point.y);
        e.entityData.set(DATA_PZ, (float) point.z);
        e.entityData.set(DATA_R, (float) (double) MelConfig.BLOOM.bloomRadius.get());
        e.entityData.set(DATA_GARDEN, (int) Math.round(MelConfig.BLOOM.gardenSeconds.get() * 20));
        return e;
    }

    public Vec3 getPoint() {
        return new Vec3(entityData.get(DATA_PX), entityData.get(DATA_PY), entityData.get(DATA_PZ));
    }

    /** Final radius of the flower and garden. */
    public float getRadius() {
        return entityData.get(DATA_R);
    }

    public int getGardenTicks() {
        return entityData.get(DATA_GARDEN);
    }

    public boolean isCatastrophe() {
        return entityData.get(DATA_CATA);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PX, 0f);
        entityData.define(DATA_PY, 0f);
        entityData.define(DATA_PZ, 0f);
        entityData.define(DATA_R, 12f);
        entityData.define(DATA_GARDEN, 200);
        entityData.define(DATA_CATA, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malenia.client.ClientFx.tickBloom(this));
            return;
        }
        var c = MelConfig.BLOOM;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        boolean casting = age < CASTER_END;
        if (casting && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            start = owner.position();
            top = start.add(0, c.riseBlocks.get(), 0);
            Util.LOCKED.add(owner.getUUID());
            Util.kbImmune(owner, CASTER_END + 6);
            Util.FALL_IMMUNE.put(owner.getUUID(), owner.level().getGameTime() + CASTER_END + 80);
            Util.sound(level(), start.x, start.y, start.z, ModSounds.HUM.get(), 1.4f, 1.0f);
        }
        if (casting) {
            owner.setSprinting(false);
            setPos(owner.getX(), owner.getY(), owner.getZ());
            moveCaster(owner, age);
        }
        if (age < IMPACT && age % 3 == 0) {
            Vec3 p = owner.position();
            Util.fx(level(), ModParticles.PETAL.get(), p.x, p.y + 1.0, p.z, 3, 0.6, 0.8, 0.6, 0.04);
            Util.fx(level(), ModParticles.SPORE.get(), point.x, point.y + 0.2, point.z, 3, c.impactRadius.get() * 0.6, 0.1, c.impactRadius.get() * 0.6, 0.02);
        }
        if (age == IMPACT) {
            impact(owner);
        }
        if (age > IMPACT && age < BLOOM1 && age % 2 == 0) {
            double u = smooth((age - BLOOM0) / (double) (BLOOM1 - BLOOM0));
            double r = getRadius() * u;
            for (int i = 0; i < 3; i++) {
                double a = level().random.nextDouble() * Math.PI * 2;
                Util.fxDir(level(), level().random.nextBoolean() ? ModParticles.PETAL.get() : ModParticles.SPORE.get(), point.x + Math.cos(a) * r, point.y + 0.3, point.z + Math.sin(a) * r,
                        Math.cos(a) * 0.05, 0.12, Math.sin(a) * 0.05);
            }
        }
        if (age == BLOOM0) {
            Util.sound(level(), point.x, point.y, point.z, ModSounds.BLOOM.get(), 1.6f, 1.0f);
        }
        if (age == BLOOM1) {
            explode(owner);
        }
        if (age >= BLOOM1) {
            garden(owner, age - BLOOM1);
        }
        if (age == CASTER_END) {
            finish(true);
        }
        if (age >= BLOOM1 + getGardenTicks()) {
            finish(true);
            discard();
        }
    }

    private void moveCaster(LivingEntity owner, int age) {
        Vec3 p;
        Vec3 look = point;
        if (age < ARMS) {
            p = start;
        } else if (age < TOP) {
            p = start.lerp(top, smooth((age - ARMS) / (double) (TOP - ARMS)));
        } else if (age < IMPACT) {
            double u = (age - TOP + 1) / (double) (IMPACT - TOP);
            p = top.lerp(point, u * u);
        } else {
            p = point;
        }
        float yaw = Util.yawTo(p, new Vec3(look.x, p.y, look.z));
        if (p.distanceToSqr(look) < 0.01) {
            yaw = owner.getYRot();
        }
        hover(owner, p, yaw, age >= TOP && age < IMPACT ? 60f : -10f);
        if (age >= CASTER_END - 4) {
            owner.setNoGravity(false);
        }
    }

    private void impact(LivingEntity owner) {
        var c = MelConfig.BLOOM;
        Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 1.8f, 0.8f);
        Util.bloomBurst(level(), point.x, point.y, point.z, c.impactRadius.get(), 70);
        Util.ring(level(), ModParticles.PETAL.get(), point.add(0, 0.4, 0), 40, 0.5, 0.06);
        Util.fxDir(level(), ModParticles.RING.get(), point.x, point.y + 0.5, point.z, 0, 0, 0);
        Util.fx(level(), ModParticles.FLASH.get(), point.x, point.y + 1, point.z, 2, 0, 0, 0, 0);
        List<LivingEntity> hit = Util.enemies(level(), point, c.impactRadius.get(), owner);
        for (LivingEntity e : hit) {
            Util.hurt(owner, e, c.impactFlat.get(), c.impactPercent.get());
            if (e.isAlive()) {
                Util.rot(owner, e, c.rotDps.get(), c.rotSeconds.get(), c.rotHealCutPercent.get(), 0);
            }
            hitCount++;
            if (regen < c.regenMax.get()) {
                double h = Math.min(c.regenPerEnemy.get(), c.regenMax.get() - regen);
                regen += h;
                owner.heal((float) h);
            }
        }
        boolean cata = hitCount >= c.catastropheCount.get();
        entityData.set(DATA_CATA, cata);
        if (cata) {
            entityData.set(DATA_R, (float) (double) c.catastropheGardenRadius.get());
            Util.sound(level(), point.x, point.y, point.z, ModSounds.WINGS.get(), 1.2f, 0.7f);
        }
    }

    private void explode(LivingEntity owner) {
        var c = MelConfig.BLOOM;
        boolean cata = isCatastrophe();
        double mult = cata ? 1.0 + c.catastropheBonusPercent.get() / 100.0 : 1.0;
        double rotSec = c.rotSeconds.get() + (cata ? c.catastropheRotSeconds.get() : 0);
        Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 1.8f, 1.0f);
        Util.bloomBurst(level(), point.x, point.y, point.z, c.bloomRadius.get(), 90);
        Util.ring(level(), ModParticles.PETAL.get(), point.add(0, 0.6, 0), 56, 0.7, 0.08);
        Util.ring(level(), ModParticles.GOLD.get(), point.add(0, 0.9, 0), 32, 0.5, 0.1);
        Util.fxDir(level(), ModParticles.RING.get(), point.x, point.y + 0.5, point.z, 0, 0, 0);
        for (LivingEntity e : Util.enemies(level(), point, c.bloomRadius.get(), owner)) {
            Util.hurt(owner, e, c.bloomFlat.get(), c.bloomPercent.get(), mult, 1.0);
            if (e.isAlive()) {
                Util.rot(owner, e, c.rotDps.get(), rotSec, c.rotHealCutPercent.get(), 0);
            }
        }
    }

    /** The Rotting Garden: each second everything inside takes rot damage and is slowed; exposure builds up to the full Scarlet Rot. */
    private void garden(LivingEntity owner, int gage) {
        var c = MelConfig.BLOOM;
        double r = getRadius();
        if (gage % 5 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = level().random.nextDouble() * Math.PI * 2, d = Math.sqrt(level().random.nextDouble()) * r;
                Util.fxDir(level(), level().random.nextInt(3) == 0 ? ModParticles.PETAL.get() : ModParticles.SPORE.get(), point.x + Math.cos(a) * d, point.y + 0.2 + level().random.nextDouble() * 1.5,
                        point.z + Math.sin(a) * d, 0, 0.03 + level().random.nextDouble() * 0.04, 0);
            }
            Util.fx(level(), ModParticles.ROT.get(), point.x, point.y + 0.3, point.z, 3, r * 0.5, 0.1, r * 0.5, 0.01);
        }
        if (gage % 20 != 10) {
            return;
        }
        LivingEntity o = owner;
        if (o == null) {
            o = ownerAny();
        }
        if (o == null) {
            return;
        }
        double cata = isCatastrophe() ? c.catastropheRotSeconds.get() : 0;
        int need = (int) Math.ceil(c.gardenExposureSeconds.get());
        List<LivingEntity> inside = Util.enemies(level(), point, r, o);
        exposure.keySet().removeIf(id -> inside.stream().noneMatch(e -> e.getUUID().equals(id)));
        for (LivingEntity e : inside) {
            Util.hurt(o, e, c.gardenDps.get(), 0);
            if (!e.isAlive()) {
                continue;
            }
            Util.slowPct(e, c.gardenSlowPercent.get(), 30);
            int x = exposure.merge(e.getUUID(), 1, Integer::sum);
            if (x >= need) {
                Util.rot(o, e, c.rotDps.get(), c.rotSeconds.get() + cata, c.rotHealCutPercent.get(), 0);
            }
        }
        Util.sound(level(), point.x, point.y, point.z, ModSounds.ROT.get(), 0.6f, 0.9f);
    }

    @Override
    protected void finish(boolean normal) {
        releaseCommon(normal);
    }
}
