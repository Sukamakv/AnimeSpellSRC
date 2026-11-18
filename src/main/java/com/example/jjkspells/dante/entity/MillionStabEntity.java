package com.example.jjkspells.dante.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.dante.DanteRegistry;
import com.example.jjkspells.dante.spell.MillionStabSpell;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Million Stab". The entity sits at the start A of the Stinger; the dash direction is synced and the end point B is synced
 * once the dash is over. Timeline (ticks since cast):
 * <pre>
 *   0 .. DASH0              charge
 *   DASH0 .. DASH0 + 8      Stinger: the caster is driven forward, enemies close to him are skewered and carried along
 *   B_TICK                  end of dash: B is recorded, carried enemies are pinned
 *   STAB0 + k               stab k (k = 0..13): every enemy within STAB_RADIUS of B is hit
 *   FINAL                   a giant Rebellion lands on B: heavy hit in FINAL_RADIUS
 *   .. END                  the blade shatters
 * </pre>
 */
public class MillionStabEntity extends Entity {
    public static final int DASH0 = 6;
    public static final int DASH_TICKS = 8;
    public static final int B_TICK = DASH0 + DASH_TICKS;
    public static final int STAB0 = B_TICK + 2;
    public static final int STABS = 14;
    public static final int FINAL = STAB0 + STABS + 4;
    public static final int END = FINAL + 40;

    /** Tick at which event k happens: 0..13 the stabs, 14 the final landing. */
    public static int eventTick(int k) {
        return k < STABS ? STAB0 + k : FINAL;
    }

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(MillionStabEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(MillionStabEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> END_POS = SynchedEntityData.defineId(MillionStabEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(MillionStabEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final CompoundTag log = new CompoundTag();
    private final Set<Integer> carried = new LinkedHashSet<>();
    private final List<Integer> pierced = new ArrayList<>();
    private final List<int[]> clientEvents = new ArrayList<>();
    private List<int[]> clientPierced = new ArrayList<>();
    private int clientPiercedSize = 0;

    public MillionStabEntity(EntityType<? extends MillionStabEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static MillionStabEntity create(Level level, LivingEntity caster) {
        MillionStabEntity e = new MillionStabEntity(DanteRegistry.STAB, level);
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

    public Vec3 dir() {
        Vector3f v = this.entityData.get(DIR);
        return new Vec3(v.x(), 0, v.z());
    }

    /** End point of the dash, or null while the dash is still going. */
    public Vec3 endPos() {
        Vector3f v = this.entityData.get(END_POS);
        return (v.x() == 0 && v.y() == 0 && v.z() == 0) ? null : new Vec3(v.x(), v.y(), v.z());
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: ids of the enemies hit by each event (see eventTick). */
    public List<int[]> events() {
        return clientEvents;
    }

    /** Client: enemies skewered by the Stinger, as {entity id, tick}. */
    public List<int[]> pierced() {
        return clientPierced;
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
        AABB box = new AABB(c.x - radius, c.y - 6, c.z - radius, c.x + radius, c.y + 12, c.z + radius);
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

    private void syncPierced() {
        int[] arr = new int[pierced.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = pierced.get(i);
        }
        log.putIntArray("p", arr);
        this.entityData.set(LOG, log.copy());
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = owner(sl);
        Vec3 a = position();
        Vec3 dir = dir();

        if (tickCount == 1) {
            if (owner != null) {
                owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, END, 1, false, false, true));
            }
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.6f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.5f, 1.6f);
        }
        if (tickCount == DASH0) {
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.2f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3.0f, 0.6f);
        }
        boolean dashing = tickCount >= DASH0 && tickCount < B_TICK;
        if (dashing && owner != null) {
            owner.setDeltaMovement(dir.x * MillionStabSpell.DASH_SPEED, 0.04, dir.z * MillionStabSpell.DASH_SPEED);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
            boolean any = false;
            for (LivingEntity t : near(owner.position(), MillionStabSpell.DASH_RADIUS, owner)) {
                if (carried.add(t.getId())) {
                    pierced.add(t.getId());
                    pierced.add(tickCount);
                    hit(t, owner, MillionStabSpell.DASH_FLAT, MillionStabSpell.DASH_PERCENT);
                    any = true;
                }
            }
            if (any) {
                syncPierced();
                level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 3.0f, 0.9f);
                level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 1.8f);
            }
        }
        if (tickCount >= DASH0 && tickCount <= B_TICK) {
            for (int id : carried) {
                Entity e = sl.getEntity(id);
                if (e instanceof LivingEntity t && t.isAlive()) {
                    boolean stop = tickCount == B_TICK;
                    t.setDeltaMovement(stop ? Vec3.ZERO : new Vec3(dir.x * MillionStabSpell.DASH_SPEED * 0.9, 0.05, dir.z * MillionStabSpell.DASH_SPEED * 0.9));
                    t.hurtMarked = true;
                }
            }
        }
        if (tickCount == B_TICK) {
            Vec3 b = owner != null ? owner.position() : a.add(dir.scale(20));
            this.entityData.set(END_POS, new Vector3f((float) b.x, (float) b.y, (float) b.z));
            if (owner != null) {
                owner.setDeltaMovement(Vec3.ZERO);
                owner.hurtMarked = true;
            }
            level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0f, 0.6f);
        }
        Vec3 b = endPos();
        if (b == null) {
            return;
        }
        if (tickCount > B_TICK && tickCount < FINAL && tickCount % 3 == 0) {
            for (LivingEntity t : near(b, MillionStabSpell.FINAL_RADIUS, owner)) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 9, false, false, false));
            }
        }
        int k = -1;
        for (int i = 0; i <= STABS; i++) {
            if (tickCount == eventTick(i)) {
                k = i;
            }
        }
        if (k >= 0) {
            boolean last = k == STABS;
            List<LivingEntity> in = near(b, last ? MillionStabSpell.FINAL_RADIUS : MillionStabSpell.STAB_RADIUS, owner);
            int[] ids = new int[in.size()];
            for (int i = 0; i < ids.length; i++) {
                LivingEntity t = in.get(i);
                ids[i] = t.getId();
                if (!last) {
                    hit(t, owner, MillionStabSpell.STAB_FLAT, MillionStabSpell.STAB_PERCENT);
                } else {
                    hit(t, owner, MillionStabSpell.FINAL_FLAT, MillionStabSpell.FINAL_PERCENT);
                    Vec3 out = new Vec3(t.getX() - b.x, 0, t.getZ() - b.z);
                    out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(0.7);
                    t.setDeltaMovement(t.getDeltaMovement().add(out.x, 0.6, out.z));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, true, true));
                    t.hurtMarked = true;
                }
            }
            log.putIntArray("c" + k, ids);
            log.putInt("n", k + 1);
            this.entityData.set(LOG, log.copy());
            if (!last) {
                level().playSound(null, b.x, b.y + 1.5, b.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 1.2f + 0.04f * k);
                level().playSound(null, b.x, b.y + 1.5, b.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8f, 1.8f + 0.03f * k);
            } else {
                level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.6f);
                level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 0.9f);
                level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3.0f, 0.5f);
                level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.6f);
            }
        }
        if (tickCount == FINAL - 6) {
            level().playSound(null, b.x, b.y + 12, b.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 4.0f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int n = tag.getInt("n");
        while (clientEvents.size() < n) {
            clientEvents.add(tag.getIntArray("c" + clientEvents.size()));
        }
        int[] pr = tag.getIntArray("p");
        if (pr.length / 2 != clientPiercedSize) {
            clientPierced = new ArrayList<>();
            for (int i = 0; i + 1 < pr.length; i += 2) {
                clientPierced.add(new int[]{pr[i], pr[i + 1]});
            }
            clientPiercedSize = pr.length / 2;
        }
        Entity own = owner();
        if (own != null && tickCount >= DASH0 && tickCount < B_TICK + 2) {
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.FLAME, own.getX() + (random.nextDouble() - 0.5), own.getY() + 0.5 + random.nextDouble(), own.getZ() + (random.nextDouble() - 0.5), 0, 0.03, 0);
            }
        }
        Vec3 b = endPos();
        if (b != null && tickCount == FINAL) {
            for (int i = 0; i < 80; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double s = 0.5 + random.nextDouble() * 1.8;
                level().addParticle(ParticleTypes.FLAME, b.x, b.y + 0.5, b.z, Math.cos(ang) * s, 0.1 + random.nextDouble() * 0.7, Math.sin(ang) * s);
            }
        }
    }
}
