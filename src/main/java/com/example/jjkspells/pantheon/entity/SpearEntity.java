package com.example.jjkspells.pantheon.entity;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.registry.ModEntities;
import com.example.jjkspells.pantheon.registry.ModParticles;
import com.example.jjkspells.pantheon.registry.ModSounds;
import com.example.jjkspells.pantheon.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Comet Spear - the flying spear. Moves in sweeps of config.speed blocks per tick so nothing can be skipped; pierces a limited
 * number of enemies (a fully charged one pierces everything), stops at walls and at the end of its range. Position and
 * rotation reach the client through the normal entity sync; the renderer and the trail particles are client-side.
 */
public class SpearEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_F = SynchedEntityData.defineId(SpearEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_W = SynchedEntityData.defineId(SpearEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SpearEntity.class, EntityDataSerializers.INT);

    public static final int ENDING_TICKS = 6;

    private UUID ownerId;
    private Vec3 dir = new Vec3(0, 0, 1);
    private double travelled;
    private double range;
    private double speed;
    private double flat, pct;
    private boolean charged;
    private int pierceLeft;
    private double width;
    private int ending;
    private final Set<UUID> hit = new HashSet<>();

    public SpearEntity(EntityType<? extends SpearEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Throws the spear. charge = ticks the caster channelled. */
    public static void launch(Level level, LivingEntity caster, int charge) {
        var c = PanConfig.COMET;
        SpearEntity s = new SpearEntity(ModEntities.SPEAR.get(), level);
        s.ownerId = caster.getUUID();
        double span = Math.max(1, c.fullChargeTicks.get() - c.minChargeTicks.get());
        double f = Mth.clamp((charge - c.minChargeTicks.get()) / span, 0.0, 1.0);
        s.charged = charge >= c.fullChargeTicks.get();
        s.flat = Mth.lerp(f, c.quickFlat.get(), c.fullFlat.get());
        s.pct = Mth.lerp(f, c.quickPercent.get(), c.fullPercent.get());
        s.width = Mth.lerp(f, c.quickWidth.get(), c.fullWidth.get());
        s.pierceLeft = s.charged ? Integer.MAX_VALUE : c.quickPierce.get();
        s.range = c.range.get();
        s.speed = c.speed.get();
        Vec3 look = caster.getLookAngle();
        s.dir = look.normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
        Vec3 start = caster.getEyePosition().add(right.scale(0.35)).add(0, -0.25, 0).add(look.scale(0.9));
        s.setPos(start.x, start.y, start.z);
        s.setRot((float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG), (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG));
        s.entityData.set(DATA_F, (float) f);
        s.entityData.set(DATA_W, (float) s.width);
        level.addFreshEntity(s);
        Util.swing(caster);
        Util.sound(level, start.x, start.y, start.z, ModSounds.THROW.get(), 1.2f, 0.9f + (float) f * 0.25f);
        Util.fx(level, ModParticles.FLASH.get(), start.x, start.y, start.z, 1, 0, 0, 0, 0);
        Util.fx(level, ModParticles.SPARK.get(), start.x, start.y, start.z, 8, 0.3, 0.3, 0.3, 0.2);
    }

    public float getCharge() {
        return entityData.get(DATA_F);
    }

    public float getWidth() {
        return entityData.get(DATA_W);
    }

    /** 0 while flying, then counts up through the short fade at the end. */
    public int getState() {
        return entityData.get(DATA_STATE);
    }

    public Vec3 getDir() {
        float yaw = getYRot() * Mth.DEG_TO_RAD, pitch = getXRot() * Mth.DEG_TO_RAD;
        double cp = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_F, 0f);
        entityData.define(DATA_W, 0.6f);
        entityData.define(DATA_STATE, 0);
    }

    private LivingEntity owner() {
        if (ownerId != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTrail();
            return;
        }
        if (ending > 0) {
            ending++;
            entityData.set(DATA_STATE, ending);
            if (ending > ENDING_TICKS) {
                discard();
            }
            return;
        }
        LivingEntity owner = owner();
        if (owner == null) {
            discard();
            return;
        }
        Vec3 from = position();
        double step = Math.min(speed, range - travelled);
        Vec3 to = from.add(dir.scale(step));
        // walls
        BlockHitResult bhr = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = bhr.getType() != HitResult.Type.MISS;
        if (blocked) {
            to = bhr.getLocation();
        }
        // enemies along the segment, nearest first
        double half = width / 2.0 + 0.3;
        AABB box = new AABB(from, to).inflate(half + 0.5);
        List<double[]> order = new ArrayList<>();
        List<LivingEntity> cand = new ArrayList<>();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, x -> Util.isEnemy(owner, x) && !hit.contains(x.getUUID()))) {
            AABB bb = e.getBoundingBox().inflate(half);
            double d;
            if (bb.contains(from)) {
                d = 0;
            } else {
                var cl = bb.clip(from, to);
                if (cl.isEmpty()) {
                    continue;
                }
                d = cl.get().distanceTo(from);
            }
            cand.add(e);
            order.add(new double[]{d, cand.size() - 1});
        }
        order.sort((a, b) -> Double.compare(a[0], b[0]));
        boolean stop = false;
        for (double[] o : order) {
            LivingEntity e = cand.get((int) o[1]);
            if (!hit.add(e.getUUID())) {
                continue;
            }
            hitEnemy(owner, e);
            pierceLeft--;
            if (pierceLeft <= 0) {
                to = from.add(dir.scale(Math.max(0.3, o[0])));
                stop = true;
                break;
            }
        }
        // the visible streak of the flight
        int n = Math.max(2, (int) (from.distanceTo(to) * 1.5));
        for (int i = 0; i < n; i++) {
            Vec3 p = from.lerp(to, i / (double) n);
            if (i % 2 == 0) {
                Util.fxDir(level(), ModParticles.SPARK.get(), p.x, p.y, p.z, 0, 0, 0);
            }
        }
        travelled += from.distanceTo(to);
        setPos(to.x, to.y, to.z);
        if (blocked || stop || travelled >= range - 1.0E-3) {
            end(owner, to, blocked ? bhr.getDirection().getNormal() : null);
        }
    }

    private void hitEnemy(LivingEntity owner, LivingEntity e) {
        var c = PanConfig.COMET;
        double mult = 1.0;
        if (e.getHealth() < e.getMaxHealth() * c.executeBelowPercent.get() / 100.0) {
            mult += c.executeBonusPercent.get() / 100.0;
        }
        Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
        Util.hurt(owner, e, flat, pct, mult, 1.0);
        if (e.isAlive()) {
            Util.hold(e, 6, 40);
            if (charged) {
                Util.slow(e, c.slowSeconds.get(), c.slowAmp.get());
            }
        }
        Util.sound(level(), p.x, p.y, p.z, ModSounds.PIERCE.get(), 1.0f, 0.95f + random.nextFloat() * 0.2f);
        Util.fx(level(), ModParticles.FLASH.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.FLAME.get(), p.x, p.y, p.z, 10, 0.3, 0.4, 0.3, 0.06);
        Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y, p.z, 14, 0.3, 0.4, 0.3, 0.3);
    }

    private void end(LivingEntity owner, Vec3 at, net.minecraft.core.Vec3i wall) {
        var c = PanConfig.COMET;
        ending = 1;
        entityData.set(DATA_STATE, 1);
        if (charged) {
            // the secondary shockwave where the spear stops
            for (LivingEntity e : Util.enemies(level(), at, c.shockRadius.get(), owner)) {
                Util.hurt(owner, e, c.shockFlat.get(), 0);
            }
            Util.blast(level(), at.x, at.y, at.z, c.shockRadius.get() * 1.2, 24);
            Util.fxDir(level(), ModParticles.RING.get(), at.x, at.y, at.z, 0, 0, 0);
            Util.ring(level(), ModParticles.SPARK.get(), at, 24, 0.35, 0.04);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 1.1f, 1.3f);
        } else {
            Util.fx(level(), ModParticles.FLASH.get(), at.x, at.y, at.z, 1, 0, 0, 0, 0);
            Util.fx(level(), ModParticles.SPARK.get(), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.25);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.CLANG.get(), 0.8f, 1.4f);
        }
    }

    /** Trail drawn on the client between this tick and the last. */
    private void clientTrail() {
        if (getState() > 0) {
            return;
        }
        Vec3 now = position(), old = new Vec3(xo, yo, zo);
        int n = Math.max(1, (int) (now.distanceTo(old) * 1.2));
        for (int i = 0; i < n; i++) {
            Vec3 p = old.lerp(now, i / (double) n);
            level().addParticle(ModParticles.SPARK.get(), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04);
            if (random.nextInt(3) == 0) {
                level().addParticle(ModParticles.FLAME.get(), p.x + (random.nextDouble() - 0.5) * 0.3, p.y + (random.nextDouble() - 0.5) * 0.3, p.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
            }
        }
    }

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
