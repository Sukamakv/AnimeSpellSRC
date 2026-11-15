package com.example.jjkspells.pantheon.entity;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.registry.ModEntities;
import com.example.jjkspells.pantheon.registry.ModParticles;
import com.example.jjkspells.pantheon.registry.ModSounds;
import com.example.jjkspells.pantheon.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Aegis Storm - The Immortal Phalanx. Follows the caster; one clock (age = tickCount - 1):
 * <pre>
 *  form       0..F          the shield appears
 *  advance    F..D-O        slowed advance, spear thrusts every few ticks, frontal damage mostly stopped
 *  overload   D-O..D        the shield cracks, thrusts stop
 *  detonation D             the frontal cone detonates, enemies are thrown back and up
 * </pre>
 * Frontal protection itself is applied by PanEvents through Util.AEGIS.
 */
public class AegisEntity extends PanEntityBase {
    private static final EntityDataAccessor<Integer> DATA_DUR = SynchedEntityData.defineId(AegisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FIN = SynchedEntityData.defineId(AegisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LAST = SynchedEntityData.defineId(AegisEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_FRAC = SynchedEntityData.defineId(AegisEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RANGE = SynchedEntityData.defineId(AegisEntity.class, EntityDataSerializers.FLOAT);

    /** After the detonation the entity lingers this long for the flames on the ground. */
    public static final int AFTERGLOW = 30;

    private Util.Aegis state;
    private boolean lastStandUsed, detonated;
    private int detonatedAt = -1;

    public AegisEntity(EntityType<? extends AegisEntity> type, Level level) {
        super(type, level);
    }

    public static AegisEntity create(Level level, LivingEntity caster) {
        AegisEntity e = new AegisEntity(ModEntities.AEGIS.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_DUR, PanConfig.AEGIS.durationTicks.get());
        e.entityData.set(DATA_RANGE, PanConfig.AEGIS.finalRange.get().floatValue());
        return e;
    }

    public int getDuration() {
        return entityData.get(DATA_DUR);
    }

    /** Age of the detonation, or -1 before it. */
    public int getDetonationAt() {
        return entityData.get(DATA_FIN);
    }

    public boolean isLastStand() {
        return entityData.get(DATA_LAST);
    }

    /** 0..1 how loaded the shield is (blocked damage towards the maximum retaliation bonus). */
    public float getCharge() {
        return entityData.get(DATA_FRAC);
    }

    public float getRange() {
        return entityData.get(DATA_RANGE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DUR, 80);
        entityData.define(DATA_FIN, -1);
        entityData.define(DATA_LAST, false);
        entityData.define(DATA_FRAC, 0f);
        entityData.define(DATA_RANGE, 7f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.pantheon.client.ClientFx.tickAegis(this));
            return;
        }
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        var c = PanConfig.AEGIS;
        int dur = c.durationTicks.get();
        if (!detonated) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }

        if (age == 0) {
            state = new Util.Aegis();
            state.cosHalf = Math.cos(Math.toRadians(c.defenseDegrees.get() / 2.0));
            state.block = c.frontBlockPercent.get() / 100.0;
            Util.AEGIS.put(owner.getUUID(), state);
            Util.LOCKED.add(owner.getUUID());
            AttributeInstance sp = owner.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) {
                sp.removeModifier(AEGIS_SPEED_ID);
                sp.addTransientModifier(new AttributeModifier(AEGIS_SPEED_ID, "pantheon_aegis", -(1.0 - c.speedPercent.get() / 100.0), AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            AttributeInstance kb = owner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(KB_ID);
                kb.addTransientModifier(new AttributeModifier(KB_ID, "pantheon_aegis_kb", 1.0, AttributeModifier.Operation.ADDITION));
            }
            Util.sound(level(), owner.getX(), owner.getY() + 1, owner.getZ(), ModSounds.AEGIS.get(), 1.3f, 1.0f);
        }
        if (state == null) {
            // restored from an odd state: stop cleanly
            finish(false);
            discard();
            return;
        }

        if (!detonated) {
            owner.setSprinting(false);
            if (age < dur) {
                if (owner.getHealth() < owner.getMaxHealth() * c.lastStandBelowPercent.get() / 100.0 && !lastStandUsed) {
                    lastStandUsed = true;
                    state.block = 1.0;
                    entityData.set(DATA_LAST, true);
                    entityData.set(DATA_RANGE, c.lastStandRange.get().floatValue());
                    Util.sound(level(), owner.getX(), owner.getY() + 1, owner.getZ(), ModSounds.STAR.get(), 1.0f, 0.7f);
                    Vec3 p = owner.position().add(0, 1, 0);
                    Util.fx(level(), ModParticles.FLASH.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    Util.fx(level(), ModParticles.FLAME.get(), p.x, p.y, p.z, 24, 0.5, 0.7, 0.5, 0.1);
                }
                entityData.set(DATA_FRAC, (float) Math.min(1.0, (state.prevented / c.perStep.get() * c.perStepBonus.get()) / Math.max(1.0, c.maxBonus.get())));
            }
            Vec3 look = owner.getLookAngle();
            Vec3 face = new Vec3(look.x, 0, look.z);
            face = face.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : face.normalize();
            Vec3 shield = owner.position().add(face.x * 1.0, 1.1, face.z * 1.0);

            if (age < c.formTicks.get() && age % 2 == 0) {
                Util.fx(level(), ModParticles.SPARK.get(), shield.x, shield.y, shield.z, 5, 0.5, 0.7, 0.5, 0.02);
            }
            int adv0 = c.formTicks.get();
            int advEnd = dur - c.overloadTicks.get();
            if (age >= adv0 && age < advEnd && (age - adv0) % c.thrustGap.get() == 0) {
                thrust(owner, face);
            }
            if (age >= advEnd && age < dur && age % 2 == 0) {
                // overload: cracks of golden energy
                Util.fx(level(), ModParticles.SPARK.get(), shield.x, shield.y, shield.z, 6, 0.55, 0.7, 0.55, 0.12);
                Util.fx(level(), ModParticles.FLAME.get(), shield.x, shield.y, shield.z, 2, 0.4, 0.6, 0.4, 0.03);
            }
            if (age == advEnd) {
                Util.sound(level(), shield.x, shield.y, shield.z, ModSounds.AEGIS.get(), 1.0f, 1.6f);
            }
            if (age >= dur) {
                detonate(owner, face);
            }
            return;
        }
        if (age >= detonatedAt + AFTERGLOW) {
            discard();
        }
    }

    private void thrust(LivingEntity owner, Vec3 face) {
        var c = PanConfig.AEGIS;
        Util.swing(owner);
        double reach = c.range.get();
        double cosHalf = Math.cos(Math.toRadians(c.attackDegrees.get() / 2.0));
        Vec3 from = owner.position().add(face.x * 0.8, 1.2, face.z * 0.8);
        Util.arc(level(), ModParticles.STREAK.get(), from, from.add(face.scale(reach * 0.8)), 0.0);
        Util.fx(level(), ModParticles.SPARK.get(), from.x + face.x * reach * 0.8, from.y, from.z + face.z * reach * 0.8, 3, 0.2, 0.2, 0.2, 0.1);
        Util.sound(level(), from.x, from.y, from.z, ModSounds.THRUST.get(), 0.7f, 0.9f + random.nextFloat() * 0.25f);
        for (LivingEntity e : Util.enemies(level(), owner.position(), reach, owner)) {
            Vec3 to = new Vec3(e.getX() - owner.getX(), 0, e.getZ() - owner.getZ());
            if (to.lengthSqr() > 1.0E-6 && to.normalize().dot(face) < cosHalf) {
                continue;
            }
            Util.hurt(owner, e, c.hitFlat.get(), 0);
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.55, 0);
            Util.fx(level(), ModParticles.METAL.get(), p.x, p.y, p.z, 3, 0.25, 0.25, 0.25, 0.12);
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y, p.z, 5, 0.25, 0.25, 0.25, 0.2);
        }
    }

    private void detonate(LivingEntity owner, Vec3 face) {
        var c = PanConfig.AEGIS;
        detonated = true;
        detonatedAt = tickCount - 1;
        entityData.set(DATA_FIN, detonatedAt);
        double range = lastStandUsed ? c.lastStandRange.get() : c.finalRange.get();
        int steps = (int) Math.min(Math.floor(state.prevented / c.perStep.get()), Math.floor(c.maxBonus.get() / Math.max(0.0001, c.perStepBonus.get())));
        double flat = c.finalFlat.get() + steps * c.perStepBonus.get();
        double cosHalf = Math.cos(Math.toRadians(c.defenseDegrees.get() / 2.0));
        Vec3 at = owner.position();
        Util.sound(level(), at.x, at.y + 1, at.z, ModSounds.BASH.get(), 1.5f, 0.9f);
        Util.sound(level(), at.x, at.y + 1, at.z, ModSounds.DIVINE.get(), 1.0f, 1.2f);
        Util.swing(owner);
        // a golden semicircle of fire and sparks, then flames left on the ground
        for (int i = -10; i <= 10; i++) {
            double a = Math.toRadians(i * (c.defenseDegrees.get() / 20.0));
            double cs = Math.cos(a), sn = Math.sin(a);
            Vec3 d = new Vec3(face.x * cs - face.z * sn, 0, face.x * sn + face.z * cs);
            for (int k = 1; k <= 4; k++) {
                double r = range * k / 4.0;
                Util.fxDir(level(), ModParticles.FLAME.get(), at.x + d.x * r * 0.4, at.y + 0.5, at.z + d.z * r * 0.4, d.x * 0.5, 0.04, d.z * 0.5);
            }
            Util.fxDir(level(), ModParticles.SPARK.get(), at.x + d.x * 1.5, at.y + 1.0, at.z + d.z * 1.5, d.x * 0.9, 0.05, d.z * 0.9);
        }
        Util.blast(level(), at.x + face.x * 2, at.y + 0.6, at.z + face.z * 2, 4.0, 30);
        for (LivingEntity e : Util.enemies(level(), at, range, owner)) {
            Vec3 to = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
            if (to.lengthSqr() > 1.0E-6 && to.normalize().dot(face) < cosHalf) {
                continue;
            }
            Util.hurt(owner, e, flat, c.finalPercent.get());
            if (e.isAlive()) {
                Util.knock(e, at, 5.0, 0.0);
                Util.launch(e, 0.55);
            }
        }
        // release the caster; Last Stand leaves a burst of speed
        releaseCaster(owner);
        if (lastStandUsed) {
            Util.hastePct(owner, c.lastStandSpeedPercent.get(), c.lastStandSpeedTicks.get());
        }
    }

    private void releaseCaster(LivingEntity owner) {
        Util.AEGIS.remove(owner.getUUID());
        Util.LOCKED.remove(owner.getUUID());
        AttributeInstance sp = owner.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null) {
            sp.removeModifier(AEGIS_SPEED_ID);
        }
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
            Util.AEGIS.remove(ownerId);
            Util.LOCKED.remove(ownerId);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            releaseCaster(owner);
        }
    }
}
