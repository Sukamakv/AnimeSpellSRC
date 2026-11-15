package com.example.jjkspells.entity;

import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.gil.GilDmg;
import com.example.jjkspells.spells.FX;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One legendary weapon of the Gate of Babylon. It appears in a portal, turns to face the target while it spins into
 * position, then is fired. Standard weapons hit the first creature they meet; heavy ones are bigger, pierce several
 * creatures (each hit weaker than the one before); a weapon with a blast radius explodes where it lands instead.
 * Damage is dealt through GilDmg (flat + percent of max health, with Resonance / Divinity / Supremacy bonuses).
 */
public class GateWeaponEntity extends Entity {
    public static final int TYPES = 8;
    private static final int STUCK_TICKS = 24;

    /** What a weapon does. {@code speed} is blocks per tick, {@code homing} 0..1 per tick, {@code blast} 0 = single hit. */
    public record Params(float flat, float pct, float mult, boolean heavy, int pierce, float homing, double speed, float blast, boolean gate) {
        public static Params standard(float flat, float mult, double speed) {
            return new Params(flat, 0f, mult, false, 0, 0.06f, speed, 0f, true);
        }
    }

    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(GateWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(GateWeaponEntity.class, EntityDataSerializers.INT); // 0 forming, 1 flying, 2 stuck
    private static final EntityDataAccessor<Boolean> DATA_HEAVY = SynchedEntityData.defineId(GateWeaponEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerId;
    private UUID targetId;
    private Vec3 aim = Vec3.ZERO;
    private int formTicks = 14;
    private Vec3 vel = Vec3.ZERO;
    private int stuckFor = 0;
    private Params p = Params.standard(18f, 1f, 2.5);
    private int pierceLeft;
    private float falloff = 1f;
    private final Set<UUID> hit = new HashSet<>();

    public GateWeaponEntity(EntityType<? extends GateWeaponEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public GateWeaponEntity(Level level, LivingEntity owner, LivingEntity target, Vec3 aim, Vec3 at, int type, int formTicks, Params params) {
        this(ModEntities.GATE_WEAPON.get(), level);
        this.ownerId = owner.getUUID();
        this.targetId = target == null ? null : target.getUUID();
        this.aim = aim;
        this.formTicks = formTicks;
        this.p = params;
        this.pierceLeft = params.pierce();
        this.entityData.set(DATA_TYPE, Math.floorMod(type, TYPES));
        this.entityData.set(DATA_HEAVY, params.heavy());
        this.setPos(at.x, at.y, at.z);
        face(aim.subtract(at));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public int getWeaponType() {
        return this.entityData.get(DATA_TYPE);
    }

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    public boolean isHeavy() {
        return this.entityData.get(DATA_HEAVY);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, 0);
        this.entityData.define(DATA_STATE, 0);
        this.entityData.define(DATA_HEAVY, false);
    }

    private void face(Vec3 d) {
        if (d.lengthSqr() < 1e-6) return;
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        this.setYRot((float) (Mth.atan2(d.x, d.z) * 57.2957795));
        this.setXRot((float) (Mth.atan2(d.y, h) * 57.2957795));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        int state = getState();
        if (state == 0) {
            Vec3 want = currentAim(level);
            face(want.subtract(position()));
            if (tickCount == 1) {
                level.playSound(null, getX(), getY(), getZ(), ModSounds.GATE_FIRE.get(), SoundSource.PLAYERS, 0.6f, 1.2f + random.nextFloat() * 0.3f);
            }
            if (tickCount % 3 == 0) {
                FX.one(level, ModParticles.GOLD_SPARK.get(), position().add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6), Vec3.ZERO);
            }
            if (tickCount >= formTicks) {
                Vec3 d = want.subtract(position());
                if (d.lengthSqr() < 1e-4) d = new Vec3(0, -1, 0);
                d = d.normalize().add((random.nextDouble() - 0.5) * 0.03, (random.nextDouble() - 0.5) * 0.03, (random.nextDouble() - 0.5) * 0.03).normalize();
                this.vel = d.scale(p.speed());
                face(d);
                this.entityData.set(DATA_STATE, 1);
                level.playSound(null, getX(), getY(), getZ(), ModSounds.GATE_FIRE.get(), SoundSource.PLAYERS, p.heavy() ? 1.6f : 1.3f, 0.9f + random.nextFloat() * 0.3f);
                FX.one(level, ModParticles.GOLD_GLOW.get(), position(), new Vec3(p.heavy() ? 2.2 : 1.4, 0, 0));
            }
            return;
        }
        if (state == 2) {
            if (++stuckFor > STUCK_TICKS) {
                burst(level, position(), false);
                this.discard();
            }
            return;
        }
        // flying (with a little steering toward a living target)
        Entity tgt = targetId == null ? null : level.getEntity(targetId);
        if (p.homing() > 0f && tgt instanceof LivingEntity le && le.isAlive()) {
            Vec3 want = le.getBoundingBox().getCenter().subtract(position());
            if (want.lengthSqr() > 1e-4) {
                Vec3 nv = vel.normalize().scale(1f - p.homing()).add(want.normalize().scale(p.homing())).normalize();
                vel = nv.scale(p.speed());
                face(nv);
            }
        }
        Vec3 from = position();
        Vec3 to = from.add(vel);
        BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? to : bh.getLocation();
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        LivingEntity caster = owner instanceof LivingEntity lo ? lo : null;
        AABB box = new AABB(from, stop).inflate(p.heavy() ? 1.3 : 0.7);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, this, from, stop, box,
                e -> e instanceof LivingEntity le && !hit.contains(e.getUUID()) && GilDmg.canHit(caster, le), p.heavy() ? 0.9f : 0.5f);
        FX.one(level, ModParticles.GOLD_LINE.get(), from.subtract(vel.scale(0.4)), vel.scale(1.4));
        if (tickCount % 2 == 0) {
            FX.one(level, ModParticles.GOLD_SPARK.get(), from, new Vec3((random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1));
        }
        if (eh != null) {
            Vec3 at = eh.getLocation();
            setPos(at.x, at.y, at.z);
            LivingEntity victim = (LivingEntity) eh.getEntity();
            if (p.blast() > 0f) {
                explode(level, caster, at);
                this.discard();
                return;
            }
            GilDmg.hit(level, caster, victim, p.flat() * falloff, p.pct(), p.mult(), p.gate(), this);
            if (p.heavy() && !GilDmg.isBoss(victim)) {
                Vec3 push = vel.normalize().scale(0.9).add(0, 0.3, 0);
                victim.setDeltaMovement(push);
                victim.hurtMarked = true;
            }
            hit.add(victim.getUUID());
            burst(level, at, true);
            if (pierceLeft > 0) {
                pierceLeft--;
                falloff *= 0.85f;
                setPos(to.x, to.y, to.z);
            } else {
                this.discard();
            }
            return;
        }
        if (bh.getType() != HitResult.Type.MISS) {
            Vec3 at = bh.getLocation();
            setPos(at.x, at.y, at.z);
            if (p.blast() > 0f) {
                explode(level, caster, at);
                this.discard();
                return;
            }
            this.entityData.set(DATA_STATE, 2);
            burst(level, at, true);
            BlockState st = level.getBlockState(bh.getBlockPos());
            if (!st.isAir()) {
                for (int i = 0; i < 6; i++) {
                    FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), at,
                            new Vec3((random.nextDouble() - 0.5) * 0.7, 0.2 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.7));
                }
            }
            Vec3 in = vel.normalize().scale(0.6);
            setPos(at.x + in.x, at.y + in.y, at.z + in.z);
            return;
        }
        setPos(to.x, to.y, to.z);
        if (tickCount > formTicks + 70) {
            this.discard();
        }
    }

    private Vec3 currentAim(ServerLevel level) {
        Entity t = targetId == null ? null : level.getEntity(targetId);
        if (t instanceof LivingEntity le && le.isAlive()) {
            Vec3 c = le.getBoundingBox().getCenter();
            Vec3 mv = le.getDeltaMovement();
            double dist = c.distanceTo(position());
            return c.add(mv.x * dist / p.speed() * 0.6, 0, mv.z * dist / p.speed() * 0.6);
        }
        return aim;
    }

    /** Blast mode: everything within the radius is hit once, with a small explosion of gold. */
    private void explode(ServerLevel level, LivingEntity caster, Vec3 at) {
        float r = p.blast();
        for (LivingEntity e : GilDmg.around(level, caster, at, r)) {
            GilDmg.hit(level, caster, e, p.flat(), p.pct(), p.mult(), p.gate(), this);
        }
        level.playSound(null, at.x, at.y, at.z, ModSounds.WEAPON_IMPACT.get(), SoundSource.PLAYERS, 1.6f, 0.6f + random.nextFloat() * 0.3f);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.3f);
        FX.one(level, ModParticles.GOLD_GLOW.get(), at, new Vec3(r * 1.6, 0, 0));
        FX.one(level, ModParticles.GOLD_RING.get(), at, new Vec3(r * 1.2, 0, 0));
        FX.one(level, ModParticles.GOLD_RING_FLAT.get(), at.add(0, 0.1, 0), new Vec3(r * 1.3, 0, 0));
        for (int i = 0; i < 16; i++) {
            Vec3 d = new Vec3(random.nextDouble() - 0.5, random.nextDouble() * 0.9, random.nextDouble() - 0.5).normalize().scale(0.3 + random.nextDouble() * 0.7);
            FX.one(level, i % 3 == 0 ? ModParticles.GOLD_SHARD.get() : ModParticles.GOLD_SPARK.get(), at, d);
        }
    }

    private void burst(ServerLevel level, Vec3 at, boolean loud) {
        FX.one(level, ModParticles.GOLD_GLOW.get(), at, new Vec3(1.8, 0, 0));
        for (int i = 0; i < 8; i++) {
            FX.one(level, ModParticles.GOLD_SPARK.get(), at, new Vec3((random.nextDouble() - 0.5) * 0.7, 0.1 + random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.7));
        }
        for (int i = 0; i < 3; i++) {
            FX.one(level, ModParticles.GOLD_SHARD.get(), at, new Vec3((random.nextDouble() - 0.5) * 0.4, 0.15 + random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4));
        }
        if (loud) {
            level.playSound(null, at.x, at.y, at.z, ModSounds.WEAPON_IMPACT.get(), SoundSource.PLAYERS, 1.4f, 0.8f + random.nextFloat() * 0.5f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.discard();
    }
}
