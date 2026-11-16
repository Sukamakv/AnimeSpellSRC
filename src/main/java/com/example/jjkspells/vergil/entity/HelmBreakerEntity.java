package com.example.jjkspells.vergil.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.vergil.VergilRegistry;
import com.example.jjkspells.vergil.spell.HelmBreakerSpell;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller of "Helm Breaker". The entity sits on the impact point P. Timeline (ticks since cast):
 * <pre>
 *   LAUNCH                 the caster is thrown skyward
 *   APEX                   the caster's position is recorded and the caster is moved onto P
 *   IMPACT (event 0)       slam: heavy hit in the whole radius, ground splits open
 *   IMPACT + 10 k          fissure pulse k (k = 1..5): small hit + slow
 *   FINAL (event 6)        fissures slam shut: final hit
 *   .. END                 fading glass
 * </pre>
 */
public class HelmBreakerEntity extends Entity {
    public static final int LAUNCH = 1;
    public static final int APEX = 16;
    public static final int IMPACT = 20;
    public static final int STEP = 10;
    public static final int EVENTS = 7;
    public static final int FINAL = IMPACT + STEP * (EVENTS - 1);
    public static final int END = FINAL + 46;
    public static final double R = HelmBreakerSpell.RADIUS;
    public static final double BELOW = 6.0;
    public static final double ABOVE = 30.0;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HelmBreakerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> APEX_POS = SynchedEntityData.defineId(HelmBreakerEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(HelmBreakerEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final CompoundTag log = new CompoundTag();
    private final List<int[]> clientEvents = new ArrayList<>();

    public HelmBreakerEntity(EntityType<? extends HelmBreakerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HelmBreakerEntity create(Level level, LivingEntity caster) {
        HelmBreakerEntity e = new HelmBreakerEntity(VergilRegistry.HELM, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().normalize().scale(HelmBreakerSpell.RANGE));
        BlockHitResult bhit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(level, caster, eye, stop, new AABB(eye, stop).inflate(1.5),
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en != caster);
        Vec3 at = stop;
        if (ehit != null) {
            at = ehit.getEntity().position();
        } else if (bhit.getType() == HitResult.Type.MISS) {
            BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -56, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (down.getType() != HitResult.Type.MISS) {
                at = down.getLocation();
            }
        }
        e.setPos(at.x, at.y, at.z);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(APEX_POS, new Vector3f());
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

    /** Client: where the caster was when the dive started (zero vector until then). */
    public Vec3 apexPos() {
        Vector3f v = this.entityData.get(APEX_POS);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: ids of the enemies each event hit (index = event: 0 slam, 1..5 pulses, 6 final). */
    public List<int[]> events() {
        return clientEvents;
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

    private List<LivingEntity> inZone(LivingEntity owner) {
        double x = getX();
        double y = getY();
        double z = getZ();
        AABB box = new AABB(x - R, y - BELOW, z - R, x + R, y + ABOVE, z + R);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - x;
            double dz = t.getZ() - z;
            if (dx * dx + dz * dz <= R * R && isEnemy(t, owner)) {
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
        double x = getX();
        double y = getY();
        double z = getZ();

        if (tickCount == LAUNCH) {
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 7.0), IMPACT - 1);
        }
        if (tickCount == APEX) {
            com.example.jjkspells.vfx.sprite.SpriteFx.beam(sl, new Vec3(x, y + 20, z), new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f, IMPACT - APEX);
        }
        if (tickCount == LAUNCH && owner != null) {
            owner.setDeltaMovement(owner.getDeltaMovement().add(0, 1.9, 0));
            owner.hurtMarked = true;
            owner.fallDistance = 0;
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.5f, 0.6f);
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.0f, 1.6f);
            level().playSound(null, x, y + 1, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 1.8f);
        }
        if (tickCount == APEX && owner != null) {
            this.entityData.set(APEX_POS, new Vector3f((float) owner.getX(), (float) owner.getY(), (float) owner.getZ()));
            if (owner instanceof ServerPlayer sp) {
                sp.connection.teleport(x, y, z, sp.getYRot(), sp.getXRot());
            } else {
                owner.moveTo(x, y, z, owner.getYRot(), owner.getXRot());
            }
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 1, false, false, true));
            level().playSound(null, x, y + 2, z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 4.0f, 0.6f);
        }
        if (tickCount == APEX && owner == null) {
            this.entityData.set(APEX_POS, new Vector3f((float) x, (float) (y + 20), (float) z));
        }
        int k = (tickCount - IMPACT) / STEP;
        if (tickCount >= IMPACT && (tickCount - IMPACT) % STEP == 0 && k < EVENTS) {
            List<LivingEntity> in = inZone(owner);
            int[] ids = new int[in.size()];
            for (int i = 0; i < ids.length; i++) {
                LivingEntity t = in.get(i);
                ids[i] = t.getId();
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, k == 0 || k == EVENTS - 1 ? 3f : 1.5f);
                if (k == 0) {
                    hit(t, owner, HelmBreakerSpell.IMPACT_FLAT, HelmBreakerSpell.IMPACT_PERCENT);
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 0.8, 0));
                    t.hurtMarked = true;
                } else if (k < EVENTS - 1) {
                    hit(t, owner, HelmBreakerSpell.PULSE_FLAT, HelmBreakerSpell.PULSE_PERCENT);
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 4, false, false, false));
                } else {
                    hit(t, owner, HelmBreakerSpell.FINAL_FLAT, HelmBreakerSpell.FINAL_PERCENT);
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, true, true));
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 0.5, 0));
                    t.hurtMarked = true;
                }
            }
            log.putIntArray("c" + k, ids);
            log.putInt("n", k + 1);
            this.entityData.set(LOG, log.copy());
            if (k == 0) {
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, new Vec3(x, y + 1, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 8f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 6.0));
                level().playSound(null, x, y + 1, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.6f);
                level().playSound(null, x, y + 1, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 1.0f);
                level().playSound(null, x, y + 1, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.5f);
                level().playSound(null, x, y + 1, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 1.2f);
            } else if (k < EVENTS - 1) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, (float) (R * 2.0 / 6.0) * (0.4f + 0.12f * k));
                level().playSound(null, x, y + 2, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 1.0f + 0.08f * k);
                level().playSound(null, x, y + 2, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 1.3f);
            } else {
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, new Vec3(x, y + 1, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 6f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) (R * 2.0 / 6.0));
                level().playSound(null, x, y + 2, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.6f);
                level().playSound(null, x, y + 2, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 1.1f);
                level().playSound(null, x, y + 2, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.2f);
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int n = tag.getInt("n");
        while (clientEvents.size() < n) {
            clientEvents.add(tag.getIntArray("c" + clientEvents.size()));
        }
        double x = getX();
        double y = getY();
        double z = getZ();
        if (tickCount >= IMPACT && tickCount < FINAL && tickCount % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * R;
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r, 0, 0.2 + random.nextDouble() * 0.3, 0);
            }
        }
        if (tickCount == IMPACT || tickCount == FINAL) {
            for (int i = 0; i < 70; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.5 + random.nextDouble() * 1.6;
                level().addParticle(ParticleTypes.END_ROD, x, y + 0.5, z, Math.cos(a) * s, 0.1 + random.nextDouble() * 0.7, Math.sin(a) * s);
            }
        }
    }
}
