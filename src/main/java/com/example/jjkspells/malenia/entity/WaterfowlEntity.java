package com.example.jjkspells.malenia.entity;

import com.example.jjkspells.malenia.config.MelConfig;
import com.example.jjkspells.malenia.registry.ModEntities;
import com.example.jjkspells.malenia.registry.ModParticles;
import com.example.jjkspells.malenia.registry.ModSounds;
import com.example.jjkspells.malenia.util.Util;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Waterfowl Dance - The Unending Blade. One clock (age = tickCount - 1):
 * <pre>
 *  0..F1     leap 4 blocks up
 *  F1..F2    flurry 1: eight slashes while closing in on the target
 *  F2..F3    flurry 2: eight slashes circling, hitting everything within the area
 *  F3..WIND  flurry 3: eight rising slashes, the last launches
 *  WIND..DIVE hang above, gathering
 *  DIVE..CRES plunge, then the final crescent
 *  CRES..END recover
 * </pre>
 */
public class WaterfowlEntity extends MelEntityBase {
    public static final int F1 = 10, F2 = 28, F3 = 46, WIND = 64, DIVE = 72, CRES = 77, END = 90;

    private static final EntityDataAccessor<Integer> DATA_COUNT = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SX = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SY = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SZ = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CX = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CY = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CZ = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CRES = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DANCE = SynchedEntityData.defineId(WaterfowlEntity.class, EntityDataSerializers.BOOLEAN);

    // client only
    public final List<double[]> fx = new ArrayList<>();
    public final List<Vec3> trail = new ArrayList<>();
    public int seen;

    private UUID targetId;
    private Vec3 start, center, leapTop, approach, diveFrom;
    private double angle;
    private int connected, slashes;

    public WaterfowlEntity(EntityType<? extends WaterfowlEntity> type, Level level) {
        super(type, level);
    }

    public static WaterfowlEntity create(Level level, LivingEntity caster, LivingEntity target) {
        WaterfowlEntity e = new WaterfowlEntity(ModEntities.WATERFOWL.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.targetId = target == null ? null : target.getUUID();
        return e;
    }

    /** The enemy to dance around: the one looked at, else the nearest within range. */
    public static LivingEntity pickTarget(Level level, LivingEntity caster, double range, Vec3 around) {
        HitResult hr = Utils.raycastForEntity(level, caster, (float) range, true, 0.35f);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && Util.isEnemy(caster, le)) {
            return le;
        }
        return nearest(level, caster, around, range);
    }

    public static LivingEntity nearest(Level level, LivingEntity caster, Vec3 around, double range) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Util.enemies(level, around, range, caster)) {
            double d = e.distanceToSqr(around);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    public int getCount() {
        return entityData.get(DATA_COUNT);
    }

    public float getSlashYaw() {
        return entityData.get(DATA_YAW);
    }

    public float getSlashRoll() {
        return entityData.get(DATA_ROLL);
    }

    public Vec3 getSlashPos() {
        return new Vec3(entityData.get(DATA_SX), entityData.get(DATA_SY), entityData.get(DATA_SZ));
    }

    public Vec3 getCenter() {
        return new Vec3(entityData.get(DATA_CX), entityData.get(DATA_CY), entityData.get(DATA_CZ));
    }

    public int getCresAge() {
        return entityData.get(DATA_CRES);
    }

    public boolean isDance() {
        return entityData.get(DATA_DANCE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COUNT, 0);
        entityData.define(DATA_YAW, 0f);
        entityData.define(DATA_ROLL, 0f);
        entityData.define(DATA_SX, 0f);
        entityData.define(DATA_SY, 0f);
        entityData.define(DATA_SZ, 0f);
        entityData.define(DATA_CX, 0f);
        entityData.define(DATA_CY, 0f);
        entityData.define(DATA_CZ, 0f);
        entityData.define(DATA_CRES, -1);
        entityData.define(DATA_DANCE, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malenia.client.ClientFx.tickWaterfowl(this));
            return;
        }
        var c = MelConfig.WATERFOWL;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            start = owner.position();
            LivingEntity t = target();
            center = t != null ? t.position() : start.add(flat(owner.getLookAngle()).scale(5.0));
            leapTop = start.add(0, c.leapBlocks.get(), 0);
            Vec3 away = flat(start.subtract(center));
            approach = center.add(away.scale(2.0)).add(0, 1.4, 0);
            angle = Math.atan2(approach.z - center.z, approach.x - center.x);
            lsCap = c.lifestealCap.get();
            Util.LOCKED.add(owner.getUUID());
            Util.reduce(owner, c.flowReductionPercent.get() / 100.0, WIND);
            Util.kbImmune(owner, END + 6);
            Util.FALL_IMMUNE.put(owner.getUUID(), owner.level().getGameTime() + END + 60);
            Util.sound(level(), start.x, start.y, start.z, ModSounds.DASH.get(), 1.2f, 1.1f);
        }
        owner.setSprinting(false);
        if (age < CRES) {
            // Flowing Blade: ordinary slowness does nothing
            owner.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            Util.clearSpeed(owner);
        }
        // Relentless Pursuit
        LivingEntity t = target();
        if (age < CRES) {
            if (t == null || !t.isAlive() || Util.HIDDEN.contains(t.getUUID())) {
                t = nearest(level(), owner, owner.position(), c.trackRange.get());
                targetId = t == null ? null : t.getUUID();
            }
            if (t != null) {
                center = center.lerp(t.position(), 0.5);
            }
        }
        entityData.set(DATA_CX, (float) center.x);
        entityData.set(DATA_CY, (float) center.y);
        entityData.set(DATA_CZ, (float) center.z);
        setPos(owner.getX(), owner.getY(), owner.getZ());
        move(owner, age);
        // slashes
        if (age >= F1 && age < WIND && (age - F1) % 2 == 0) {
            int k = (age - F1) / 2;
            int flurry = k / 9;
            // every flurry lasts 18 ticks: nine slots of two ticks, eight slashes (the ninth slot is the breath between flurries)
            if (k % 9 < 8) {
                slash(owner, flurry, slashes++);
            }
        }
        if (age == CRES) {
            crescent(owner);
        }
        if (age > CRES && age < END && age % 3 == 0) {
            Util.fx(level(), ModParticles.SILVER.get(), center.x, center.y + 0.3, center.z, 3, 3.0, 0.2, 3.0, 0.05);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private LivingEntity target() {
        if (targetId != null && level() instanceof ServerLevel sl && sl.getEntity(targetId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    /** Where the caster is at each moment. */
    private void move(LivingEntity owner, int age) {
        Vec3 here = owner.position();
        Vec3 p;
        if (age < F1) {
            p = start.add(0, (leapTop.y - start.y) * smooth(age / (double) F1), 0);
        } else if (age < F2) {
            double u = smooth((age - F1 + 1) / 18.0);
            Vec3 a = leapTop.lerp(approach.add(0, 0, 0), u);
            Vec3 side = flat(center.subtract(start));
            double zig = Math.sin(age * 1.7) * 0.9 * (1.0 - u);
            p = new Vec3(a.x - side.z * zig, a.y, a.z + side.x * zig);
            angle = Math.atan2(p.z - center.z, p.x - center.x);
        } else if (age < F3) {
            angle += Math.toRadians(20);
            p = new Vec3(center.x + Math.cos(angle) * 2.8, center.y + 1.6 + 0.3 * Math.sin(age * 0.9), center.z + Math.sin(angle) * 2.8);
        } else if (age < WIND) {
            double u = smooth((age - F3) / 18.0);
            angle += Math.toRadians(24);
            double r = 2.4 - 0.9 * u;
            p = new Vec3(center.x + Math.cos(angle) * r, center.y + 1.5 + 2.2 * u, center.z + Math.sin(angle) * r);
        } else if (age < DIVE) {
            double u = smooth((age - WIND) / 8.0);
            p = new Vec3(here.x, center.y + 3.7 + 0.8 * u, here.z);
        } else if (age < CRES) {
            double u = smooth((age - DIVE + 1) / (double) (CRES - DIVE));
            Vec3 a = new Vec3(center.x + Math.cos(angle) * 1.6, center.y + 1.0, center.z + Math.sin(angle) * 1.6);
            if (diveFrom == null) {
                diveFrom = here;
            }
            p = diveFrom.lerp(a, u);
        } else {
            Double g = Util.findGround(level(), here.x, here.y + 1, here.z);
            double gy = g != null ? g : center.y;
            p = new Vec3(here.x, Math.max(gy, here.y - 0.6), here.z);
        }
        float yaw = Util.yawTo(p, new Vec3(center.x, p.y, center.z));
        float pitch = age >= WIND && age < CRES ? 40f : 15f;
        hover(owner, p, yaw, pitch);
        if (age >= END - 6) {
            owner.setNoGravity(false);
        }
    }

    private void slash(LivingEntity owner, int flurry, int n) {
        var c = MelConfig.WATERFOWL;
        Vec3 p = owner.position();
        Vec3 mid = p.add(0, owner.getBbHeight() * 0.55, 0);
        Vec3 face = flat(center.subtract(p));
        Vec3 sp = mid.add(face.scale(1.4));
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity e : Util.enemies(level(), center, Math.max(9.0, c.areaRadius.get() + 3), owner)) {
            Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
            boolean in;
            if (flurry == 1) {
                double dx = e.getX() - center.x, dz = e.getZ() - center.z;
                double r = c.areaRadius.get() + e.getBbWidth() / 2.0;
                in = dx * dx + dz * dz <= r * r;
            } else {
                in = ec.distanceTo(sp) <= c.slashReach.get() + e.getBbWidth() / 2.0;
            }
            if (in) {
                hit.add(e);
            }
        }
        entityData.set(DATA_YAW, owner.getYRot());
        entityData.set(DATA_ROLL, ((n * 67) % 140) - 70f);
        entityData.set(DATA_SX, (float) sp.x);
        entityData.set(DATA_SY, (float) sp.y);
        entityData.set(DATA_SZ, (float) sp.z);
        entityData.set(DATA_COUNT, entityData.get(DATA_COUNT) + 1);
        Util.swing(owner);
        Util.sound(level(), sp.x, sp.y, sp.z, n % 8 == 0 ? ModSounds.FLURRY.get() : ModSounds.SLASH.get(), 1.0f, 0.95f + 0.1f * (n % 3));
        Util.bladeBurst(level(), sp.x, sp.y, sp.z, 4);
        boolean last = flurry == 2 && n % 8 == 7;
        for (LivingEntity e : hit) {
            Util.hurt(owner, e, c.slashFlat.get(), 0);
            Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Util.bladeBurst(level(), ec.x, ec.y, ec.z, 5);
            if (e.isAlive() && last) {
                Util.launchBlocks(e, c.launchBlocks.get());
            }
        }
        if (!hit.isEmpty()) {
            connected++;
            steal(c.lifestealSlash.get());
        }
    }

    private void crescent(LivingEntity owner) {
        var c = MelConfig.WATERFOWL;
        boolean dance = connected >= c.danceSlashes.get();
        entityData.set(DATA_CRES, tickCount - 1);
        entityData.set(DATA_DANCE, dance);
        Vec3 g = center;
        Double gy = Util.findGround(level(), g.x, g.y + 2, g.z);
        if (gy != null) {
            g = new Vec3(g.x, gy, g.z);
            center = g;
        }
        Util.sound(level(), g.x, g.y, g.z, ModSounds.BOOM.get(), 1.4f, 1.3f);
        Util.sound(level(), g.x, g.y, g.z, ModSounds.RING.get(), 1.2f, 1.0f);
        Util.ring(level(), ModParticles.SILVER.get(), g.add(0, 0.5, 0), 36, 0.55, 0.05);
        Util.ring(level(), ModParticles.GOLD.get(), g.add(0, 0.9, 0), 24, 0.4, 0.08);
        Util.fxDir(level(), ModParticles.RING.get(), g.x, g.y + 0.6, g.z, 0, 0, 0);
        Util.fx(level(), ModParticles.FLASH.get(), g.x, g.y + 1.0, g.z, 1, 0, 0, 0, 0);
        double mult = dance ? 1.0 + c.danceBonusPercent.get() / 100.0 : 1.0;
        for (LivingEntity e : Util.enemies(level(), g, c.crescentRadius.get(), owner)) {
            Util.hurt(owner, e, c.crescentFlat.get(), c.crescentPercent.get(), mult, 1.0);
            steal(c.lifestealCrescent.get());
            if (e.isAlive()) {
                Util.knock(e, g, c.crescentKnock.get(), 0.25);
            }
        }
        if (dance) {
            Util.hastePct(owner, c.danceSpeedPercent.get(), (int) (c.danceSpeedSeconds.get() * 20));
            Util.reduce(owner, c.danceReductionPercent.get() / 100.0, (int) (c.danceReductionSeconds.get() * 20));
            owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.example.jjkspells.malenia.registry.ModEffects.SWIFT.get(), (int) (c.danceSpeedSeconds.get() * 20), 0, false, false, true));
        }
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        releaseCommon(normal);
    }
}
