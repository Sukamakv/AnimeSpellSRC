package com.example.jjkspells.diablo.entity;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.registry.ModEffects;
import com.example.jjkspells.diablo.registry.ModEntities;
import com.example.jjkspells.diablo.registry.ModParticles;
import com.example.jjkspells.diablo.registry.ModSounds;
import com.example.jjkspells.diablo.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Absolute Negation - Law of the Primordial. The barrier itself is a state in Util.BARRIER which DiaEvents applies; this entity follows the
 * caster, keeps the knockback immunity and ends the covenant (by time or by a recast) with the Covenant Release.
 * Visual events (fx list): type 1 = reflected bolt {from, to}.
 */
public class NegationEntity extends DiaEntityBase {
    public static final int FORM = 30, AFTER = 16;

    private static final Map<UUID, NegationEntity> ACTIVE = new ConcurrentHashMap<>();
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(NegationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RELEASE = SynchedEntityData.defineId(NegationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RR = SynchedEntityData.defineId(NegationEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STORED = SynchedEntityData.defineId(NegationEntity.class, EntityDataSerializers.FLOAT);

    private boolean releaseRequested, released;
    private int releaseAge = -1;
    private Util.Barrier barrier;

    public NegationEntity(EntityType<? extends NegationEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 14;
    }

    public static NegationEntity create(Level level, LivingEntity caster) {
        NegationEntity e = new NegationEntity(ModEntities.NEGATION.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_RR, (float) (double) DiaConfig.NEGATION.radius.get());
        e.entityData.set(DATA_DURATION, (int) Math.round(DiaConfig.NEGATION.durationSeconds.get() * 20));
        return e;
    }

    /** True while the caster has a covenant running. */
    public static boolean isActive(LivingEntity caster) {
        NegationEntity d = ACTIVE.get(caster.getUUID());
        return d != null && d.isAlive() && !d.released && !d.level().isClientSide;
    }

    /** Recast: ends the running covenant with the release; true if there was one. */
    public static boolean tryRelease(LivingEntity caster) {
        NegationEntity d = ACTIVE.get(caster.getUUID());
        if (d != null && d.isAlive() && !d.released && !d.level().isClientSide && d.tickCount > 2) {
            d.releaseRequested = true;
            return true;
        }
        return false;
    }

    /** Magical Rejection: a crimson bolt back at whoever shot the blocked projectile. */
    public static void reflect(LivingEntity caster, LivingEntity attacker) {
        NegationEntity d = ACTIVE.get(caster.getUUID());
        Vec3 from = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
        Vec3 to = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0);
        Util.reflecting = true;
        try {
            Util.hurt(caster, attacker, DiaConfig.NEGATION.reflectDamage.get(), 0);
        } finally {
            Util.reflecting = false;
        }
        Util.arc(caster.level(), ModParticles.EMBER.get(), from, to, 0.3);
        Util.fx(caster.level(), ModParticles.FLASH.get(), to.x, to.y, to.z, 1, 0, 0, 0, 0);
        Util.sound(caster.level(), from.x, from.y, from.z, ModSounds.SNAP.get(), 0.8f, 1.5f);
        if (d != null && d.isAlive()) {
            d.pushFx(1, from.x, from.y, from.z, to.x, to.y, to.z);
        }
    }

    public int getDuration() {
        return entityData.get(DATA_DURATION);
    }

    /** Age at which the release happened, or -1. */
    public int getReleaseAge() {
        return entityData.get(DATA_RELEASE);
    }

    public float getReleaseRadius() {
        return entityData.get(DATA_RR);
    }

    public float getStored() {
        return entityData.get(DATA_STORED);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DURATION, 160);
        entityData.define(DATA_RELEASE, -1);
        entityData.define(DATA_STORED, 0f);
        entityData.define(DATA_RR, 12f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.diablo.client.ClientFx.tickNegation(this));
            return;
        }
        var c = DiaConfig.NEGATION;
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (!released && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            long now = level().getGameTime();
            barrier = new Util.Barrier();
            barrier.charges = c.charges.get();
            barrier.until = now + getDuration() + 2;
            Util.BARRIER.put(ownerId, barrier);
            ACTIVE.put(ownerId, this);
            owner.addEffect(new MobEffectInstance(ModEffects.NEGATION.get(), getDuration(), 0, false, false, true));
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.HUM.get(), 1.4f, 1.0f);
        }
        if (!released) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
            Util.kbImmune(owner, 4);
            Util.ccImmune(owner, 4);
            Util.FALL_IMMUNE.merge(ownerId, level().getGameTime() + 10, Math::max);
            if (age % 8 == 0) {
                Util.fx(level(), ModParticles.RUNE.get(), owner.getX(), owner.getY() + 1.0, owner.getZ(), 3, 1.0, 0.9, 1.0, 0.02);
            }
            if (releaseRequested || age >= getDuration()) {
                release(owner, c);
            }
        } else if (age - releaseAge >= AFTER) {
            finish(true);
            discard();
        }
    }

    private void release(LivingEntity owner, DiaConfig.Negation c) {
        released = true;
        releaseAge = tickCount - 1;
        entityData.set(DATA_RELEASE, releaseAge);
        double stored = barrier == null ? 0 : Math.min(c.storedMax.get(), Math.floor(barrier.prevented / c.storedPer.get()) * c.storedBonus.get());
        entityData.set(DATA_STORED, (float) stored);
        Util.BARRIER.remove(ownerId);
        ACTIVE.remove(ownerId, this);
        owner.removeEffect(ModEffects.NEGATION.get());
        Vec3 p = owner.position();
        for (LivingEntity e : Util.enemies(level(), p, c.radius.get(), owner)) {
            Util.hurt(owner, e, c.explosionFlat.get() + stored, c.explosionPercent.get());
            Util.knock(e, p, c.knockBlocks.get(), 0.35);
        }
        Util.sparkBurst(level(), p.x, p.y + 1.0, p.z, 4.0, 60);
        Util.flameBurst(level(), p.x, p.y + 1.0, p.z, 4.0, 40);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 1.8f, 1.2f);
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.BARRIER.remove(ownerId);
            ACTIVE.remove(ownerId, this);
            LivingEntity o = ownerAny();
            if (o != null) {
                o.removeEffect(ModEffects.NEGATION.get());
            }
        }
    }
}
