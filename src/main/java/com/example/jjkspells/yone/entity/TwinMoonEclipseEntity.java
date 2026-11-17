package com.example.jjkspells.yone.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yone.YoneRegistry;
import com.example.jjkspells.yone.spell.TwinMoonEclipseSpell;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Twin Moon Eclipse". The entity sits on the ground at the centre of the eclipse; the horizontal direction from the caster to it is synced
 * (the giant X stands facing the caster).
 * <pre>
 *   1 .. CHARGE                       the moons slide together; from PULL_START everything near is dragged into the funnel
 *   X_T = CHARGE + X_DELAY            the X falls: everything near the centre is cut and thrown
 *   X_T + 8                           aftershock
 * </pre>
 */
public class TwinMoonEclipseEntity extends Entity {
    public static final int CHARGE = TwinMoonEclipseSpell.CHARGE;
    public static final int X_T = CHARGE + TwinMoonEclipseSpell.X_DELAY;
    public static final int AFTER_T = X_T + 8;
    public static final int END = X_T + 30;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(TwinMoonEclipseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(TwinMoonEclipseEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(TwinMoonEclipseEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final Set<Integer> caught = new LinkedHashSet<>();
    private int[] clientCaught = new int[0];

    public TwinMoonEclipseEntity(EntityType<? extends TwinMoonEclipseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static TwinMoonEclipseEntity create(Level level, LivingEntity caster) {
        TwinMoonEclipseEntity e = new TwinMoonEclipseEntity(YoneRegistry.ECLIPSE, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 far = eye.add(look.scale(TwinMoonEclipseSpell.RANGE));
        HitResult hr = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 c = hr.getType() == HitResult.Type.MISS ? far : hr.getLocation();
        double y = c.y;
        if (level instanceof ServerLevel sl) {
            double ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(c.x), (int) Math.floor(c.z));
            if (hr.getType() == HitResult.Type.MISS || Math.abs(ground - caster.getY()) < 12) {
                y = Mth.clamp(ground, caster.getY() - 12, caster.getY() + 12);
            }
        }
        e.setPos(c.x, y, c.z);
        Vec3 to = new Vec3(c.x - caster.getX(), 0, c.z - caster.getZ());
        to = to.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : to.normalize();
        e.entityData.set(DIR, new Vector3f((float) to.x, 0f, (float) to.z));
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(DIR, new Vector3f(0, 0, 1));
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

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: enemies caught in the funnel. */
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
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        float flatDmg = (float) (flat);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, dsrc);
        }
    }

    private void syncCaught() {
        int[] arr = new int[caught.size()];
        int i = 0;
        for (int id : caught) {
            arr[i++] = id;
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("t", arr);
        this.entityData.set(LOG, tag);
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        Vec3 c = position();
        if (tickCount == 1) {
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 0.6f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 4.0f, 0.7f);
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2.5f, CHARGE);
        }
        if (tickCount >= TwinMoonEclipseSpell.PULL_START && tickCount < X_T) {
            pull(owner, c);
        }
        if (tickCount == CHARGE) {
            level().playSound(null, c.x, c.y + 6, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.5f, 0.7f);
            level().playSound(null, c.x, c.y + 6, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 0.8f);
        }
        if (tickCount == X_T) {
            double r = TwinMoonEclipseSpell.X_RADIUS;
            AABB box = new AABB(c.x - r, c.y - 4, c.z - r, c.x + r, c.y + 18, c.z + r);
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r || !isEnemy(t, owner)) {
                    continue;
                }
                hit(t, owner, TwinMoonEclipseSpell.X_FLAT, TwinMoonEclipseSpell.X_PERCENT);
                double d = Math.max(dist, 0.01);
                t.setDeltaMovement(dx / d * 0.9, 0.9, dz / d * 0.9);
                t.hurtMarked = true;
                t.fallDistance = 0;
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.5f, 0.9f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2.0f);
            }
            caught.clear();
            syncCaught();
            level().playSound(null, c.x, c.y + 3, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.9f);
            level().playSound(null, c.x, c.y + 3, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.5f);
            level().playSound(null, c.x, c.y + 3, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 4.0f, 0.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 2.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 3.3f);
        }
        if (tickCount == AFTER_T) {
            double r = TwinMoonEclipseSpell.AFTER_RADIUS;
            AABB box = new AABB(c.x - r, c.y - 4, c.z - r, c.x + r, c.y + 18, c.z + r);
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r || !isEnemy(t, owner)) {
                    continue;
                }
                hit(t, owner, TwinMoonEclipseSpell.AFTER_FLAT, TwinMoonEclipseSpell.AFTER_PERCENT);
                double d = Math.max(dist, 0.01);
                t.setDeltaMovement(dx / d * 0.5, 0.35, dz / d * 0.5);
                t.hurtMarked = true;
            }
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.4f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 4.3f);
        }
    }

    private void pull(LivingEntity owner, Vec3 c) {
        double R = TwinMoonEclipseSpell.PULL_RADIUS;
        AABB box = new AABB(c.x - R, c.y - 3, c.z - R, c.x + R, c.y + 14, c.z + R);
        Set<Integer> inside = new LinkedHashSet<>();
        boolean freeze = tickCount >= CHARGE;
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - c.x;
            double dz = t.getZ() - c.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R || !isEnemy(t, owner)) {
                continue;
            }
            inside.add(t.getId());
            double d = Math.max(dist, 0.5);
            double inward = dist > 1.5 ? Math.min(0.5, dist * 0.1 + 0.12) : 0.0;
            double sw = freeze ? 0.0 : 0.16;
            double want = c.y + 2.5 + (t.getId() % 4) * 0.7;
            double vy = Mth.clamp((want - t.getY()) * 0.2, -0.2, 0.5);
            t.setDeltaMovement(-dx / d * inward - dz / d * sw, vy, -dz / d * inward + dx / d * sw);
            t.hurtMarked = true;
            t.fallDistance = 0;
        }
        if (!inside.equals(caught)) {
            caught.clear();
            caught.addAll(inside);
            syncCaught();
        }
        if (tickCount % 8 == 0) {
            level().playSound(null, c.x, c.y + 4, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        clientCaught = this.entityData.get(LOG).getIntArray("t");
        if (tickCount < X_T && tickCount > 2) {
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 2 + random.nextDouble() * (TwinMoonEclipseSpell.PULL_RADIUS - 2);
                level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, -Math.cos(a) * 0.3, 0.05, -Math.sin(a) * 0.3);
            }
        }
    }
}
