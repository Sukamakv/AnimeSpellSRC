package com.example.jjkspells.enchant;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The flame slash: flies straight along the wielder's look direction, hurting and igniting everything it passes through. */
public class FlameSlashEntity extends Entity {
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(FlameSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(FlameSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(FlameSlashEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> SPD = SynchedEntityData.defineId(FlameSlashEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;
    private Vec3 start = Vec3.ZERO;
    private final Set<UUID> hit = new HashSet<>();

    public FlameSlashEntity(EntityType<? extends FlameSlashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static void spawn(ServerLevel level, LivingEntity owner) {
        FlameSlashEntity e = new FlameSlashEntity(FlameSlashRegistry.SLASH.get(), level);
        Vec3 dir = owner.getLookAngle().normalize();
        Vec3 pos = owner.getEyePosition().add(dir.scale(1.2)).add(0, -0.25, 0);
        e.owner = owner.getUUID();
        e.start = pos;
        e.entityData.set(DX, (float) dir.x);
        e.entityData.set(DY, (float) dir.y);
        e.entityData.set(DZ, (float) dir.z);
        e.entityData.set(SPD, (float) FlameSlashConfig.speed());
        e.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(e);
    }

    public Vec3 dir() {
        Vec3 d = new Vec3(entityData.get(DX), entityData.get(DY), entityData.get(DZ));
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** Tilt of the crescent's long edge from horizontal, degrees (diagonal slash, readable from behind). */
    public static final double ROLL = 40.0;

    /** Unit vector along the slash's long edge (perpendicular to flight). */
    public static Vec3 chord(Vec3 dir) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        double r = Math.toRadians(ROLL);
        return side.scale(Math.cos(r)).add(up.scale(Math.sin(r))).normalize();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DX, 0f);
        entityData.define(DY, 0f);
        entityData.define(DZ, 1f);
        entityData.define(SPD, 1.5f);
    }

    /** How far along its flight the slash is, in blocks, at a (fractional) tick count. Same maths on client and server. */
    public double travelled(float ticks) {
        return Math.min(FlameSlashConfig.range(), entityData.get(SPD) * (double) ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return; // the client animates it smoothly in the renderer
        ServerLevel sl = (ServerLevel) level();
        Vec3 dir = dir();
        if (travelled(tickCount) >= FlameSlashConfig.range()) {
            discard();
            return;
        }
        // the entity itself never moves (no position packets): the flight is start + dir * speed * ticks, passes straight through walls
        Vec3 next = start.add(dir.scale(travelled(tickCount)));
        Entity own = owner != null ? sl.getEntity(owner) : null;
        // width = thickness along the flight, length = the long edge of the crescent
        double hl = FlameSlashConfig.width() * 0.5, hw = FlameSlashConfig.length() * 0.5;
        Vec3 side = chord(dir);
        Vec3 up = side.cross(dir).normalize();
        AABB box = new AABB(next, next).inflate(hw + 2.0);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box)) {
            if (t == own || !t.isAlive() || hit.contains(t.getUUID())) continue;
            if (own != null && (t.isAlliedTo(own) || (own instanceof LivingEntity ol && io.redspace.ironsspellbooks.damage.DamageSources.isFriendlyFireBetween(ol, t)))) continue;
            if (t instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 rel = t.getBoundingBox().getCenter().subtract(next);
            double along = rel.dot(dir), sd = rel.dot(side), ud = rel.dot(up);
            double pad = t.getBbWidth() * 0.5;
            if (Math.abs(along) > hl + pad + 0.1 || Math.abs(sd) > hw + pad || Math.abs(ud) > hl + t.getBbHeight() * 0.5) continue;
            hit.add(t.getUUID());
            DamageSource src = own instanceof LivingEntity ol ? sl.damageSources().indirectMagic(this, ol) : sl.damageSources().magic();
            t.invulnerableTime = 0;
            t.hurt(src, FlameSlashConfig.damage());
            if (t.isAlive()) t.setSecondsOnFire(FlameSlashConfig.burn());
        }
        // a little ember trail
        for (int i = 0; i < 2; i++) {
            double s2 = (random.nextDouble() - 0.5) * 2 * hw;
            Vec3 p = next.add(side.scale(s2));
            sl.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }
}
