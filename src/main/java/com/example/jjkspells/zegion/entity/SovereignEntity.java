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

/**
 * Kaiser's Final Form - The Sovereign of Destruction. Follows the caster; one clock (age = tickCount - 1):
 * <pre>
 *  0..PLATES        awakening, the crystalline plates grow (caster locked)
 *  PLATES..WINGS    the wings of the sovereign unfold; at SHOCK the transformation shockwave hits
 *  ACTIVE           buffs begin (the caster is free again); the form lasts durationSeconds (+ overdrive)
 *  end              wings fold (FOLD ticks), the sphere detonates
 * </pre>
 */
public class SovereignEntity extends ZegEntityBase {
    public static final int PLATES = 20, WINGS = 50, SHOCK = 66, ACTIVE = 80, FOLD = 34, AFTER = 22;

    private static final EntityDataAccessor<Integer> DATA_END = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FIN = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MELEE = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BLAST = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_MX = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MY = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MZ = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_OVERDRIVE = SynchedEntityData.defineId(SovereignEntity.class, EntityDataSerializers.BOOLEAN);

    private int melee, pendingBlast = -1;
    private boolean overdrive, detonated;
    private Vec3 blastAt = Vec3.ZERO;
    private double groundY;

    public SovereignEntity(EntityType<? extends SovereignEntity> type, Level level) {
        super(type, level);
    }

    public static SovereignEntity create(Level level, LivingEntity caster) {
        SovereignEntity e = new SovereignEntity(ModEntities.SOVEREIGN.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.groundY = caster.getY();
        e.entityData.set(DATA_END, ACTIVE + (int) Math.round(ZegConfig.FORM.durationSeconds.get() * 20));
        return e;
    }

    /** Age at which the form ends (grows with Kaiser Overdrive). */
    public int getEndAt() {
        return entityData.get(DATA_END);
    }

    /** Age at which the final fold started, or -1. */
    public int getFoldAt() {
        return entityData.get(DATA_FIN);
    }

    public int getMeleeAge() {
        return entityData.get(DATA_MELEE);
    }

    public int getBlastAge() {
        return entityData.get(DATA_BLAST);
    }

    public Vec3 getMeleePos() {
        return new Vec3(entityData.get(DATA_MX), entityData.get(DATA_MY), entityData.get(DATA_MZ));
    }

    public boolean isOverdrive() {
        return entityData.get(DATA_OVERDRIVE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_END, 460);
        entityData.define(DATA_FIN, -1);
        entityData.define(DATA_MELEE, -100);
        entityData.define(DATA_BLAST, -100);
        entityData.define(DATA_MX, 0f);
        entityData.define(DATA_MY, 0f);
        entityData.define(DATA_MZ, 0f);
        entityData.define(DATA_OVERDRIVE, false);
    }

    /** Called by ZegEvents for every melee hit the caster lands while transformed. */
    public void onMelee(LivingEntity target) {
        if (level().isClientSide || getFoldAt() >= 0) {
            return;
        }
        melee++;
        entityData.set(DATA_MELEE, tickCount - 1);
        entityData.set(DATA_MX, (float) target.getX());
        entityData.set(DATA_MY, (float) (target.getY() + target.getBbHeight() * 0.5));
        entityData.set(DATA_MZ, (float) target.getZ());
        if (melee % ZegConfig.FORM.strikesPerBlast.get() == 0) {
            pendingBlast = tickCount - 1;
            blastAt = target.position();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickSovereign(this));
            return;
        }
        var c = ZegConfig.FORM;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        if (detonated) {
            if (age >= getFoldAt() + FOLD + AFTER) {
                discard();
            }
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        Vec3 p = owner.position();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.FORM.put(owner.getUUID(), this);
            Util.KILLS.put(owner.getUUID(), 0);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 2.0f, 0.7f);
        }
        // the whole form: armor, no knockback, no slowness, projectile resistance, fall immunity
        if (age % 5 == 0) {
            Util.reduce(owner, c.reductionPercent.get() / 100.0, 12);
            Util.put(Util.PROJ_REDUCE, owner, c.projectileReductionPercent.get() / 100.0, 12);
            if (age % 20 == 0) {
                owner.addEffect(new MobEffectInstance(ModEffects.SOVEREIGN.get(), 30, 0, false, false, true));
            }
        }
        Util.kbImmune(owner, 4);
        Util.slowImmune(owner, 4);
        Util.fallImmune(owner, 6);
        if (age < ACTIVE) {
            introFx(owner, p, age);
        }
        if (age >= PLATES && age < ACTIVE + 30 && getFoldAt() < 0) {
            double target = groundY + (age < 100 ? 1.6 : 0.0);
            if (age < 110) {
                Util.hover(owner, target);
            }
        }
        if (age == 110) {
            Util.unhover(owner);
        }
        if (age == SHOCK) {
            shockwave(owner, p);
        }
        if (age == ACTIVE) {
            Util.LOCKED.remove(owner.getUUID());
            Util.unhover(owner);
        }
        if (age >= ACTIVE && getFoldAt() < 0) {
            Util.hastePct(owner, c.speedPercent.get(), 12);
            double mb = c.physicalPercent.get() / 100.0 + (overdrive ? c.overdriveMeleePercent.get() / 100.0 : 0.0);
            Util.put(Util.MELEE_BONUS, owner, mb, 12);
            Util.put(Util.MELEE_FLAT, owner, c.meleeFlat.get(), 12);
            Util.put(Util.OUT_BONUS, owner, c.physicalPercent.get() / 100.0, 12);
            if (age % 3 == 0) {
                Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1.0, p.z, 2, 0.6, 0.9, 0.6, 0.03);
            }
            if (age % 8 == 0) {
                Util.fx(level(), ModParticles.MIST.get(), p.x, p.y + 0.2, p.z, 1, 0.8, 0.1, 0.8, 0.01);
            }
            if (!overdrive && Util.KILLS.getOrDefault(owner.getUUID(), 0) >= c.overdriveKills.get()) {
                overdrive = true;
                entityData.set(DATA_OVERDRIVE, true);
                entityData.set(DATA_END, getEndAt() + (int) Math.round(c.overdriveSeconds.get() * 20));
                Util.sound(level(), p.x, p.y, p.z, ModSounds.WING.get(), 2.0f, 1.3f);
                Util.blast(level(), p.x, p.y + 1, p.z, 4, 50);
            }
            if (pendingBlast >= 0) {
                pendingBlast = -1;
                blast(owner);
            }
            if (age >= getEndAt()) {
                beginFold(owner, p, age);
            }
        }
        if (getFoldAt() >= 0) {
            int f = getFoldAt();
            Util.reduce(owner, c.reductionPercent.get() / 100.0, 6);
            if (age == f + FOLD) {
                detonate(owner, p);
            }
        }
    }

    private void introFx(LivingEntity owner, Vec3 p, int age) {
        if (age % 2 == 0) {
            double a = age * 0.5;
            Util.fx(level(), ModParticles.EMBER.get(), p.x + Math.cos(a) * 1.5, p.y + 0.1, p.z + Math.sin(a) * 1.5, 2, 0.1, 0.1, 0.1, 0.0);
            Util.fx(level(), ModParticles.VOID.get(), p.x, p.y + 0.5, p.z, 1, 1.0, 1.2, 1.0, 0.01);
            Util.fx(level(), ModParticles.SHARD.get(), p.x, p.y + 1.2, p.z, 2, 0.9, 1.0, 0.9, 0.05);
        }
        if (age == PLATES) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ARMOR.get(), 2.0f, 0.6f);
            Util.blast(level(), p.x, p.y + 0.5, p.z, 3, 40);
        }
        if (age == WINGS) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.WING.get(), 2.4f, 0.8f);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 1.6f, 1.0f);
            Util.fxDir(level(), ModParticles.RING.get(), p.x, p.y + 0.1, p.z, 0, 0, 0);
        }
        if (age > WINGS && age < SHOCK && age % 2 == 0) {
            Util.arc(level(), ModParticles.SPARK.get(), p.add(0, 1.8, 0), p.add((random.nextDouble() - 0.5) * 24, 1 + random.nextDouble() * 8, (random.nextDouble() - 0.5) * 24), 1.2);
        }
    }

    private void shockwave(LivingEntity owner, Vec3 p) {
        var c = ZegConfig.FORM;
        Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 2.6f, 0.8f);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.SHATTER.get(), 2.0f, 0.8f);
        Util.blast(level(), p.x, p.y + 1, p.z, c.radius.get() * 0.4, 110);
        Util.ring(level(), ModParticles.MIST.get(), p.add(0, 0.3, 0), 36, 1.1, 0.04);
        Util.ring(level(), ModParticles.SHARD.get(), p.add(0, 0.6, 0), 30, 0.9, 0.2);
        for (LivingEntity e : Util.enemies(level(), p, c.radius.get(), owner)) {
            Util.hurt(owner, e, c.openFlat.get(), c.openPercent.get());
            if (e.isAlive()) {
                Util.knock(e, p, 9.0, 0.3);
            }
        }
    }

    private void blast(LivingEntity owner) {
        var c = ZegConfig.FORM;
        Vec3 at = blastAt;
        entityData.set(DATA_BLAST, tickCount - 1);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 1.8f, 1.2f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.PUNCH.get(), 1.6f, 0.9f);
        Util.blast(level(), at.x, at.y + 0.6, at.z, c.blastRadius.get() * 0.5, 60);
        Util.ring(level(), ModParticles.SHARD.get(), at.add(0, 0.5, 0), 20, 0.6, 0.2);
        for (LivingEntity e : Util.enemies(level(), at, c.blastRadius.get(), owner)) {
            Util.hurt(owner, e, c.blastFlat.get(), c.blastPercent.get());
            if (e.isAlive()) {
                Util.knock(e, at, 4.0, 0.25);
            }
        }
    }

    private void beginFold(LivingEntity owner, Vec3 p, int age) {
        entityData.set(DATA_FIN, age);
        Util.LOCKED.add(owner.getUUID());
        Util.FORM.remove(owner.getUUID());
        Util.MELEE_BONUS.remove(owner.getUUID());
        Util.MELEE_FLAT.remove(owner.getUUID());
        Util.OUT_BONUS.remove(owner.getUUID());
        Util.KILLS.remove(owner.getUUID());
        Util.sound(level(), p.x, p.y, p.z, ModSounds.WING.get(), 2.0f, 0.6f);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 2.0f, 0.8f);
    }

    private void detonate(LivingEntity owner, Vec3 p) {
        var c = ZegConfig.FORM;
        detonated = true;
        Util.swing(owner);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 3.2f, 0.6f);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.SHATTER.get(), 2.6f, 0.6f);
        Util.blast(level(), p.x, p.y + 1, p.z, c.finalRadius.get() * 0.4, 160);
        Util.ring(level(), ModParticles.MIST.get(), p.add(0, 0.3, 0), 48, 1.5, 0.05);
        Util.ring(level(), ModParticles.SHARD.get(), p.add(0, 0.6, 0), 40, 1.1, 0.2);
        Util.ring(level(), ModParticles.MIRROR.get(), p.add(0, 0.8, 0), 32, 0.9, 0.25);
        for (LivingEntity e : Util.enemies(level(), p, c.finalRadius.get(), owner)) {
            Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get());
            if (e.isAlive()) {
                Util.knock(e, p, 10.0, 0.35);
            }
        }
        Util.LOCKED.remove(owner.getUUID());
        Util.clearSpeed(owner);
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
            Util.FORM.remove(ownerId, this);
            Util.MELEE_BONUS.remove(ownerId);
            Util.MELEE_FLAT.remove(ownerId);
            Util.OUT_BONUS.remove(ownerId);
            Util.KILLS.remove(ownerId);
            if (level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity o) {
                o.setNoGravity(false);
                Util.clearSpeed(o);
            }
        }
    }
}
