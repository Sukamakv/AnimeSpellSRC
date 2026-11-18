package com.example.jjkspells.vergil.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.vergil.VergilRegistry;
import com.example.jjkspells.vergil.spell.DimensionRushSpell;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller of "Dimension Rush". The entity sits at the start of the dash (A); the end (B) is synced. Timeline:
 * <pre>
 *   BLINK                  caster is moved from A to B
 *   CUT0 + k * STEP        rapid cut k: damage + slice lines through every enemy in the corridor
 *   SHEATH                 sheathing click at B
 *   FINAL                  corridor shatters: heavy hit, knock away from the path
 *   .. END                 fading glass
 * </pre>
 */
public class DimensionRushEntity extends Entity {
    public static final int BLINK = 3;
    public static final int CUT0 = 9;
    public static final int STEP = 2;
    public static final int CUTS = 6;
    public static final int SHEATH = 42;
    public static final int FINAL = 48;
    public static final int END = 100;
    public static final double W = DimensionRushSpell.WIDTH;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(DimensionRushEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> END_POS = SynchedEntityData.defineId(DimensionRushEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(DimensionRushEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final CompoundTag log = new CompoundTag();
    private final List<int[]> clientCuts = new ArrayList<>();

    public DimensionRushEntity(EntityType<? extends DimensionRushEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static DimensionRushEntity create(Level level, LivingEntity caster) {
        DimensionRushEntity e = new DimensionRushEntity(VergilRegistry.RUSH, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        Vec3 far = eye.add(look.scale(DimensionRushSpell.RANGE));
        BlockHitResult bhit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = far;
        if (bhit.getType() != HitResult.Type.MISS) {
            stop = bhit.getLocation().subtract(look.scale(1.4));
        }
        Vec3 a = caster.position();
        Vec3 b = stop.subtract(0, caster.getEyeHeight(), 0);
        if (b.subtract(a).length() < 4.0) {
            b = a.add(look.scale(4.0));
        }
        e.setPos(a.x, a.y, a.z);
        e.entityData.set(END_POS, new Vector3f((float) b.x, (float) b.y, (float) b.z));
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
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

    public Vec3 endPos() {
        Vector3f v = this.entityData.get(END_POS);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: ids of the enemies each rapid cut hit (index = cut). */
    public List<int[]> cuts() {
        return clientCuts;
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

    private List<LivingEntity> inCorridor(LivingEntity owner) {
        Vec3 a = position();
        Vec3 b = endPos();
        AABB box = new AABB(Math.min(a.x, b.x) - W, Math.min(a.y, b.y) - 6, Math.min(a.z, b.z) - W,
                Math.max(a.x, b.x) + W, Math.max(a.y, b.y) + 20, Math.max(a.z, b.z) + W);
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len2 = Math.max(1.0E-6, dx * dx + dz * dz);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double f = Math.max(0.0, Math.min(1.0, ((t.getX() - a.x) * dx + (t.getZ() - a.z) * dz) / len2));
            double px = a.x + dx * f - t.getX();
            double pz = a.z + dz * f - t.getZ();
            double py = a.y + (b.y - a.y) * f;
            if (px * px + pz * pz <= W * W && t.getY() > py - 6 && t.getY() < py + 20 && isEnemy(t, owner)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Flat part through Iron's damage, percent part through SpellDamage.percent (respects Max Health Damage / Resistance). */
    private void hit(LivingEntity t, LivingEntity owner, double flat, double percent) {
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource src = level().damageSources().indirectMagic(this, owner);
        if (flat > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, (float) flat, src);
        }
        if (t.isAlive() && percent > 0) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, src);
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
        LivingEntity owner = owner(sl);
        Vec3 a = position();
        Vec3 b = endPos();
        Vec3 mid = a.add(b).scale(0.5);

        if (tickCount == 1) {
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.5f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.0f, 1.8f);
        }
        if (tickCount == BLINK && owner != null) {
            if (owner instanceof ServerPlayer sp) {
                sp.connection.teleport(b.x, b.y, b.z, sp.getYRot(), sp.getXRot());
            } else {
                owner.moveTo(b.x, b.y, b.z, owner.getYRot(), owner.getXRot());
            }
            owner.fallDistance = 0;
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, false, true));
            level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.0f, 1.4f);
            level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 2.0f);
        }
        if (tickCount >= BLINK && tickCount < FINAL && tickCount % 4 == 0) {
            for (LivingEntity t : inCorridor(owner)) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 9, false, false, false));
            }
        }
        int k = (tickCount - CUT0) / STEP;
        if (tickCount >= CUT0 && (tickCount - CUT0) % STEP == 0 && k < CUTS) {
            List<LivingEntity> in = inCorridor(owner);
            int[] ids = new int[in.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = in.get(i).getId();
                hit(in.get(i), owner, DimensionRushSpell.CUT_FLAT, DimensionRushSpell.CUT_PERCENT);
            }
            log.putIntArray("c" + k, ids);
            log.putInt("n", k + 1);
            this.entityData.set(LOG, log.copy());
            level().playSound(null, mid.x, mid.y + 3, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 3.0f, 1.4f + 0.1f * k);
            level().playSound(null, b.x, b.y + 3, b.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.5f, 1.4f + 0.1f * k);
        }
        if (tickCount == SHEATH) {
            level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.5f, 2.0f);
            level().playSound(null, b.x, b.y + 1, b.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 2.0f);
        }
        if (tickCount == FINAL) {
            level().playSound(null, mid.x, mid.y + 4, mid.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.6f);
            level().playSound(null, mid.x, mid.y + 4, mid.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 1.1f);
            level().playSound(null, b.x, b.y + 4, b.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.8f);
            level().playSound(null, mid.x, mid.y + 4, mid.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.6f);
            double dx = b.x - a.x;
            double dz = b.z - a.z;
            double len2 = Math.max(1.0E-6, dx * dx + dz * dz);
            for (LivingEntity t : inCorridor(owner)) {
                hit(t, owner, DimensionRushSpell.FINAL_FLAT, DimensionRushSpell.FINAL_PERCENT);
                double f = Math.max(0.0, Math.min(1.0, ((t.getX() - a.x) * dx + (t.getZ() - a.z) * dz) / len2));
                Vec3 out = new Vec3(t.getX() - (a.x + dx * f), 0, t.getZ() - (a.z + dz * f));
                out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(0.6);
                t.setDeltaMovement(t.getDeltaMovement().add(out.x, 0.45, out.z));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, true, true));
                t.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int n = tag.getInt("n");
        while (clientCuts.size() < n) {
            clientCuts.add(tag.getIntArray("c" + clientCuts.size()));
        }
        Vec3 a = position();
        Vec3 b = endPos();
        if (tickCount >= 2 && tickCount < 40 && tickCount % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double f = random.nextDouble();
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, a.x + (b.x - a.x) * f + (random.nextDouble() - 0.5) * W,
                        a.y + (b.y - a.y) * f + 0.3, a.z + (b.z - a.z) * f + (random.nextDouble() - 0.5) * W, 0, 0.1, 0);
            }
        }
        if (tickCount == FINAL) {
            for (int i = 0; i < 70; i++) {
                double f = random.nextDouble();
                level().addParticle(ParticleTypes.END_ROD, a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f + 4 + random.nextDouble() * 6, a.z + (b.z - a.z) * f,
                        (random.nextDouble() - 0.5) * 0.8, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.8);
            }
        }
    }
}
