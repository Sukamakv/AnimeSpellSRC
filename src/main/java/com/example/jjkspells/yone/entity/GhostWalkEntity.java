package com.example.jjkspells.yone.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yone.YoneRegistry;
import com.example.jjkspells.yone.spell.GhostWalkSpell;
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
import net.minecraft.world.level.levelgen.Heightmap;
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
 * Controller of "Ghost Walk". This entity IS the spirit: its position is the spirit's position. The body stays where it was cast (ORIGIN).
 * <pre>
 *   1 .. GO                the spirit rushes ahead, steered by the caster's aim; every CUT_INTERVAL ticks it cuts and marks what is near it
 *   GO .. GO + RETURN      it flies back to the body (still cutting)
 *   SNAP = GO + RETURN     marked enemies are torn to the body and ripped; the shockwave hits everything around the body
 * </pre>
 */
public class GhostWalkEntity extends Entity {
    public static final int GO = GhostWalkSpell.GO_TICKS;
    public static final int RET = GhostWalkSpell.RETURN_TICKS;
    public static final int SNAP = GO + RET;
    public static final int END = SNAP + 24;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(GhostWalkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> ORIGIN = SynchedEntityData.defineId(GhostWalkEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(GhostWalkEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private Vec3 heading;
    private Vec3 returnFrom;
    private final Set<Integer> marked = new LinkedHashSet<>();
    private int[] clientMarked = new int[0];
    private final List<Vec3> trail = new ArrayList<>();

    public GhostWalkEntity(EntityType<? extends GhostWalkEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static GhostWalkEntity create(Level level, LivingEntity caster) {
        GhostWalkEntity e = new GhostWalkEntity(YoneRegistry.GHOST, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : dir.normalize();
        e.heading = dir;
        e.entityData.set(ORIGIN, new Vector3f((float) caster.getX(), (float) caster.getY(), (float) caster.getZ()));
        e.setPos(caster.getX() + dir.x, caster.getY(), caster.getZ() + dir.z);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
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

    public Vec3 origin() {
        Vector3f v = this.entityData.get(ORIGIN);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: marked enemies. */
    public int[] markedIds() {
        return clientMarked;
    }

    /** Client: recent spirit positions, oldest first. */
    public List<Vec3> trail() {
        return trail;
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

    private void syncMarked() {
        int[] arr = new int[marked.size()];
        int i = 0;
        for (int id : marked) {
            arr[i++] = id;
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("m", arr);
        this.entityData.set(LOG, tag);
    }

    private void cutAround(LivingEntity owner, Vec3 c) {
        double r = GhostWalkSpell.CUT_RADIUS;
        AABB box = new AABB(c.x - r, c.y - 3, c.z - r, c.x + r, c.y + 4, c.z + r);
        boolean changed = false;
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isEnemy(t, owner)) {
                continue;
            }
            double dx = t.getX() - c.x;
            double dz = t.getZ() - c.z;
            if (dx * dx + dz * dz > (r + t.getBbWidth() * 0.5) * (r + t.getBbWidth() * 0.5)) {
                continue;
            }
            hit(t, owner, GhostWalkSpell.CUT_FLAT, GhostWalkSpell.CUT_PERCENT);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact((ServerLevel) level(), t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 1.4f);
            if (marked.add(t.getId())) {
                changed = true;
            }
            level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.8f, 1.5f);
        }
        com.example.jjkspells.vfx.sprite.SpriteFx.slash((ServerLevel) level(), c.add(0, 1.0, 0), new Vec3(random.nextDouble() - 0.5, 0.2, random.nextDouble() - 0.5).add(0, 1.0, 0), random.nextFloat() * 360f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2.5f);
        if (changed) {
            syncMarked();
        }
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (owner == null) {
            discard();
            return;
        }
        Vec3 origin = origin();
        if (tickCount == 1) {
            level().playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.5f, 1.4f);
            level().playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 3.0f, 1.5f);
            level().playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.5f, 0.9f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, origin.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);
        }
        if (tickCount < SNAP) {
            // the body stands untouchable while the spirit is out
            owner.setDeltaMovement(0, Math.min(0.0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            owner.invulnerableTime = Math.max(owner.invulnerableTime, 6);
            owner.fallDistance = 0;
        }
        Vec3 pos = position();
        if (tickCount <= GO) {
            Vec3 look = owner.getLookAngle();
            Vec3 want = new Vec3(look.x, 0, look.z);
            if (want.lengthSqr() > 1.0E-4) {
                want = want.normalize();
                double cur = Math.atan2(heading.z, heading.x);
                double tgt = Math.atan2(want.z, want.x);
                double diff = Mth.wrapDegrees(Math.toDegrees(tgt - cur));
                double step = Mth.clamp(diff, -GhostWalkSpell.TURN_RATE_DEG, GhostWalkSpell.TURN_RATE_DEG);
                double na = cur + Math.toRadians(step);
                heading = new Vec3(Math.cos(na), 0, Math.sin(na));
            }
            Vec3 next = pos.add(heading.scale(GhostWalkSpell.SPEED));
            double dx = next.x - origin.x;
            double dz = next.z - origin.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > GhostWalkSpell.MAX_RANGE) {
                double k = GhostWalkSpell.MAX_RANGE / d;
                next = new Vec3(origin.x + dx * k, next.y, origin.z + dz * k);
            }
            double ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(next.x), (int) Math.floor(next.z));
            double wantY = Mth.clamp(ground, origin.y - 3, origin.y + 3);
            setPos(next.x, pos.y + (wantY - pos.y) * 0.4, next.z);
            if (tickCount == GO) {
                returnFrom = position();
            }
        } else if (tickCount <= SNAP) {
            if (returnFrom == null) {
                returnFrom = pos;
            }
            double f = Math.pow((double) (tickCount - GO) / RET, 1.5);
            setPos(returnFrom.x + (origin.x - returnFrom.x) * f, returnFrom.y + (origin.y - returnFrom.y) * f, returnFrom.z + (origin.z - returnFrom.z) * f);
        }
        if (tickCount <= SNAP && tickCount % GhostWalkSpell.CUT_INTERVAL == 0 && tickCount < SNAP) {
            cutAround(owner, position());
        }
        if (tickCount == SNAP) {
            snap(sl, owner, origin);
        }
    }

    private void snap(ServerLevel sl, LivingEntity owner, Vec3 origin) {
        // tear every marked enemy to the body and rip it
        for (int id : marked) {
            Entity e = sl.getEntity(id);
            if (e instanceof LivingEntity t && t.isAlive()) {
                hit(t, owner, GhostWalkSpell.RIP_FLAT, GhostWalkSpell.RIP_PERCENT);
                Vec3 to = origin.subtract(t.position());
                double dist = Math.max(to.length(), 0.01);
                Vec3 v = to.scale(Math.min(1.5, dist * 0.2) / dist);
                t.setDeltaMovement(v.x, 0.3, v.z);
                t.hurtMarked = true;
                t.fallDistance = 0;
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.5f, 0.9f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2f);
            }
        }
        // shockwave around the body
        double r = GhostWalkSpell.SNAP_RADIUS;
        AABB box = new AABB(origin.x - r, origin.y - 3, origin.z - r, origin.x + r, origin.y + 5, origin.z + r);
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isEnemy(t, owner)) {
                continue;
            }
            double dx = t.getX() - origin.x;
            double dz = t.getZ() - origin.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > r) {
                continue;
            }
            hit(t, owner, GhostWalkSpell.SNAP_FLAT, GhostWalkSpell.SNAP_PERCENT);
            double d = Math.max(dist, 0.01);
            t.setDeltaMovement(dx / d * 0.8, 0.5, dz / d * 0.8);
            t.hurtMarked = true;
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.6f);
        }
        level().playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5f, 1.5f);
        level().playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 3.0f, 0.8f);
        level().playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 1.0f);
        com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, origin, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (r * 2.0 / 6.0));
        com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, origin.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 3f);
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        clientMarked = this.entityData.get(LOG).getIntArray("m");
        if (tickCount <= SNAP) {
            trail.add(position());
            while (trail.size() > 48) {
                trail.remove(0);
            }
            if (tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), (random.nextDouble() - 0.5) * 0.1, 0.02, (random.nextDouble() - 0.5) * 0.1);
            }
        }
    }
}
