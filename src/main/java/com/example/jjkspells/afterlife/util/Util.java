package com.example.jjkspells.afterlife.util;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModSpells;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared server helpers: targeting, damage, safe teleporting, particles. */
public final class Util {
    /** Casters that cannot move or cast (the ultimate's ceremony). */
    public static final Set<UUID> LOCKED = Collections.synchronizedSet(new HashSet<>());
    /** Casters that take no damage right now. */
    public static final Set<UUID> UNTOUCHABLE = Collections.synchronizedSet(new HashSet<>());
    /** Entity -> game time until which fall damage is ignored. */
    public static final Map<UUID, Long> FALL_IMMUNE = new ConcurrentHashMap<>();

    private Util() {
    }

    // ------------------------------------------------------------------ targeting

    public static boolean isEnemy(Entity caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive() || e.isSpectator()) {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || !AfterlifeConfig.G.hitPlayers.get())) {
            return false;
        }
        if (e instanceof com.example.jjkspells.afterlife.entity.SpiritGhostEntity || e instanceof com.example.jjkspells.afterlife.entity.FuneralEntity) {
            return false;
        }
        return !DamageSources.isFriendlyFireBetween(caster, e);
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Living enemies inside a cylinder (radius r, generous height) around c, nearest first is NOT guaranteed. */
    public static List<LivingEntity> enemies(Level level, Vec3 c, double r, LivingEntity caster) {
        double hh = Math.max(3.0, r * 0.7);
        AABB box = new AABB(c.x - r, c.y - hh, c.z - r, c.x + r, c.y + hh + 2, c.z + r).inflate(1.0);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> isEnemy(caster, x))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double reach = r + e.getBbWidth() / 2.0;
            if (dx * dx + dz * dz <= reach * reach && e.getY() <= c.y + hh + 2 && e.getY() + e.getBbHeight() >= c.y - hh) {
                out.add(e);
            }
        }
        return out;
    }

    /** Point the caster is aiming at: a looked-at entity, a block hit, or the end of the ray. */
    public static Vec3 aimPoint(Level level, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult bhr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double blockDist = bhr.getType() == HitResult.Type.MISS ? range : bhr.getLocation().distanceTo(eye);
        HitResult hr = Utils.raycastForEntity(level, caster, (float) Math.min(range, blockDist), false, 0.3f);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            return new Vec3(le.getX(), le.getY(), le.getZ());
        }
        return bhr.getType() == HitResult.Type.MISS ? end : bhr.getLocation();
    }

    /** Standing surface at/below (x, y, z) or null when there is none within 14 blocks. */
    public static Double findGround(Level l, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y) + 2, Math.floor(z));
        for (int i = 0; i < 16; i++) {
            BlockPos below = p.below();
            if (!l.getBlockState(below).getCollisionShape(l, below).isEmpty() && l.getBlockState(p).getCollisionShape(l, p).isEmpty()) {
                return (double) p.getY();
            }
            p.move(0, -1, 0);
        }
        return null;
    }

    /** Standing surface at/below (x, y, z). */
    public static double groundY(Level l, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y) + 2, Math.floor(z));
        for (int i = 0; i < 14; i++) {
            BlockPos below = p.below();
            if (!l.getBlockState(below).getCollisionShape(l, below).isEmpty() && l.getBlockState(p).getCollisionShape(l, p).isEmpty()) {
                return p.getY();
            }
            p.move(0, -1, 0);
        }
        return y;
    }

    /** Free height above p, up to max blocks (for hovering). */
    public static double clearAbove(Level l, Vec3 p, double max) {
        double h = 0;
        for (double s = 1; s <= max; s += 1) {
            BlockPos a = BlockPos.containing(p.x, p.y + s, p.z), b = BlockPos.containing(p.x, p.y + s + 1, p.z);
            if (!l.getBlockState(a).getCollisionShape(l, a).isEmpty() || !l.getBlockState(b).getCollisionShape(l, b).isEmpty()) {
                break;
            }
            h = s;
        }
        return h;
    }

    // ------------------------------------------------------------------ damage

    public static DamageSource src(LivingEntity caster) {
        return ModSpells.GUIDE.get().getDamageSource(caster, caster);
    }

    /**
     * flat + pct% of the target's max HP, times the config multiplier and extra. The flat part is a normal spell hit; the
     * percent part goes through SpellDamage.percent (ignores damage caps, respects wards / Time Stop). True if the target died.
     */
    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct, double extraMult) {
        if (target.level().isClientSide || !target.isAlive()) {
            return false;
        }
        double m = extraMult * AfterlifeConfig.G.damageMultiplier.get();
        DamageSource src = src(caster);
        float flatAmt = (float) (flat * m * AfterlifeConfig.G.flatScale.get());
        float pctAmt = budget(target, (float) (pct / 100.0 * target.getMaxHealth() * m * AfterlifeConfig.G.percentScale.get()));
        target.invulnerableTime = 0;
        if (flatAmt > 0 && !DamageSources.applyDamage(target, flatAmt, src)) {
            return !target.isAlive();
        }
        if (target.isAlive() && pctAmt > 0) {
            com.example.jjkspells.spells.SpellDamage.percent(target, pctAmt, caster, src);
        }
        return !target.isAlive();
    }

    private static final java.util.Map<UUID, double[]> BUDGET = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Balance: caps the total max-HP-percent damage these spells can do to one target inside a short window, so no
     * combination of them can melt a target in a few seconds. Returns how much of {@code amount} may be dealt.
     */
    public static float budget(LivingEntity target, float amount) {
        double cap = AfterlifeConfig.G.budgetPercent.get() / 100.0 * target.getMaxHealth();
        if (amount <= 0 || cap <= 0) {
            return amount;
        }
        long now = target.level().getGameTime();
        long window = Math.round(AfterlifeConfig.G.budgetSeconds.get() * 20);
        double[] b = BUDGET.computeIfAbsent(target.getUUID(), k -> new double[]{now, 0});
        synchronized (b) {
            if (now - b[0] >= window) {
                b[0] = now;
                b[1] = 0;
            }
            double allowed = Math.max(0, Math.min(amount, cap - b[1]));
            b[1] += allowed;
            return (float) allowed;
        }
    }

    // ------------------------------------------------------------------ movement

    public static void snapTo(LivingEntity e, Vec3 p, float yaw, float pitch) {
        if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, yaw, pitch);
        } else {
            e.moveTo(p.x, p.y, p.z, yaw, pitch);
        }
        e.setYHeadRot(yaw);
        e.setDeltaMovement(Vec3.ZERO);
        e.hurtMarked = true;
        e.fallDistance = 0;
    }

    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(-(to.x - from.x), to.z - from.z) * Mth.RAD_TO_DEG);
    }

    /** A spot around 'around' (at distance r) where the entity fits, or null. */
    public static Vec3 safeNear(Level level, LivingEntity e, Vec3 around, double r) {
        for (int ring = 0; ring < 2; ring++) {
            double rr = r + ring * 0.9;
            double base = level.random.nextDouble() * Math.PI * 2;
            for (int k = 0; k < 10; k++) {
                double a = base + k * Math.PI * 2 / 10;
                Vec3 p = new Vec3(around.x + Math.cos(a) * rr, around.y, around.z + Math.sin(a) * rr);
                AABB box = e.getBoundingBox().move(p.x - e.getX(), p.y - e.getY(), p.z - e.getZ());
                if (level.noCollision(e, box)) {
                    return p;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ fx

    public static void fx(Level level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            int n = Math.max(1, (int) Math.round(count * AfterlifeConfig.G.particleDensity.get()));
            sl.sendParticles(type, x, y, z, n, dx, dy, dz, speed);
        }
    }

    /** One particle with an exact velocity (count 0 trick), not affected by density. */
    public static void fxDir(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(type, x, y, z, 0, vx, vy, vz, 1.0);
        }
    }

    public static void sound(Level level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playSound(null, x, y, z, s, SoundSource.PLAYERS, vol, pitch);
    }
}
