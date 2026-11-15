package com.example.jjkspells.zegion.util;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.registry.ModEffects;
import com.example.jjkspells.zegion.registry.ModParticles;
import com.example.jjkspells.zegion.registry.ModSpells;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

/** Shared server helpers for the Zegion spells: targeting, damage, crowd control, buffs, movement, particles. */
public final class Util {
    /** Casters that cannot move or cast (the long sequences). */
    public static final Set<UUID> LOCKED = Collections.synchronizedSet(new HashSet<>());
    /** Casters that take no damage right now. */
    public static final Set<UUID> UNTOUCHABLE = Collections.synchronizedSet(new HashSet<>());
    /** Casters that cannot be targeted or hurt right now (vanished between strikes). */
    public static final Set<UUID> HIDDEN = Collections.synchronizedSet(new HashSet<>());
    /** Entities whose slowness effects are removed every tick. */
    public static final Map<UUID, Long> SLOW_IMMUNE = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> FALL_IMMUNE = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> HOLD = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> STUN = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> KB_IMMUNE = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> CC_IMMUNE = new ConcurrentHashMap<>();
    /** Kaiser Fracture: game time until which the entity takes extra physical damage. */
    public static final Map<UUID, double[]> FRACTURE = new ConcurrentHashMap<>();
    /** {fraction, until} maps (the strongest one wins). */
    public static final Map<UUID, double[]> REDUCE = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> SLOWED = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> HASTED = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> HEAL_CUT = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> MELEE_BONUS = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> MELEE_FLAT = new ConcurrentHashMap<>();
    /** Extra damage dealt by Zegion spells (caster side). */
    public static final Map<UUID, double[]> OUT_BONUS = new ConcurrentHashMap<>();
    /** Reduced damage dealt (victim of a domain). */
    public static final Map<UUID, double[]> OUT_DOWN = new ConcurrentHashMap<>();
    /** Extra damage taken from Zegion spells. */
    public static final Map<UUID, double[]> VULN = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> ATK_SLOW = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> PROJ_REDUCE = new ConcurrentHashMap<>();
    /** Casters wearing the Obsidian Kaiser carapace. */
    public static final Map<UUID, Carapace> CARAPACE = new ConcurrentHashMap<>();
    /** Casters in Kaiser's Final Form -> their controller. */
    public static final Map<UUID, com.example.jjkspells.zegion.entity.SovereignEntity> FORM = new ConcurrentHashMap<>();
    /** Casters whose kills are being counted -> kills so far. */
    public static final Map<UUID, Integer> KILLS = new ConcurrentHashMap<>();
    public static volatile boolean reflecting;

    public static final class Carapace {
        public int charges;
        public double block = 0.95, prevented;
        public int reflections;
    }

    public static final UUID SLOW_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700002");
    public static final UUID HASTE_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700003");
    public static final UUID ATK_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700004");
    public static final UUID FRACTURE_KB_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700005");
    private static final UUID HOLD_ID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c700001");

    private Util() {
    }

    // ------------------------------------------------------------------ targeting

    public static boolean isEnemy(Entity caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive() || e.isSpectator()) {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || !ZegConfig.G.hitPlayers.get())) {
            return false;
        }
        if (HIDDEN.contains(e.getUUID())) {
            return false;
        }
        return !DamageSources.isFriendlyFireBetween(caster, e);
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Living enemies inside a cylinder (radius r, generous height) around c. */
    public static List<LivingEntity> enemies(Level level, Vec3 c, double r, LivingEntity caster) {
        double hh = Math.max(4.0, r * 0.8);
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

    /** The nearest enemy to a point. */
    public static LivingEntity nearest(Level level, Vec3 c, double r, LivingEntity caster) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : enemies(level, c, r, caster)) {
            double d = e.position().distanceToSqr(c);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** The enemy the caster is looking at (within range), or null. */
    public static LivingEntity aimedEnemy(Level level, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult bhr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double blockDist = bhr.getType() == HitResult.Type.MISS ? range : bhr.getLocation().distanceTo(eye);
        HitResult hr = Utils.raycastForEntity(level, caster, (float) Math.min(range, blockDist), false, 0.4f);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && isEnemy(caster, le)) {
            return le;
        }
        return null;
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

    /** Like aimPoint, but when the ray hits nothing the caster's own position is returned and 'hit' is false. */
    public static Vec3 aimOrSelf(Level level, LivingEntity caster, double range, boolean[] hit) {
        Vec3 eye = caster.getEyePosition();
        Vec3 p = aimPoint(level, caster, range);
        boolean h = p.distanceTo(eye) < range - 0.75;
        if (hit != null && hit.length > 0) {
            hit[0] = h;
        }
        if (!h) {
            return caster.position();
        }
        Double g = findGround(level, p.x, p.y, p.z);
        return g != null ? new Vec3(p.x, g, p.z) : p;
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

    // ------------------------------------------------------------------ damage

    public static DamageSource src(LivingEntity caster) {
        return ModSpells.IMPACT.get().getDamageSource(caster, caster);
    }

    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct) {
        return hurt(caster, target, flat, pct, 1.0, 0.0);
    }

    /**
     * flat + pct% of the target's max HP. The flat part is a normal spell hit except for the trueShare fraction of it
     * (0..1), which goes through SpellDamage.percent (ignores armor, resistances and caps). Returns true if the target died.
     */
    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct, double extraMult, double trueShare) {
        if (target.level().isClientSide || !target.isAlive()) {
            return false;
        }
        double m = extraMult * ZegConfig.G.damageMultiplier.get() * (1.0 + frac(VULN, target)) * (1.0 + frac(OUT_BONUS, caster));
        DamageSource src = src(caster);
        trueShare = Mth.clamp(trueShare, 0.0, 1.0);
        float normal = (float) (flat * m * (1.0 - trueShare));
        double pf = isBoss(target) ? ZegConfig.G.bossPercentFactor.get() : 1.0;
        float trueAmt = (float) (flat * m * trueShare + pct / 100.0 * target.getMaxHealth() * m * pf);
        target.invulnerableTime = 0;
        com.example.jjkspells.spells.Debuffs.hit(caster, target);
        if (normal > 0 && !DamageSources.applyDamage(target, normal, src)) {
            if (!target.isAlive()) {
                return true;
            }
        }
        if (target.isAlive() && trueAmt > 0) {
            com.example.jjkspells.spells.SpellDamage.percent(target, trueAmt, caster, src);
        }
        return !target.isAlive();
    }

    /** Damage that ignores everything (soul damage). */
    public static void soul(LivingEntity caster, LivingEntity target, double amount) {
        if (target.level().isClientSide || !target.isAlive() || amount <= 0) {
            return;
        }
        double m = ZegConfig.G.damageMultiplier.get() * (1.0 + frac(VULN, target)) * (1.0 + frac(OUT_BONUS, caster));
        target.invulnerableTime = 0;
        com.example.jjkspells.spells.SpellDamage.percent(target, (float) (amount * m), caster, src(caster));
    }

    public static double frac(Map<UUID, double[]> map, LivingEntity e) {
        if (map.isEmpty()) {
            return 0.0;
        }
        double[] v = map.get(e.getUUID());
        return v != null && v[1] >= e.level().getGameTime() ? v[0] : 0.0;
    }

    /** Sets a {fraction, until} entry, keeping the stronger one while it lasts. */
    public static void put(Map<UUID, double[]> map, LivingEntity e, double fraction, int ticks) {
        long until = e.level().getGameTime() + ticks;
        map.merge(e.getUUID(), new double[]{fraction, until}, (a, b) -> {
            boolean aLive = a[1] >= e.level().getGameTime();
            if (!aLive) {
                return b;
            }
            return a[0] >= b[0] ? new double[]{a[0], Math.max(a[1], b[1])} : new double[]{b[0], Math.max(a[1], b[1])};
        });
    }

    // ------------------------------------------------------------------ status

    public static void effect(LivingEntity e, net.minecraft.world.effect.MobEffect eff, int ticks, int amp) {
        if (ticks > 0) {
            e.addEffect(new MobEffectInstance(eff, ticks, amp, false, false, true));
        }
    }

    public static boolean ccImmune(LivingEntity e) {
        Long u = CC_IMMUNE.get(e.getUUID());
        return u != null && u >= e.level().getGameTime();
    }

    public static boolean kbImmune(LivingEntity e) {
        Long u = KB_IMMUNE.get(e.getUUID());
        return u != null && u >= e.level().getGameTime();
    }

    public static void ccImmune(LivingEntity e, int ticks) {
        CC_IMMUNE.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
    }

    public static void kbImmune(LivingEntity e, int ticks) {
        KB_IMMUNE.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
    }

    public static void slowImmune(LivingEntity e, int ticks) {
        SLOW_IMMUNE.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
    }

    public static void fallImmune(LivingEntity e, int ticks) {
        FALL_IMMUNE.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
    }

    /** Holds an enemy still; bosses only lose bossSlowPercent of their speed. */
    public static void hold(LivingEntity e, int ticks, double bossSlowPercent) {
        if (e.level().isClientSide || ticks <= 0 || ccImmune(e)) {
            return;
        }
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        double f = isBoss(e) ? Mth.clamp(bossSlowPercent / 100.0, 0.0, 1.0) : 1.0;
        inst.removeModifier(HOLD_ID);
        inst.addTransientModifier(new AttributeModifier(HOLD_ID, "zegion_hold", -f, AttributeModifier.Operation.MULTIPLY_TOTAL));
        long until = e.level().getGameTime() + ticks;
        HOLD.merge(e.getUUID(), until, Math::max);
        if (e instanceof Mob m) {
            m.getNavigation().stop();
        }
    }

    public static void release(LivingEntity e) {
        HOLD.remove(e.getUUID());
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(HOLD_ID);
        }
    }

    public static void stagger(LivingEntity e, int ticks) {
        hold(e, ticks, 40);
    }

    public static void stun(LivingEntity e, int ticks) {
        if (e.level().isClientSide || ticks <= 0 || ccImmune(e)) {
            return;
        }
        hold(e, ticks, ZegConfig.G.bossStunSlowPercent.get());
        if (!isBoss(e)) {
            STUN.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
        }
    }

    public static boolean isStunned(Entity e) {
        Long u = STUN.get(e.getUUID());
        return u != null && u >= e.level().getGameTime();
    }

    public static void reduce(LivingEntity e, double fraction, int ticks) {
        put(REDUCE, e, fraction, ticks);
    }

    public static void clearReduce(LivingEntity e) {
        REDUCE.remove(e.getUUID());
    }

    public static double reduction(LivingEntity e) {
        return frac(REDUCE, e);
    }

    private static void speedChange(Map<UUID, double[]> map, UUID id, String name, LivingEntity e, double amount, int ticks) {
        if (e.level().isClientSide || ticks <= 0) {
            return;
        }
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        long until = e.level().getGameTime() + ticks;
        double[] old = map.get(e.getUUID());
        if (old != null && old[1] >= e.level().getGameTime() && Math.abs(old[0]) > Math.abs(amount)) {
            amount = old[0];
            until = Math.max(until, (long) old[1]);
        }
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        map.put(e.getUUID(), new double[]{amount, until});
    }

    public static void slowPct(LivingEntity e, double percent, int ticks) {
        speedChange(SLOWED, SLOW_ID, "zegion_slow", e, -Mth.clamp(percent / 100.0, 0, 0.95), ticks);
    }

    public static void hastePct(LivingEntity e, double percent, int ticks) {
        speedChange(HASTED, HASTE_ID, "zegion_haste", e, Math.max(0, percent / 100.0), ticks);
    }

    public static void clearSpeed(LivingEntity e) {
        SLOWED.remove(e.getUUID());
        HASTED.remove(e.getUUID());
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(SLOW_ID);
            inst.removeModifier(HASTE_ID);
        }
    }

    /** Attack speed reduced by percent for some ticks (players through the attribute, mobs miss a share of attacks in ZegEvents). */
    public static void atkSlow(LivingEntity e, double percent, int ticks) {
        if (e.level().isClientSide || ticks <= 0) {
            return;
        }
        double frac = Mth.clamp(percent / 100.0, 0, 0.95);
        put(ATK_SLOW, e, frac, ticks);
        AttributeInstance inst = e.getAttribute(Attributes.ATTACK_SPEED);
        if (inst != null) {
            inst.removeModifier(ATK_ID);
            inst.addTransientModifier(new AttributeModifier(ATK_ID, "zegion_atk", -frac, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Kaiser Fracture: extra physical damage, reduced healing, reduced knockback resistance. */
    public static void fracture(LivingEntity e, double seconds) {
        if (e.level().isClientSide || seconds <= 0) {
            return;
        }
        var c = ZegConfig.IMPACT;
        int ticks = (int) (seconds * 20);
        put(FRACTURE, e, c.fractureDamagePercent.get() / 100.0, ticks);
        put(HEAL_CUT, e, c.fractureHealPercent.get() / 100.0, ticks);
        AttributeInstance kb = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(FRACTURE_KB_ID);
            kb.addTransientModifier(new AttributeModifier(FRACTURE_KB_ID, "zegion_fracture", -c.fractureKbPercent.get() / 100.0, AttributeModifier.Operation.ADDITION));
        }
        e.addEffect(new MobEffectInstance(ModEffects.FRACTURE.get(), ticks, 0, false, false, true));
    }

    public static void clearFracture(LivingEntity e) {
        FRACTURE.remove(e.getUUID());
        AttributeInstance kb = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(FRACTURE_KB_ID);
        }
    }

    // ------------------------------------------------------------------ movement

    public static void swing(LivingEntity e) {
        e.swing(InteractionHand.MAIN_HAND, true);
    }

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
        for (int ring = 0; ring < 3; ring++) {
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

    /** Moves a living entity to a place only if it fits there. */
    public static boolean moveTo(LivingEntity e, Vec3 p, float yaw, float pitch) {
        Vec3 d = p.subtract(e.position());
        if (!e.level().noCollision(e, e.getBoundingBox().move(d))) {
            return false;
        }
        snapTo(e, p, yaw, pitch);
        return true;
    }

    /** Pushes an enemy away from a point by about this many blocks (bosses are barely moved). */
    public static void knock(LivingEntity e, Vec3 from, double blocks, double up) {
        if (kbImmune(e)) {
            return;
        }
        Vec3 d = new Vec3(e.getX() - from.x, 0, e.getZ() - from.z);
        if (d.lengthSqr() < 1.0E-4) {
            d = new Vec3(0, 0, 1);
        }
        d = d.normalize();
        double f = isBoss(e) ? 0.25 : 1.0;
        double v = blocks / 4.2 * f * (1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1));
        e.setDeltaMovement(d.x * v, Math.max(e.getDeltaMovement().y, up), d.z * v);
        e.hurtMarked = true;
        e.setOnGround(false);
    }

    public static void launchBlocks(LivingEntity e, double blocks) {
        if (kbImmune(e)) {
            return;
        }
        double f = isBoss(e) ? 0.4 : 1.0;
        Vec3 m = e.getDeltaMovement();
        e.setDeltaMovement(m.x * 0.3, Math.sqrt(0.16 * Math.max(0.0, blocks)) * 1.15 * f, m.z * 0.3);
        e.hurtMarked = true;
        e.setOnGround(false);
    }

    /** Pulls an enemy towards a point with the given speed (blocks per tick); bosses are pulled less. */
    public static void pull(LivingEntity e, Vec3 to, double speed) {
        if (kbImmune(e) || ccImmune(e)) {
            return;
        }
        Vec3 d = to.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
        double len = d.length();
        if (len < 0.6) {
            return;
        }
        double f = isBoss(e) ? ZegConfig.G.bossPullFactor.get() : 1.0;
        Vec3 v = d.scale(speed * f / len);
        Vec3 m = e.getDeltaMovement();
        e.setDeltaMovement(m.x * 0.5 + v.x, m.y * 0.6 + v.y * 0.7, m.z * 0.5 + v.z);
        e.hurtMarked = true;
    }

    /** Hovers the entity at a height above the given ground level (smooth); caller must call unhover when done. */
    public static void hover(LivingEntity e, double y) {
        e.setNoGravity(true);
        Vec3 m = e.getDeltaMovement();
        double dy = Mth.clamp((y - e.getY()) * 0.2, -0.6, 0.6);
        e.setDeltaMovement(m.x * 0.5, dy, m.z * 0.5);
        e.hurtMarked = true;
        e.fallDistance = 0;
    }

    public static void unhover(LivingEntity e) {
        e.setNoGravity(false);
        fallImmune(e, 80);
    }

    /** Removes a share (0..1) of a spell's remaining cooldown from a player. */
    public static void refundFraction(LivingEntity caster, io.redspace.ironsspellbooks.api.spells.AbstractSpell spell, double fraction) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        var cds = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).getPlayerCooldowns();
        var ci = cds.getSpellCooldowns().get(spell.getSpellId());
        if (ci == null) {
            return;
        }
        ci.decrementBy((int) (ci.getCooldownRemaining() * Mth.clamp(fraction, 0, 1)));
        cds.syncToPlayer(player);
    }

    // ------------------------------------------------------------------ fx

    public static void fx(Level level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            int n = Math.max(1, (int) Math.round(count * ZegConfig.G.particleDensity.get()));
            sl.sendParticles(type, x, y, z, n, dx, dy, dz, speed);
        }
    }

    /** One particle with an exact velocity (count 0 trick). */
    public static void fxDir(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(type, x, y, z, 0, vx, vy, vz, 1.0);
        }
    }

    public static void sound(Level level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playSound(null, x, y, z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** A jagged arc of particles from a to b. */
    public static void arc(Level level, ParticleOptions type, Vec3 a, Vec3 b, double jitter) {
        double len = a.distanceTo(b);
        int steps = Math.max(3, (int) (len * 1.6));
        var rnd = level.random;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            Vec3 p = a.lerp(b, f);
            double j = (i == 0 || i == steps) ? 0 : jitter * Math.sin(f * Math.PI);
            fxDir(level, type, p.x + (rnd.nextDouble() - 0.5) * j, p.y + (rnd.nextDouble() - 0.5) * j, p.z + (rnd.nextDouble() - 0.5) * j, 0, 0, 0);
        }
    }

    /** A flat ring of particles expanding outward. */
    public static void ring(Level level, ParticleOptions type, Vec3 c, int n, double speed, double up) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            fxDir(level, type, c.x, c.y, c.z, Math.cos(a) * speed, up, Math.sin(a) * speed);
        }
    }

    /** A burst of violet light, mist and shards. size is roughly the radius in blocks. */
    public static void blast(Level level, double x, double y, double z, double size, int power) {
        fx(level, ModParticles.FLASH.get(), x, y + 0.3, z, 1, 0, 0, 0, 0);
        fx(level, ModParticles.MIST.get(), x, y + 0.4, z, Math.max(3, power / 3), size * 0.35, 0.2, size * 0.35, 0.03);
        fx(level, ModParticles.SPARK.get(), x, y + 0.4, z, Math.max(3, power / 2), size * 0.3, size * 0.15, size * 0.3, 0.3);
        fx(level, ModParticles.SHARD.get(), x, y + 0.5, z, power, size * 0.3, size * 0.15, size * 0.3, 0.3);
        fx(level, ModParticles.MIRROR.get(), x, y + 0.5, z, Math.max(2, power / 3), size * 0.25, size * 0.12, size * 0.25, 0.25);
        fx(level, ModParticles.STAR.get(), x, y + 0.5, z, Math.max(2, power / 4), size * 0.25, size * 0.2, size * 0.25, 0.2);
    }

    /** A tear in space: crack glyphs, sparks and a void blob. */
    public static void tear(Level level, double x, double y, double z, double size) {
        fx(level, ModParticles.CRACK.get(), x, y, z, 4, size, size, size, 0.02);
        fx(level, ModParticles.VOID.get(), x, y, z, 2, size * 0.5, size * 0.5, size * 0.5, 0.01);
        fx(level, ModParticles.SPARK.get(), x, y, z, 5, size, size, size, 0.1);
    }

    public static void mist(Level level, double x, double y, double z, double spread, int n) {
        fx(level, ModParticles.MIST.get(), x, y, z, n, spread, spread * 0.3, spread, 0.02);
    }
}
