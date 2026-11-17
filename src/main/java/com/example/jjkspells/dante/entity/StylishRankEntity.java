package com.example.jjkspells.dante.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.dante.DanteRegistry;
import com.example.jjkspells.dante.spell.StylishRankSpell;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of the Stylish Rank spell. It follows the caster and shows the rank letter beside him.
 * <pre>
 *   RANKING    rank D..A, every Dante spell cast = +1 rank. No Dante spell for IDLE_TICKS: the spell turns off.
 *   COUNTDOWN  rank S reached: COUNTDOWN_TICKS timer. The rank can still climb to SS / SSS meanwhile.
 *   HELL       timer over at S / SS: bullet hell disc around the caster for HELL_TICKS (SS: bigger and stronger).
 *   SLASH      timer over at SSS: wind-up, then the Hellfire Slash in the direction the caster looks at.
 *   END        short fade out.
 * </pre>
 * Every other Dante spell must call {@link #notifyDanteCast(LivingEntity)} when it is cast.
 */
public class StylishRankEntity extends Entity {
    public static final int PH_RANKING = 0;
    public static final int PH_COUNTDOWN = 1;
    public static final int PH_HELL = 2;
    public static final int PH_SLASH = 3;
    public static final int PH_END = 4;

    public static final int RANK_S = 4;
    public static final int RANK_SS = 5;
    public static final int RANK_SSS = 6;

    public static final int IDLE_TICKS = 200;
    public static final int COUNTDOWN_TICKS = 140;
    public static final int HELL_TICKS = 180;
    public static final int SLASH_WINDUP = 26;
    public static final int SLASH_TICKS = 40;
    public static final int END_TICKS = 24;

    private static final Map<UUID, StylishRankEntity> ACTIVE = new HashMap<>();

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK_TICK = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HELL_RADIUS = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> SLASH_ORIGIN = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> SLASH_DIR = SynchedEntityData.defineId(StylishRankEntity.class, EntityDataSerializers.VECTOR3);

    private UUID ownerUuid;
    private int idle = 0;
    private boolean slashThenHell = false;

    public StylishRankEntity(EntityType<? extends StylishRankEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Called by the spell. Starts a session, or counts as a Dante cast when one is already running. */
    public static void cast(Level level, LivingEntity caster) {
        ACTIVE.values().removeIf(Entity::isRemoved);
        StylishRankEntity old = ACTIVE.get(caster.getUUID());
        if (old != null) {
            old.rankUp();
            return;
        }
        StylishRankEntity e = new StylishRankEntity(DanteRegistry.STYLISH, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        ACTIVE.put(caster.getUUID(), e);
        level.addFreshEntity(e);
    }

    /** Every Dante spell calls this (server side) when it is cast: the style rank goes up by one. */
    public static void notifyDanteCast(LivingEntity caster) {
        if (caster == null || caster.level().isClientSide) {
            return;
        }
        StylishRankEntity e = ACTIVE.get(caster.getUUID());
        if (e != null && !e.isRemoved()) {
            e.rankUp();
        }
    }

    private void rankUp() {
        idle = 0;
        int ph = phase();
        if (ph != PH_RANKING && ph != PH_COUNTDOWN) {
            return;
        }
        int r = rank();
        if (r >= RANK_SSS) {
            return;
        }
        r++;
        this.entityData.set(RANK, r);
        this.entityData.set(RANK_TICK, tickCount);
        float pitch = 0.7f + 0.13f * r;
        level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 2.5f, Math.min(2.0f, pitch));
        level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.4f + 0.08f * r);
        if (level() instanceof ServerLevel rsl) {
            Vec3 hp = position().add(0, 2.0, 0);
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(rsl, hp, r >= RANK_S ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.0f + 0.2f * r);   // rank up
            if (r >= RANK_S) {
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(rsl, hp, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f + 0.5f * (r - RANK_S));
            }
            if (r == RANK_S && ph == PH_RANKING) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(rsl, position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 1.5f);
            }
        }
        if (r >= RANK_S) {
            level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2.0f, 0.8f + 0.1f * r);
        }
        if (r == RANK_S && ph == PH_RANKING) {
            setPhase(PH_COUNTDOWN);
            level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.5f, 1.4f);
        }
    }

    private void setPhase(int ph) {
        this.entityData.set(PHASE, ph);
        this.entityData.set(PHASE_TICK, tickCount);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(RANK, 0);
        this.entityData.define(RANK_TICK, 0);
        this.entityData.define(PHASE, PH_RANKING);
        this.entityData.define(PHASE_TICK, 0);
        this.entityData.define(HELL_RADIUS, 7f);
        this.entityData.define(SLASH_ORIGIN, new Vector3f());
        this.entityData.define(SLASH_DIR, new Vector3f(0, 0, 1));
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

    @Override
    public void remove(RemovalReason reason) {
        if (ownerUuid != null) {
            ACTIVE.remove(ownerUuid, this);
        }
        super.remove(reason);
    }

    public int rank() {
        return this.entityData.get(RANK);
    }

    public int rankTick() {
        return this.entityData.get(RANK_TICK);
    }

    public int phase() {
        return this.entityData.get(PHASE);
    }

    public int phaseTick() {
        return this.entityData.get(PHASE_TICK);
    }

    public float hellRadius() {
        return this.entityData.get(HELL_RADIUS);
    }

    public Vec3 slashOrigin() {
        Vector3f v = this.entityData.get(SLASH_ORIGIN);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Vec3 slashDir() {
        Vector3f v = this.entityData.get(SLASH_DIR);
        Vec3 d = new Vec3(v.x(), v.y(), v.z());
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
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

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = owner(sl);
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        int ph = phase();
        int age = tickCount - phaseTick();

        switch (ph) {
            case PH_RANKING -> {
                if (++idle > IDLE_TICKS) {
                    level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 1.0f);
                    setPhase(PH_END);
                }
            }
            case PH_COUNTDOWN -> {
                int left = COUNTDOWN_TICKS - age;
                if (left > 0 && left <= 60 && left % 20 == 0) {
                    level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 2.5f, 1.0f + (60 - left) / 60f);
                }
                if (age >= COUNTDOWN_TICKS) {
                    finale(owner);
                }
            }
            case PH_HELL -> hellTick(owner, age);
            case PH_SLASH -> slashTick(owner, age);
            default -> {
                if (age >= END_TICKS) {
                    discard();
                }
            }
        }
    }

    private void finale(LivingEntity owner) {
        int r = rank();
        if (r >= RANK_SSS) {
            Vec3 look = owner.getLookAngle();
            Vec3 o = owner.position().add(0, owner.getBbHeight() * 0.65, 0);
            this.entityData.set(SLASH_ORIGIN, new Vector3f((float) o.x, (float) o.y, (float) o.z));
            this.entityData.set(SLASH_DIR, new Vector3f((float) look.x, (float) look.y, (float) look.z));
            slashThenHell = StylishRankSpell.SSS_ALSO_BULLET_HELL;
            setPhase(PH_SLASH);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.5f);
            if (level() instanceof ServerLevel fsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.beam(fsl, o, o.add(look.scale(StylishRankSpell.SLASH_LENGTH)), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.0f, SLASH_WINDUP);   // telegraph: slash line
            }
        } else {
            startHell(r >= RANK_SS ? (float) StylishRankSpell.HELL_RADIUS_SS : (float) StylishRankSpell.HELL_RADIUS_S);
        }
    }

    private void startHell(float radius) {
        this.entityData.set(HELL_RADIUS, radius);
        setPhase(PH_HELL);
        level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.7f);
        level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.6f);
        level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5f, 1.6f);
        if (level() instanceof ServerLevel hsl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(hsl, position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, radius / 3.0f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(hsl, position().add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3.0f);
        }
    }

    private void hellTick(LivingEntity owner, int age) {
        float radius = hellRadius();
        boolean strong = radius > StylishRankSpell.HELL_RADIUS_S + 0.5;
        if (age % 10 == 0) {
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6f, 1.2f + 0.2f * (age % 20 == 0 ? 1 : 0));
        }
        if (age > 0 && age % StylishRankSpell.HELL_HIT_INTERVAL == 0 && age <= HELL_TICKS) {
            double mult = strong ? StylishRankSpell.SS_DPS_MULT : 1.0;
            double perHit = StylishRankSpell.HELL_HIT_INTERVAL / 20.0 * mult;
            double flat = StylishRankSpell.HELL_DPS_FLAT * perHit;
            double pct = StylishRankSpell.HELL_DPS_PERCENT * perHit;
            Vec3 c = owner.position();
            AABB box = new AABB(c.x - radius, c.y - StylishRankSpell.HELL_DEPTH, c.z - radius, c.x + radius, c.y + StylishRankSpell.HELL_HEIGHT, c.z + radius);
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                if (dx * dx + dz * dz <= radius * radius && isEnemy(t, owner)) {
                    hit(t, owner, flat, pct);
                }
            }
        }
        if (age >= HELL_TICKS) {
            setPhase(PH_END);
        }
    }

    private void slashTick(LivingEntity owner, int age) {
        if (age < SLASH_WINDUP) {
            // the caster gathers power: hold him in place
            Vec3 dm = owner.getDeltaMovement();
            owner.setDeltaMovement(0, Math.min(0, dm.y), 0);
            owner.hurtMarked = true;
            return;
        }
        int rel = age - SLASH_WINDUP;
        Vec3 o = slashOrigin();
        Vec3 d = slashDir();
        if (rel == 0) {
            level().playSound(null, o.x, o.y, o.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0f, 0.5f);
            level().playSound(null, o.x, o.y, o.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 0.8f);
            level().playSound(null, o.x, o.y, o.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.6f);
            level().playSound(null, o.x, o.y, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 4.0f, 0.4f);
            if (level() instanceof ServerLevel ssl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(ssl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 5.0f);   // Hellfire Slash released
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(ssl, owner.position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2.5f);
                com.example.jjkspells.vfx.sprite.SpriteFx.beam(ssl, o, o.add(d.scale(StylishRankSpell.SLASH_LENGTH)), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 10.0f, 10);
                com.example.jjkspells.vfx.sprite.SpriteFx.slash(ssl, o.add(d.scale(6.0)), Math.abs(d.y) > 0.99 ? new Vec3(0, 0, 1) : new Vec3(-d.z, 0, d.x), 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 8.0f);
            }
        }
        if (rel == 0 || rel == 12) {
            double f = rel == 0 ? 1.0 : 0.5;
            Vec3 end = o.add(d.scale(StylishRankSpell.SLASH_LENGTH));
            double r = StylishRankSpell.SLASH_RADIUS;
            AABB box = new AABB(o, end).inflate(r + 1);
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (!isEnemy(t, owner)) {
                    continue;
                }
                Vec3 p = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ());
                double along = Math.max(0, Math.min(StylishRankSpell.SLASH_LENGTH, p.subtract(o).dot(d)));
                if (p.distanceTo(o.add(d.scale(along))) <= r + t.getBbWidth() * 0.5) {
                    hit(t, owner, StylishRankSpell.SLASH_FLAT * f, StylishRankSpell.SLASH_PERCENT * f);
                    t.setSecondsOnFire(6);
                    if (level() instanceof ServerLevel esl) {
                        com.example.jjkspells.vfx.sprite.SpriteFx.impact(esl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2.5f);
                    }
                    if (rel == 0) {
                        t.setDeltaMovement(t.getDeltaMovement().add(d.x * 1.4, 0.45 + d.y * 0.6, d.z * 1.4));
                        t.hurtMarked = true;
                    }
                }
            }
        }
        if (rel == 12 && level() instanceof ServerLevel psl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(psl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3.5f);   // second pulse
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(psl, o.add(d.scale(6.0)), Math.abs(d.y) > 0.99 ? new Vec3(0, 0, 1) : new Vec3(-d.z, 0, d.x), 90f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 6.0f);
        }
        if (rel % 8 == 0 && rel > 0 && rel < SLASH_TICKS) {
            level().playSound(null, o.x, o.y, o.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3.0f, 0.5f);
        }
        if (rel >= SLASH_TICKS) {
            if (slashThenHell) {
                startHell((float) StylishRankSpell.HELL_RADIUS_SS);
            } else {
                setPhase(PH_END);
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        Entity own = owner();
        if (own == null) {
            return;
        }
        int ph = phase();
        int age = tickCount - phaseTick();
        if (ph == PH_HELL) {
            float radius = hellRadius();
            int n = radius > 10 ? 10 : 5;
            for (int i = 0; i < n; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(random.nextDouble()) * radius;
                level().addParticle(ParticleTypes.FLAME, own.getX() + Math.cos(ang) * rr, own.getY() + 0.1, own.getZ() + Math.sin(ang) * rr, 0, 0.04 + random.nextDouble() * 0.05, 0);
            }
            if (age % 5 == 0) {
                double r2 = radius;
                AABB box = new AABB(own.getX() - r2, own.getY() - StylishRankSpell.HELL_DEPTH, own.getZ() - r2, own.getX() + r2, own.getY() + StylishRankSpell.HELL_HEIGHT, own.getZ() + r2);
                List<LivingEntity> in = new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class, box));
                for (LivingEntity t : in) {
                    if (t == own) {
                        continue;
                    }
                    double dx = t.getX() - own.getX();
                    double dz = t.getZ() - own.getZ();
                    if (dx * dx + dz * dz <= r2 * r2) {
                        for (int i = 0; i < 4; i++) {
                            level().addParticle(ParticleTypes.LAVA, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 0, 0, 0);
                        }
                    }
                }
            }
        }
        if (ph == PH_SLASH) {
            Vec3 o = slashOrigin();
            Vec3 d = slashDir();
            if (age < SLASH_WINDUP) {
                for (int i = 0; i < 3; i++) {
                    double ang = random.nextDouble() * Math.PI * 2;
                    double rr = 3 + random.nextDouble() * 4;
                    level().addParticle(ParticleTypes.FLAME, own.getX() + Math.cos(ang) * rr, own.getY() + 0.3 + random.nextDouble() * 2.5, own.getZ() + Math.sin(ang) * rr,
                            -Math.cos(ang) * 0.5, 0.05, -Math.sin(ang) * 0.5);
                }
            } else if (age - SLASH_WINDUP < SLASH_TICKS - 8) {
                for (int i = 0; i < 12; i++) {
                    double along = random.nextDouble() * StylishRankSpell.SLASH_LENGTH * 0.8;
                    Vec3 c = o.add(d.scale(along)).add((random.nextDouble() - 0.5) * 12, (random.nextDouble() - 0.5) * 12, (random.nextDouble() - 0.5) * 12);
                    level().addParticle(ParticleTypes.FLAME, c.x, c.y, c.z, d.x * 0.8, d.y * 0.8, d.z * 0.8);
                }
            }
        }
    }
}
