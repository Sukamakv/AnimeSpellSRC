package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.effect.StunEffect;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
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
 * Runs Spectral Maw.
 *
 *   CHARGE - green-black mist coils around Viego and his sword and gathers behind him while the cast runs.
 *   DASH   - a rapid low dash that ends at the first enemy it reaches (or after 10 blocks, or at a wall).
 *   WRAP   - the enemy is hit and stunned, and Black Mist tendrils burst out and wrap around it.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class SpectralMawManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final double SPEED = 2.5;            // blocks per tick
    private static final double HIT_RADIUS = 1.3;       // how close to the path an enemy must be to stop the charge
    private static final int WRAP_TICKS = 15;

    // ---- charge -------------------------------------------------------------------------------

    private static class Charge {
        final ServerLevel level;
        final UUID casterId;
        int age = 0;

        Charge(ServerLevel level, UUID casterId) {
            this.level = level;
            this.casterId = casterId;
        }
    }

    private static final List<Charge> CHARGES = new ArrayList<>();

    public static void beginCharge(ServerLevel level, LivingEntity caster) {
        CHARGES.removeIf(c -> c.casterId.equals(caster.getUUID()));
        CHARGES.add(new Charge(level, caster.getUUID()));
        MartialFX.sound(level, caster.position(), SoundEvents.SOUL_ESCAPE, 1.3f, 0.6f);
        MartialFX.sound(level, caster.position(), SoundEvents.PHANTOM_AMBIENT, 1.0f, 0.5f);
    }

    public static void cancelCharge(LivingEntity caster) {
        CHARGES.removeIf(c -> c.casterId.equals(caster.getUUID()));
    }

    // ---- dash ---------------------------------------------------------------------------------

    private static class Dash {
        final ServerLevel level;
        final UUID casterId;
        final SpectralMawSpell spell;
        final int spellLevel;
        final Vec3 dir;
        double remaining;
        Vec3 last;
        int age = 0;

        Dash(ServerLevel level, LivingEntity caster, SpectralMawSpell spell, int spellLevel, Vec3 dir, double distance) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.dir = dir;
            this.remaining = distance;
            this.last = caster.position();
        }
    }

    private static final List<Dash> DASHES = new ArrayList<>();

    // ---- wrap (tendrils around the stunned enemy) ------------------------------------------------

    private static class Wrap {
        final ServerLevel level;
        final UUID targetId;
        int age = 0;

        Wrap(ServerLevel level, UUID targetId) {
            this.level = level;
            this.targetId = targetId;
        }
    }

    private static final List<Wrap> WRAPS = new ArrayList<>();

    public static void startDash(ServerLevel level, LivingEntity caster, SpectralMawSpell spell, int spellLevel) {
        CHARGES.removeIf(c -> c.casterId.equals(caster.getUUID()));
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        // lock onto the enemy being looked at: charge straight at it
        LivingEntity lock = GuillotineManager.pickTarget(level, caster, SpectralMawSpell.RANGE);
        if (lock != null) dir = new Vec3(lock.getX() - caster.getX(), 0, lock.getZ() - caster.getZ());
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();

        double distance = SpectralMawSpell.RANGE;
        Vec3 from = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
        var hit = level.clip(new ClipContext(from, from.add(dir.scale(distance + 0.6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() != HitResult.Type.MISS) distance = Math.max(0, hit.getLocation().distanceTo(from) - 0.7);
        if (distance < 0.5) return;

        MartialFX.sound(level, caster.position(), SoundEvents.PHANTOM_SWOOP, 1.4f, 0.5f);
        MartialFX.sound(level, caster.position(), SoundEvents.GENERIC_EXPLODE, 0.5f, 1.8f);
        ViegoFX.ring(level, caster.position(), 2.5f);
        Dash d = new Dash(level, caster, spell, spellLevel, dir, distance);
        DASHES.add(d);
        BOLTS.add(new Bolt(level, caster, spell, spellLevel, caster.position().add(0, caster.getBbHeight() * 0.65, 0).add(dir.scale(0.8)), dir));
        setVelocity(d, caster);      // launches this very tick
    }

    // ---- the spectral bolt thrown forward with the dash ---------------------------------------------

    private static class Bolt {
        final ServerLevel level;
        final UUID casterId;
        final SpectralMawSpell spell;
        final int spellLevel;
        Vec3 pos;
        final Vec3 dir;
        double traveled = 0;
        final java.util.Set<UUID> hit = new java.util.HashSet<>();

        Bolt(ServerLevel level, LivingEntity caster, SpectralMawSpell spell, int spellLevel, Vec3 pos, Vec3 dir) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.pos = pos;
            this.dir = dir;
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final double BOLT_SPEED = 1.6;

    /** @return true when the bolt is spent. */
    private static boolean boltTick(Bolt b, LivingEntity caster) {
        ServerLevel level = b.level;
        double step = Math.min(BOLT_SPEED, SpectralMawSpell.PROJECTILE_RANGE - b.traveled);
        Vec3 next = b.pos.add(b.dir.scale(step));
        boolean wall = level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getType() != HitResult.Type.MISS;
        RandomSource rng = level.random;
        ViegoFX.mist(level, next, 2, 0.4);
        ViegoFX.flames(level, next, 2, 0.3);
        FX.one(level, ModParticles.VG_GLOW.get(), next, new Vec3(1.1, 0, 0));
        FX.one(level, ModParticles.VG_LINE.get(), b.pos, next.subtract(b.pos));
        FX.one(level, ModParticles.VG_TENDRIL.get(), b.pos.add((rng.nextDouble() - 0.5) * 0.4, (rng.nextDouble() - 0.5) * 0.4, (rng.nextDouble() - 0.5) * 0.4), next.subtract(b.pos));
        AABB box = new AABB(b.pos, next).inflate(1.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster) || b.hit.contains(e.getUUID())) continue;
            if (e.getBoundingBox().inflate(0.5).clip(b.pos, next).isEmpty()) continue;
            b.hit.add(e.getUUID());
            ViegoFX.strike(caster, b.spell, e, SpectralMawSpell.PROJECTILE_DAMAGE * DariusFX.levelScale(b.spellLevel), SpectralMawSpell.PROJECTILE_PERCENT);
            Vec3 p = e.getBoundingBox().getCenter();
            ViegoFX.burst(level, p, 1.2f);
            MartialFX.sound(level, p, SoundEvents.SOUL_ESCAPE, 0.8f, 1.2f);
        }
        b.traveled += step;
        b.pos = next;
        if (wall) ViegoFX.burst(level, next, 1.0f);
        return wall || b.traveled >= SpectralMawSpell.PROJECTILE_RANGE - 0.01;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Iterator<Charge> it = CHARGES.iterator(); it.hasNext(); ) {
            Charge c = it.next();
            Entity e = c.level.getEntity(c.casterId);
            if (!(e instanceof LivingEntity caster) || !caster.isAlive() || c.age > 40) {
                it.remove();
                continue;
            }
            chargeTick(c, caster);
            c.age++;
        }

        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            Entity e = b.level.getEntity(b.casterId);
            boolean done;
            try {
                done = !(e instanceof LivingEntity caster) || boltTick(b, caster);
            } catch (Exception ex) {
                LOGGER.error("Spectral Maw bolt failed", ex);
                done = true;
            }
            if (done) it.remove();
        }

        for (Iterator<Dash> it = DASHES.iterator(); it.hasNext(); ) {
            Dash d = it.next();
            Entity e = d.level.getEntity(d.casterId);
            boolean done;
            try {
                done = !(e instanceof LivingEntity caster) || !caster.isAlive() || dashTick(d, caster);
            } catch (Exception ex) {
                LOGGER.error("Spectral Maw failed, cancelling it", ex);
                done = true;
            }
            d.age++;
            if (done) it.remove();
        }

        for (Iterator<Wrap> it = WRAPS.iterator(); it.hasNext(); ) {
            Wrap w = it.next();
            Entity e = w.level.getEntity(w.targetId);
            if (!(e instanceof LivingEntity t) || !t.isAlive() || w.age > WRAP_TICKS) {
                it.remove();
                continue;
            }
            if (w.age % 5 == 0) {
                ViegoFX.tendrils(w.level, t.position(), t.getBbWidth() * 0.9f + 0.3f, t.getBbHeight() + 0.4f, 5);
            }
            ViegoFX.mist(w.level, t.position().add(0, t.getBbHeight() * 0.5, 0), 1, 0.5);
            w.age++;
        }
    }

    // =============================================================================================
    //  CHARGE
    // =============================================================================================

    private static void chargeTick(Charge c, LivingEntity caster) {
        ServerLevel level = c.level;
        RandomSource rng = level.random;
        Vec3 pos = caster.position();
        Vec3 look = caster.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

        // mist coils up around the body, thicker as the charge goes on
        if (c.age % 4 == 0) {
            ViegoFX.tendrils(level, pos, 0.9f, 1.9f, 2);
        }
        ViegoFX.mist(level, pos.add(0, 0.9, 0), 2 + c.age / 8, 0.8);
        // the sword takes on the green glow
        Vec3 hand = DariusFX.handPos(caster);
        ViegoFX.gather(level, hand, 1.4, 2 + c.age / 6);
        ViegoFX.flames(level, hand, 1, 0.2);
        // the mist gathers behind him: he is crouched and about to launch
        ViegoFX.gather(level, pos.add(0, 0.8, 0).subtract(flat.scale(1.5)), 1.5, 3);
        if (c.age == 10) {
            MartialFX.sound(level, pos, SoundEvents.PHANTOM_AMBIENT, 1.0f, 0.4f);
        }
    }

    // =============================================================================================
    //  DASH
    // =============================================================================================

    private static void setVelocity(Dash d, LivingEntity caster) {
        double s = Math.min(SPEED, d.remaining);
        if (s <= 0.01) return;
        // moved by position, not velocity: a player's own client would overwrite a velocity push almost at once
        Vec3 from = caster.position();
        Vec3 mid = from.add(0, caster.getBbHeight() * 0.5, 0);
        var wall = d.level.clip(new ClipContext(mid, mid.add(d.dir.scale(s + 0.4)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (wall.getType() != HitResult.Type.MISS) s = Math.max(0, wall.getLocation().distanceTo(mid) - 0.5);
        if (s <= 0.01) return;
        SunFX.place(caster, from.add(d.dir.scale(s)), caster.getYRot(), caster.getXRot(), false);
        caster.setDeltaMovement(0, Math.min(0, caster.getDeltaMovement().y), 0);
        caster.hurtMarked = true;
    }

    /** @return true when the dash is over. */
    private static boolean dashTick(Dash d, LivingEntity caster) {
        ServerLevel level = d.level;
        Vec3 now = caster.position();
        double moved = now.subtract(d.last).horizontalDistance();

        // a low trail of Black Mist and spectral flames
        ViegoFX.mist(level, now.add(0, 0.3, 0), 3, 0.6);
        ViegoFX.flames(level, now.add(0, 0.2, 0), 2, 0.4);
        FX.one(level, ModParticles.VG_LINE.get(), d.last.add(0, 0.6, 0), now.subtract(d.last).add(0, 0, 0));
        FX.one(level, ModParticles.VG_TENDRIL.get(), d.last.add(0, 1.0, 0), now.subtract(d.last));

        // the first enemy near the path stops the charge
        LivingEntity victim = firstEnemy(d, caster, d.last, now);
        d.remaining -= moved;
        boolean blocked = d.age > 0 && moved < 0.25;
        d.last = now;

        if (victim != null) {
            crash(d, caster, victim);
            return true;
        }
        if (d.remaining <= 0.2 || blocked || d.age > 12) {
            Vec3 v = caster.getDeltaMovement();
            caster.setDeltaMovement(0, v.y, 0);
            caster.hurtMarked = true;
            return true;
        }
        setVelocity(d, caster);
        return false;
    }

    private static LivingEntity firstEnemy(Dash d, LivingEntity caster, Vec3 a, Vec3 b) {
        AABB box = new AABB(a, b).inflate(HIT_RADIUS + 1, 1.5, HIT_RADIUS + 1);
        Vec3 seg = new Vec3(b.x - a.x, 0, b.z - a.z);
        double segLen2 = Math.max(seg.lengthSqr(), 1.0E-6);
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (LivingEntity e : d.level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            double px = e.getX() - a.x, pz = e.getZ() - a.z;
            double t = Math.max(0, Math.min(1, (px * seg.x + pz * seg.z) / segLen2));
            double dx = px - seg.x * t, dz = pz - seg.z * t;
            if (Math.sqrt(dx * dx + dz * dz) > HIT_RADIUS + e.getBbWidth() * 0.5) continue;
            if (t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }

    /** The impact: damage, stun, a burst of tendrils. */
    private static void crash(Dash d, LivingEntity caster, LivingEntity victim) {
        ServerLevel level = d.level;
        Vec3 v = caster.getDeltaMovement();
        caster.setDeltaMovement(0, v.y, 0);
        caster.hurtMarked = true;

        boolean ok = ViegoFX.strike(caster, d.spell, victim, SpectralMawSpell.DAMAGE * DariusFX.levelScale(d.spellLevel), SpectralMawSpell.MAX_HEALTH_PERCENT);
        Vec3 p = victim.getBoundingBox().getCenter();
        if (ok && victim.isAlive()) {
            StunEffect.apply(victim, SpectralMawSpell.STUN_TICKS);
            WRAPS.add(new Wrap(level, victim.getUUID()));
        }
        ViegoFX.burst(level, p, 2.0f);
        ViegoFX.ring(level, victim.position(), 4f);
        ViegoFX.tendrils(level, victim.position(), victim.getBbWidth() * 0.9f + 0.4f, victim.getBbHeight() + 0.6f, 8);   // tendrils burst outward
        FX.one(level, ModParticles.VG_SHOCK.get(), p, new Vec3(3.0, 0, 0));
        MartialFX.sound(level, p, SoundEvents.GENERIC_EXPLODE, 1.0f, 1.2f);
        MartialFX.sound(level, p, SoundEvents.SOUL_ESCAPE, 1.2f, 0.7f);
        MartialFX.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
    }
}
