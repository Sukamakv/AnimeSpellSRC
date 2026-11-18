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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obsidian Kaiser - The Invincible Exoskeleton. Follows the caster; one clock (age = tickCount - 1):
 * <pre>
 *  0..FORM             awakening and the obsidian plates lock on (caster planted)
 *  FORM..FORM+dur      the carapace: negation charges, then heavy damage reduction (rules in ZegEvents)
 *  end / recast        the counterattack: the plates converge on the fist (WINDUP), then the ground punch
 *  after the punch     afterglow
 * </pre>
 */
public class ObsidianEntity extends ZegEntityBase {
    private static final Map<UUID, ObsidianEntity> ACTIVE = new ConcurrentHashMap<>();

    public static final int FORM = 30, WINDUP = 28, AFTERGLOW = 26;

    private static final EntityDataAccessor<Integer> DATA_CHARGES = SynchedEntityData.defineId(ObsidianEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_STORE = SynchedEntityData.defineId(ObsidianEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_HIT = SynchedEntityData.defineId(ObsidianEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HIT_YAW = SynchedEntityData.defineId(ObsidianEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_WIND = SynchedEntityData.defineId(ObsidianEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FIN = SynchedEntityData.defineId(ObsidianEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DUR = SynchedEntityData.defineId(ObsidianEntity.class, EntityDataSerializers.INT);

    private Util.Carapace state;
    private double lastPrevented;
    private boolean release, punched;

    public ObsidianEntity(EntityType<? extends ObsidianEntity> type, Level level) {
        super(type, level);
    }

    public static ObsidianEntity create(Level level, LivingEntity caster) {
        ObsidianEntity e = new ObsidianEntity(ModEntities.OBSIDIAN.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_DUR, (int) Math.round(ZegConfig.OBSIDIAN.durationSeconds.get() * 20));
        e.entityData.set(DATA_CHARGES, ZegConfig.OBSIDIAN.charges.get());
        return e;
    }

    public static boolean isActive(LivingEntity caster) {
        ObsidianEntity g = ACTIVE.get(caster.getUUID());
        return g != null && !g.isRemoved() && !g.punched;
    }

    /** A recast while the carapace stands releases the counterattack early. */
    public static boolean tryRelease(LivingEntity caster) {
        ObsidianEntity g = ACTIVE.get(caster.getUUID());
        if (g == null || g.isRemoved() || g.punched) {
            ACTIVE.remove(caster.getUUID());
            return false;
        }
        g.release = true;
        return true;
    }

    public int getCharges() {
        return entityData.get(DATA_CHARGES);
    }

    /** 0..1: how full the stored cataclysm is. */
    public float getStore() {
        return entityData.get(DATA_STORE);
    }

    public int getHitAge() {
        return entityData.get(DATA_HIT);
    }

    public float getHitYaw() {
        return entityData.get(DATA_HIT_YAW);
    }

    /** Age at which the counterattack windup started, or -1. */
    public int getWindupAt() {
        return entityData.get(DATA_WIND);
    }

    /** Age of the ground punch, or -1. */
    public int getPunchAt() {
        return entityData.get(DATA_FIN);
    }

    public int getDuration() {
        return entityData.get(DATA_DUR);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CHARGES, 6);
        entityData.define(DATA_STORE, 0f);
        entityData.define(DATA_HIT, -100);
        entityData.define(DATA_HIT_YAW, 0f);
        entityData.define(DATA_WIND, -1);
        entityData.define(DATA_FIN, -1);
        entityData.define(DATA_DUR, 200);
    }

    private int steps() {
        var c = ZegConfig.OBSIDIAN;
        return (int) Math.min(Math.floor(state.prevented / c.perPrevented.get()), Math.floor(c.maxBonus.get() / Math.max(0.0001, c.perPreventedBonus.get())));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickObsidian(this));
            return;
        }
        var c = ZegConfig.OBSIDIAN;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        int dur = (int) Math.round(c.durationSeconds.get() * 20);
        if (!punched) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        if (age == 0) {
            state = new Util.Carapace();
            state.charges = c.charges.get();
            state.block = c.reductionPercent.get() / 100.0;
            Util.CARAPACE.put(owner.getUUID(), state);
            ACTIVE.put(owner.getUUID(), this);
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.HUM.get(), 1.6f, 1.2f);
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.ARMOR.get(), 1.6f, 0.6f);
        }
        if (state == null) {
            finish(false);
            discard();
            return;
        }
        if (punched) {
            if (age >= getPunchAt() + AFTERGLOW) {
                discard();
            }
            return;
        }
        Vec3 p = owner.position();
        // Immovable Kaiser
        Util.kbImmune(owner, 4);
        Util.ccImmune(owner, 4);
        Util.fallImmune(owner, 6);
        if (age % 20 == 0) {
            owner.addEffect(new MobEffectInstance(ModEffects.CARAPACE.get(), 30, 0, false, false, true));
        }
        if (age < FORM) {
            owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            if (age % 2 == 0) {
                Util.fx(level(), ModParticles.SHARD.get(), p.x, p.y + 0.3, p.z, 3, 1.1, 0.3, 1.1, 0.05);
                Util.fx(level(), ModParticles.VOID.get(), p.x, p.y + 1.0, p.z, 1, 0.7, 0.8, 0.7, 0.01);
                Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 1.0, p.z, 2, 0.6, 0.8, 0.6, 0.03);
            }
        }
        if (age == FORM) {
            Util.LOCKED.remove(owner.getUUID());
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ARMOR.get(), 1.8f, 0.8f);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SHATTER.get(), 1.0f, 1.2f);
            Util.blast(level(), p.x, p.y + 0.4, p.z, 3.5, 40);
            Util.fxDir(level(), ModParticles.RING.get(), p.x, p.y + 0.1, p.z, 0, 0, 0);
            for (LivingEntity e : Util.enemies(level(), p, 6.0, owner)) {
                Util.knock(e, p, 3.0, 0.15);
            }
        }
        if (age >= FORM && getWindupAt() < 0) {
            entityData.set(DATA_CHARGES, state.charges);
            entityData.set(DATA_STORE, (float) Math.min(1.0, steps() * c.perPreventedBonus.get() / Math.max(1.0, c.maxBonus.get())));
            if (state.prevented != lastPrevented) {
                lastPrevented = state.prevented;
                entityData.set(DATA_HIT, age);
                entityData.set(DATA_HIT_YAW, random.nextFloat() * 360f);
            }
            if (age % 4 == 0) {
                Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1.0, p.z, 1, 0.5, 0.8, 0.5, 0.02);
            }
            if (release || age >= FORM + dur) {
                entityData.set(DATA_WIND, age);
                Util.LOCKED.add(owner.getUUID());
                Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 1.6f, 0.9f);
            }
        }
        int w = getWindupAt();
        if (w >= 0) {
            Util.kbImmune(owner, 4);
            if ((age - w) % 2 == 0) {
                double k = (age - w) / (double) WINDUP;
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 2.5 * (1 - k) + 0.3;
                    Util.fxDir(level(), ModParticles.STAR.get(), p.x + Math.cos(a) * r, p.y + 0.5 + random.nextDouble() * 1.5, p.z + Math.sin(a) * r,
                            -Math.cos(a) * 0.1, 0.0, -Math.sin(a) * 0.1);
                }
            }
            if (age == w + WINDUP) {
                punch(owner, p, age);
            }
        }
    }

    private void punch(LivingEntity owner, Vec3 at, int age) {
        var c = ZegConfig.OBSIDIAN;
        punched = true;
        entityData.set(DATA_FIN, age);
        double flat = c.finalFlat.get() + Math.min(c.maxBonus.get(), steps() * c.perPreventedBonus.get());
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.PUNCH.get(), 2.2f, 0.7f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 2.0f, 0.8f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 1.6f, 0.9f);
        Util.blast(level(), at.x, at.y + 0.6, at.z, c.radius.get() * 0.4, 100);
        Util.fxDir(level(), ModParticles.RING.get(), at.x, at.y + 0.1, at.z, 0, 0, 0);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.3, 0), 36, 0.9, 0.04);
        Util.ring(level(), ModParticles.SHARD.get(), at.add(0, 0.6, 0), 30, 0.7, 0.2);
        for (LivingEntity e : Util.enemies(level(), at, c.radius.get(), owner)) {
            Util.hurt(owner, e, flat, c.finalPercent.get());
            if (e.isAlive()) {
                Util.knock(e, at, 8.0, 0.3);
            }
        }
        releaseCaster(owner);
    }

    private void releaseCaster(LivingEntity owner) {
        Util.CARAPACE.remove(owner.getUUID());
        ACTIVE.remove(owner.getUUID(), this);
        Util.LOCKED.remove(owner.getUUID());
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.CARAPACE.remove(ownerId);
            ACTIVE.remove(ownerId, this);
            Util.LOCKED.remove(ownerId);
        }
    }
}
