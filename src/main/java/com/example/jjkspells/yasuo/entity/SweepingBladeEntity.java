package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yasuo.YasuoRegistry;
import com.example.jjkspells.yasuo.spell.SweepingBladeSpell;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Sweeping Blade". Dash i happens at tick T0 + STEP * i: Yasuo teleports past the nearest enemy that was not cut yet and cuts everything on the way.
 * Every dash is logged as {startXYZ * 10, endXYZ * 10, tick} so the renderer can draw the trail, afterimages and cuts.
 */
public class SweepingBladeEntity extends Entity {
    public static final int T0 = 3;
    public static final int STEP = SweepingBladeSpell.STEP;
    public static final int END = T0 + SweepingBladeSpell.MAX_DASHES * STEP + 30;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SweepingBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(SweepingBladeEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(SweepingBladeEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final Set<Integer> cut = new HashSet<>();
    private final List<Integer> dashLog = new ArrayList<>();
    private int dashes = 0;
    private boolean finished = false;
    private List<int[]> clientDashes = new ArrayList<>();

    public SweepingBladeEntity(EntityType<? extends SweepingBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SweepingBladeEntity create(Level level, LivingEntity caster) {
        SweepingBladeEntity e = new SweepingBladeEntity(YasuoRegistry.BLADE, level);
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

    /** Client: dashes so far, each {sx, sy, sz, ex, ey, ez (all * 10), tick}. */
    public List<int[]> dashes() {
        return clientDashes;
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

    private void hit(LivingEntity t, LivingEntity owner, Vec3 push) {
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        float flatDmg = (float) (SweepingBladeSpell.DASH_FLAT);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * SweepingBladeSpell.DASH_PERCENT), owner, dsrc);
        }
        t.setDeltaMovement(push.x, 0.25, push.z);
        t.hurtMarked = true;
    }

    private boolean free(LivingEntity owner, Vec3 c) {
        AABB bb = owner.getBoundingBox().move(c.subtract(owner.position()));
        return level().noCollision(owner, bb);
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (owner == null) {
            return;
        }
        if (tickCount == 1) {
            level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 3.0f, 1.6f);
            level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.5f, 1.9f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, owner.position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.2f, T0 + 6);
            com.example.jjkspells.vfx.sprite.SpriteFx.glowOn(sl, owner, 1.0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.8f, 12);
        }
        if (finished || tickCount < T0) {
            return;
        }
        if (tickCount < T0 + dashes * STEP || (tickCount - T0) % STEP != 0) {
            // hold still between dashes
            if (!finished && tickCount > T0) {
                owner.setDeltaMovement(0, 0, 0);
                owner.hurtMarked = true;
                owner.fallDistance = 0;
            }
            return;
        }
        if (dashes >= SweepingBladeSpell.MAX_DASHES) {
            finished = true;
            return;
        }
        Vec3 start = owner.position();
        Vec3 dir = dir();
        // nearest enemy not cut yet
        LivingEntity target = null;
        double best = SweepingBladeSpell.RANGE * SweepingBladeSpell.RANGE;
        double r = SweepingBladeSpell.RANGE;
        AABB box = new AABB(start.x - r, start.y - 4, start.z - r, start.x + r, start.y + 6, start.z + r);
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isEnemy(t, owner) || cut.contains(t.getId())) {
                continue;
            }
            double d2 = t.distanceToSqr(owner);
            if (d2 < best) {
                best = d2;
                target = t;
            }
        }
        Vec3 end = null;
        if (target != null) {
            Vec3 tp = target.position();
            Vec3 to = new Vec3(tp.x - start.x, 0, tp.z - start.z);
            Vec3 nd = to.lengthSqr() < 0.01 ? dir : to.normalize();
            Vec3[] tries = {tp.add(nd.scale(2.3)), tp.add(nd.scale(1.3)), tp.add(nd.scale(-1.6)), tp};
            for (Vec3 c : tries) {
                if (free(owner, c)) {
                    end = c;
                    break;
                }
            }
            dir = nd;
        } else if (dashes == 0) {
            end = start;
            for (double f = 1.0; f <= SweepingBladeSpell.FALLBACK_LENGTH; f += 1.0) {
                Vec3 c = start.add(dir.scale(f));
                if (free(owner, c)) {
                    end = c;
                } else {
                    break;
                }
            }
        }
        if (end == null || end.distanceToSqr(start) < 0.5) {
            finished = true;
            return;
        }
        // cut everything along the path, the target included
        Vec3 seg = end.subtract(start);
        double len = seg.length();
        Vec3 sd = seg.scale(1.0 / len);
        AABB path = new AABB(Math.min(start.x, end.x), Math.min(start.y, end.y) - 2, Math.min(start.z, end.z), Math.max(start.x, end.x), Math.max(start.y, end.y) + 3, Math.max(start.z, end.z)).inflate(SweepingBladeSpell.HIT_HALF_WIDTH + 1.0);
        boolean any = false;
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, path)) {
            if (!isEnemy(t, owner)) {
                continue;
            }
            Vec3 rel = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(start.add(0, 1.0, 0));
            double along = rel.dot(sd);
            double lat = rel.subtract(sd.scale(along)).length();
            boolean isTarget = t == target;
            if (!isTarget && (along < -0.5 || along > len + 0.5 || lat > SweepingBladeSpell.HIT_HALF_WIDTH + t.getBbWidth() * 0.5)) {
                continue;
            }
            if (!isTarget && cut.contains(t.getId())) {
                continue;
            }
            cut.add(t.getId());
            Vec3 side = new Vec3(-sd.z, 0, sd.x);
            double sgn = rel.dot(side) >= 0 ? 1 : -1;
            hit(t, owner, sd.scale(0.35).add(side.scale(0.35 * sgn)));
            any = true;
            level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.5f, 1.5f);
            level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 1.3f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.8f);
        }
        // move Yasuo
        float yaw = (float) (Math.toDegrees(Math.atan2(-sd.x, sd.z)));
        if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.connection.teleport(end.x, end.y, end.z, yaw, owner.getXRot());
        } else {
            owner.moveTo(end.x, end.y, end.z, yaw, owner.getXRot());
        }
        owner.setDeltaMovement(0, 0, 0);
        owner.hurtMarked = true;
        owner.fallDistance = 0;
        owner.invulnerableTime = Math.max(owner.invulnerableTime, STEP + 4);
        level().playSound(null, end.x, end.y + 1, end.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 1.7f);
        level().playSound(null, start.x, start.y + 1, start.z, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 2.0f, 1.5f);
        Vec3 slashN = new Vec3(-sd.z, 0, sd.x);
        com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, start.add(end).scale(0.5).add(0, 1.0, 0), slashN, 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) Math.max(2.5, len * 0.8));
        com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, start.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f);
        com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, end.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2f);

        dashLog.add((int) Math.round(start.x * 10));
        dashLog.add((int) Math.round(start.y * 10));
        dashLog.add((int) Math.round(start.z * 10));
        dashLog.add((int) Math.round(end.x * 10));
        dashLog.add((int) Math.round(end.y * 10));
        dashLog.add((int) Math.round(end.z * 10));
        dashLog.add(tickCount);
        dashes++;
        int[] arr = new int[dashLog.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = dashLog.get(i);
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("d", arr);
        this.entityData.set(LOG, tag);
        if (target == null) {
            finished = true;
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        int[] d = this.entityData.get(LOG).getIntArray("d");
        if (d.length / 7 != clientDashes.size()) {
            clientDashes = new ArrayList<>();
            for (int i = 0; i + 6 < d.length; i += 7) {
                clientDashes.add(new int[]{d[i], d[i + 1], d[i + 2], d[i + 3], d[i + 4], d[i + 5], d[i + 6]});
            }
            if (!clientDashes.isEmpty()) {
                int[] l = clientDashes.get(clientDashes.size() - 1);
                for (int i = 0; i < 20; i++) {
                    double f = random.nextDouble();
                    level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            (l[0] + (l[3] - l[0]) * f) / 10.0, (l[1] + (l[4] - l[1]) * f) / 10.0 + 1 + (random.nextDouble() - 0.5), (l[2] + (l[5] - l[2]) * f) / 10.0,
                            (random.nextDouble() - 0.5) * 0.2, 0.02, (random.nextDouble() - 0.5) * 0.2);
                }
            }
        }
    }
}
