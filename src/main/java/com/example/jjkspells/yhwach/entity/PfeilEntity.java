package com.example.jjkspells.yhwach.entity;

import com.example.jjkspells.yhwach.YhwachRegistry;
import com.example.jjkspells.yhwach.spell.HeiligerPfeilSpell;
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
 * Controller entity of "Heiliger Pfeil". It owns the whole timeline:
 * <pre>
 *   0 .. CHARGE_TICKS          bow materialises, string is drawn, arrow charges (1.2 s)
 *   CHARGE_TICKS               arrow is loosed: hit-scan to the aimed point, laser beam visual
 *   +TRAVEL_TICKS              blast detonates, damage is dealt
 *   +LIFE_AFTER_FIRE           visuals fade out, entity is discarded
 * </pre>
 * All visuals are drawn client-side by PfeilRenderer; the server only keeps the synced state.
 */
public class PfeilEntity extends Entity {
    public static final int CHARGE_TICKS = 24;
    public static final int TRAVEL_TICKS = 3;
    public static final int LIFE_AFTER_FIRE = 48;
    public static final double RANGE = 160.0;
    public static final double BOW_HEIGHT = 2.3;
    public static final double BOW_FORWARD = 2.4;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(PfeilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIRED = SynchedEntityData.defineId(PfeilEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> ORIGIN = SynchedEntityData.defineId(PfeilEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> IMPACT = SynchedEntityData.defineId(PfeilEntity.class, EntityDataSerializers.VECTOR3);

    private UUID ownerUuid;
    private int fireTick = -1;

    public PfeilEntity(EntityType<? extends PfeilEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static PfeilEntity create(Level level, LivingEntity caster) {
        PfeilEntity e = new PfeilEntity(YhwachRegistry.PFEIL, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(FIRED, false);
        this.entityData.define(ORIGIN, new Vector3f());
        this.entityData.define(IMPACT, new Vector3f());
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

    // ------------------------------------------------------------------ accessors used by the renderer

    public int getOwnerId() {
        return this.entityData.get(OWNER_ID);
    }

    public boolean isFired() {
        return this.entityData.get(FIRED);
    }

    public int getFireTick() {
        return fireTick;
    }

    public Vec3 origin() {
        Vector3f v = this.entityData.get(ORIGIN);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Vec3 impact() {
        Vector3f v = this.entityData.get(IMPACT);
        return new Vec3(v.x(), v.y(), v.z());
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
        if (!isFired()) {
            if (owner == null) {
                discard();
                return;
            }
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
            if (tickCount == 1) {
                Vec3 c = BowFrame.charging(owner.position(), owner.getLookAngle()).center;
                level().playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.9f);
                level().playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.6f);
            }
            if (tickCount == 12) {
                Vec3 c = owner.position();
                level().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.6f, 1.5f);
            }
            if (tickCount >= CHARGE_TICKS) {
                fire(owner);
            }
            return;
        }
        int fa = tickCount - fireTick;
        if (fa == TRAVEL_TICKS) {
            explode(owner);
        }
        if (fa > LIFE_AFTER_FIRE) {
            discard();
        }
    }

    private void fire(LivingEntity owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult bhit = this.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        AABB box = new AABB(eye, stop).inflate(1.0);
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(this.level(), owner, eye, stop, box,
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en != owner);
        Vec3 p = ehit != null ? ehit.getLocation() : stop;

        BowFrame frame = BowFrame.charging(owner.position(), look);
        Vec3 origin = frame.center;
        if (p.subtract(origin).dot(look) < 0.5) {
            p = origin.add(look.scale(0.5));
        }
        this.entityData.set(ORIGIN, new Vector3f((float) origin.x, (float) origin.y, (float) origin.z));
        this.entityData.set(IMPACT, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        this.entityData.set(FIRED, true);
        this.fireTick = tickCount;
        this.setPos(origin.x, origin.y, origin.z);

        level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.9f);
        level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3.0f, 0.7f);
        level().playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 2.0f);
    }

    private void explode(@Nullable LivingEntity owner) {
        Vec3 p = impact();
        double r = HeiligerPfeilSpell.BLAST_RADIUS;
        AABB area = new AABB(p, p).inflate(r);
        for (LivingEntity t : this.level().getEntitiesOfClass(LivingEntity.class, area,
                x -> x.isAlive() && x != owner && !x.isSpectator())) {
            AABB bb = t.getBoundingBox();
            double cx = Mth.clamp(p.x, bb.minX, bb.maxX);
            double cy = Mth.clamp(p.y, bb.minY, bb.maxY);
            double cz = Mth.clamp(p.z, bb.minZ, bb.maxZ);
            if (p.distanceToSqr(cx, cy, cz) > r * r) {
                continue;
            }
            float pctAmt = (float) (HeiligerPfeilSpell.PERCENT_MAX_HEALTH / 100.0 * t.getMaxHealth());
            net.minecraft.world.damagesource.DamageSource dsrc = this.level().damageSources().indirectMagic(this, owner);
            t.invulnerableTime = 0;
            if (HeiligerPfeilSpell.FLAT_DAMAGE > 0) io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, (float) HeiligerPfeilSpell.FLAT_DAMAGE, dsrc);
            if (t.isAlive()) com.example.jjkspells.spells.SpellDamage.percent(t, pctAmt, owner, dsrc);   // respects Max Health Damage / Resistance
            com.example.jjkspells.spells.SpellDamage.irradiate(t, 120);
            if (t.isAlive()) {
                Vec3 push = t.position().subtract(p);
                push = new Vec3(push.x, 0, push.z);
                push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : push.normalize().scale(1.4);
                t.setDeltaMovement(t.getDeltaMovement().add(push.x, 0.65, push.z));
                t.hurtMarked = true;
            }
        }
        level().playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.8f);
        level().playSound(null, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.4f);
        level().playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 4.0f, 0.5f);
    }

    // ------------------------------------------------------------------ client-only particles (the heavy VFX is in PfeilRenderer)

    private void clientTick() {
        if (isFired() && fireTick < 0) {
            fireTick = tickCount;
        }
        if (!isFired()) {
            Entity owner = this.level().getEntity(getOwnerId());
            if (owner == null) {
                return;
            }
            BowFrame fr = BowFrame.charging(owner.position(), owner.getLookAngle());
            double reveal = Math.min(1.0, tickCount / 7.0);
            for (int i = 0; i < 3; i++) {
                double t = (this.random.nextDouble() * 2 - 1) * reveal;
                Vec3 bp = fr.bowPoint(t, 1.0);
                Vec3 v = fr.center.subtract(bp).scale(0.025).add(fr.f.scale(0.01));
                this.level().addParticle(ParticleTypes.END_ROD, bp.x, bp.y, bp.z, v.x, v.y + 0.01, v.z);
            }
            if (tickCount > 10) {
                Vec3 tip = fr.nock(1.9 * Math.min(1.0, tickCount / (double) CHARGE_TICKS)).add(fr.f.scale(4.2));
                for (int i = 0; i < 2; i++) {
                    this.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                            tip.x + (random.nextDouble() - 0.5) * 0.6, tip.y + (random.nextDouble() - 0.5) * 0.6, tip.z + (random.nextDouble() - 0.5) * 0.6,
                            (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2);
                }
            }
            return;
        }
        int fa = tickCount - fireTick;
        Vec3 o = origin();
        Vec3 p = impact();
        if (fa == 1) {
            Vec3 d = p.subtract(o);
            double len = d.length();
            Vec3 dir = d.scale(1.0 / Math.max(len, 1.0E-4));
            for (double s = 0; s < len; s += Math.max(1.5, len / 60.0)) {
                Vec3 q = o.add(dir.scale(s));
                this.level().addParticle(ParticleTypes.END_ROD, q.x, q.y, q.z,
                        (random.nextDouble() - 0.5) * 0.12, (random.nextDouble() - 0.5) * 0.12, (random.nextDouble() - 0.5) * 0.12);
            }
        }
        if (fa == TRAVEL_TICKS) {
            this.level().addParticle(ParticleTypes.FLASH, p.x, p.y, p.z, 0, 0, 0);
            for (int i = 0; i < 140; i++) {
                double th = random.nextDouble() * Math.PI * 2;
                double ph = Math.acos(random.nextDouble() * 1.6 - 0.6);
                double sp = 0.4 + random.nextDouble() * 1.6;
                double dx = Math.sin(ph) * Math.cos(th) * sp;
                double dy = Math.cos(ph) * sp * 0.9;
                double dz = Math.sin(ph) * Math.sin(th) * sp;
                this.level().addParticle(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, p.x, p.y + 0.3, p.z, dx, dy, dz);
            }
            for (int i = 0; i < 40; i++) {
                double th = random.nextDouble() * Math.PI * 2;
                double sp = 0.8 + random.nextDouble() * 1.4;
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.2, p.z, Math.cos(th) * sp, 0.2 + random.nextDouble() * 0.6, Math.sin(th) * sp);
            }
        }
        if (fa > TRAVEL_TICKS && fa < TRAVEL_TICKS + 24 && fa % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double th = random.nextDouble() * Math.PI * 2;
                double rr = random.nextDouble() * HeiligerPfeilSpell.BLAST_RADIUS * 0.9;
                this.level().addParticle(ParticleTypes.END_ROD, p.x + Math.cos(th) * rr, p.y + 0.2, p.z + Math.sin(th) * rr, 0, 0.18 + random.nextDouble() * 0.25, 0);
            }
        }
    }
}
