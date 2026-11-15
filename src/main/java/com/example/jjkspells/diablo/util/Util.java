package com.example.jjkspells.diablo.util;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.registry.ModSpells;
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
import com.example.jjkspells.diablo.registry.ModEffects;
import com.example.jjkspells.diablo.registry.ModParticles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared server helpers: targeting, damage, stuns, damage reduction, speed changes, burning, safe movement, particles. */
public final class Util {
    /** Casters that cannot move or cast (the long sequences). */
    public static final Set<UUID> LOCKED = Collections.synchronizedSet(new HashSet<>());
    /** Casters that take no damage right now. */
    public static final Set<UUID> UNTOUCHABLE = Collections.synchronizedSet(new HashSet<>());
    /** Entity -> game time until which fall damage is ignored. */
    public static final Map<UUID, Long> FALL_IMMUNE = new ConcurrentHashMap<>();
    /** Entity -> game time until which it is held still. */
    public static final Map<UUID, Long> HOLD = new ConcurrentHashMap<>();

    /** Entity -> {fraction, game time until} of incoming damage reduction (the strongest one wins). */
    public static final Map<UUID, double[]> REDUCE = new ConcurrentHashMap<>();
    /** Entity -> {speed change as a signed fraction, game time until}; kept apart for debuffs and buffs. */
    public static final Map<UUID, double[]> SLOWED = new ConcurrentHashMap<>();
    public static final Map<UUID, double[]> HASTED = new ConcurrentHashMap<>();
    /** Entity -> {fraction, game time until} of reduced healing. */
    public static final Map<UUID, double[]> HEAL_CUT = new ConcurrentHashMap<>();
    /** Entity -> {fraction, game time until} of extra melee damage dealt. */
    public static final Map<UUID, double[]> MELEE_BONUS = new ConcurrentHashMap<>();
    /** Entity -> game time until which it cannot be knocked back. */
    public static final Map<UUID, Long> KB_IMMUNE = new ConcurrentHashMap<>();
    /** Casters that cannot be targeted or hurt right now (hidden in the sky). */
    public static final Set<UUID> HIDDEN = Collections.synchronizedSet(new HashSet<>());
    /** Entity -> {fraction, game time until}: extra spell damage dealt by the caster. */
    public static final Map<UUID, double[]> SPELLPOW = new ConcurrentHashMap<>();
    /** Entity -> {fraction, game time until}: reduced outgoing damage (void instability, gluttonous suppression). */
    public static final Map<UUID, double[]> OUTDMG = new ConcurrentHashMap<>();
    /** Entity -> {fraction, game time until}: attack speed reduction (electrical paralysis). */
    public static final Map<UUID, double[]> ATK_SLOW = new ConcurrentHashMap<>();
    /** Casters inside an Absolute Defense barrier. */
    public static final Map<UUID, Barrier> BARRIER = new ConcurrentHashMap<>();
    /** Entity -> game time until which stuns and roots do nothing. */
    public static final Map<UUID, Long> CC_IMMUNE = new ConcurrentHashMap<>();
    /** Entity -> {fraction, game time until}: extra damage taken from every source (wounds, marks, domains). */
    public static final Map<UUID, double[]> VULN = new ConcurrentHashMap<>();
    /** Entity -> burning state (Eternal Hellfire): damage per second until a game time, dealt for a caster. */
    public static final Map<UUID, Dot> DOT = new ConcurrentHashMap<>();
    /** Entity -> {caster most significant bits as id marker..}: mobs that cannot target a caster for some time. */
    public static final Map<UUID, Fear> FEAR = new ConcurrentHashMap<>();
    /** Set while a reflected bolt is dealt, so reflections never reflect. */
    public static volatile boolean reflecting;

    /** Damage over time on behalf of a caster. */
    public static final class Dot {
        public double dps;
        public long until, nextAt;
        public UUID caster;
    }

    /** A mob that is afraid of a caster. */
    public static final class Fear {
        public UUID caster;
        public long until;
    }

    /** State of one Absolute Negation barrier. */
    public static final class Barrier {
        public int charges, reflects;
        public double prevented;
        public long until;
    }

    public static final UUID SLOW_ID = UUID.fromString("d1a8b10c-7b3e-4c1d-a6f2-9d0e40000002");
    public static final UUID HASTE_ID = UUID.fromString("d1a8b10c-7b3e-4c1d-a6f2-9d0e40000003");
    public static final UUID ATK_ID = UUID.fromString("d1a8b10c-7b3e-4c1d-a6f2-9d0e40000004");
    public static final UUID ARMOR_ID = UUID.fromString("d1a8b10c-7b3e-4c1d-a6f2-9d0e40000005");
    private static final UUID HOLD_ID = UUID.fromString("d1a8b10c-7b3e-4c1d-a6f2-9d0e40000001");

    private Util() {
    }

    // ------------------------------------------------------------------ targeting

    public static boolean isEnemy(Entity caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive() || e.isSpectator()) {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || !DiaConfig.G.hitPlayers.get())) {
            return false;
        }
        return !DamageSources.isFriendlyFireBetween(caster, e);
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Living enemies inside a cylinder (radius r, generous height) around c. */
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

    // ------------------------------------------------------------------ damage

    public static DamageSource src(LivingEntity caster) {
        return ModSpells.NETHER.get().getDamageSource(caster, caster);
    }

    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct) {
        return hurt(caster, target, flat, pct, 1.0, 1.0, 0.0);
    }

    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct, double extraMult, double pctFactor) {
        return hurt(caster, target, flat, pct, extraMult, pctFactor, 0.0);
    }

    /**
     * flat + pct% of the target max HP, times the config multiplier and extra. The flat part is a normal spell hit; the
     * percent part goes through SpellDamage.percent (ignores damage caps, respects wards / Time Stop). pctFactor scales only
     * the percent part (bosses take less of it). armorIgnore (0..1) lowers the target armor for the duration of the hit.
     * True if the target died.
     */
    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct, double extraMult, double pctFactor, double armorIgnore) {
        return hurt(caster, target, flat, pct, extraMult, pctFactor, armorIgnore, 0.0);
    }

    /**
     * flat + pct% of the target max HP, times the config multiplier, extra and the caster spell bonus. armorIgnore (0..1) lowers
     * the target armor for the duration of the hit; reduceIgnore (0..1) lets that share of the target damage reduction through.
     * True if the target died.
     */
    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct, double extraMult, double pctFactor, double armorIgnore, double reduceIgnore) {
        if (target.level().isClientSide || !target.isAlive()) {
            return false;
        }
        double m = extraMult * DiaConfig.G.damageMultiplier.get() * (1.0 + spellBonus(caster));
        double red = reduction(target);
        if (red > 0 && reduceIgnore > 0 && red < 0.99) {
            m *= (1.0 - red * (1.0 - Mth.clamp(reduceIgnore, 0, 1))) / (1.0 - red);
        }
        DamageSource src = src(caster);
        float flatAmt = (float) (flat * m);
        float pctAmt = (float) (pct / 100.0 * target.getMaxHealth() * m * pctFactor);
        target.invulnerableTime = 0;
        com.example.jjkspells.spells.Debuffs.hit(caster, target);
        AttributeInstance armor = armorIgnore > 0 ? target.getAttribute(Attributes.ARMOR) : null;
        if (armor != null) {
            armor.removeModifier(ARMOR_ID);
            armor.addTransientModifier(new AttributeModifier(ARMOR_ID, "diablo_bypass", -Mth.clamp(armorIgnore, 0, 1), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        try {
            if (flatAmt > 0 && !DamageSources.applyDamage(target, flatAmt, src)) {
                return !target.isAlive();
            }
            if (target.isAlive() && pctAmt > 0) {
                com.example.jjkspells.spells.SpellDamage.percent(target, pctAmt, caster, src);
            }
        } finally {
            if (armor != null) {
                armor.removeModifier(ARMOR_ID);
            }
        }
        return !target.isAlive();
    }

    // ------------------------------------------------------------------ status / holds

    public static void effect(LivingEntity e, net.minecraft.world.effect.MobEffect eff, double seconds, int amp) {
        if (seconds > 0) {
            e.addEffect(new MobEffectInstance(eff, (int) Math.round(seconds * 20), amp, false, true, true));
        }
    }

    public static void slow(LivingEntity e, double seconds, int amp) {
        effect(e, MobEffects.MOVEMENT_SLOWDOWN, seconds, amp);
    }

    public static void resist(LivingEntity e, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, amp, false, false, true));
    }

    /**
     * Holds an enemy still for some ticks. Ordinary enemies cannot move at all; bosses only lose bossSlowPercent of their
     * speed. The events class releases the hold when it ends.
     */
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
        inst.addTransientModifier(new AttributeModifier(HOLD_ID, "diablo_hold", -f, AttributeModifier.Operation.MULTIPLY_TOTAL));
        long until = e.level().getGameTime() + ticks;
        Long old = HOLD.get(e.getUUID());
        HOLD.put(e.getUUID(), old == null ? until : Math.max(old, until));
        if (e instanceof Mob m) {
            m.getNavigation().stop();
        }
    }

    public static boolean isHeld(LivingEntity e) {
        return HOLD.containsKey(e.getUUID());
    }

    public static void release(LivingEntity e) {
        HOLD.remove(e.getUUID());
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(HOLD_ID);
        }
    }

    public static void swing(LivingEntity e) {
        e.swing(InteractionHand.MAIN_HAND, true);
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


    // ------------------------------------------------------------------ stun / reduce / speed / burn

    /** Incoming damage reduction (fraction 0..1) for some ticks. */
    public static void reduce(LivingEntity e, double fraction, int ticks) {
        long until = e.level().getGameTime() + ticks;
        REDUCE.merge(e.getUUID(), new double[]{fraction, until}, (a, b) -> a[0] >= b[0] ? new double[]{a[0], Math.max(a[1], b[1])} : new double[]{b[0], Math.max(a[1], b[1])});
    }

    public static void clearReduce(LivingEntity e) {
        REDUCE.remove(e.getUUID());
    }

    public static double reduction(LivingEntity e) {
        double[] r = REDUCE.get(e.getUUID());
        return r != null && r[1] >= e.level().getGameTime() ? r[0] : 0.0;
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

    /** Movement speed reduced by percent for some ticks (bosses included). */
    public static void slowPct(LivingEntity e, double percent, int ticks) {
        speedChange(SLOWED, SLOW_ID, "diablo_slow", e, -Mth.clamp(percent / 100.0, 0, 0.95), ticks);
    }

    /** Movement speed raised by percent for some ticks. */
    public static void hastePct(LivingEntity e, double percent, int ticks) {
        speedChange(HASTED, HASTE_ID, "diablo_haste", e, Math.max(0, percent / 100.0), ticks);
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

    /** Launches an enemy so that it rises about this many blocks. */
    public static void launchBlocks(LivingEntity e, double blocks) {
        launch(e, Math.sqrt(0.16 * Math.max(0.0, blocks)) * 1.15);
    }

    /** Launches an enemy upward. */
    public static void launch(LivingEntity e, double speed) {
        if (kbImmune(e)) {
            return;
        }
        double f = isBoss(e) ? 0.4 : 1.0;
        Vec3 m = e.getDeltaMovement();
        e.setDeltaMovement(m.x * 0.3, speed * f, m.z * 0.3);
        e.hurtMarked = true;
        e.setOnGround(false);
    }

    /** Removes a share of a spell's remaining cooldown from a player (Iron's API). */
    public static void refund(LivingEntity caster, io.redspace.ironsspellbooks.api.spells.AbstractSpell spell, int ticks) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        var cds = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).getPlayerCooldowns();
        var ci = cds.getSpellCooldowns().get(spell.getSpellId());
        if (ci == null) {
            return;
        }
        ci.decrementBy(Math.min(ci.getCooldownRemaining(), ticks));
        cds.syncToPlayer(player);
    }

    /** Moves a living entity to a place, keeping (or setting) its rotation, without ever pushing it into a wall. */
    public static boolean moveTo(LivingEntity e, Vec3 p, float yaw, float pitch) {
        Vec3 d = p.subtract(e.position());
        var box = e.getBoundingBox().move(d);
        if (!e.level().noCollision(e, box)) {
            return false;
        }
        snapTo(e, p, yaw, pitch);
        return true;
    }

    // ------------------------------------------------------------------ states

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

    /** A brief stagger: held for a few ticks without the stun marker (bosses only lose a share of their speed). */
    public static void stagger(LivingEntity e, int ticks) {
        hold(e, ticks, 40);
    }

    /** Removes a share (0..1) of a spell's remaining cooldown. */
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

    // ------------------------------------------------------------------ Diablo rules

    /** Extra spell damage (percent) for the caster for some ticks; the strongest one wins, they never stack. */
    public static void spellBonus(LivingEntity e, double percent, int ticks) {
        long until = e.level().getGameTime() + ticks;
        double frac = Math.max(0, percent / 100.0);
        SPELLPOW.merge(e.getUUID(), new double[]{frac, until}, (a, b) -> a[0] >= b[0] ? new double[]{a[0], Math.max(a[1], b[1])} : new double[]{b[0], Math.max(a[1], b[1])});
    }

    public static double spellBonus(LivingEntity e) {
        if (SPELLPOW.isEmpty()) {
            return 0.0;
        }
        double[] b = SPELLPOW.get(e.getUUID());
        return b != null && b[1] >= e.level().getGameTime() ? b[0] : 0.0;
    }

    /** The enemy deals less damage for some ticks. */
    public static void outDamage(LivingEntity e, double percent, int ticks) {
        if (e.level().isClientSide || ticks <= 0) {
            return;
        }
        long until = e.level().getGameTime() + ticks;
        double frac = Mth.clamp(percent / 100.0, 0, 0.95);
        OUTDMG.merge(e.getUUID(), new double[]{frac, until}, (a, b) -> a[0] >= b[0] ? new double[]{a[0], Math.max(a[1], b[1])} : new double[]{b[0], Math.max(a[1], b[1])});
    }

    /** Attack speed reduced by percent for some ticks (players through the attribute; mobs lose a share of their attacks in DiaEvents). */
    public static void atkSlow(LivingEntity e, double percent, int ticks) {
        if (e.level().isClientSide || ticks <= 0) {
            return;
        }
        long until = e.level().getGameTime() + ticks;
        double frac = Mth.clamp(percent / 100.0, 0, 0.95);
        double[] old = ATK_SLOW.get(e.getUUID());
        if (old != null && old[1] >= e.level().getGameTime() && old[0] > frac) {
            frac = old[0];
            until = Math.max(until, (long) old[1]);
        }
        ATK_SLOW.put(e.getUUID(), new double[]{frac, until});
        AttributeInstance inst = e.getAttribute(Attributes.ATTACK_SPEED);
        if (inst != null) {
            inst.removeModifier(ATK_ID);
            inst.addTransientModifier(new AttributeModifier(ATK_ID, "diablo_paralysis", -frac, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        e.addEffect(new MobEffectInstance(ModEffects.SUPPRESS.get(), ticks, 0, false, false, true));
    }

    /** Pulls an enemy towards a point (bosses are barely moved, knockback immunity is respected). */
    public static void pull(LivingEntity e, Vec3 to, double strength, double stopDist) {
        if (kbImmune(e) || ccImmune(e)) {
            return;
        }
        Vec3 d = new Vec3(to.x - e.getX(), to.y - e.getY(), to.z - e.getZ());
        double len = d.length();
        if (len < stopDist) {
            e.setDeltaMovement(e.getDeltaMovement().scale(0.5));
            e.hurtMarked = true;
            return;
        }
        double f = isBoss(e) ? 0.25 : 1.0;
        double k = Math.min(strength * f * (1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1)), len * 0.5);
        Vec3 v = d.normalize().scale(k);
        e.setDeltaMovement(v.x, v.y * 0.6 + (e.onGround() && v.y < 0.1 ? 0.04 : 0), v.z);
        e.hurtMarked = true;
        e.fallDistance = 0;
    }

    /**
     * Predatory execution: a living enemy below the threshold is consumed through a controlled lethal hit, unless it is a boss, a
     * player or otherwise protected; a boss takes the bonus damage instead. True if the target died.
     */
    public static boolean consume(LivingEntity caster, LivingEntity target, double thresholdPercent, double bossBonus) {
        if (target.level().isClientSide || !target.isAlive()) {
            return false;
        }
        if (isBoss(target)) {
            if (bossBonus > 0) {
                return hurt(caster, target, bossBonus, 0);
            }
            return false;
        }
        if (target instanceof Player || target.isInvulnerable() || UNTOUCHABLE.contains(target.getUUID())) {
            return false;
        }
        if (target.getHealth() <= 0 || target.getHealth() > target.getMaxHealth() * thresholdPercent / 100.0) {
            return false;
        }
        target.invulnerableTime = 0;
        target.hurt(src(caster), target.getMaxHealth() * 10f + 1000f);
        return !target.isAlive();
    }

    /** Gives mana back to a player caster (scaled by the config multiplier). */
    public static void mana(LivingEntity caster, double amount) {
        if (!(caster instanceof ServerPlayer sp) || amount <= 0) {
            return;
        }
        var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(sp);
        md.addMana((float) (amount * DiaConfig.G.manaRestoreMultiplier.get()));
    }

    /** Heals the caster and returns the amount actually added. */
    public static double heal(LivingEntity caster, double amount) {
        if (amount <= 0 || !caster.isAlive()) {
            return 0;
        }
        float before = caster.getHealth();
        caster.heal((float) amount);
        return caster.getHealth() - before;
    }

    /** Extra damage taken (percent) for some ticks; the strongest value wins. */
    public static void vulnerable(LivingEntity e, double percent, int ticks) {
        if (e.level().isClientSide || ticks <= 0) {
            return;
        }
        long until = e.level().getGameTime() + ticks;
        double frac = Math.max(0, percent / 100.0);
        VULN.merge(e.getUUID(), new double[]{frac, until}, (a, b) -> a[0] >= b[0] ? new double[]{a[0], Math.max(a[1], b[1])} : new double[]{b[0], Math.max(a[1], b[1])});
    }

    public static double vulnerability(LivingEntity e) {
        double[] v = VULN.isEmpty() ? null : VULN.get(e.getUUID());
        return v != null && v[1] >= e.level().getGameTime() ? v[0] : 0.0;
    }

    /** Healing reduced by a percent for some ticks (the strongest one wins). */
    public static void healCut(LivingEntity e, double percent, int ticks) {
        if (e.level().isClientSide || ticks <= 0) {
            return;
        }
        long until = e.level().getGameTime() + ticks;
        double frac = Mth.clamp(percent / 100.0, 0, 1);
        HEAL_CUT.merge(e.getUUID(), new double[]{frac, until}, (a, b) -> a[0] >= b[0] ? new double[]{a[0], Math.max(a[1], b[1])} : new double[]{b[0], Math.max(a[1], b[1])});
    }

    /** Burning: dps once per second for some ticks on behalf of the caster (the stronger one wins). */
    public static void burn(LivingEntity caster, LivingEntity e, double dps, int ticks) {
        if (e.level().isClientSide || ticks <= 0) {
            return;
        }
        long now = e.level().getGameTime();
        Dot d = DOT.get(e.getUUID());
        if (d == null || d.until < now || d.dps <= dps) {
            d = new Dot();
            d.dps = dps;
            d.caster = caster.getUUID();
            d.nextAt = now + 20;
            DOT.put(e.getUUID(), d);
        }
        d.until = Math.max(d.until, now + ticks);
    }

    public static boolean burning(LivingEntity e) {
        Dot d = DOT.get(e.getUUID());
        return d != null && d.until >= e.level().getGameTime();
    }

    /** A mob becomes afraid of the caster: it cannot target it and runs away from it. */
    public static void terrify(LivingEntity caster, Mob mob, int ticks) {
        if (mob.level().isClientSide || isBoss(mob)) {
            return;
        }
        Fear f = new Fear();
        f.caster = caster.getUUID();
        f.until = mob.level().getGameTime() + ticks;
        FEAR.put(mob.getUUID(), f);
        if (mob.getTarget() == caster) {
            mob.setTarget(null);
        }
        Vec3 away = mob.position().subtract(caster.position());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        Vec3 to = mob.position().add(away.scale(14));
        mob.getNavigation().moveTo(to.x, to.y, to.z, 1.3);
    }

    // ------------------------------------------------------------------ demonic fx

    /** Black fire and embers. */
    public static void flameBurst(Level level, double x, double y, double z, double size, int power) {
        fx(level, ModParticles.FLASH.get(), x, y + 0.4, z, 1, 0, 0, 0, 0);
        fx(level, ModParticles.FLAME.get(), x, y + 0.5, z, power, size * 0.3, size * 0.2, size * 0.3, 0.2);
        fx(level, ModParticles.EMBER.get(), x, y + 0.5, z, Math.max(2, power / 2), size * 0.3, size * 0.25, size * 0.3, 0.2);
        fx(level, ModParticles.ASH.get(), x, y + 0.6, z, Math.max(1, power / 3), size * 0.3, size * 0.3, size * 0.3, 0.1);
    }

    /** Crimson lightning and sparks. */
    public static void sparkBurst(Level level, double x, double y, double z, double size, int power) {
        fx(level, ModParticles.FLASH.get(), x, y, z, 1, 0, 0, 0, 0);
        fx(level, ModParticles.EMBER.get(), x, y, z, power, size * 0.3, size * 0.3, size * 0.3, 0.3);
        fx(level, ModParticles.STREAK.get(), x, y, z, Math.max(1, power / 3), size * 0.2, size * 0.2, size * 0.2, 0.25);
    }

    /** A void explosion: ash, cracks, souls, embers and a flash. */
    public static void voidBurst(Level level, double x, double y, double z, double size, int power) {
        fx(level, ModParticles.FLASH.get(), x, y + 0.5, z, 2, 0, 0, 0, 0);
        fx(level, ModParticles.ASH.get(), x, y + 0.6, z, power, size * 0.3, size * 0.2, size * 0.3, 0.3);
        fx(level, ModParticles.CRACK.get(), x, y + 0.8, z, Math.max(2, power / 3), size * 0.3, size * 0.2, size * 0.3, 0.05);
        fx(level, ModParticles.SOUL.get(), x, y + 0.6, z, Math.max(2, power / 2), size * 0.35, size * 0.25, size * 0.35, 0.15);
        fx(level, ModParticles.EMBER.get(), x, y + 0.6, z, Math.max(2, power / 2), size * 0.3, size * 0.25, size * 0.3, 0.3);
    }

    // ------------------------------------------------------------------ fx

    public static void fx(Level level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            int n = Math.max(1, (int) Math.round(count * DiaConfig.G.particleDensity.get()));
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

    /** A jagged lightning arc of particles from a to b. */
    public static void arc(Level level, ParticleOptions type, Vec3 a, Vec3 b, double jitter) {
        double len = a.distanceTo(b);
        int steps = Math.max(3, (int) (len * 2.2));
        var rnd = level.random;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            Vec3 p = a.lerp(b, f);
            double j = (i == 0 || i == steps) ? 0 : jitter * Math.sin(f * Math.PI);
            fxDir(level, type, p.x + (rnd.nextDouble() - 0.5) * j, p.y + (rnd.nextDouble() - 0.5) * j, p.z + (rnd.nextDouble() - 0.5) * j, 0, 0, 0);
        }
    }

    /** A vertical bolt of lightning from (x, y + height, z) down to (x, y, z). */
    public static void bolt(Level level, ParticleOptions type, double x, double y, double z, double height) {
        var rnd = level.random;
        Vec3 prev = new Vec3(x, y + height, z);
        int segs = Math.max(4, (int) (height / 1.4));
        for (int i = 1; i <= segs; i++) {
            double f = i / (double) segs;
            Vec3 next = new Vec3(x + (i == segs ? 0 : (rnd.nextDouble() - 0.5) * 2.4), y + height * (1 - f), z + (i == segs ? 0 : (rnd.nextDouble() - 0.5) * 2.4));
            arc(level, type, prev, next, 0.35);
            prev = next;
        }
    }

    /** A flat ring of particles expanding outward. */
    public static void ring(Level level, ParticleOptions type, Vec3 c, int n, double speed, double up) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            fxDir(level, type, c.x, c.y, c.z, Math.cos(a) * speed, up, Math.sin(a) * speed);
        }
    }
}
