package com.example.jjkspells.dante.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.dante.DanteRegistry;
import com.example.jjkspells.dante.spell.CavaliereSpell;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Cavaliere Angelo: Lightning Ride".
 * <pre>
 *   0 .. RIDE0           the bike is summoned
 *   RIDE0 .. RIDE_END    the caster rides (heading follows his look, limited turn rate); enemies touched are rammed, enemies near are shocked
 *   BOLT0 + 3k           small bolt k (k = 0..BOLTS-1) around the end point
 *   BIG                  giant bolt on the end point
 *   .. END               fade
 * </pre>
 */
public class CavaliereEntity extends Entity {
    public static final int RIDE0 = 12;
    public static final int RIDE_TICKS = 46;
    public static final int RIDE_END = RIDE0 + RIDE_TICKS;
    public static final int BOLT0 = RIDE_END + 6;
    public static final int BOLTS = 7;
    public static final int BIG = BOLT0 + BOLTS * 3 + 4;
    public static final int END = BIG + 36;
    public static final double SCATTER = 15.0;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(CavaliereEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEADING = SynchedEntityData.defineId(CavaliereEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> END_POS = SynchedEntityData.defineId(CavaliereEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(CavaliereEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final Set<Integer> rammed = new HashSet<>();
    private final List<Integer> rammedLog = new ArrayList<>();
    private final List<Vec3> trail = new ArrayList<>();
    private List<int[]> clientRams = new ArrayList<>();
    private int clientRamSize = 0;

    public CavaliereEntity(EntityType<? extends CavaliereEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static CavaliereEntity create(Level level, LivingEntity caster) {
        CavaliereEntity e = new CavaliereEntity(DanteRegistry.CAVALIERE, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.entityData.set(HEADING, caster.getYRot());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    /** Position of small bolt k (k = BOLTS means the giant bolt) around the end point; identical on server and client. */
    public Vec3 boltPos(int k) {
        Vec3 b = endPos();
        if (b == null) {
            return null;
        }
        if (k >= BOLTS) {
            return b;
        }
        Random r = new Random(getId() * 31L + k * 977L);
        double ang = (k + r.nextDouble() * 0.6) * Math.PI * 2 / BOLTS;
        double rad = 6 + r.nextDouble() * (SCATTER - 6);
        return b.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
    }

    public static int boltTick(int k) {
        return k >= BOLTS ? BIG : BOLT0 + k * 3;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(HEADING, 0f);
        this.entityData.define(END_POS, new Vector3f());
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

    public float heading() {
        return this.entityData.get(HEADING);
    }

    public Vec3 endPos() {
        Vector3f v = this.entityData.get(END_POS);
        return (v.x() == 0 && v.y() == 0 && v.z() == 0) ? null : new Vec3(v.x(), v.y(), v.z());
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: positions of the rider over the last ticks, newest last. */
    public List<Vec3> trail() {
        return trail;
    }

    /** Client: rammed enemies as {entity id, tick}. */
    public List<int[]> rams() {
        return clientRams;
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
        AABB box = new AABB(c.x - radius, c.y - 6, c.z - radius, c.x + radius, c.y + 14, c.z + radius);
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
        if (owner != null) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        Vec3 a = position();

        if (tickCount == 1) {
            if (owner != null) {
                owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, END, 1, false, false, true));
            }
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 1.6f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 3.0f, 1.4f);
        }
        if (tickCount == RIDE0) {
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.1f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.5f);
        }

        boolean riding = tickCount >= RIDE0 && tickCount < RIDE_END;
        if (owner != null && tickCount < RIDE0) {
            Vec3 dm = owner.getDeltaMovement();
            owner.setDeltaMovement(0, Math.min(0, dm.y), 0);
            owner.hurtMarked = true;
        }
        if (riding && owner != null) {
            float want = owner.getYRot();
            float h = heading();
            float diff = Mth.wrapDegrees(want - h);
            h += (float) Mth.clamp(diff, -CavaliereSpell.TURN_DEG_PER_TICK, CavaliereSpell.TURN_DEG_PER_TICK);
            this.entityData.set(HEADING, h);
            double rad = Math.toRadians(h);
            Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            double vy = owner.getDeltaMovement().y;
            if (owner.horizontalCollision && owner.onGround()) {
                vy = 0.5;
            }
            owner.setDeltaMovement(fwd.x * CavaliereSpell.SPEED, vy, fwd.z * CavaliereSpell.SPEED);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
            boolean any = false;
            for (LivingEntity t : near(owner.position(), CavaliereSpell.RAM_RADIUS, owner)) {
                if (rammed.add(t.getId())) {
                    rammedLog.add(t.getId());
                    rammedLog.add(tickCount);
                    hit(t, owner, CavaliereSpell.RAM_FLAT, CavaliereSpell.RAM_PERCENT);
                    Vec3 side = new Vec3(t.getX() - owner.getX(), 0, t.getZ() - owner.getZ());
                    side = side.lengthSqr() < 0.01 ? Vec3.ZERO : side.normalize();
                    t.setDeltaMovement(fwd.x * 1.1 + side.x * 0.9, 0.85, fwd.z * 1.1 + side.z * 0.9);
                    t.hurtMarked = true;
                    any = true;
                }
            }
            if (any) {
                int[] arr = new int[rammedLog.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = rammedLog.get(i);
                }
                CompoundTag log = this.entityData.get(LOG).copy();
                log.putIntArray("r", arr);
                this.entityData.set(LOG, log);
                level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 3.0f, 1.3f);
                level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.5f, 0.7f);
            }
            if (tickCount % 5 == 0) {
                for (LivingEntity t : near(owner.position(), CavaliereSpell.SHOCK_RADIUS, owner)) {
                    hit(t, owner, CavaliereSpell.SHOCK_FLAT, CavaliereSpell.SHOCK_PERCENT);
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, true, true));
                }
            }
            if (tickCount % 4 == 0) {
                level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.8f);
            }
        }
        if (tickCount == RIDE_END) {
            Vec3 b = owner != null ? owner.position() : a;
            this.entityData.set(END_POS, new Vector3f((float) b.x, (float) b.y, (float) b.z));
            if (owner != null) {
                owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
                owner.hurtMarked = true;
            }
        }
        Vec3 b = endPos();
        if (b == null) {
            return;
        }
        for (int k = 0; k <= BOLTS; k++) {
            if (tickCount != boltTick(k)) {
                continue;
            }
            boolean big = k == BOLTS;
            Vec3 c = boltPos(k);
            double r = big ? CavaliereSpell.BIG_RADIUS : CavaliereSpell.BOLT_RADIUS;
            for (LivingEntity t : near(c, r, owner)) {
                if (big) {
                    hit(t, owner, CavaliereSpell.BIG_FLAT, CavaliereSpell.BIG_PERCENT);
                    Vec3 out = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z);
                    out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(0.9);
                    t.setDeltaMovement(out.x, 0.9, out.z);
                    t.hurtMarked = true;
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, true, true));
                } else {
                    hit(t, owner, CavaliereSpell.BOLT_FLAT, CavaliereSpell.BOLT_PERCENT);
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 0.5, 0));
                    t.hurtMarked = true;
                }
            }
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, big ? 8.0f : 5.0f, big ? 0.7f : 1.0f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, big ? 6.0f : 3.0f, big ? 0.7f : 1.2f);
            if (big) {
                level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.7f);
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        Entity own = owner();
        if (own == null) {
            return;
        }
        if (tickCount >= RIDE0 && tickCount < RIDE_END + 8) {
            trail.add(own.position());
            while (trail.size() > 60) {
                trail.remove(0);
            }
        }
        CompoundTag tag = this.entityData.get(LOG);
        int[] r = tag.getIntArray("r");
        if (r.length / 2 != clientRamSize) {
            clientRams = new ArrayList<>();
            for (int i = 0; i + 1 < r.length; i += 2) {
                clientRams.add(new int[]{r[i], r[i + 1]});
            }
            clientRamSize = r.length / 2;
        }
        if (tickCount >= RIDE0 && tickCount < RIDE_END) {
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, own.getX() + (random.nextDouble() - 0.5) * 3, own.getY() + random.nextDouble() * 2, own.getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.05, 0);
            }
        }
        for (int k = 0; k <= BOLTS; k++) {
            if (tickCount == boltTick(k)) {
                Vec3 c = boltPos(k);
                if (c != null) {
                    int n = k == BOLTS ? 90 : 25;
                    for (int i = 0; i < n; i++) {
                        double ang = random.nextDouble() * Math.PI * 2;
                        double s = 0.4 + random.nextDouble() * (k == BOLTS ? 2.2 : 1.2);
                        level().addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.3, c.z, Math.cos(ang) * s, 0.1 + random.nextDouble() * 0.6, Math.sin(ang) * s);
                    }
                }
            }
        }
    }
}
