package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs every active Blade of the Ruined King dash.
 *
 * The dash path is planned once, server-side, when the spell is cast: the caster is walked forward in half-block
 * steps (stepping up one block if needed, stopping at walls), so the dash can never end up inside a wall or fall
 * short because of lag. Then the caster is moved along that path a few blocks per tick, and the blade cuts
 * everything its swept hitbox touches (each enemy once). A green arc is left at each step as a lingering trail.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class BladeManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final double STEP = 0.5;             // spacing of the planned path points
    private static final int POINTS_PER_TICK = 5;       // 2.5 blocks per tick: 7 blocks take 3 ticks
    private static final double CUT_WIDTH = 1.7;        // how far to each side of the path the blade reaches
    private static final double CUT_HEIGHT = 0.8;       // and above / below the body

    private static class Dash {
        final ServerLevel level;
        final UUID casterId;
        final BladeOfTheRuinedKingSpell spell;
        final int spellLevel;
        final Vec3 dir;
        final List<Vec3> path;
        @Nullable final UUID targetId;
        int index = 0;
        Vec3 last;
        final Set<UUID> hit = new HashSet<>();

        Dash(ServerLevel level, LivingEntity caster, BladeOfTheRuinedKingSpell spell, int spellLevel, Vec3 dir, List<Vec3> path, @Nullable LivingEntity target) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.dir = dir;
            this.path = path;
            this.targetId = target == null ? null : target.getUUID();
            this.last = caster.position();
        }
    }

    private static final List<Dash> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, BladeOfTheRuinedKingSpell spell, int spellLevel) {
        // lock on to the enemy being looked at (even a bit beyond the normal dash length), otherwise dash straight ahead
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        double distance = BladeOfTheRuinedKingSpell.RANGE;
        LivingEntity target = GuillotineManager.pickTarget(level, caster, BladeOfTheRuinedKingSpell.LOCK_RANGE);
        if (target != null) {
            Vec3 to = new Vec3(target.getX() - caster.getX(), 0, target.getZ() - caster.getZ());
            if (to.lengthSqr() > 1.0E-4) {
                dir = to;
                distance = Math.min(BladeOfTheRuinedKingSpell.LOCK_RANGE, to.length() + 2.0);   // ends just past it
            }
        }
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();

        List<Vec3> path = plan(level, caster, dir, distance);
        if (path.size() < 2) return;       // boxed in: nowhere to dash

        // the stance, the glow, the whoosh
        Vec3 hand = DariusFX.handPos(caster);
        ViegoFX.gather(level, hand, 1.6, 8);
        FX.one(level, ModParticles.VG_GLOW.get(), hand.add(0, 0.2, 0), new Vec3(0.8, 0, 0));
        MartialFX.sound(level, caster.position(), SoundEvents.PHANTOM_SWOOP, 1.2f, 0.7f);
        MartialFX.sound(level, caster.position(), SoundEvents.SOUL_ESCAPE, 1.0f, 1.1f);

        Dash d = new Dash(level, caster, spell, spellLevel, dir, path, target);
        ACTIVE.add(d);
        advance(d, caster);      // the dash starts this very tick
    }

    /** Walks the caster's hitbox forward in small steps and records every spot it can stand on. */
    private static List<Vec3> plan(ServerLevel level, LivingEntity caster, Vec3 dir, double distance) {
        List<Vec3> pts = new ArrayList<>();
        Vec3 pos = caster.position();
        pts.add(pos);
        AABB box = caster.getBoundingBox();
        double walked = 0;
        while (walked + 1.0E-6 < distance) {
            double s = Math.min(STEP, distance - walked);
            Vec3 move = new Vec3(dir.x * s, 0, dir.z * s);
            Vec3 next = null;
            for (double lift : new double[]{0.0, 0.6, 1.1}) {           // flat, then up a slab, then up a full block
                AABB moved = box.move(move.x, lift, move.z);
                if (level.noCollision(caster, moved)) {
                    next = new Vec3(pos.x + move.x, pos.y + lift, pos.z + move.z);
                    box = moved;
                    break;
                }
            }
            if (next == null) break;                                      // a wall
            pos = next;
            pts.add(pos);
            walked += s;
        }
        return pts;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Dash> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Dash d = it.next();
            Entity e = d.level.getEntity(d.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done;
            try {
                done = caster == null || advance(d, caster);
            } catch (Exception ex) {
                LOGGER.error("Blade of the Ruined King failed, cancelling it", ex);
                done = true;
            }
            if (done) it.remove();
        }
    }

    /** Moves the caster along the next stretch of the path and cuts what is in the way. @return true when the dash is over. */
    private static boolean advance(Dash d, LivingEntity caster) {
        int from = d.index;
        int to = Math.min(d.path.size() - 1, from + POINTS_PER_TICK);
        if (to <= from) {
            finish(d, caster);
            return true;
        }
        Vec3 a = d.path.get(from);
        Vec3 b = d.path.get(to);
        d.index = to;

        ViegoFX.teleport(caster, b);
        caster.hurtMarked = true;
        cut(d, caster, a, b);
        trail(d, caster, a, b);
        d.last = b;

        if (to >= d.path.size() - 1) {
            finish(d, caster);
            return true;
        }
        return false;
    }

    private static void finish(Dash d, LivingEntity caster) {
        ServerLevel level = d.level;
        Vec3 end = caster.position();
        // make sure the enemy that was locked on to is cut even if it stepped out of the path
        cut(d, caster, end, end);
        if (d.targetId != null && !d.hit.contains(d.targetId)) {
            Entity t = level.getEntity(d.targetId);
            if (t instanceof LivingEntity le && le.isAlive() && le.distanceTo(caster) < CUT_WIDTH + 2.0) {
                hitOne(d, caster, le);
            }
        }
        Vec3 v = caster.getDeltaMovement();
        caster.setDeltaMovement(0, Math.min(0, v.y), 0);
        caster.hurtMarked = true;
        // the big finishing slash, left hanging in the air
        Vec3 c = end.add(0, caster.getBbHeight() * 0.6, 0);
        Vec3 side = new Vec3(-d.dir.z, 0, d.dir.x);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 v2 = side.scale(Math.cos(0.35)).add(up.scale(Math.sin(0.35)));
        ViegoFX.ribbon(level, c, d.dir, v2, 2.8f, 3.0f, 0f, -1.3f, 1.3f, 1.3f, 16, MoonRibbonOptions.GREEN);
        ViegoFX.ribbon(level, c, d.dir, v2, 3.1f, 3.4f, 0.4f, -1.1f, 1.2f, 0.5f, 14, MoonRibbonOptions.DARK_GREEN);
        MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
    }

    /** The lingering blade trail: arcs along the stretch just travelled, plus spectral flames at the feet. */
    private static void trail(Dash d, LivingEntity caster, Vec3 a, Vec3 b) {
        ServerLevel level = d.level;
        Vec3 side = new Vec3(-d.dir.z, 0, d.dir.x);
        Vec3 v = side.scale(Math.cos(0.35)).add(0, Math.sin(0.35), 0);
        int arcs = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 1.25));
        for (int k = 1; k <= arcs; k++) {
            Vec3 p = a.lerp(b, k / (double) arcs);
            Vec3 c = p.add(0, caster.getBbHeight() * 0.55, 0);
            ViegoFX.ribbon(level, c, d.dir, v, 1.8f, 2.0f, 0f, -1.0f, 1.0f, 0.9f, 12, MoonRibbonOptions.GREEN);
            ViegoFX.flames(level, p.add(0, 0.1, 0), 2, 0.5);
        }
        ViegoFX.mist(level, b.add(0, 0.3, 0), 2, 0.5);
        FX.one(level, ModParticles.VG_LINE.get(), a.add(0, 1.0, 0), b.subtract(a));
    }

    /** Cuts everything the swept blade touches between {@code a} and {@code b}, once each. */
    private static void cut(Dash d, LivingEntity caster, Vec3 a, Vec3 b) {
        ServerLevel level = d.level;
        double h = caster.getBbHeight() * 0.5;
        Vec3 a2 = a.add(0, h, 0), b2 = b.add(0, h, 0);
        AABB search = new AABB(a2, b2).inflate(CUT_WIDTH + 2.0, CUT_HEIGHT + 2.0, CUT_WIDTH + 2.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, search, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster) || d.hit.contains(e.getUUID())) continue;
            AABB bb = e.getBoundingBox().inflate(CUT_WIDTH, CUT_HEIGHT, CUT_WIDTH);
            Optional<Vec3> through = bb.clip(a2, b2);
            if (!bb.contains(a2) && !bb.contains(b2) && through.isEmpty()) continue;
            hitOne(d, caster, e);
        }
    }

    private static void hitOne(Dash d, LivingEntity caster, LivingEntity e) {
        ServerLevel level = d.level;
        d.hit.add(e.getUUID());
        if (!ViegoFX.strike(caster, d.spell, e, BladeOfTheRuinedKingSpell.DAMAGE * DariusFX.levelScale(d.spellLevel),
                BladeOfTheRuinedKingSpell.MAX_HEALTH_PERCENT)) return;
        if (e.isAlive()) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BladeOfTheRuinedKingSpell.SLOW_TICKS, BladeOfTheRuinedKingSpell.SLOW_AMPLIFIER));
        }
        Vec3 p = e.getBoundingBox().getCenter();
        ViegoFX.burst(level, p, 1.3f);
        MartialFX.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
        MartialFX.sound(level, p, SoundEvents.SOUL_ESCAPE, 0.8f, 1.3f);
    }
}
