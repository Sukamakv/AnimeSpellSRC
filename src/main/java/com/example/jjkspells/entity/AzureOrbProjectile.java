package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A spinning disc of blue chakra fire. It flies straight (with optional gentle homing), and on contact with a block or
 * a creature it detonates in a large blue-fire explosion. Every living thing caught in the blast loses
 * {@link #PERCENT_OF_MAX_HEALTH} of ITS OWN maximum health. The caster (and the caster's own colossus) are never hurt.
 *
 * Thrown by both the Azure Colossus and the Azure Orb Barrage spell.
 */
public class AzureOrbProjectile extends Projectile {
    /** Fraction of the victim's max health removed by one explosion (5%). */
    public static final float PERCENT_OF_MAX_HEALTH = 0.05f;

    private float speed = 1.2f;
    private float radius = 6f;
    private int maxLife = 90;
    /** 0 = fly straight, 1 = strongly steer toward the homing point. */
    private float homing = 0f;
    private Vec3 homingPoint = null;

    public AzureOrbProjectile(EntityType<? extends AzureOrbProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public AzureOrbProjectile(Level level, LivingEntity owner) {
        this(ModEntities.AZURE_ORB.get(), level);
        this.setOwner(owner);
    }

    public AzureOrbProjectile setRadius(float radius) { this.radius = radius; return this; }
    public AzureOrbProjectile setMaxLife(int ticks) { this.maxLife = ticks; return this; }

    /** Launches the orb from its current position along {@code dir}. */
    public AzureOrbProjectile launch(Vec3 dir, float speed) {
        this.speed = speed;
        this.setDeltaMovement(dir.normalize().scale(speed));
        return this;
    }

    /** Makes the orb curve slowly toward {@code point}. */
    public AzureOrbProjectile setHoming(Vec3 point, float strength) {
        this.homingPoint = point;
        this.homing = strength;
        return this;
    }

    @Override
    protected void defineSynchedData() {
        // nothing to sync
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 motion = this.getDeltaMovement();

        if (!this.level().isClientSide) {
            if (homingPoint != null && homing > 0f && motion.lengthSqr() > 1.0E-4) {
                Vec3 want = homingPoint.subtract(this.position());
                if (want.lengthSqr() > 1.0) {
                    Vec3 dir = motion.normalize().scale(1.0 - homing).add(want.normalize().scale(homing)).normalize();
                    motion = dir.scale(speed);
                    this.setDeltaMovement(motion);
                }
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.setPos(hit.getLocation());
                explode();
                return;
            }
            if (this.tickCount > maxLife) {
                explode();
                return;
            }
        }

        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);

        if (this.level().isClientSide) {
            trail(motion);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        if (target instanceof AzureOrbProjectile) return false;
        Entity owner = this.getOwner();
        if (target instanceof AzureColossus c && owner != null && c.isOwnedBy(owner)) return false;
        if (owner instanceof AzureColossus c && target == c.getOwnerEntity()) return false;
        return !(target instanceof LivingEntity) || !DamageSources.isFriendlyFireBetween(owner, target);
    }

    /** Client: a streak of blue fire behind the orb. */
    private void trail(Vec3 motion) {
        for (int i = 0; i < 4; i++) {
            double back = this.random.nextDouble();
            Vec3 j = Utils.getRandomVec3(0.5);
            this.level().addParticle(ModParticles.AZURE_FLAME.get(),
                    this.getX() - motion.x * back + j.x, this.getY() + 0.6 - motion.y * back + j.y, this.getZ() - motion.z * back + j.z,
                    j.x * 0.05, j.y * 0.05 + 0.02, j.z * 0.05);
        }
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(ModParticles.AZURE_EMBER.get(), this.getX(), this.getY() + 0.6, this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.1, (this.random.nextDouble() - 0.5) * 0.1, (this.random.nextDouble() - 0.5) * 0.1);
        }
    }

    private void explode() {
        if (this.level().isClientSide || this.isRemoved()) return;
        ServerLevel server = (ServerLevel) this.level();
        Vec3 c = this.position().add(0, this.getBbHeight() * 0.5, 0);
        Entity owner = this.getOwner();
        Entity attacker = owner != null ? owner : this;

        AABB area = new AABB(c, c).inflate(radius);
        for (Entity e : server.getEntities(this, area, t -> t.isAlive() && !t.isSpectator())) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e == owner) continue;
            if (owner != null && (e.isAlliedTo(owner) || DamageSources.isFriendlyFireBetween(owner, e))) continue;
            if (e instanceof AzureColossus col && owner != null && col.isOwnedBy(owner)) continue;
            if (owner instanceof AzureColossus col && e == col.getOwnerEntity()) continue;

            Vec3 centre = e.getBoundingBox().getCenter();
            double dist = centre.distanceTo(c);
            // the explosion is spherical: use the closest point of the victim's box so big targets are caught too
            double edge = e.getBoundingBox().distanceToSqr(c);
            if (edge > radius * radius) continue;

            float dmg = Math.max(1f, living.getMaxHealth() * PERCENT_OF_MAX_HEALTH);
            living.invulnerableTime = 0; // every orb of a barrage must connect, not just the first
            if (attacker instanceof LivingEntity atk) SpellDamage.percent(living, dmg, atk, ModSpells.AZURE_ORB_BARRAGE.get().getDamageSource(this, attacker));
            else DamageSources.applyDamage(living, dmg, ModSpells.AZURE_ORB_BARRAGE.get().getDamageSource(this, attacker));

            // shove away from the blast
            Vec3 away = centre.subtract(c);
            Vec3 flat = new Vec3(away.x, 0, away.z);
            flat = flat.lengthSqr() < 1.0E-3 ? new Vec3(this.random.nextDouble() - 0.5, 0, this.random.nextDouble() - 0.5) : flat;
            flat = flat.normalize();
            double falloff = 1.0 - Math.min(1.0, dist / radius);
            double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double push = (0.5 + 0.9 * falloff) * Math.max(0.0, resist);
            living.setDeltaMovement(living.getDeltaMovement().add(flat.x * push, 0.25 * Math.max(0.0, resist), flat.z * push));
            living.hurtMarked = true;
        }

        // ---- the blue fire explosion ------------------------------------------------------------
        server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.7f);
        server.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.5f, 0.5f);
        FX.emit(server, ModParticles.AZURE_GLOW.get(), c.x, c.y, c.z, 0, radius * 1.1, 0, 0, 1.0);
        FX.emit(server, ModParticles.AZURE_RING.get(), c.x, c.y, c.z, 0, radius * 1.3, 0, 0, 1.0);
        FX.emit(server, ModParticles.AZURE_RING_FLAT.get(), c.x, c.y + 0.2, c.z, 0, radius * 1.6, 0, 0, 1.0);
        int blasts = 14 + (int) radius * 2;
        for (int i = 0; i < blasts; i++) {
            Vec3 d = Utils.getRandomVec3(1.0).normalize().scale(this.random.nextDouble() * radius * 0.7);
            FX.emit(server, ModParticles.AZURE_BLAST.get(), c.x + d.x, c.y + d.y * 0.7, c.z + d.z, 0,
                    d.x * 0.03, 0.04 + this.random.nextDouble() * 0.06, d.z * 0.03, 1.0);
        }
        for (int i = 0; i < 26; i++) {
            Vec3 d = Utils.getRandomVec3(1.0).normalize().scale(0.4 + this.random.nextDouble() * 0.9);
            FX.emit(server, ModParticles.AZURE_FLAME.get(), c.x, c.y, c.z, 0, d.x, d.y + 0.2, d.z, 1.0);
        }
        for (int i = 0; i < 24; i++) {
            Vec3 d = Utils.getRandomVec3(1.0).normalize().scale(0.3 + this.random.nextDouble() * 0.7);
            FX.emit(server, ModParticles.AZURE_EMBER.get(), c.x, c.y, c.z, 0, d.x, d.y + 0.3, d.z, 1.0);
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Radius", radius);
        tag.putFloat("Speed", speed);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Radius")) radius = tag.getFloat("Radius");
        if (tag.contains("Speed")) speed = tag.getFloat("Speed");
    }
}
