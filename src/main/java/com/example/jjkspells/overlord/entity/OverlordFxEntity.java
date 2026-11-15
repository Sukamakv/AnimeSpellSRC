package com.example.jjkspells.overlord.entity;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.registry.ModEffects;
import com.example.jjkspells.overlord.registry.ModEntities;
import com.example.jjkspells.overlord.registry.ModParticles;
import com.example.jjkspells.overlord.registry.ModSounds;
import com.example.jjkspells.overlord.registry.ModSpells;
import com.example.jjkspells.overlord.util.Magic;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invisible controller entity. One entity type drives the timeline, the gameplay effects and (through its client
 * renderer / ClientFx) the visuals of seven of the eight spells; {@link #getKind()} says which one it is.
 */
public class OverlordFxEntity extends Entity {
    public static final int SUMMON = 0, BANSHEE = 1, HOLE = 2, GOAL = 3, FALLEN = 4, SLASH = 5, GOAT = 6, GRASP = 7;

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LOCK = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.FLOAT);
    /** Kind-specific tick marker (charge end / collapse tick / slash windup). */
    private static final EntityDataAccessor<Integer> DATA_MARK = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_LENGTH = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DATA_DIR = SynchedEntityData.defineId(OverlordFxEntity.class, EntityDataSerializers.VECTOR3);

    // server-only
    private int spellLevel = 1;
    private int lifeTicks = 0;
    private Vec3 anchor = Vec3.ZERO;
    private final Set<Integer> done = new HashSet<>();

    public OverlordFxEntity(EntityType<? extends OverlordFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static OverlordFxEntity create(Level level, int kind, LivingEntity owner, Vec3 pos, int duration, float radius, int spellLevel) {
        OverlordFxEntity e = new OverlordFxEntity(ModEntities.FX.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(DATA_KIND, kind);
        e.entityData.set(DATA_OWNER, owner.getId());
        e.entityData.set(DATA_DURATION, duration);
        e.entityData.set(DATA_RADIUS, radius);
        e.spellLevel = spellLevel;
        e.anchor = owner.position();
        return e;
    }

    public OverlordFxEntity mark(int m) {
        entityData.set(DATA_MARK, m);
        return this;
    }

    public OverlordFxEntity lock(int ticks) {
        entityData.set(DATA_LOCK, ticks);
        return this;
    }

    public OverlordFxEntity target(Entity t) {
        entityData.set(DATA_TARGET, t.getId());
        return this;
    }

    public OverlordFxEntity direction(Vec3 dir, float length) {
        entityData.set(DATA_DIR, new Vector3f((float) dir.x, (float) dir.y, (float) dir.z));
        entityData.set(DATA_LENGTH, length);
        return this;
    }

    public OverlordFxEntity life(int ticks) {
        this.lifeTicks = ticks;
        return this;
    }

    // ------------------------------------------------------------------ synced getters (used by client too)

    public int getKind() {
        return entityData.get(DATA_KIND);
    }

    public int getDuration() {
        return entityData.get(DATA_DURATION);
    }

    public int getLock() {
        return entityData.get(DATA_LOCK);
    }

    public int getMark() {
        return entityData.get(DATA_MARK);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    public float getLength() {
        return entityData.get(DATA_LENGTH);
    }

    public Vec3 getDir() {
        Vector3f v = entityData.get(DATA_DIR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Entity getOwnerEntity() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    public Entity getTargetEntity() {
        return level().getEntity(entityData.get(DATA_TARGET));
    }

    /** True while the caster is held in place by this ritual. */
    public boolean isLocking() {
        return getLock() > 0 && tickCount < getLock();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_KIND, 0);
        entityData.define(DATA_OWNER, -1);
        entityData.define(DATA_TARGET, -1);
        entityData.define(DATA_DURATION, 40);
        entityData.define(DATA_LOCK, 0);
        entityData.define(DATA_RADIUS, 5f);
        entityData.define(DATA_MARK, 0);
        entityData.define(DATA_LENGTH, 0f);
        entityData.define(DATA_DIR, new Vector3f(0, 0, 1));
    }

    // ------------------------------------------------------------------ tick

    private AbstractSpell spell() {
        return switch (getKind()) {
            case SUMMON -> ModSpells.SUMMON_UNDEAD.get();
            case BANSHEE -> ModSpells.CRY_OF_THE_BANSHEE.get();
            case HOLE -> ModSpells.BLACK_HOLE.get();
            case GOAL -> ModSpells.GOAL_OF_ALL_LIFE_IS_DEATH.get();
            case FALLEN -> ModSpells.FALLEN_DOWN.get();
            case SLASH -> ModSpells.REALITY_SLASH.get();
            case GOAT -> ModSpells.BLACK_GOAT_SACRIFICE.get();
            default -> ModSpells.GRASP_HEART.get();
        };
    }

    private net.minecraft.world.damagesource.DamageSource src(LivingEntity owner) {
        return spell().getDamageSource(this, owner);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.overlord.client.ClientFx.tick(this));
            return;
        }
        int age = tickCount;
        Entity ownerE = getOwnerEntity();
        if (!(ownerE instanceof LivingEntity owner) || !owner.isAlive()) {
            discard();
            return;
        }
        if (age >= getDuration()) {
            discard();
            return;
        }
        // keep the caster still while the ritual locks them
        if (getLock() > 0) {
            if (age < getLock()) {
                Magic.LOCKED.add(owner.getUUID());
                Vec3 p = owner.position();
                if (p.distanceToSqr(anchor) > 0.04) {
                    Magic.snapTo(owner, anchor);
                }
            } else {
                Magic.LOCKED.remove(owner.getUUID());
            }
        }
        switch (getKind()) {
            case SUMMON -> tickSummon(owner, age);
            case BANSHEE -> tickBanshee(owner, age);
            case HOLE -> tickHole(owner, age);
            case GOAL -> tickGoal(owner, age);
            case FALLEN -> tickFallen(owner, age);
            case SLASH -> tickSlash(owner, age);
            case GOAT -> tickGoat(owner, age);
            default -> tickGrasp(owner, age);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && getLock() > 0 && getOwnerEntity() != null) {
            Magic.LOCKED.remove(getOwnerEntity().getUUID());
        }
        super.remove(reason);
    }

    private void sound(SoundEvent s, float vol, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    private void burst(ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double speed) {
        Magic.particles(level(), p, x, y, z, n, dx, dy, dz, speed);
    }

    private List<LivingEntity> enemiesIn(LivingEntity owner, Vec3 c, double r, double up) {
        AABB box = new AABB(c.x - r, c.y - 3, c.z - r, c.x + r, c.y + up, c.z + r);
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> Magic.isEnemy(owner, e));
    }

    // ------------------------------------------------------------------ 1: Summon Undead

    private void tickSummon(LivingEntity owner, int age) {
        if (age == 1) {
            sound(ModSounds.BONE_RISE.get(), 2.5f, 1.0f);
        }
        if (age < 20 && age % 2 == 0) {
            double r = getRadius();
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * r;
                burst(ModParticles.BONE.get(), getX() + Math.cos(a) * d, getY() + 0.1, getZ() + Math.sin(a) * d, 1, 0.1, 0.4, 0.1, 0.12);
            }
        }
        if (age == 18) {
            spawnMinions(owner);
        }
    }

    private void spawnMinions(LivingEntity owner) {
        var c = OverlordConfig.UNDEAD;
        int lo = Math.min(c.minMinions.get(), c.maxMinions.get()), hi = Math.max(c.minMinions.get(), c.maxMinions.get());
        int n = lo + random.nextInt(hi - lo + 1);
        LivingEntity command = Magic.lookedAtLiving(level(), owner, c.targetRange.get());
        float hpScale = Magic.scale(spellLevel);
        double r = getRadius();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + random.nextDouble() * 0.4;
            double d = r * (0.55 + random.nextDouble() * 0.45);
            double x = getX() + Math.cos(a) * d, z = getZ() + Math.sin(a) * d;
            double y = Magic.groundY(level(), x, getY(), z);
            try {
                UndeadSoldierEntity m = new UndeadSoldierEntity(ModEntities.UNDEAD_SOLDIER.get(), level());
                m.moveTo(x, y, z, (float) (Math.toDegrees(a) + 90), 0);
                m.configure(owner, hpScale, spellLevel);
                level().addFreshEntity(m);
                if (command != null && Magic.isEnemy(owner, command)) {
                    m.setTarget(command);
                }
            } catch (RuntimeException ex) {
                com.mojang.logging.LogUtils.getLogger().error("Summon Undead: could not spawn a soldier", ex);
            }
            burst(ModParticles.VOID.get(), x, y + 1, z, 14, 0.3, 0.8, 0.3, 0.04);
            burst(ModParticles.BONE.get(), x, y + 0.3, z, 8, 0.3, 0.2, 0.3, 0.1);
        }
    }

    // ------------------------------------------------------------------ 2: Cry of the Banshee

    private void tickBanshee(LivingEntity owner, int age) {
        var c = OverlordConfig.BANSHEE;
        if (age == 0) {
            sound(ModSounds.BANSHEE_CRY.get(), 3.0f, 1.0f);
        }
        if (age < 12 && age % 3 == 0) {
            burst(ModParticles.SOUL.get(), getX(), getY() + 1.5, getZ(), 6, 0.6, 0.6, 0.6, 0.05);
        }
        final int start = 10, travel = 14;
        if (age >= start) {
            double waveR = c.radius.get() * Math.min(1.0, (age - start + 1) / (double) travel);
            Vec3 center = position();
            for (LivingEntity e : enemiesIn(owner, center, c.radius.get(), 6)) {
                if (done.contains(e.getId())) {
                    continue;
                }
                double dx = e.getX() - getX(), dz = e.getZ() - getZ();
                if (Math.sqrt(dx * dx + dz * dz) <= waveR + e.getBbWidth() * 0.5) {
                    done.add(e.getId());
                    hitBanshee(owner, e);
                }
            }
        }
    }

    private void hitBanshee(LivingEntity owner, LivingEntity e) {
        var c = OverlordConfig.BANSHEE;
        e.invulnerableTime = 0;
        DamageSources.applyDamage(e, (float) (c.damage.get() * Magic.scale(spellLevel)), src(owner));
        if (!e.isAlive()) {
            return;
        }
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (c.weaknessSeconds.get() * 20), c.weaknessAmplifier.get()));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (c.slownessSeconds.get() * 20), c.slownessAmplifier.get()));
        e.addEffect(new MobEffectInstance(ModEffects.FEAR.get(), (int) (c.fearSeconds.get() * 20), 0));
        burst(ModParticles.SOUL.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.4, 0.3, 0.08);
    }

    // ------------------------------------------------------------------ 3: Black Hole

    private void tickHole(LivingEntity owner, int age) {
        var c = OverlordConfig.BLACK_HOLE;
        int collapse = getMark();
        int formed = 8;
        Vec3 center = position();
        if (age == 1) {
            sound(ModSounds.HOLE_CHARGE.get(), 3.0f, 1.0f);
        }
        if (age >= formed && age < collapse) {
            double R = c.pullRadius.get();
            double base = c.pullStrength.get();
            boolean warning = age >= collapse - Math.round(c.warningSeconds.get() * 20);
            for (LivingEntity e : enemiesIn(owner, center, R, R)) {
                Vec3 to = center.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
                double d = to.length();
                if (d > R || d < 0.4) {
                    continue;
                }
                double s = base * (0.4 + 0.6 * (1.0 - d / R)) * (warning ? 0.55 : 1.0);
                Vec3 m = e.getDeltaMovement().scale(0.6).add(to.normalize().scale(s));
                double len = m.length();
                if (len > 0.8) {
                    m = m.scale(0.8 / len);
                }
                e.setDeltaMovement(m);
                e.hurtMarked = true;
                e.hasImpulse = true;
                e.fallDistance = 0;
            }
        }
        if (age == collapse) {
            sound(ModSounds.HOLE_COLLAPSE.get(), 4.0f, 1.0f);
            float dmg = (float) (c.damage.get() * Magic.scale(spellLevel));
            double dr = c.damageRadius.get();
            for (LivingEntity e : enemiesIn(owner, center, c.pullRadius.get(), c.pullRadius.get())) {
                double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(center);
                if (d <= dr + e.getBbWidth() * 0.5) {
                    e.invulnerableTime = 0;
                    DamageSources.applyDamage(e, dmg, src(owner));
                }
                if (e.isAlive()) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (c.slownessSeconds.get() * 20), c.slownessAmplifier.get()));
                }
            }
            burst(ModParticles.VOID.get(), center.x, center.y, center.z, 80, 0.4, 0.4, 0.4, 0.5);
            burst(ModParticles.SMOKE.get(), center.x, center.y, center.z, 30, 0.5, 0.5, 0.5, 0.25);
        }
    }

    // ------------------------------------------------------------------ 4: The Goal of All Life Is Death

    private void tickGoal(LivingEntity owner, int age) {
        var c = OverlordConfig.GOAL;
        int charge = getMark();
        if (age == 1) {
            sound(ModSounds.DEATH_HUM.get(), 3.0f, 1.0f);
        }
        if (age < charge && age > 0 && age % 20 == 0) {
            sound(ModSounds.DEATH_PULSE.get(), 3.0f, 0.7f + 0.5f * age / charge);
        }
        if (age < charge && age % 4 == 0) {
            double r = c.radius.get() * Math.min(1.0, age / 40.0);
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * r;
            burst(ModParticles.SOUL.get(), getX() + Math.cos(a) * d, getY() + 0.2, getZ() + Math.sin(a) * d, 1, 0.1, 0.3, 0.1, 0.06);
        }
        if (age == charge) {
            sound(ModSounds.DEATH_WAVE.get(), 4.0f, 1.0f);
            int asc = (int) (c.ascendanceSeconds.get() * 20);
            if (asc > 0) {
                owner.addEffect(new MobEffectInstance(ModEffects.ASCENDANCE.get(), asc, 0));
            }
        }
        if (age >= charge) {
            double waveR = c.radius.get() * Math.min(1.0, (age - charge + 1) / 20.0);
            for (LivingEntity e : enemiesIn(owner, position(), c.radius.get(), 10)) {
                if (done.contains(e.getId())) {
                    continue;
                }
                double dx = e.getX() - getX(), dz = e.getZ() - getZ();
                if (Math.sqrt(dx * dx + dz * dz) <= waveR + e.getBbWidth() * 0.5) {
                    done.add(e.getId());
                    hitGoal(owner, e);
                }
            }
        }
    }

    private void hitGoal(LivingEntity owner, LivingEntity e) {
        var c = OverlordConfig.GOAL;
        burst(ModParticles.SMOKE.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 20, 0.4, 0.6, 0.4, 0.04);
        burst(ModParticles.SOUL.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 14, 0.3, 0.6, 0.3, 0.1);
        float frac = e.getHealth() / e.getMaxHealth();
        if (frac <= c.instantDeathHealthThreshold.get() && !Magic.isBoss(e) && random.nextDouble() < c.instantDeathChance.get()) {
            Magic.trueDamage(e, e.getHealth() + e.getMaxHealth() + 1000f, src(owner));
            burst(ModParticles.SOUL.get(), e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
            return;
        }
        e.addEffect(new MobEffectInstance(ModEffects.DEATH_CURSE.get(), (int) (c.curseSeconds.get() * 20), 0));
        // register the attack so the caster is credited / target retaliates; the max-health loss itself is the curse
        e.setLastHurtByMob(owner);
        float max = e.getMaxHealth();
        if (e.getHealth() > max) {
            e.setHealth(max);
        }
    }

    // ------------------------------------------------------------------ 5: Fallen Down

    private void tickFallen(LivingEntity owner, int age) {
        var c = OverlordConfig.FALLEN;
        int charge = getMark();
        int impact = charge + 10;
        if (age == 1) {
            sound(ModSounds.HOLY_CHARGE.get(), 3.5f, 1.0f);
        }
        if (age < impact && age % 5 == 0) {
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * c.radius.get();
            burst(ModParticles.GOLD.get(), getX() + Math.cos(a) * d, getY() + 14, getZ() + Math.sin(a) * d, 2, 0.2, 0.2, 0.2, 0.02);
        }
        if (age == impact) {
            sound(ModSounds.HOLY_BLAST.get(), 5.0f, 1.0f);
            float fraction = (float) (c.damageFractionOfMaxHealth.get() * Magic.scale(spellLevel));
            for (LivingEntity e : enemiesIn(owner, position(), c.radius.get(), 20)) {
                double dx = e.getX() - getX(), dz = e.getZ() - getZ();
                if (Math.sqrt(dx * dx + dz * dz) <= c.radius.get() + e.getBbWidth() * 0.5) {
                    Magic.trueDamage(e, e.getMaxHealth() * fraction, src(owner));
                    if (e.isAlive()) {
                        Vec3 away = new Vec3(dx, 0, dz).normalize().scale(0.8);
                        e.setDeltaMovement(e.getDeltaMovement().add(away.x, 0.45, away.z));
                        e.hurtMarked = true;
                    }
                }
            }
            burst(ModParticles.GOLD.get(), getX(), getY() + 0.5, getZ(), 160, 2.0, 0.6, 2.0, 0.6);
        }
        if (age > impact && age < impact + 40 && age % 4 == 0) {
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * c.radius.get() * 0.6;
            burst(ModParticles.GOLD.get(), getX() + Math.cos(a) * d, getY() + 0.2, getZ() + Math.sin(a) * d, 2, 0.2, 0.3, 0.2, 0.03);
        }
    }

    // ------------------------------------------------------------------ 6: Reality Slash

    private void tickSlash(LivingEntity owner, int age) {
        int windup = getMark();
        if (age == 0) {
            sound(ModSounds.SLASH_LINE.get(), 1.6f, 1.0f);
        }
        if (age == windup) {
            sound(ModSounds.SLASH_TEAR.get(), 3.0f, 1.0f);
            executeSlash(owner);
        }
    }

    private void executeSlash(LivingEntity owner) {
        var c = OverlordConfig.SLASH;
        Vec3 start = position();
        Vec3 dir = getDir();
        double len = getLength();
        Vec3 end = start.add(dir.scale(len));
        if (!c.ignoreBlocks.get()) {
            var hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                end = hit.getLocation();
            }
        }
        double half = c.width.get() * 0.5;
        AABB box = new AABB(start, end).inflate(c.width.get());
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, x -> Magic.isEnemy(owner, x))) {
            AABB bb = e.getBoundingBox().inflate(half);
            if (bb.contains(start) || bb.clip(start, end).isPresent()) {
                float raw = (float) ((c.baseDamage.get() + c.maxHealthFraction.get() * e.getMaxHealth()) * Magic.scale(spellLevel));
                float dmg = raw * Magic.armorFactor(e, c.armorIgnore.get().floatValue());
                Magic.trueDamage(e, dmg, src(owner));
                burst(ModParticles.VOID.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 22, 0.3, 0.5, 0.3, 0.15);
            }
        }
    }

    // ------------------------------------------------------------------ 7: Black Goat Sacrifice

    private void tickGoat(LivingEntity owner, int age) {
        var c = OverlordConfig.GOAT;
        int charge = getMark();
        if (age < charge && age % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * getRadius();
            burst(ModParticles.SMOKE.get(), getX() + Math.cos(a) * d, getY() + 0.1, getZ() + Math.sin(a) * d, 1, 0.1, 0.3, 0.1, 0.03);
        }
        if (age == Math.max(1, charge - 30)) {
            sound(ModSounds.GOAT_ROAR.get(), 4.5f, 1.0f);
        }
        if (age >= charge - 15 && age < charge + 10 && age % 2 == 0) {
            burst(ModParticles.SMOKE.get(), getX(), getY() + 0.3, getZ(), 16, getRadius() * 0.35, 0.4, getRadius() * 0.35, 0.25);
            burst(ModParticles.SOUL.get(), getX(), getY() + 0.3, getZ(), 6, getRadius() * 0.3, 0.4, getRadius() * 0.3, 0.12);
        }
        if (age == charge) {
            spawnGoats(owner, c);
        }
    }

    private void spawnGoats(LivingEntity owner, OverlordConfig.BlackGoat c) {
        double lo = Math.min(c.minDurationSeconds.get(), c.maxDurationSeconds.get());
        double hi = Math.max(c.minDurationSeconds.get(), c.maxDurationSeconds.get());
        int life = (int) ((lo + random.nextDouble() * (hi - lo)) * 20);
        float scale = Magic.scale(spellLevel);
        double y = Magic.groundY(level(), getX(), getY(), getZ());
        BlackGoatEntity goat = new BlackGoatEntity(ModEntities.BLACK_GOAT.get(), level());
        goat.moveTo(getX(), y, getZ(), owner.getYRot() + 180f, 0);
        goat.configure(owner, scale, life);
        level().addFreshEntity(goat);
        int a = Math.min(c.minOffspring.get(), c.maxOffspring.get()), b = Math.max(c.minOffspring.get(), c.maxOffspring.get());
        int n = a + random.nextInt(b - a + 1);
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / Math.max(1, n) + random.nextDouble() * 0.5;
            double d = 3.2 + random.nextDouble() * 2.0;
            double x = getX() + Math.cos(ang) * d, z = getZ() + Math.sin(ang) * d;
            GoatSpawnEntity g = new GoatSpawnEntity(ModEntities.GOAT_SPAWN.get(), level());
            g.moveTo(x, Magic.groundY(level(), x, getY(), z), z, (float) Math.toDegrees(ang), 0);
            g.configure(owner, scale, life + random.nextInt(60) - 30);
            level().addFreshEntity(g);
        }
        burst(ModParticles.SMOKE.get(), getX(), y + 1, getZ(), 60, 2.0, 1.5, 2.0, 0.2);
    }

    // ------------------------------------------------------------------ 8: Grasp Heart

    private void tickGrasp(LivingEntity owner, int age) {
        Entity te = getTargetEntity();
        if (!(te instanceof LivingEntity target) || !target.isAlive()) {
            if (age > 4) {
                discard();
            }
            return;
        }
        if (age == 1) {
            sound(ModSounds.DARK_CAST.get(), 2.0f, 0.8f);
        }
        if (age % 3 == 0) {
            burst(ModParticles.SOUL.get(), target.getX(), target.getY() + target.getBbHeight() * 0.65, target.getZ(), 3, 0.25, 0.25, 0.25, 0.03);
        }
        if (age == getMark()) {
            resolveGrasp(owner, target);
        }
    }

    private void resolveGrasp(LivingEntity owner, LivingEntity target) {
        var c = OverlordConfig.GRASP;
        boolean boss = Magic.isBoss(target);
        boolean canKill = !boss || Magic.hasAscendance(owner);
        sound(ModSounds.HEART_CRUSH.get(), 3.0f, 1.0f);
        double cx = target.getX(), cy = target.getY() + target.getBbHeight() * 0.65, cz = target.getZ();
        boolean weakEnough = target.getHealth() < target.getMaxHealth() * c.instantDeathHealthThreshold.get();   // only the nearly dead can be killed outright
        if (canKill && weakEnough && random.nextDouble() < Math.min(1.0, c.instantDeathChance.get() * (1.0 + 0.05 * (spellLevel - 1)))) {
            burst(ModParticles.SOUL.get(), cx, cy, cz, 40, 0.3, 0.4, 0.3, 0.25);
            Magic.trueDamage(target, target.getHealth() + target.getMaxHealth() + 1000f, src(owner));
        } else {
            double frac = boss ? c.bossFailDamageFraction.get() : c.failDamageFractionOfMaxHealth.get();
            Magic.trueDamage(target, (float) (target.getMaxHealth() * frac * Magic.scale(spellLevel)), src(owner));
            if (target.isAlive()) {
                int stun = (int) (c.stunSeconds.get() * 20);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 2));
                target.addEffect(new MobEffectInstance(ModEffects.FEAR.get(), Math.min(stun, 20), 0));
            }
            burst(ModParticles.SOUL.get(), cx, cy, cz, 16, 0.3, 0.4, 0.3, 0.1);
        }
    }

    // ------------------------------------------------------------------ plumbing

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256.0 * 256.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
