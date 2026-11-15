package com.example.jjkspells.prime;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.prime.fx.PrimeFxEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.jjkspells.prime.PrimeFx.*;

/**
 * Universe Severing Slash: the world cutting slash as a projectile. A front of cosmic cuts sweeps forward over a lattice and ends in a small
 * black hole. Two seconds later the hole explodes and throws every slash out as a crescent in all directions.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class UniverseSeveringManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int CHARGE = 24, START = 4, SPACING = 8, CONDENSE = 20, DOWN = 12, UP = 34;
    private static final double PROJ_SIZE = 14.0, PROJ_RANGE = 60.0;

    private static final class Proj {
        final Vec3 dir, r, u;
        final double tilt;
        final Set<UUID> hit = new HashSet<>();
        int t;

        Proj(Vec3 dir, double tilt) {
            this.dir = dir;
            this.r = SukunaFx.rightOf(dir);
            this.u = SukunaFx.upOf(dir, this.r);
            this.tilt = tilt;
        }
    }

    private static final class Us {
        final ServerLevel level;
        final UUID caster;
        final PrimeBlocks blocks;
        final Set<UUID> hit = new HashSet<>();
        final Map<UUID, Integer> projHits = new HashMap<>();
        final List<Proj> projs = new ArrayList<>();
        final int range, half;
        final double speed;
        int age, fireAt = -1;
        boolean exploded;
        int explodeAfter;
        Vec3 origin = Vec3.ZERO, F = new Vec3(0, 0, 1), L = new Vec3(1, 0, 0), hole = Vec3.ZERO;

        Us(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.caster = c.getUUID();
            this.blocks = new PrimeBlocks(level, c);
            this.range = PrimeConfig.US_RANGE.get();
            this.half = Math.max(SPACING, (PrimeConfig.US_HALF_WIDTH.get() / SPACING) * SPACING);
            this.speed = PrimeConfig.US_SPEED.get();
        }
    }

    private static final List<Us> ACTIVE = new ArrayList<>();

    private UniverseSeveringManager() {
    }

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Us(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Us> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Us s = it.next();
            Entity e = s.level.getEntity(s.caster);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done = false;
            try {
                if (s.fireAt < 0) {
                    if (c == null) done = true;
                    else charge(s, c);
                } else {
                    done = run(s, c);
                }
            } catch (Exception ex) {
                LOGGER.error("Universe Severing Slash failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done || s.age > 1500) it.remove();
        }
    }

    // ---------------------------------------------------------------------------------------------- charge
    private static void charge(Us s, LivingEntity c) {
        ServerLevel level = s.level;
        int t = s.age;
        Vec3 feet = c.position();
        if (t % 4 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 9, false, false, false));
        if (t == 0) {
            sound(level, feet, SoundEvents.ENDER_DRAGON_GROWL, 3f, 0.7f);
            announce(level, c, Component.literal("Scales of the Dragon.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            SukunaFx.blast(level, feet.add(0, 1, 0), 9f, 0);
        }
        if (t == 8) {
            announce(level, c, Component.literal("Repulsion.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            sound(level, feet, SoundEvents.GENERIC_EXPLODE, 3f, 1.4f);
            SukunaFx.blast(level, feet.add(0, 1, 0), 14f, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(10), LivingEntity::isAlive)) {
                if (!foe(c, e)) continue;
                Vec3 out = e.position().subtract(feet);
                out = new Vec3(out.x, 0, out.z);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                e.setDeltaMovement(out.x * 1.3, 0.35, out.z * 1.3);
                OverloadEvents.forceMove(e);
                e.hurtMarked = true;
            }
        }
        if (t == 16) {
            announce(level, c, Component.literal("Paired Falling Stars.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            sound(level, feet, SoundEvents.FIREWORK_ROCKET_LAUNCH, 3f, 0.5f);
            SukunaFx.blast(level, feet.add(0, 1, 0), 20f, 0);
        }
        if (t >= CHARGE - 1) fire(s, c);
    }

    private static void fire(Us s, LivingEntity c) {
        ServerLevel level = s.level;
        Direction d = c.getDirection();
        s.F = new Vec3(d.getStepX(), 0, d.getStepZ());
        s.L = new Vec3(-s.F.z, 0, s.F.x);
        s.origin = new Vec3(Math.floor(c.getX()) + 0.5, Math.floor(c.getY()), Math.floor(c.getZ()) + 0.5);
        s.hole = s.origin.add(s.F.scale(s.range)).add(0, 8, 0);
        s.fireAt = s.age;
        int travel = (int) Math.ceil((s.range - START) / s.speed);
        int hold = (int) Math.round(PrimeConfig.US_HOLD_SECONDS.get() * 20);
        s.explodeAfter = travel + CONDENSE + hold;
        PrimeFxEntity.spawn(level, PrimeFxEntity.LATTICE, s.origin, s.F, s.hole, s.range, s.explodeAfter + 3, (float) s.speed, s.half, -1);
        sound(level, s.origin, SoundEvents.GLASS_BREAK, 6f, 0.5f);
        sound(level, s.origin, SoundEvents.TRIDENT_THUNDER, 6f, 1.2f);
        sound(level, s.origin, SoundEvents.WARDEN_SONIC_BOOM, 5f, 1.5f);
        SukunaFx.blast(level, s.origin.add(s.F.scale(START)).add(0, 2, 0), 16f, 0);
    }

    // ---------------------------------------------------------------------------------------------- run
    private static boolean run(Us s, LivingEntity c) {
        ServerLevel level = s.level;
        if (c == null) return true;
        int t = s.age - s.fireAt;
        s.blocks.newTick();
        int travel = (int) Math.ceil((s.range - START) / s.speed);
        if (!s.exploded) {
            double prev = START + t * s.speed, cur = Math.min(s.range, START + (t + 1) * s.speed);
            if (prev < s.range) {
                damageFront(s, c, prev, cur);
                if (JJKConfig.breaks(PrimeConfig.US_BREAKS)) carve(s, prev, cur);
                if (t % 3 == 0) sound(level, s.origin.add(s.F.scale((prev + cur) * 0.5)), SoundEvents.GLASS_BREAK, 4f, 0.6f + level.random.nextFloat() * 0.5f);
            }
            if (t >= travel) pull(s, c, t - travel);
            if (t == travel + CONDENSE) {
                sound(level, s.hole, SoundEvents.BEACON_ACTIVATE, 6f, 0.5f);
            }
            if (t >= s.explodeAfter) explode(s, c);
            return false;
        }
        return flyProjectiles(s, c);
    }

    private static void damageFront(Us s, LivingEntity c, double prev, double cur) {
        ServerLevel level = s.level;
        float flat = PrimeConfig.US_FLAT.get().floatValue(), pct = PrimeConfig.US_PERCENT.get().floatValue();
        Vec3 O = s.origin;
        double W = s.half;
        Vec3 a = O.add(s.F.scale(prev - 2)).add(s.L.scale(-W - 1)).add(0, -DOWN, 0);
        Vec3 b = O.add(s.F.scale(cur + 1)).add(s.L.scale(W + 1)).add(0, UP, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b), LivingEntity::isAlive)) {
            if (!foe(c, e) || s.hit.contains(e.getUUID())) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            Vec3 rel = ec.subtract(O);
            double fd = rel.dot(s.F), ld = rel.dot(s.L);
            if (fd < prev - 1 || fd > cur + 0.5 || Math.abs(ld) > W + 1) continue;
            s.hit.add(e.getUUID());
            smite(level, ModSpells.UNIVERSE_SEVERING_SLASH.get(), c, e, flat, pct);
            SukunaFx.dismantle(e, c, PrimeConfig.US_DISMANTLE_SECONDS.get());
            SukunaFx.blast(level, ec, 6f, 0);
            sound(level, ec, SoundEvents.PLAYER_ATTACK_CRIT, 4f, 0.6f);
        }
    }

    private static int hash(int a, int b, int c) {
        int h = (a * 73856093) ^ (b * 19349663) ^ (c * 83492791);
        return (h >>> 3) & 0x7fffffff;
    }

    /** Slits along the grid: planes across the travel direction and lines along it, like the World Cutting Slash. */
    private static void carve(Us s, double prev, double cur) {
        int fa = (int) Math.floor(prev) + 1, fb = (int) Math.floor(cur);
        if (prev <= START) fa = START;
        int ox = (int) Math.floor(s.origin.x), oy = (int) Math.floor(s.origin.y), oz = (int) Math.floor(s.origin.z);
        int fx = (int) s.F.x, fz = (int) s.F.z, lx = (int) s.L.x, lz = (int) s.L.z;
        for (int f = fa; f <= fb; f++) {
            boolean across = Math.floorMod(f - START, SPACING) == 0;
            for (int l = -s.half; l <= s.half; l++) {
                boolean lat = Math.floorMod(l + s.half, SPACING) == 0;
                if (!(across || lat)) continue;
                int down = DOWN - (hash(f, l, 11) % 4);
                int up = UP - (hash(f, l, 13) % 8);
                for (int y = -down; y <= up; y++) {
                    s.blocks.erase(ox + fx * f + lx * l, oy + y, oz + fz * f + lz * l, s.origin, 0.4);
                }
            }
        }
    }

    /** From the moment the cuts reach the end, the hole drags things in. */
    private static void pull(Us s, LivingEntity c, int since) {
        double k = Math.min(1.0, since / 30.0);
        for (LivingEntity e : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(s.hole, s.hole).inflate(26), LivingEntity::isAlive)) {
            if (!foe(c, e)) continue;
            Vec3 to = s.hole.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d < 1.5 || d > 26) continue;
            Vec3 add = to.scale(1.0 / d).scale(0.16 * k);
            Vec3 v = e.getDeltaMovement().add(add);
            if (v.length() > 1.1) v = v.normalize().scale(1.1);
            e.setDeltaMovement(v);
            OverloadEvents.forceMove(e);
            e.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------------------------------------- explosion
    private static void explode(Us s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        s.exploded = true;
        Vec3 H = s.hole;
        SukunaFx.blast(level, H, 30f, 0);
        SukunaFx.blast(level, H, 18f, 2);
        sound(level, H, SoundEvents.GENERIC_EXPLODE, 12f, 0.4f);
        sound(level, H, SoundEvents.WARDEN_SONIC_BOOM, 10f, 0.6f);
        sound(level, H, SoundEvents.END_GATEWAY_SPAWN, 10f, 0.5f);
        sound(level, H, SoundEvents.LIGHTNING_BOLT_THUNDER, 10f, 0.5f);
        int n = PrimeConfig.US_PROJECTILES.get();
        double speed = PrimeConfig.US_PROJ_SPEED.get();
        int life = (int) Math.ceil(PROJ_RANGE / speed) + 4;
        for (int i = 0; i < n; i++) {
            double y = 1 - (i + 0.5) * 2.0 / n;
            double rad = Math.sqrt(Math.max(0, 1 - y * y));
            double phi = i * 2.399963 + rng.nextDouble() * 0.25;
            Vec3 dir = new Vec3(Math.cos(phi) * rad, y, Math.sin(phi) * rad).normalize();
            double tilt = rng.nextDouble() * Math.PI;
            s.projs.add(new Proj(dir, tilt));
            PrimeFxEntity.spawn(level, PrimeFxEntity.CSLASH, H, dir, null, (float) PROJ_SIZE, life, (float) speed, (float) tilt, -1);
        }
        if (JJKConfig.breaks(PrimeConfig.US_BREAKS)) {
            s.blocks.newTick();
            s.blocks.sphere(H, 6.0, H, 1.2);
        }
        s.explodeAfter = 0;
    }

    /** @return true when every thrown slash has flown its range */
    private static boolean flyProjectiles(Us s, LivingEntity c) {
        ServerLevel level = s.level;
        double speed = PrimeConfig.US_PROJ_SPEED.get();
        float flat = PrimeConfig.US_PROJ_FLAT.get().floatValue(), pct = PrimeConfig.US_PROJ_PERCENT.get().floatValue();
        int maxHits = PrimeConfig.US_PROJ_MAX_HITS.get();
        boolean any = false;
        for (Proj p : s.projs) {
            p.t++;
            double d1 = speed * p.t, d0 = speed * (p.t - 1);
            if (d0 >= PROJ_RANGE) continue;
            any = true;
            for (double d = d0 + 0.5; d <= d1 + 0.5; d += 1.0) {
                Vec3 centre = s.hole.add(p.dir.scale(d));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(PROJ_SIZE * 0.62), LivingEntity::isAlive)) {
                    if (!foe(c, e) || p.hit.contains(e.getUUID())) continue;
                    int got = s.projHits.getOrDefault(e.getUUID(), 0);
                    if (got >= maxHits) continue;
                    if (!SukunaFx.touches(e, centre, p.dir, p.r, p.u, p.tilt, PROJ_SIZE, 0.9)) continue;
                    p.hit.add(e.getUUID());
                    s.projHits.put(e.getUUID(), got + 1);
                    Vec3 ec = e.getBoundingBox().getCenter();
                    smite(level, ModSpells.UNIVERSE_SEVERING_SLASH.get(), c, e, flat, pct);
                    SukunaFx.dismantle(e, c, PrimeConfig.US_DISMANTLE_SECONDS.get());
                    SukunaFx.blast(level, ec, 5f, 0);
                    sound(level, ec, SoundEvents.PLAYER_ATTACK_CRIT, 4f, 0.7f);
                    if (e.isAlive()) {
                        e.setDeltaMovement(e.getDeltaMovement().add(p.dir.scale(0.5)).add(0, 0.2, 0));
                        e.hurtMarked = true;
                    }
                }
            }
        }
        return !any;
    }
}
