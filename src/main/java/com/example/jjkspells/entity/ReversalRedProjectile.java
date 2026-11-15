package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModSpells;
import io.redspace.ironsspellbooks.api.entity.NoKnockbackProjectile;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Projectile fired by Cursed Technique Reversal: Red.
 * On impact it deals explosive damage and repels every entity around the impact point.
 */
public class ReversalRedProjectile extends AbstractMagicProjectile implements NoKnockbackProjectile {
    private float knockback = 1.2f;
    private float upwardKnockback = 0.4f;
    private float edgeDamageFraction = 0.3f;
    private float projectileSpeed = 1.1f;

    public ReversalRedProjectile(EntityType<? extends ReversalRedProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public ReversalRedProjectile(Level level, LivingEntity owner) {
        this(ModEntities.REVERSAL_RED.get(), level);
        this.setOwner(owner);
    }

    public void setKnockback(float horizontal, float upward) {
        this.knockback = horizontal;
        this.upwardKnockback = upward;
    }

    public void setEdgeDamageFraction(float fraction) {
        this.edgeDamageFraction = Mth.clamp(fraction, 0f, 1f);
    }

    public void setProjectileSpeed(float speed) {
        this.projectileSpeed = speed;
    }

    // ---- AbstractMagicProjectile -----------------------------------------------------------------------------

    @Override
    public float getSpeed() {
        return projectileSpeed;
    }

    @Override
    public Optional<Supplier<SoundEvent>> getImpactSound() {
        return Optional.of(ModSounds.REVERSAL_RED_IMPACT::get);
    }

    /** Client side, every tick: a hot red trail. */
    @Override
    public void trailParticles() {
        Vec3 motion = this.getDeltaMovement();
        Vec3 c = this.getBoundingBox().getCenter();
        for (int i = 0; i < 3; i++) {
            double back = this.random.nextDouble();
            Vec3 jitter = Utils.getRandomVec3(0.18);
            this.level().addParticle(ModParticles.RED_SPARK.get(),
                    c.x - motion.x * back + jitter.x, c.y - motion.y * back + jitter.y, c.z - motion.z * back + jitter.z,
                    jitter.x * 0.15, jitter.y * 0.15 + 0.01, jitter.z * 0.15);
        }
    }

    /** Server side, on impact: tell nearby clients to play the shockwave (xd = explosion radius). */
    @Override
    public void impactParticles(double x, double y, double z) {
        MagicManager.spawnParticles(this.level(), ModParticles.RED_IMPACT.get(), x, y, z, 0, getExplosionRadius(), 0, 0, 1, true);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit); // plays impact sound + particles
        if (!this.level().isClientSide) {
            explode(hit);
            this.discard();
        }
    }

    private void explode(HitResult hit) {
        Vec3 impact = hit.getLocation();
        float radius = getExplosionRadius();
        if (radius <= 0) {
            return;
        }
        Entity owner = getOwner();
        Entity attacker = owner != null ? owner : this;

        // Line-of-sight is checked from just behind the impact point so a wall the orb hit does not shield its own target.
        Vec3 heading = this.getDeltaMovement();
        Vec3 origin = impact;
        if (hit instanceof BlockHitResult bhr) {
            // step out of the surface that was hit, so the ground/wall itself never blocks the sight check
            origin = impact.add(Vec3.atLowerCornerOf(bhr.getDirection().getNormal()).scale(0.6));
        } else if (heading.lengthSqr() > 1.0E-6) {
            origin = impact.subtract(heading.normalize().scale(0.4));
        }

        AABB area = new AABB(impact, impact).inflate(radius);
        for (Entity e : this.level().getEntities(this, area, t -> t.isAlive() && !t.isSpectator() && t != owner)) {
            Vec3 centre = e.getBoundingBox().getCenter();
            double dist = centre.distanceTo(impact);
            if (dist > radius) {
                continue;
            }
            if (e instanceof LivingEntity && DamageSources.isFriendlyFireBetween(owner, e)) {
                continue;
            }
            if (dist > 0.75 && !Utils.hasLineOfSight(this.level(), origin, centre, true)) {
                continue;
            }

            double falloff = 1.0 - dist / radius; // 1 at the centre, 0 at the edge

            if (e instanceof LivingEntity living) {
                float dmg = (float) (this.damage * (edgeDamageFraction + (1.0 - edgeDamageFraction) * falloff));
                DamageSources.applyDamage(living, dmg, ModSpells.REVERSAL_RED.get().getDamageSource(this, attacker));
            }

            // Repulsion: shove everything directly away from the impact point (mostly sideways, plus a lift).
            Vec3 away = centre.subtract(impact);
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1.0E-3) {
                // directly above/below the impact: push in the direction the orb was travelling instead
                flat = new Vec3(heading.x, 0, heading.z);
                if (flat.lengthSqr() < 1.0E-3) flat = new Vec3(this.random.nextDouble() - 0.5, 0, this.random.nextDouble() - 0.5);
            }
            flat = flat.normalize();
            double push = knockback * (0.5 + 0.5 * falloff);
            double lift = upwardKnockback * (0.5 + 0.5 * falloff);
            if (e instanceof LivingEntity living) {
                double resist = living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                push *= Math.max(0.0, 1.0 - resist);
                lift *= Math.max(0.0, 1.0 - resist);
            }
            // replace (not add to) the current motion so earlier velocity/damage knockback cannot cancel the shove
            e.setDeltaMovement(flat.x * push, Math.max(e.getDeltaMovement().y, 0) * 0.5 + lift + Math.max(0, away.y) * 0.05 * push, flat.z * push);
            e.setOnGround(false);
            com.example.jjkspells.effect.OverloadEvents.forceMove(e);
            e.hurtMarked = true; // makes the server send the new velocity to players
            e.hasImpulse = true;
            e.fallDistance = 0;
        }
    }

    // ---- save data -------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Knockback", knockback);
        tag.putFloat("UpwardKnockback", upwardKnockback);
        tag.putFloat("EdgeDamageFraction", edgeDamageFraction);
        tag.putFloat("ProjectileSpeed", projectileSpeed);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Knockback")) {
            knockback = tag.getFloat("Knockback");
        }
        if (tag.contains("UpwardKnockback")) {
            upwardKnockback = tag.getFloat("UpwardKnockback");
        }
        if (tag.contains("EdgeDamageFraction")) {
            edgeDamageFraction = tag.getFloat("EdgeDamageFraction");
        }
        if (tag.contains("ProjectileSpeed")) {
            projectileSpeed = tag.getFloat("ProjectileSpeed");
        }
    }
}
