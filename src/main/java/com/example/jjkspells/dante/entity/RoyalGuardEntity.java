package com.example.jjkspells.dante.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.dante.DanteRegistry;
import com.example.jjkspells.dante.spell.RoyalGuardSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of "Royal Guard: Release". Follows the caster.
 * <pre>
 *   GUARD    up to GUARD_TICKS: every hit on the caster is cancelled and added to the stored energy (see DanteEvents)
 *   RELEASE  CHARGE ticks of gathering, then the shockwave hits everything in RADIUS with the stored energy
 *   END      the effect fades
 * </pre>
 */
public class RoyalGuardEntity extends Entity {
    public static final int PH_GUARD = 0;
    public static final int PH_RELEASE = 1;
    public static final int CHARGE = 14;
    public static final int WAVE_TICKS = 30;
    public static final int END = CHARGE + 50;
    public static final double REF_STORED = 1200.0;

    private static final Map<UUID, RoyalGuardEntity> ACTIVE = new HashMap<>();

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(RoyalGuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(RoyalGuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(RoyalGuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> STORED = SynchedEntityData.defineId(RoyalGuardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> HIT_TICK = SynchedEntityData.defineId(RoyalGuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HITS = SynchedEntityData.defineId(RoyalGuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> HIT_DIR = SynchedEntityData.defineId(RoyalGuardEntity.class, EntityDataSerializers.VECTOR3);

    private UUID ownerUuid;
    private boolean blasted = false;

    public RoyalGuardEntity(EntityType<? extends RoyalGuardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Starts the guard, or releases it early when it is already up. */
    public static void cast(Level level, LivingEntity caster) {
        ACTIVE.values().removeIf(Entity::isRemoved);
        RoyalGuardEntity old = ACTIVE.get(caster.getUUID());
        if (old != null) {
            old.release();
            return;
        }
        RoyalGuardEntity e = new RoyalGuardEntity(DanteRegistry.ROYAL, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        ACTIVE.put(caster.getUUID(), e);
        level.addFreshEntity(e);
    }

    /** The guard of this entity while it is still absorbing hits, else null. */
    public static RoyalGuardEntity guardOf(LivingEntity target) {
        RoyalGuardEntity g = ACTIVE.get(target.getUUID());
        return g != null && !g.isRemoved() && !g.blasted ? g : null;
    }

    /** A hit was absorbed. */
    public void block(float amount, Entity attacker) {
        double stored = Math.min(RoyalGuardSpell.MAX_STORED, this.entityData.get(STORED) + Math.max(0f, amount));
        this.entityData.set(STORED, (float) stored);
        this.entityData.set(HIT_TICK, tickCount);
        this.entityData.set(HITS, this.entityData.get(HITS) + 1);
        Vec3 dir = new Vec3(0, 0, 1);
        if (attacker != null) {
            Vec3 d = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0).subtract(position().add(0, 1.2, 0));
            if (d.lengthSqr() > 1.0E-4) {
                dir = d.normalize();
            }
            if (attacker instanceof LivingEntity le && le.distanceToSqr(this) < 64) {
                le.setDeltaMovement(le.getDeltaMovement().add(dir.x * 0.5, 0.15, dir.z * 0.5));
                le.hurtMarked = true;
            }
        } else {
            dir = new Vec3(Math.cos(tickCount * 2.4), 0.2, Math.sin(tickCount * 2.4)).normalize();
        }
        this.entityData.set(HIT_DIR, new Vector3f((float) dir.x, (float) dir.y, (float) dir.z));
        if (level() instanceof ServerLevel bsl) {
            Vec3 hp = position().add(dir.x * 1.6, 1.2 + dir.y * 1.6, dir.z * 1.6);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(bsl, hp, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.4f);   // hit absorbed
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(bsl, hp, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.0f);
        }
        level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 3.0f, 0.7f);
        level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.0f, 1.7f);
        level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 0.6f + (float) (stored / RoyalGuardSpell.MAX_STORED));
    }

    private void release() {
        if (phase() == PH_GUARD && tickCount - phaseTick() >= 8) {
            this.entityData.set(PHASE, PH_RELEASE);
            this.entityData.set(PHASE_TICK, tickCount);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 4.0f, 0.5f);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 3.0f, 0.6f);
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(PHASE, PH_GUARD);
        this.entityData.define(PHASE_TICK, 0);
        this.entityData.define(STORED, 0f);
        this.entityData.define(HIT_TICK, -100);
        this.entityData.define(HITS, 0);
        this.entityData.define(HIT_DIR, new Vector3f(0, 0, 1));
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

    @Override
    public void remove(RemovalReason reason) {
        if (ownerUuid != null) {
            ACTIVE.remove(ownerUuid, this);
        }
        super.remove(reason);
    }

    public int phase() {
        return this.entityData.get(PHASE);
    }

    public int phaseTick() {
        return this.entityData.get(PHASE_TICK);
    }

    public float stored() {
        return this.entityData.get(STORED);
    }

    public int hitTick() {
        return this.entityData.get(HIT_TICK);
    }

    public int hits() {
        return this.entityData.get(HITS);
    }

    public Vec3 hitDir() {
        Vector3f v = this.entityData.get(HIT_DIR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
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

    // ------------------------------------------------------------------ server

    private boolean isEnemy(LivingEntity t, LivingEntity owner) {
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

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        Entity oe = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        if (!(oe instanceof LivingEntity owner) || !owner.isAlive()) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        int age = tickCount - phaseTick();
        if (phase() == PH_GUARD) {
            if (tickCount == 1) {
                level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 2.0f, 0.7f);
                level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.3f);
                com.example.jjkspells.vfx.sprite.SpriteFx.sparks(sl, position().add(0, 1.2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.2f);
            }
            if (age >= RoyalGuardSpell.GUARD_TICKS) {
                release();
                if (phase() == PH_GUARD) {
                    this.entityData.set(PHASE, PH_RELEASE);
                    this.entityData.set(PHASE_TICK, tickCount);
                }
            }
            return;
        }
        if (age < CHARGE) {
            Vec3 dm = owner.getDeltaMovement();
            owner.setDeltaMovement(0, Math.min(0, dm.y), 0);
            owner.hurtMarked = true;
            return;
        }
        if (age == CHARGE && !blasted) {
            blasted = true;
            blast(owner);
        }
        if (age >= END) {
            discard();
        }
    }

    private void blast(LivingEntity owner) {
        double stored = this.entityData.get(STORED);
        double flat = Math.min(RoyalGuardSpell.DAMAGE_CAP, RoyalGuardSpell.BASE_DAMAGE + stored * RoyalGuardSpell.STORED_MULT);
        double r = RoyalGuardSpell.RADIUS;
        Vec3 c = owner.position();
        level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0f, 0.5f);
        level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 0.7f);
        level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.6f);
        level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3.0f, 0.5f);
        if (level() instanceof ServerLevel bsl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(bsl, c.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4.0f);   // release shockwave
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(bsl, c.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 4.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(bsl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (r / 3.0));
        }
        AABB box = new AABB(c.x - r, c.y - 6, c.z - r, c.x + r, c.y + 14, c.z + r);
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - c.x;
            double dz = t.getZ() - c.z;
            if (dx * dx + dz * dz > r * r || !isEnemy(t, owner)) {
                continue;
            }
            t.invulnerableTime = 0;
            net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
            float flatDmg = (float) (flat);
            if (flatDmg > 0) {
                io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
            }
            if (t.isAlive()) {
                com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * RoyalGuardSpell.PERCENT), owner, dsrc);
            }
            Vec3 out = new Vec3(dx, 0, dz);
            out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(1.6);
            t.setDeltaMovement(out.x, 0.8, out.z);
            t.hurtMarked = true;
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, true, true));
            t.setSecondsOnFire(4);
            if (level() instanceof ServerLevel esl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(esl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 1.8f);
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        Entity own = owner();
        if (own == null) {
            return;
        }
        int age = tickCount - phaseTick();
        if (phase() == PH_GUARD && tickCount - hitTick() == 1) {
            Vec3 d = hitDir();
            for (int i = 0; i < 14; i++) {
                level().addParticle(ParticleTypes.FLAME, own.getX() + d.x * 1.6, own.getY() + 1.2 + d.y * 1.6, own.getZ() + d.z * 1.6,
                        d.x * 0.3 + (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.3) * 0.3, d.z * 0.3 + (random.nextDouble() - 0.5) * 0.4);
            }
        }
        if (phase() == PH_RELEASE && age == CHARGE) {
            for (int i = 0; i < 120; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double s = 0.6 + random.nextDouble() * 2.2;
                level().addParticle(ParticleTypes.FLAME, own.getX(), own.getY() + 0.5, own.getZ(), Math.cos(ang) * s, 0.1 + random.nextDouble() * 0.9, Math.sin(ang) * s);
            }
        }
    }
}
