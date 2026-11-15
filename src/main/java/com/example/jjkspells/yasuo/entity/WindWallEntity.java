package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yasuo.YasuoRegistry;
import com.example.jjkspells.yasuo.spell.WindWallSpell;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller of "Wind Wall". The entity sits at the middle of the wall's base; the wall is perpendicular to the synced facing.
 * Every tick it destroys foreign projectiles that cross the wall and every few ticks it shoves and cuts enemies touching it.
 */
public class WindWallEntity extends Entity {
    public static final int RISE = 8;
    public static final int DURATION = WindWallSpell.DURATION_TICKS;
    public static final int END = DURATION + 16;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(WindWallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(WindWallEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(WindWallEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final List<Integer> blockLog = new ArrayList<>();
    private List<int[]> clientBlocks = new ArrayList<>();
    private int clientBlockSize = 0;

    public WindWallEntity(EntityType<? extends WindWallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static WindWallEntity create(Level level, LivingEntity caster) {
        WindWallEntity e = new WindWallEntity(YasuoRegistry.WALL, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : dir.normalize();
        e.entityData.set(DIR, new Vector3f((float) dir.x, 0f, (float) dir.z));
        e.setPos(caster.getX() + dir.x * WindWallSpell.DISTANCE, caster.getY(), caster.getZ() + dir.z * WindWallSpell.DISTANCE);
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

    /** Client: projectiles destroyed so far, as {x * 10, y * 10, z * 10, tick}. */
    public List<int[]> blocks() {
        return clientBlocks;
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

    /** Is the point inside the wall slab, thickened by {@code thick} on both sides? */
    private boolean inside(Vec3 pt, Vec3 c, Vec3 d, Vec3 s, double thick) {
        Vec3 r = pt.subtract(c);
        double along = r.dot(d);
        double lat = r.dot(s);
        return Math.abs(along) <= thick && Math.abs(lat) <= WindWallSpell.HALF_WIDTH + 0.5 && r.y >= -1.5 && r.y <= WindWallSpell.HEIGHT;
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        Vec3 c = position();
        Vec3 d = dir();
        Vec3 s = new Vec3(d.z, 0, -d.x);

        if (tickCount == 1) {
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 4.0f, 0.7f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 3.0f, 1.3f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 1.8f);
        }
        if (tickCount < RISE || tickCount > DURATION) {
            return;
        }
        if (tickCount % 30 == 0) {
            level().playSound(null, c.x, c.y + 4, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
        double reach = WindWallSpell.HALF_WIDTH + WindWallSpell.HEIGHT;
        AABB box = new AABB(c.x - reach, c.y - 3, c.z - reach, c.x + reach, c.y + WindWallSpell.HEIGHT + 3, c.z + reach);
        for (Projectile pr : level().getEntitiesOfClass(Projectile.class, box)) {
            Entity po = pr.getOwner();
            if (po != null && (po == owner || (owner != null && owner.isAlliedTo(po)))) {
                continue;
            }
            Vec3 now = pr.position();
            Vec3 before = new Vec3(pr.xOld, pr.yOld, pr.zOld);
            double a0 = before.subtract(c).dot(d);
            double a1 = now.subtract(c).dot(d);
            boolean crossed = (a0 < 0) != (a1 < 0);
            Vec3 hitPt = now;
            if (crossed && Math.abs(a0 - a1) > 1.0E-6) {
                double f = a0 / (a0 - a1);
                hitPt = before.add(now.subtract(before).scale(f));
            }
            if ((crossed && inside(hitPt, c, d, s, 1.0E9)) || inside(now, c, d, s, 1.2)) {
                if (!inside(hitPt, c, d, s, 1.0E9)) {
                    continue;
                }
                blockLog.add((int) Math.round(hitPt.x * 10));
                blockLog.add((int) Math.round(hitPt.y * 10));
                blockLog.add((int) Math.round(hitPt.z * 10));
                blockLog.add(tickCount);
                while (blockLog.size() > 160) {
                    blockLog.subList(0, 4).clear();
                }
                int[] arr = new int[blockLog.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = blockLog.get(i);
                }
                CompoundTag tag = new CompoundTag();
                tag.putIntArray("b", arr);
                this.entityData.set(LOG, tag);
                level().playSound(null, hitPt.x, hitPt.y, hitPt.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.5f, 1.4f);
                level().playSound(null, hitPt.x, hitPt.y, hitPt.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.5f, 1.6f);
                pr.discard();
            }
        }
        if (tickCount % 5 == 0) {
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (!isEnemy(t, owner)) {
                    continue;
                }
                Vec3 centre = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ());
                if (!inside(centre, c, d, s, 1.6 + t.getBbWidth() * 0.5)) {
                    continue;
                }
                t.invulnerableTime = 0;
                net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
                float flatDmg = (float) (WindWallSpell.TOUCH_FLAT);
                if (flatDmg > 0) {
                    io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
                }
                if (t.isAlive()) {
                    com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * WindWallSpell.TOUCH_PERCENT), owner, dsrc);
                }
                double side = Math.signum(centre.subtract(c).dot(d));
                if (side == 0) {
                    side = 1;
                }
                t.setDeltaMovement(d.x * side * 0.9, 0.3, d.z * side * 0.9);
                t.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int[] b = tag.getIntArray("b");
        if (b.length / 4 != clientBlockSize || (b.length >= 4 && (clientBlocks.isEmpty() || clientBlocks.get(clientBlocks.size() - 1)[3] != b[b.length - 1]))) {
            clientBlocks = new ArrayList<>();
            for (int i = 0; i + 3 < b.length; i += 4) {
                clientBlocks.add(new int[]{b[i], b[i + 1], b[i + 2], b[i + 3]});
            }
            clientBlockSize = b.length / 4;
            if (!clientBlocks.isEmpty()) {
                int[] last = clientBlocks.get(clientBlocks.size() - 1);
                if (last[3] >= tickCount - 2) {
                    for (int i = 0; i < 14; i++) {
                        level().addParticle(ParticleTypes.CLOUD, last[0] / 10.0, last[1] / 10.0, last[2] / 10.0, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
                    }
                }
            }
        }
        if (tickCount == 2) {
            Vec3 d = dir();
            Vec3 s = new Vec3(d.z, 0, -d.x);
            for (int i = 0; i < 50; i++) {
                double x = (random.nextDouble() * 2 - 1) * WindWallSpell.HALF_WIDTH;
                level().addParticle(ParticleTypes.CLOUD, getX() + s.x * x, getY() + 0.3, getZ() + s.z * x, d.x * 0.25, 0.15, d.z * 0.25);
            }
        }
    }
}
