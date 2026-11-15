package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.registry.ModEntities;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Megiddo - God's Wrath. One clock (age = tickCount - 1), the entity stands at the marked point:
 * <pre>
 *  0..DROPS      droplets gather
 *  DROPS..LENS   three lenses form in the sky
 *  BARRAGE..     twelve beams, one every 2 ticks, at the weakest enemies
 *  FOCUS         a lens above the weakest enemy fires the focused judgment
 *  RAIN..END     the final rain; every enemy inside the radius is hit once at RAIN_HIT
 * </pre>
 * Visual events (fx list): type 1 = beam {from, to, thickness}, 2 = focus lens {pos}.
 */
public class MegiddoEntity extends RimEntityBase {
    public static final int DROPS = 16, LENS = 32, BARRAGE = 32, FOCUS_LENS = 56, FOCUS = 70, RAIN = 76, RAIN_HIT = 90, END = 100;

    private static final EntityDataAccessor<Boolean> DATA_DAY = SynchedEntityData.defineId(MegiddoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_R = SynchedEntityData.defineId(MegiddoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(MegiddoEntity.class, EntityDataSerializers.INT);

    private Vec3 point;
    private double dayMult = 1.0;
    private UUID primary;

    public MegiddoEntity(EntityType<? extends MegiddoEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 24;
    }

    /** Clear sky, daytime, no rain, open to the sky at the point. */
    public static boolean daylight(Level level, Vec3 p) {
        return level.isDay() && !level.isRaining() && !level.isThundering() && level.canSeeSky(BlockPos.containing(p.x, p.y + 1, p.z));
    }

    public static MegiddoEntity create(Level level, LivingEntity caster, Vec3 point, boolean day) {
        MegiddoEntity e = new MegiddoEntity(ModEntities.MEGIDDO.get(), level);
        e.setOwner(caster);
        e.setPos(point.x, point.y, point.z);
        e.point = point;
        e.dayMult = day ? 1.0 + RimConfig.MEGIDDO.daylightBonusPercent.get() / 100.0 : 1.0;
        e.entityData.set(DATA_DAY, day);
        e.entityData.set(DATA_R, (float) (double) RimConfig.MEGIDDO.radius.get());
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        return e;
    }

    public boolean isDay() {
        return entityData.get(DATA_DAY);
    }

    public float getRadius() {
        return entityData.get(DATA_R);
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    /** One of the three lens positions in the sky above the point (relative to the entity). */
    public static Vec3 lensPos(int seed, int i, int age) {
        double a = seed * 0.37 + i * 2.0944 + age * 0.004;
        return new Vec3(Math.cos(a) * 7.0, 20.0 + i * 2.5, Math.sin(a) * 7.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DAY, false);
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_R, 20f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.rimuru.client.ClientFx.tickMegiddo(this));
            return;
        }
        var c = RimConfig.MEGIDDO;
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (age < END && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), point.x, point.y, point.z, ModSounds.HUM.get(), 1.6f, 1.2f);
        }
        if (age < END) {
            freeze(owner);
        }
        double radius = c.radius.get();
        if (age >= BARRAGE && age < FOCUS_LENS && (age - BARRAGE) % 2 == 0) {
            int i = (age - BARRAGE) / 2;
            List<LivingEntity> cand = weakest(owner, radius);
            if (i < c.beamCount.get()) {
                Vec3 to;
                if (cand.isEmpty()) {
                    double a = level().random.nextDouble() * Math.PI * 2, r = level().random.nextDouble() * radius;
                    to = point.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                } else {
                    LivingEntity t = cand.get(i % cand.size());
                    to = t.position().add(0, t.getBbHeight() * 0.5, 0);
                    Util.hurt(owner, t, c.beamFlat.get(), 0, dayMult, 1.0);
                    Util.lightBurst(level(), to.x, to.y, to.z, 8);
                }
                beam(i, to, 0.5);
            }
        }
        if (age == FOCUS_LENS) {
            List<LivingEntity> cand = weakest(owner, radius);
            if (!cand.isEmpty()) {
                primary = cand.get(0).getUUID();
                Vec3 p = cand.get(0).position();
                pushFx(2, p.x - point.x, p.y - point.y + 18.0, p.z - point.z);
            }
        }
        if (age == FOCUS && primary != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(primary) instanceof LivingEntity t && t.isAlive()) {
            double mult = dayMult;
            if (t.getHealth() < t.getMaxHealth() * 0.2) {
                mult *= 1.0 + c.executeBonusPercent.get() / 100.0;
            }
            Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
            Util.hurt(owner, t, c.focusFlat.get(), c.focusPercent.get(), mult, 1.0);
            pushFx(1, t.getX() - point.x, t.getY() - point.y + 18.0, t.getZ() - point.z, to.x - point.x, to.y - point.y, to.z - point.z, 2.2);
            Util.lightBurst(level(), to.x, to.y, to.z, 30);
            Util.sound(level(), to.x, to.y, to.z, ModSounds.LASER.get(), 1.8f, 0.8f);
        }
        if (age >= RAIN && age < END && age % 2 == 0) {
            double a = level().random.nextDouble() * Math.PI * 2, r = Math.sqrt(level().random.nextDouble()) * radius;
            beam(age, point.add(Math.cos(a) * r, 0, Math.sin(a) * r), 0.35);
        }
        if (age == RAIN_HIT) {
            for (LivingEntity e : Util.enemies(level(), point, radius, owner)) {
                Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get(), dayMult, 1.0);
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
                beam(age + e.getId(), to, 0.7);
                Util.lightBurst(level(), to.x, to.y, to.z, 14);
            }
            Util.sound(level(), point.x, point.y, point.z, ModSounds.LASER.get(), 1.8f, 1.1f);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private void beam(int i, Vec3 to, double thick) {
        Vec3 from = lensPos(getSeed(), Math.floorMod(i, 3), tickCount - 1);
        pushFx(1, from.x, from.y, from.z, to.x - point.x, to.y - point.y, to.z - point.z, thick);
        Util.sound(level(), to.x, to.y, to.z, ModSounds.LASER.get(), 0.7f, 1.2f + (i % 5) * 0.05f);
    }

    /** Enemies inside the attack radius, lowest health share first. */
    private List<LivingEntity> weakest(LivingEntity owner, double radius) {
        List<LivingEntity> l = new ArrayList<>(Util.enemies(level(), point, radius, owner));
        l.sort(Comparator.comparingDouble(e -> e.getHealth() / Math.max(1f, e.getMaxHealth())));
        return l;
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
