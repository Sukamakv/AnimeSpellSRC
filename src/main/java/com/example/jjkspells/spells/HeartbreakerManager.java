package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs Heartbreaker.
 *
 *   WINDUP  (the 1.2 second cast) - Viego's body dissolves into swirling green mist (he turns invisible), and a ghostly
 *           green circle, as wide as the blast, is drawn on the ground under the target.
 *   REFORM  - he reforms in mid-air above the target in a burst of mist, sword raised, held there for a moment.
 *   PLUNGE  - he drives down onto the target (following it if it moves).
 *   IMPACT  - sharp green flash, ring shockwave, spectral shards; the primary target is hit (or executed), enemies
 *             within the blast take damage and are thrown back, and the mist disperses.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class HeartbreakerManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final double HOVER_HEIGHT = 7.0;     // how high above the target he reforms (less if the ceiling is low)
    private static final int REFORM_TICKS = 4;
    private static final int MAX_PLUNGE_TICKS = 10;
    private static final double PLUNGE_SPEED = 1.9;

    /** caster -> the target chosen when the cast was checked */
    private static final Map<UUID, UUID> LOCKS = new HashMap<>();

    public static void lock(LivingEntity caster, LivingEntity target) {
        LOCKS.put(caster.getUUID(), target.getUUID());
    }

    // ---- wind-up ------------------------------------------------------------------------------

    private static class Windup {
        final ServerLevel level;
        final UUID casterId;
        final UUID targetId;
        Vec3 lastTargetPos;
        int age = 0;

        Windup(ServerLevel level, UUID casterId, UUID targetId, Vec3 pos) {
            this.level = level;
            this.casterId = casterId;
            this.targetId = targetId;
            this.lastTargetPos = pos;
        }
    }

    private static final List<Windup> WINDUPS = new ArrayList<>();

    public static void beginWindup(ServerLevel level, LivingEntity caster) {
        UUID targetId = LOCKS.get(caster.getUUID());
        Entity te = targetId == null ? null : level.getEntity(targetId);
        LivingEntity target = (te instanceof LivingEntity t && t.isAlive()) ? t : GuillotineManager.pickTarget(level, caster, HeartbreakerSpell.RANGE);
        if (target == null) return;
        lock(caster, target);
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        WINDUPS.add(new Windup(level, caster.getUUID(), target.getUUID(), target.position()));
        MartialFX.sound(level, caster.position(), SoundEvents.SOUL_ESCAPE, 1.6f, 0.6f);
        MartialFX.sound(level, caster.position(), SoundEvents.WITHER_AMBIENT, 0.8f, 0.5f);
    }

    public static void cancelWindup(LivingEntity caster) {
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        LOCKS.remove(caster.getUUID());
        caster.removeEffect(MobEffects.INVISIBILITY);
    }

    // ---- reform, plunge, impact -------------------------------------------------------------------

    private static class Strike {
        final ServerLevel level;
        final UUID casterId;
        final UUID targetId;
        final HeartbreakerSpell spell;
        final int spellLevel;
        Vec3 impactPos;
        int age = 0;

        Strike(ServerLevel level, UUID casterId, UUID targetId, HeartbreakerSpell spell, int spellLevel, Vec3 impactPos) {
            this.level = level;
            this.casterId = casterId;
            this.targetId = targetId;
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.impactPos = impactPos;
        }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    public static void startStrike(ServerLevel level, LivingEntity caster, HeartbreakerSpell spell, int spellLevel) {
        UUID targetId = LOCKS.remove(caster.getUUID());
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        Entity te = targetId == null ? null : level.getEntity(targetId);
        LivingEntity target = (te instanceof LivingEntity t && t.isAlive()) ? t : null;
        if (target == null) {
            target = GuillotineManager.pickTarget(level, caster, HeartbreakerSpell.RANGE);
        }
        if (target == null) {                                   // nothing left to strike: he just reforms
            caster.removeEffect(MobEffects.INVISIBILITY);
            return;
        }

        // reform above the target if there is room, otherwise beside it
        Vec3 tp = target.position();
        Vec3 dest = findSpot(level, caster, tp, caster.position());
        Vec3 from = caster.position();
        ViegoFX.mist(level, from.add(0, 1.0, 0), 14, 0.9);       // the last of him dissolving where he stood
        ViegoFX.teleport(caster, dest);
        caster.removeEffect(MobEffects.INVISIBILITY);

        // reforming: the mist rushes back into his shape
        Vec3 body = dest.add(0, 1.0, 0);
        ViegoFX.gather(level, body, 2.8, 16);
        ViegoFX.burst(level, body, 1.2f);
        FX.one(level, ModParticles.VG_SHOCK.get(), body, new Vec3(2.2, 0, 0));
        ViegoFX.tendrils(level, dest, 0.9f, 2.0f, 4);
        MartialFX.sound(level, dest, SoundEvents.SOUL_ESCAPE, 1.4f, 1.1f);
        MartialFX.sound(level, dest, SoundEvents.PHANTOM_SWOOP, 1.2f, 0.6f);

        STRIKES.add(new Strike(level, caster.getUUID(), target.getUUID(), spell, spellLevel, tp));
    }

    /** Above the target, as high as there is room for; failing that, next to it on the ground. */
    private static Vec3 findSpot(ServerLevel level, LivingEntity caster, Vec3 tp, Vec3 casterPos) {
        for (double h = HOVER_HEIGHT; h >= 2.0; h -= 1.0) {
            Vec3 p = new Vec3(tp.x, tp.y + h, tp.z);
            AABB box = caster.getBoundingBox().move(p.x - casterPos.x, p.y - casterPos.y, p.z - casterPos.z);
            if (level.noCollision(caster, box)) return p;
        }
        Vec3 away = new Vec3(casterPos.x - tp.x, 0, casterPos.z - tp.z);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        return new Vec3(tp.x + away.x * 1.8, tp.y, tp.z + away.z * 1.8);
    }

    // ---- ticking --------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Iterator<Windup> it = WINDUPS.iterator(); it.hasNext(); ) {
            Windup w = it.next();
            Entity e = w.level.getEntity(w.casterId);
            if (!(e instanceof LivingEntity caster) || !caster.isAlive() || w.age > 40) {
                if (e instanceof LivingEntity c) c.removeEffect(MobEffects.INVISIBILITY);
                it.remove();
                continue;
            }
            windupTick(w, caster);
            w.age++;
        }

        for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            boolean done;
            try {
                done = !(e instanceof LivingEntity caster) || !caster.isAlive() || strikeTick(s, caster);
            } catch (Exception ex) {
                LOGGER.error("Heartbreaker failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done) it.remove();
        }
    }

    private static void windupTick(Windup w, LivingEntity caster) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        Entity te = level.getEntity(w.targetId);
        if (te instanceof LivingEntity t && t.isAlive()) w.lastTargetPos = t.position();

        // his body dissolves into swirling green mist and he vanishes
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
        Vec3 pos = caster.position();
        ViegoFX.mist(level, pos.add(0, 0.9, 0), 3 + w.age / 6, 0.7);
        ViegoFX.flames(level, pos.add(0, 0.3, 0), 1, 0.4);
        if (w.age % 3 == 0) ViegoFX.tendrils(level, pos, 0.8f, 2.0f, 2);

        // the ghostly circle under the target, as wide as the blast
        double gy = MoonFX.groundAt(level, w.lastTargetPos.x, w.lastTargetPos.z, w.lastTargetPos.y);
        Vec3 ground = new Vec3(w.lastTargetPos.x, gy, w.lastTargetPos.z);
        if (w.age % 2 == 0) {
            FX.one(level, ModParticles.VG_CIRCLE.get(), ground.add(0, 0.06, 0), new Vec3(HeartbreakerSpell.SPLASH_RADIUS, 0, 0));
        }
        double a = rng.nextDouble() * Math.PI * 2;
        double r = HeartbreakerSpell.SPLASH_RADIUS;
        FX.one(level, ModParticles.VG_FLAME.get(), ground.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), new Vec3(0, 0.05, 0));
        if (w.age == 12) MartialFX.sound(level, ground, SoundEvents.PHANTOM_AMBIENT, 1.2f, 0.5f);
    }

    /** @return true when the strike is over. */
    private static boolean strikeTick(Strike s, LivingEntity caster) {
        ServerLevel level = s.level;
        Entity te = level.getEntity(s.targetId);
        if (te instanceof LivingEntity t && t.isAlive()) s.impactPos = t.position();

        caster.fallDistance = 0;
        if (s.age < REFORM_TICKS) {
            // hanging in the air, sword raised, flames pouring off the blade
            caster.setDeltaMovement(Vec3.ZERO);
            caster.hurtMarked = true;
            ViegoFX.flames(level, DariusFX.handPos(caster), 2, 0.3);
            ViegoFX.gather(level, DariusFX.handPos(caster), 1.2, 2);
            return false;
        }

        // plunge toward the marked spot
        double gy = MoonFX.groundAt(level, s.impactPos.x, s.impactPos.z, s.impactPos.y);
        Vec3 pos = caster.position();
        boolean landed = pos.y <= gy + 0.8 || caster.onGround() || s.age - REFORM_TICKS >= MAX_PLUNGE_TICKS;
        if (landed) {
            impact(s, caster, new Vec3(s.impactPos.x, gy, s.impactPos.z));
            return true;
        }
        Vec3 to = new Vec3(s.impactPos.x - pos.x, gy - pos.y, s.impactPos.z - pos.z);
        double len = to.length();
        Vec3 v = len < 1.0E-4 ? new Vec3(0, -PLUNGE_SPEED, 0) : to.scale(Math.min(PLUNGE_SPEED, len) / len);
        caster.setDeltaMovement(v);
        caster.hurtMarked = true;
        FX.one(level, ModParticles.VG_LINE.get(), pos.add(0, 2.0, 0), new Vec3(0, 2.2, 0));
        ViegoFX.flames(level, pos.add(0, 1.0, 0), 2, 0.4);
        return false;
    }

    // =============================================================================================
    //  IMPACT
    // =============================================================================================

    private static void impact(Strike s, LivingEntity caster, Vec3 ground) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        caster.fallDistance = 0;
        float scale = DariusFX.levelScale(s.spellLevel);

        // ---- visuals: sharp green flash, shockwave, shards, mist dispersing
        FX.one(level, ModParticles.VG_GLOW.get(), ground.add(0, 0.7, 0), new Vec3(4.5, 0, 0));
        FX.one(level, ModParticles.VG_HIT.get(), ground.add(0, 0.8, 0), new Vec3(2.6, 0, 0));
        FX.one(level, ModParticles.VG_SHOCK.get(), ground.add(0, 0.8, 0), new Vec3(5.0, 0, 0));
        ViegoFX.ring(level, ground, 7f);
        FX.one(level, ModParticles.MA_CRACKS.get(), ground.add(0, 0.05, 0), new Vec3(4.0, 0, 0));
        for (int i = 0; i < 24; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.VG_SHARD.get(), ground.add(0, 0.4, 0), new Vec3(d.x * 0.45, Math.abs(d.y) * 0.6 + 0.1, d.z * 0.45));
        }
        for (int i = 0; i < 16; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            FX.one(level, ModParticles.VG_FLAME.get(), ground.add(Math.cos(a) * 2.5, 0.1, Math.sin(a) * 2.5), new Vec3(0, 0.06 + rng.nextDouble() * 0.05, 0));
        }
        ViegoFX.mist(level, ground.add(0, 0.6, 0), 14, 2.5);
        MartialFX.sound(level, ground, SoundEvents.GENERIC_EXPLODE, 1.3f, 0.8f);
        MartialFX.sound(level, ground, SoundEvents.SOUL_ESCAPE, 1.5f, 0.6f);
        MartialFX.sound(level, ground, SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
        MartialFX.sound(level, ground, SoundEvents.WITHER_BREAK_BLOCK, 0.6f, 1.4f);
        DariusFX.shake(level, ground, 22, 7);

        // ---- the primary target: struck, or executed if it is nearly dead
        Entity te = level.getEntity(s.targetId);
        LivingEntity primary = (te instanceof LivingEntity t && t.isAlive()) ? t : null;
        if (primary != null) {
            float frac = primary.getHealth() / Math.max(1f, primary.getMaxHealth());
            primary.invulnerableTime = 0;
            if (frac < HeartbreakerSpell.EXECUTE_BELOW) {
                DamageSources.applyDamage(primary, 1.0E6f, s.spell.getDamageSource(caster));
                if (primary.isAlive()) primary.kill();                       // nothing survives the execution
                ViegoFX.burst(level, primary.getBoundingBox().getCenter(), 2.4f);
                MartialFX.sound(level, ground, SoundEvents.WITHER_DEATH, 0.6f, 1.6f);
            } else {
                ViegoFX.strike(caster, s.spell, primary, HeartbreakerSpell.PRIMARY_DAMAGE * scale, HeartbreakerSpell.PRIMARY_PERCENT);
                ViegoFX.burst(level, primary.getBoundingBox().getCenter(), 1.8f);
            }
            if (primary.isAlive()) {
                Vec3 away = primary.position().subtract(ground);
                MartialFX.shove(primary, away.x, away.z, 0.8, 0.4);
            }
        }

        // ---- everyone else caught in the blast
        double r = HeartbreakerSpell.SPLASH_RADIUS;
        AABB box = new AABB(ground.x - r, ground.y - 2, ground.z - r, ground.x + r, ground.y + 4, ground.z + r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e == primary || e.isAlliedTo(caster)) continue;
            double dx = e.getX() - ground.x, dz = e.getZ() - ground.z;
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() * 0.5 > r) continue;
            if (!ViegoFX.strike(caster, s.spell, e, HeartbreakerSpell.SPLASH_DAMAGE * scale, HeartbreakerSpell.SPLASH_PERCENT)) continue;
            ViegoFX.burst(level, e.getBoundingBox().getCenter(), 1.2f);
            if (e.isAlive()) MartialFX.shove(e, dx, dz, 1.0, 0.45);         // knocked backward
        }
    }
}
