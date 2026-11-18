package com.example.jjkspells.dante.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.dante.DanteRegistry;
import com.example.jjkspells.dante.spell.DevilTriggerSpell;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of "Devil Trigger: Crimson Awakening". Follows the caster.
 * <pre>
 *   1                       awakening: buffs are applied, shockwave hits everything in OPEN_RADIUS
 *   every 10 ticks          the burning aura hits enemies near the caster
 *   PULSE0 + k * PULSE_EVERY  Devil Burst: blast in PULSE_RADIUS
 *   DURATION                devil blood calms down: buffs removed, small burst
 * </pre>
 */
public class DevilTriggerEntity extends Entity {
    public static final int DURATION = DevilTriggerSpell.DURATION_TICKS;
    public static final int END = DURATION + 34;

    public static final UUID DMG_MOD = UUID.fromString("b8d2e3f4-5c6b-4d7e-9fa0-1b2c3d4e5f01");
    public static final UUID RESIST_MOD = UUID.fromString("b8d2e3f4-5c6b-4d7e-9fa0-1b2c3d4e5f02");

    /** Server only: one active Devil Trigger per caster. */
    private static final Map<UUID, DevilTriggerEntity> ACTIVE = new HashMap<>();

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(DevilTriggerEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;
    private boolean buffed;

    public DevilTriggerEntity(EntityType<? extends DevilTriggerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static DevilTriggerEntity create(Level level, LivingEntity caster) {
        DevilTriggerEntity old = ACTIVE.remove(caster.getUUID());
        if (old != null) {
            old.discard();
        }
        DevilTriggerEntity e = new DevilTriggerEntity(DanteRegistry.TRIGGER, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        ACTIVE.put(caster.getUUID(), e);
        return e;
    }

    /** Tick of Devil Burst k. */
    public static int pulseTick(int k) {
        return DevilTriggerSpell.PULSE0 + k * DevilTriggerSpell.PULSE_EVERY;
    }

    public static int pulseCount() {
        return (DURATION - DevilTriggerSpell.PULSE0 - 1) / DevilTriggerSpell.PULSE_EVERY + 1;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
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

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            if (ownerUuid != null && ACTIVE.get(ownerUuid) == this) {
                ACTIVE.remove(ownerUuid);
            }
            clearBuffs();
        }
        super.remove(reason);
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

    private LivingEntity owner(ServerLevel sl) {
        Entity e = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        return e instanceof LivingEntity le ? le : null;
    }

    private void applyBuffs(LivingEntity owner) {
        buffed = true;
        AttributeInstance dmg = owner.getAttribute(ModAttributes.MAX_HP_DAMAGE.get());
        if (dmg != null) {
            dmg.removeModifier(DMG_MOD);
            dmg.addTransientModifier(new AttributeModifier(DMG_MOD, "dante_dt_damage", DevilTriggerSpell.DAMAGE_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        AttributeInstance res = owner.getAttribute(ModAttributes.MAX_HP_RESIST.get());
        if (res != null) {
            res.removeModifier(RESIST_MOD);
            res.addTransientModifier(new AttributeModifier(RESIST_MOD, "dante_dt_resist", DevilTriggerSpell.RESIST_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    private void clearBuffs() {
        if (!buffed || !(this.level() instanceof ServerLevel sl)) {
            return;
        }
        buffed = false;
        LivingEntity owner = owner(sl);
        if (owner == null) {
            return;
        }
        AttributeInstance dmg = owner.getAttribute(ModAttributes.MAX_HP_DAMAGE.get());
        if (dmg != null) {
            dmg.removeModifier(DMG_MOD);
        }
        AttributeInstance res = owner.getAttribute(ModAttributes.MAX_HP_RESIST.get());
        if (res != null) {
            res.removeModifier(RESIST_MOD);
        }
    }

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

    private List<LivingEntity> enemies(LivingEntity owner, double radius) {
        Vec3 c = owner.position();
        AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (t.distanceToSqr(owner) <= radius * radius && isEnemy(t, owner)) {
                out.add(t);
            }
        }
        return out;
    }

    private void hit(LivingEntity t, LivingEntity owner, double flat, double percent) {
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        float flatDmg = (float) (flat);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, dsrc);
        }
        t.setDeltaMovement(keep);
        t.hurtMarked = true;
    }

    private void knock(LivingEntity t, Vec3 c, double out, double up) {
        Vec3 d = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z);
        d = d.lengthSqr() < 0.01 ? Vec3.ZERO : d.normalize().scale(out);
        t.setDeltaMovement(t.getDeltaMovement().add(d.x, up, d.z));
        t.hurtMarked = true;
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = owner(sl);
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        Vec3 c = owner.position();

        if (tickCount == 1) {
            applyBuffs(owner);
            int d = DURATION + 20;
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, d, 1, false, false, true));
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, d, 1, false, false, true));
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, d, 1, false, false, true));
            owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, d, 0, false, false, true));
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.6f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 0.7f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 0.8f);
            for (LivingEntity t : enemies(owner, DevilTriggerSpell.OPEN_RADIUS)) {
                hit(t, owner, DevilTriggerSpell.OPEN_FLAT, DevilTriggerSpell.OPEN_PERCENT);
                knock(t, c, 1.4, 0.7);
                t.setSecondsOnFire(3);
            }
        }
        if (tickCount > 10 && tickCount < DURATION && tickCount % 10 == 0) {
            for (LivingEntity t : enemies(owner, DevilTriggerSpell.AURA_RADIUS)) {
                hit(t, owner, DevilTriggerSpell.AURA_FLAT, DevilTriggerSpell.AURA_PERCENT);
                t.setSecondsOnFire(2);
            }
        }
        if (tickCount % 40 == 0 && tickCount < DURATION) {
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.6f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.8f);
        }
        for (int k = 0; k < pulseCount(); k++) {
            if (tickCount == pulseTick(k)) {
                level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.5f, 0.9f);
                level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.5f);
                for (LivingEntity t : enemies(owner, DevilTriggerSpell.PULSE_RADIUS)) {
                    hit(t, owner, DevilTriggerSpell.PULSE_FLAT, DevilTriggerSpell.PULSE_PERCENT);
                    knock(t, c, 1.0, 0.4);
                }
            }
        }
        if (tickCount == DURATION) {
            clearBuffs();
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.0f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0f, 0.8f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        Entity own = owner();
        double x = own != null ? own.getX() : getX();
        double y = own != null ? own.getY() : getY();
        double z = own != null ? own.getZ() : getZ();
        if (tickCount < DURATION) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 0.6 + random.nextDouble() * 1.6;
                level().addParticle(ParticleTypes.FLAME, x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r, 0, 0.06 + random.nextDouble() * 0.08, 0);
            }
        }
        if (tickCount == 1 || tickCount == DURATION) {
            for (int i = 0; i < 70; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.4 + random.nextDouble() * 1.6;
                level().addParticle(ParticleTypes.FLAME, x, y + 1, z, Math.cos(a) * s, 0.1 + random.nextDouble() * 0.6, Math.sin(a) * s);
            }
        }
    }
}
