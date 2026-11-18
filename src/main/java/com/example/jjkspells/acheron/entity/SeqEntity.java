package com.example.jjkspells.acheron.entity;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.registry.ModParticles;
import com.example.jjkspells.acheron.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.vfx.sprite.SpriteFx;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A timed spell sequence centred on its own position. The server runs {@link #run} once per tick with the age in ticks; the client
 * draws everything from the age plus the synched radii and yaw, so nothing else has to be sent.
 */
public abstract class SeqEntity extends AchEntityBase {
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

    protected LivingEntity find(UUID id) {
        if (id != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    protected void setYaw(float y) {
        entityData.set(DATA_YAW, y);
    }

    public float yaw() {
        return entityData.get(DATA_YAW);
    }

    /** The aimed point put on the ground; in open air (looking up, over a drop) it falls back to the ground in front of the caster. */
    public static Vec3 groundPoint(Level level, LivingEntity caster, double range) {
        Vec3 p = Util.aimPoint(level, caster, range);
        Double g = Util.findGround(level, p.x, p.y + 1.0, p.z);
        if (g == null) {
            g = Util.findGround(level, p.x, caster.getY() + 2.0, p.z);
        }
        return g == null ? new Vec3(p.x, Math.min(p.y, caster.getY()), p.z) : new Vec3(p.x, g, p.z);
    }

    /**
     * Aim assist for the spells that are placed on the ground: the point you look at, moved to the middle of the biggest group of
     * enemies that stand NEAR that point (within the radius). Enemies elsewhere never pull it away from where you aim; with no
     * enemy near the aim the plain aimed point is used.
     */
    public static Vec3 aimCenter(Level level, LivingEntity caster, double range, double radius) {
        Vec3 aim = groundPoint(level, caster, range);
        double reach = Math.max(4.0, radius);
        List<LivingEntity> all = new java.util.ArrayList<>();
        for (LivingEntity e : Util.enemies(level, aim, reach + 2.0, caster)) {
            double dx = e.getX() - aim.x, dz = e.getZ() - aim.z;
            if (Math.sqrt(dx * dx + dz * dz) <= reach && e.distanceTo(caster) <= range + radius) {
                all.add(e);
            }
        }
        if (all.isEmpty()) {
            return aim;
        }
        LivingEntity best = null;
        double bs = -1e9;
        for (LivingEntity c : all) {
            int n = 0;
            for (LivingEntity o : all) {
                if (o.distanceToSqr(c) <= radius * radius * 0.5) {
                    n++;
                }
            }
            double score = n - c.position().distanceTo(aim) * 0.05;
            if (score > bs) {
                bs = score;
                best = c;
            }
        }
        double x = 0, z = 0;
        int n = 0;
        for (LivingEntity o : all) {
            if (o.distanceToSqr(best) <= radius * radius * 0.5) {
                x += o.getX();
                z += o.getZ();
                n++;
            }
        }
        x /= n;
        z /= n;
        // never drift further than the radius from the aim point
        double dx = x - aim.x, dz = z - aim.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > reach) {
            x = aim.x + dx / d * reach;
            z = aim.z + dz / d * reach;
        }
        Double g = Util.findGround(level, x, best.getY() + 2, z);
        return new Vec3(x, g == null ? best.getY() : g, z);
    }

    // ------------------------------------------------------------------ Slashed Dream

    /** Slashed Dream this spell gives (set when it is created); it is paid out at the FIRST hit, not at a fixed moment, so nothing can skip it. */
    protected int dreamAmt;
    private boolean dreamGiven;

    protected void tryDream(LivingEntity o) {
        if (dreamGiven || dreamAmt <= 0 || o == null || !o.isAlive()) {
            return;
        }
        if (landed > 0 || !AcheronConfig.G.dreamNeedsHit.get()) {
            dreamGiven = true;
            com.example.jjkspells.acheron.util.Nihility.addDreams(o, dreamAmt);
        }
    }

    /** Older call sites: declares the amount and tries at once. */
    protected void award(LivingEntity o, int n) {
        dreamAmt = n;
        tryDream(o);
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
                    () -> () -> com.example.jjkspells.acheron.client.ClientFx.tickSeq(this));
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
            org.slf4j.LoggerFactory.getLogger("jjk_spells").error("acheron sequence failed", t);
            finish(false);
            discard();
            return;
        }
        tryDream(owner);
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
        tryDream(ownerAny());
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

    /** Enemies this sequence has hit (any spell calls track after a hit). */
    protected int landed;

    protected void track(LivingEntity e) {
        landed++;
        if (!e.isAlive()) {
            dead.add(e.getUUID());
        }
    }

    /** Absolute Predation / Soul Devouring: the weak are consumed. */
    protected void devourWeak(LivingEntity owner, LivingEntity e, double thresholdPercent) {
        if (e.isAlive() && Util.consume(owner, e, thresholdPercent, 0)) {
            Util.fx(level(), ModParticles.MIST.get(), e.getX(), e.getY() + 1, e.getZ(), 14, 0.3, 0.6, 0.3, 0.1);
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
        return AcheronConfig.G.particleDensity.get();
    }

    /** Server level for sprite effects (the sequence only ever runs on the server). */
    protected net.minecraft.server.level.ServerLevel sv() {
        return (net.minecraft.server.level.ServerLevel) level();
    }

    /** Horizontal unit vector from p towards the caster: the plane normal that makes a flat cut face the player who cast it. */
    protected static Vec3 faceCaster(LivingEntity o, Vec3 p) {
        Vec3 d = new Vec3(o.getX() - p.x, 0, o.getZ() - p.z);
        return d.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : d.normalize();
    }
}
