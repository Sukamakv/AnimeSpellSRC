package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.network.ModNetwork;
import com.example.jjkspells.network.TimeStopPacket;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every active Za Warudo.
 *
 *   CHANT   (0-79)    the caster stands still chanting; a vast golden clock circle spins up beneath them.
 *   FLASH   (80-87)   the whole screen flashes in negative.
 *   STOPPED (88-...)  time is stopped for everything but the caster. Damage the caster deals is held and lands all
 *                     at once when time resumes. Clients drain to grey (see TimeStopClient).
 *
 * How the freeze works: every living entity's tick is cancelled (so AI, physics, effects, fire, hunger, animations
 * all stop), projectiles, items and other entities have their motion and gravity held, primed TNT keeps its fuse, and
 * frozen players are pinned in place and cannot act. Clients get a packet so they freeze and tint their screen too.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class TimeStopManager {
    public static final int CHANT_TICKS = 80;
    public static final int FLASH_TICKS = 8;
    public static final int STOP_AT = CHANT_TICKS + FLASH_TICKS; // 88: the hands of the clock stop here
    private static final int FADE_TICKS = 40;
    private static final double RADIUS = 40.0;                    // radius of the clock face, in blocks

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static class Frozen {
        final Vec3 pos, motion;
        final boolean noGravity;
        final int fuse;

        Frozen(Entity e) {
            this.pos = e.position();
            this.motion = e.getDeltaMovement();
            this.noGravity = e.isNoGravity();
            this.fuse = e instanceof PrimedTnt t ? t.getFuse() : 0;
        }
    }

    private static class Pending {
        final DamageSource source;
        float total;

        Pending(DamageSource source) { this.source = source; }
    }

    private static class Stop {
        final ServerLevel level;
        final UUID caster;
        final int casterId;
        final int spellLevel;
        final int stopTicks;
        final Vec3 origin;
        final Map<Integer, Frozen> frozen = new HashMap<>();
        final Map<UUID, Pending> pending = new HashMap<>();
        long dayTime;
        int age = 0;
        boolean stopped = false;

        Stop(ServerLevel level, LivingEntity caster, int spellLevel) {
            this.level = level;
            this.caster = caster.getUUID();
            this.casterId = caster.getId();
            this.spellLevel = spellLevel;
            this.stopTicks = TimeStopSpell.stopTicks(spellLevel);
            this.origin = caster.position();
        }
    }

    private static final List<Stop> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, int spellLevel) {
        for (Stop s : ACTIVE) if (s.caster.equals(caster.getUUID())) return;
        ACTIVE.add(new Stop(level, caster, spellLevel));
    }

    // =============================================================================================
    //  RULES: who is frozen
    // =============================================================================================
    private static boolean exempt(Stop s, Entity e) {
        if (e.getUUID().equals(s.caster)) return true;
        if (isStopCaster(e)) return true;   // casters of different Time Stops do not freeze each other
        if (e instanceof LivingEntity l && l.hasEffect(ModEffects.SIMPLE_DOMAIN.get())) return true;
        if (e instanceof ServerPlayer sp && (sp.isSpectator() || !JJKConfig.TIME_STOP_FREEZES_PLAYERS.get())) return true;
        return e.isSpectator();
    }

    /** The active (stopped) time stop that freezes this entity, or null. */
    private static Stop stopFor(Entity e) {
        if (ACTIVE.isEmpty() || e.level().isClientSide) return null;
        for (Stop s : ACTIVE) {
            if (s.stopped && s.level == e.level() && !exempt(s, e)) return s;
        }
        return null;
    }

    /** True for the caster of any Time Stop that is still running (chant, flash or stopped): they cannot be harmed. */
    public static boolean isStopCaster(Entity e) {
        if (ACTIVE.isEmpty() || e == null || e.level().isClientSide) return false;
        for (Stop s : ACTIVE) if (s.caster.equals(e.getUUID())) return true;
        return false;
    }

    /** A stop caster is untouchable, except by another stop caster (they are not affected by each other's Time Stop). */
    private static boolean shielded(Entity victim, DamageSource src) {
        if (!isStopCaster(victim) || passesInvulnerability(src)) return false;
        Entity a = src.getEntity();
        return !(a != null && a != victim && isStopCaster(a));
    }

    /** For SpellDamage: is {@code target} protected from {@code attacker} by a running Time Stop? */
    public static boolean protects(Entity target, Entity attacker) {
        return isStopCaster(target) && !(attacker != null && attacker != target && isStopCaster(attacker));
    }

    private static boolean passesInvulnerability(DamageSource src) {
        return src.is(DamageTypes.GENERIC_KILL) || src.is(DamageTypes.FELL_OUT_OF_WORLD); // /kill and the void only
    }

    public static boolean isFrozen(Entity e) {
        return stopFor(e) != null;
    }

    // =============================================================================================
    //  TICKING
    // =============================================================================================
    /** Before the world ticks: hold everything still so nothing moves during this tick. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (ACTIVE.isEmpty()) return;
        if (event.phase == TickEvent.Phase.START) {
            for (Stop s : ACTIVE) {
                if (s.stopped) {
                    try { hold(s); } catch (Exception ex) { LOGGER.error("Time stop hold failed", ex); }
                }
            }
            return;
        }
        Iterator<Stop> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Stop s = it.next();
            boolean done;
            try {
                done = advance(s);
            } catch (Exception ex) {
                LOGGER.error("Za Warudo failed, ending it", ex);
                done = true;
            }
            if (done) {
                it.remove();
                release(s);
            } else {
                s.age++;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private static void hold(Stop s) {
        for (Entity e : s.level.getAllEntities()) {
            if (!e.isAlive() || exempt(s, e)) continue;
            Frozen f = s.frozen.get(e.getId());
            if (f == null) {
                // things the caster throws fly for a moment, then hang in the air until time resumes
                if (e instanceof Projectile p && p.getOwner() != null && p.getOwner().getUUID().equals(s.caster) && e.tickCount < 2) continue;
                f = new Frozen(e);
                s.frozen.put(e.getId(), f);
                if (!(e instanceof LivingEntity)) e.setNoGravity(true);
            }
            if (e instanceof LivingEntity) {
                if (e instanceof ServerPlayer sp && sp.position().distanceToSqr(f.pos) > 0.25) {
                    sp.connection.teleport(f.pos.x, f.pos.y, f.pos.z, sp.getYRot(), sp.getXRot());
                }
            } else {
                e.setDeltaMovement(Vec3.ZERO);
                if (e.position().distanceToSqr(f.pos) > 0.0025) e.setPos(f.pos);
                if (e instanceof PrimedTnt t) t.setFuse(f.fuse + 1); // its own tick takes it back down to f.fuse
            }
        }
        if (JJKConfig.TIME_STOP_FREEZES_DAYTIME.get() && s.level.getDayTime() != s.dayTime) s.level.setDayTime(s.dayTime);
    }

    /** @return true when the time stop is over. */
    private static boolean advance(Stop s) {
        ServerLevel level = s.level;
        Entity ce = level.getEntity(s.caster);
        LivingEntity c = (ce instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) ? le : null;
        int t = s.age;

        if (c == null || c.level() != level) {
            if (t < STOP_AT) { // the chant was interrupted
                send(s, TimeStopPacket.NONE, 0);
                return true;
            }
            return true; // caster gone: time resumes
        }
        Vec3 p = c.position();

        if (t < CHANT_TICKS) chant(s, c, p, t);
        else if (t < STOP_AT) flash(s, c, p, t);
        else {
            if (t == STOP_AT) beginStop(s, c, p);
            stoppedTick(s, c, p, t - STOP_AT);
            if (t - STOP_AT >= s.stopTicks) return true;
        }
        if (t % 20 == 0 && t > 0) send(s, t < CHANT_TICKS ? TimeStopPacket.CHANT : t < STOP_AT ? TimeStopPacket.FLASH : TimeStopPacket.STOPPED,
                s.stopTicks - Math.max(0, t - STOP_AT));
        return false;
    }

    // =============================================================================================
    //  CHANT
    // =============================================================================================
    private static void chant(Stop s, LivingEntity c, Vec3 p, int t) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;

        // stand still
        if (t % 4 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 9, false, false, false));
        c.setSprinting(false);
        Vec3 m = c.getDeltaMovement();
        c.setDeltaMovement(0, Math.min(0, m.y), 0);
        c.hurtMarked = true;

        if (t == 0) {
            send(s, TimeStopPacket.CHANT, 0);
            int life = STOP_AT + s.stopTicks + FADE_TICKS;
            double y = p.y + 0.06;
            double tau = Math.PI * 2;
            FX.one(level, ModParticles.TS_RAYS.get(), new Vec3(p.x, y, p.z), new Vec3(RADIUS * 1.35, life, 0.0045));
            FX.one(level, ModParticles.TS_FACE.get(), new Vec3(p.x, y + 0.02, p.z), new Vec3(RADIUS, life, 0.0));
            FX.one(level, ModParticles.TS_RUNES.get(), new Vec3(p.x, y + 0.04, p.z), new Vec3(RADIUS, life, -0.011));
            FX.one(level, ModParticles.TS_GEARS.get(), new Vec3(p.x, y + 0.06, p.z), new Vec3(RADIUS, life, 0.02));
            FX.one(level, ModParticles.TS_HAND_HR.get(), new Vec3(p.x, y + 0.08, p.z), new Vec3(RADIUS, life, tau * 1 / STOP_AT));
            FX.one(level, ModParticles.TS_HAND_MIN.get(), new Vec3(p.x, y + 0.10, p.z), new Vec3(RADIUS, life, tau * 8 / STOP_AT));
            sound(level, p, ModSounds.TIME_CHARGE.get(), 4f, 1f);
            FX.one(level, ModParticles.TS_RING_FLAT.get(), new Vec3(p.x, y, p.z), new Vec3(10, 0, 0));
        }
        // the clock is chanted into being, line by line
        if (t == 8) line(s, c, "Time");
        if (t == 26) line(s, c, "Time is mine");
        if (t == 44) line(s, c, "Time is mine to");
        if (t == 62) { line(s, c, "Time is mine to control"); sound(level, p, ModSounds.TIME_BELL.get(), 4f, 0.8f); }
        if (t == 61) sound(level, p, ModSounds.TIME_STOP.get(), 5f, 1f); // its riser lasts 19 ticks and lands on the flash

        // ripples of gold race out across the whole circle
        if (t % 10 == 5) FX.one(level, ModParticles.TS_RING_FLAT.get(), new Vec3(p.x, p.y + 0.12, p.z), new Vec3(RADIUS * (0.5 + 0.5 * t / CHANT_TICKS), 0, 0));

        // a column of golden light that tightens and brightens as the chant goes on
        double k = t / (double) CHANT_TICKS;
        int motes = 2 + (int) (6 * k);
        for (int i = 0; i < motes; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = (1.2 + rng.nextDouble() * 2.5) * (1.3 - 0.6 * k);
            FX.one(level, ModParticles.TS_MOTE.get(), p.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), new Vec3(0, 0.25 + rng.nextDouble() * 0.3, 0));
        }
        // gears and numerals drift up from the circle
        if (t % 2 == 0) {
            double a = rng.nextDouble() * Math.PI * 2, r = 6 + rng.nextDouble() * (RADIUS - 8);
            FX.one(level, ModParticles.TS_NUMERAL.get(), p.add(Math.cos(a) * r, 0.5, Math.sin(a) * r), new Vec3(0, 0.12 + rng.nextDouble() * 0.1, 0));
        }
        if (t % 3 == 0) {
            double a = rng.nextDouble() * Math.PI * 2, r = 4 + rng.nextDouble() * (RADIUS * 0.8);
            FX.one(level, ModParticles.TS_GEAR.get(), p.add(Math.cos(a) * r, 0.4, Math.sin(a) * r), new Vec3(0, 0.15 + rng.nextDouble() * 0.1, 0));
        }
        if (t % 6 == 0) FX.one(level, ModParticles.TS_STAR.get(), p.add(0, 1.0 + rng.nextDouble(), 0), new Vec3(0.8 + 2.5 * k, 0, 0));
    }

    private static void flash(Stop s, LivingEntity c, Vec3 p, int t) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        c.setDeltaMovement(0, Math.min(0, c.getDeltaMovement().y), 0);
        c.hurtMarked = true;
        if (t == CHANT_TICKS) {
            send(s, TimeStopPacket.FLASH, 0);
            FX.one(level, ModParticles.TS_GLOW.get(), p.add(0, 1.2, 0), new Vec3(18, 0, 0));
            FX.one(level, ModParticles.TS_STAR.get(), p.add(0, 1.2, 0), new Vec3(14, 0, 0));
            FX.one(level, ModParticles.TS_RING_FLAT.get(), p.add(0, 0.2, 0), new Vec3(RADIUS * 2.2, 0, 0));
            FX.one(level, ModParticles.TS_RING.get(), p.add(0, 1.2, 0), new Vec3(30, 0, 0));
            for (int i = 0; i < 40; i++) {
                double a = rng.nextDouble() * Math.PI * 2, sp = 0.5 + rng.nextDouble() * 1.4;
                FX.one(level, ModParticles.TS_GEAR.get(), p.add(0, 1.2, 0), new Vec3(Math.cos(a) * sp, 0.2 + rng.nextDouble() * 0.4, Math.sin(a) * sp));
            }
            for (int i = 0; i < 24; i++) {
                double a = rng.nextDouble() * Math.PI * 2, sp = 0.4 + rng.nextDouble() * 0.9;
                FX.one(level, ModParticles.TS_NUMERAL.get(), p.add(0, 1.2, 0), new Vec3(Math.cos(a) * sp, 0.1 + rng.nextDouble() * 0.3, Math.sin(a) * sp));
            }
        }
        if (t == CHANT_TICKS + 3) FX.one(level, ModParticles.TS_RING_FLAT.get(), p.add(0, 0.2, 0), new Vec3(RADIUS * 1.2, 0, 0));
    }

    // =============================================================================================
    //  STOPPED
    // =============================================================================================
    private static void beginStop(Stop s, LivingEntity c, Vec3 p) {
        s.stopped = true;
        s.dayTime = s.level.getDayTime();
        c.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        send(s, TimeStopPacket.STOPPED, s.stopTicks);
        sound(s.level, p, ModSounds.TIME_THROB.get(), 3f, 1f);
        FX.one(s.level, ModParticles.TS_RING.get(), p.add(0, 1.2, 0), new Vec3(22, 0, 0));
    }

    private static void stoppedTick(Stop s, LivingEntity c, Vec3 p, int t) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;

        if (t % 40 == 0) sound(level, p, ModSounds.TIME_THROB.get(), 2.5f, 0.9f);
        int left = s.stopTicks - t;
        if (left <= 60 && left % 20 == 0 && left > 0) sound(level, p, ModSounds.TIME_TICK.get(), 3f, 1.0f + 0.15f * (3 - left / 20));

        // stillness: slow golden dust hangs around the caster, numerals float up from the circle
        if (t % 3 == 0) {
            double a = rng.nextDouble() * Math.PI * 2, r = 2 + rng.nextDouble() * 5;
            FX.one(level, ModParticles.TS_MOTE.get(), p.add(Math.cos(a) * r, rng.nextDouble() * 2.5, Math.sin(a) * r), new Vec3(0, 0.05, 0));
        }
        if (t % 6 == 0) {
            double a = rng.nextDouble() * Math.PI * 2, r = 5 + rng.nextDouble() * (RADIUS - 8);
            FX.one(level, ModParticles.TS_NUMERAL.get(), p.add(Math.cos(a) * r, 0.5, Math.sin(a) * r), new Vec3(0, 0.05, 0));
        }
        // frozen things glint
        if (t % 8 == 0) {
            int n = 0;
            for (Integer id : s.frozen.keySet()) {
                Entity e = level.getEntity(id);
                if (e == null || e.distanceToSqr(p) > 48 * 48) continue;
                FX.one(level, ModParticles.TS_STAR.get(), e.getBoundingBox().getCenter(), new Vec3(Math.max(0.5, e.getBbWidth() * 0.9), 0, 0));
                if (++n >= 24) break;
            }
        }
    }

    // =============================================================================================
    //  RESUME
    // =============================================================================================
    private static void release(Stop s) {
        ServerLevel level = s.level;
        // ACTIVE no longer holds s, so none of the events below treat anything as frozen
        for (Map.Entry<Integer, Frozen> en : s.frozen.entrySet()) {
            Entity e = level.getEntity(en.getKey());
            if (e == null) continue;
            Frozen f = en.getValue();
            if (!(e instanceof LivingEntity)) {
                e.setNoGravity(f.noGravity);
                e.setDeltaMovement(f.motion);
                e.hasImpulse = true;
                if (e instanceof PrimedTnt t) t.setFuse(f.fuse);
            }
        }
        Entity ce = level.getEntity(s.caster);
        Vec3 p = ce != null ? ce.position() : s.origin;
        if (s.stopped) {
            send(s, TimeStopPacket.NONE, 0);
            sound(level, p, ModSounds.TIME_RESUME.get(), 5f, 1f);
            FX.one(level, ModParticles.TS_RING_FLAT.get(), p.add(0, 0.2, 0), new Vec3(RADIUS * 1.5, 0, 0));
            FX.one(level, ModParticles.TS_RING.get(), p.add(0, 1.2, 0), new Vec3(26, 0, 0));
            FX.one(level, ModParticles.TS_GLOW.get(), p.add(0, 1.2, 0), new Vec3(10, 0, 0));
        }
        // the blows struck while time was stopped all land now
        for (Map.Entry<UUID, Pending> en : s.pending.entrySet()) {
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof LivingEntity victim) || !victim.isAlive()) continue;
            Pending pd = en.getValue();
            Vec3 at = victim.getBoundingBox().getCenter();
            victim.invulnerableTime = 0;
            victim.hurt(pd.source, pd.total);
            FX.one(level, ModParticles.TS_GLOW.get(), at, new Vec3(Math.max(1.5, victim.getBbWidth() * 2.0), 0, 0));
            FX.one(level, ModParticles.TS_STAR.get(), at, new Vec3(Math.max(2.0, victim.getBbWidth() * 2.6), 0, 0));
            FX.one(level, ModParticles.TS_RING.get(), at, new Vec3(Math.max(2.0, victim.getBbWidth() * 3), 0, 0));
        }
        s.frozen.clear();
        s.pending.clear();
    }

    // =============================================================================================
    //  EVENTS while time is stopped
    // =============================================================================================
    /** Nothing ticks: AI, physics, status effects, fire, hunger, animations, even the passing of time for them. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    /** Harm to a frozen entity is held back. What the caster deals is stored and lands when time resumes. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (shielded(victim, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        Stop s = stopFor(victim);
        if (s == null) return;
        DamageSource src = event.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // void, /kill
        event.setCanceled(true);
        Entity attacker = src.getEntity();
        if (attacker != null && attacker.getUUID().equals(s.caster) && event.getAmount() > 0) {
            Pending pd = s.pending.computeIfAbsent(victim.getUUID(), k -> new Pending(src));
            pd.total += event.getAmount();
            FX.one(s.level, ModParticles.TS_STAR.get(), victim.getBoundingBox().getCenter(), new Vec3(Math.max(1.0, victim.getBbWidth() * 1.5), 0, 0));
            sound(s.level, victim.position(), ModSounds.TIME_TICK.get(), 1.2f, 1.6f);
        }
    }

    // The caster of a Time Stop cannot be touched at all, however the harm arrives.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (shielded(event.getEntity(), event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingDamageEvent event) {
        if (shielded(event.getEntity(), event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (isStopCaster(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!isStopCaster(event.getEntity())) return;
        MobEffect eff = event.getEffectInstance().getEffect();
        if (!eff.isBeneficial() && eff != MobEffects.MOVEMENT_SLOWDOWN) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isFrozen(event.getPlayer())) event.setCanceled(true);
    }

    // =============================================================================================
    //  HELPERS
    // =============================================================================================
    private static void send(Stop s, int state, int ticks) {
        ModNetwork.sendToDimension(s.level.dimension(), new TimeStopPacket(state, s.casterId, ticks, JJKConfig.TIME_STOP_FREEZES_PLAYERS.get()));
    }

    private static void line(Stop s, LivingEntity c, String text) {
        Component msg = Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD, ChatFormatting.ITALIC);
        for (ServerPlayer sp : s.level.players()) {
            if (sp.distanceToSqr(c) < 128 * 128) sp.displayClientMessage(msg, true);
        }
        sound(s.level, c.position(), ModSounds.TIME_TICK.get(), 3f, 0.9f);
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent sound, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, sound, SoundSource.PLAYERS, vol, pitch);
    }
}
