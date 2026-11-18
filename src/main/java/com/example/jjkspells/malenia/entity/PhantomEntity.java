package com.example.jjkspells.malenia.entity;

import com.example.jjkspells.malenia.config.MelConfig;
import com.example.jjkspells.malenia.registry.ModEntities;
import com.example.jjkspells.malenia.registry.ModParticles;
import com.example.jjkspells.malenia.registry.ModSounds;
import com.example.jjkspells.malenia.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.vfx.sprite.SpriteFx;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phantom Blossom - The Seven Shadows. One clock (age = tickCount - 1):
 * <pre>
 *  0..RISE      rise 6 blocks, Aerial Grace
 *  RISE..SHOW   seven crimson phantoms appear in a semicircle
 *  AT[i]        phantom i strikes its own target (three thrusts, two diagonals, a spin, a downward thrust)
 *  DIVE..HIT..LAND  the real Malenia dives at the primary target and lands behind it
 *  END          done
 * </pre>
 */
public class PhantomEntity extends MelEntityBase {
    public static final int N = 7, RISE = 14, SHOW = 26, DIVE = 68, HIT = 77, LAND = 80, END = 110;
    public static final int[] AT = {28, 33, 38, 44, 49, 55, 61};
    /** 0 thrust, 1 diagonal one way, 2 diagonal the other way, 3 spin, 4 downward thrust. */
    public static final int[] TYPE = {0, 0, 0, 1, 2, 3, 4};

    private static final EntityDataAccessor<CompoundTag> DATA_PH = SynchedEntityData.defineId(PhantomEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> DATA_TX = SynchedEntityData.defineId(PhantomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TY = SynchedEntityData.defineId(PhantomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TZ = SynchedEntityData.defineId(PhantomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_PERFECT = SynchedEntityData.defineId(PhantomEntity.class, EntityDataSerializers.BOOLEAN);

    // client only
    public final List<Vec3> trail = new ArrayList<>();
    private CompoundTag cacheSrc;
    private float[][] cache = new float[N][9];

    private UUID primaryId;
    private final double[][] pos = new double[N][3];
    private final double[][] end = new double[N][3];
    private final int[] at = new int[N];
    private final List<double[]> clouds = new ArrayList<>();
    private Vec3 start, top, landing, diveFrom, center;
    private int hits;
    private boolean perfect;

    public PhantomEntity(EntityType<? extends PhantomEntity> type, Level level) {
        super(type, level);
    }

    public static PhantomEntity create(Level level, LivingEntity caster, LivingEntity target) {
        PhantomEntity e = new PhantomEntity(ModEntities.PHANTOM.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.primaryId = target == null ? null : target.getUUID();
        return e;
    }

    public Vec3 getTargetPos() {
        return new Vec3(entityData.get(DATA_TX), entityData.get(DATA_TY), entityData.get(DATA_TZ));
    }

    public boolean isPerfect() {
        return entityData.get(DATA_PERFECT);
    }

    /** Per phantom: x, y, z, end x, end y, end z, attack age (-1 not yet), type, 1 when it exists. */
    public float[][] ph() {
        CompoundTag t = entityData.get(DATA_PH);
        if (t != cacheSrc) {
            cacheSrc = t;
            cache = new float[N][9];
            ListTag l = t.getList("p", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(N, l.size()); i++) {
                CompoundTag c = l.getCompound(i);
                cache[i] = new float[]{c.getFloat("x"), c.getFloat("y"), c.getFloat("z"), c.getFloat("ex"), c.getFloat("ey"), c.getFloat("ez"), c.getInt("t"), c.getInt("k"), 1f};
            }
        }
        return cache;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PH, new CompoundTag());
        entityData.define(DATA_TX, 0f);
        entityData.define(DATA_TY, 0f);
        entityData.define(DATA_TZ, 0f);
        entityData.define(DATA_PERFECT, false);
    }

    private void sync() {
        ListTag l = new ListTag();
        for (int i = 0; i < N; i++) {
            CompoundTag c = new CompoundTag();
            c.putFloat("x", (float) pos[i][0]);
            c.putFloat("y", (float) pos[i][1]);
            c.putFloat("z", (float) pos[i][2]);
            c.putFloat("ex", (float) end[i][0]);
            c.putFloat("ey", (float) end[i][1]);
            c.putFloat("ez", (float) end[i][2]);
            c.putInt("t", at[i]);
            c.putInt("k", TYPE[i]);
            l.add(c);
        }
        CompoundTag root = new CompoundTag();
        root.put("p", l);
        entityData.set(DATA_PH, root);
    }

    private LivingEntity primary() {
        if (primaryId != null && level() instanceof ServerLevel sl && sl.getEntity(primaryId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malenia.client.ClientFx.tickPhantom(this));
            return;
        }
        var c = MelConfig.PHANTOM;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            start = owner.position();
            top = start.add(0, c.riseBlocks.get(), 0);
            LivingEntity t = primary();
            center = t != null ? t.position() : start.add(flat(owner.getLookAngle()).scale(8.0));
            for (int i = 0; i < N; i++) {
                at[i] = -1;
            }
            Util.LOCKED.add(owner.getUUID());
            Util.kbImmune(owner, END + 6);
            Util.FALL_IMMUNE.put(owner.getUUID(), owner.level().getGameTime() + END + 80);
            Util.projReduce(owner, c.projReducePercent.get(), END + 6);
            Util.sound(level(), start.x, start.y, start.z, ModSounds.PHANTOM.get(), 1.4f, 1.0f);
        }
        owner.setSprinting(false);
        LivingEntity pt = primary();
        if (pt != null && age < DIVE) {
            center = pt.position();
        }
        entityData.set(DATA_TX, (float) center.x);
        entityData.set(DATA_TY, (float) center.y);
        entityData.set(DATA_TZ, (float) center.z);
        setPos(owner.getX(), owner.getY(), owner.getZ());
        moveCaster(owner, age);
        if (age == SHOW - 8) {
            spawnPhantoms(owner);
        }
        if (age >= SHOW - 8 && age < DIVE) {
            // the phantoms shimmer
            if (age % 3 == 0) {
                for (int i = 0; i < N; i++) {
                    if (at[i] < 0 || age - at[i] < 6) {
                        Util.fx(level(), ModParticles.SPORE.get(), pos[i][0], pos[i][1] + 1.0, pos[i][2], 2, 0.3, 0.7, 0.3, 0.02);
                    }
                }
            }
        }
        for (int i = 0; i < N; i++) {
            if (age == AT[i]) {
                attack(owner, i);
            }
        }
        // rot clouds left by the later attacks
        if (!clouds.isEmpty()) {
            tickClouds(owner, age);
        }
        if (age == HIT) {
            dive(owner);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private void moveCaster(LivingEntity owner, int age) {
        Vec3 p;
        Vec3 look = center;
        if (age < RISE) {
            p = start.add(0, (top.y - start.y) * smooth(age / (double) RISE), 0);
        } else if (age < DIVE) {
            p = top.add(0, 0.25 * Math.sin(age * 0.25), 0);
        } else if (age < LAND) {
            if (diveFrom == null) {
                diveFrom = owner.position();
                Vec3 dir = flat(center.subtract(diveFrom));
                Vec3 behind = center.add(dir.scale(2.2));
                Double g = Util.findGround(level(), behind.x, behind.y + 3, behind.z);
                landing = new Vec3(behind.x, g != null ? g : center.y, behind.z);
            }
            double u = smooth((age - DIVE + 1) / (double) (LAND - DIVE));
            p = diveFrom.lerp(landing, u);
            look = landing;
        } else {
            p = landing != null ? landing : owner.position();
            look = center;
        }
        float yaw = Util.yawTo(p, new Vec3(look.x, p.y, look.z));
        hover(owner, p, yaw, age >= DIVE && age < LAND ? 50f : 10f);
        if (age >= LAND + 4) {
            owner.setNoGravity(false);
        }
    }

    private void spawnPhantoms(LivingEntity owner) {
        Vec3 here = owner.position();
        Vec3 dir = flat(center.subtract(here));
        double base = Math.atan2(dir.z, dir.x);
        for (int i = 0; i < N; i++) {
            double a = base + Math.toRadians(-90 + 30.0 * i);
            pos[i][0] = here.x + Math.cos(a) * 3.6;
            pos[i][1] = here.y - 0.4;
            pos[i][2] = here.z + Math.sin(a) * 3.6;
            end[i][0] = pos[i][0];
            end[i][1] = pos[i][1];
            end[i][2] = pos[i][2];
        }
        sync();
        for (int i = 0; i < N; i++) {
            Util.fx(level(), ModParticles.FLASH.get(), pos[i][0], pos[i][1] + 1, pos[i][2], 1, 0, 0, 0, 0);
            Util.fx(level(), ModParticles.PETAL.get(), pos[i][0], pos[i][1] + 1, pos[i][2], 8, 0.4, 0.7, 0.4, 0.08);
            SpriteFx.fracture(sv(), new Vec3(pos[i][0], pos[i][1] + 1.2, pos[i][2]), SpriteFx.Palette.CRIMSON, 2.2f);
            SpriteFx.petals(sv(), new Vec3(pos[i][0], pos[i][1] + 1.2, pos[i][2]), SpriteFx.Palette.CRIMSON, 1.4f);
        }
        Util.sound(level(), here.x, here.y, here.z, ModSounds.WINGS.get(), 1.2f, 1.2f);
    }

    /** The enemy a phantom attacks: hostile creatures first, nearest to the phantom; otherwise the primary target. */
    private LivingEntity choose(LivingEntity owner, Vec3 from) {
        double range = MelConfig.PHANTOM.range.get();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        boolean bestHostile = false;
        for (LivingEntity e : Util.enemies(level(), owner.position(), range, owner)) {
            boolean host = e instanceof net.minecraft.world.entity.monster.Enemy;
            double d = e.distanceToSqr(from);
            if ((host && !bestHostile) || (host == bestHostile && d < bd)) {
                best = e;
                bd = d;
                bestHostile = host;
            }
        }
        if (best == null) {
            best = primary();
        }
        return best;
    }

    private void attack(LivingEntity owner, int i) {
        var c = MelConfig.PHANTOM;
        Vec3 from = new Vec3(pos[i][0], pos[i][1] + 1, pos[i][2]);
        LivingEntity t = choose(owner, from);
        at[i] = tickCount - 1;
        if (t == null) {
            end[i][0] = center.x;
            end[i][1] = center.y;
            end[i][2] = center.z;
            sync();
            return;
        }
        end[i][0] = t.getX();
        end[i][1] = t.getY();
        end[i][2] = t.getZ();
        sync();
        Vec3 ec = t.position().add(0, t.getBbHeight() * 0.5, 0);
        int kind = TYPE[i];
        Util.sound(level(), ec.x, ec.y, ec.z, kind == 0 ? ModSounds.STAB.get() : ModSounds.SLASH.get(), 1.1f, 0.9f + 0.05f * i);
        Util.bladeBurst(level(), ec.x, ec.y, ec.z, 6);
        Util.fx(level(), ModParticles.PETAL.get(), ec.x, ec.y, ec.z, 6, 0.3, 0.4, 0.3, 0.12);
        Vec3 aim = flat(ec.subtract(from));
        if (kind == 0) {
            SpriteFx.beam(sv(), from, ec, SpriteFx.Palette.CRIMSON, 0.3f, 4);
            SpriteFx.impact(sv(), ec, SpriteFx.Palette.CRIMSON, 1.5f);
        } else if (kind == 1 || kind == 2) {
            SpriteFx.streak(sv(), ec, aim, kind == 1 ? 38f : -38f, SpriteFx.Palette.CRIMSON, 3.4f);
            SpriteFx.streak(sv(), ec, aim, kind == 1 ? 38f : -38f, SpriteFx.Palette.WHITE, 2.6f);
        } else if (kind == 3) {
            for (int s = 0; s < 3; s++) {
                SpriteFx.streak(sv(), ec, aim, s * 60f, SpriteFx.Palette.CRIMSON, 3.2f);
            }
            SpriteFx.ring(sv(), t.position(), SpriteFx.Palette.ROT, 0.7f);
        } else {
            SpriteFx.beam(sv(), ec.add(0, 5, 0), ec, SpriteFx.Palette.CRIMSON, 0.5f, 5);
            SpriteFx.cracks(sv(), t.position(), SpriteFx.Palette.ROT, 0.6f);
        }
        SpriteFx.shards(sv(), ec, SpriteFx.Palette.CRIMSON, 1.2f);
        Util.hurt(owner, t, c.phantomFlat.get(), 0);
        hits++;
        if (t.isAlive()) {
            Util.wound(owner, t, c.stackSeconds.get(), c.stackThreshold.get(), c.rotDps.get(), c.rotSeconds.get());
        }
        if (kind != 0) {
            clouds.add(new double[]{t.getX(), t.getY(), t.getZ(), tickCount - 1 + 60});
            Util.fx(level(), ModParticles.ROT.get(), t.getX(), t.getY() + 0.3, t.getZ(), 10, 1.0, 0.2, 1.0, 0.03);
            SpriteFx.cracks(sv(), t.position(), SpriteFx.Palette.ROT, f(c.cloudRadius.get()) * 2f / 7f);
            SpriteFx.tendrils(sv(), t.position().add(0, 0.8, 0), SpriteFx.Palette.ROT, 2.2f);
        }
    }

    private void tickClouds(LivingEntity owner, int age) {
        var c = MelConfig.PHANTOM;
        clouds.removeIf(k -> age > k[3]);
        for (double[] k : clouds) {
            if (age % 4 == 0) {
                Util.fx(level(), ModParticles.ROT.get(), k[0], k[1] + 0.3, k[2], 2, c.cloudRadius.get() * 0.5, 0.15, c.cloudRadius.get() * 0.5, 0.02);
                Util.fx(level(), ModParticles.SPORE.get(), k[0], k[1] + 0.6, k[2], 2, c.cloudRadius.get() * 0.5, 0.4, c.cloudRadius.get() * 0.5, 0.03);
            }
            if (age % 20 == 0) {
                for (LivingEntity e : Util.enemies(level(), new Vec3(k[0], k[1], k[2]), c.cloudRadius.get(), owner)) {
                    Util.rot(owner, e, c.cloudDps.get(), 1.5, 0, 0);
                }
            }
        }
    }

    private void dive(LivingEntity owner) {
        var c = MelConfig.PHANTOM;
        perfect = hits >= N;
        entityData.set(DATA_PERFECT, perfect);
        LivingEntity t = primary();
        if (t == null) {
            t = WaterfowlEntity.nearest(level(), owner, owner.position(), 8.0);
        }
        Vec3 at0 = owner.position();
        Util.sound(level(), at0.x, at0.y, at0.z, ModSounds.BOOM.get(), 1.4f, 1.2f);
        Util.ring(level(), ModParticles.SILVER.get(), at0.add(0, 0.4, 0), 24, 0.4, 0.05);
        if (t == null) {
            return;
        }
        double mult = perfect ? 1.0 + c.perfectBonusPercent.get() / 100.0 : 1.0;
        Vec3 ec = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Util.bloomBurst(level(), t.getX(), t.getY(), t.getZ(), 3.0, 22);
        Util.bladeBurst(level(), ec.x, ec.y, ec.z, 14);
        Vec3 dv = flat(ec.subtract(at0));
        SpriteFx.fracture(sv(), ec, SpriteFx.Palette.WHITE, 4.2f);
        SpriteFx.claws(sv(), ec, dv, 25f, SpriteFx.Palette.CRIMSON, 5.0f);
        SpriteFx.streak(sv(), ec, dv, -30f, SpriteFx.Palette.WHITE, 5.0f);
        SpriteFx.shards(sv(), ec, SpriteFx.Palette.CRIMSON, 2.6f);
        SpriteFx.ring(sv(), t.position(), SpriteFx.Palette.CRIMSON, 1.0f);
        if (perfect) {
            SpriteFx.petals(sv(), ec.add(0, 1.0, 0), SpriteFx.Palette.CRIMSON, 5.0f);
            SpriteFx.tendrils(sv(), ec, SpriteFx.Palette.ROT, 3.5f);
        }
        Util.hurt(owner, t, c.diveFlat.get(), c.divePercent.get(), mult, 1.0);
        if (perfect) {
            owner.heal((float) (double) c.perfectHeal.get());
            if (t.isAlive()) {
                Util.slowPct(t, c.perfectSlowPercent.get(), (int) (c.perfectSlowSeconds.get() * 20));
            }
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
