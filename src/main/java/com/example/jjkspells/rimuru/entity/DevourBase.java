package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.registry.ModEffects;
import com.example.jjkspells.rimuru.registry.ModParticles;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared sequence of Predator and Beelzebuth: pull, capture (first damage), consumption (damage over time, health and mana to
 * the caster), final damage and execution. One clock (age = tickCount - 1). The entity stands at the centre of the effect.
 */
public abstract class DevourBase extends RimEntityBase {
    private static final EntityDataAccessor<Float> DATA_R = SynchedEntityData.defineId(DevourBase.class, EntityDataSerializers.FLOAT);

    /** Timeline in ticks; set by the subclass constructor so the client knows it too. */
    public int tPull, tCapture, tConsumeEnd, tFinal, tEnd, tL0, tL1;

    // damage and rules (server only, set by the subclass create)
    protected double radius, pullStrength, initFlat, initPct, dps, finFlat, finPct, threshold, bossBonus, healPer, manaPer, bossSlow;
    protected boolean holdVictims;
    protected double suppressSlow, suppressOut;

    protected final Set<UUID> victims = new LinkedHashSet<>();
    protected Vec3 center = Vec3.ZERO;
    protected int kills;

    protected DevourBase(EntityType<?> type, Level level) {
        super(type, level);
    }

    public float getRadius() {
        return entityData.get(DATA_R);
    }

    protected void init(LivingEntity caster, Vec3 point, double r) {
        setOwner(caster);
        this.center = point;
        this.radius = r;
        setPos(point.x, point.y, point.z);
        entityData.set(DATA_R, (float) r);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_R, 5f);
    }

    protected abstract void onStart(LivingEntity caster);

    /** Called after the final damage and the execution with the number of victims that died. */
    protected abstract void onFinal(LivingEntity caster, int total, int dead);

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.rimuru.client.ClientFx.tickDevour(this));
            return;
        }
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (age < tEnd && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            onStart(owner);
        }
        if (age < tEnd) {
            freeze(owner);
        }
        // pull and hold
        if (age >= tPull && age < tFinal) {
            for (LivingEntity e : Util.enemies(level(), center, radius, owner)) {
                if (age < tCapture || !holdVictims || Util.isBoss(e) || !victims.contains(e.getUUID())) {
                    Util.pull(e, center.add(0, 0.2, 0), pullStrength * (age < tCapture ? 1.3 : 0.7), 0.8);
                }
                if (age >= tCapture && victims.contains(e.getUUID())) {
                    if (holdVictims) {
                        Util.hold(e, 6, bossSlow);
                    }
                    if (suppressSlow > 0 && age % 10 == 0) {
                        Util.slowPct(e, suppressSlow, 30);
                        Util.outDamage(e, suppressOut, 30);
                    }
                }
            }
        }
        if (age == tCapture) {
            for (LivingEntity e : Util.enemies(level(), center, radius, owner)) {
                victims.add(e.getUUID());
                Util.hurt(owner, e, initFlat, initPct);
                absorb(healPer);
                Util.mana(owner, manaPer);
                e.addEffect(new MobEffectInstance(ModEffects.GLUTTONY.get(), tFinal - age + 10, 0, false, false, true));
                if (Util.isBoss(e)) {
                    Util.slowPct(e, bossSlow, tFinal - age + 10);
                }
                Util.slimeBurst(level(), e.getX(), e.getY(), e.getZ(), 1.0, 6);
            }
            Util.sound(level(), center.x, center.y, center.z, ModSounds.SUCTION.get(), 1.4f, 0.8f);
        }
        if (age > tCapture && age <= tConsumeEnd && age % 5 == 0) {
            double step = dps / 4.0;
            for (UUID id : victims) {
                LivingEntity e = level() instanceof ServerLevel sl && sl.getEntity(id) instanceof LivingEntity le && le.isAlive() ? le : null;
                if (e != null && e.position().distanceTo(center) <= radius * 1.6 + 2) {
                    Util.hurt(owner, e, step, 0);
                    Util.fx(level(), ModParticles.SOUL.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, 0.3, 0.4, 0.3, 0.02);
                    pushFx(1, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
                }
            }
        }
        if (age == tFinal) {
            int total = victims.size();
            int dead = 0;
            for (UUID id : victims) {
                var ent = level() instanceof ServerLevel sl ? sl.getEntity(id) : null;
                if (ent instanceof LivingEntity e && e.isAlive() && e.position().distanceTo(center) <= radius * 1.6 + 2) {
                    Util.hurt(owner, e, finFlat, finPct);
                    if (e.isAlive()) {
                        Util.consume(owner, e, threshold, bossBonus);
                    }
                    Util.slimeBurst(level(), e.getX(), e.getY(), e.getZ(), 1.2, 8);
                }
                if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                    dead++;
                }
            }
            kills = dead;
            Util.sound(level(), center.x, center.y, center.z, ModSounds.BOOM.get(), 1.6f, 1.1f);
            onFinal(owner, total, dead);
        }
        if (age >= tEnd) {
            finish(true);
            discard();
        }
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        releaseCommon(normal);
    }
}
