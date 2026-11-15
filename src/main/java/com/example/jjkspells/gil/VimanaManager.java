package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.entity.GateWeaponEntity;
import com.example.jjkspells.gil.entity.GilFxEntity;
import com.example.jjkspells.gil.entity.VimanaEntity;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.GoldTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Vimana: The King's Aerial Dominion (7 s of cast, then the vessel lands).
 *   0-20    a great portal opens beneath the caster, the vessel unfolds out of it and the caster boards
 *   20-40   it climbs and turns toward the target area; side portals open; a targeting circle marks the ground
 *   40-90   treasury bombardment: weapons fall on random spots of the area (enemies first)
 *   90-110  the bow charges a golden sphere, concentric rings gather, then the Divine Cannon fires at the ground
 *   110-126 eight portals ring the vessel and each lets a great spear fall; a ninth lands in the centre
 *   126     all nine land together: the final golden explosion; the vessel then descends and vanishes
 * The clock stops while the vessel is staggered by a strong magical hit.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class VimanaManager {
    private static final int BOARD = 6, CLIMB = 20, BOMB0 = 40, BOMB1 = 90, CHARGE0 = 90, FIRE = 100, CANNON = 108, PORTALS = 110, FINAL = 126, END = 140;

    private static final class Session {
        final ServerLevel level;
        final UUID casterId;
        VimanaEntity vessel;
        final Vec3 start;
        final Vec3 center;
        final double orbitR, orbitA0;
        int clock;
        int age;
        int fired;
        double yaw;

        Session(ServerLevel level, LivingEntity c, Vec3 center) {
            this.level = level;
            this.casterId = c.getUUID();
            this.start = c.position();
            this.center = center;
            this.orbitR = Math.min(13.0, Math.max(6.0, center.distanceTo(start) * 0.6));
            Vec3 off = start.subtract(center);
            this.orbitA0 = Math.atan2(off.z, off.x);
        }
    }

    private static final List<Session> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster) {
        double range = GilConfig.VIMANA.range.get();
        Vec3 aim = GilDmg.aim(level, caster, range);
        double gy = GoldTarget.groundY(level, aim.x, aim.y + 2, aim.z, 40, aim.y);
        Vec3 center = new Vec3(aim.x, gy, aim.z);
        ACTIVE.add(new Session(level, caster, center));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Session> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done;
            try {
                done = tick(s, c);
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getLogger().error("Vimana failed", ex);
                done = true;
            }
            s.age++;
            if (done) {
                cleanup(s, c);
                it.remove();
            }
        }
    }

    private static void cleanup(Session s, LivingEntity c) {
        if (s.vessel != null && !s.vessel.isRemoved()) {
            if (s.level.isLoaded(s.vessel.blockPosition())) {
                GilUtil.portal(s.level, s.vessel.position().add(0, 0.5, 0), new Vec3(0, 1, 0), 3.5, true, 0.8f);
            }
            s.vessel.ejectPassengers();
            s.vessel.discard();
        }
        if (c != null) {
            c.stopRiding();
            GilState.clearGuard(c);
            GilState.fallSafe(c, 100);
            c.fallDistance = 0;
        }
    }

    private static boolean tick(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        var cfg = GilConfig.VIMANA;
        if (c == null) return true;
        int age = s.age;

        // ---- phase 1: portal and vessel
        if (age == 0) {
            GilUtil.announce(c, Component.literal("Vimana").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            GilUtil.portal(level, s.start.add(0, 0.05, 0), new Vec3(0, 1, 0), 4.2, true, 0.7f);
            GilUtil.sound(level, s.start, SoundEvents.BEACON_ACTIVATE, 3f, 0.6f);
            GilState.guard(c, END + 40, 0f, 0.6f, true, false);   // Aerial Sovereignty
            GilState.fallSafe(c, END + 200);
            Vec3 d = s.center.subtract(s.start);
            s.yaw = Math.toDegrees(Mth.atan2(-d.x, d.z));
        }
        if (age == 2) {
            VimanaEntity v = new VimanaEntity(level);
            v.moveTo(s.start.x, s.start.y - 0.25, s.start.z, (float) s.yaw, 0f);
            level.addFreshEntity(v);
            s.vessel = v;
            FX.one(level, ModParticles.GOLD_GLOW.get(), s.start.add(0, 0.5, 0), new Vec3(5, 0, 0));
        }
        VimanaEntity v = s.vessel;
        if (v == null) return false;
        if (v.isRemoved()) return true;
        if (age >= BOARD && c.getVehicle() != v) c.startRiding(v, true);
        c.fallDistance = 0;

        // stagger: the whole timeline pauses
        if (age > BOARD && v.staggered()) {
            if (age % 2 == 0) FX.one(level, ModParticles.GOLD_SPARK.get(), v.position().add((rng.nextDouble() - 0.5) * 6, rng.nextDouble(), (rng.nextDouble() - 0.5) * 6), new Vec3(0, 0.2, 0));
            GilUtil.sound(level, v.position(), SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
            return false;
        }
        int t = s.clock++;
        v.setUnfold(Mth.clamp(t / 20f, 0f, 1f) * (t >= END - 14 ? Mth.clamp((END - t) / 14f, 0f, 1f) : 1f));
        if (t % 8 == 0) GilUtil.sound(level, v.position(), SoundEvents.BEACON_AMBIENT, 1.6f, 0.7f + 0.3f * Mth.sin(t * 0.1f));

        // ---- movement: climb, glide to the orbit, circle the area
        move(s, v, t);
        // engine glow
        if (t > 4 && t % 2 == 0) {
            Vec3 under = v.position().add(0, -0.4, 0);
            for (int i = 0; i < 3; i++) {
                FX.one(level, ModParticles.GOLD_SPARK.get(), under.add((rng.nextDouble() - 0.5) * 4, 0, (rng.nextDouble() - 0.5) * 4), new Vec3(0, -0.2 - rng.nextDouble() * 0.2, 0));
            }
        }

        // ---- phase 2: the targeting circle and the side portals
        if (t == CLIMB) {
            double r = cfg.radius.get();
            level.addFreshEntity(GilFxEntity.create(level, GilFxEntity.CIRCLE, 0, CANNON - CLIMB + 14, (float) r, s.center.add(0, 0.08, 0)).path(Vec3.ZERO, new Vec3(0, 1, 0), 0, 0));
        }
        Vec3 fwd = forward(v);
        Vec3 right = GilUtil.rightOf(fwd);
        if (t >= CLIMB + 6 && t <= BOMB1 && t % 10 == 6) {
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 2; i++) {
                    Vec3 pp = v.position().add(right.scale(side * (3.6 + i * 0.6))).add(fwd.scale(-0.8 + i * 2.2)).add(0, -0.1, 0);
                    GilUtil.portal(level, pp, new Vec3(0, -1, 0), 0.9, i == 0 && side > 0, 1.2f);
                }
            }
        }

        // ---- phase 3: treasury bombardment
        if (t >= BOMB0 && t < BOMB1) {
            int n = cfg.projectiles.i();
            double span = (BOMB1 - BOMB0);
            int shouldHave = (int) Math.floor((t - BOMB0 + 1) * n / span);
            while (s.fired < shouldHave) {
                fireBomb(s, c, v, right, fwd);
                s.fired++;
            }
        }

        // ---- phase 4: the Divine Cannon
        Vec3 bow = v.position().add(fwd.scale(4.3)).add(0, 0.1, 0);
        if (t >= CHARGE0 && t < CANNON) {
            float k = Mth.clamp((t - CHARGE0) / (float) (FIRE - CHARGE0), 0f, 1f);
            v.setCharge(k);
            if (t == CHARGE0) GilUtil.sound(level, bow, SoundEvents.BEACON_POWER_SELECT, 3f, 0.5f);
            if (t < FIRE) {
                for (int i = 0; i < 5; i++) {
                    Vec3 from = bow.add(new Vec3(rng.nextDouble() - 0.5, rng.nextDouble() - 0.5, rng.nextDouble() - 0.5).normalize().scale(4.5 * (1 - k * 0.5) + rng.nextDouble() * 2));
                    FX.one(level, ModParticles.GOLD_SPARK.get(), from, bow.subtract(from).scale(0.13));
                }
                if (t % 3 == 0) {
                    FX.one(level, ModParticles.GOLD_RING.get(), bow, new Vec3(2.2 * (1.1 - k), 0, 0));
                    FX.one(level, ModParticles.GOLD_GLOW.get(), bow, new Vec3(0.8 + 2.2 * k, 0, 0));
                }
            } else {
                // the sphere compresses into a narrow beam and fires at the ground
                Vec3 end = s.center.add(0, 0.2, 0);
                Vec3 dir = end.subtract(bow);
                double len = dir.length();
                Vec3 u = dir.normalize();
                if (t == FIRE) GilUtil.sound(level, bow, ModSounds.EA_FIRE.get(), 4f, 1.6f);
                for (double d = 0; d < len; d += 5.0) {
                    FX.one(level, ModParticles.GOLD_LINE.get(), bow.add(u.scale(d)), u.scale(Math.min(5.0, len - d)));
                }
                FX.one(level, ModParticles.GOLD_GLOW.get(), bow, new Vec3(1.8, 0, 0));
            }
        }
        if (t == CANNON) cannon(s, c);
        if (t > CANNON) v.setCharge(Math.max(0f, v.charge() - 0.15f));

        // ---- phase 5: eight spears and a ninth
        if (t == PORTALS) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Vec3 pp = v.position().add(Math.cos(a) * 5.5, 0.8 + (i % 2) * 0.8, Math.sin(a) * 5.5);
                GilUtil.portal(level, pp, new Vec3(0, -1, 0), 1.5, i == 0, 0.8f);
            }
        }
        if (t == PORTALS + 4) spears(s, c, v);
        if (t == FINAL) finale(s, c);

        // ---- the descent
        if (t == END) return true;
        return false;
    }

    private static Vec3 forward(VimanaEntity v) {
        double a = Math.toRadians(v.getYRot());
        return new Vec3(-Math.sin(a), 0, Math.cos(a));
    }

    private static void move(Session s, VimanaEntity v, int t) {
        ServerLevel level = s.level;
        double alt = GilConfig.VIMANA.ascend.get();
        Vec3 pos;
        double tx, tz;
        if (t < 60) {
            double f = FxEase.smooth((t - 12) / 48.0);
            double ox = s.center.x + Math.cos(s.orbitA0) * s.orbitR, oz = s.center.z + Math.sin(s.orbitA0) * s.orbitR;
            tx = Mth.lerp(f, s.start.x, ox);
            tz = Mth.lerp(f, s.start.z, oz);
        } else {
            double a = s.orbitA0 + (t - 60) * 0.022;
            tx = s.center.x + Math.cos(a) * s.orbitR;
            tz = s.center.z + Math.sin(a) * s.orbitR;
        }
        double ground = (t % 2 == 0 || Double.isNaN(lastGround(v))) ? GoldTarget.groundY(level, tx, Math.max(s.start.y, s.center.y) + alt + 20, tz, 90, s.center.y) : lastGround(v);
        setLastGround(v, ground);
        double base = Math.max(s.start.y, ground);
        double climb = FxEase.ease((t - CLIMB) / 20.0);
        double y = s.start.y - 0.25 + (Math.max(base, s.center.y) + alt - s.start.y + 0.25) * climb;
        if (t >= FINAL + 0) {
            double d = FxEase.smooth((t - FINAL) / (double) (END - FINAL));
            y = Mth.lerp(d, y, Math.max(ground, s.center.y) + 1.0);
        }
        if (t < 8) y = s.start.y - 0.25 + 0.0;
        // face the area
        Vec3 look = s.center.subtract(tx, 0, tz);
        float yaw = (float) Math.toDegrees(Mth.atan2(-look.x, look.z));
        float cur = v.getYRot();
        float ny = cur + Mth.wrapDegrees(yaw - cur) * 0.12f;
        if (t < 8) { tx = s.start.x; tz = s.start.z; }
        v.setPos(tx, y, tz);
        v.setYRot(ny);
        v.setXRot(0);
    }

    // tiny per-vessel memory of the last ground height (avoid a lookup every tick)
    private static final java.util.Map<UUID, Double> GROUND = new java.util.concurrent.ConcurrentHashMap<>();
    static void forget(VimanaEntity v) { GROUND.remove(v.getUUID()); }
    private static double lastGround(VimanaEntity v) { return GROUND.getOrDefault(v.getUUID(), Double.NaN); }
    private static void setLastGround(VimanaEntity v, double g) { GROUND.put(v.getUUID(), g); }

    private static void fireBomb(Session s, LivingEntity c, VimanaEntity v, Vec3 right, Vec3 fwd) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        var cfg = GilConfig.VIMANA;
        double r = cfg.radius.get();
        Vec3 aim;
        List<LivingEntity> foes = GilDmg.around(level, c, s.center, r);
        if (!foes.isEmpty() && rng.nextInt(10) < 8) {
            LivingEntity e = foes.get(rng.nextInt(foes.size()));
            aim = e.getBoundingBox().getCenter().add((rng.nextDouble() - 0.5) * 2, 0, (rng.nextDouble() - 0.5) * 2);
        } else {
            double a = rng.nextDouble() * Mth.TWO_PI, d = Math.sqrt(rng.nextDouble()) * r;
            double x = s.center.x + Math.cos(a) * d, z = s.center.z + Math.sin(a) * d;
            aim = new Vec3(x, GoldTarget.groundY(level, x, s.center.y + 10, z, 30, s.center.y) + 0.3, z);
        }
        int side = s.fired % 2 == 0 ? 1 : -1;
        Vec3 at = v.position().add(right.scale(side * (3.6 + (s.fired % 3) * 0.5))).add(fwd.scale(-0.8 + (s.fired % 3) * 1.1)).add(0, -0.3, 0);
        GateWeaponEntity.Params p = new GateWeaponEntity.Params(cfg.projectile.f(), 0f, 1f, false, 0, 0f, 2.3, cfg.projectileBlast.f(), false);
        level.addFreshEntity(new GateWeaponEntity(level, c, null, aim, at, rng.nextInt(GateWeaponEntity.TYPES), 5, p));
    }

    /** The Divine Cannon strikes the centre of the area. */
    private static void cannon(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        var cfg = GilConfig.VIMANA;
        Vec3 g = s.center;
        double r = cfg.cannonRadius.get();
        GilUtil.sound(level, g, SoundEvents.GENERIC_EXPLODE, 4f, 0.6f);
        GilUtil.sound(level, g, ModSounds.WEAPON_IMPACT.get(), 3f, 0.5f);
        FX.one(level, ModParticles.GOLD_GLOW.get(), g.add(0, 1.5, 0), new Vec3(r * 2.6, 0, 0));
        FX.one(level, ModParticles.GOLD_RING.get(), g.add(0, 1.5, 0), new Vec3(r * 1.6, 0, 0));
        for (int i = 0; i < 3; i++) GilUtil.flatRing(level, g.add(0, 0.1 + i * 0.05, 0), r * (0.7 + i * 0.6));
        GilUtil.sparks(level, g.add(0, 0.5, 0), 80, 1.6);
        for (LivingEntity e : GilDmg.around(level, c, g, r)) {
            GilDmg.hit(level, c, e, cfg.cannonFlat.f(), cfg.cannonPercent.f());
            GilUtil.launch(e, g, 0.5, 1.2);
        }
        // a glowing golden mark stays where it struck
        level.addFreshEntity(GilFxEntity.create(level, GilFxEntity.MARK, 0, 110, (float) (r * 0.9), g.add(0, 0.05, 0)).path(g, g.add(0, 0.05, 0), 0, 0));
    }

    private static void spears(Session s, LivingEntity c, VimanaEntity v) {
        ServerLevel level = s.level;
        double ring = GilConfig.VIMANA.spearRing.get();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3 pp = v.position().add(Math.cos(a) * 5.5, 0.8 + (i % 2) * 0.8, Math.sin(a) * 5.5);
            double x = s.center.x + Math.cos(a + 0.4) * ring, z = s.center.z + Math.sin(a + 0.4) * ring;
            Vec3 g = new Vec3(x, GoldTarget.groundY(level, x, s.center.y + 12, z, 40, s.center.y), z);
            level.addFreshEntity(GilFxEntity.create(level, GilFxEntity.GIANT, 1, 30, 11f, pp).path(pp, g, 4, 8).burstAtEnd());
        }
        // the ninth, from straight above the centre
        Vec3 top = s.center.add(0, GilConfig.VIMANA.ascend.get() + 8, 0);
        GilUtil.portal(level, top, new Vec3(0, -1, 0), 2.6, true, 0.6f);
        level.addFreshEntity(GilFxEntity.create(level, GilFxEntity.GIANT, 1, 30, 16f, top).path(top, s.center, 6, 6).burstAtEnd());
    }

    private static void finale(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        var cfg = GilConfig.VIMANA;
        Vec3 g = s.center;
        double r = cfg.radius.get();
        GilUtil.sound(level, g, SoundEvents.GENERIC_EXPLODE, 6f, 0.5f);
        GilUtil.sound(level, g, SoundEvents.LIGHTNING_BOLT_THUNDER, 5f, 0.6f);
        GilUtil.sound(level, g, ModSounds.WEAPON_IMPACT.get(), 4f, 0.5f);
        FX.one(level, ModParticles.GOLD_GLOW.get(), g.add(0, 2, 0), new Vec3(r * 3.2, 0, 0));
        FX.one(level, ModParticles.GOLD_RING.get(), g.add(0, 2, 0), new Vec3(r * 2.0, 0, 0));
        FX.one(level, ModParticles.GOLD_GLOW.get(), g.add(0, 1, 0), new Vec3(r * 1.8, 0, 0));
        for (int i = 0; i < 4; i++) GilUtil.flatRing(level, g.add(0, 0.12 + i * 0.05, 0), r * (0.6 + i * 0.5));
        double ring = cfg.spearRing.get();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + 0.4;
            Vec3 p = g.add(Math.cos(a) * ring, 0.8, Math.sin(a) * ring);
            FX.one(level, ModParticles.GOLD_GLOW.get(), p, new Vec3(4.0, 0, 0));
            GilUtil.sparks(level, p, 24, 1.1);
        }
        GilUtil.sparks(level, g.add(0, 0.5, 0), 90, 1.8);
        for (LivingEntity e : GilDmg.around(level, c, g, r)) {
            GilDmg.hit(level, c, e, cfg.finalFlat.f(), cfg.finalPercent.f());
            GilUtil.launch(e, g, 0.6, 1.0);
        }
    }

    /** Tiny easing helpers (kept here so the manager has no client-side imports). */
    private static final class FxEase {
        static double smooth(double x) {
            x = Mth.clamp(x, 0.0, 1.0);
            return x * x * (3 - 2 * x);
        }

        static double ease(double x) {
            x = Mth.clamp(x, 0.0, 1.0);
            double k = 1 - x;
            return 1 - k * k * k;
        }
    }
}
