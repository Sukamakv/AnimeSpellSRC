package com.example.jjkspells.zegion.entity;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.registry.ModEffects;
import com.example.jjkspells.zegion.registry.ModEntities;
import com.example.jjkspells.zegion.registry.ModParticles;
import com.example.jjkspells.zegion.registry.ModSounds;
import com.example.jjkspells.zegion.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mist Lord's Domain - The World That Never Existed. Sits at the centre of the domain; one clock (age = tickCount - 1):
 * <pre>
 *  0..DOME        the silent mist, then the dome grows (caster locked); at DOME the first manifestation hits
 *  DOME..SHIFT    the Thousand Kaisers strike from a ring
 *  SHIFT..GATHER  the uncertain reality: phantoms and mirrors jump about
 *  GATHER..LAUNCH the phantoms gather around the central target
 *  LAUNCH         the mirror execution
 *  FREEZE..HIT    the domain freezes, then collapses into a point and the final hit lands
 *  END            the real world is back
 * </pre>
 */
public class DomainEntity extends ZegEntityBase {
    public static final int SILENT = 20, DOME = 50, SHIFT = 110, GATHER = 170, LAUNCH = 196, FREEZE = 208, HIT = 226, END = 240;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_TX = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TZ = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TY = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STRIKE = SynchedEntityData.defineId(DomainEntity.class, EntityDataSerializers.INT);

    private final Map<UUID, Integer> stacks = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private LivingEntity centre;

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
    }

    public static DomainEntity create(Level level, LivingEntity caster) {
        DomainEntity e = new DomainEntity(ModEntities.DOMAIN.get(), level);
        e.setOwner(caster);
        boolean[] hit = new boolean[1];
        Vec3 c = Util.aimOrSelf(level, caster, ZegConfig.DOMAIN.range.get(), hit);
        e.setPos(c.x, c.y, c.z);
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        return e;
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    /** Offset of the central target from the domain centre (x, y, z), valid after GATHER starts. */
    public Vec3 getTargetOffset() {
        return new Vec3(entityData.get(DATA_TX), entityData.get(DATA_TY), entityData.get(DATA_TZ));
    }

    /** Age of the latest phantom strike volley, or -1. */
    public int getStrikeAge() {
        return entityData.get(DATA_STRIKE);
    }

    public float getRadius() {
        return ZegConfig.DOMAIN.radius.get().floatValue();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_TX, 0f);
        entityData.define(DATA_TZ, 0f);
        entityData.define(DATA_TY, 0f);
        entityData.define(DATA_STRIKE, -1);
    }

    private static double hash(int seed, int i, int k, int salt) {
        long h = seed * 73856093L ^ i * 19349663L ^ k * 83492791L ^ salt * 2654435761L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        h ^= (h >>> 15);
        return ((h & 0xFFFFFFL) / (double) 0x1000000L);
    }

    /** Position of phantom i (of n) relative to the domain centre at a given age. Shared by the server and the renderer. */
    public static double[] phantomPos(int seed, int i, int n, int age, double R, double tx, double tz) {
        double ring = seed * 0.01 + i * Math.PI * 2 / n;
        if (age < SHIFT) {
            double a = ring + (age - DOME) * 0.004 * (i % 2 == 0 ? 1 : -1);
            double r = R * (0.62 + 0.1 * Math.sin(i * 1.7));
            return new double[]{Math.cos(a) * r, Math.sin(a) * r};
        }
        if (age < GATHER) {
            int k = (age - SHIFT) / 20;
            double a = hash(seed, i, k, 1) * Math.PI * 2, r = R * (0.25 + 0.6 * hash(seed, i, k, 2));
            return new double[]{Math.cos(a) * r, Math.sin(a) * r};
        }
        int k = (GATHER - 1 - SHIFT) / 20;
        double a0 = hash(seed, i, k, 1) * Math.PI * 2, r0 = R * (0.25 + 0.6 * hash(seed, i, k, 2));
        double sx = Math.cos(a0) * r0, sz = Math.sin(a0) * r0;
        double gr = 8.0;
        double gx = tx + Math.cos(ring) * gr, gz = tz + Math.sin(ring) * gr;
        double f = Mth.clamp((age - GATHER) / (double) (LAUNCH - GATHER), 0.0, 1.0);
        f = f * f * (3 - 2 * f);
        double x = sx + (gx - sx) * f, z = sz + (gz - sz) * f;
        if (age >= LAUNCH) {
            double l = Mth.clamp((age - LAUNCH) / 6.0, 0.0, 1.0);
            x += (tx - x) * l;
            z += (tz - z) * l;
        }
        return new double[]{x, z};
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickDomain(this));
            return;
        }
        var c = ZegConfig.DOMAIN;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        Vec3 at = position();
        double R = c.radius.get();
        int n = c.phantoms.get();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.MIST.get(), 1.6f, 0.9f);
        }
        if (age < DOME) {
            Vec3 p = owner.position();
            if (age % 2 == 0) {
                Util.mist(level(), p.x, p.y + 0.3, p.z, 1.6 + age * 0.12, 4);
                Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 1.4, p.z, 2, 0.6, 0.8, 0.6, 0.02);
            }
            if (age == SILENT) {
                Util.sound(level(), at.x, at.y, at.z, ModSounds.HUM.get(), 2.0f, 0.6f);
            }
            if (age >= SILENT && age % 3 == 0) {
                double k = (age - SILENT) / (double) (DOME - SILENT);
                double rr = R * k;
                for (int i = 0; i < 8; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    Util.fxDir(level(), ModParticles.MIST.get(), at.x + Math.cos(a) * rr, at.y + 0.3, at.z + Math.sin(a) * rr, Math.cos(a) * 0.05, 0.03, Math.sin(a) * 0.05);
                }
            }
        }
        if (age == DOME) {
            open(owner, at, R);
        }
        // the lock: first the silent mist, then the final collapse
        if (age == DOME) {
            Util.LOCKED.remove(owner.getUUID());
        }
        if (age == FREEZE - 8) {
            Util.LOCKED.add(owner.getUUID());
        }
        if (age >= DOME && age < END) {
            upkeep(owner, at, R, age);
        }
        if (age >= DOME && age < LAUNCH) {
            strikes(owner, at, R, n, age);
        }
        if (age == GATHER) {
            pickCentre(owner, at, R);
        }
        if (age == LAUNCH) {
            launch(owner, at, n);
        }
        if (age == FREEZE) {
            Util.swing(owner);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.TOLL.get(), 2.0f, 0.7f);
        }
        if (age == HIT) {
            collapse(owner, at, R);
        }
        if (age == END) {
            finish(true);
            discard();
        }
    }

    private void open(LivingEntity owner, Vec3 at, double R) {
        var c = ZegConfig.DOMAIN;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 2.0f, 0.7f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.CHIME.get(), 1.8f, 0.6f);
        Util.blast(level(), at.x, at.y + 1, at.z, 5, 60);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.4, 0), 40, 1.1, 0.03);
        for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
            Util.hurt(owner, e, c.openFlat.get(), c.openPercent.get());
        }
    }

    /** Everything that holds while the domain stands: the debuffs on enemies, Mist Sovereignty on the caster, confused mobs. */
    private void upkeep(LivingEntity owner, Vec3 at, double R, int age) {
        var c = ZegConfig.DOMAIN;
        if (age % 10 == 0) {
            List<LivingEntity> in = Util.enemies(level(), at, R, owner);
            for (LivingEntity e : in) {
                Util.slowPct(e, c.slowPercent.get(), 16);
                Util.put(Util.OUT_DOWN, e, c.outDownPercent.get() / 100.0, 16);
                Util.put(Util.HEAL_CUT, e, c.healDownPercent.get() / 100.0, 16);
                Util.put(Util.VULN, e, c.vulnPercent.get() / 100.0, 16);
                e.addEffect(new MobEffectInstance(ModEffects.ILLUSION.get(), 24, 0, false, false, true));
            }
            // False Target: ordinary hostile mobs lose their target and stumble towards a phantom
            if (age >= DOME + 10 && age < LAUNCH) {
                int n = c.phantoms.get();
                for (LivingEntity e : in) {
                    if (e instanceof Mob m && !Util.isBoss(e) && !(e instanceof net.minecraft.world.entity.player.Player) && random.nextInt(3) != 0) {
                        double[] p = phantomPos(getSeed(), random.nextInt(n), n, age, R, 0, 0);
                        m.setTarget(null);
                        m.getNavigation().moveTo(at.x + p[0], at.y, at.z + p[1], 1.0);
                    }
                }
            }
        }
        if (owner.position().distanceTo(at) <= R + 2 && age % 5 == 0) {
            Util.reduce(owner, c.selfReductionPercent.get() / 100.0, 12);
            Util.hastePct(owner, c.selfSpeedPercent.get(), 12);
            Util.put(Util.OUT_BONUS, owner, c.selfDamagePercent.get() / 100.0, 12);
            Util.put(Util.MELEE_BONUS, owner, c.selfDamagePercent.get() / 100.0, 12);
        }
    }

    private void strikes(LivingEntity owner, Vec3 at, double R, int n, int age) {
        var c = ZegConfig.DOMAIN;
        int interval = Math.max(2, (int) Math.round(c.strikeInterval.get()));
        if ((age - DOME) % interval != 0 || age < DOME + 10) {
            return;
        }
        List<LivingEntity> in = Util.enemies(level(), at, R, owner);
        if (in.isEmpty()) {
            return;
        }
        entityData.set(DATA_STRIKE, age);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SLASH.get(), 1.2f, 0.7f + random.nextFloat() * 0.5f);
        int volley = Math.max(1, n / 3);
        for (int k = 0; k < volley; k++) {
            int i = (age / interval + k * 3) % n;
            double[] p = phantomPos(getSeed(), i, n, age, R, 0, 0);
            Vec3 pp = new Vec3(at.x + p[0], at.y + 6, at.z + p[1]);
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : in) {
                double d = e.position().distanceToSqr(pp);
                if (d < bd && d < 30 * 30) {
                    bd = d;
                    best = e;
                }
            }
            if (best == null) {
                continue;
            }
            strike(owner, best, pp);
        }
    }

    private void strike(LivingEntity owner, LivingEntity e, Vec3 from) {
        var c = ZegConfig.DOMAIN;
        long now = level().getGameTime();
        Long last = lastHit.get(e.getUUID());
        if (last != null && now - last < 6) {
            return;
        }
        lastHit.put(e.getUUID(), now);
        Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
        Util.arc(level(), ModParticles.SPARK.get(), from, p, 1.4);
        Util.fx(level(), ModParticles.SHARD.get(), p.x, p.y, p.z, 6, 0.4, 0.4, 0.4, 0.2);
        Util.fx(level(), ModParticles.FLASH.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        Util.hurt(owner, e, c.strikeDamage.get(), 0);
        addStack(owner, e);
    }

    private void addStack(LivingEntity owner, LivingEntity e) {
        var c = ZegConfig.DOMAIN;
        if (!e.isAlive()) {
            stacks.remove(e.getUUID());
            return;
        }
        int s = stacks.merge(e.getUUID(), 1, Integer::sum);
        if (s >= c.fractureStacks.get()) {
            stacks.remove(e.getUUID());
            Util.soul(owner, e, c.soulDamage.get());
            Util.fx(level(), ModParticles.RUNE.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.5, 0.6, 0.5, 0.05);
            Util.fx(level(), ModParticles.VOID.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.4, 0.5, 0.4, 0.02);
        }
    }

    private void pickCentre(LivingEntity owner, Vec3 at, double R) {
        LivingEntity best = null;
        double bs = -1;
        for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
            double score = e.getMaxHealth() + (Util.isBoss(e) ? 1.0E6 : 0.0);
            if (score > bs) {
                bs = score;
                best = e;
            }
        }
        centre = best;
        Vec3 t = best != null ? best.position().subtract(at) : Vec3.ZERO;
        entityData.set(DATA_TX, (float) t.x);
        entityData.set(DATA_TZ, (float) t.z);
        entityData.set(DATA_TY, (float) t.y);
    }

    private void launch(LivingEntity owner, Vec3 at, int n) {
        var c = ZegConfig.DOMAIN;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 2.0f, 0.8f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BLINK.get(), 2.0f, 0.7f);
        if (centre == null || !centre.isAlive()) {
            return;
        }
        Vec3 p = centre.position().add(0, centre.getBbHeight() * 0.5, 0);
        Util.blast(level(), p.x, p.y, p.z, 3.5, 60);
        for (int i = 0; i < n; i++) {
            if (!centre.isAlive()) {
                break;
            }
            Util.hurt(owner, centre, c.strikeDamage.get(), 0);
            addStack(owner, centre);
        }
        Util.stagger(centre, 20);
    }

    private void collapse(LivingEntity owner, Vec3 at, double R) {
        var c = ZegConfig.DOMAIN;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 2.6f, 0.7f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 2.4f, 0.5f);
        Util.blast(level(), at.x, at.y + 1.5, at.z, R * 0.4, 120);
        Util.ring(level(), ModParticles.MIRROR.get(), at.add(0, 1.0, 0), 40, 0.9, 0.3);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.5, 0), 40, 1.3, 0.05);
        for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
            Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get());
            Util.put(Util.OUT_DOWN, e, 0, 1);
        }
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
