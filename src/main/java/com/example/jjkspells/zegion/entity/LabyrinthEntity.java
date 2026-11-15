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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mephisto's Labyrinth - The Endless Dimension. Sits at the centre of the labyrinth; one clock (age = tickCount - 1):
 * <pre>
 *  0..GROW        the black cube turns above Zegion's palm, then the labyrinth unfolds; at GROW the capture hits
 *  GROW..WAVES    the mirrored walls shift and the prisoners are slowed
 *  WAVES..CONVERGE the cutting waves (deterministic lines, see waveLine) sweep through
 *  CONVERGE..JUDGE the corridors close in, prisoners are pulled to the centre
 *  JUDGE          Mephisto's Judgment
 *  END
 * </pre>
 */
public class LabyrinthEntity extends ZegEntityBase {
    public static final int CUBE = 20, GROW = 60, WAVES = 100, CONVERGE = 160, JUDGE = 200, END = 224, WAVE_LIFE = 16;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(LabyrinthEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(LabyrinthEntity.class, EntityDataSerializers.FLOAT);

    private final Set<UUID> captured = new HashSet<>();
    private final Map<UUID, Integer> stacks = new HashMap<>();
    private final Map<Integer, Set<UUID>> waveHits = new HashMap<>();

    public LabyrinthEntity(EntityType<? extends LabyrinthEntity> type, Level level) {
        super(type, level);
    }

    public static LabyrinthEntity create(Level level, LivingEntity caster) {
        var c = ZegConfig.LABYRINTH;
        LabyrinthEntity e = new LabyrinthEntity(ModEntities.LABYRINTH.get(), level);
        e.setOwner(caster);
        boolean[] hit = new boolean[1];
        Vec3 p = Util.aimOrSelf(level, caster, c.range.get(), hit);
        e.setPos(p.x, p.y, p.z);
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        e.entityData.set(DATA_RADIUS, c.radius.get().floatValue());
        return e;
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_RADIUS, 35f);
    }

    private static double hash(int seed, int k, int salt) {
        long h = seed * 73856093L ^ k * 19349663L ^ salt * 83492791L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        h ^= (h >>> 15);
        return ((h & 0xFFFFFFL) / (double) 0x1000000L);
    }

    public static int waveStart(int k, int waves) {
        return WAVES + (int) Math.round(k * (CONVERGE - WAVES - WAVE_LIFE) / (double) Math.max(1, waves - 1));
    }

    /** Wave k travels along a chord of the labyrinth: {x0, z0, x1, z1} relative to the centre. Shared with the renderer. */
    public static double[] waveLine(int seed, int k, double R) {
        double a = hash(seed, k, 1) * Math.PI * 2;
        double off = (hash(seed, k, 2) - 0.5) * R * 0.9;
        double dx = Math.cos(a), dz = Math.sin(a);
        double nx = -dz, nz = dx;
        double len = R * 0.95;
        return new double[]{nx * off - dx * len, nz * off - dz * len, nx * off + dx * len, nz * off + dz * len};
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickLabyrinth(this));
            return;
        }
        var c = ZegConfig.LABYRINTH;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        Vec3 at = position();
        double R = c.radius.get();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.HUM.get(), 1.6f, 0.8f);
        }
        if (age < GROW) {
            Vec3 hand = owner.position().add(0, 2.4, 0);
            if (age % 2 == 0) {
                Util.fx(level(), ModParticles.RUNE.get(), hand.x, hand.y, hand.z, 1, 0.5, 0.4, 0.5, 0.02);
                Util.fx(level(), ModParticles.SPARK.get(), hand.x, hand.y, hand.z, 2, 0.4, 0.4, 0.4, 0.05);
            }
            if (age == CUBE) {
                Util.sound(level(), at.x, at.y, at.z, ModSounds.CHARGE.get(), 2.0f, 0.8f);
            }
            if (age > CUBE && age % 3 == 0) {
                double k = (age - CUBE) / (double) (GROW - CUBE);
                double r = R * k;
                for (int i = 0; i < 10; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    Util.fxDir(level(), ModParticles.MIRROR.get(), at.x + Math.cos(a) * r, at.y + 0.5 + random.nextDouble() * 10, at.z + Math.sin(a) * r, 0, 0.02, 0);
                }
            }
        }
        if (age == 40) {
            Util.LOCKED.remove(owner.getUUID());
        }
        if (age >= CUBE && age < JUDGE + 4) {
            if (age % 5 == 0) {
                Util.reduce(owner, c.selfReductionPercent.get() / 100.0, 10);
                Util.put(Util.OUT_BONUS, owner, c.selfDamagePercent.get() / 100.0, 10);
            }
            Util.kbImmune(owner, 4);
        }
        if (age == GROW) {
            capture(owner, at, R);
        }
        if (age >= GROW && age < JUDGE) {
            upkeep(owner, at, R, age);
        }
        if (age >= WAVES && age < CONVERGE) {
            waves(owner, at, R, age);
        }
        if (age >= CONVERGE - 8 && age < JUDGE && age % 2 == 0) {
            double k = (age - CONVERGE) / (double) (JUDGE - CONVERGE);
            double r = R * (1.0 - Math.max(0, k) * 0.8);
            for (int i = 0; i < 12; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Util.fxDir(level(), ModParticles.MIRROR.get(), at.x + Math.cos(a) * r, at.y + 0.5 + random.nextDouble() * 8, at.z + Math.sin(a) * r, -Math.cos(a) * 0.4, 0.0, -Math.sin(a) * 0.4);
            }
        }
        if (age == JUDGE - 10) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), at.x, at.y, at.z, ModSounds.TOLL.get(), 2.2f, 0.6f);
        }
        if (age == JUDGE) {
            judgment(owner, at, R);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private void capture(LivingEntity owner, Vec3 at, double R) {
        var c = ZegConfig.LABYRINTH;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 2.2f, 0.7f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.CHIME.get(), 1.8f, 0.5f);
        Util.blast(level(), at.x, at.y + 2, at.z, 6, 70);
        Util.ring(level(), ModParticles.MIRROR.get(), at.add(0, 0.6, 0), 40, 1.2, 0.2);
        for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
            captured.add(e.getUUID());
            Util.hurt(owner, e, c.openFlat.get(), c.openPercent.get());
        }
    }

    /** Dimensional Imprisonment: prisoners cannot walk out, everybody inside is slowed and dizzy. */
    private void upkeep(LivingEntity owner, Vec3 at, double R, int age) {
        var c = ZegConfig.LABYRINTH;
        if (age % 10 == 0) {
            for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
                double slow = Util.isBoss(e) ? c.bossSlowPercent.get() : c.slowPercent.get();
                Util.slowPct(e, slow, 16);
                e.addEffect(new MobEffectInstance(ModEffects.IMPRISON.get(), 24, 0, false, false, true));
                if (age >= WAVES - 20 && age < CONVERGE && e instanceof net.minecraft.world.entity.player.Player) {
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 50, 0, false, false, false));
                }
            }
        }
        if (captured.isEmpty()) {
            return;
        }
        for (UUID id : captured) {
            if (!(((net.minecraft.server.level.ServerLevel) level()).getEntity(id) instanceof LivingEntity e) || !e.isAlive() || Util.isBoss(e)) {
                continue;
            }
            double dx = e.getX() - at.x, dz = e.getZ() - at.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            double lim = R - 1.0;
            if (d > lim) {
                double f = lim / d;
                e.teleportTo(at.x + dx * f, e.getY(), at.z + dz * f);
                e.setDeltaMovement(-dx / d * 0.2, e.getDeltaMovement().y, -dz / d * 0.2);
                e.hurtMarked = true;
            }
        }
        if (age >= CONVERGE) {
            double k = (age - CONVERGE) / (double) (JUDGE - CONVERGE);
            for (LivingEntity e : Util.enemies(level(), at, R + 2, owner)) {
                Util.pull(e, at.add(0, 1.5, 0), 0.10 + 0.22 * k);
            }
        }
    }

    private void waves(LivingEntity owner, Vec3 at, double R, int age) {
        var c = ZegConfig.LABYRINTH;
        int n = c.waves.get();
        for (int k = 0; k < n; k++) {
            int st = waveStart(k, n);
            if (age == st) {
                Util.sound(level(), at.x, at.y, at.z, ModSounds.SLASH.get(), 2.0f, 0.6f + random.nextFloat() * 0.3f);
                Util.sound(level(), at.x, at.y, at.z, ModSounds.RIFT.get(), 1.4f, 0.7f);
            }
            if (age < st || age > st + WAVE_LIFE) {
                continue;
            }
            double[] l = waveLine(getSeed(), k, R);
            double f = (age - st) / (double) WAVE_LIFE;
            double x = at.x + l[0] + (l[2] - l[0]) * f, z = at.z + l[1] + (l[3] - l[1]) * f;
            Vec3 p = new Vec3(x, at.y, z);
            double dxl = l[2] - l[0], dzl = l[3] - l[1];
            double len = Math.sqrt(dxl * dxl + dzl * dzl);
            double px = -dzl / len, pz = dxl / len;
            // the blade is a wall that sweeps along the chord: it is long across the chord direction
            for (int s = -3; s <= 3; s++) {
                Util.fxDir(level(), ModParticles.SPARK.get(), x + px * s * 3, at.y + 1.5 + random.nextDouble() * 6, z + pz * s * 3, dxl / len * 0.3, 0, dzl / len * 0.3);
            }
            Set<UUID> done = waveHits.computeIfAbsent(k, i -> new HashSet<>());
            for (LivingEntity e : Util.enemies(level(), at, R + 2, owner)) {
                double rx = e.getX() - x, rz = e.getZ() - z;
                double along = rx * dxl / len + rz * dzl / len;
                double across = Math.abs(rx * px + rz * pz);
                if (Math.abs(along) < 2.2 && across < 14 && done.add(e.getUUID())) {
                    Vec3 ep = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    Util.hurt(owner, e, c.waveDamage.get(), 0);
                    Util.fx(level(), ModParticles.FLASH.get(), ep.x, ep.y, ep.z, 1, 0, 0, 0, 0);
                    Util.fx(level(), ModParticles.SHARD.get(), ep.x, ep.y, ep.z, 6, 0.3, 0.4, 0.3, 0.25);
                    if (e.isAlive()) {
                        int s = stacks.merge(e.getUUID(), 1, Integer::sum);
                        if (s >= c.fractureStacks.get()) {
                            stacks.remove(e.getUUID());
                            Util.soul(owner, e, c.soulDamage.get());
                            Util.fx(level(), ModParticles.RUNE.get(), ep.x, ep.y, ep.z, 8, 0.5, 0.6, 0.5, 0.05);
                        }
                    } else {
                        stacks.remove(e.getUUID());
                    }
                }
            }
        }
    }

    private void judgment(LivingEntity owner, Vec3 at, double R) {
        var c = ZegConfig.LABYRINTH;
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 3.0f, 0.6f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 2.6f, 0.5f);
        Util.blast(level(), at.x, at.y + 3, at.z, R * 0.5, 140);
        Util.ring(level(), ModParticles.MIRROR.get(), at.add(0, 1, 0), 48, 1.3, 0.3);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.5, 0), 40, 1.5, 0.05);
        double ig = Math.max(c.ignoreArmorPercent.get(), c.ignoreMagicPercent.get()) / 100.0;
        for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
            Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get(), 1.0, ig);
            if (e.isAlive()) {
                Util.launchBlocks(e, 2.5);
            }
        }
        captured.clear();
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
        captured.clear();
    }
}
