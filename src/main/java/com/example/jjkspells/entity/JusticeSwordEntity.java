package com.example.jjkspells.entity;

import com.example.jjkspells.GarenConfig;
import com.example.jjkspells.spells.SpellDamage;
import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.spells.FX;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Demacian Justice: a giant spectral sword. Timeline (ticks since spawn):
 * 0-14 a golden rune opens under the target and the light dims, 6-22 the sword materialises overhead,
 * 22-{@link #CRASH} it falls, at {@link #CRASH} it lands (damage + pillar + shockwave), then it dissolves into fragments
 * until {@link #END}. The entity follows its target until the moment of impact.
 */
public class JusticeSwordEntity extends Entity {
    public static final int CRASH = 30;
    public static final int END = 54;
    public static final int FALL_START = 22;
    /** Height of the sword's tip above the ground while it hangs in the air. */
    public static final float HANG_HEIGHT = 17f;
    public static final float SHOCK_RADIUS = 9f;

    private UUID ownerId;
    private UUID targetId;

    public JusticeSwordEntity(EntityType<? extends JusticeSwordEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static void summon(ServerLevel level, LivingEntity owner, LivingEntity target) {
        JusticeSwordEntity e = new JusticeSwordEntity(ModEntities.JUSTICE_SWORD.get(), level);
        e.ownerId = owner.getUUID();
        e.targetId = target.getUUID();
        e.setPos(target.getX(), target.getY(), target.getZ());
        level.addFreshEntity(e);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.6f);
    }

    /** Damage for a target at the given fraction (0..1) of its maximum health, linear between the configured points. */
    public static float damageFor(float healthFraction) {
        double f = Math.max(0.0, Math.min(1.0, healthFraction));
        double[] x = {1.0, 0.5, 0.2, 0.1, 0.0};
        double[] y = {GarenConfig.DJ_BASE_DAMAGE.get(), GarenConfig.DJ_DAMAGE_50.get(), GarenConfig.DJ_DAMAGE_20.get(), GarenConfig.DJ_DAMAGE_10.get(), GarenConfig.DJ_DAMAGE_0.get()};
        for (int i = 0; i < x.length - 1; i++) {
            if (f <= x[i] && f >= x[i + 1]) {
                double t = (x[i] - f) / (x[i] - x[i + 1]);
                return (float) Math.min(GarenConfig.DJ_DAMAGE_0.get(), y[i] + (y[i + 1] - y[i]) * t);
            }
        }
        return (float) (double) y[0];
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160.0 * 160.0;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel server = (ServerLevel) this.level();
        LivingEntity target = targetId != null && server.getEntity(targetId) instanceof LivingEntity le ? le : null;
        if (this.tickCount < CRASH) {
            if (target == null || !target.isAlive()) {
                this.discard();
                return;
            }
            this.setPos(target.getX(), target.getY(), target.getZ());
            if (this.tickCount % 4 == 0) {
                double a = this.random.nextDouble() * Math.PI * 2;
                FX.one(server, ModParticles.GOLD_MOTE.get(), position().add(Math.cos(a) * 2.5, 0.1, Math.sin(a) * 2.5), new Vec3(0, 0.12, 0));
            }
            if (this.tickCount == 1) {
                FX.emit(server, ModParticles.GG_RING_FLAT.get(), getX(), getY() + 0.1, getZ(), 0, 4.0, 0, 0, 1.0);
            }
            if (this.tickCount == FALL_START) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 3.0f, 0.5f);
            }
        } else if (this.tickCount == CRASH) {
            crash(server, target);
        } else if (this.tickCount >= END) {
            this.discard();
        }
    }

    private void crash(ServerLevel server, LivingEntity target) {
        Vec3 p = position();
        Entity owner = ownerId != null ? server.getEntity(ownerId) : null;
        LivingEntity attacker = owner instanceof LivingEntity le ? le : null;

        if (target != null && target.isAlive()) {
            executeHit(server, attacker, target);
        }

        // shove everyone else around the impact away (no damage: the sword only executes its target)
        for (Entity e : server.getEntities(this, new AABB(p, p).inflate(SHOCK_RADIUS, 4, SHOCK_RADIUS), t -> t instanceof LivingEntity && t.isAlive())) {
            if (e == target || e == attacker) continue;
            if (attacker != null && (e.isAlliedTo(attacker) || DamageSources.isFriendlyFireBetween(attacker, e))) continue;
            LivingEntity le = (LivingEntity) e;
            Vec3 away = le.position().subtract(p);
            Vec3 flat = new Vec3(away.x, 0, away.z);
            double d = flat.length();
            if (d > SHOCK_RADIUS) continue;
            flat = d < 0.01 ? new Vec3(1, 0, 0) : flat.scale(1.0 / d);
            double resist = Math.max(0.0, 1.0 - le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            double push = (0.4 + 1.0 * (1.0 - d / SHOCK_RADIUS)) * resist;
            le.setDeltaMovement(le.getDeltaMovement().add(flat.x * push, 0.35 * resist, flat.z * push));
            le.hurtMarked = true;
        }

        server.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.5f, 0.6f);
        server.playSound(null, p.x, p.y, p.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3.0f, 0.5f);
        server.playSound(null, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.2f);
        server.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 1.4f);

        FX.emit(server, ModParticles.GG_GLOW.get(), p.x, p.y + 1.0, p.z, 0, 8.0, 0, 0, 1.0);
        FX.emit(server, ModParticles.GR_GLOW.get(), p.x, p.y + 1.0, p.z, 0, 5.0, 0, 0, 1.0);
        for (int i = 0; i < 4; i++) {
            FX.emit(server, ModParticles.GG_RING_FLAT.get(), p.x, p.y + 0.12 + i * 0.03, p.z, 0, SHOCK_RADIUS * (0.55 + 0.25 * i), 0, 0, 1.0);
        }
        FX.emit(server, ModParticles.GR_RING_FLAT.get(), p.x, p.y + 0.1, p.z, 0, SHOCK_RADIUS * 1.3, 0, 0, 1.0);
        FX.emit(server, ModParticles.GG_RING.get(), p.x, p.y + 1.5, p.z, 0, SHOCK_RADIUS * 0.8, 0, 0, 1.0);
        // pillar of light: stacked beam streaks going straight up
        for (int i = 0; i < 7; i++) {
            double a = i / 7.0 * Math.PI * 2;
            Vec3 base = p.add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
            FX.one(server, ModParticles.GOLD_BEAM.get(), base, new Vec3(0, 30, 0));
        }
        FX.one(server, ModParticles.GOLD_BEAM.get(), p, new Vec3(0, 36, 0));
        for (int i = 0; i < 40; i++) {
            Vec3 v = new Vec3(this.random.nextGaussian(), this.random.nextDouble() * 1.4 + 0.2, this.random.nextGaussian()).scale(0.35);
            FX.one(server, ModParticles.GG_SHARD.get(), p.add(0, 0.5, 0), v);
        }
        for (int i = 0; i < 24; i++) {
            Vec3 v = new Vec3(this.random.nextGaussian(), this.random.nextDouble() * 0.8, this.random.nextGaussian()).scale(0.5);
            FX.one(server, ModParticles.GR_SPARK.get(), p.add(0, 0.3, 0), v);
        }
    }

    /** Applies the curve damage, then makes sure the full amount really arrives even through armour, caps and absorption. */
    private void executeHit(ServerLevel server, LivingEntity attacker, LivingEntity target) {
        float dmg = damageFor(target.getHealth() / Math.max(1f, target.getMaxHealth()));
        DamageSource src = attacker instanceof Player p ? attacker.damageSources().playerAttack(p)
                : attacker != null ? attacker.damageSources().mobAttack(attacker)
                : damageSources().magic();
        float before = target.getHealth() + target.getAbsorptionAmount();
        target.invulnerableTime = 0;
        float fracBefore = target.getHealth() / Math.max(1f, target.getMaxHealth());
        target.hurt(src, dmg);
        if (target.isAlive() && fracBefore < GarenConfig.DJ_EXECUTE_BELOW.get().floatValue()) {
            // finishing bonus: extra % of max health that ignores damage caps
            SpellDamage.percent(target, target.getMaxHealth() * GarenConfig.DJ_EXECUTE_PERCENT.get().floatValue(), attacker, src);
        }
        if (target.isAlive()) {
            float lost = before - (target.getHealth() + target.getAbsorptionAmount());
            float remaining = dmg - lost;
            if (remaining > 0f) {
                target.setAbsorptionAmount(0f);
                float left = target.getHealth() - remaining;
                if (left <= 0f) {
                    target.setHealth(0.001f);
                    target.invulnerableTime = 0;
                    target.die(src);
                } else {
                    target.setHealth(left);
                }
            }
        }
    }

    /** Client side: motes drifting up, fragments breaking off the dissolving sword. */
    private void clientFx() {
        if (this.tickCount > CRASH) {
            for (int i = 0; i < 3; i++) {
                double h = this.random.nextDouble() * HANG_HEIGHT * Math.max(0.0, 1.0 - (this.tickCount - CRASH) / (double) (END - CRASH));
                this.level().addParticle(ModParticles.GG_SHARD.get(),
                        getX() + (this.random.nextDouble() - 0.5) * 2.4, getY() + h, getZ() + (this.random.nextDouble() - 0.5) * 2.4,
                        (this.random.nextDouble() - 0.5) * 0.08, 0.02 + this.random.nextDouble() * 0.06, (this.random.nextDouble() - 0.5) * 0.08);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
