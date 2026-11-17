package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yasuo.YasuoRegistry;
import com.example.jjkspells.yasuo.spell.WindboundDuelSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * Controller of "Windbound Duel". Sits at the middle of the arena. The enemy is kept inside the ring, everybody else is kept out, foreign projectiles are
 * torn apart at the wall, the enemy is cut once a second, and the duel ends when time runs out or the enemy dies.
 */
public class WindboundDuelEntity extends Entity {
    public static final int RISE = 10;
    public static final int DURATION = WindboundDuelSpell.DURATION_TICKS;
    public static final int FADE = 14;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(WindboundDuelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(WindboundDuelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(WindboundDuelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> END_TICK = SynchedEntityData.defineId(WindboundDuelEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;
    private UUID targetUuid;

    public WindboundDuelEntity(EntityType<? extends WindboundDuelEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    private static boolean enemy(LivingEntity t, LivingEntity owner) {
        if (t == owner || !t.isAlive() || t.isSpectator() || t instanceof ArmorStand) {
            return false;
        }
        if (owner != null) {
            if (owner.isAlliedTo(t)) {
                return false;
            }
            if (t instanceof TamableAnimal ta && ta.isOwnedBy(owner)) {
                return false;
            }
        }
        return true;
    }

    /** Picks the enemy the caster faces; null if there is none. */
    public static WindboundDuelEntity create(Level level, LivingEntity caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        double r = WindboundDuelSpell.TARGET_RANGE;
        LivingEntity best = null;
        double bestScore = -1;
        AABB box = caster.getBoundingBox().inflate(r);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!enemy(t, caster)) {
                continue;
            }
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > r || dist < 0.1) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            double score = dot * 10.0 - dist * 0.1;
            if (dot > 0.35 && score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        if (best == null) {
            return null;
        }
        WindboundDuelEntity e = new WindboundDuelEntity(YasuoRegistry.DUEL, level);
        e.ownerUuid = caster.getUUID();
        e.targetUuid = best.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.entityData.set(TARGET_ID, best.getId());
        Vec3 mid = caster.position().add(best.position()).scale(0.5);
        double half = caster.position().distanceTo(best.position()) * 0.5;
        e.entityData.set(RADIUS, (float) Math.max(WindboundDuelSpell.MIN_RADIUS, half + 4.0));
        e.setPos(mid.x, Math.min(caster.getY(), best.getY()), mid.z);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(TARGET_ID, -1);
        this.entityData.define(RADIUS, 8f);
        this.entityData.define(END_TICK, -1);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    public double radius() {
        return this.entityData.get(RADIUS);
    }

    /** Tick the duel ended on, or -1 while it runs. */
    public int endTick() {
        return this.entityData.get(END_TICK);
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    public Entity target() {
        int id = this.entityData.get(TARGET_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (targetUuid != null && !this.level().isClientSide) {
            DuelEvents.DUELS.remove(targetUuid);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ server

    private void tp(LivingEntity t, Vec3 p) {
        if (t instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, t.getYRot(), t.getXRot());
        } else {
            t.moveTo(p.x, p.y, p.z, t.getYRot(), t.getXRot());
        }
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        int end = endTick();
        if (end >= 0) {
            if (tickCount > end + FADE) {
                discard();
            }
            return;
        }
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        Entity te = sl.getEntity(targetUuid);
        LivingEntity target = te instanceof LivingEntity lt ? lt : null;
        Vec3 c = position();
        double R = radius();

        if (tickCount == 1) {
            DuelEvents.DUELS.put(targetUuid, ownerUuid);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 1.6f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.8f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 6.0));
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3f);
        }
        boolean over = tickCount >= DURATION || target == null || !target.isAlive();
        if (over) {
            if (target != null && !target.isAlive() && owner != null && owner.isAlive()) {
                owner.heal((float) (owner.getMaxHealth() * WindboundDuelSpell.KILL_HEAL_PERCENT));
            }
            this.entityData.set(END_TICK, tickCount);
            DuelEvents.DUELS.remove(targetUuid);
            level().playSound(null, c.x, c.y + 3, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 3.0f, 1.2f);
            level().playSound(null, c.x, c.y + 3, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 1.2f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) (R * 2.0 / 6.0));
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 3.5f);
            return;
        }
        if (tickCount % 40 == 0) {
            level().playSound(null, c.x, c.y + 4, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
        if (tickCount < RISE) {
            return;
        }
        // keep the enemy in
        double dx = target.getX() - c.x;
        double dz = target.getZ() - c.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > R - 0.3) {
            double k = (R - 1.4) / Math.max(dist, 1.0E-3);
            tp(target, new Vec3(c.x + dx * k, target.getY(), c.z + dz * k));
            target.setDeltaMovement(0, target.getDeltaMovement().y, 0);
            target.hurtMarked = true;
            level().playSound(null, target.getX(), target.getY() + 1, target.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.5f, 1.5f);
        } else if (dist > R - 1.8) {
            double k = 0.35 / Math.max(dist, 1.0E-3);
            target.setDeltaMovement(-dx * k + target.getDeltaMovement().x * 0.3, target.getDeltaMovement().y, -dz * k + target.getDeltaMovement().z * 0.3);
            target.hurtMarked = true;
        }
        // keep the others out
        double reach = R + 4.0;
        AABB box = new AABB(c.x - reach, c.y - 4, c.z - reach, c.x + reach, c.y + WindboundDuelSpell.HEIGHT + 2, c.z + reach);
        for (LivingEntity o : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (o == owner || o == target || !o.isAlive() || o.isSpectator() || (owner != null && owner.isAlliedTo(o))) {
                continue;
            }
            if (o instanceof TamableAnimal ta && owner != null && ta.isOwnedBy(owner)) {
                continue;
            }
            double ox = o.getX() - c.x;
            double oz = o.getZ() - c.z;
            double od = Math.sqrt(ox * ox + oz * oz);
            if (od < R + 0.8) {
                double k = 0.9 / Math.max(od, 1.0E-3);
                o.setDeltaMovement(ox * k, 0.25, oz * k);
                o.hurtMarked = true;
            }
        }
        for (Projectile pr : level().getEntitiesOfClass(Projectile.class, box)) {
            Entity po = pr.getOwner();
            if (po != null && (po == owner || (owner != null && owner.isAlliedTo(po)))) {
                continue;
            }
            double px = pr.getX() - c.x;
            double pz = pr.getZ() - c.z;
            double ox = pr.xOld - c.x;
            double oz = pr.zOld - c.z;
            double pd = Math.sqrt(px * px + pz * pz);
            double od = Math.sqrt(ox * ox + oz * oz);
            if ((pd < R) != (od < R) || Math.abs(pd - R) < 0.8) {
                level().playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 1.4f);
                pr.discard();
            }
        }
        // the wind cuts the enemy once a second
        if (tickCount % 20 == 0) {
            Vec3 keep = target.getDeltaMovement();
            target.invulnerableTime = 0;
            net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
            float flatDmg = (float) (WindboundDuelSpell.PULSE_FLAT);
            if (flatDmg > 0) {
                io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(target, flatDmg, dsrc);
            }
            if (target.isAlive()) {
                com.example.jjkspells.spells.SpellDamage.percent(target, (float) (target.getMaxHealth() * WindboundDuelSpell.PULSE_PERCENT), owner, dsrc);
            }
            target.setDeltaMovement(keep);
            target.hurtMarked = true;
            level().playSound(null, target.getX(), target.getY() + 1, target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 1.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, target.position().add(0, target.getBbHeight() * 0.5, 0), new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, 1.0), random.nextFloat() * 360f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, target.position().add(0, target.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.5f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        if (endTick() >= 0 || tickCount < 2) {
            return;
        }
        double R = radius();
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * R, getY() + 0.2 + random.nextDouble() * 2, getZ() + Math.sin(a) * R, -Math.sin(a) * 0.25, 0.02, Math.cos(a) * 0.25);
        }
    }
}
