package com.example.jjkspells.prime;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.effect.SimpleDomainEffect;
import com.example.jjkspells.entity.UnlimitedVoidDome;
import com.example.jjkspells.spells.DioHold;
import com.example.jjkspells.spells.SpellDamage;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.jjkspells.prime.PrimeFx.*;

/**
 * Heavenly Onslaught: Unlimited Void and Hollow Purple: Nine Ropes fused.
 *   CHARGE  - the hand sign and a galaxy of stars, then the domain opens (much larger than Unlimited Void).
 *   ACTIVE  - everyone inside is Overloaded. At the configured second the caster charges at every creature in turn, tears its head off
 *             by hand, and impales whatever survives (those lose max health for a while). Then the caster returns to the centre
 *             and builds a Hollow Purple above the domain.
 *   IMPACT  - the Purple detonates with the end of the domain: a nuke that reaches well outside the domain.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class HeavenlyOnslaughtManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int CHARGE_TICKS = 64, EXPAND_TICK = 58, FINALE_TICKS = 32;
    private static final double SHELL_SPEED = 2.4;
    private static final int LINGER = 44;

    private enum Phase { CHARGE, ACTIVE, IMPACT }

    private static final class Strike {
        final LivingEntity target;
        final int at;
        int stage;

        Strike(LivingEntity t, int at) {
            this.target = t;
            this.at = at;
        }
    }

    private static final class Dom {
        final ServerLevel level;
        final UUID caster;
        final float R;
        final int D, rampStart;
        final PrimeBlocks blocks;
        final Set<UUID> hit = new HashSet<>();
        final List<Strike> strikes = new ArrayList<>();
        Phase phase = Phase.CHARGE;
        int age, phaseAge, impactAge;
        Vec3 center = Vec3.ZERO;
        double startY;
        UnlimitedVoidDome dome;
        boolean gathered, finaleStarted;

        Dom(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.caster = c.getUUID();
            this.R = PrimeConfig.HO_RADIUS.get().floatValue();
            this.D = PrimeConfig.HO_SECONDS.get() * 20;
            this.rampStart = Math.min(PrimeConfig.HO_RAMPAGE_START.get() * 20, this.D - FINALE_TICKS - 10);
            this.blocks = new PrimeBlocks(level, c);
        }
    }

    private static final List<Dom> ACTIVE = new ArrayList<>();

    private HeavenlyOnslaughtManager() {
    }

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Dom(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Dom> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Dom v = it.next();
            Entity e = v.level.getEntity(v.caster);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done = false;
            try {
                switch (v.phase) {
                    case CHARGE -> {
                        if (c == null) done = true;
                        else charge(v, c);
                    }
                    case ACTIVE -> done = active(v, c);
                    case IMPACT -> done = impact(v, c);
                }
            } catch (Exception ex) {
                LOGGER.error("Heavenly Onslaught failed, cancelling it", ex);
                done = true;
            }
            if (done) {
                if (v.dome != null) v.dome.discard();
                release(v);
            }
            v.age++;
            v.phaseAge++;
            if (done || v.age > 3600) it.remove();
        }
    }

    /** Nobody stays locked after the spell, whatever ended it. */
    private static void release(Dom v) {
        if (v.center.equals(Vec3.ZERO)) return;
        for (LivingEntity e : v.level.getEntitiesOfClass(LivingEntity.class, new AABB(v.center, v.center).inflate(PrimeConfig.HO_BLAST_RADIUS.get()))) {
            PrimeStatus.unlock(e);
        }
    }

    // ============================================================================================ CHARGE
    private static void charge(Dom v, LivingEntity c) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        int t = v.age;
        double k = Math.min(1.0, t / (double) EXPAND_TICK);
        double sm = smooth(k);
        Basis b = Basis.of(c.getLookAngle());
        Vec3 f = b.f(), r = b.r(), u = b.u();
        Vec3 feet = c.position();
        Vec3 H = c.getEyePosition().add(f.scale(1.4)).subtract(u.scale(0.45));

        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        if (t % 10 == 0) SimpleDomainEffect.grant(c, 40);
        if (t == 0) {
            sound(level, feet, SoundEvents.BEACON_ACTIVATE, 5f, 0.5f);
            title(level, feet, Component.literal("DOMAIN EXPANSION").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), Component.literal("HEAVENLY ONSLAUGHT").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (t == 32) sound(level, feet, SoundEvents.RESPAWN_ANCHOR_CHARGE, 5f, 0.5f);
        if (t % 8 == 0) sound(level, H, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, (float) (0.5 + k));

        double gy = feet.y + 0.06;
        double rho = 2 + (14.0 - 2.0) * sm;
        double spin = t * 0.06;
        if (t % 2 == 0) {
            for (int i = 0; i < 16; i++) {
                double a = spin + i * Math.PI * 2 / 16;
                Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                line(level, ModParticles.UV_LINE.get(), new Vec3(feet.x, gy, feet.z).add(d.scale(rho * 0.12)), d.scale(rho * 0.78));
            }
        }
        if (t % 3 == 0) {
            polygon(level, feet, gy, rho, 36, spin * 0.6);
            polygon(level, feet, gy, rho * 0.6, 20, -spin);
            for (int i = 0; i < 10; i++) {
                double a0 = -spin * 0.8 + i * Math.PI * 2 / 10, a1 = -spin * 0.8 + (i + 4) * Math.PI * 2 / 10;
                Vec3 pa = new Vec3(feet.x + Math.cos(a0) * rho * 0.84, gy, feet.z + Math.sin(a0) * rho * 0.84);
                Vec3 pb = new Vec3(feet.x + Math.cos(a1) * rho * 0.84, gy, feet.z + Math.sin(a1) * rho * 0.84);
                line(level, ModParticles.HP_LINE.get(), pa, pb.subtract(pa));
            }
        }
        if (t % 10 == 0) glow(level, ModParticles.UV_RING_FLAT.get(), new Vec3(feet.x, gy, feet.z), (float) (rho * 1.3));

        int rush = 5 + t / 4;
        for (int i = 0; i < rush; i++) {
            Vec3 d = unit(rng);
            Vec3 p = H.add(d.scale(10 + rng.nextDouble() * 22));
            one(level, ModParticles.UV_STAR_IN.get(), p, H.subtract(p).scale(1.0 / 20.0));
        }
        for (int arm = 0; arm < 4; arm++) {
            for (int n = 0; n < 2; n++) {
                double s = ((t * 0.06 + arm / 4.0 + n * 0.5) % 1.0);
                double rad = 0.2 + 5.5 * Math.pow(1 - s, 1.6) * (0.35 + 0.65 * sm);
                double ang = s * 6.5 + t * 0.14 + arm * Math.PI / 2;
                Vec3 p = H.add(r.scale(Math.cos(ang) * rad)).add(u.scale(Math.sin(ang) * rad)).add(f.scale((rng.nextDouble() - 0.5) * 0.2));
                one(level, ModParticles.UV_STAR_IN.get(), p, Vec3.ZERO);
            }
        }
        if (t >= 6) {
            double rs = 0.12 + 0.75 * sm + (t > EXPAND_TICK - 8 ? (t - (EXPAND_TICK - 8)) * 0.12 : 0);
            glow(level, ModParticles.UV_GLOW.get(), H, (float) (rs * 3.6));
            glow(level, ModParticles.UV_GLOW.get(), H, (float) (rs * 1.9));
            glow(level, ModParticles.UV_CORE.get(), H, (float) rs);
            glow(level, ModParticles.HP_GLOW.get(), H, (float) (rs * 2.2));
            if (t % 4 == 0) glow(level, ModParticles.UV_RING.get(), H, (float) (rs * 5));
            for (int i = 0; i < 6; i++) {
                double a = t * 0.4 + i * Math.PI * 2 / 6;
                Vec3 off = r.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
                Vec3 tang = r.scale(-Math.sin(a)).add(u.scale(Math.cos(a)));
                one(level, ModParticles.UV_STAR_IN.get(), H.add(off.scale(rs * 2.2)), tang.scale(0.16));
            }
        }
        if (t >= 8) {
            double ang = t * 0.12, len = 0.4 + 1.2 * sm;
            Vec3 d1 = r.scale(Math.cos(ang)).add(u.scale(Math.sin(ang)));
            Vec3 d2 = r.scale(-Math.sin(ang)).add(u.scale(Math.cos(ang)));
            line(level, ModParticles.UV_LINE.get(), H.subtract(d1.scale(len)), d1.scale(len * 2));
            line(level, ModParticles.UV_LINE.get(), H.subtract(d2.scale(len)), d2.scale(len * 2));
        }
        if (t >= EXPAND_TICK) expand(v, c, H);
    }

    private static void expand(Dom v, LivingEntity c, Vec3 H) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        float R = v.R;
        Vec3 center = c.position();
        v.center = center;
        v.startY = center.y;
        sound(level, center, SoundEvents.END_PORTAL_SPAWN, 6f, 0.7f);
        sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 6f, 0.4f);
        sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 6f, 0.6f);
        glow(level, ModParticles.UV_CORE.get(), H, 3f);
        glow(level, ModParticles.UV_GLOW.get(), H, 16f);
        glow(level, ModParticles.HP_GLOW.get(), H, 22f);
        for (int i = 0; i < 6; i++) glow(level, ModParticles.UV_RING.get(), H, R * (0.3f + i * 0.16f));
        glow(level, ModParticles.UV_RING_FLAT.get(), center.add(0, 0.08, 0), R);
        glow(level, ModParticles.UV_RING_FLAT.get(), center.add(0, 0.08, 0), R * 0.66f);
        glow(level, ModParticles.UV_RING_FLAT.get(), center.add(0, 0.08, 0), R * 0.33f);
        for (int i = 0; i < 160; i++) one(level, ModParticles.UV_STAR.get(), H, unit(rng).scale(0.3 + rng.nextDouble() * 1.4));
        for (int i = 0; i < 50; i++) {
            Vec3 d = unit(rng);
            line(level, ModParticles.UV_LINE.get(), H.add(d.scale(0.5)), d.scale(4 + rng.nextDouble() * 10));
        }
        UnlimitedVoidDome dome = new UnlimitedVoidDome(level, R, v.D + 2);
        dome.setPos(center.x, center.y, center.z);
        level.addFreshEntity(dome);
        v.dome = dome;
        v.phase = Phase.ACTIVE;
        v.phaseAge = -1;
    }

    // ============================================================================================ ACTIVE
    private static boolean active(Dom v, LivingEntity c) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        int t = v.phaseAge;
        float R = v.R;
        Vec3 C = v.center;
        if (c == null) {
            sound(level, C, SoundEvents.BEACON_DEACTIVATE, 5f, 0.6f);
            return true;
        }
        c.removeEffect(ModEffects.OVERLOAD.get());
        if (t % 10 == 0) SimpleDomainEffect.grant(c, 40);
        c.fallDistance = 0;

        // ---- everyone inside is Overloaded (and cannot attack or cast, even if immune to effects) ----------------
        if (t % 4 == 0) {
            int remaining = v.D - t;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(C, C).inflate(R), LivingEntity::isAlive)) {
                if (!foe(c, e)) continue;
                if (e.getBoundingBox().getCenter().distanceTo(C) > R) continue;
                PrimeStatus.lock(e, 10);
                if (!e.hasEffect(ModEffects.SIMPLE_DOMAIN.get())) {
                    MobEffectInstance cur = e.getEffect(ModEffects.OVERLOAD.get());
                    if (cur == null || cur.getDuration() < remaining) {
                        e.addEffect(new MobEffectInstance(ModEffects.OVERLOAD.get(), Math.max(2, remaining), 0, false, true, true), c);
                    }
                }
                SpellDamage.irradiate(e, 60);
                if (!e.hasEffect(ModEffects.OVERLOAD.get())) {
                    DioHold.hold(e, 10);
                    OverloadEvents.blockHealing(e, 10);
                }
            }
        }

        // ---- the sky of the domain ---------------------------------------------------------------------
        int n = 12 + (int) (R * 0.6);
        for (int i = 0; i < n; i++) {
            Vec3 p = inSphere(rng, R * 0.97);
            one(level, ModParticles.UV_STAR.get(), C.add(p), new Vec3(-p.z, 0, p.x).scale(0.02 / Math.max(4, R)).add(rnd(rng).scale(0.006)));
        }
        for (int i = 0; i < 8; i++) one(level, ModParticles.UV_STAR.get(), c.position().add(inSphere(rng, 10)).add(0, 1, 0), rnd(rng).scale(0.01));
        int nf = 8 + (int) (R * 0.4);
        for (int i = 0; i < nf; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * R * 0.97;
            double x = C.x + Math.cos(a) * d, z = C.z + Math.sin(a) * d;
            Vec3 fl = floor(level, new Vec3(x, C.y + 6, z));
            if (fl.y > C.y + R * 0.5) continue;
            one(level, ModParticles.UV_FLOOR.get(), new Vec3(x, fl.y + 0.04, z), new Vec3(-(z - C.z), 0, x - C.x).scale(0.02 / Math.max(4, R)));
        }
        if (t % 30 == 0) glow(level, ModParticles.UV_RING_FLAT.get(), C.add(0, 0.08, 0), R);
        if (t % 40 == 0) sound(level, C, SoundEvents.PORTAL_AMBIENT, 4f, 0.5f);
        if (t % 8 == 0) {
            for (int i = 0; i < 2; i++) {
                Vec3 a = C.add(unit(rng).scale(R * 0.95)), bb = C.add(unit(rng).scale(R * 0.95));
                bolt(level, ModParticles.UV_LINE.get(), a, bb, R * 0.04, 8);
                if (t >= v.rampStart) bolt(level, ModParticles.FUGA_LINE.get(), C.add(unit(rng).scale(R * 0.9)), C.add(unit(rng).scale(R * 0.9)), R * 0.04, 8);
            }
        }

        // ---- the rampage ---------------------------------------------------------------------------------
        int finaleAt = v.D - FINALE_TICKS;
        if (t == v.rampStart) gather(v, c);
        if (!v.strikes.isEmpty()) rampage(v, c, t);

        // ---- the finale: home to the centre, a Purple above the domain ---------------------------------------
        if (t == finaleAt) {
            v.finaleStarted = true;
            Vec3 home = C.add(0, 3.0, 0);
            tp(c, home, null);
            c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, false));
            sound(level, home, SoundEvents.ENDERMAN_TELEPORT, 5f, 0.5f);
            glow(level, ModParticles.HP_CORE.get(), home, 6f);
            for (int i = 0; i < 3; i++) glow(level, ModParticles.UV_RING.get(), home, 4f + i * 4f);
            title(level, C, Component.literal("HOLLOW TECHNIQUE").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), Component.literal("NINTH ROPE").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (v.finaleStarted && t < v.D) finale(v, c, t - finaleAt);

        if (t >= v.D) {
            detonate(v, c);
        }
        return false;
    }

    private static void gather(Dom v, LivingEntity c) {
        v.gathered = true;
        List<LivingEntity> pool = new ArrayList<>();
        for (LivingEntity e : v.level.getEntitiesOfClass(LivingEntity.class, new AABB(v.center, v.center).inflate(v.R), LivingEntity::isAlive)) {
            if (foe(c, e) && e.getBoundingBox().getCenter().distanceTo(v.center) <= v.R) pool.add(e);
        }
        int max = PrimeConfig.HO_MAX_TARGETS.get();
        Vec3 at = c.position();
        List<LivingEntity> order = new ArrayList<>();
        while (!pool.isEmpty() && order.size() < max) {
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : pool) {
                double d = e.position().distanceToSqr(at);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            pool.remove(best);
            order.add(best);
            at = best.position();
        }
        int window = Math.max(6, (v.D - FINALE_TICKS - 4) - v.rampStart);
        int n = order.size();
        for (int i = 0; i < n; i++) {
            v.strikes.add(new Strike(order.get(i), v.rampStart + (int) ((long) i * (window - 3) / Math.max(1, n))));
        }
        sound(v.level, v.center, SoundEvents.WARDEN_ROAR, 6f, 0.7f);
        announce(v.level, c, Component.literal("Heavenly Onslaught").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    private static void rampage(Dom v, LivingEntity c, int t) {
        for (Strike s : v.strikes) {
            if (s.stage >= 3) continue;
            if (t < s.at + s.stage) continue;
            LivingEntity e = s.target;
            if (!e.isAlive() || e.isRemoved()) {
                s.stage = 3;
                continue;
            }
            switch (s.stage) {
                case 0 -> dash(v, c, e);
                case 1 -> rip(v, c, e);
                default -> impale(v, c, e);
            }
            s.stage++;
        }
    }

    private static void dash(Dom v, LivingEntity c, LivingEntity e) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        Vec3 from = c.position();
        Vec3 tp = e.position();
        Vec3 flat = new Vec3(from.x - tp.x, 0, from.z - tp.z);
        flat = flat.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : flat.normalize();
        Vec3 dest = tp.add(flat.scale(e.getBbWidth() * 0.5 + 1.15));
        sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 3f, 0.6f);
        // the streak the caster leaves behind
        Vec3 span = dest.subtract(from);
        double len = span.length();
        if (len > 0.5) {
            Vec3 d = span.scale(1.0 / len);
            for (double at = 0; at < len; at += 1.4) {
                Vec3 p = from.add(d.scale(at)).add(0, 1, 0);
                glow(level, ModParticles.HP_GLOW.get(), p, 2.2f);
                one(level, ModParticles.HP_WISP.get(), p, unit(rng).scale(0.12));
            }
            line(level, ModParticles.HP_LINE.get(), from.add(0, 1, 0), span);
        }
        glow(level, ModParticles.HP_CORE.get(), from.add(0, 1, 0), 3f);
        glow(level, ModParticles.HP_RING.get(), dest.add(0, 1, 0), 4f);
        tp(c, dest, e.getEyePosition());
    }

    private static void rip(Dom v, LivingEntity c, LivingEntity e) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        Vec3 head = e.getEyePosition();
        Vec3 hand = c.getEyePosition().add(0, -0.35, 0);
        float flat = PrimeConfig.HO_FLAT.get().floatValue(), pct = PrimeConfig.HO_PERCENT.get().floatValue();
        tp(c, c.position(), head);
        line(level, ModParticles.FUGA_BEAM.get(), hand, head.subtract(hand));
        sound(level, head, SoundEvents.PLAYER_ATTACK_STRONG, 4f, 0.5f);
        sound(level, head, SoundEvents.LIGHTNING_BOLT_IMPACT, 3f, 1.8f);
        smite(level, ModSpells.HEAVENLY_ONSLAUGHT.get(), c, e, flat, pct);
        one(level, ModParticles.RED_IMPACT.get(), head, new Vec3(2.4, 0, 0));
        glow(level, ModParticles.HP_CORE.get(), head, 3.5f);
        for (int i = 0; i < 36; i++) one(level, ModParticles.RED_SPARK.get(), head, unit(rng).scale(0.4 + rng.nextDouble() * 0.9));
        if (!e.isAlive()) {
            // the head comes off and is flung away
            sound(level, head, SoundEvents.GENERIC_EXPLODE, 2f, 1.6f);
            for (int i = 0; i < 24; i++) one(level, ModParticles.RED_SPARK.get(), head, new Vec3((rng.nextDouble() - 0.5) * 0.6, 0.5 + rng.nextDouble() * 0.8, (rng.nextDouble() - 0.5) * 0.6));
            for (int i = 0; i < 10; i++) one(level, ModParticles.HP_WISP_RED.get(), head, unit(rng).scale(0.5));
            glow(level, ModParticles.HP_GLOW_RED.get(), head, 5f);
            redRing(level, head, 3.5f, false);
        }
    }

    private static void impale(Dom v, LivingEntity c, LivingEntity e) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        Vec3 chest = e.position().add(0, e.getBbHeight() * 0.6, 0);
        Vec3 hand = c.getEyePosition().add(0, -0.5, 0);
        Vec3 through = chest.subtract(hand);
        through = through.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : through.normalize();
        Vec3 out = chest.add(through.scale(e.getBbWidth() + 2.5));
        line(level, ModParticles.FUGA_BEAM.get(), hand, out.subtract(hand));
        line(level, ModParticles.HP_LINE.get(), hand, out.subtract(hand));
        bolt(level, ModParticles.FUGA_LINE.get(), hand, out, 0.4, 5);
        sound(level, chest, SoundEvents.TRIDENT_HIT, 4f, 0.5f);
        sound(level, chest, SoundEvents.PLAYER_ATTACK_CRIT, 4f, 0.6f);
        one(level, ModParticles.RED_IMPACT.get(), chest, new Vec3(3.0, 0, 0));
        redRing(level, chest, 4.5f, false);
        float flat = PrimeConfig.HO_FLAT.get().floatValue(), pct = PrimeConfig.HO_PERCENT.get().floatValue();
        if (PrimeConfig.HO_IMPALE_REPEATS.get()) smite(level, ModSpells.HEAVENLY_ONSLAUGHT.get(), c, e, flat, pct);
        for (int i = 0; i < 28; i++) one(level, ModParticles.RED_SPARK.get(), chest, through.scale(0.4 + rng.nextDouble() * 1.2).add(unit(rng).scale(0.25)));
        if (e.isAlive()) {
            PrimeStatus.shrink(e, PrimeConfig.HO_MAXHP_LOSS.get(), PrimeConfig.HO_MAXHP_SECONDS.get() * 20);
            flakes(level, e, ModParticles.HP_WISP_RED.get(), 14);
        }
    }

    /** The two spheres close in above the caster and fuse into a Purple the size of a house. */
    private static void finale(Dom v, LivingEntity c, int k) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        Vec3 P = v.center.add(0, 12, 0);
        double q = Math.min(1.0, k / (double) (FINALE_TICKS - 6));
        double sep = 9.0 * (1 - q * q) + 0.1;
        double a = k * 0.3 + k * k * 0.012;
        Vec3 dv = new Vec3(Math.cos(a), 0, Math.sin(a));
        Vec3 tang = new Vec3(-Math.sin(a), 0, Math.cos(a));
        if (k % 4 == 0) {
            c.setDeltaMovement(Vec3.ZERO);
            c.hurtMarked = true;
        }
        if (k < FINALE_TICKS - 6) {
            Vec3 blue = P.add(dv.scale(sep)), red = P.subtract(dv.scale(sep));
            float gr = (float) (1.5 + 2.5 * q);
            glow(level, ModParticles.HP_GLOW_BLUE.get(), blue, gr * 2.6f);
            glow(level, ModParticles.HP_CORE.get(), blue, gr * 0.9f);
            glow(level, ModParticles.HP_GLOW_RED.get(), red, gr * 2.6f);
            glow(level, ModParticles.HP_CORE.get(), red, gr * 0.9f);
            for (int i = 0; i < 6; i++) {
                Vec3 p = blue.add(rnd(rng).scale(3));
                one(level, ModParticles.HP_WISP_BLUE.get(), p, blue.subtract(p).scale(0.14).add(tang.scale(0.2)));
                Vec3 p2 = red.add(rnd(rng).scale(0.8));
                one(level, ModParticles.HP_WISP_RED.get(), p2, p2.subtract(red).normalize().scale(0.35).add(tang.scale(-0.2)));
            }
            bolt(level, ModParticles.HP_LINE.get(), blue, red, 1.2, 5);
            if (k % 4 == 0) sound(level, P, SoundEvents.AMETHYST_BLOCK_RESONATE, 4f, (float) (0.5 + q));
            if (k % 5 == 0) glow(level, ModParticles.HP_RING.get(), P, (float) (3 + q * 10));
        } else {
            int m = k - (FINALE_TICKS - 6);
            float rad = (float) (1.0 + 8.0 * smooth(m / 6.0));
            if (m == 0) {
                sound(level, P, SoundEvents.LIGHTNING_BOLT_IMPACT, 8f, 1.2f);
                sound(level, P, SoundEvents.AMETHYST_BLOCK_RESONATE, 8f, 0.4f);
                glow(level, ModParticles.HP_CORE.get(), P, 20f);
                glow(level, ModParticles.HP_GLOW.get(), P, 34f);
                for (int i = 0; i < 6; i++) glow(level, ModParticles.HP_RING.get(), P, 8f + i * 5f);
            }
            glow(level, ModParticles.HP_GLOW.get(), P, rad * 2f);
            glow(level, ModParticles.HP_CORE.get(), P, rad * 0.9f);
            for (int i = 0; i < 24; i++) {
                double ang = k * 0.4 + i * Math.PI * 2 / 24;
                Vec3 off = new Vec3(Math.cos(ang), Math.sin(ang * 0.5), Math.sin(ang)).normalize();
                one(level, ModParticles.HP_WISP.get(), P.add(off.scale(rad * 1.05)), new Vec3(-Math.sin(ang), 0, Math.cos(ang)).scale(0.3));
            }
            for (int i = 0; i < 8; i++) {
                Vec3 p1 = unit(rng);
                Vec3 s1 = P.add(p1.scale(rad));
                line(level, ModParticles.HP_LINE.get(), s1, p1.add(rnd(rng).scale(0.6)).normalize().scale(rad * 0.7));
            }
        }
    }

    // ============================================================================================ DETONATION
    private static void detonate(Dom v, LivingEntity c) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        Vec3 M = v.center.add(0, 12, 0);
        if (v.dome != null) {
            v.dome.discard();
            v.dome = null;
        }
        v.phase = Phase.IMPACT;
        v.impactAge = 0;
        sound(level, M, SoundEvents.WARDEN_SONIC_BOOM, 10f, 0.4f);
        sound(level, M, SoundEvents.GENERIC_EXPLODE, 12f, 0.35f);
        sound(level, M, SoundEvents.LIGHTNING_BOLT_THUNDER, 12f, 0.3f);
        sound(level, c.position(), SoundEvents.END_GATEWAY_SPAWN, 8f, 0.6f);
        glow(level, ModParticles.HP_CORE.get(), M, 60f);
        glow(level, ModParticles.HP_GLOW.get(), M, 90f);
        for (int i = 0; i < 7; i++) glow(level, ModParticles.HP_RING.get(), M, 24f + i * 14f);
        for (int i = 0; i < 260; i++) one(level, ModParticles.HP_WISP.get(), M, unit(rng).scale(0.7 + rng.nextDouble() * 2.0));
        for (int i = 0; i < 100; i++) {
            Vec3 d = unit(rng);
            line(level, ModParticles.HP_LINE.get(), M.add(d.scale(6)), d.scale(16 + rng.nextDouble() * 34));
        }
        c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false, false));
    }

    private static boolean impact(Dom v, LivingEntity c) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        int i = v.impactAge++;
        Vec3 C = v.center;
        Vec3 M = C.add(0, 12, 0);
        int RC = PrimeConfig.HO_BLAST_RADIUS.get();
        double cur = Math.min(RC, (i + 1) * SHELL_SPEED);
        double prev = Math.min(RC, i * SHELL_SPEED);
        int shellTicks = (int) Math.ceil(RC / SHELL_SPEED);
        v.blocks.newTick();

        if (cur > prev) {
            if (JJKConfig.breaks(PrimeConfig.HO_BREAKS)) breakShell(v, cur, prev, RC);
            damageShell(v, c, cur, RC);
        }
        if (i <= shellTicks) {
            for (int n = 0; n < 90; n++) {
                Vec3 d = unit(rng);
                one(level, ModParticles.HP_WISP.get(), M.add(d.scale(cur)), d.scale(0.3 + rng.nextDouble() * 0.4));
            }
            for (int n = 0; n < 24; n++) {
                Vec3 d = unit(rng);
                one(level, ModParticles.HP_VOID.get(), M.add(d.scale(cur * (0.9 + rng.nextDouble() * 0.1))), d.scale(0.1));
            }
            // a wall of dust racing along the ground
            for (int n = 0; n < 70; n++) {
                double a = rng.nextDouble() * Math.PI * 2;
                Vec3 g = floor(level, new Vec3(C.x + Math.cos(a) * cur, C.y + 8, C.z + Math.sin(a) * cur));
                one(level, ModParticles.HP_WISP.get(), g.add(0, 0.5 + rng.nextDouble() * 3, 0), new Vec3(Math.cos(a) * 0.5, 0.25, Math.sin(a) * 0.5));
            }
            if (i % 2 == 0) {
                glow(level, ModParticles.HP_RING.get(), M, (float) (cur * 1.15));
                glow(level, ModParticles.HP_RING_FLAT.get(), new Vec3(C.x, v.startY + 0.1, C.z), (float) (cur + 10));
            }
            if (i % 2 == 0) {
                for (int n = 0; n < 6; n++) {
                    Vec3 d = unit(rng);
                    line(level, ModParticles.HP_LINE.get(), M.add(d.scale(cur * 0.85)), d.scale(10 + rng.nextDouble() * 20));
                }
            }
            if (i < 22) {
                glow(level, ModParticles.HP_CORE.get(), M, (float) Math.min(70, 30 + cur * 0.6));
                glow(level, ModParticles.HP_GLOW.get(), M, (float) Math.min(110, 50 + cur));
            }
            if (i % 6 == 0) sound(level, M, SoundEvents.GENERIC_EXPLODE, 10f, 0.5f + rng.nextFloat() * 0.2f);
        }
        // mushroom: a stem of light and a rolling cap
        if (i < 60) {
            for (int n = 0; n < 20; n++) {
                double a = rng.nextDouble() * Math.PI * 2, d = rng.nextDouble() * 9;
                one(level, ModParticles.HP_WISP.get(), C.add(Math.cos(a) * d, 1 + rng.nextDouble() * 6, Math.sin(a) * d), new Vec3(0, 0.9 + rng.nextDouble() * 1.4, 0));
            }
            if (i >= 4) {
                double H = 14 + i * 1.9;
                double capR = Math.min(34, 7 + i * 0.95);
                for (int n = 0; n < 72; n++) {
                    double a = n * Math.PI * 2 / 72 + i * 0.2;
                    one(level, ModParticles.HP_WISP.get(), C.add(Math.cos(a) * capR * 0.5, H + Math.sin(a * 3) * 2, Math.sin(a) * capR * 0.5),
                            new Vec3(Math.cos(a) * 0.35, 0.12, Math.sin(a) * 0.35));
                }
                if (i % 2 == 0) glow(level, ModParticles.HP_RING.get(), C.add(0, H, 0), (float) (capR * 1.2));
                glow(level, ModParticles.HP_GLOW.get(), C.add(0, H, 0), (float) (capR * 0.9));
            }
        }
        return i >= shellTicks + LINGER;
    }

    /** Erases the shell of terrain the blast front crosses this tick. Only the cells of the shell are visited. */
    private static void breakShell(Dom v, double cur, double prev, int RC) {
        Vec3 C = v.center;
        Vec3 M = C.add(0, 12, 0);
        int ox = (int) Math.floor(C.x), oy = (int) Math.floor(C.y), oz = (int) Math.floor(C.z);
        int ext = (int) Math.ceil(cur);
        double cur2 = cur * cur, prev2 = prev * prev;
        for (int x = -ext; x <= ext; x++) {
            for (int z = -ext; z <= ext; z++) {
                double hd2 = x * x + z * z;
                if (hd2 > cur2) continue;
                double lo2 = Math.max(0, prev2 - hd2), hi2 = cur2 - hd2;
                // above the centre the crater is as deep as it is wide; below it, half as deep
                int upLo = (int) Math.ceil(Math.sqrt(lo2)), upHi = (int) Math.floor(Math.sqrt(hi2));
                for (int y = upLo; y <= upHi; y++) shellCell(v, ox + x, oy + y, oz + z, x, y, z, RC, M);
                int dnLo = (int) Math.ceil(Math.sqrt(lo2) / 2.0), dnHi = (int) Math.floor(Math.sqrt(hi2) / 2.0);
                for (int y = Math.max(1, dnLo); y <= dnHi; y++) shellCell(v, ox + x, oy - y, oz + z, x, -y, z, RC, M);
            }
        }
    }

    private static void shellCell(Dom v, int bx, int by, int bz, int x, int y, int z, int RC, Vec3 from) {
        double dy = y < 0 ? y * 2.0 : y;
        double d = Math.sqrt(x * x + dy * dy + z * z);
        if (d > RC * (0.93 + 0.07 * PrimeBlocks.jag(bx, by, bz))) return;
        v.blocks.erase(bx, by, bz, from, 1.6);
    }

    private static void damageShell(Dom v, LivingEntity c, double cur, int RC) {
        ServerLevel level = v.level;
        Vec3 C = v.center;
        Vec3 M = C.add(0, 12, 0);
        float pct = PrimeConfig.HO_BLAST_PERCENT.get().floatValue();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(M, M).inflate(cur), LivingEntity::isAlive)) {
            if (!foe(c, e) || v.hit.contains(e.getUUID())) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double d = ec.distanceTo(C.add(0, 4, 0));
            if (d > cur) continue;
            v.hit.add(e.getUUID());
            double fall = d <= v.R ? 1.0 : 1.0 - 0.6 * Math.min(1.0, (d - v.R) / Math.max(1.0, RC - v.R));
            PrimeStatus.unlock(e);
            smite(level, ModSpells.HEAVENLY_ONSLAUGHT.get(), c, e, 0f, (float) (pct * fall));
            Vec3 out = ec.subtract(C);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
            double push = 1.5 + 4.0 * (1.0 - Math.min(1.0, d / RC));
            e.setDeltaMovement(out.x * push, out.y * push * 0.4 + 0.9, out.z * push);
            OverloadEvents.forceMove(e);
            e.hurtMarked = true;
            e.hasImpulse = true;
            glow(level, ModParticles.HP_CORE.get(), ec, 4f);
        }
    }

    // ============================================================================================ helpers
    private static void tp(LivingEntity c, Vec3 p, Vec3 face) {
        float yaw = c.getYRot(), pitch = c.getXRot();
        if (face != null) {
            Vec3 d = face.subtract(p.add(0, c.getEyeHeight(), 0));
            yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        }
        if (c instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, yaw, pitch);
        } else {
            c.moveTo(p.x, p.y, p.z, yaw, pitch);
        }
        c.setDeltaMovement(Vec3.ZERO);
        c.fallDistance = 0;
    }

    private static Vec3 inSphere(RandomSource rng, double radius) {
        Vec3 p;
        do {
            p = rnd(rng);
        } while (p.lengthSqr() > 1);
        return p.scale(radius);
    }

    private static void polygon(ServerLevel level, Vec3 feet, double gy, double rad, int segs, double spin) {
        for (int i = 0; i < segs; i++) {
            double a0 = spin + i * Math.PI * 2 / segs, a1 = spin + (i + 1) * Math.PI * 2 / segs;
            Vec3 pa = new Vec3(feet.x + Math.cos(a0) * rad, gy, feet.z + Math.sin(a0) * rad);
            Vec3 pb = new Vec3(feet.x + Math.cos(a1) * rad, gy, feet.z + Math.sin(a1) * rad);
            line(level, ModParticles.UV_LINE.get(), pa, pb.subtract(pa));
        }
    }
}
