package com.example.jjkspells.prime;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSpells;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.jjkspells.prime.PrimeFx.*;

/**
 * Divine Purple: Hollow Purple evolved. Blue and Red collide into a huge Purple, then it is fired as a laser:
 * it crosses 7 blocks every tick, erases what it touches and deals 40% max health. Everything standing in its path
 * is held in place with Slowness VI from the moment the spell is cast.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class DivinePurpleManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int CHARGE = 36, MERGE = 22, COLLAPSE = 20;

    private enum Phase { CHARGE, FLIGHT, COLLAPSE }

    private static final class Beam {
        final ServerLevel level;
        final UUID caster;
        final PrimeBlocks blocks;
        final Set<UUID> hit = new HashSet<>();
        Phase phase = Phase.CHARGE;
        int age, phaseAge;
        Vec3 origin = Vec3.ZERO, head = Vec3.ZERO, dir = new Vec3(0, 0, 1);
        double travelled;
        boolean breaks;
        float R;

        Beam(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.caster = c.getUUID();
            this.blocks = new PrimeBlocks(level, c);
            this.R = PrimeConfig.DP_RADIUS.get().floatValue();
            this.breaks = JJKConfig.breaks(PrimeConfig.DP_BREAKS);
        }
    }

    private static final List<Beam> ACTIVE = new ArrayList<>();

    private DivinePurpleManager() {
    }

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Beam(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Beam> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Beam o = it.next();
            Entity e = o.level.getEntity(o.caster);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done = false;
            try {
                if (o.phase == Phase.CHARGE) {
                    if (c == null) done = true;
                    else charge(o, c);
                } else if (o.phase == Phase.FLIGHT) {
                    flight(o, c);
                } else {
                    done = collapse(o);
                }
            } catch (Exception ex) {
                LOGGER.error("Divine Purple failed, cancelling it", ex);
                done = true;
            }
            o.age++;
            o.phaseAge++;
            if (done || o.age > 900) it.remove();
        }
    }

    // ------------------------------------------------------------------------------------------ the path
    private static double pathLength(Beam o, LivingEntity c, Vec3 eye, Vec3 look) {
        double len = PrimeConfig.DP_RANGE.get();
        if (!o.breaks) {
            BlockHitResult hit = o.level.clip(new ClipContext(eye, eye.add(look.scale(len)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
            if (hit.getType() != HitResult.Type.MISS) len = hit.getLocation().distanceTo(eye);
        }
        return len;
    }

    private static double segDist(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double l2 = Math.max(1.0E-6, ab.lengthSqr());
        double t = Mth.clamp(p.subtract(a).dot(ab) / l2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Everything that is going to be in the beam's way gets Slowness VI so it cannot step aside. */
    private static void pin(Beam o, LivingEntity c, int ticksLeftInCharge) {
        Vec3 eye = c.getEyePosition(), look = c.getLookAngle();
        double len = pathLength(o, c, eye, look);
        Vec3 end = eye.add(look.scale(len));
        int dur = PrimeConfig.DP_SLOW_SECONDS.get() * 20 + ticksLeftInCharge + 20;
        for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(o.R + 4), LivingEntity::isAlive)) {
            if (!foe(c, e)) continue;
            if (segDist(eye, end, e.getBoundingBox().getCenter()) > o.R + 2.5 + e.getBbWidth() * 0.5) continue;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 5, false, true, true), c);
            if (o.age % 8 == 0) flakes(o.level, e, ModParticles.HP_WISP.get(), 3);
        }
    }

    // ------------------------------------------------------------------------------------------ charge
    private static void charge(Beam o, LivingEntity c) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        int t = o.age;
        float R = o.R;
        Basis b = Basis.of(c.getLookAngle());
        Vec3 f = b.f(), r = b.r(), u = b.u();
        Vec3 C = c.getEyePosition().add(f.scale(4.2 + R * 0.45)).subtract(u.scale(0.2));
        o.head = C;
        o.origin = c.getEyePosition();
        o.dir = f;

        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        if (t == 0 || t % 4 == 0) pin(o, c, CHARGE - t);

        if (t == 0) {
            sound(level, c.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 5f, 0.5f);
            sound(level, c.position(), SoundEvents.BEACON_ACTIVATE, 4f, 0.5f);
            announce(level, c, Component.literal("Lapse: Blue").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        if (t == 12) announce(level, c, Component.literal("Reversal: Red").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        if (t < MERGE) {
            double k = t / (double) MERGE;
            double sep = 3.4 * (1 - k * k) + 0.05;
            double a = t * 0.16 + t * t * 0.006;
            Vec3 dv = r.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
            Vec3 tang = r.scale(-Math.sin(a)).add(u.scale(Math.cos(a)));
            Vec3 blue = C.add(dv.scale(sep)), red = C.subtract(dv.scale(sep));
            float gr = (float) (0.6 + 0.9 * k);
            glow(level, ModParticles.HP_GLOW_BLUE.get(), blue, gr * 2.6f);
            glow(level, ModParticles.HP_CORE.get(), blue, gr * 0.8f);
            glow(level, ModParticles.HP_GLOW_RED.get(), red, gr * 2.6f);
            glow(level, ModParticles.HP_CORE.get(), red, gr * 0.8f);
            for (int i = 0; i < 5; i++) {
                Vec3 p = blue.add(rnd(rng).scale(2.2));
                one(level, ModParticles.HP_WISP_BLUE.get(), p, blue.subtract(p).scale(0.14).add(tang.scale(0.12)));
                Vec3 q = red.add(rnd(rng).scale(0.4));
                one(level, ModParticles.HP_WISP_RED.get(), q, q.subtract(red).normalize().scale(0.28).add(tang.scale(-0.1)));
            }
            for (int i = 0; i < 4; i++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                Vec3 p = C.add(r.scale(Math.cos(ang) * 7).add(u.scale(Math.sin(ang) * 7)).add(f.scale((rng.nextDouble() - 0.5) * 3)));
                line(level, ModParticles.HP_LINE.get(), p, C.subtract(p).scale(0.4));
            }
            bolt(level, ModParticles.HP_LINE.get(), blue, red, 0.7, 4);
            if (t % 5 == 0) sound(level, C, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, (float) (0.5 + k));
            if (t % 6 == 0) glow(level, ModParticles.HP_RING.get(), C, (float) (1.5 + k * 4));
            return;
        }

        int m = t - MERGE;
        double g = smooth(m / (double) (CHARGE - MERGE));
        float rad = (float) (0.5 + (R - 0.5) * g);
        if (m == 0) {
            title(level, c.position(), Component.literal("DIVINE PURPLE").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), Component.literal("PRIME TECHNIQUE").withStyle(ChatFormatting.DARK_PURPLE));
            sound(level, C, SoundEvents.LIGHTNING_BOLT_IMPACT, 5f, 1.3f);
            sound(level, C, SoundEvents.AMETHYST_BLOCK_RESONATE, 5f, 0.5f);
            glow(level, ModParticles.HP_CORE.get(), C, 10f);
            glow(level, ModParticles.HP_GLOW.get(), C, 20f);
            for (int i = 0; i < 5; i++) glow(level, ModParticles.HP_RING.get(), C, 5f + i * 3f);
            for (int i = 0; i < 70; i++) one(level, ModParticles.HP_WISP.get(), C, unit(rng).scale(0.5 + rng.nextDouble() * 0.9));
            for (int i = 0; i < 30; i++) {
                Vec3 d = unit(rng);
                line(level, ModParticles.HP_LINE.get(), C.add(d.scale(0.8)), d.scale(4 + rng.nextDouble() * 8));
            }
        }
        drawOrb(level, C, rad, b, t);
        if (t % 4 == 0) sound(level, C, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 3f, (float) (0.5 + g * 0.7));
        if (t >= CHARGE - 1) release(o, c, b);
    }

    private static void release(Beam o, LivingEntity c, Basis b) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        o.phase = Phase.FLIGHT;
        o.phaseAge = -1;
        o.dir = b.f();
        o.travelled = 0;
        Vec3 C = o.head;
        o.origin = c.getEyePosition().subtract(o.dir.scale(0.5));
        sound(level, C, SoundEvents.WARDEN_SONIC_BOOM, 8f, 0.6f);
        sound(level, C, SoundEvents.END_GATEWAY_SPAWN, 6f, 1.2f);
        sound(level, C, SoundEvents.GENERIC_EXPLODE, 7f, 0.5f);
        glow(level, ModParticles.HP_CORE.get(), C, o.R * 3.2f);
        glow(level, ModParticles.HP_GLOW.get(), C, o.R * 5f);
        for (int i = 0; i < 6; i++) glow(level, ModParticles.HP_RING.get(), C.add(o.dir.scale(i * 2.5)), o.R * (1.8f + i * 0.9f));
        for (int i = 0; i < 40; i++) {
            Vec3 d = o.dir.scale(-1).add(unit(rng).scale(0.7)).normalize();
            line(level, ModParticles.HP_LINE.get(), C.add(d.scale(o.R)), d.scale(6 + rng.nextDouble() * 10));
        }
        c.setDeltaMovement(c.getDeltaMovement().add(-o.dir.x * 1.0, 0.2, -o.dir.z * 1.0));
        c.hurtMarked = true;
    }

    // ------------------------------------------------------------------------------------------ flight
    private static void flight(Beam o, LivingEntity c) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        float R = o.R;
        Basis b = Basis.of(o.dir);
        double speed = PrimeConfig.DP_SPEED.get();
        double range = PrimeConfig.DP_RANGE.get();
        int steps = Math.max(1, (int) Math.ceil(speed / 1.5));
        double stepLen = speed / steps;
        float pct = PrimeConfig.DP_PERCENT.get().floatValue();
        o.blocks.newTick();
        Vec3 tickStart = o.head;
        boolean end = false;

        for (int s = 0; s < steps && !end; s++) {
            Vec3 prev = (o.phaseAge == 0 && s == 0) ? o.origin : o.head;
            Vec3 next = o.head.add(o.dir.scale(stepLen));
            BlockPos np = BlockPos.containing(next);
            if (!level.hasChunkAt(np) || level.isOutsideBuildHeight(np)) {
                end = true;
                break;
            }
            if (!o.breaks) {
                BlockPos fp = BlockPos.containing(next.add(o.dir.scale(R * 0.5)));
                BlockState fs = level.getBlockState(fp);
                if (!fs.getCollisionShape(level, fp).isEmpty()) {
                    end = true;
                    break;
                }
            }
            o.head = next;
            o.travelled += stepLen;

            if (o.breaks) o.blocks.sphere(next, R, next.subtract(o.dir.scale(2)), 0.9);

            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(prev, next).inflate(R + 2), LivingEntity::isAlive)) {
                if (!foe(c, e) || o.hit.contains(e.getUUID())) continue;
                Vec3 ec = e.getBoundingBox().getCenter();
                if (segDist(prev, next, ec) > R + 0.6 + e.getBbWidth() * 0.5) continue;
                o.hit.add(e.getUUID());
                smite(level, ModSpells.DIVINE_PURPLE.get(), c, e, 0f, pct);
                e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                glow(level, ModParticles.HP_CORE.get(), ec, 4f);
                glow(level, ModParticles.HP_RING.get(), ec, 6f);
                flakes(level, e, ModParticles.HP_WISP.get(), 22);
                for (int i = 0; i < 6; i++) {
                    Vec3 d = unit(rng);
                    line(level, ModParticles.HP_LINE.get(), ec, d.scale(2 + rng.nextDouble() * 3));
                }
            }

            // the laser, drawn where the head just went
            glow(level, ModParticles.HP_GLOW.get(), next, R * 2.1f);
            glow(level, ModParticles.HP_CORE.get(), next, R * 1.05f);
            glow(level, ModParticles.HP_GLOW_BLUE.get(), next.add(b.r().scale(R * 0.6)), R * 1.3f);
            glow(level, ModParticles.HP_GLOW_RED.get(), next.subtract(b.r().scale(R * 0.6)), R * 1.3f);
            double ang = o.travelled * 0.75;
            for (int strand = 0; strand < 3; strand++) {
                double a = ang + strand * Math.PI * 2 / 3;
                Vec3 off = b.r().scale(Math.cos(a)).add(b.u().scale(Math.sin(a))).scale(R * 1.02);
                one(level, ModParticles.HP_WISP.get(), next.add(off), o.dir.scale(-0.3));
            }
            glow(level, ModParticles.HP_RING.get(), next.subtract(o.dir.scale(R * 0.8)), R * 2.4f);
            one(level, ModParticles.HP_VOID.get(), next.add(rnd(rng).scale(R * 0.8)), rnd(rng).scale(0.05));
        }

        for (int i = 0; i < 6; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            Vec3 off = b.r().scale(Math.cos(ang)).add(b.u().scale(Math.sin(ang))).scale(R * (0.3 + rng.nextDouble() * 0.9));
            line(level, ModParticles.HP_LINE.get(), o.head.add(off), o.dir.scale(-(8 + rng.nextDouble() * 24)));
        }
        for (int i = 0; i < 3; i++) {
            Vec3 axis = o.head.subtract(o.dir.scale(rng.nextDouble() * 30));
            double ang = rng.nextDouble() * Math.PI * 2;
            Vec3 radial = b.r().scale(Math.cos(ang)).add(b.u().scale(Math.sin(ang)));
            bolt(level, ModParticles.HP_LINE.get(), axis, axis.add(radial.scale(R * (2.0 + rng.nextDouble() * 2.0))), 0.9, 4);
        }
        if (o.phaseAge % 3 == 0) sound(level, o.head, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 4f, 0.5f);
        if (o.phaseAge % 5 == 0) sound(level, o.head, SoundEvents.WARDEN_SONIC_BOOM, 2.5f, 1.3f);

        if (end || o.travelled >= range) {
            o.phase = Phase.COLLAPSE;
            o.phaseAge = -1;
            sound(level, o.head, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 6f, 0.5f);
        }
    }

    // ------------------------------------------------------------------------------------------ collapse
    private static boolean collapse(Beam o) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        int t = o.phaseAge;
        Vec3 P = o.head;
        float R = o.R;
        Vec3 span = P.subtract(o.origin);
        double len = span.length();

        // the trace of the laser fades along its whole length
        if (t < 14 && len > 1) {
            Vec3 dir = span.scale(1.0 / len);
            for (int i = 0; i < 7; i++) {
                double at = rng.nextDouble() * Math.max(0, len - 20);
                line(level, ModParticles.HP_LINE.get(), o.origin.add(dir.scale(at)), dir.scale(Math.min(20, len)));
            }
            for (double at = 0; at < len; at += 6) {
                if (rng.nextInt(3) == 0) glow(level, ModParticles.HP_GLOW.get(), o.origin.add(dir.scale(at)), R * (1.1f - t * 0.06f));
            }
        }
        if (t == 0) {
            sound(level, P, SoundEvents.WARDEN_SONIC_BOOM, 8f, 0.7f);
            sound(level, P, SoundEvents.LIGHTNING_BOLT_THUNDER, 7f, 0.5f);
            sound(level, P, SoundEvents.GENERIC_EXPLODE, 8f, 0.45f);
            glow(level, ModParticles.HP_CORE.get(), P, R * 4f);
            glow(level, ModParticles.HP_GLOW.get(), P, R * 7f);
            for (int i = 0; i < 6; i++) glow(level, ModParticles.HP_RING.get(), P, R * (2.5f + i * 1.4f));
            for (int i = 0; i < 100; i++) one(level, ModParticles.HP_WISP.get(), P, unit(rng).scale(0.4 + rng.nextDouble() * (0.8 + R * 0.15)));
            for (int i = 0; i < 40; i++) {
                Vec3 d = unit(rng);
                line(level, ModParticles.HP_LINE.get(), P.add(d.scale(R * 0.5)), d.scale(R * (1.5 + rng.nextDouble() * 3)));
            }
        }
        return t >= COLLAPSE;
    }

    /** The purple orb: layered glows, three spinning gyroscope rings of wisps and surface arcs. */
    private static void drawOrb(ServerLevel level, Vec3 P, float rad, Basis b, int age) {
        RandomSource rng = level.random;
        glow(level, ModParticles.HP_GLOW.get(), P, rad * 2.0f);
        glow(level, ModParticles.HP_GLOW.get(), P, rad * 1.35f);
        glow(level, ModParticles.HP_CORE.get(), P, rad * 0.85f);
        Vec3[][] planes = {{b.r(), b.u()}, {b.r(), b.f()}, {b.u(), b.f()}};
        int n = 3 + (int) (rad * 0.9);
        for (int pi = 0; pi < 3; pi++) {
            Vec3 A = planes[pi][0], B = planes[pi][1];
            double spin = age * (0.28 + pi * 0.08) * (pi == 1 ? -1 : 1);
            for (int i = 0; i < n; i++) {
                double a = spin + i * Math.PI * 2 / n;
                Vec3 off = A.scale(Math.cos(a)).add(B.scale(Math.sin(a)));
                Vec3 tang = A.scale(-Math.sin(a)).add(B.scale(Math.cos(a)));
                one(level, ModParticles.HP_WISP.get(), P.add(off.scale(rad * 1.06)), tang.scale(0.14 * Math.sqrt(rad)));
            }
        }
        for (int i = 0; i < 5 + (int) (rad * 0.6); i++) {
            Vec3 p1 = unit(rng);
            Vec3 p2 = p1.add(rnd(rng).scale(0.7)).normalize();
            Vec3 a = P.add(p1.scale(rad));
            Vec3 e = P.add(p2.scale(rad * 1.2));
            line(level, ModParticles.HP_LINE.get(), a, e.subtract(a));
        }
    }
}
