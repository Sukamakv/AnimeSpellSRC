package com.example.jjkspells.dante.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.dante.DanteRegistry;
import com.example.jjkspells.dante.spell.RoundTripSpell;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller of "Round Trip: Twin Rebellion". The entity sits at the cast point A; the loop direction is synced.
 * <pre>
 *   0 .. START          wind-up: both blades materialise beside the caster
 *   START .. START+TRIP the blades fly their mirrored loops (see swordPos), shredding and dragging enemies every 3 ticks
 *   START + TRIP/2      the blades meet at the far end: slam
 *   START + TRIP        the blades are back: ring of blades around the caster
 *   .. END              fade
 * </pre>
 */
public class RoundTripEntity extends Entity {
    public static final int START = 10;
    public static final int TRIP = 64;
    public static final int APEX = START + TRIP / 2;
    public static final int HOME = START + TRIP;
    public static final int END = HOME + 34;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(RoundTripEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(RoundTripEntity.class, EntityDataSerializers.VECTOR3);

    private UUID ownerUuid;

    public RoundTripEntity(EntityType<? extends RoundTripEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static RoundTripEntity create(Level level, LivingEntity caster) {
        RoundTripEntity e = new RoundTripEntity(DanteRegistry.ROUND, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : dir.normalize();
        e.entityData.set(DIR, new Vector3f((float) dir.x, 0f, (float) dir.z));
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    /**
     * Centre of blade s (-1 or +1) at tick tt. Before START it hovers beside the caster. Then it flies an ellipse loop: out on one side, round the far
     * end, home on the other side; over the last 30% of the trip the loop is blended onto the caster's current position.
     */
    public static Vec3 swordPos(Vec3 origin, Vec3 dir, int s, double tt, Vec3 ownerPos) {
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        if (tt < START) {
            double k = Math.min(1.0, tt / START);
            return ownerPos.add(side.scale(s * (1.2 + 1.2 * k))).add(0, 1.4 + 0.5 * k, 0).subtract(dir.scale(0.4 * k));
        }
        double u = Math.min(1.0, (tt - START) / TRIP);
        double a = u * Math.PI * 2;
        double fwd = RoundTripSpell.LENGTH * (1.0 - Math.cos(a)) / 2.0;
        double lat = s * RoundTripSpell.WIDTH * Math.sin(a);
        Vec3 base = origin.add(dir.scale(fwd)).add(side.scale(lat));
        double w = Math.max(0.0, Math.min(1.0, (u - 0.7) / 0.3));
        w = w * w * (3 - 2 * w);
        base = base.add(ownerPos.subtract(origin).scale(w));
        double yy = 1.4 + 0.9 * Math.sin(a * 2 + (s > 0 ? 0 : Math.PI));
        return new Vec3(base.x, origin.y + yy + (ownerPos.y - origin.y) * w, base.z);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(DIR, new Vector3f(0, 0, 1));
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

    public Vec3 dir() {
        Vector3f v = this.entityData.get(DIR);
        return new Vec3(v.x(), 0, v.z());
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

    private List<LivingEntity> near(Vec3 c, double radius, LivingEntity owner) {
        AABB box = new AABB(c.x - radius, c.y - 8, c.z - radius, c.x + radius, c.y + 12, c.z + radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - c.x;
            double dz = t.getZ() - c.z;
            if (dx * dx + dz * dz <= radius * radius && isEnemy(t, owner)) {
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

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        Vec3 a = position();
        Vec3 dir = dir();
        Vec3 op = owner != null ? owner.position() : a;

        if (tickCount == 1) {
            if (owner != null) {
                owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, HOME, 1, false, false, true));
            }
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.6f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.5f, 1.6f);
        }
        if (tickCount == START) {
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 4.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3.0f, 0.6f);
        }
        if (tickCount >= START && tickCount < HOME && (tickCount - START) % 3 == 0) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = swordPos(a, dir, s, tickCount, op);
                AABB box = new AABB(c, c).inflate(RoundTripSpell.SWORD_RADIUS);
                for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                    Vec3 tc = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ());
                    if (tc.distanceTo(c) <= RoundTripSpell.SWORD_RADIUS && isEnemy(t, owner)) {
                        hit(t, owner, RoundTripSpell.SPIN_FLAT, RoundTripSpell.SPIN_PERCENT);
                        Vec3 pull = c.subtract(tc).scale(0.22);
                        double len = pull.length();
                        if (len > 0.9) {
                            pull = pull.scale(0.9 / len);
                        }
                        t.setDeltaMovement(pull);
                        t.hurtMarked = true;
                        t.fallDistance = 0;
                    }
                }
                level().playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.5f + 0.1f * s);
            }
        }
        if (tickCount == APEX) {
            Vec3 c = swordPos(a, dir, 1, tickCount, op);
            Vec3 g = new Vec3(c.x, a.y, c.z);
            for (LivingEntity t : near(g, RoundTripSpell.APEX_RADIUS, owner)) {
                hit(t, owner, RoundTripSpell.APEX_FLAT, RoundTripSpell.APEX_PERCENT);
                Vec3 out = new Vec3(t.getX() - g.x, 0, t.getZ() - g.z);
                out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(0.8);
                t.setDeltaMovement(out.x, 0.8, out.z);
                t.hurtMarked = true;
            }
            level().playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 4.0f, 0.5f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.8f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.7f);
        }
        if (tickCount == HOME) {
            for (LivingEntity t : near(op, RoundTripSpell.FINAL_RADIUS, owner)) {
                hit(t, owner, RoundTripSpell.FINAL_FLAT, RoundTripSpell.FINAL_PERCENT);
                Vec3 out = new Vec3(t.getX() - op.x, 0, t.getZ() - op.z);
                out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(1.1);
                t.setDeltaMovement(out.x, 0.7, out.z);
                t.hurtMarked = true;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, true, true));
            }
            level().playSound(null, op.x, op.y + 1, op.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.7f);
            level().playSound(null, op.x, op.y + 1, op.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.0f);
            level().playSound(null, op.x, op.y + 1, op.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 4.0f, 0.6f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        Entity own = owner();
        Vec3 op = own != null ? own.position() : position();
        if (tickCount >= START && tickCount < HOME) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = swordPos(position(), dir(), s, tickCount, op);
                for (int i = 0; i < 3; i++) {
                    double ang = random.nextDouble() * Math.PI * 2;
                    level().addParticle(ParticleTypes.FLAME, c.x + Math.cos(ang) * 3, c.y + (random.nextDouble() - 0.5) * 2, c.z + Math.sin(ang) * 3, 0, 0.03, 0);
                }
            }
        }
        if (tickCount == APEX) {
            Vec3 c = swordPos(position(), dir(), 1, tickCount, op);
            for (int i = 0; i < 70; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double sp = 0.5 + random.nextDouble() * 1.8;
                level().addParticle(ParticleTypes.FLAME, c.x, position().y + 0.4, c.z, Math.cos(ang) * sp, 0.1 + random.nextDouble() * 0.6, Math.sin(ang) * sp);
            }
        }
        if (tickCount == HOME) {
            for (int i = 0; i < 90; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double sp = 0.5 + random.nextDouble() * 2.0;
                level().addParticle(ParticleTypes.FLAME, op.x, op.y + 0.5, op.z, Math.cos(ang) * sp, 0.1 + random.nextDouble() * 0.6, Math.sin(ang) * sp);
            }
        }
    }
}
