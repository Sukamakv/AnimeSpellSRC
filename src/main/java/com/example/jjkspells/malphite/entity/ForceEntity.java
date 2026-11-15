package com.example.jjkspells.malphite.entity;

import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.registry.ModEffects;
import com.example.jjkspells.malphite.registry.ModEntities;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.registry.ModSpells;
import com.example.jjkspells.malphite.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Unstoppable Force - Cataclysmic Impact. One clock (age = tickCount - 1); the entity stands at the destination:
 * <pre>
 *  0..T_GATHER     the mountain awakens (targeting circle at the destination), the caster is planted
 *  T_GATHER..T_CHARGE  gathering monolithic force
 *  T_CHARGE..T_IMPACT  the charge (swept collision, carries enemies, crowd-control immune, 90% less damage)
 *  T_IMPACT        catastrophic impact: damage, launch, immobilise; the fractured-earth zone begins
 *  T_Q0..          five seismic waves out to the seismic radius
 *  T_RISE          the Living Mountain stands up
 *  T_JUDG          Monolith's Final Judgment
 *  T_END           the caster is free and gains Monolith's Resilience; the zone lasts on its own
 * </pre>
 */
public class ForceEntity extends MalEntityBase {
    public static final int T_GATHER = 20, T_CHARGE = 36, T_IMPACT = 52, T_Q0 = 70, WAVE_GAP = 7, WAVE_LIFE = 10, T_RISE = 104, T_JUDG = 126, T_END = 160;
    public static final int WAVES = 5, SPIKES = 22, ERUPT_LIFE = 14;

    private static final EntityDataAccessor<Float> DATA_SX = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SY = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SZ = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ZONE = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MB = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_EAGE = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_EX = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_EZ = SynchedEntityData.defineId(ForceEntity.class, EntityDataSerializers.FLOAT);

    private Vec3 start = Vec3.ZERO, dest = Vec3.ZERO, impact;
    private boolean charging, blocked, mountainbreaker;
    private final Set<UUID> carried = new HashSet<>(), collided = new HashSet<>(), quakeHit = new HashSet<>();
    private final List<LivingEntity> carriedList = new ArrayList<>();

    public ForceEntity(EntityType<? extends ForceEntity> type, Level level) {
        super(type, level);
    }

    /** Picks the destination from where the caster is looking; null when there is nowhere to go (too short). */
    public static ForceEntity create(Level level, LivingEntity caster) {
        var c = MalConfig.FORCE;
        Vec3 aim = Util.aimPoint(level, caster, c.range.get());
        Vec3 from = caster.position();
        Vec3 flat = new Vec3(aim.x - from.x, 0, aim.z - from.z);
        double len = flat.length();
        if (len < 4.0) {
            Vec3 look = caster.getLookAngle();
            flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize().scale(6.0);
            len = 6.0;
        }
        if (len > c.range.get()) {
            flat = flat.scale(c.range.get() / len);
        }
        double x = from.x + flat.x, z = from.z + flat.z;
        Double g = Util.findGround(level, x, Math.max(from.y, aim.y) + 1, z);
        Vec3 d = new Vec3(x, g != null ? g : from.y, z);
        ForceEntity e = new ForceEntity(ModEntities.FORCE.get(), level);
        e.setOwner(caster);
        e.start = from;
        e.dest = d;
        e.setPos(d.x, d.y, d.z);
        e.entityData.set(DATA_SX, (float) from.x);
        e.entityData.set(DATA_SY, (float) from.y);
        e.entityData.set(DATA_SZ, (float) from.z);
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        return e;
    }

    public Vec3 getStart() {
        return new Vec3(entityData.get(DATA_SX), entityData.get(DATA_SY), entityData.get(DATA_SZ));
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    public int getZoneTicks() {
        return entityData.get(DATA_ZONE);
    }

    public boolean isMountainbreaker() {
        return entityData.get(DATA_MB);
    }

    public int getEruptAge() {
        return entityData.get(DATA_EAGE);
    }

    public Vec3 getEruptPos() {
        return new Vec3(entityData.get(DATA_EX), getY(), entityData.get(DATA_EZ));
    }

    public static double waveRadius(int k) {
        return MalConfig.FORCE.seismicRadius.get() * (k + 1) / (double) WAVES;
    }

    /** Position (x, z offset from the centre) of the i-th stone formation of the seismic cataclysm; they rise one after another. */
    public static double[] spikeOffset(int i, int seed, double radius) {
        double a = ((seed * 13 + i * 137) % 360) * Math.PI / 180.0 + i * 2.399963;
        double r = 3.0 + (radius - 4.0) * Math.sqrt((i + 0.5) / SPIKES);
        return new double[]{Math.cos(a) * r, Math.sin(a) * r};
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SX, 0f);
        entityData.define(DATA_SY, 0f);
        entityData.define(DATA_SZ, 0f);
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_ZONE, 0);
        entityData.define(DATA_MB, false);
        entityData.define(DATA_EAGE, -1);
        entityData.define(DATA_EX, 0f);
        entityData.define(DATA_EZ, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malphite.client.ClientFx.tickForce(this));
            return;
        }
        var c = MalConfig.FORCE;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            start = owner.position();
            Util.LOCKED.add(owner.getUUID());
            Util.FALL_IMMUNE.put(owner.getUUID(), owner.level().getGameTime() + 400);
            Util.sound(level(), start.x, start.y, start.z, ModSounds.RUMBLE.get(), 1.6f, 0.9f);
        }
        Vec3 here = owner.position();
        // phases 1 and 2: planted, gathering, rocks and dust around the body
        if (age < T_CHARGE) {
            owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            Vec3 face = dest.subtract(start);
            owner.setYRot(Util.yawTo(start, dest));
            owner.setYHeadRot(owner.getYRot());
            if (age % 2 == 0) {
                double k = age < T_GATHER ? 1.0 : 1.6;
                Util.fx(level(), ModParticles.DUST.get(), here.x, here.y + 0.2, here.z, (int) (3 * k), 1.4, 0.05, 1.4, 0.03);
                Util.fx(level(), ModParticles.ROCK.get(), here.x, here.y + 1.0, here.z, (int) (2 * k), 0.9, 0.8, 0.9, 0.05);
                Util.fx(level(), ModParticles.EMBER.get(), here.x, here.y + 1.0, here.z, (int) (2 * k), 0.7, 0.8, 0.7, 0.04);
            }
            if (age == T_GATHER) {
                Util.sound(level(), here.x, here.y, here.z, ModSounds.ARMOR.get(), 1.5f, 0.8f);
            }
            if (age == T_CHARGE - 1) {
                Util.sound(level(), here.x, here.y, here.z, ModSounds.CRACK.get(), 1.4f, 0.7f);
            }
        }
        if (age == T_CHARGE) {
            charging = true;
            owner.setNoGravity(true);
            Util.sound(level(), here.x, here.y, here.z, ModSounds.CHARGE.get(), 1.8f, 1.0f);
            Util.reduce(owner, c.chargeReductionPercent.get() / 100.0, T_IMPACT - T_CHARGE + 6);
            Util.kbImmune(owner, T_IMPACT - T_CHARGE + 6);
            Util.ccImmune(owner, T_IMPACT - T_CHARGE + 6);
        }
        if (age >= T_CHARGE && age < T_IMPACT && charging) {
            chargeStep(owner, age);
        } else if (age >= T_CHARGE && age < T_IMPACT) {
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
        }
        if (age == T_IMPACT) {
            impact(owner);
        }
        if (age >= T_IMPACT && impact != null) {
            afterwards(owner, age);
        }
        if (age == T_END) {
            Util.LOCKED.remove(owner.getUUID());
            resilience(owner);
        }
        if (age >= T_IMPACT && age - T_IMPACT >= getZoneTicks() && age > T_END) {
            discard();
        }
    }

    private void chargeStep(LivingEntity owner, int age) {
        var c = MalConfig.FORCE;
        double u = (age - T_CHARGE + 1) / (double) (T_IMPACT - T_CHARGE);
        double f = Math.pow(u, 1.5);
        Vec3 line = start.lerp(dest, f);
        Vec3 prev = owner.position();
        double y = line.y;
        Double g = Util.findGround(level(), line.x, line.y + 3, line.z);
        if (g != null) {
            if (g - prev.y > 1.7) {
                stopCharge(owner);
                return;
            }
            y = g;
        }
        Vec3 next = new Vec3(line.x, y, line.z);
        if (!Util.moveTo(owner, next, Util.yawTo(start, dest), 0f)) {
            stopCharge(owner);
            return;
        }
        owner.setNoGravity(true);
        owner.setDeltaMovement(Vec3.ZERO);
        // everything in the way
        double r = c.collisionRadius.get();
        Vec3 dir = dest.subtract(start);
        dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : new Vec3(dir.x, 0, dir.z).normalize();
        AABB box = new AABB(prev, next).inflate(r + 2.0);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, x -> Util.isEnemy(owner, x))) {
            Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = distToSegment(ec, prev.add(0, 1, 0), next.add(0, 1, 0));
            if (d <= r + e.getBbWidth() / 2.0 && collided.add(e.getUUID())) {
                Util.hurt(owner, e, c.collisionFlat.get(), c.collisionPercent.get());
                if (e.isAlive()) {
                    carried.add(e.getUUID());
                    carriedList.add(e);
                }
                Util.fx(level(), ModParticles.ROCK.get(), ec.x, ec.y, ec.z, 6, 0.3, 0.3, 0.3, 0.3);
                Util.sound(level(), ec.x, ec.y, ec.z, ModSounds.PUNCH.get(), 1.2f, 0.8f);
            }
        }
        // carried enemies ride in front of the caster
        for (LivingEntity e : carriedList) {
            if (e.isAlive()) {
                Vec3 p = next.add(dir.scale(1.9));
                Util.moveTo(e, p, e.getYRot(), e.getXRot());
            }
        }
        // the roaring trail
        Vec3 mid = prev.lerp(next, 0.5);
        Util.fx(level(), ModParticles.DUST.get(), mid.x, mid.y + 0.4, mid.z, 6, 1.2, 0.3, 1.2, 0.08);
        Util.fx(level(), ModParticles.GRAVEL.get(), mid.x, mid.y + 0.4, mid.z, 8, 1.0, 0.3, 1.0, 0.25);
        Util.fx(level(), ModParticles.ROCK.get(), mid.x, mid.y + 0.4, mid.z, 3, 1.0, 0.3, 1.0, 0.2);
        Util.fx(level(), ModParticles.SMOKE.get(), mid.x, mid.y + 0.6, mid.z, 2, 1.0, 0.3, 1.0, 0.04);
        if (age % 3 == 0) {
            Util.sound(level(), mid.x, mid.y, mid.z, ModSounds.GRAVEL.get(), 0.8f, 0.8f);
        }
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double t = l2 < 1.0E-9 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / l2, 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private void stopCharge(LivingEntity owner) {
        charging = false;
        blocked = true;
        owner.setDeltaMovement(Vec3.ZERO);
    }

    private void impact(LivingEntity owner) {
        var c = MalConfig.FORCE;
        owner.setNoGravity(false);
        if (charging) {
            Util.moveTo(owner, dest, Util.yawTo(start, dest), 0f);
        }
        charging = false;
        impact = owner.position();
        setPos(impact.x, impact.y, impact.z);
        Vec3 ip = impact;
        Util.swing(owner);
        Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.BOOM.get(), 2.4f, 0.8f);
        Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.SLAM.get(), 2.0f, 0.7f);
        Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.SPIKE.get(), 1.6f, 0.7f);
        Util.blast(level(), ip.x, ip.y, ip.z, c.impactRadius.get() * 0.7, 140);
        Util.column(level(), ip.x, ip.y, ip.z, 10, 60);
        Util.fxDir(level(), ModParticles.RING.get(), ip.x, ip.y + 0.1, ip.z, 0, 0, 0);
        Util.ring(level(), ModParticles.DUST.get(), ip.add(0, 0.3, 0), 48, 0.9, 0.05);
        Util.ring(level(), ModParticles.ROCK.get(), ip.add(0, 0.6, 0), 32, 0.7, 0.16);
        Util.ring(level(), ModParticles.GRAVEL.get(), ip.add(0, 0.5, 0), 40, 0.6, 0.22);
        List<LivingEntity> in = Util.enemies(level(), ip, c.impactRadius.get(), owner);
        int immobile = (int) Math.round(c.immobileSeconds.get() * 20);
        for (LivingEntity e : in) {
            double d = Math.sqrt((e.getX() - ip.x) * (e.getX() - ip.x) + (e.getZ() - ip.z) * (e.getZ() - ip.z));
            double mult = d <= c.centralRadius.get() ? 1.0 + c.centralBonusPercent.get() / 100.0 : 1.0;
            Util.hurt(owner, e, c.impactFlat.get(), c.impactPercent.get(), mult, 1.0);
            if (e.isAlive()) {
                Util.launchBlocks(e, c.launchBlocks.get());
                Util.stun(e, immobile);
                Util.slowPct(e, c.slowPercent.get(), immobile + (int) (c.slowSeconds.get() * 20) + 20);
                Util.earthVuln(e, c.vulnPercent.get(), (int) (c.vulnSeconds.get() * 20));
            }
        }
        if (in.size() >= c.mbCount.get()) {
            mountainbreaker = true;
            entityData.set(DATA_MB, true);
            Util.refundFraction(owner, ModSpells.GRANITE.get(), c.mbRefundPercent.get() / 100.0);
        }
        int zone = (int) Math.round((c.zoneSeconds.get() + (mountainbreaker ? c.mbZoneSeconds.get() : 0)) * 20);
        entityData.set(DATA_ZONE, zone);
    }

    private void afterwards(LivingEntity owner, int age) {
        var c = MalConfig.FORCE;
        Vec3 ip = impact;
        // the seismic cataclysm: five waves
        if (age >= T_Q0 && age < T_RISE && age % 3 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * (c.seismicRadius.get() - 3);
            Util.fxDir(level(), ModParticles.ROCK.get(), ip.x + Math.cos(a) * r, ip.y + 0.3, ip.z + Math.sin(a) * r, 0, 0.3, 0);
            Util.fxDir(level(), ModParticles.DUST.get(), ip.x + Math.cos(a) * r, ip.y + 0.2, ip.z + Math.sin(a) * r, 0, 0.05, 0);
        }
        if (age == T_Q0) {
            Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.RUMBLE.get(), 2.0f, 0.8f);
        }
        for (int k = 0; k < WAVES; k++) {
            int t0 = T_Q0 + k * WAVE_GAP;
            if (age == t0) {
                Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.SPIKE.get(), 1.4f, 0.7f + 0.1f * k);
            }
            if (age == t0 + WAVE_LIFE) {
                double r1 = waveRadius(k);
                double mult = mountainbreaker ? 1.0 + c.mbDamagePercent.get() / 100.0 : 1.0;
                for (LivingEntity e : Util.enemies(level(), ip, r1, owner)) {
                    if (quakeHit.add(e.getUUID())) {
                        Util.hurt(owner, e, c.quakeFlat.get(), c.quakePercent.get(), mult, 1.0);
                        if (e.isAlive()) {
                            Util.launchBlocks(e, 2.5);
                            Util.knock(e, ip, 3.0, 0.1);
                        }
                    }
                }
                Util.ring(level(), ModParticles.DUST.get(), ip.add(0, 0.3, 0), 40, 0.15, 0.05);
                int n = 36;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    Util.fxDir(level(), i % 3 == 0 ? ModParticles.ROCK.get() : ModParticles.DUST.get(), ip.x + Math.cos(a) * r1, ip.y + 0.3, ip.z + Math.sin(a) * r1, 0, 0.22, 0);
                }
            }
        }
        // the Living Mountain stands up
        if (age == T_RISE) {
            Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.GRIND.get(), 1.8f, 0.6f);
        }
        if (age >= T_RISE && age < T_JUDG && age % 2 == 0) {
            Util.fx(level(), ModParticles.ROCK.get(), ip.x, ip.y + 2.5, ip.z, 3, 1.0, 0.5, 1.0, 0.05);
            Util.fx(level(), ModParticles.DUST.get(), ip.x, ip.y + 3.0, ip.z, 3, 1.4, 0.6, 1.4, 0.03);
            Util.fx(level(), ModParticles.EMBER.get(), ip.x, ip.y + 1.5, ip.z, 3, 0.8, 1.0, 0.8, 0.05);
        }
        // Monolith's Final Judgment
        if (age == T_JUDG) {
            Util.swing(owner);
            Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.BOOM.get(), 2.4f, 0.7f);
            Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.SLAM.get(), 2.0f, 0.6f);
            Util.blast(level(), ip.x, ip.y, ip.z, c.finalRadius.get() * 0.6, 120);
            Util.column(level(), ip.x, ip.y, ip.z, 12, 50);
            Util.fxDir(level(), ModParticles.RING.get(), ip.x, ip.y + 0.1, ip.z, 0, 0, 0);
            Util.ring(level(), ModParticles.DUST.get(), ip.add(0, 0.3, 0), 48, 0.8, 0.05);
            Util.ring(level(), ModParticles.ROCK.get(), ip.add(0, 0.5, 0), 32, 0.6, 0.15);
            for (LivingEntity e : Util.enemies(level(), ip, c.finalRadius.get(), owner)) {
                Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get());
                if (e.isAlive()) {
                    Util.knock(e, ip, 6.0, 0.25);
                }
            }
        }
        if (age > T_JUDG && age < T_END && age % 4 == 0) {
            // aftermath: rubble and dust drifting down
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 12;
            Util.fxDir(level(), ModParticles.GRAVEL.get(), ip.x + Math.cos(a) * r, ip.y + 6, ip.z + Math.sin(a) * r, 0, -0.1, 0);
            Util.fxDir(level(), ModParticles.DUST.get(), ip.x + Math.cos(a) * r, ip.y + 1, ip.z + Math.sin(a) * r, 0, 0.02, 0);
        }
        zone(owner, age);
    }

    private void zone(LivingEntity owner, int age) {
        var c = MalConfig.FORCE;
        int rel = age - T_IMPACT;
        int total = getZoneTicks();
        if (rel < 0 || rel >= total) {
            return;
        }
        Vec3 ip = impact;
        double zr = c.seismicRadius.get();
        if (rel % 10 == 0) {
            for (LivingEntity e : Util.enemies(level(), ip, zr, owner)) {
                Util.slowPct(e, c.zoneSlowPercent.get(), 14);
                e.addEffect(new MobEffectInstance(ModEffects.QUAKE.get(), 14, 0, false, false, true));
            }
        }
        if (rel % 8 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * zr;
            Util.fxDir(level(), ModParticles.EMBER.get(), ip.x + Math.cos(a) * r, ip.y + 0.2, ip.z + Math.sin(a) * r, 0, 0.05, 0);
        }
        if (rel > 0 && rel % c.zoneInterval.get() == 0) {
            List<LivingEntity> in = Util.enemies(level(), ip, zr, owner);
            Vec3 p;
            if (!in.isEmpty() && random.nextInt(3) != 0) {
                LivingEntity t = in.get(random.nextInt(in.size()));
                p = new Vec3(t.getX(), ip.y, t.getZ());
            } else {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * zr;
                p = new Vec3(ip.x + Math.cos(a) * r, ip.y, ip.z + Math.sin(a) * r);
            }
            entityData.set(DATA_EX, (float) p.x);
            entityData.set(DATA_EZ, (float) p.z);
            entityData.set(DATA_EAGE, age);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SPIKE.get(), 1.0f, 0.8f);
            Util.debris(level(), p.x, p.y, p.z, 1.0, 12);
            Util.column(level(), p.x, p.y, p.z, 4, 10);
            for (LivingEntity e : Util.enemies(level(), p, c.spikeRadius.get(), owner)) {
                Util.hurt(owner, e, c.spikeFlat.get(), 0);
                if (e.isAlive()) {
                    Util.launchBlocks(e, 1.2);
                }
            }
        }
        if (rel > 0 && rel % 60 == 0) {
            // minor tremors
            for (LivingEntity e : Util.enemies(level(), ip, zr, owner)) {
                if (random.nextInt(100) < 40) {
                    Util.stagger(e, MalConfig.G.staggerTicks.get());
                }
            }
            Util.sound(level(), ip.x, ip.y, ip.z, ModSounds.RUMBLE.get(), 1.0f, 1.0f);
        }
    }

    /** Monolith's Resilience: the reward for finishing the sequence. */
    private void resilience(LivingEntity owner) {
        var c = MalConfig.FORCE;
        int ticks = (int) Math.round(c.resSeconds.get() * 20);
        Util.reduce(owner, c.resReductionPercent.get() / 100.0, ticks);
        Util.kbImmune(owner, ticks);
        Util.FALL_IMMUNE.put(owner.getUUID(), owner.level().getGameTime() + ticks);
        Util.MELEE_BONUS.put(owner.getUUID(), new double[]{c.resMeleePercent.get() / 100.0, owner.level().getGameTime() + ticks});
        owner.addEffect(new MobEffectInstance(ModEffects.RESILIENCE.get(), ticks, 0, false, false, true));
        Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.ARMOR.get(), 1.2f, 0.7f);
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
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            owner.setNoGravity(false);
            if (!normal && tickCount - 1 < T_END) {
                Util.clearReduce(owner);
            }
        }
    }
}
