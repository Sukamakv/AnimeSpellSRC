package com.example.jjkspells.malphite.entity;

import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.registry.ModEntities;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Brutal Strikes - Titanic Onslaught. Follows the caster; one clock (age = tickCount - 1):
 * <pre>
 *  0..S1   reinforcement: stone plates grow over the arms
 *  S1      right hook (cone, knocks sideways)
 *  S2      left backhand (knocks back)
 *  S3      two-handed punch: cone shockwave, stagger
 *  S3..SM  raising the fists
 *  SM      Titanic Smash: stone spikes, launch, Armorbreaker
 *  END     done
 * </pre>
 * The cones face wherever the caster is looking at the moment of each blow.
 */
public class StrikesEntity extends MalEntityBase {
    public static final int S1 = 14, S2 = 26, S3 = 42, SM = 66, END = 78;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(StrikesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(StrikesEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_K = SynchedEntityData.defineId(StrikesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_KAGE = SynchedEntityData.defineId(StrikesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MONO = SynchedEntityData.defineId(StrikesEntity.class, EntityDataSerializers.BOOLEAN);

    private final Map<UUID, Integer> hits = new HashMap<>();
    private final Set<UUID> struck = new HashSet<>();

    public StrikesEntity(EntityType<? extends StrikesEntity> type, Level level) {
        super(type, level);
    }

    public static StrikesEntity create(Level level, LivingEntity caster) {
        StrikesEntity e = new StrikesEntity(ModEntities.STRIKES.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        e.entityData.set(DATA_YAW, caster.getYRot());
        return e;
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    /** Yaw (degrees) the latest blow was aimed along. */
    public float getBlowYaw() {
        return entityData.get(DATA_YAW);
    }

    /** Index of the latest blow (1, 2, 3, 4 = smash), 0 before the first. */
    public int getBlow() {
        return entityData.get(DATA_K);
    }

    public int getBlowAge() {
        return entityData.get(DATA_KAGE);
    }

    public boolean isMonolithic() {
        return entityData.get(DATA_MONO);
    }

    public float getSmashRange() {
        return ((Number) (isMonolithic() ? MalConfig.STRIKES.monolithRadius.get() : MalConfig.STRIKES.smashRange.get())).floatValue();
    }

    /** Offset (x, z) from the caster of the i-th of n smash spikes: spread over the cone, nearer ones first. */
    public static double[] spikeOffset(int i, int n, int seed, double yawDeg, double range, double coneDeg) {
        double f = (i + 0.5) / n;
        double d = 1.6 + (range - 1.6) * Math.sqrt(f);
        double jitter = ((seed * 31 + i * 17) % 100) / 100.0 - 0.5;
        double ang = Math.toRadians(yawDeg) + Math.toRadians(coneDeg) * (((i * 7) % n) / (double) Math.max(1, n - 1) - 0.5) * 0.9 + jitter * 0.25;
        return new double[]{-Math.sin(ang) * d, Math.cos(ang) * d};
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_YAW, 0f);
        entityData.define(DATA_K, 0);
        entityData.define(DATA_KAGE, -1);
        entityData.define(DATA_MONO, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malphite.client.ClientFx.tickStrikes(this));
            return;
        }
        var c = MalConfig.STRIKES;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            AttributeInstance kb = owner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(KB_ID);
                kb.addTransientModifier(new AttributeModifier(KB_ID, "malphite_strikes_kb", c.kbResistPercent.get() / 100.0, AttributeModifier.Operation.ADDITION));
            }
            Util.reduce(owner, c.reductionPercent.get() / 100.0, END + 4);
            double sp = c.speedPercent.get();
            if (sp < 0) {
                Util.slowPct(owner, -sp, END + 4);
            } else if (sp > 0) {
                Util.hastePct(owner, sp, END + 4);
            }
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.GRIND.get(), 1.4f, 0.8f);
        }
        owner.setSprinting(false);
        if (age < S1 && age % 2 == 0) {
            Vec3 p = owner.position();
            Util.fx(level(), ModParticles.ROCK.get(), p.x, p.y + 1.1, p.z, 3, 0.6, 0.5, 0.6, 0.05);
            Util.fx(level(), ModParticles.GRAVEL.get(), p.x, p.y + 1.3, p.z, 4, 0.5, 0.4, 0.5, 0.04);
            Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1.1, p.z, 2, 0.5, 0.5, 0.5, 0.03);
        }
        if (age > S3 && age < SM && age % 2 == 0) {
            Vec3 p = owner.position();
            double a = age * 0.8;
            Util.fx(level(), ModParticles.DUST.get(), p.x + Math.cos(a), p.y + 1.8, p.z + Math.sin(a), 2, 0.2, 0.2, 0.2, 0.03);
            Util.fx(level(), ModParticles.ROCK.get(), p.x, p.y + 2.2, p.z, 2, 0.5, 0.3, 0.5, 0.05);
            Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1.8, p.z, 2, 0.5, 0.4, 0.5, 0.03);
        }
        if (age == S1) {
            blow(owner, 1);
        } else if (age == S2) {
            blow(owner, 2);
        } else if (age == S3) {
            blow(owner, 3);
        } else if (age == SM) {
            blow(owner, 4);
        } else if (age >= END) {
            discard();
        }
    }

    /** Enemies in front of the caster inside the cone. */
    private java.util.List<LivingEntity> cone(LivingEntity owner, Vec3 face, double reach) {
        double cosHalf = Math.cos(Math.toRadians(MalConfig.STRIKES.coneDegrees.get() / 2.0));
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        for (LivingEntity e : Util.enemies(level(), owner.position(), reach, owner)) {
            Vec3 to = new Vec3(e.getX() - owner.getX(), 0, e.getZ() - owner.getZ());
            if (to.lengthSqr() < 1.0E-6 || to.normalize().dot(face) >= cosHalf) {
                out.add(e);
            }
        }
        return out;
    }

    private void blow(LivingEntity owner, int k) {
        var c = MalConfig.STRIKES;
        Vec3 look = owner.getLookAngle();
        Vec3 face = new Vec3(look.x, 0, look.z);
        face = face.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : face.normalize();
        entityData.set(DATA_YAW, owner.getYRot());
        entityData.set(DATA_K, k);
        entityData.set(DATA_KAGE, tickCount - 1);
        Util.swing(owner);
        Vec3 at = owner.position();
        double reach = k >= 3 ? c.smashRange.get() : c.strikeRadius.get();
        boolean smash = k == 4;
        double flat = switch (k) {
            case 1 -> c.flat1.get();
            case 2 -> c.flat2.get();
            case 3 -> c.flat3.get();
            default -> c.flat4.get();
        };
        double pct = k == 3 ? c.percent3.get() : k == 4 ? c.percent4.get() : 0.0;
        double bonus = 1.0;
        if (smash) {
            if (struck.size() >= c.monolithCount.get()) {
                entityData.set(DATA_MONO, true);
                reach = c.monolithRadius.get();
                bonus = 1.0 + c.monolithDamagePercent.get() / 100.0;
                Util.MELEE_BONUS.put(owner.getUUID(), new double[]{c.monolithMeleePercent.get() / 100.0, owner.level().getGameTime() + c.monolithMeleeSeconds.get() * 20});
                Util.sound(level(), at.x, at.y, at.z, ModSounds.ARMOR.get(), 1.2f, 0.8f);
            }
        }
        Util.sound(level(), at.x, at.y + 1, at.z, smash ? ModSounds.SLAM.get() : ModSounds.PUNCH.get(), smash ? 1.8f : 1.4f, smash ? 0.8f : 0.9f + 0.1f * k);
        // the visible sweep: dust and gravel along the cone
        for (int i = -10; i <= 10; i++) {
            double a = Math.toRadians(i * c.coneDegrees.get() / 20.0);
            double cs = Math.cos(a), sn = Math.sin(a);
            Vec3 d = new Vec3(face.x * cs - face.z * sn, 0, face.x * sn + face.z * cs);
            for (int s = 1; s <= 3; s++) {
                double r = reach * s / 3.0 * (smash ? 1.0 : 0.85);
                Util.fxDir(level(), s == 3 ? ModParticles.GRAVEL.get() : ModParticles.DUST.get(), at.x + d.x * r, at.y + 0.5 + (k == 3 || smash ? 0.1 : 1.0), at.z + d.z * r,
                        d.x * 0.25, 0.04, d.z * 0.25);
            }
        }
        if (smash) {
            Util.blast(level(), at.x + face.x * reach * 0.5, at.y + 0.3, at.z + face.z * reach * 0.5, reach * 0.55, 70);
            Util.column(level(), at.x + face.x * reach * 0.6, at.y, at.z + face.z * reach * 0.6, 4, 20);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.SPIKE.get(), 1.4f, 0.8f);
        } else if (k == 3) {
            Util.fxDir(level(), ModParticles.RING.get(), at.x + face.x * 1.5, at.y + 0.9, at.z + face.z * 1.5, 0, 0, 0);
        }
        for (LivingEntity e : cone(owner, face, reach)) {
            int prev = hits.getOrDefault(e.getUUID(), 0);
            double mom = 1.0 + Math.min(c.momentumMax.get(), c.momentumPercent.get() * prev) / 100.0;
            hits.put(e.getUUID(), prev + 1);
            struck.add(e.getUUID());
            Util.hurt(owner, e, flat, pct, mom * bonus, 1.0);
            if (!e.isAlive()) {
                continue;
            }
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Util.fx(level(), ModParticles.ROCK.get(), p.x, p.y, p.z, 5, 0.3, 0.3, 0.3, 0.2);
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.25);
            switch (k) {
                case 1 -> Util.knock(e, e.position().subtract(face.z, 0, -face.x), 3.0, 0.1);
                case 2 -> Util.knock(e, at, 3.0, 0.12);
                case 3 -> Util.stagger(e, MalConfig.G.staggerTicks.get());
                default -> {
                    Util.launchBlocks(e, c.smashLaunchBlocks.get());
                    Util.armorDown(e, c.armorBreakPercent.get(), (int) (c.armorBreakSeconds.get() * 20));
                    Util.slowPct(e, c.smashSlowPercent.get(), (int) (c.smashSlowSeconds.get() * 20));
                }
            }
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
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            AttributeInstance kb = owner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(KB_ID);
            }
            if (!normal) {
                Util.clearReduce(owner);
            }
        }
    }
}
