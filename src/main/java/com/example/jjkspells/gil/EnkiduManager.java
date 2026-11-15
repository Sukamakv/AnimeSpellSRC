package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.effect.StunEffect;
import com.example.jjkspells.gil.entity.GilFxEntity;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.GoldTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
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
 * Enkidu, Chains of Heaven.
 *   0-10     a golden seal under the target, four portals open around it
 *   10-20    four chains curve out of the portals toward the head, torso and both arms
 *   20-      the chains bind: initial damage, stun (a boss is only slowed and capped at 3 s), the target hangs half a
 *            block above the ground, takes damage every second; secondary chains root up to four nearby enemies
 *   end-20..end the chains tighten, runes spin, a final shockwave shatters them
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class EnkiduManager {
    private static final int UNLEASH = 10, BIND = 20;

    private static final class Session {
        final ServerLevel level;
        final UUID casterId, targetId;
        final boolean divine, boss;
        final int bindTicks;
        final float phase;
        final List<UUID> secondaries = new ArrayList<>();
        final Vec3[] portals = new Vec3[4];
        Vec3 last;
        double hoverY = Double.NaN;
        int age;

        Session(ServerLevel level, LivingEntity c, LivingEntity t) {
            this.level = level;
            this.casterId = c.getUUID();
            this.targetId = t.getUUID();
            this.divine = GilDmg.isDivine(t);
            this.boss = GilDmg.isBoss(t);
            this.bindTicks = (divine ? GilConfig.ENKIDU.divineSeconds.i() : GilConfig.ENKIDU.seconds.i()) * 20;
            this.phase = level.random.nextFloat() * Mth.TWO_PI;
            this.last = t.position();
            Vec3 p = t.position();
            double w = Math.max(2.6, t.getBbWidth() * 1.6 + 1.8);
            for (int i = 0; i < 4; i++) {
                double a = phase + i * Math.PI / 2;
                portals[i] = p.add(Math.cos(a) * w, t.getBbHeight() * (0.55 + 0.25 * (i % 2)) + 0.3, Math.sin(a) * w);
            }
        }

        int end() {
            return bindTicks + 10;
        }
    }

    private static final List<Session> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster) {
        LivingEntity target = GilDmg.target(level, caster, GilConfig.ENKIDU.range.get());
        if (target == null || !GilDmg.canHit(caster, target)) {
            GilUtil.announce(caster, Component.literal("The chains find no one to bind").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
            return;
        }
        ACTIVE.add(new Session(level, caster, target));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Session> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            Entity te = s.level.getEntity(s.targetId);
            Entity ce = s.level.getEntity(s.casterId);
            boolean done;
            try {
                done = tick(s, te instanceof LivingEntity l && l.isAlive() ? l : null, ce instanceof LivingEntity c && c.isAlive() ? c : null);
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getLogger().error("Enkidu failed", ex);
                done = true;
            }
            s.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Session s, LivingEntity target, LivingEntity caster) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        int t = s.age;
        var cfg = GilConfig.ENKIDU;
        if (target == null) {
            shatter(s, s.last, 1f, 1.8f);
            return true;
        }
        s.last = target.position();
        Vec3 pos = target.position();
        float w = target.getBbWidth(), h = target.getBbHeight();

        if (t == 0) {
            GilUtil.sound(level, pos, ModSounds.CHAIN_BURST.get(), 2f, 1f);
            GilUtil.announce(caster, Component.literal("Enkidu").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            level.addFreshEntity(GilFxEntity.create(level, GilFxEntity.CIRCLE, 0, s.end() + 12, 3.6f + w, pos.add(0, 0.06, 0)).path(Vec3.ZERO, new Vec3(0, 1, 0), 0, 0));
            for (int i = 0; i < 4; i++) {
                Vec3 pp = s.portals[i];
                GilUtil.portal(level, pp, pos.add(0, h * 0.5, 0).subtract(pp), 0.8, true, 1.1f + i * 0.08f);
            }
        }
        if (t < UNLEASH && t % 2 == 0) {
            FX.one(level, ModParticles.GOLD_RUNE.get(), pos.add((rng.nextDouble() - 0.5) * 3, 0.15, (rng.nextDouble() - 0.5) * 3), new Vec3(0, 0.05, 0));
        }

        // ---- the chains fly out
        if (t >= UNLEASH && t < BIND) {
            float f = (t - UNLEASH + 1) / (float) (BIND - UNLEASH);
            for (int i = 0; i < 4; i++) {
                Vec3 from = s.portals[i];
                Vec3 to = pos.add(Math.cos(s.phase + i * 1.57 + 0.6) * (w * 0.5), h * (i == 0 ? 0.9 : i == 1 ? 0.55 : 0.7), Math.sin(s.phase + i * 1.57 + 0.6) * (w * 0.5));
                drawArc(level, from, to, f, 1.2 + i * 0.3);
            }
            if (t % 3 == 0) GilUtil.sound(level, pos, SoundEvents.CHAIN_PLACE, 1.2f, 0.7f + rng.nextFloat() * 0.4f);
        }

        // ---- the bind
        if (t == BIND) {
            GilUtil.sound(level, pos, ModSounds.CHAIN_SHATTER.get(), 1.6f, 1.6f);
            FX.one(level, ModParticles.GOLD_GLOW.get(), pos.add(0, h * 0.5, 0), new Vec3(4.5, 0, 0));
            FX.one(level, ModParticles.GOLD_RING.get(), pos.add(0, h * 0.5, 0), new Vec3(3.5, 0, 0));
            GilDmg.hit(level, caster, target, cfg.initial.f(), 0f);
            if (s.divine) GilState.divineBound(target, s.end() - BIND + 10);
            // chain propagation: up to four other enemies are rooted
            int left = cfg.secondaryTargets.i();
            for (LivingEntity e : GilDmg.around(level, caster, pos, cfg.secondaryRadius.get())) {
                if (e == target || left <= 0) continue;
                left--;
                s.secondaries.add(e.getUUID());
                e.addEffect(new MobEffectInstance(GilEffects.ROOTED.get(), (int) (cfg.secondarySeconds.get() * 20), 0, false, false, true));
                GilDmg.hit(level, caster, e, cfg.initial.f() * cfg.secondaryFraction.f(), 0f);
                Vec3 a = pos.add(0, h * 0.5, 0), b = e.getBoundingBox().getCenter();
                drawArc(level, a, b, 1f, 0.9);
                GilUtil.sound(level, b, SoundEvents.CHAIN_PLACE, 1.2f, 0.8f);
            }
        }
        if (t >= BIND) {
            int remaining = s.end() - t;
            if (s.boss) {
                if (t < BIND + Math.min(cfg.bossSeconds.i() * 20, remaining)) {
                    target.addEffect(new MobEffectInstance(GilEffects.HEAVEN_BOUND.get(), 6, 0, false, false, true));
                }
            } else {
                StunEffect.apply(target, 6);
                hover(s, target);
            }
            if (t % 2 == 0) emitChains(s, pos, w, h, t >= s.end() - 20);
            // damage every second
            if (t > BIND && (t - BIND) % 20 == 0 && t < s.end()) {
                float per = cfg.perSecond.f() * (s.divine ? cfg.divineMult.f() : 1f);
                GilDmg.hit(level, caster, target, per, 0f);
                GilUtil.sound(level, pos, SoundEvents.CHAIN_PLACE, 1.4f, 0.6f);
                FX.one(level, ModParticles.GOLD_GLINT.get(), pos.add(0, h * 0.5, 0), new Vec3(2.0, 0, 0));
            }
            if (t >= BIND && t % 3 == 0) {
                double a = rng.nextDouble() * Mth.TWO_PI, r = w * 0.7 + 0.25;
                FX.one(level, ModParticles.GOLD_RUNE.get(), pos.add(Math.cos(a) * r, rng.nextDouble() * h, Math.sin(a) * r), new Vec3(0, 0.02, 0));
            }
            // secondary chains stay drawn while their root lasts
            if (t % 3 == 0 && t < BIND + (int) (cfg.secondarySeconds.get() * 20)) {
                for (UUID id : s.secondaries) {
                    Entity e = level.getEntity(id);
                    if (e instanceof LivingEntity le && le.isAlive()) {
                        Vec3 a = pos.add(0, h * 0.5, 0), b = le.getBoundingBox().getCenter();
                        drawArc(level, a, b, 1f, 0.7);
                    }
                }
            }
            // constriction: everything tightens and spins
            if (t >= s.end() - 20 && t < s.end()) {
                float k = (t - (s.end() - 20)) / 20f;
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.5 + i * 2.1;
                    FX.one(level, ModParticles.GOLD_RUNE.get(), pos.add(Math.cos(a) * (w * 0.9 + 1.2 * (1 - k)), h * (0.2 + 0.3 * i), Math.sin(a) * (w * 0.9 + 1.2 * (1 - k))), new Vec3(0, 0.03, 0));
                }
                if (t == s.end() - 20) GilUtil.sound(level, pos, SoundEvents.CHAIN_PLACE, 2f, 0.4f);
                if (t % 4 == 0) FX.one(level, ModParticles.GOLD_GLOW.get(), pos.add(0, h * 0.5, 0), new Vec3(1.5 + 2.5 * k, 0, 0));
            }
        }
        if (t >= s.end()) {
            finale(s, caster, target);
            return true;
        }
        return false;
    }

    private static void hover(Session s, LivingEntity target) {
        if (target.isNoGravity() || target.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)) return;
        if (Double.isNaN(s.hoverY)) {
            double gy = GoldTarget.groundY(s.level, target.getX(), target.getY() + 0.5, target.getZ(), 8, target.getY());
            s.hoverY = gy + 0.5;
        }
        double vy = Mth.clamp((s.hoverY - target.getY()) * 0.3, -0.5, 0.5);
        target.setDeltaMovement(0, vy, 0);
        target.hurtMarked = true;
        target.fallDistance = 0;
    }

    /** Chain links along a sagging arc from {@code from} to {@code to}, drawn up to fraction {@code f}. */
    private static void drawArc(ServerLevel level, Vec3 from, Vec3 to, float f, double sag) {
        int n = 10;
        Vec3 prev = from;
        for (int i = 1; i <= n; i++) {
            double u = (double) i / n * f;
            Vec3 p = from.add(to.subtract(from).scale(u)).add(0, Math.sin(u * Math.PI) * sag, 0);
            FX.one(level, ModParticles.GOLD_CHAIN.get(), prev, p.subtract(prev).scale(1.04));
            prev = p;
        }
    }

    /** Spirals around the body plus four taut anchor chains to the portals. */
    private static void emitChains(Session s, Vec3 pos, float w, float h, boolean tight) {
        ServerLevel level = s.level;
        float spin = s.age * (tight ? 0.09f : 0.035f) + s.phase;
        double baseR = Math.max(0.45, w * 0.62 + 0.12) * (tight ? 0.88 : 1.0);
        for (int c = 0; c < 5; c++) {
            double lo = c * 0.17, hi = Math.min(1.0, lo + 0.42);
            double turns = 2.2 + (c % 2);
            double dir = c % 2 == 0 ? 1 : -1;
            double a0 = s.phase + c * 1.9;
            Vec3 prev = null;
            for (int k = 0; k <= 11; k++) {
                double f = (double) k / 11;
                double yf = lo + (hi - lo) * f;
                double ang = a0 + dir * (f * turns * Mth.TWO_PI) + dir * spin * 0.6;
                double r = baseR * (1.0 + 0.12 * Math.sin(f * 9 + c));
                Vec3 p = pos.add(Math.cos(ang) * r, yf * h, Math.sin(ang) * r);
                if (prev != null) FX.one(level, ModParticles.GOLD_CHAIN.get(), prev, p.subtract(prev).scale(1.04));
                prev = p;
            }
        }
        for (int k = 0; k < 4; k++) {
            Vec3 to = pos.add(Math.cos(s.phase + k * 1.57 + 0.4) * baseR, h * 0.5, Math.sin(s.phase + k * 1.57 + 0.4) * baseR);
            drawArc(level, s.portals[k], to, 1f, 0.35);
        }
    }

    private static void finale(Session s, LivingEntity caster, LivingEntity target) {
        ServerLevel level = s.level;
        var cfg = GilConfig.ENKIDU;
        Vec3 pos = target.position();
        float h = target.getBbHeight();
        float flat = cfg.finalFlat.f(), pct = cfg.finalPercent.f();
        GilDmg.hit(level, caster, target, flat, pct);
        double r = cfg.splashRadius.get();
        for (LivingEntity e : GilDmg.around(level, caster, pos, r)) {
            if (e == target) continue;
            GilDmg.hit(level, caster, e, flat * cfg.splashFraction.f(), pct * cfg.splashFraction.f());
            GilUtil.launch(e, pos, 0.4, 0.3);
        }
        GilState.clearDivine(target);
        shatter(s, pos, target.getBbWidth(), h);
        FX.one(level, ModParticles.GOLD_RING_FLAT.get(), pos.add(0, 0.1, 0), new Vec3(r * 1.5, 0, 0));
        FX.one(level, ModParticles.GOLD_RING.get(), pos.add(0, h * 0.5, 0), new Vec3(r * 1.2, 0, 0));
    }

    private static void shatter(Session s, Vec3 pos, float w, float h) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        GilUtil.sound(level, pos, ModSounds.CHAIN_SHATTER.get(), 2f, 1f);
        FX.one(level, ModParticles.GOLD_GLOW.get(), pos.add(0, h * 0.5, 0), new Vec3(4.0, 0, 0));
        FX.one(level, ModParticles.GOLD_RING_FLAT.get(), pos.add(0, 0.08, 0), new Vec3(5.0, 0, 0));
        for (int i = 0; i < 70; i++) {
            double a = rng.nextDouble() * Mth.TWO_PI, r = w * 0.6 + rng.nextDouble() * 0.6;
            Vec3 p = pos.add(Math.cos(a) * r, rng.nextDouble() * h, Math.sin(a) * r);
            Vec3 v = new Vec3(Math.cos(a) * (0.15 + rng.nextDouble() * 0.35), 0.1 + rng.nextDouble() * 0.35, Math.sin(a) * (0.15 + rng.nextDouble() * 0.35));
            FX.one(level, (i % 3 == 0 ? ModParticles.GOLD_SPARK : ModParticles.GOLD_SHARD).get(), p, v);
        }
    }
}
