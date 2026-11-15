package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModSpells;
import io.redspace.ironsspellbooks.api.entity.NoKnockbackProjectile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Stationary orb created by Cursed Technique Lapse: Blue.
 * Server: pulls entities toward its centre and periodically damages anything inside it.
 * Client: draws the inward-spiralling vortex (the orb sprite itself is drawn by LapseBlueRenderer).
 */
public class LapseBlueOrb extends Projectile implements AntiMagicSusceptible, NoKnockbackProjectile {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(LapseBlueOrb.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RANGE = SynchedEntityData.defineId(LapseBlueOrb.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(LapseBlueOrb.class, EntityDataSerializers.INT);

    private static final int HUM_INTERVAL_TICKS = 40;

    private float damage = 2f;
    private int damageInterval = 10;
    private float pullStrength = 0.14f;
    private float maxPullSpeed = 0.9f;
    private boolean pullPlayers = true;
    private boolean pullItems = true;

    public LapseBlueOrb(EntityType<? extends LapseBlueOrb> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public LapseBlueOrb(Level level, LivingEntity owner) {
        this(ModEntities.LAPSE_BLUE_ORB.get(), level);
        this.setOwner(owner);
    }

    /** Called by the spell right after construction. */
    public void setup(float radius, float pullRange, float damage, int damageInterval, int lifetimeTicks,
                      float pullStrength, float maxPullSpeed, boolean pullPlayers, boolean pullItems) {
        this.entityData.set(DATA_RADIUS, radius);
        this.entityData.set(DATA_RANGE, pullRange);
        this.entityData.set(DATA_LIFETIME, lifetimeTicks);
        this.damage = damage;
        this.damageInterval = Math.max(1, damageInterval);
        this.pullStrength = pullStrength;
        this.maxPullSpeed = maxPullSpeed;
        this.pullPlayers = pullPlayers;
        this.pullItems = pullItems;
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public float getRange() {
        return this.entityData.get(DATA_RANGE);
    }

    public int getLifetime() {
        return this.entityData.get(DATA_LIFETIME);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 1.0f);
        this.entityData.define(DATA_RANGE, 6.0f);
        this.entityData.define(DATA_LIFETIME, 80);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            clientTick();
            return;
        }

        if (this.tickCount > getLifetime()) {
            this.discard();
            return;
        }

        if (this.tickCount % HUM_INTERVAL_TICKS == 1) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.LAPSE_BLUE_HUM.get(),
                    SoundSource.PLAYERS, 1.2f + getRadius() * 0.25f, 1.0f);
        }

        pullEntities();
        if (this.tickCount > 3 && this.tickCount % this.damageInterval == 0) {
            damageEntities();
        }
    }

    // ---- server ----------------------------------------------------------------------------------------------

    private boolean canAffect(Entity e) {
        if (e == this || e == getOwner() || !e.isAlive() || e.isSpectator() || e.isPassenger()) {
            return false;
        }
        if (e instanceof LapseBlueOrb) {
            return false;
        }
        if (e instanceof Player p && (!pullPlayers || p.getAbilities().flying)) {
            return false;
        }
        if (e instanceof ItemEntity && !pullItems) {
            return false;
        }
        return !(e instanceof LivingEntity) || !DamageSources.isFriendlyFireBetween(getOwner(), e);
    }

    private void pullEntities() {
        Vec3 center = this.getBoundingBox().getCenter();
        double range = getRange();
        AABB area = new AABB(center.x - range, center.y - range, center.z - range, center.x + range, center.y + range, center.z + range);
        List<Entity> targets = this.level().getEntities(this, area, this::canAffect);

        double clumpRadius = Math.max(1.2, getRadius() * 1.5);
        for (Entity e : targets) {
            Vec3 diff = center.subtract(e.getBoundingBox().getCenter());
            double dist = diff.length();
            if (dist > range || dist < 0.05) {
                continue;
            }
            double closeness = 1.0 - dist / range; // 0 at the edge of the range, 1 at the centre
            double accel = pullStrength * (0.35 + 0.65 * closeness);

            Vec3 motion = e.getDeltaMovement().add(diff.scale(accel / dist));
            if (dist < clumpRadius) {
                motion = motion.scale(0.55); // damp near the core so victims clump instead of oscillating
            }
            double speed = motion.length();
            if (speed > maxPullSpeed) {
                motion = motion.scale(maxPullSpeed / speed);
            }
            if (e.onGround()) {
                motion = motion.add(0, 0.06, 0); // lift off the ground so floor friction does not stop the pull
            }
            e.setDeltaMovement(motion);
            com.example.jjkspells.effect.OverloadEvents.forceMove(e);
            e.hurtMarked = true; // makes the server send the new velocity to players
            e.hasImpulse = true;
            e.fallDistance = 0;
        }
    }

    private void damageEntities() {
        Vec3 center = this.getBoundingBox().getCenter();
        double reach = Math.max(1.6, getRadius() + 0.9);
        double range = getRange();
        // everything the suction affects is damaged, not just what reaches the core
        AABB area = new AABB(center, center).inflate(range + 1.0);
        Entity owner = getOwner();
        Entity attacker = owner != null ? owner : this;

        for (Entity e : this.level().getEntities(this, area, this::canAffect)) {
            if (!(e instanceof LivingEntity living)) {
                continue;
            }
            double dist = center.distanceTo(living.getBoundingBox().getCenter());
            if (dist > range) {
                continue;
            }
            // full damage at the core, a bit less for targets still being dragged in from afar
            float mult = dist <= reach + living.getBbWidth() * 0.5 ? 1.0f : (float) (0.4 + 0.5 * (1.0 - dist / range));
            living.invulnerableTime = 0; // let every tick of damage land, so the configured interval is the real rate
            DamageSources.applyDamage(living, damage * mult, ModSpells.LAPSE_BLUE.get().getDamageSource(this, attacker));
        }
    }

    @Override
    public void onAntiMagic(MagicData playerMagicData) {
        this.discard();
    }

    // ---- client ----------------------------------------------------------------------------------------------

    private void clientTick() {
        float radius = getRadius();
        float range = getRange();
        int life = getLifetime();
        Vec3 c = this.getBoundingBox().getCenter();
        RandomSource rand = this.random;

        float grow = easeOut(Math.min(1f, this.tickCount / 6f)) * easeOut(Math.max(0f, Math.min(1f, (life - this.tickCount) / 10f)));
        if (grow <= 0.01f) {
            return;
        }

        // Inward spiralling vortex: every particle is told where the centre is (xd,yd,zd = vector to centre).
        int count = 2 + (int) (range * 0.9f);
        for (int i = 0; i < count; i++) {
            double angle = rand.nextDouble() * Math.PI * 2;
            double dist = range * grow * (0.3 + 0.7 * Math.sqrt(rand.nextDouble()));
            double height = (rand.nextDouble() - 0.5) * range * 0.45 * grow;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            this.level().addParticle(ModParticles.BLUE_VORTEX.get(), c.x + dx, c.y + height, c.z + dz, -dx, -height, -dz);
        }

        // Bright sparks dancing around the core.
        for (int i = 0; i < 2; i++) {
            double ox = (rand.nextDouble() - 0.5) * radius * 1.4;
            double oy = (rand.nextDouble() - 0.5) * radius * 1.4;
            double oz = (rand.nextDouble() - 0.5) * radius * 1.4;
            this.level().addParticle(ModParticles.BLUE_SPARK.get(), c.x + ox, c.y + oy, c.z + oz, ox * 0.08, oy * 0.08, oz * 0.08);
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    // ---- save data -------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Radius", getRadius());
        tag.putFloat("Range", getRange());
        tag.putInt("Lifetime", getLifetime());
        tag.putInt("Age", this.tickCount);
        tag.putFloat("Damage", damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Radius")) {
            this.entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        }
        if (tag.contains("Range")) {
            this.entityData.set(DATA_RANGE, tag.getFloat("Range"));
        }
        if (tag.contains("Lifetime")) {
            this.entityData.set(DATA_LIFETIME, tag.getInt("Lifetime"));
        }
        this.tickCount = tag.getInt("Age");
        this.damage = tag.getFloat("Damage");
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }
}
