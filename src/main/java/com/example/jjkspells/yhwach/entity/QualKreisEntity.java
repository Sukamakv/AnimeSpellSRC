package com.example.jjkspells.yhwach.entity;

import com.example.jjkspells.yhwach.YhwachRegistry;
import com.example.jjkspells.yhwach.spell.QualKreisSpell;
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

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Controller of "Qual Kreis". Timeline (ticks since cast):
 * <pre>
 *   0 .. APPEAR                           the 12 bows materialise one after another in a ring above the caster
 *   APPEAR .. APPEAR+FIRE_TICKS           barrage on the caster's aim point, damage every tick (7 s)
 *   .. +FADE                              bows dissolve
 * </pre>
 * The server keeps the aim point synced; every arrow, bow and ring is drawn client-side by QualKreisRenderer.
 */
public class QualKreisEntity extends Entity {
    public static final int APPEAR = 12;
    public static final int FIRE_TICKS = QualKreisSpell.FIRE_TICKS;
    public static final int FADE = 12;
    public static final int TOTAL = APPEAR + FIRE_TICKS + FADE;
    public static final double RANGE = 64.0;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(QualKreisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> TARGET = SynchedEntityData.defineId(QualKreisEntity.class, EntityDataSerializers.VECTOR3);

    private UUID ownerUuid;
    private Vec3 prevTarget;
    private Vec3 curTarget;

    public QualKreisEntity(EntityType<? extends QualKreisEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static QualKreisEntity create(Level level, LivingEntity caster) {
        QualKreisEntity e = new QualKreisEntity(YhwachRegistry.QUAL, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        Vec3 p = aimPoint(level, caster);
        e.entityData.set(TARGET, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        return e;
    }

    /** Centre of bow {@code i}; the ring slowly turns over the caster. */
    public static Vec3 bowCenter(Vec3 casterPos, int i, float t) {
        double a = i * (Math.PI * 2.0 / QualKreisSpell.BOW_COUNT) + t * 0.025;
        return casterPos.add(Math.cos(a) * QualKreisSpell.RING_RADIUS, QualKreisSpell.BOW_HEIGHT, Math.sin(a) * QualKreisSpell.RING_RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(TARGET, new Vector3f());
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

    public int getOwnerId() {
        return this.entityData.get(OWNER_ID);
    }

    public Vec3 target() {
        Vector3f v = this.entityData.get(TARGET);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Smoothed aim point for rendering. */
    public Vec3 targetAt(float pt) {
        if (curTarget == null || prevTarget == null) {
            return target();
        }
        return prevTarget.lerp(curTarget, pt);
    }

    // ------------------------------------------------------------------ simulation

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    /** Where the caster is aiming: first entity or block on the ray; in open air the point is dropped onto the ground below. */
    private static Vec3 aimPoint(Level level, LivingEntity owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult bhit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(level, owner, eye, stop, new AABB(eye, stop).inflate(1.0),
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en != owner);
        if (ehit != null) {
            return ehit.getEntity().position();
        }
        if (bhit.getType() == HitResult.Type.MISS) {
            BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -48, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (down.getType() != HitResult.Type.MISS) {
                return down.getLocation();
            }
        }
        return stop;
    }

    @Nullable
    private LivingEntity serverOwner() {
        if (ownerUuid == null || !(this.level() instanceof ServerLevel sl)) {
            return null;
        }
        Entity e = sl.getEntity(ownerUuid);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private void serverTick() {
        LivingEntity owner = serverOwner();
        if (owner == null || tickCount > TOTAL) {
            discard();
            return;
        }
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        Vec3 p = aimPoint(this.level(), owner);
        this.entityData.set(TARGET, new Vector3f((float) p.x, (float) p.y, (float) p.z));

        if (tickCount == 1) {
            Vec3 c = owner.position().add(0, QualKreisSpell.BOW_HEIGHT, 0);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.6f, 1.5f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.8f);
        }
        if (tickCount >= APPEAR && tickCount < APPEAR + FIRE_TICKS) {
            if (tickCount == APPEAR && level() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (QualKreisSpell.ZONE_RADIUS / 3.0));   // barrage begins
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);
            }
            if ((tickCount - APPEAR) % 20 == 10 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.sparks(sl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.5f);   // periodic arrow-rain pulse on the aim point
            }
            damage(owner, p);
            if (tickCount % 3 == 0) {
                level().playSound(null, p.x, p.y, p.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.4f, 1.6f + level().random.nextFloat() * 0.5f);
            }
            if (tickCount % 8 == 0) {
                level().playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.7f);
            }
        }
        if (tickCount == APPEAR + FIRE_TICKS) {
            Vec3 c = owner.position().add(0, QualKreisSpell.BOW_HEIGHT, 0);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 1.6f);
            if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);   // finish: bows dissolve
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2.5f);
            }
        }
    }

    private void damage(LivingEntity owner, Vec3 p) {
        double r = QualKreisSpell.ZONE_RADIUS;
        AABB box = new AABB(p.x - r, p.y - 3.0, p.z - r, p.x + r, p.y + QualKreisSpell.BOW_HEIGHT, p.z + r);
        for (LivingEntity t : this.level().getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && x != owner && !x.isSpectator())) {
            AABB bb = t.getBoundingBox();
            double dx = Mth.clamp(p.x, bb.minX, bb.maxX) - p.x;
            double dz = Mth.clamp(p.z, bb.minZ, bb.maxZ) - p.z;
            if (dx * dx + dz * dz > r * r) {
                continue;
            }
            float dmg = (float) (t.getMaxHealth() * QualKreisSpell.PERCENT_MAX_HEALTH_PER_TICK / 100.0);
            Vec3 keep = t.getDeltaMovement();
            t.invulnerableTime = 0;
            com.example.jjkspells.spells.SpellDamage.percent(t, dmg, owner, this.level().damageSources().indirectMagic(this, owner));   // respects Max Health Damage / Resistance
            com.example.jjkspells.spells.SpellDamage.irradiate(t, 120);
            t.setDeltaMovement(keep);
        }
    }

    // ------------------------------------------------------------------ client: smoothing + a few vanilla particles

    private void clientTick() {
        Vec3 t = target();
        if (curTarget == null) {
            prevTarget = t;
            curTarget = t;
        } else {
            prevTarget = curTarget;
            curTarget = curTarget.lerp(t, 0.45);
        }
        if (tickCount >= APPEAR && tickCount < APPEAR + FIRE_TICKS) {
            Vec3 p = curTarget;
            double r = QualKreisSpell.ZONE_RADIUS;
            for (int i = 0; i < 3; i++) {
                double th = random.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(random.nextDouble()) * r;
                this.level().addParticle(ParticleTypes.END_ROD, p.x + Math.cos(th) * rr, p.y + 0.2, p.z + Math.sin(th) * rr, 0, 0.12 + random.nextDouble() * 0.2, 0);
            }
            if (tickCount % 2 == 0) {
                double th = random.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(random.nextDouble()) * r;
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x + Math.cos(th) * rr, p.y + 0.3, p.z + Math.sin(th) * rr, (random.nextDouble() - 0.5) * 0.4, 0.2, (random.nextDouble() - 0.5) * 0.4);
            }
        }
    }
}
