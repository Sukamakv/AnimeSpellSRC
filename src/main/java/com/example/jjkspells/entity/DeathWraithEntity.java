package com.example.jjkspells.entity;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.SpellDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * One of the wraiths of Death's Wraiths. It circles the caster for a moment, then flies straight through the world
 * (blocks do not stop it) at its target and, on touching it, removes a fraction of its max health.
 * The server moves it every tick; the client only draws it (DeathWraithRenderer).
 */
public class DeathWraithEntity extends Entity {
    private static final int MAX_FLIGHT = 200;

    private UUID ownerId;
    private UUID targetId;
    private int launchTick = 30;
    private float phase;
    private float orbitRadius = 3f;
    private float orbitHeight = 2f;
    private Vec3 vel = Vec3.ZERO;

    public DeathWraithEntity(EntityType<? extends DeathWraithEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public DeathWraithEntity(Level level, LivingEntity owner, LivingEntity target, int launchTick, float phase, float radius, float height) {
        this(ModEntities.DEATH_WRAITH.get(), level);
        this.ownerId = owner.getUUID();
        this.targetId = target.getUUID();
        this.launchTick = launchTick;
        this.phase = phase;
        this.orbitRadius = radius;
        this.orbitHeight = height;
        this.setPos(owner.getX() + Math.cos(phase) * radius, owner.getY() + 0.2, owner.getZ() + Math.sin(phase) * radius);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        if (tickCount < launchTick) {
            // circle the caster, rising from the ground
            if (owner != null) {
                double a = phase + tickCount * 0.12;
                double rise = Math.min(1.0, tickCount / 14.0);
                Vec3 want = owner.position().add(Math.cos(a) * orbitRadius, 0.3 + (orbitHeight + 0.5 * Math.sin(tickCount * 0.2 + phase)) * rise, Math.sin(a) * orbitRadius);
                Vec3 p = this.position();
                Vec3 np = p.lerp(want, 0.35);
                this.vel = np.subtract(p);
                this.setPos(np.x, np.y, np.z);
            }
            if (tickCount % 3 == 0) {
                trail(level, 1);
            }
            return;
        }
        if (tickCount - launchTick > MAX_FLIGHT) {
            puff(level);
            this.discard();
            return;
        }
        LivingEntity target = resolveTarget(level);
        if (target == null) {
            puff(level);
            this.discard();
            return;
        }
        int fly = tickCount - launchTick;
        double speed = Math.min(1.35, 0.35 + fly * 0.07);
        Vec3 to = target.getBoundingBox().getCenter().subtract(this.position());
        double dist = to.length();
        if (dist < 1.1) {
            hit(level, target, owner instanceof LivingEntity le ? le : null);
            this.discard();
            return;
        }
        Vec3 want = to.scale(1.0 / dist).scale(speed);
        this.vel = this.vel.lerp(want, 0.35);
        if (this.vel.length() > dist) {
            this.vel = this.vel.normalize().scale(dist);
        }
        this.setPos(this.getX() + vel.x, this.getY() + vel.y, this.getZ() + vel.z);
        trail(level, 2);
        if (fly == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1.2f, 0.5f + random.nextFloat() * 0.3f);
        }
    }

    private LivingEntity resolveTarget(ServerLevel level) {
        Entity e = targetId == null ? null : level.getEntity(targetId);
        if (e instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        // target gone: pick the nearest living thing that is not the caster or an ally
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        LivingEntity best = null;
        double bd = 30 * 30;
        for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(30), LivingEntity::isAlive)) {
            if (c == owner || (owner != null && c.isAlliedTo(owner)) || c.isSpectator()) continue;
            if (c instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().invulnerable) continue;
            double d = c.distanceToSqr(this);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        if (best != null) {
            targetId = best.getUUID();
        }
        return best;
    }

    private void trail(ServerLevel level, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 p = position().add((random.nextDouble() - 0.5) * 0.7, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 0.7);
            FX.one(level, ModParticles.BILLOW_SMOKE.get(), p, new Vec3((random.nextDouble() - 0.5) * 0.03, 0.01, (random.nextDouble() - 0.5) * 0.03));
        }
        if (tickCount % 2 == 0) {
            FX.one(level, ModParticles.GHOST_GLOW.get(), position(), new Vec3(0.9 + random.nextDouble() * 0.5, 0, 0));
        }
    }

    private void puff(ServerLevel level) {
        FX.one(level, ModParticles.GHOST_GLOW.get(), position(), new Vec3(2.0, 0, 0));
        for (int i = 0; i < 4; i++) {
            FX.one(level, ModParticles.BILLOW_SMOKE.get(), position().add(0, (random.nextDouble() - 0.5), 0), new Vec3((random.nextDouble() - 0.5) * 0.12, 0.03, (random.nextDouble() - 0.5) * 0.12));
        }
    }

    private void hit(ServerLevel level, LivingEntity target, LivingEntity owner) {
        double pct = JJKConfig.DEATH_WRAITH_PERCENT.get();
        if (pct > 0 && !(owner != null && (target == owner || target.isAlliedTo(owner)))) {
            target.invulnerableTime = 0;
            DamageSource src = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
            SpellDamage.percent(target, (float) (target.getMaxHealth() * pct), owner, src);
        }
        Vec3 c = target.getBoundingBox().getCenter();
        FX.one(level, ModParticles.GHOST_GLOW.get(), c, new Vec3(2.8, 0, 0));
        for (int i = 0; i < 6; i++) {
            FX.one(level, ModParticles.BILLOW_SMOKE.get(), c.add((random.nextDouble() - 0.5), (random.nextDouble() - 0.5), (random.nextDouble() - 0.5)),
                    new Vec3((random.nextDouble() - 0.5) * 0.18, 0.04 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.18));
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.6f + random.nextFloat() * 0.3f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.discard();
    }
}
