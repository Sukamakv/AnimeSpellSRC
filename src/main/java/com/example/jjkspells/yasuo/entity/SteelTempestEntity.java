package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yasuo.YasuoRegistry;
import com.example.jjkspells.yasuo.spell.SteelTempestSpell;
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
import net.minecraft.util.Mth;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Steel Tempest". The entity sits at the cast point; the facing is synced.
 * <pre>
 *   0 .. T0            Yasuo gathers himself
 *   T0 + 8k            thrust k (k = 0, 1, 2): he lunges forward, everything in the lane in front of him is hit
 *   LAUNCH (= thrust 2) the tornado is born 3 blocks ahead and sweeps forward TORNADO_TICKS, lifting and shredding every enemy it touches
 *   LAUNCH + TORNADO_TICKS   the tornado collapses and drops its victims
 * </pre>
 */
public class SteelTempestEntity extends Entity {
    public static final int T0 = 6;
    public static final int STEP = 8;
    public static final int LAUNCH = T0 + 2 * STEP;
    public static final int TORNADO_TICKS = 36;
    public static final int END = LAUNCH + TORNADO_TICKS + 16;

    public static int thrustTick(int k) {
        return T0 + k * STEP;
    }

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SteelTempestEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(SteelTempestEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> ORIGIN = SynchedEntityData.defineId(SteelTempestEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(SteelTempestEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final CompoundTag log = new CompoundTag();
    private final Set<Integer> caught = new LinkedHashSet<>();
    private final List<int[]> clientHits = new ArrayList<>();
    private int[] clientCaught = new int[0];

    public SteelTempestEntity(EntityType<? extends SteelTempestEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SteelTempestEntity create(Level level, LivingEntity caster) {
        SteelTempestEntity e = new SteelTempestEntity(YasuoRegistry.STEEL, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : dir.normalize();
        e.entityData.set(DIR, new Vector3f((float) dir.x, 0f, (float) dir.z));
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(DIR, new Vector3f(0, 0, 1));
        this.entityData.define(ORIGIN, new Vector3f());
        this.entityData.define(LOG, new CompoundTag());
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

    /** Where the tornado was born, or null before the launch. */
    public Vec3 origin() {
        Vector3f v = this.entityData.get(ORIGIN);
        return (v.x() == 0 && v.y() == 0 && v.z() == 0) ? null : new Vec3(v.x(), v.y(), v.z());
    }

    /** Centre of the tornado's base after tt ticks of travel. */
    public Vec3 tornadoAt(double tt) {
        Vec3 o = origin();
        if (o == null) {
            return null;
        }
        double d = Math.min(tt, TORNADO_TICKS) * SteelTempestSpell.TORNADO_SPEED;
        return o.add(dir().scale(d));
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: enemies hit by thrust k. */
    public List<int[]> thrustHits() {
        return clientHits;
    }

    /** Client: enemies currently caught in the tornado. */
    public int[] caughtIds() {
        return clientCaught;
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

    private void syncCaught() {
        int[] arr = new int[caught.size()];
        int i = 0;
        for (int id : caught) {
            arr[i++] = id;
        }
        log.putIntArray("t", arr);
        this.entityData.set(LOG, log.copy());
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        Vec3 dir = dir();

        if (tickCount == 1) {
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 3.0f, 1.5f);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.8f);
        }
        if (owner != null && tickCount < T0) {
            owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
        }
        for (int k = 0; k < 3; k++) {
            int tk = thrustTick(k);
            if (owner == null) {
                break;
            }
            if (tickCount >= tk && tickCount < tk + 3) {
                owner.setDeltaMovement(dir.x * (k == 2 ? 1.1 : 0.9), Math.min(0.05, owner.getDeltaMovement().y), dir.z * (k == 2 ? 1.1 : 0.9));
                owner.hurtMarked = true;
                owner.fallDistance = 0;
            }
            if (tickCount == tk) {
                doThrust(owner, dir, k);
            }
        }
        if (tickCount == LAUNCH && owner != null) {
            Vec3 o = owner.position().add(dir.scale(3.0));
            this.entityData.set(ORIGIN, new Vector3f((float) o.x, (float) o.y, (float) o.z));
            level().playSound(null, o.x, o.y + 2, o.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.6f);
            level().playSound(null, o.x, o.y + 2, o.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 3.0f, 1.2f);
        }
        int tt = tickCount - LAUNCH;
        Vec3 axis = tt >= 0 ? tornadoAt(tt) : null;
        if (axis != null && tt > 0 && tt <= TORNADO_TICKS) {
            tornadoTick(owner, axis, dir, tt);
        }
        if (tt == TORNADO_TICKS && axis != null) {
            // collapse: drop the victims with a last blow
            for (int id : caught) {
                Entity e = sl.getEntity(id);
                if (e instanceof LivingEntity t && t.isAlive()) {
                    hit(t, owner, SteelTempestSpell.DROP_FLAT, SteelTempestSpell.DROP_PERCENT);
                    Vec3 out = new Vec3(t.getX() - axis.x, 0, t.getZ() - axis.z);
                    out = out.lengthSqr() < 0.01 ? dir.scale(0.6) : out.normalize().scale(0.8);
                    t.setDeltaMovement(out.x, -0.2, out.z);
                    t.hurtMarked = true;
                }
            }
            caught.clear();
            syncCaught();
            level().playSound(null, axis.x, axis.y + 3, axis.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5f, 1.4f);
        }
    }

    private void doThrust(LivingEntity owner, Vec3 dir, int k) {
        boolean last = k == 2;
        double len = last ? SteelTempestSpell.LAST_LENGTH : SteelTempestSpell.THRUST_LENGTH;
        double half = last ? SteelTempestSpell.LAST_HALF_WIDTH : SteelTempestSpell.THRUST_HALF_WIDTH;
        Vec3 o = owner.position();
        Vec3 end = o.add(dir.scale(len));
        AABB box = new AABB(Math.min(o.x, end.x), o.y - 2, Math.min(o.z, end.z), Math.max(o.x, end.x), o.y + 4, Math.max(o.z, end.z)).inflate(half);
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isEnemy(t, owner)) {
                continue;
            }
            Vec3 r = new Vec3(t.getX() - o.x, 0, t.getZ() - o.z);
            double along = r.dot(dir);
            double lat = r.subtract(dir.scale(along)).length();
            if (along < -1.0 || along > len || lat > half + t.getBbWidth() * 0.5) {
                continue;
            }
            ids.add(t.getId());
            if (last) {
                hit(t, owner, SteelTempestSpell.LAST_FLAT, SteelTempestSpell.LAST_PERCENT);
                t.setDeltaMovement(dir.x * 0.5, 0.75, dir.z * 0.5);
            } else {
                hit(t, owner, SteelTempestSpell.THRUST_FLAT, SteelTempestSpell.THRUST_PERCENT);
                t.setDeltaMovement(dir.x * 0.45, 0.2, dir.z * 0.45);
            }
            t.hurtMarked = true;
        }
        int[] arr = new int[ids.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ids.get(i);
        }
        log.putIntArray("c" + k, arr);
        log.putInt("n", k + 1);
        this.entityData.set(LOG, log.copy());
        level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, 1.5f + 0.15f * k);
        level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 1.6f - 0.2f * k);
        if (!ids.isEmpty()) {
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.5f, 1.2f + 0.1f * k);
        }
    }

    private void tornadoTick(LivingEntity owner, Vec3 axis, Vec3 dir, int tt) {
        double r = SteelTempestSpell.TORNADO_RADIUS;
        boolean changed = false;
        AABB box = new AABB(axis.x - r, axis.y - 3, axis.z - r, axis.x + r, axis.y + SteelTempestSpell.TORNADO_HEIGHT, axis.z + r);
        Set<Integer> inside = new LinkedHashSet<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - axis.x;
            double dz = t.getZ() - axis.z;
            if (dx * dx + dz * dz > r * r || !isEnemy(t, owner)) {
                continue;
            }
            inside.add(t.getId());
            if (caught.add(t.getId())) {
                hit(t, owner, SteelTempestSpell.CATCH_FLAT, SteelTempestSpell.CATCH_PERCENT);
                changed = true;
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 2.0f, 1.3f);
            } else if (tt % 6 == 0) {
                hit(t, owner, SteelTempestSpell.TICK_FLAT, SteelTempestSpell.TICK_PERCENT);
            }
            // spiral up and carry along
            double dist = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            double tx = -dz / dist * 0.34;
            double tz = dx / dist * 0.34;
            double inward = dist > 1.8 ? 0.16 : 0.0;
            double want = axis.y + 3.0 + (t.getId() % 7) * 0.9;
            double vy = Mth.clamp((want - t.getY()) * 0.2, -0.3, 0.6);
            t.setDeltaMovement(dir.x * SteelTempestSpell.TORNADO_SPEED * 0.8 + tx - dx / dist * inward, vy, dir.z * SteelTempestSpell.TORNADO_SPEED * 0.8 + tz - dz / dist * inward);
            t.hurtMarked = true;
            t.fallDistance = 0;
        }
        if (caught.removeIf(id -> !inside.contains(id))) {
            changed = true;
        }
        if (changed) {
            syncCaught();
        }
        if (tt % 6 == 0) {
            level().playSound(null, axis.x, axis.y + 3, axis.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.5f, 0.6f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int n = tag.getInt("n");
        while (clientHits.size() < n) {
            clientHits.add(tag.getIntArray("c" + clientHits.size()));
        }
        clientCaught = tag.getIntArray("t");
        Entity own = owner();
        for (int k = 0; k < 3; k++) {
            if (own != null && tickCount == thrustTick(k)) {
                Vec3 d = dir();
                for (int i = 0; i < 16; i++) {
                    double f = random.nextDouble() * (k == 2 ? 12 : 9);
                    level().addParticle(ParticleTypes.CLOUD, own.getX() + d.x * f + (random.nextDouble() - 0.5), own.getY() + 1 + (random.nextDouble() - 0.5), own.getZ() + d.z * f + (random.nextDouble() - 0.5), d.x * 0.3, 0, d.z * 0.3);
                }
            }
        }
        int tt = tickCount - LAUNCH;
        Vec3 axis = tt > 0 && tt <= TORNADO_TICKS ? tornadoAt(tt) : null;
        if (axis != null) {
            for (int i = 0; i < 8; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double h = random.nextDouble() * 12;
                double rr = 1.2 + 3.8 * Math.pow(h / 16.0, 1.2);
                level().addParticle(ParticleTypes.CLOUD, axis.x + Math.cos(ang) * rr, axis.y + h, axis.z + Math.sin(ang) * rr, -Math.sin(ang) * 0.3, 0.05, Math.cos(ang) * 0.3);
            }
        }
    }
}
