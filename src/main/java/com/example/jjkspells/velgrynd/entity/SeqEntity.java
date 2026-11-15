package com.example.jjkspells.velgrynd.entity;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.registry.ModParticles;
import com.example.jjkspells.velgrynd.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A timed spell sequence centred on its own position. The server runs {@link #run} once per tick with the age in ticks; the client
 * draws everything from the age plus the synched radii and yaw, so nothing else has to be sent.
 */
public abstract class SeqEntity extends VgrEntityBase {
    private static final EntityDataAccessor<Float> DATA_R1 = SynchedEntityData.defineId(SeqEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_R2 = SynchedEntityData.defineId(SeqEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(SeqEntity.class, EntityDataSerializers.FLOAT);

    protected SeqEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static int tk(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    /** Length of the whole sequence in ticks. */
    public abstract int total();

    /** One server tick of the sequence. */
    protected abstract void run(int age, LivingEntity owner);

    /** Hook for subclasses when the sequence ends or is cut short. */
    protected void onFinish(boolean normal) {
    }

    protected void begin(LivingEntity caster, Vec3 p, double r1, double r2, float yaw) {
        setOwner(caster);
        setPos(p.x, p.y, p.z);
        entityData.set(DATA_R1, (float) r1);
        entityData.set(DATA_R2, (float) r2);
        entityData.set(DATA_YAW, yaw);
    }

    public float r1() {
        return entityData.get(DATA_R1);
    }

    public float r2() {
        return entityData.get(DATA_R2);
    }

    protected void setR1(double r) {
        if (Math.abs(entityData.get(DATA_R1) - r) > 0.2) {
            entityData.set(DATA_R1, (float) r);
        }
    }

    /** The caster stands firm: damage reduction, no knockback, no crowd control, no Slowness. */
    protected void guard(LivingEntity o, double reductionPercent) {
        Util.reduce(o, reductionPercent / 100.0, 6);
        Util.kbImmune(o, 6);
        Util.ccImmune(o, 6);
        o.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
    }

    protected static double water(LivingEntity e, double bonusPercent) {
        return e.isInWater() ? 1.0 + bonusPercent / 100.0 : 1.0;
    }

    protected void setR2(double r) {
        entityData.set(DATA_R2, (float) r);
    }

    public float yaw() {
        return entityData.get(DATA_YAW);
    }

    /** The aimed point put on the ground (falls back to the raw point). */
    public static Vec3 groundPoint(Level level, LivingEntity caster, double range) {
        Vec3 p = Util.aimPoint(level, caster, range);
        Double g = Util.findGround(level, p.x, p.y + 1.0, p.z);
        return g == null ? p : new Vec3(p.x, g, p.z);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_R1, 20f);
        entityData.define(DATA_R2, 40f);
        entityData.define(DATA_YAW, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.velgrynd.client.ClientFx.tickSeq(this));
            return;
        }
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        try {
            run(age, owner);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("jjk_spells").error("velgrynd sequence failed", t);
            finish(false);
            discard();
            return;
        }
        if (age >= total()) {
            finish(true);
            discard();
        }
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        releaseCommon(normal);
        onFinish(normal);
    }

    // ------------------------------------------------------------------ helpers

    protected static boolean at(int age, double sec) {
        return age == tk(sec);
    }

    protected static boolean in(int age, double a, double b) {
        return age >= tk(a) && age < tk(b);
    }

    protected List<LivingEntity> foes(LivingEntity owner, double r) {
        return Util.enemies(level(), position(), r, owner);
    }

    protected static double boss(LivingEntity e, double bossBonusPercent) {
        return Util.isBoss(e) ? 1.0 + bossBonusPercent / 100.0 : 1.0;
    }

    protected static double below(LivingEntity e, double thresholdPercent, double bonusPercent) {
        return e.getHealth() <= e.getMaxHealth() * thresholdPercent / 100.0 ? 1.0 + bonusPercent / 100.0 : 1.0;
    }

    /** Ordinary hit with the common ignore values (percent). Returns true if the target died. */
    protected boolean strike(LivingEntity owner, LivingEntity e, double flat, double pct, double mult, double armorIgnorePercent, double reduceIgnorePercent) {
        return Util.hurt(owner, e, flat, pct, mult, 1.0, armorIgnorePercent / 100.0, reduceIgnorePercent / 100.0);
    }

    protected static double rnd(java.util.Random r, double a, double b) {
        return a + (b - a) * r.nextDouble();
    }

    protected Vec3 spot(java.util.Random r, double radius) {
        double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
        Vec3 p = position();
        double x = p.x + Math.cos(a) * d, z = p.z + Math.sin(a) * d;
        Double g = Util.findGround(level(), x, p.y + 6, z);
        return new Vec3(x, g == null ? p.y : g, z);
    }

    /** Enemies this sequence has defeated (for restoration and the ultimate's bonus). */
    protected final Set<UUID> dead = new HashSet<>();

    protected void track(LivingEntity e) {
        if (!e.isAlive()) {
            dead.add(e.getUUID());
        }
    }

    /** Absolute Predation / Soul Devouring: the weak are consumed. */
    protected void devourWeak(LivingEntity owner, LivingEntity e, double thresholdPercent) {
        if (e.isAlive() && Util.consume(owner, e, thresholdPercent, 0)) {
            Util.fx(level(), ModParticles.SOUL.get(), e.getX(), e.getY() + 1, e.getZ(), 14, 0.3, 0.6, 0.3, 0.1);
        }
        track(e);
    }

    /** Heal and mana from the defeated: perKill percent each, never more than maxPercent in all. */
    protected void restoreFromKills(LivingEntity owner, double healPerKill, double manaPerKill, double maxPercent) {
        double n = dead.size();
        if (n > 0) {
            Util.restore(owner, Math.min(maxPercent, n * healPerKill) / 100.0, Math.min(maxPercent, n * manaPerKill) / 100.0);
        }
    }

    protected double density() {
        return VgrConfig.G.particleDensity.get();
    }
}
