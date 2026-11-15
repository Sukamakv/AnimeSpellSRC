package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.effect.StunEffect;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Runs every active Infernal Chains.
 *
 *   WINDUP - his arm goes out and red-black energy coils around the hand.
 *   FLIGHT - a jagged crimson projectile flies up to 12 blocks and stops at the first enemy (or a wall).
 *   TETHER - chains join the enemy to a cursed circle on the ground where it was hit; it is slowed and has 1.5 seconds to leave.
 *   PULL   - if it stayed, it is dragged to the circle's centre (a controlled pull, not a permanent restriction).
 *   HOLD   - stunned for 3 seconds, chains wrapped around it.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class InfernalChainsManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int WINDUP = 0, FLIGHT = 1, TETHER = 2, PULL = 3, HOLD = 4;
    private static final double SPEED = 2.0;           // projectile blocks per tick
    private static final double HIT_WIDTH = 0.9;
    private static final double PULL_SPEED = 0.95;
    private static final int MAX_PULL_TICKS = 10;

    private static class Chain {
        final ServerLevel level;
        final UUID casterId;
        final InfernalChainsSpell spell;
        final int spellLevel;
        int state = WINDUP;
        int age = 0;               // ticks in the current state
        Vec3 pos;                  // projectile
        Vec3 dir;
        double remaining = InfernalChainsSpell.RANGE;
        UUID targetId;
        Vec3 anchor;               // centre of the cursed circle, on the ground
        boolean boss = false;

        Chain(ServerLevel level, LivingEntity caster, InfernalChainsSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
        }
    }

    private static final List<Chain> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, InfernalChainsSpell spell, int spellLevel) {
        ACTIVE.add(new Chain(level, caster, spell, spellLevel));
        MartialFX.sound(level, caster.position(), SoundEvents.WITHER_AMBIENT, 1.0f, 0.5f);
        MartialFX.sound(level, caster.position(), SoundEvents.CHAIN_PLACE, 1.0f, 0.5f);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Chain> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Chain c = it.next();
            Entity ce = c.level.getEntity(c.casterId);
            LivingEntity caster = (ce instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done;
            try {
                done = tick(c, caster);
            } catch (Exception ex) {
                LOGGER.error("Infernal Chains failed, cancelling it", ex);
                done = true;
            }
            c.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Chain c, LivingEntity caster) {
        switch (c.state) {
            case WINDUP:
                if (caster == null) return true;
                windup(c, caster);
                return false;
            case FLIGHT:
                if (caster == null) return true;
                return flight(c, caster);
            case TETHER:
                return tether(c, caster);
            case PULL:
                return pull(c, caster);
            default:
                return hold(c);
        }
    }

    private static void next(Chain c, int state) {
        c.state = state;
        c.age = -1;       // the tick counter is incremented right after, so the new state starts at 0
    }

    // =============================================================================================
    //  WINDUP and FLIGHT
    // =============================================================================================

    private static void windup(Chain c, LivingEntity caster) {
        ServerLevel level = c.level;
        Vec3 hand = DariusFX.handPos(caster);
        DariusFX.gather(level, hand, 1.3, 3);
        AatroxFX.smoke(level, hand, 1, 0.3);
        FX.one(level, ModParticles.DR_GLOW.get(), hand, new Vec3(0.35 + c.age * 0.06, 0, 0));
        if (c.age >= InfernalChainsSpell.WINDUP_TICKS - 1) {
            c.pos = hand;
            c.dir = caster.getLookAngle().normalize();
            MartialFX.sound(level, hand, SoundEvents.PHANTOM_SWOOP, 1.2f, 0.6f);
            MartialFX.sound(level, hand, SoundEvents.CHAIN_BREAK, 1.0f, 0.6f);
            next(c, FLIGHT);
        }
    }

    private static boolean flight(Chain c, LivingEntity caster) {
        ServerLevel level = c.level;
        double step = Math.min(SPEED, c.remaining);
        Vec3 from = c.pos;
        Vec3 to = from.add(c.dir.scale(step));

        // walls stop it
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3 end = blocked ? hit.getLocation() : to;

        // the first enemy near this stretch of the path
        LivingEntity victim = firstEnemy(level, caster, from, end);
        if (victim != null) end = nearestPoint(from, end, victim.getBoundingBox().getCenter());

        trail(level, from, end);
        c.pos = end;
        c.remaining -= from.distanceTo(end);

        if (victim != null) {
            impact(c, caster, victim);
            return false;
        }
        if (blocked || c.remaining <= 0.1) {
            DariusFX.redBurst(level, end, 0.9f);                     // it fizzles out
            MartialFX.sound(level, end, SoundEvents.CHAIN_BREAK, 0.8f, 0.8f);
            return true;
        }
        return false;
    }

    /** A jagged red streak with smoke and sparks along the projectile's path. */
    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        RandomSource rng = level.random;
        Vec3 d = to.subtract(from);
        Vec3 prev = from;
        for (int i = 1; i <= 3; i++) {
            Vec3 pt = from.add(d.scale(i / 3.0));
            if (i < 3) pt = pt.add((rng.nextDouble() - 0.5) * 0.5, (rng.nextDouble() - 0.5) * 0.5, (rng.nextDouble() - 0.5) * 0.5);   // the jagged zig-zag
            FX.one(level, ModParticles.DR_LINE.get(), prev, pt.subtract(prev));
            prev = pt;
        }
        FX.one(level, ModParticles.DR_GLOW.get(), to, new Vec3(0.55, 0, 0));
        AatroxFX.smoke(level, to, 1, 0.25);
        FX.one(level, ModParticles.DR_SPARK.get(), to, MartialFX.randomDir(rng).scale(0.12));
    }

    private static LivingEntity firstEnemy(ServerLevel level, LivingEntity caster, Vec3 a, Vec3 b) {
        AABB box = new AABB(a, b).inflate(HIT_WIDTH + 1.0);
        Vec3 seg = b.subtract(a);
        double segLen2 = Math.max(seg.lengthSqr(), 1.0E-6);
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double t = Mth.clamp(ec.subtract(a).dot(seg) / segLen2, 0, 1);
            Vec3 closest = a.add(seg.scale(t));
            if (closest.distanceTo(ec) > HIT_WIDTH + e.getBbWidth() * 0.5 + e.getBbHeight() * 0.2) continue;
            if (t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }

    private static Vec3 nearestPoint(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 seg = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(seg) / Math.max(seg.lengthSqr(), 1.0E-6), 0, 1);
        return a.add(seg.scale(t));
    }

    // =============================================================================================
    //  IMPACT and TETHER
    // =============================================================================================

    private static void impact(Chain c, LivingEntity caster, LivingEntity target) {
        ServerLevel level = c.level;
        float damage = InfernalChainsSpell.DAMAGE * DariusFX.levelScale(c.spellLevel);
        target.invulnerableTime = 0;
        boolean ok = DamageSources.applyDamage(target, damage, c.spell.getDamageSource(caster));
        Vec3 p = target.getBoundingBox().getCenter();
        DariusFX.redBurst(level, p, 1.5f);
        AatroxFX.blood(level, p, 8, 0.25);
        MartialFX.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        MartialFX.sound(level, p, SoundEvents.CHAIN_PLACE, 1.2f, 0.7f);
        if (!ok || !target.isAlive()) {
            return;
        }
        AatroxFX.irradiate(target, InfernalChainsSpell.IRRADIATED_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, InfernalChainsSpell.SLOW_TICKS, InfernalChainsSpell.SLOW_AMPLIFIER));

        c.targetId = target.getUUID();
        c.boss = DariusFX.isImmovable(target);
        double gy = MoonFX.groundAt(level, target.getX(), target.getZ(), target.getY());
        c.anchor = new Vec3(target.getX(), gy, target.getZ());
        AatroxFX.chainWrap(level, target.position(), target.getBbWidth() * 0.9f + 0.3f, target.getBbHeight() + 0.5f, 6);
        next(c, TETHER);
    }

    private static LivingEntity target(Chain c) {
        Entity e = c.targetId == null ? null : c.level.getEntity(c.targetId);
        return (e instanceof LivingEntity le && le.isAlive()) ? le : null;
    }

    /** Chains from four points on the circle's edge to the enemy, the circle itself drawn on the ground. */
    private static void drawChains(Chain c, LivingEntity t, int phase) {
        ServerLevel level = c.level;
        Vec3 core = t.getBoundingBox().getCenter();
        for (int i = 0; i < 4; i++) {
            double a = phase * 0.08 + i * Math.PI / 2;
            Vec3 edge = c.anchor.add(Math.cos(a) * InfernalChainsSpell.CIRCLE_RADIUS, 0.25, Math.sin(a) * InfernalChainsSpell.CIRCLE_RADIUS);
            FX.one(level, ModParticles.DR_CHAIN.get(), edge, core.subtract(edge));
        }
        if (phase % 2 == 0) {
            FX.one(level, ModParticles.AT_CIRCLE.get(), c.anchor.add(0, 0.06, 0), new Vec3(InfernalChainsSpell.CIRCLE_RADIUS, 0, 0));
        }
    }

    private static boolean tether(Chain c, LivingEntity caster) {
        LivingEntity t = target(c);
        if (t == null) return true;
        ServerLevel level = c.level;
        drawChains(c, t, c.age);
        if (c.age % 6 == 3) {
            AatroxFX.chainWrap(level, t.position(), t.getBbWidth() * 0.9f + 0.3f, t.getBbHeight() + 0.5f, 3);
        }
        if (c.age < InfernalChainsSpell.ESCAPE_TICKS) return false;

        // time is up: did it get out of the circle?
        double dx = t.getX() - c.anchor.x, dz = t.getZ() - c.anchor.z;
        if (Math.sqrt(dx * dx + dz * dz) > InfernalChainsSpell.CIRCLE_RADIUS + 0.2) {
            DariusFX.redBurst(level, t.getBoundingBox().getCenter(), 1.0f);        // the chains snap
            MartialFX.sound(level, t.position(), SoundEvents.CHAIN_BREAK, 1.2f, 0.7f);
            MartialFX.sound(level, t.position(), SoundEvents.ANVIL_BREAK, 0.6f, 1.2f);
            return true;
        }
        if (c.boss) {                                                               // cannot be dragged: heavily slowed instead
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, InfernalChainsSpell.STUN_TICKS, 2));
            DariusFX.metalBurst(level, t.getBoundingBox().getCenter(), 1.4f);
            MartialFX.sound(level, t.position(), SoundEvents.CHAIN_PLACE, 1.2f, 0.5f);
            return true;
        }
        MartialFX.sound(level, t.position(), SoundEvents.CHAIN_HIT, 1.4f, 0.5f);
        next(c, PULL);
        return false;
    }

    // =============================================================================================
    //  PULL and HOLD
    // =============================================================================================

    private static boolean pull(Chain c, LivingEntity caster) {
        LivingEntity t = target(c);
        if (t == null) return true;
        ServerLevel level = c.level;
        drawChains(c, t, c.age + 30);
        Vec3 to = new Vec3(c.anchor.x - t.getX(), 0, c.anchor.z - t.getZ());
        double dist = to.length();
        if (dist <= 0.6 || c.age >= MAX_PULL_TICKS) {
            land(c, t);
            next(c, HOLD);
            return false;
        }
        Vec3 dir = to.scale(1.0 / dist);
        double speed = Math.min(PULL_SPEED, dist);
        double vy = t.isNoGravity() ? (c.anchor.y + 0.5 - t.getY()) * 0.15 : (t.onGround() ? 0.12 : Math.min(0, t.getDeltaMovement().y));
        t.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        t.hurtMarked = true;
        t.fallDistance = 0;
        OverloadEvents.forceMove(t);
        return false;
    }

    /** It arrives at the centre of the circle: stun, dust, blood. */
    private static void land(Chain c, LivingEntity t) {
        ServerLevel level = c.level;
        Vec3 v = t.getDeltaMovement();
        t.setDeltaMovement(0, v.y, 0);
        t.hurtMarked = true;
        StunEffect.apply(t, InfernalChainsSpell.STUN_TICKS);
        DariusFX.ring(level, t.position(), 3.0f);
        AatroxFX.blood(level, t.getBoundingBox().getCenter(), 8, 0.25);
        AatroxFX.chainWrap(level, t.position(), t.getBbWidth() * 0.9f + 0.3f, t.getBbHeight() + 0.6f, 7);
        MartialFX.sound(level, t.position(), SoundEvents.ANVIL_LAND, 1.0f, 0.6f);
        MartialFX.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
    }

    /** The stunned enemy stays wrapped in chains until the stun is over. */
    private static boolean hold(Chain c) {
        LivingEntity t = target(c);
        if (t == null || c.age >= InfernalChainsSpell.STUN_TICKS) return true;
        drawChains(c, t, c.age + 60);
        if (c.age % 8 == 4) {
            AatroxFX.chainWrap(c.level, t.position(), t.getBbWidth() * 0.9f + 0.3f, t.getBbHeight() + 0.6f, 3);
        }
        return false;
    }
}
