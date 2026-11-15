package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every active Sun Breathing form. Each form is a short scripted sequence driven by its age in ticks:
 *   DANCE       lunge up to 12 blocks (invulnerable), then a downward flame slash.
 *   RADIANCE    0.4s thrust stance with a golden point at the tip, then an instant 12-block piercing dash.
 *   SETTING_SUN leap 5 blocks up, flip backward, a giant crescent erupts toward the ground.
 *   ETERNAL     awakening, twelve forms in a row, a final solar eruption.
 * Every hit is a multiple of the damage of the weapon in the caster's main hand.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class SunBreathingManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public enum Form { DANCE, RADIANCE, SETTING_SUN, ETERNAL }

    // ---- invulnerability while a form is in progress ---------------------------------------------
    private static final Map<UUID, Long> INVULN = new HashMap<>();

    private static void grant(ServerLevel level, LivingEntity c, int ticks) {
        long until = level.getGameTime() + ticks;
        INVULN.merge(c.getUUID(), until, Math::max);
    }

    private static boolean protectedNow(LivingEntity e) {
        Long until = INVULN.get(e.getUUID());
        if (until == null) return false;
        if (e.level().getGameTime() > until) {
            INVULN.remove(e.getUUID());
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (protectedNow(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (protectedNow(event.getEntity())) event.setCanceled(true);
    }

    // ---- sessions ----------------------------------------------------------------------------------
    private static class Sess {
        final Form form;
        final ServerLevel level;
        final UUID casterId;
        final AbstractSpell spell;
        final int lvl;
        int age = 0;
        // shared scratch
        Vec3 f = new Vec3(0, 0, 1);
        Vec3 origin = Vec3.ZERO;
        Vec3 dest = Vec3.ZERO;
        Vec3 last = Vec3.ZERO;
        Vec3 landing = Vec3.ZERO;
        final Set<UUID> hit = new HashSet<>();
        final Map<UUID, Integer> visited = new HashMap<>();
        UUID primary;
        boolean landed = false;

        Sess(Form form, ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
            this.form = form;
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.lvl = lvl;
        }
    }

    private static final List<Sess> ACTIVE = new ArrayList<>();

    public static void start(Form form, ServerLevel level, LivingEntity caster, AbstractSpell spell, int spellLevel) {
        ACTIVE.add(new Sess(form, level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Sess> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Sess s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            if (!(e instanceof LivingEntity c) || !c.isAlive()) {
                it.remove();
                continue;
            }
            boolean done;
            try {
                done = switch (s.form) {
                    case DANCE -> dance(s, c);
                    case RADIANCE -> radiance(s, c);
                    case SETTING_SUN -> settingSun(s, c);
                    case ETERNAL -> eternal(s, c);
                };
            } catch (Exception ex) {
                LOGGER.error("Sun Breathing failed", ex);
                done = true;
            }
            s.age++;
            if (done) it.remove();
        }
    }

    // =================================================================================================================
    //  FIRST FORM: DANCE
    // =================================================================================================================
    private static final int DANCE_DASH = 6;

    private static boolean dance(Sess s, LivingEntity c) {
        ServerLevel level = s.level;
        int t = s.age;
        if (t == 0) {
            s.f = SunFX.flat(c.getLookAngle());
            s.origin = c.position();
            double dist = 12;
            // stop just short of the first enemy in the way
            for (LivingEntity e : MartialFX.inFront(level, c, c.position().add(0, 1, 0), s.f, 12, 2.2)) {
                double along = e.position().subtract(s.origin).dot(s.f);
                dist = Math.min(dist, Math.max(1.0, along - 1.7));
            }
            s.dest = SunFX.dashPath(level, c, s.f, dist);
            s.last = s.origin;
            grant(level, c, DANCE_DASH + 12);
            sound(level, s.origin, ModSounds.SUN_SLASH.get(), 1.2f, 0.9f);
            FX.one(level, ModParticles.SUN_RING_FLAT.get(), new Vec3(s.origin.x, s.origin.y + 0.08, s.origin.z), new Vec3(3.5, 0, 0));
            SunFX.embers(level, s.origin.add(0, 0.5, 0), 12, 1.2, 0.12);
            announce(c, "First Form: Dance");
        }
        if (t >= 1 && t <= DANCE_DASH) {
            Vec3 p = s.origin.lerp(s.dest, (double) t / DANCE_DASH);
            Vec3 prev = s.last;
            SunFX.place(c, p, 0, 0, false);
            c.setDeltaMovement(Vec3.ZERO);
            s.last = p;
            SunFX.afterimage(level, prev);
            FX.one(level, ModParticles.SUN_BEAM.get(), prev.add(0, 1.0, 0), p.subtract(prev));
            FX.one(level, ModParticles.SUN_LINE.get(), prev.add(0, 1.5, 0), p.subtract(prev));
            SunFX.embers(level, p.add(0, 0.9, 0), 4, 0.8, 0.1);
            // anything the lunge runs through is cut
            for (LivingEntity e : SunFX.enemies(level, c, prev.add(p).scale(0.5).add(0, 1, 0), prev.distanceTo(p) * 0.5 + 1.4)) {
                if (s.hit.add(e.getUUID())) SunFX.hit(level, s.spell, c, e, 0.8f * SunFX.levelMult(s.lvl));
            }
        }
        if (t == DANCE_DASH + 1) {
            // the powerful downward slash
            Vec3 eye = c.getEyePosition();
            Vec3 f = s.f;
            Vec3 up = new Vec3(0, 1, 0);
            SunFX.ribbon(level, SunFX.SUN, eye, f, up, 5.2f, 5.6f, 0f, 1.5f, -0.45f, 1.3f, 12);
            SunFX.ribbon(level, SunFX.SUN, eye, f, up, 4.4f, 4.8f, 0.3f, 1.45f, -0.35f, 0.7f, 11);
            SunFX.ribbon(level, SunFX.CRIMSON, eye, f, up, 5.9f, 6.2f, -0.3f, 1.4f, -0.4f, 0.4f, 10);
            Vec3 ground = new Vec3(eye.x, c.getY(), eye.z).add(f.scale(4.2));
            FX.one(level, ModParticles.SUN_RING_FLAT.get(), ground.add(0, 0.1, 0), new Vec3(6.5, 0, 0));
            FX.one(level, ModParticles.SUN_GLOW.get(), ground.add(0, 0.6, 0), new Vec3(4.0, 0, 0));
            FX.one(level, ModParticles.SUN_FLARE.get(), ground.add(0, 0.8, 0), new Vec3(2.6, 0, 0));
            RandomSource rng = level.random;
            for (int i = 0; i < 46; i++) {      // golden sparks scattered across the ground in front
                double a = (rng.nextDouble() - 0.5) * 2.2, d = 1.5 + rng.nextDouble() * 6;
                Vec3 dir = f.scale(Math.cos(a)).add(SunFX.rightOfDir(f).scale(Math.sin(a)));
                Vec3 q = new Vec3(eye.x, c.getY() + 0.15, eye.z).add(dir.scale(d));
                FX.one(level, ModParticles.SUN_SPARK.get(), q, new Vec3((rng.nextDouble() - 0.5) * 0.1, 0.18 + rng.nextDouble() * 0.3, (rng.nextDouble() - 0.5) * 0.1));
            }
            SunFX.embers(level, ground.add(0, 0.5, 0), 14, 3.0, 0.12);
            sound(level, eye, ModSounds.SUN_SLASH.get(), 1.6f, 0.7f);
            sound(level, ground, SoundEvents.GENERIC_EXPLODE, 0.9f, 1.5f);
            float mult = 3.0f * SunFX.levelMult(s.lvl);
            for (LivingEntity e : SunFX.enemies(level, c, new Vec3(eye.x, c.getY() + 1, eye.z).add(f.scale(3.2)), 4.6)) {
                Vec3 to = e.position().subtract(c.position());
                if (to.dot(f) < -0.8) continue;
                SunFX.hit(level, s.spell, c, e, mult);
                MartialFX.shove(e, f.x, f.z, 0.7, 0.2);
            }
        }
        return t >= DANCE_DASH + 10;
    }

    // =================================================================================================================
    //  SEVENTH FORM: BENEFICENT RADIANCE
    // =================================================================================================================
    private static final int THRUST_STANCE = 8;

    private static boolean radiance(Sess s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        int t = s.age;
        if (t == 0) {
            announce(c, "Seventh Form: Beneficent Radiance");
            sound(level, c.position(), SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
        }
        if (t < THRUST_STANCE) {
            // sword-thrusting stance: rooted, a golden point forms at the tip
            c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 9, false, false, false));
            Vec3 f = c.getLookAngle();
            Vec3 tip = SunFX.tipOf(c, f);
            float k = (t + 1f) / THRUST_STANCE;
            FX.one(level, ModParticles.SUN_GLOW.get(), tip, new Vec3(0.25 + k * 0.7, 0, 0));
            FX.one(level, ModParticles.SUN_FLARE.get(), tip, new Vec3(0.2 + k * 0.9, 0, 0));
            for (int i = 0; i < 3; i++) {
                Vec3 from = tip.add(MartialFX.randomDir(rng).scale(1.6));
                FX.one(level, ModParticles.SUN_SPARK.get(), from, tip.subtract(from).scale(0.18));
            }
            if (t == THRUST_STANCE - 1) grant(level, c, 14);
            return false;
        }
        if (t == THRUST_STANCE) {
            Vec3 f = c.getLookAngle().normalize();
            s.f = f;
            s.origin = c.position();
            Vec3 tip = SunFX.tipOf(c, f);
            s.dest = SunFX.dashPath(level, c, f, 12);
            Vec3 a = s.origin, b = s.dest;
            SunFX.place(c, b, 0, 0, false);
            c.setDeltaMovement(f.scale(0.3));
            c.hurtMarked = true;
            // the narrow golden beam that follows the sword, and the afterimages
            Vec3 from = tip, to = tip.add(b.subtract(a));
            FX.one(level, ModParticles.SUN_BEAM.get(), from, to.subtract(from));
            for (int i = -2; i <= 2; i++) {
                Vec3 off = SunFX.rightOfDir(f).scale(i * 0.12).add(0, i * 0.05, 0);
                FX.one(level, ModParticles.SUN_LINE.get(), from.add(off), to.subtract(from));
            }
            for (int i = 0; i < 5; i++) SunFX.afterimage(level, a.lerp(b, i / 5.0));
            // the shockwave distortion: ring after ring along the path
            double len = a.distanceTo(b);
            for (int i = 1; i <= 4; i++) {
                Vec3 p = a.lerp(b, i / 4.0).add(0, 1.0, 0);
                FX.one(level, ModParticles.SUN_RING.get(), p, new Vec3(2.0 + i * 0.7, 0, 0));
            }
            sound(level, a, ModSounds.SUN_THRUST.get(), 1.8f, 1.0f);
            sound(level, b, ModSounds.SUN_THRUST.get(), 1.2f, 1.3f);
            // everything on the line is pierced
            float mult = 2.6f * SunFX.levelMult(s.lvl);
            for (LivingEntity e : SunFX.enemies(level, c, a.lerp(b, 0.5).add(0, 1, 0), len * 0.5 + 1.8)) {
                Vec3 ec = e.getBoundingBox().getCenter();
                Vec3 rel = ec.subtract(a.add(0, 1, 0));
                double along = rel.dot(f);
                if (along < -1.0 || along > len + 1.5) continue;
                if (rel.subtract(f.scale(along)).length() > 1.6 + e.getBbWidth() * 0.5) continue;
                SunFX.hit(level, s.spell, c, e, mult);
            }
            // burst of sunlight at the end
            SunFX.burst(level, b.add(0, 1.0, 0), 5.5f);
            for (LivingEntity e : SunFX.enemies(level, c, b.add(0, 1, 0), 4.0)) {
                if (s.hit.add(e.getUUID())) {
                    SunFX.hit(level, s.spell, c, e, 1.0f * SunFX.levelMult(s.lvl));
                }
                Vec3 away = e.position().subtract(b);
                MartialFX.shove(e, away.x, away.z, 0.9, 0.3);
            }
        }
        return t >= THRUST_STANCE + 6;
    }

    // =================================================================================================================
    //  NINTH FORM: SETTING SUN TRANSFORMATION
    // =================================================================================================================
    private static boolean settingSun(Sess s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        int t = s.age;
        if (t == 0) {
            s.f = SunFX.flat(c.getLookAngle());
            s.origin = c.position();
            Vec3 back = s.f.scale(-0.12);
            c.setDeltaMovement(back.x, 1.0, back.z);
            c.hurtMarked = true;
            grant(level, c, 90);
            announce(c, "Ninth Form: Setting Sun Transformation");
            sound(level, s.origin, ModSounds.SUN_SLASH.get(), 1.2f, 1.2f);
            FX.one(level, ModParticles.SUN_RING_FLAT.get(), new Vec3(s.origin.x, s.origin.y + 0.08, s.origin.z), new Vec3(4.0, 0, 0));
            SunFX.embers(level, s.origin.add(0, 0.4, 0), 14, 1.4, 0.18);
        }
        if (!s.landed) c.fallDistance = 0;
        Vec3 body = c.position().add(0, 1.0, 0);
        Vec3 up = new Vec3(0, 1, 0);

        // ---- the backward flip: a fiery loop traced around the body
        if (t >= 2 && t <= 13) {
            double phi = (t - 2) / 11.0 * Mth.TWO_PI;
            double prevPhi = (t - 3) / 11.0 * Mth.TWO_PI;
            Vec3 back = s.f.scale(-1);
            Vec3 p = body.add(up.scale(Math.cos(phi) * 1.4)).add(back.scale(Math.sin(phi) * 1.4));
            Vec3 q = body.add(up.scale(Math.cos(prevPhi) * 1.4)).add(back.scale(Math.sin(prevPhi) * 1.4));
            FX.one(level, ModParticles.SUN_BEAM.get(), q, p.subtract(q));
            SunFX.embers(level, p, 3, 0.5, 0.05);
            SunFX.afterimage(level, c.position());
        }
        if (t == 3) {
            // the great crescent that appears during the flip: it wraps over and behind the leap
            SunFX.ribbon(level, SunFX.SUN, body, up, s.f.scale(-1), 3.0f, 3.4f, 0f, 0.5f, 4.3f, 1.3f, 14);
            SunFX.ribbon(level, SunFX.SUN, body, up, s.f.scale(-1), 3.9f, 4.2f, 0f, 0.6f, 4.1f, 0.6f, 13);
        }
        // ---- the slash erupts toward the ground
        if (t == 12) {
            Vec3 eye = c.getEyePosition();
            Vec3 target = null;
            double best = 1e9;
            for (LivingEntity e : MartialFX.inFront(level, c, eye, s.f, 14, 3.0)) {
                double d = e.position().distanceToSqr(c.position());
                if (d < best) {
                    best = d;
                    target = e.position();
                }
            }
            if (target == null) target = new Vec3(c.getX(), c.getY(), c.getZ()).add(s.f.scale(8));
            double gy = MoonFX.groundAt(level, target.x, target.z, target.y);
            s.landing = new Vec3(target.x, gy, target.z);
            s.origin = eye;
            sound(level, eye, ModSounds.SUN_SLASH.get(), 1.8f, 0.6f);
        }
        if (t >= 12 && t < 17) {
            // the crescent travels from the swordsman to the ground, growing
            int k = t - 12;
            Vec3 path = s.landing.subtract(s.origin);
            Vec3 dir = path.normalize();
            Vec3 pos = s.origin.add(path.scale((k + 1) / 5.0));
            Vec3 right = SunFX.rightOfDir(s.f);
            float r = 3.2f + k * 1.3f;
            Vec3 centre = pos.subtract(dir.scale(r));
            SunFX.ribbon(level, SunFX.SUN, centre, right, dir, r, r, 0f, (float) (Math.PI / 2 - 1.2), (float) (Math.PI / 2 + 1.2), 1.9f, 9);
            SunFX.ribbon(level, SunFX.CRIMSON, centre.subtract(dir.scale(0.5)), right, dir, r * 1.08f, r * 1.08f, 0f, (float) (Math.PI / 2 - 1.1), (float) (Math.PI / 2 + 1.1), 0.7f, 8);
            for (int i = 0; i < 6; i++) {
                double a = (rng.nextDouble() - 0.5) * 2.0;
                Vec3 sp = centre.add(dir.scale(r * Math.cos(a))).add(right.scale(r * Math.sin(a)));
                FX.one(level, ModParticles.SUN_EMBER.get(), sp, dir.scale(0.2));
            }
        }
        if (t == 17) {
            Vec3 L = s.landing;
            SunFX.burst(level, L.add(0, 0.6, 0), 8.0f);
            FX.one(level, ModParticles.SUN_RING_FLAT.get(), L.add(0, 0.1, 0), new Vec3(11.0, 0, 0));
            FX.one(level, ModParticles.SUN_CIRCLE.get(), L.add(0, 0.06, 0), new Vec3(5.5, 0, 0));
            SunFX.embers(level, L.add(0, 0.8, 0), 40, 6.0, 0.22);
            sound(level, L, ModSounds.SUN_ERUPTION.get(), 1.5f, 1.4f);
            sound(level, L, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.9f);
            float mult = 3.6f * SunFX.levelMult(s.lvl);
            for (LivingEntity e : SunFX.enemies(level, c, L.add(0, 1, 0), 6.5)) {
                SunFX.hit(level, s.spell, c, e, mult);
                Vec3 away = e.position().subtract(L);
                MartialFX.shove(e, away.x, away.z, 0.7, 0.6);
            }
        }
        // ---- landing
        if (t >= 14 && !s.landed && (c.onGround() || t > 70)) {
            s.landed = true;
            Vec3 p = c.position();
            SunFX.burst(level, p.add(0, 0.5, 0), 5.0f);
            for (int i = 0; i < 20; i++) {
                double a = i * Mth.TWO_PI / 20;
                FX.one(level, ModParticles.SUN_EMBER.get(), p.add(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6), new Vec3(Math.cos(a) * 0.35, 0.12, Math.sin(a) * 0.35));
            }
            sound(level, p, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.6f);
            grant(level, c, 4);
            return false;
        }
        return s.landed && t > 16 || t > 80;
    }

    // =================================================================================================================
    //  THIRTEENTH FORM: THE SUN'S ETERNAL DANCE
    // =================================================================================================================
    private static final int AWAKEN = 20;
    private static final int[] SCHEDULE = {0, 6, 12, 20, 26, 32, 40, 46, 52, 60, 64, 72};
    private static final int ERUPT = AWAKEN + 80;

    private static boolean eternal(Sess s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        int t = s.age;
        if (t == 0) {
            grant(level, c, ERUPT + 20);
            s.origin = c.position();
            announce(c, "Thirteenth Form: The Sun's Eternal Dance");
            sound(level, s.origin, SoundEvents.BEACON_ACTIVATE, 2.0f, 1.6f);
            sound(level, s.origin, ModSounds.SUN_SLASH.get(), 1.4f, 0.5f);
            FX.one(level, ModParticles.SUN_CIRCLE.get(), new Vec3(s.origin.x, s.origin.y + 0.06, s.origin.z), new Vec3(7.0, 0, 0));
        }
        // ---- phase 1: awakening
        if (t < AWAKEN) {
            c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 9, false, false, false));
            Vec3 f = SunFX.flat(c.getLookAngle());
            Vec3 hand = c.getEyePosition().add(f.scale(1.0)).add(SunFX.rightOfDir(f).scale(0.45)).add(0, -0.5, 0);
            SunFX.embers(level, hand, 5, 0.6, 0.12);
            FX.one(level, ModParticles.SUN_GLOW.get(), hand.add(f.scale(0.7)), new Vec3(0.6 + t * 0.03, 0, 0));
            if (t % 4 == 0) FX.one(level, ModParticles.SUN_RING_FLAT.get(), new Vec3(c.getX(), c.getY() + 0.1, c.getZ()), new Vec3(5.0 + t * 0.2, 0, 0));
            if (t == 4) {
                for (LivingEntity e : SunFX.enemies(level, c, c.position(), 12)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3, false, true, true));
                    FX.one(level, ModParticles.SUN_RING.get(), e.getBoundingBox().getCenter(), new Vec3(1.8, 0, 0));
                }
            }
            return false;
        }
        // ---- phase 2: the twelve forms
        int rel = t - AWAKEN;
        for (int i = 0; i < SCHEDULE.length; i++) {
            if (SCHEDULE[i] == rel) twelve(s, c, i);
        }
        // ---- phase 3: the final solar eruption
        if (t >= ERUPT && t <= ERUPT + 10) {
            Vec3 p = s.origin.equals(Vec3.ZERO) ? c.position() : c.position();
            int k = t - ERUPT;
            if (k == 0) {
                sound(level, p, ModSounds.SUN_ERUPTION.get(), 3.0f, 0.9f);
                sound(level, p, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.5f);
                sound(level, p, SoundEvents.BEACON_DEACTIVATE, 2.0f, 1.2f);
                FX.one(level, ModParticles.SUN_GLOW.get(), p.add(0, 1.5, 0), new Vec3(26, 0, 0));
                FX.one(level, ModParticles.SUN_FLARE.get(), p.add(0, 2, 0), new Vec3(14, 0, 0));
                FX.one(level, ModParticles.SUN_CIRCLE.get(), new Vec3(p.x, p.y + 0.06, p.z), new Vec3(16.0, 0, 0));
                // the pillar of sunlight
                FX.one(level, ModParticles.SUN_BEAM.get(), p, new Vec3(0, 40, 0));
                FX.one(level, ModParticles.SUN_BEAM.get(), p.add(0.3, 0, 0), new Vec3(0, 40, 0));
                SunFX.sparks(level, p.add(0, 1, 0), 120, 1.0, 0.3);
                float flat = JJKConfig.SUN_ERUPTION_DAMAGE.get().floatValue();
                for (LivingEntity e : SunFX.enemies(level, c, p, 16)) {
                    e.invulnerableTime = 0;
                    DamageSources.applyDamage(e, flat, s.spell.getDamageSource(c));
                    SpellDamage.wound(e);
                    if (JJKConfig.SUN_SETS_FIRE.get()) e.setSecondsOnFire(6);
                    Vec3 away = e.position().subtract(p);
                    MartialFX.shove(e, away.x, away.z, 1.4, 0.7);
                    FX.one(level, ModParticles.SUN_GLOW.get(), e.getBoundingBox().getCenter(), new Vec3(2.4, 0, 0));
                }
            }
            // the ring of fire racing outward
            double r = 1.5 + k * 1.55;
            int n = (int) (14 + r * 3);
            for (int i = 0; i < n; i++) {
                double a = rng.nextDouble() * Mth.TWO_PI;
                FX.one(level, ModParticles.SUN_EMBER.get(), p.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), new Vec3(Math.cos(a) * 0.25, 0.1 + rng.nextDouble() * 0.12, Math.sin(a) * 0.25));
            }
            if (k % 2 == 0) {
                FX.one(level, ModParticles.SUN_RING_FLAT.get(), new Vec3(p.x, p.y + 0.1 + k * 0.01, p.z), new Vec3(6 + k * 1.6, 0, 0));
                FX.one(level, ModParticles.SUN_RING.get(), p.add(0, 1, 0), new Vec3(8 + k * 1.6, 0, 0));
            }
        }
        return t > ERUPT + 14;
    }

    private static LivingEntity nearest(ServerLevel level, LivingEntity c, Sess s, double radius) {
        List<LivingEntity> list = SunFX.enemies(level, c, c.position(), radius);
        if (list.isEmpty()) return null;
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
        if (s.primary != null) {
            for (LivingEntity e : list) if (e.getUUID().equals(s.primary)) return e;
        }
        s.primary = list.get(0).getUUID();
        return list.get(0);
    }

    /** One of the twelve attacks (index 0-11) of the Thirteenth Form. */
    private static void twelve(Sess s, LivingEntity c, int idx) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        LivingEntity target = nearest(level, c, s, 18);
        Vec3 pos = c.position();
        Vec3 f = SunFX.flat(c.getLookAngle());
        if (target != null) {
            f = SunFX.flat(target.position().subtract(pos));
            SunFX.place(c, pos, SunFX.yawOf(f), 0, true);
        }
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = SunFX.rightOfDir(f);
        Vec3 eye = c.getEyePosition();
        float lm = SunFX.levelMult(s.lvl);
        SunFX.afterimage(level, pos);
        sound(level, eye, ModSounds.SUN_SLASH.get(), 1.2f, 0.8f + rng.nextFloat() * 0.5f);
        // an overlapping trail of gold and crimson around the swordsman with every attack
        for (int k = 0; k < 2; k++) {
            double tilt = rng.nextDouble() * Math.PI;
            Vec3 u = f.scale(Math.cos(tilt)).add(right.scale(Math.sin(tilt)));
            Vec3 v = up.scale(Math.cos(tilt * 0.7)).add(right.scale(Math.sin(tilt * 0.7))).normalize();
            SunFX.ribbon(level, k == 0 ? SunFX.SUN : SunFX.CRIMSON, eye.add(0, -0.3, 0), u, v, 2.4f + k, 3.2f + k, 0f, -1.2f, 1.3f, 0.5f, 7);
        }

        if (idx < 3) {                                           // 1-3: Solar Slashes
            float tilt = (idx - 1) * 0.7f;
            Vec3 v = up.scale(Mth.cos(tilt)).add(right.scale(Mth.sin(tilt))).normalize();
            SunFX.ribbon(level, SunFX.SUN, eye, f, v, 4.6f, 5.0f, 0f, 1.25f, -1.25f, 1.0f, 8);
            SunFX.ribbon(level, SunFX.CRIMSON, eye, f, v, 5.4f, 5.7f, 0.2f, 1.1f, -1.1f, 0.4f, 7);
            for (LivingEntity e : MartialFX.inFront(level, c, eye, f, 6.0, 2.6)) {
                SunFX.hit(level, s.spell, c, e, 1.0f * lm);
            }
            SunFX.sparks(level, eye.add(f.scale(3)), 12, 0.3, 0.1);
        } else if (idx < 6) {                                    // 4-6: Blazing Dance (teleport between enemies)
            List<LivingEntity> list = SunFX.enemies(level, c, pos, 18);
            LivingEntity pick = null;
            int bestVisits = Integer.MAX_VALUE;
            double bestD = 1e9;
            for (LivingEntity e : list) {
                int v = s.visited.getOrDefault(e.getUUID(), 0);
                double d = e.distanceToSqr(c);
                if (v < bestVisits || (v == bestVisits && d < bestD)) {
                    bestVisits = v;
                    bestD = d;
                    pick = e;
                }
            }
            if (pick != null) {
                s.visited.merge(pick.getUUID(), 1, Integer::sum);
                Vec3 from = pos;
                Vec3 away = pos.subtract(pick.position());
                away = SunFX.flat(away);
                Vec3 spot = pick.position().add(away.scale(1.7));
                // use the spot if there is room, otherwise the side of the target
                if (!level.noCollision(c, c.getBoundingBox().move(spot.subtract(pos)))) {
                    spot = pick.position().add(right.scale(1.7));
                    if (!level.noCollision(c, c.getBoundingBox().move(spot.subtract(pos)))) spot = pos;
                }
                Vec3 face = SunFX.flat(pick.position().subtract(spot));
                SunFX.place(c, spot, SunFX.yawOf(face), 0, true);
                FX.one(level, ModParticles.SUN_BEAM.get(), from.add(0, 1, 0), spot.subtract(from));
                FX.one(level, ModParticles.SUN_BEAM_RED.get(), from.add(0, 1.4, 0), spot.subtract(from));
                SunFX.burst(level, from.add(0, 1, 0), 2.5f);
                SunFX.burst(level, spot.add(0, 1, 0), 2.5f);
                Vec3 pc = pick.getBoundingBox().getCenter();
                Vec3 r2 = SunFX.rightOfDir(face);
                SunFX.ribbon(level, SunFX.SUN, pc, face, r2.add(0, 0.6, 0).normalize(), 2.3f, 2.5f, 0f, 1.1f, -1.1f, 0.8f, 8);
                SunFX.ribbon(level, SunFX.CRIMSON, pc, face, r2.add(0, -0.6, 0).normalize(), 2.3f, 2.5f, 0f, 1.1f, -1.1f, 0.6f, 8);
                SunFX.hit(level, s.spell, c, pick, 1.5f * lm);
                for (LivingEntity e : SunFX.enemies(level, c, pc, 2.6)) {
                    if (e != pick) SunFX.hit(level, s.spell, c, e, 0.6f * lm);
                }
            }
        } else if (idx < 9) {                                    // 7-9: Solar Cyclone
            Vec3 centre = pos.add(0, 0.8 + (idx - 6) * 0.35, 0);
            Vec3 ex = new Vec3(1, 0, 0), ez = new Vec3(0, 0, 1);
            SunFX.ribbon(level, SunFX.SUN, centre, ex, ez, 5.0f, 5.6f, 0f, (float) (idx * 0.7), (float) (idx * 0.7 + Mth.TWO_PI * 0.95), 1.0f, 9);
            SunFX.ribbon(level, SunFX.CRIMSON, centre, ex, ez, 4.2f, 4.7f, 0f, (float) (idx * 0.7 + 1.0), (float) (idx * 0.7 + 1.0 + Mth.TWO_PI * 0.9), 0.6f, 8);
            FX.one(level, ModParticles.SUN_RING_FLAT.get(), new Vec3(pos.x, pos.y + 0.1, pos.z), new Vec3(6.5, 0, 0));
            SunFX.embers(level, pos.add(0, 0.5, 0), 10, 6.0, 0.15);
            for (LivingEntity e : SunFX.enemies(level, c, pos.add(0, 1, 0), 5.8)) {
                SunFX.hit(level, s.spell, c, e, 1.2f * lm);
                Vec3 in = pos.subtract(e.position());
                MartialFX.shove(e, in.x, in.z, 0.25, 0.1);
            }
        } else if (idx < 11) {                                   // 10-11: Crimson Flash (extremely fast piercing)
            double want = target != null ? Math.min(9.0, target.position().distanceTo(pos) + 3.0) : 9.0;
            Vec3 dir = target != null ? target.getBoundingBox().getCenter().subtract(pos.add(0, 1, 0)).normalize() : c.getLookAngle().normalize();
            Vec3 dest = SunFX.dashPath(level, c, SunFX.flat(dir), want);
            Vec3 from = pos;
            SunFX.place(c, dest, SunFX.yawOf(dir), 0, true);
            FX.one(level, ModParticles.SUN_BEAM_RED.get(), from.add(0, 1.2, 0), dest.subtract(from));
            FX.one(level, ModParticles.SUN_BEAM.get(), from.add(0, 1.2, 0), dest.subtract(from));
            Vec3 d = SunFX.flat(dest.subtract(from));
            SunFX.ribbon(level, SunFX.CRIMSON, from.add(0, 1.2, 0), d, up, (float) (from.distanceTo(dest) * 0.5), (float) (from.distanceTo(dest) * 0.5), 0f, 0f, 0f, 0f, 2);
            for (int i = 0; i < 4; i++) SunFX.afterimage(level, from.lerp(dest, i / 4.0));
            SunFX.burst(level, dest.add(0, 1, 0), 3.0f);
            double len = from.distanceTo(dest);
            for (LivingEntity e : SunFX.enemies(level, c, from.lerp(dest, 0.5).add(0, 1, 0), len * 0.5 + 1.8)) {
                Vec3 rel = e.getBoundingBox().getCenter().subtract(from.add(0, 1, 0));
                double along = rel.dot(d);
                if (along < -1 || along > len + 1.5) continue;
                if (rel.subtract(d.scale(along)).length() > 1.7 + e.getBbWidth() * 0.5) continue;
                SunFX.hit(level, s.spell, c, e, 1.8f * lm);
            }
        } else {                                                 // 12: Final Sunrise
            SunFX.ribbon(level, SunFX.SUN, eye, f, up, 9.0f, 9.6f, 0f, 1.5f, -0.6f, 2.4f, 14);
            SunFX.ribbon(level, SunFX.SUN, eye, f, up, 7.6f, 8.0f, 0.5f, 1.5f, -0.5f, 1.3f, 13);
            SunFX.ribbon(level, SunFX.CRIMSON, eye, f, up, 10.4f, 10.8f, -0.5f, 1.4f, -0.6f, 0.8f, 12);
            Vec3 g = new Vec3(pos.x, pos.y, pos.z).add(f.scale(6.5));
            SunFX.burst(level, g.add(0, 0.8, 0), 9.0f);
            FX.one(level, ModParticles.SUN_RING_FLAT.get(), g.add(0, 0.1, 0), new Vec3(11, 0, 0));
            for (int i = 2; i <= 14; i += 3) {
                Vec3 q = pos.add(f.scale(i));
                FX.one(level, ModParticles.SUN_BEAM.get(), q, new Vec3(0, 10 + (14 - i) * 0.5, 0));
                SunFX.embers(level, q.add(0, 1, 0), 8, 2.5, 0.2);
            }
            sound(level, g, SoundEvents.GENERIC_EXPLODE, 2.0f, 0.8f);
            for (LivingEntity e : SunFX.enemies(level, c, g.add(0, 1, 0), 8.0)) {
                SunFX.hit(level, s.spell, c, e, 4.0f * lm);
                Vec3 away = e.position().subtract(g);
                MartialFX.shove(e, away.x, away.z, 0.9, 0.7);
            }
        }
    }

    // =================================================================================================================
    private static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void announce(LivingEntity c, String text) {
        if (c instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
    }
}
