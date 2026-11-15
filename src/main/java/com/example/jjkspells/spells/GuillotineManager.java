package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs Noxian Guillotine.
 *
 *   LOCK   (while the 1 second cast runs) - the target gets a crimson mark over its head, red energy streams into
 *          the axe, a heartbeat pounds and the world sounds go muffled.
 *   LEAP   - Darius arcs through the air onto the target, homing on it if it moves.
 *   CRASH  - the vertical chop: damage (more against a wounded target), a red shockwave across the ground, rubble,
 *            a heavy impact and a screen shake.
 *   ROAR   - if the target died, Darius roars and raises his axe as red energy erupts around him.
 *            An ordinary (non-boss) kill also resets the spell's cooldown.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class GuillotineManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int LEAP_TICKS = 8;
    private static final int ROAR_TICKS = 12;
    private static final double LAND_OFFSET = 1.4;      // Darius comes down this far in front of the target
    private static final double MAX_CRASH_DISTANCE = 5.5;

    // ---- lock-on --------------------------------------------------------------------------------

    /** caster -> the target chosen when the cast was checked */
    private static final Map<UUID, UUID> LOCKS = new HashMap<>();

    /** The enemy the caster is looking at within range, or null. A forgiving cone that has to be in line of sight. */
    @Nullable
    public static LivingEntity pickTarget(ServerLevel level, LivingEntity caster, double range) {
        return pickTarget(level, caster, range, t -> true);
    }

    /** Same, but only considers entities that pass {@code filter} (e.g. "has Weakness II"). */
    @Nullable
    public static LivingEntity pickTarget(ServerLevel level, LivingEntity caster, double range, java.util.function.Predicate<LivingEntity> filter) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(2.5);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster) || !filter.test(e)) continue;
            Vec3 v = e.getBoundingBox().getCenter().subtract(eye);
            double dist = v.length();
            if (dist < 0.5 || dist - e.getBbWidth() * 0.5 > range) continue;
            double along = v.dot(look);
            if (along <= 0) continue;
            double perp = v.subtract(look.scale(along)).length();
            double tolerance = e.getBbWidth() * 0.5 + 0.9 + along * 0.12;
            if (perp > tolerance) continue;
            if (!caster.hasLineOfSight(e)) continue;
            double score = perp / tolerance + dist * 0.01;
            if (score < bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    public static void lock(LivingEntity caster, LivingEntity target) {
        LOCKS.put(caster.getUUID(), target.getUUID());
    }

    // ---- wind-up (during the cast) -------------------------------------------------------------

    private static class Windup {
        final ServerLevel level;
        final UUID casterId;
        final UUID targetId;
        int age = 0;

        Windup(ServerLevel level, UUID casterId, UUID targetId) {
            this.level = level;
            this.casterId = casterId;
            this.targetId = targetId;
        }
    }

    private static final List<Windup> WINDUPS = new ArrayList<>();

    public static void beginWindup(ServerLevel level, LivingEntity caster) {
        UUID targetId = LOCKS.get(caster.getUUID());
        if (targetId == null) {
            LivingEntity t = pickTarget(level, caster, NoxianGuillotineSpell.RANGE);
            if (t == null) return;
            lock(caster, t);
            targetId = t.getUUID();
        }
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        WINDUPS.add(new Windup(level, caster.getUUID(), targetId));
        Entity te = level.getEntity(targetId);
        if (te instanceof LivingEntity t) {
            DariusMarkManager.mark(level, t, 30, DariusMarkManager.MARK);   // the crimson mark over its head
        }
        // muffle the world (the heartbeat is exempt on the client) and start the pounding
        DariusFX.muffle(level, caster.position(), 28, 26);
        MartialFX.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.8f);
    }

    public static void cancelWindup(LivingEntity caster) {
        for (Iterator<Windup> it = WINDUPS.iterator(); it.hasNext(); ) {
            Windup w = it.next();
            if (w.casterId.equals(caster.getUUID())) {
                DariusMarkManager.clear(w.targetId);
                it.remove();
            }
        }
        LOGGER.debug("Noxian Guillotine cast cancelled");
        LOCKS.remove(caster.getUUID());
    }

    // ---- leap and crash ----------------------------------------------------------------------------

    private static class Strike {
        final ServerLevel level;
        final UUID casterId;
        final UUID targetId;
        final NoxianGuillotineSpell spell;
        final int spellLevel;
        final float weaponDamage;
        final Vec3 start;
        Vec3 lastTargetPos;
        int age = 0;
        boolean killed = false;
        boolean crashed = false;
        Vec3 lastPos;

        Strike(ServerLevel level, LivingEntity caster, LivingEntity target, NoxianGuillotineSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.targetId = target.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.weaponDamage = MoonFX.weaponDamage(caster) * DariusFX.levelScale(spellLevel);
            this.start = caster.position();
            this.lastTargetPos = target.position();
            this.lastPos = caster.position();
        }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    public static void startLeap(ServerLevel level, LivingEntity caster, NoxianGuillotineSpell spell, int spellLevel) {
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        UUID targetId = LOCKS.remove(caster.getUUID());
        Entity te = targetId == null ? null : level.getEntity(targetId);
        LivingEntity target = (te instanceof LivingEntity le && le.isAlive()) ? le : null;
        if (target == null || target.distanceTo(caster) > NoxianGuillotineSpell.RANGE + 4) {
            // the target is gone (or far away): look for another
            target = pickTarget(level, caster, NoxianGuillotineSpell.RANGE);
        }
        if (target == null) return;                                        // nothing to execute: the cast fizzles
        STRIKES.add(new Strike(level, caster, target, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!WINDUPS.isEmpty()) {
            Iterator<Windup> it = WINDUPS.iterator();
            while (it.hasNext()) {
                Windup w = it.next();
                Entity e = w.level.getEntity(w.casterId);
                if (!(e instanceof LivingEntity caster) || !caster.isAlive() || w.age > 30) {
                    DariusMarkManager.clear(w.targetId);
                    it.remove();
                    continue;
                }
                windupTick(w, caster);
                w.age++;
            }
        }

        if (!STRIKES.isEmpty()) {
            Iterator<Strike> it = STRIKES.iterator();
            while (it.hasNext()) {
                Strike s = it.next();
                Entity e = s.level.getEntity(s.casterId);
                LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
                boolean done;
                try {
                    done = strikeTick(s, caster);
                } catch (Exception ex) {
                    LOGGER.error("Noxian Guillotine failed, cancelling it", ex);
                    done = true;
                }
                s.age++;
                if (done) {
                    DariusMarkManager.clear(s.targetId);
                    it.remove();
                }
            }
        }
    }

    private static void windupTick(Windup w, LivingEntity caster) {
        ServerLevel level = w.level;
        // red energy streams into the axe, thicker as the cast goes on
        DariusFX.gather(level, DariusFX.handPos(caster), 2.4, 2 + w.age / 5);
        if (w.age % 3 == 0) {
            FX.one(level, ModParticles.DR_GLOW.get(), DariusFX.handPos(caster).add(0, 0.2, 0), new Vec3(0.3 + w.age * 0.03, 0, 0));
        }
        if (w.age == 9 || w.age == 16) {
            MartialFX.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.9f + w.age * 0.015f);
        }
    }

    private static boolean strikeTick(Strike s, @Nullable LivingEntity caster) {
        if (caster == null) return true;
        ServerLevel level = s.level;

        if (s.age < LEAP_TICKS) {
            leapStep(s, caster);
            return false;
        }
        if (s.age == LEAP_TICKS) {
            crash(s, caster);
            return !s.killed;                // no kill: that is all; a kill goes on to the roar
        }
        // ROAR
        int k = s.age - LEAP_TICKS;
        roarTick(s, caster, k);
        return k >= ROAR_TICKS;
    }

    // =============================================================================================
    //  LEAP
    // =============================================================================================

    private static void leapStep(Strike s, LivingEntity caster) {
        ServerLevel level = s.level;
        Entity te = level.getEntity(s.targetId);
        Vec3 tp = (te instanceof LivingEntity t && t.isAlive()) ? t.position() : s.lastTargetPos;
        s.lastTargetPos = tp;

        Vec3 flat = new Vec3(tp.x - s.start.x, 0, tp.z - s.start.z);
        double dist = flat.length();
        Vec3 dir = dist < 1.0E-3 ? new Vec3(0, 0, 1) : flat.scale(1.0 / dist);
        Vec3 goal = new Vec3(tp.x - dir.x * LAND_OFFSET, tp.y, tp.z - dir.z * LAND_OFFSET);

        if (s.age == 0) {
            MartialFX.sound(level, s.start, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
            MartialFX.sound(level, s.start, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.6f);
            DariusFX.ring(level, s.start, 2.5f);            // the push off the ground
        }

        double p = (s.age + 1) / (double) LEAP_TICKS;
        double height = 2.0 + dist * 0.2;
        Vec3 desired = new Vec3(
                Mth.lerp(p, s.start.x, goal.x),
                Mth.lerp(p, s.start.y, goal.y) + height * Math.sin(Math.PI * p),
                Mth.lerp(p, s.start.z, goal.z));
        Vec3 vel = desired.subtract(caster.position());
        double len = vel.length();
        if (len > 2.5) vel = vel.scale(2.5 / len);
        caster.setDeltaMovement(vel);
        caster.hurtMarked = true;
        caster.fallDistance = 0;

        // a crimson trail behind the axe
        Vec3 now = caster.position().add(0, 1.0, 0);
        FX.one(level, ModParticles.DR_LINE.get(), s.lastPos.add(0, 1.0, 0), now.subtract(s.lastPos.add(0, 1.0, 0)));
        FX.one(level, ModParticles.DR_SPARK.get(), now, MartialFX.randomDir(level.random).scale(0.1));
        s.lastPos = caster.position();
    }

    // =============================================================================================
    //  CRASH
    // =============================================================================================

    private static void crash(Strike s, LivingEntity caster) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        s.crashed = true;
        caster.setDeltaMovement(0, Math.min(0, caster.getDeltaMovement().y), 0);
        caster.hurtMarked = true;
        caster.fallDistance = 0;

        Entity te = level.getEntity(s.targetId);
        LivingEntity target = (te instanceof LivingEntity t && t.isAlive()) ? t : null;
        Vec3 tp = target != null ? target.position() : s.lastTargetPos;
        double gy = MoonFX.groundAt(level, tp.x, tp.z, tp.y);
        Vec3 ground = new Vec3(tp.x, gy, tp.z);

        Vec3 flat = new Vec3(tp.x - caster.getX(), 0, tp.z - caster.getZ());
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 up = new Vec3(0, 1, 0);

        // ---- the vertical chop, drawn as arcs that come down over the target
        Vec3 c = ground.add(0, 0.1, 0);
        DariusFX.ribbon(level, c, flat, up, 3.4f, 3.4f, 0f, 2.4f, 0.1f, 1.3f, 11, MoonRibbonOptions.BRIGHT_RED);
        DariusFX.ribbon(level, c, flat, up, 3.6f, 3.6f, 0.5f, 2.3f, 0.15f, 0.55f, 10, MoonRibbonOptions.RED);
        DariusFX.ribbon(level, c, flat, up, 3.0f, 3.0f, -0.5f, 2.2f, 0.2f, 0.5f, 10, MoonRibbonOptions.DARK_RED);

        // ---- the damage
        boolean hit = false;
        if (target != null && target.distanceTo(caster) <= MAX_CRASH_DISTANCE) {
            float frac = target.getHealth() / Math.max(1f, target.getMaxHealth());
            float mult = NoxianGuillotineSpell.multiplierFor(frac);
            target.invulnerableTime = 0;
            hit = DamageSources.applyDamage(target, s.weaponDamage * mult, s.spell.getDamageSource(caster));
            if (hit) {
                SpellDamage.wound(target);
                DariusFX.redBurst(level, target.getBoundingBox().getCenter(), 2.0f);
                FX.one(level, ModParticles.DR_SHOCK.get(), target.getBoundingBox().getCenter(), new Vec3(3.2, 0, 0));
                if (!target.isAlive()) {
                    s.killed = true;
                    if (!DariusFX.isBoss(target)) {
                        DariusFX.resetCooldown(caster, s.spell);        // an ordinary enemy: the cooldown is gone
                    }
                }
            }
        }

        // ---- the red shockwave across the ground, cracks, rubble, flash
        DariusFX.ring(level, ground, 7f);
        FX.one(level, ModParticles.MA_CRACKS.get(), ground.add(0, 0.05, 0), new Vec3(4.2, 0, 0));
        FX.one(level, ModParticles.DR_GLOW.get(), ground.add(0, 0.6, 0), new Vec3(3.4, 0, 0));
        BlockState under = level.getBlockState(BlockPos.containing(tp.x, gy - 0.5, tp.z));
        if (under.isAir()) under = Blocks.STONE.defaultBlockState();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, under);
        for (int i = 0; i < 24; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = 0.4 + rng.nextDouble() * 2.5;
            FX.one(level, debris, ground.add(Math.cos(a) * r, 0.2, Math.sin(a) * r),
                    new Vec3(Math.cos(a) * 0.14, 0.3 + rng.nextDouble() * 0.35, Math.sin(a) * 0.14));
        }
        for (int i = 0; i < 12; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.DR_SPARK.get(), ground.add(0, 0.5, 0), new Vec3(d.x * 0.4, Math.abs(d.y) * 0.5 + 0.1, d.z * 0.4));
        }

        // ---- heavy impact sound and the screen shake
        MartialFX.sound(level, ground, SoundEvents.GENERIC_EXPLODE, 1.3f, 0.7f);
        MartialFX.sound(level, ground, SoundEvents.ANVIL_LAND, 1.3f, 0.5f);
        MartialFX.sound(level, ground, SoundEvents.IRON_GOLEM_DAMAGE, 1.2f, 0.6f);
        if (hit) MartialFX.sound(level, ground, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.5f);
        level.playSound(null, ground.x, ground.y, ground.z, under.getSoundType().getBreakSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.5f, 0.5f);
        DariusFX.shake(level, ground, 24, 9);
    }

    // =============================================================================================
    //  ROAR
    // =============================================================================================

    private static void roarTick(Strike s, LivingEntity caster, int k) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        Vec3 pos = caster.position();
        if (k == 2) {
            MartialFX.sound(level, pos, SoundEvents.RAVAGER_ROAR, 2.0f, 0.55f);
            MartialFX.sound(level, pos, SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.4f);
            DariusFX.ring(level, pos, 8.5f);
            FX.one(level, ModParticles.DR_SHOCK.get(), pos.add(0, 1.2, 0), new Vec3(6.0, 0, 0));
            FX.one(level, ModParticles.DR_GLOW.get(), pos.add(0, 1.2, 0), new Vec3(3.5, 0, 0));
            // red flames rising around him
            Vec3 up = new Vec3(0, 1, 0);
            for (int i = 0; i < 7; i++) {
                double a = i * Math.PI * 2 / 7 + rng.nextDouble() * 0.4;
                Vec3 lean = new Vec3(Math.cos(a), 0, Math.sin(a));
                float r = 5f + rng.nextFloat() * 2f;
                float span = 0.9f;
                Vec3 base = pos.add(lean.scale(1.6));
                DariusFX.ribbon(level, base.add(lean.scale(r)), lean, up, r, r, (rng.nextFloat() - 0.5f) * 1.6f,
                        (float) Math.PI, (float) Math.PI - span, 0.7f + rng.nextFloat() * 0.3f, 12,
                        (i & 1) == 0 ? MoonRibbonOptions.BRIGHT_RED : MoonRibbonOptions.RED);
            }
        }
        // energy erupts around him while the axe is held high
        DariusFX.gather(level, DariusFX.handPos(caster).add(0, 0.8, 0), 2.2, 3);
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            FX.one(level, ModParticles.DR_SPARK.get(), pos.add(Math.cos(a) * 1.3, 0.2, Math.sin(a) * 1.3),
                    new Vec3(Math.cos(a) * 0.05, 0.25 + rng.nextDouble() * 0.2, Math.sin(a) * 0.05));
        }
    }
}
