package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.entity.TheWorldEntity;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs Jotaro's four moves. Star Platinum is the same kind of Stand entity as The World (a purple skin, its own aura),
 * appearing for the move and fading into purple particles afterwards.
 *
 *   ORA ORA ORA!          - 70 punches in 4 seconds (the same numbers as DIO's barrage).
 *   Star Finger           - the finger stretches out 20 blocks, piercing up to 4 enemies (DIO's eye beam numbers).
 *   Unbreakable Guard     - 4 seconds of blocking everything aimed at Jotaro, then the stored damage is smashed back.
 *   Instantaneous Onslaught - a 16 block dash that "punches" up to 10 enemies, who only feel it a moment later.
 *
 * No blocks are ever changed: all debris is particles.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class JotaroManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    // ---- numbers ------------------------------------------------------------------------------
    public static final int ORA_TICKS = 80, ORA_HITS = 70;
    public static final float ORA_FLAT = 6f, ORA_PERCENT = 0.25f, ORA_FINAL_FLAT = 60f, ORA_FINAL_PERCENT = 1f;
    public static final double ORA_REACH = 6.0;

    public static final double FINGER_RANGE = 20.0;
    public static final int FINGER_MAX_TARGETS = 4;
    public static final float FINGER_FLAT = 30f, FINGER_PERCENT = 5f;

    public static final int GUARD_TICKS = 80;
    public static final float GUARD_REFLECT = 0.40f;
    /** Half-width of the 10x10 shockwave zone. */
    public static final double GUARD_RADIUS = 5.0;

    public static final double DASH_LENGTH = 16.0;
    public static final int DASH_TICKS = 4, DASH_MAX_TARGETS = 10, DASH_HITS = 12;
    public static final float DASH_FLAT = 240f, DASH_PERCENT = 10f;
    public static final double DASH_HALF_WIDTH = 1.5, DASH_HALF_HEIGHT = 1.5;

    // ---- sessions -----------------------------------------------------------------------------

    private abstract static class Session {
        final ServerLevel level;
        final UUID casterId;
        final AbstractSpell spell;
        int age = 0;
        @Nullable TheWorldEntity stand;

        Session(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
        }

        abstract boolean tick(LivingEntity caster);

        void cleanup() {
            if (stand != null && !stand.isRemoved()) stand.vanish();
        }

        TheWorldEntity spawnStand(Vec3 pos, float yaw, int pose) {
            TheWorldEntity s = TheWorldEntity.createStar(level);
            s.setPos(pos.x, pos.y, pos.z);
            s.setYRot(yaw);
            s.yRotO = yaw;
            s.setPose2(pose);
            level.addFreshEntity(s);
            stand = s;
            return s;
        }

        void moveStand(Vec3 pos, float yaw) {
            if (stand == null || stand.isRemoved()) return;
            stand.setPos(pos.x, pos.y, pos.z);
            stand.setYRot(yaw);
        }

        void pose(int p) {
            if (stand != null && !stand.isRemoved()) stand.setPose2(p);
        }
    }

    private static final List<Session> ACTIVE = new ArrayList<>();
    /** Casters who are currently guarding. */
    private static final Map<UUID, Guard> GUARDS = new HashMap<>();

    public static void startOra(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        ACTIVE.add(new Ora(level, caster, spell));
    }

    public static void startFinger(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        ACTIVE.add(new Finger(level, caster, spell));
    }

    public static void startGuard(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        if (GUARDS.containsKey(caster.getUUID())) return;
        ACTIVE.add(new Guard(level, caster, spell));
    }

    public static void startOnslaught(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        ACTIVE.add(new Onslaught(level, caster, spell));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Session> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done;
            try {
                done = caster == null || s.tick(caster);
            } catch (Exception ex) {
                LOGGER.error("A Jotaro spell failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done || s.age > 400) {
                try {
                    s.cleanup();
                } catch (Exception ex) {
                    LOGGER.error("Jotaro cleanup failed", ex);
                }
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Session s : ACTIVE) {
            try {
                s.cleanup();
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }
        ACTIVE.clear();
        GUARDS.clear();
    }

    // ---- guard interception -----------------------------------------------------------------------

    /** Star Platinum takes the hit: cancelled here, remembered for the counter. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (GUARDS.isEmpty()) return;
        LivingEntity victim = event.getEntity();
        Guard g = GUARDS.get(victim.getUUID());
        if (g == null || !g.guarding) return;
        DamageSource src = event.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity attacker = src.getEntity();
        if (attacker == victim) return;
        if (attacker == null && src.getDirectEntity() == null && !src.is(DamageTypeTags.IS_EXPLOSION)) return;   // plain environmental damage
        if (event.getAmount() <= 0f) return;
        event.setCanceled(true);
        g.absorb(event.getAmount(), attacker != null ? attacker : src.getDirectEntity());
    }

    /**
     * Max-health percent damage (SpellDamage.percent) is not an attack event, so it asks here.
     * @return true if Star Platinum soaked it.
     */
    public static boolean absorbPercent(LivingEntity target, float amount, @Nullable LivingEntity attacker) {
        if (GUARDS.isEmpty()) return false;
        Guard g = GUARDS.get(target.getUUID());
        if (g == null || !g.guarding || attacker == target) return false;
        g.absorb(amount, attacker);
        return true;
    }

    public static boolean isGuarding(LivingEntity e) {
        Guard g = GUARDS.get(e.getUUID());
        return g != null && g.guarding;
    }

    // =============================================================================================
    //  Shared helpers
    // =============================================================================================

    private static Vec3 v(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    private static void actionbar(LivingEntity c, String text) {
        if (c instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        }
    }

    /** Purple flash and ring: Star Platinum appearing or vanishing. */
    private static void summonFx(ServerLevel l, Vec3 p, float size) {
        RandomSource rng = l.random;
        FX.one(l, ModParticles.SP_GLOW.get(), p, v(size * 1.6, 0, 0));
        FX.one(l, ModParticles.SP_RING.get(), p, v(size * 1.3, 0, 0));
        FX.one(l, ModParticles.SP_RING_FLAT.get(), v(p.x, p.y - 1.0, p.z), v(size * 1.4, 0, 0));
        FX.one(l, ModParticles.SP_STAR.get(), p, v(size * 0.8, 0, 0));
        for (int i = 0; i < 16; i++) {
            FX.one(l, ModParticles.SP_SPARK.get(), p.add(0, rng.nextDouble() * 1.6 - 0.8, 0), MartialFX.randomDir(rng).scale(0.1 + rng.nextDouble() * 0.25));
        }
    }

    private static void speedLines(ServerLevel l, Vec3 centre, Vec3 dir, int n, double spread, double len) {
        RandomSource rng = l.random;
        for (int i = 0; i < n; i++) {
            Vec3 off = MartialFX.randomDir(rng).scale(spread * rng.nextDouble());
            FX.one(l, ModParticles.SP_LINE.get(), centre.add(off), dir.scale(len * (0.7 + rng.nextDouble() * 0.6)));
        }
    }

    /** One punch landing: blue-white flash, a clenched purple fist afterimage, sparks and speed lines along the blow. */
    private static void punchFx(ServerLevel l, Vec3 p, Vec3 dir, float size) {
        RandomSource rng = l.random;
        FX.one(l, ModParticles.SP_GLOW.get(), p, v(size * 1.1, 0, 0));
        FX.one(l, ModParticles.SP_HIT.get(), p, v(size, 0, 0));
        FX.one(l, ModParticles.SP_FIST.get(), p.subtract(dir.scale(0.5)), v(size * 0.9, 0, 0));
        for (int i = 0; i < 4; i++) {
            FX.one(l, ModParticles.SP_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.25));
        }
        speedLines(l, p.subtract(dir.scale(1.6)), dir, 2, 0.5, 3.0);
    }

    /** A faint cage of sparks around an entity. {@code white} is the final flash. */
    private static void outline(ServerLevel l, LivingEntity t, boolean white) {
        AABB b = t.getBoundingBox().inflate(0.12);
        var type = white ? ModParticles.SP_STAR.get() : ModParticles.SP_SPARK.get();
        double[] xs = {b.minX, b.maxX}, zs = {b.minZ, b.maxZ};
        int steps = Math.max(3, (int) Math.ceil(b.getYsize() / 0.45));
        for (double x : xs) {
            for (double z : zs) {
                for (int i = 0; i <= steps; i++) {
                    double y = b.minY + b.getYsize() * i / steps;
                    if (white) FX.one(l, type, v(x, y, z), v(0.16, 0, 0));
                    else FX.one(l, type, v(x, y, z), Vec3.ZERO);
                }
            }
        }
        for (double y : new double[]{b.minY, b.maxY}) {
            for (int i = 1; i < 3; i++) {
                double f = i / 3.0;
                for (double x : xs) {
                    if (white) FX.one(l, type, v(x, y, Mth.lerp(f, b.minZ, b.maxZ)), v(0.16, 0, 0));
                    else FX.one(l, type, v(x, y, Mth.lerp(f, b.minZ, b.maxZ)), Vec3.ZERO);
                }
                for (double z : zs) {
                    if (white) FX.one(l, type, v(Mth.lerp(f, b.minX, b.maxX), y, z), v(0.16, 0, 0));
                    else FX.one(l, type, v(Mth.lerp(f, b.minX, b.maxX), y, z), Vec3.ZERO);
                }
            }
        }
    }

    private static Vec3 randomOn(RandomSource rng, LivingEntity t) {
        AABB b = t.getBoundingBox();
        return v(b.minX + rng.nextDouble() * b.getXsize(), b.minY + rng.nextDouble() * b.getYsize(), b.minZ + rng.nextDouble() * b.getZsize());
    }

    // =============================================================================================
    //  1. ORA ORA ORA!
    // =============================================================================================

    private static final class Ora extends Session {
        int next = 0;

        Ora(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
        }

        @Override
        boolean tick(LivingEntity caster) {
            // appears behind, then steps forward
            double back = 1.1 - 0.55 * Math.min(1.0, age / 4.0);
            Vec3 standPos = DioManager.behind(caster, back);
            if (age == 0) {
                spawnStand(standPos, caster.getYRot(), TheWorldEntity.POSE_BARRAGE);
                summonFx(level, standPos.add(0, 1.4, 0), 1.6f);
                MartialFX.sound(level, caster.position(), ModSounds.SP_SUMMON.get(), 1.2f, 1.0f);
                MartialFX.sound(level, caster.position(), ModSounds.DIO_BARRAGE.get(), 1.4f, 1.15f);
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ORA_TICKS + 4, 2, false, false, true));
                actionbar(caster, "ORA ORA ORA ORA!");
            }
            moveStand(standPos, caster.getYRot());

            if (next < ORA_HITS && (next * ORA_TICKS) / ORA_HITS <= age) {
                punch(caster, next == ORA_HITS - 1, next);
                next++;
            }
            if (age % 6 == 0 && age < ORA_TICKS) {
                speedLines(level, caster.getEyePosition().add(caster.getLookAngle().scale(2.5)), caster.getLookAngle(), 3, 1.4, 4.0);
            }
            if (age == ORA_TICKS && stand != null) {
                stand.vanish();
                summonFx(level, standPos.add(0, 1.4, 0), 1.2f);
            }
            return age >= ORA_TICKS + 2;
        }

        private void punch(LivingEntity caster, boolean last, int index) {
            Vec3 look = caster.getLookAngle();
            Vec3 eye = caster.getEyePosition();
            boolean right = (index & 1) == 0;
            Vec3 fist = (stand != null ? stand.position() : caster.position()).add(0, 1.8, 0)
                    .add(MartialFX.rightOf(caster).scale(right ? 0.55 : -0.55)).add(look.scale(0.9));

            LivingEntity best = null;
            double bestDot = -2;
            for (LivingEntity t : MartialFX.inFront(level, caster, eye, look, ORA_REACH, 1.6)) {
                if (!DioManager.validTarget(caster, t)) continue;
                double dot = t.getBoundingBox().getCenter().subtract(eye).normalize().dot(look);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = t;
                }
            }
            if (best == null) {
                Vec3 whiff = fist.add(look.scale(2.6));
                FX.one(level, ModParticles.SP_GLOW.get(), whiff, v(0.7, 0, 0));
                FX.one(level, ModParticles.SP_FIST.get(), whiff, v(0.8, 0, 0));
                MartialFX.streak(level, fist, whiff);
                return;
            }
            Vec3 before = best.getDeltaMovement();
            DioManager.strike(level, caster, spell, best, ORA_FLAT, ORA_PERCENT, 1f, false);
            if (last && best.isAlive()) {
                DioManager.strike(level, caster, spell, best, ORA_FINAL_FLAT, ORA_FINAL_PERCENT, 1f, false);
            }
            Vec3 c = best.getBoundingBox().getCenter();
            RandomSource rng = level.random;
            Vec3 p = c.subtract(look.scale(best.getBbWidth() * 0.45))
                    .add((rng.nextDouble() - 0.5) * 0.6, (rng.nextDouble() - 0.5) * 0.6, (rng.nextDouble() - 0.5) * 0.6);
            if (best.isAlive()) {
                if (last) {
                    MartialFX.shove(best, look.x, look.z, 2.4, 0.75);
                } else {
                    best.setDeltaMovement(before.x * 0.3 + look.x * 0.07, before.y, before.z * 0.3 + look.z * 0.07);
                    best.hurtMarked = true;
                }
            }
            if (last) {
                MartialFX.streak(level, fist, p);
                punchFx(level, p, look, 2.4f);
                FX.one(level, ModParticles.SP_RING.get(), p, v(3.6, 0, 0));
                FX.one(level, ModParticles.SP_RING_FLAT.get(), v(p.x, best.getY() + 0.1, p.z), v(4.2, 0, 0));
                FX.one(level, ModParticles.SP_STAR.get(), p, v(2.2, 0, 0));
                for (int i = 0; i < 18; i++) {
                    FX.one(level, ModParticles.SP_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.2 + rng.nextDouble() * 0.45));
                }
                DariusFX.shake(level, p, 24, 10);
                MartialFX.sound(level, p, ModSounds.SP_SMASH.get(), 0.9f, 1.3f);
            } else {
                punchFx(level, p, look, 0.6f);
                if ((index & 1) == 0) MartialFX.streak(level, fist, p);
                // trailing purple afterimage of the arm
                FX.one(level, ModParticles.SP_FIST.get(), fist.add(look.scale(0.6)), v(0.55, 0, 0));
                if (index % 3 == 0) {
                    MartialFX.sound(level, p, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 0.8f + rng.nextFloat() * 0.5f);
                }
            }
        }
    }

    // =============================================================================================
    //  2. Star Finger
    // =============================================================================================

    private static final class Finger extends Session {
        static final int GATHER = 6;
        static final double SPEED = 6.0;
        double length;
        double head = 0, prevHead = 0;
        Vec3 dir, origin;
        final List<Hit> targets = new ArrayList<>();
        final Set<UUID> done = new HashSet<>();

        private record Hit(LivingEntity e, double dist) {}

        Finger(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
        }

        private Vec3 standPos(LivingEntity c) {
            return c.position().add(MartialFX.rightOf(c).scale(1.25)).subtract(DioManager.flatLook(c).scale(0.2));
        }

        private Vec3 fingertip(LivingEntity c) {
            Vec3 sp = stand != null ? stand.position() : standPos(c);
            return sp.add(0, 1.9, 0).add(DioManager.flatLook(c).scale(2.1));
        }

        @Override
        boolean tick(LivingEntity caster) {
            RandomSource rng = level.random;
            Vec3 sp = standPos(caster);
            if (age == 0) {
                spawnStand(sp, caster.getYRot(), TheWorldEntity.POSE_FINGER);
                summonFx(level, sp.add(0, 1.4, 0), 1.5f);
                MartialFX.sound(level, caster.position(), ModSounds.SP_SUMMON.get(), 1.2f, 1.2f);
                actionbar(caster, "Star Finger!");
            }
            moveStand(sp, caster.getYRot());

            if (age < GATHER) {
                Vec3 tip = fingertip(caster);
                for (int i = 0; i < 3; i++) {
                    Vec3 d = MartialFX.randomDir(rng);
                    FX.one(level, ModParticles.SP_SPARK.get(), tip.add(d.scale(0.9)), d.scale(-0.9 * 0.13));
                }
                if (age % 2 == 0) FX.one(level, ModParticles.SP_GLOW.get(), tip, v(0.2 + age * 0.06, 0, 0));
                return false;
            }
            if (age == GATHER) fire(caster);
            if (dir == null) return true;

            prevHead = head;
            head = Math.min(length, head + SPEED);
            Vec3 a = origin.add(dir.scale(prevHead));
            Vec3 b = origin.add(dir.scale(head));
            Vec3 stretch = b.subtract(a);
            FX.one(level, ModParticles.SP_BEAM.get(), a, stretch);
            FX.one(level, ModParticles.SP_LINE.get(), a, stretch);
            if (head > prevHead) {
                // the stretching finger: a bright tip with sparks falling off it
                FX.one(level, ModParticles.SP_STAR.get(), b, v(0.35, 0, 0));
                for (int i = 0; i < 3; i++) {
                    FX.one(level, ModParticles.SP_SPARK.get(), a.add(stretch.scale(rng.nextDouble())), MartialFX.randomDir(rng).scale(0.06));
                }
            }
            for (Hit h : targets) {
                if (h.dist > prevHead - 0.001 && h.dist <= head + 0.001 && done.add(h.e.getUUID())) {
                    DioManager.strike(level, caster, spell, h.e, FINGER_FLAT, FINGER_PERCENT, 1f, false);
                    Vec3 c = h.e.getBoundingBox().getCenter();
                    FX.one(level, ModParticles.SP_HIT.get(), c, v(1.4, 0, 0));
                    FX.one(level, ModParticles.SP_STAR.get(), c, v(1.0, 0, 0));
                    FX.one(level, ModParticles.SP_RING.get(), c, v(1.6, 0, 0));
                    for (int i = 0; i < 8; i++) {
                        FX.one(level, ModParticles.SP_SPARK.get(), c, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.25));
                    }
                    MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, 1.3f);
                }
            }
            if (head >= length && prevHead < length) {
                // sharp flash where the finger ends
                Vec3 end = origin.add(dir.scale(length));
                FX.one(level, ModParticles.SP_GLOW.get(), end, v(1.4, 0, 0));
                FX.one(level, ModParticles.SP_HIT.get(), end, v(1.2, 0, 0));
                FX.one(level, ModParticles.SP_STAR.get(), end, v(1.1, 0, 0));
                for (int i = 0; i < 10; i++) {
                    FX.one(level, ModParticles.SP_SPARK.get(), end, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.2));
                }
                stand.vanish();
                summonFx(level, sp.add(0, 1.4, 0), 1.1f);
            }
            return head >= length && age > GATHER + 3;
        }

        private void fire(LivingEntity caster) {
            origin = fingertip(caster);
            Vec3 look = caster.getLookAngle();
            // aim where the caster looks (blocks stop it), not parallel to the look direction
            Vec3 eye = caster.getEyePosition();
            Vec3 far = eye.add(look.scale(FINGER_RANGE));
            BlockHitResult aim = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 aimPoint = aim.getType() == HitResult.Type.MISS ? far : aim.getLocation();
            dir = aimPoint.subtract(origin);
            if (dir.lengthSqr() < 0.25) dir = look;
            dir = dir.normalize();
            Vec3 end = origin.add(dir.scale(FINGER_RANGE));
            BlockHitResult bh = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            length = bh.getType() == HitResult.Type.MISS ? FINGER_RANGE : Math.max(0.5, origin.distanceTo(bh.getLocation()));
            Vec3 tip = origin.add(dir.scale(length));
            AABB box = new AABB(origin, tip).inflate(1.5);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> DioManager.validTarget(caster, t))) {
                Optional<Vec3> hit = t.getBoundingBox().inflate(0.35).clip(origin, tip);
                if (hit.isPresent()) targets.add(new Hit(t, origin.distanceTo(hit.get())));
            }
            targets.sort(Comparator.comparingDouble(Hit::dist));
            while (targets.size() > FINGER_MAX_TARGETS) targets.remove(targets.size() - 1);
            if (targets.size() == FINGER_MAX_TARGETS) {
                length = Math.min(length, targets.get(targets.size() - 1).dist + 0.7);
            }
            FX.one(level, ModParticles.SP_GLOW.get(), origin, v(1.2, 0, 0));
            FX.one(level, ModParticles.SP_RING.get(), origin, v(1.0, 0, 0));
            MartialFX.sound(level, origin, ModSounds.SP_DASH.get(), 1.1f, 1.6f);
        }
    }

    // =============================================================================================
    //  3. Unbreakable Guard
    // =============================================================================================

    private static final class Guard extends Session {
        static final int PAUSE = 10, WIND = 14;
        static final int SMASH = GUARD_TICKS + PAUSE + WIND;   // tick of the ground smash
        static final int END = SMASH + 26;

        boolean guarding = false;
        float stored = 0f;
        Vec3 facing;                 // horizontal direction Star Platinum is facing (toward incoming attacks)
        Vec3 smashPoint;
        int flashesThisTick = 0, lastFlashTick = -1;
        final List<Vec3[]> cracks = new ArrayList<>();
        boolean smashed = false;

        Guard(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
            this.facing = DioManager.flatLook(caster);
        }

        void absorb(float amount, @Nullable Entity attacker) {
            stored += amount;
            LivingEntity c = level.getEntity(casterId) instanceof LivingEntity le ? le : null;
            if (c == null) return;
            if (attacker != null) {
                Vec3 d = attacker.position().subtract(c.position());
                d = new Vec3(d.x, 0, d.z);
                if (d.lengthSqr() > 0.01) facing = d.normalize();
            }
            if (lastFlashTick == age) {
                if (++flashesThisTick > 3) return;
            } else {
                lastFlashTick = age;
                flashesThisTick = 1;
            }
            RandomSource rng = level.random;
            Vec3 p = c.position().add(facing.scale(1.35)).add(0, 1.2 + rng.nextDouble() * 0.9, 0)
                    .add(MartialFX.rightOf(c).scale((rng.nextDouble() - 0.5) * 0.9));
            FX.one(level, ModParticles.SP_HIT.get(), p, v(1.3, 0, 0));
            FX.one(level, ModParticles.SP_GLOW.get(), p, v(1.0, 0, 0));
            FX.one(level, ModParticles.SP_RING.get(), p, v(1.2, 0, 0));
            FX.one(level, ModParticles.SP_FIST.get(), p.subtract(facing.scale(0.4)), v(0.7, 0, 0));
            for (int i = 0; i < 8; i++) {
                FX.one(level, ModParticles.SP_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.3));
            }
            MartialFX.sound(level, p, ModSounds.SP_GUARD.get(), 1.0f, 0.9f + rng.nextFloat() * 0.3f);
            actionbar(c, "UNBREAKABLE GUARD  -  stored " + Math.round(stored));
        }

        private Vec3 standPos(LivingEntity c) {
            return c.position().add(facing.scale(1.3));
        }

        private float standYaw() {
            return SunFX.yawOf(facing);
        }

        @Override
        boolean tick(LivingEntity caster) {
            RandomSource rng = level.random;
            Vec3 sp = standPos(caster);
            float yaw = standYaw();

            if (age == 0) {
                guarding = true;
                GUARDS.put(casterId, this);
                spawnStand(sp, yaw, TheWorldEntity.POSE_GUARD);
                summonFx(level, sp.add(0, 1.4, 0), 1.7f);
                // the translucent barrier flashing in front of him
                Vec3 front = sp.add(facing.scale(0.9)).add(0, 1.5, 0);
                FX.one(level, ModParticles.SP_GLOW.get(), front, v(2.8, 0, 0));
                FX.one(level, ModParticles.SP_RING.get(), front, v(2.4, 0, 0));
                MartialFX.sound(level, caster.position(), ModSounds.SP_SUMMON.get(), 1.2f, 0.8f);
                MartialFX.sound(level, caster.position(), ModSounds.SP_GUARD.get(), 1.2f, 0.6f);
                actionbar(caster, "Star Platinum: Guard!");
            }

            if (age < GUARD_TICKS) {
                DioHold.hold(caster, 3);   // Jotaro stands perfectly still, arms crossed
                caster.setDeltaMovement(0, Math.min(0, caster.getDeltaMovement().y), 0);
                moveStand(sp, yaw);
                Vec3 front = sp.add(facing.scale(0.9)).add(0, 1.5, 0);
                // tight dark-purple aura with blue sparks on the forearms
                if (age % 2 == 0) {
                    for (int i = 0; i < 2; i++) {
                        Vec3 fore = sp.add(0, 1.6 + rng.nextDouble() * 0.5, 0).add(facing.scale(0.5)).add(MartialFX.rightOf(caster).scale((rng.nextDouble() - 0.5) * 1.1));
                        FX.one(level, ModParticles.SP_SPARK.get(), fore, MartialFX.randomDir(rng).scale(0.05));
                    }
                    FX.one(level, ModParticles.SP_GLOW.get(), sp.add(0, 0.9 + rng.nextDouble() * 1.4, 0), v(0.9, 0, 0));
                }
                // faint pulses of distorted air marking the guarded zone
                if (age % 10 == 5) FX.one(level, ModParticles.SP_RING.get(), front, v(1.9, 0, 0));
                return false;
            }

            if (age == GUARD_TICKS) {
                guarding = false;
                GUARDS.remove(casterId);
                DioHold.release(caster);
                pose(TheWorldEntity.POSE_IDLE);   // the arms slowly lower
            }
            moveStand(sp, yaw);
            Vec3 fistL = sp.add(0, 1.2, 0).add(MartialFX.rightOf(caster).scale(-0.7)).add(facing.scale(0.5));
            Vec3 fistR = sp.add(0, 1.2, 0).add(MartialFX.rightOf(caster).scale(0.7)).add(facing.scale(0.5));
            float charge = Mth.clamp(stored / 120f, 0.25f, 1.6f);

            if (age < GUARD_TICKS + PAUSE) {
                // the aura contracts tightly into the fists
                for (int i = 0; i < 4; i++) {
                    Vec3 d = MartialFX.randomDir(rng);
                    Vec3 target = i % 2 == 0 ? fistL : fistR;
                    Vec3 p = target.add(d.scale(1.6));
                    FX.one(level, i % 2 == 0 ? ModParticles.SP_SPARK.get() : ModParticles.SP_GLOW.get(), p, target.subtract(p).scale(0.12));
                }
                return false;
            }

            if (age == GUARD_TICKS + PAUSE) {
                pose(TheWorldEntity.POSE_WINDUP);
                smashPoint = sp.add(facing.scale(1.5));
                double gy = DioManager.groundAt(level, smashPoint, smashPoint.y);
                smashPoint = v(smashPoint.x, gy, smashPoint.z);
                // jagged cracks around the smash point
                for (int i = 0; i < 9; i++) {
                    double ang = i * (Math.PI * 2 / 9) + rng.nextDouble() * 0.5;
                    Vec3[] pts = new Vec3[4];
                    pts[0] = smashPoint.add(0, 0.12, 0);
                    double r = 0;
                    double a = ang;
                    for (int k = 1; k < 4; k++) {
                        r += 0.9 + rng.nextDouble() * 1.1;
                        a += (rng.nextDouble() - 0.5) * 0.7;
                        pts[k] = v(smashPoint.x + Math.cos(a) * r, smashPoint.y + 0.12, smashPoint.z + Math.sin(a) * r);
                    }
                    cracks.add(pts);
                }
                MartialFX.sound(level, sp, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.6f);
            }

            if (age < SMASH) {
                // wind-up: the ground cracks, light leaks through, energy swirls around the raised fist
                int t = age - (GUARD_TICKS + PAUSE);
                float k = t / (float) WIND;
                if (age % 2 == 0) emitCracks(Math.min(1f, k * 1.4f), 0.5f);
                Vec3 high = sp.add(0, 3.3, 0).add(facing.scale(0.2));
                for (int i = 0; i < 4; i++) {
                    double ang = age * 0.9 + i * Math.PI / 2;
                    double rad = 0.5 + 0.9 * (1 - k);
                    Vec3 p = high.add(MartialFX.rightOf(caster).scale(Math.cos(ang) * rad)).add(0, Math.sin(ang) * rad, 0).add(facing.scale(Math.sin(ang * 0.5) * rad));
                    FX.one(level, i % 2 == 0 ? ModParticles.SP_SPARK.get() : ModParticles.SP_GLOW.get(), p, high.subtract(p).scale(0.1));
                }
                if (age % 2 == 0) FX.one(level, ModParticles.SP_GLOW.get(), high, v(0.5 + k * 1.2 * charge, 0, 0));
                for (int i = 0; i < 2; i++) {
                    FX.one(level, ModParticles.SP_SPARK.get(), smashPoint.add((rng.nextDouble() - 0.5) * 2.5, 0.1, (rng.nextDouble() - 0.5) * 2.5), v(0, 0.08 + rng.nextDouble() * 0.08, 0));
                }
                return false;
            }

            if (age == SMASH) smash(caster, sp);

            // aftermath: glowing cracks fade over about a second, Star Platinum rises and vanishes
            int after = age - SMASH;
            if (after < 22 && after % 2 == 0) emitCracks(1f - after / 22f, 1f);
            if (after == 6) pose(TheWorldEntity.POSE_RECOVER);
            if (after == 18 && stand != null) {
                stand.vanish();
                summonFx(level, sp.add(0, 1.4, 0), 1.2f);
            }
            return age >= END;
        }

        private void emitCracks(float strength, float lengthFrac) {
            for (Vec3[] pts : cracks) {
                int n = Math.max(1, Math.round((pts.length - 1) * lengthFrac));
                for (int i = 0; i < n; i++) {
                    Vec3 a = pts[i], b = pts[i + 1];
                    if (strength > 0.55f || level.random.nextFloat() < strength + 0.2f) {
                        FX.one(level, ModParticles.SP_BEAM.get(), a, b.subtract(a));
                    }
                    if (level.random.nextFloat() < 0.3f) {
                        FX.one(level, ModParticles.SP_SPARK.get(), a, v(0, 0.06 + level.random.nextDouble() * 0.06, 0));
                    }
                }
            }
        }

        private void smash(LivingEntity caster, Vec3 sp) {
            RandomSource rng = level.random;
            pose(TheWorldEntity.POSE_SLAM);
            Vec3 p = smashPoint;
            float reflect = stored * GUARD_REFLECT;

            AABB box = new AABB(p, p).inflate(GUARD_RADIUS + 3.0);
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> DioManager.validTarget(caster, t))) {
                double dx = t.getX() - p.x, dz = t.getZ() - p.z;
                double reach = GUARD_RADIUS + t.getBbWidth() * 0.5;
                if (dx * dx + dz * dz > reach * reach || Math.abs(t.getY() - p.y) > 4.0) continue;
                hit.add(t);
            }
            for (LivingEntity t : hit) {
                if (reflect > 0f) DioManager.strike(level, caster, spell, t, reflect, 0f, 1f, false);
            }
            for (LivingEntity t : hit) {
                if (!t.isAlive()) continue;
                Vec3 out = t.position().subtract(p);
                DioHold.release(t);
                MartialFX.shove(t, out.x, out.z, 2.6, 0.85);
            }

            // visuals: white-purple flash, rings of energy, dust and stone, arcs along the shockwave
            MartialFX.sound(level, p, ModSounds.SP_SMASH.get(), 2.2f, 0.9f);
            MartialFX.sound(level, p, SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
            MartialFX.sound(level, p, SoundEvents.GENERIC_EXPLODE, 0.9f, 0.7f);
            Vec3 mid = p.add(0, 0.8, 0);
            FX.one(level, ModParticles.SP_HIT.get(), mid, v(6.0, 0, 0));
            FX.one(level, ModParticles.SP_GLOW.get(), mid, v(7.0, 0, 0));
            FX.one(level, ModParticles.SP_STAR.get(), mid, v(4.5, 0, 0));
            FX.one(level, ModParticles.SP_RING.get(), mid, v(9.0, 0, 0));
            for (double r : new double[]{1.0, 0.75, 0.5}) {
                FX.one(level, ModParticles.SP_RING_FLAT.get(), p.add(0, 0.08 + (1 - r) * 0.1, 0), v(GUARD_RADIUS * 2.0 * r, 0, 0));
            }
            DariusFX.ring(level, p, (float) GUARD_RADIUS);
            DioManager.debris(level, p, 2.6, 60);
            DioManager.dustPuffs(level, p, GUARD_RADIUS * 0.9, 28, 0.1);
            // arcs: streaks racing outward along the ground
            for (int i = 0; i < 28; i++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                Vec3 d = v(Math.cos(ang), 0.04 + rng.nextDouble() * 0.12, Math.sin(ang));
                double r0 = 0.6 + rng.nextDouble() * 1.2;
                FX.one(level, i % 2 == 0 ? ModParticles.SP_BEAM.get() : ModParticles.SP_LINE.get(), p.add(d.scale(r0)).add(0, 0.15, 0), d.scale(2.5 + rng.nextDouble() * 2.5));
            }
            for (int i = 0; i < 40; i++) {
                Vec3 dv = MartialFX.randomDir(rng);
                FX.one(level, ModParticles.SP_SPARK.get(), mid, v(dv.x * 0.7, Math.abs(dv.y) * 0.6 + 0.1, dv.z * 0.7));
            }
            for (int i = 0; i < 3; i++) {
                FX.one(level, ModParticles.SP_FIST.get(), mid.add(MartialFX.randomDir(rng).scale(0.8)), v(1.4 + i * 0.4, 0, 0));
            }
            for (int i = 0; i < 12; i++) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + (rng.nextDouble() - 0.5) * 5, p.y + 0.2, p.z + (rng.nextDouble() - 0.5) * 5, 1, 0, 0.1, 0, 0.1);
            }
            DariusFX.shake(level, p, 34, 14);
            smashed = true;
        }

        @Override
        void cleanup() {
            GUARDS.remove(casterId);
            if (level.getEntity(casterId) instanceof LivingEntity c && !smashed) DioHold.release(c);
            super.cleanup();
        }
    }

    // =============================================================================================
    //  4. Instantaneous Onslaught
    // =============================================================================================

    private static final class Onslaught extends Session {
        static final int WINDUP = 2;
        static final int DASH_START = WINDUP, DASH_END = WINDUP + DASH_TICKS;
        static final int PAUSE = 8;
        static final int IMPACT = DASH_END + PAUSE;
        static final int END = IMPACT + 12;

        Vec3 start, end, dir;
        double length;
        final List<Target> targets = new ArrayList<>();
        boolean impacted = false;

        private static final class Target {
            final LivingEntity e;
            final double along;       // distance along the dash path
            int fistsShown = 0;
            boolean passed = false;
            Target(LivingEntity e, double along) { this.e = e; this.along = along; }
        }

        Onslaught(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
        }

        @Override
        boolean tick(LivingEntity caster) {
            RandomSource rng = level.random;
            if (age == 0) plan(caster);

            if (age < DASH_START) {
                // crouched sprinter's stance behind him; energy gathers at the legs and fists
                Vec3 sp = start.subtract(dir.scale(1.0));
                moveStand(sp, caster.getYRot());
                for (int i = 0; i < 4; i++) {
                    Vec3 d = MartialFX.randomDir(rng);
                    Vec3 p = sp.add(0, 0.5 + rng.nextDouble() * 1.4, 0).add(d.scale(0.9));
                    FX.one(level, i % 2 == 0 ? ModParticles.SP_SPARK.get() : ModParticles.SP_GLOW.get(), p, sp.add(0, 1, 0).subtract(p).scale(0.15));
                }
                FX.one(level, ModParticles.SP_RING_FLAT.get(), start.add(0, 0.1, 0), v(1.8, 0, 0));
                return false;
            }

            if (age < DASH_END) {
                int k = age - DASH_START + 1;                      // 1..DASH_TICKS
                double travelled = length * k / DASH_TICKS;
                double before = length * (k - 1) / DASH_TICKS;
                Vec3 pos = start.add(dir.scale(travelled));
                SunFX.place(caster, pos, 0f, 0f, false);
                caster.setDeltaMovement(Vec3.ZERO);
                caster.hurtMarked = true;
                // Star Platinum runs alongside, fists already moving
                moveStand(pos.add(MartialFX.rightOf(caster).scale(0.9)).subtract(dir.scale(0.4)), SunFX.yawOf(dir));
                // the purple streak marking the trajectory
                Vec3 a = start.add(dir.scale(before)).add(0, 1.0, 0), b = pos.add(0, 1.0, 0);
                FX.one(level, ModParticles.SP_BEAM.get(), a, b.subtract(a));
                FX.one(level, ModParticles.SP_LINE.get(), a.add(0, 0.5, 0), b.subtract(a));
                FX.one(level, ModParticles.SP_LINE.get(), a.add(0, -0.4, 0), b.subtract(a));
                for (int i = 0; i < 5; i++) {
                    Vec3 q = a.add(b.subtract(a).scale(rng.nextDouble())).add((rng.nextDouble() - 0.5) * 1.4, rng.nextDouble() * 1.6 - 0.8, (rng.nextDouble() - 0.5) * 1.4);
                    FX.one(level, ModParticles.SP_SPARK.get(), q, MartialFX.randomDir(rng).scale(0.05));
                }
                FX.one(level, ModParticles.SP_GLOW.get(), pos.add(0, 1.0, 0), v(1.1, 0, 0));
                FX.one(level, ModParticles.SP_FIST.get(), pos.add(dir.scale(1.0)).add(0, 1.5, 0), v(0.9, 0, 0));
                for (Target t : targets) {
                    if (t.e.isAlive() && travelled >= t.along - 0.5) {
                        if (!t.passed) {
                            t.passed = true;
                            Vec3 c = t.e.getBoundingBox().getCenter();
                            FX.one(level, ModParticles.SP_HIT.get(), c, v(1.2, 0, 0));
                            FX.one(level, ModParticles.SP_RING.get(), c, v(1.2, 0, 0));
                            MartialFX.sound(level, c, ModSounds.SP_GUARD.get(), 0.5f, 1.5f);
                        }
                        // the invisible barrage: twelve fists blink around the target over the dash
                        int want = Math.min(DASH_HITS, (int) Math.ceil(DASH_HITS * (travelled - t.along + 4.0) / 8.0));
                        while (t.fistsShown < want) {
                            Vec3 p = randomOn(rng, t.e);
                            FX.one(level, ModParticles.SP_FIST.get(), p, v(0.6 + rng.nextDouble() * 0.35, 0, 0));
                            if ((t.fistsShown & 1) == 0) FX.one(level, ModParticles.SP_STAR.get(), p, v(0.4, 0, 0));
                            t.fistsShown++;
                        }
                    }
                }
                if (age == DASH_START) {
                    MartialFX.sound(level, start, ModSounds.SP_DASH.get(), 1.6f, 1.0f);
                    summonFx(level, start.add(0, 1.2, 0), 1.3f);
                }
                return false;
            }

            // arrived: Jotaro stands still, Star Platinum behind him with one fist extended
            if (age == DASH_END) {
                SunFX.place(caster, end, 0f, 0f, false);
                moveStand(end.subtract(dir.scale(0.9)), SunFX.yawOf(dir));
                pose(TheWorldEntity.POSE_RECOVER);
                FX.one(level, ModParticles.SP_RING_FLAT.get(), end.add(0, 0.1, 0), v(2.4, 0, 0));
                FX.one(level, ModParticles.SP_GLOW.get(), end.add(0, 1.2, 0), v(1.6, 0, 0));
            }
            moveStand(end.subtract(dir.scale(0.9)), SunFX.yawOf(dir));
            if (age < IMPACT) {
                // the world has not caught up yet: faint purple outlines
                if (age % 2 == 0) {
                    for (Target t : targets) {
                        if (t.e.isAlive()) outline(level, t.e, false);
                    }
                }
                return false;
            }

            if (age == IMPACT) impact(caster);
            if (age == IMPACT + 6 && stand != null) {
                stand.vanish();
                summonFx(level, end.add(0, 1.2, 0), 1.1f);
            }
            return age >= END;
        }

        private void plan(LivingEntity caster) {
            start = caster.position();
            dir = DioManager.flatLook(caster);
            if (dir.lengthSqr() < 1.0E-4) dir = v(0, 0, 1);
            dir = dir.normalize();
            end = SunFX.dashPath(level, caster, dir, DASH_LENGTH);
            length = Math.max(0.5, end.subtract(start).length());
            Vec3 path = end.subtract(start);
            double plen2 = Math.max(1.0E-4, path.lengthSqr());
            AABB box = new AABB(start, end).inflate(DASH_HALF_WIDTH + 2.0, DASH_HALF_HEIGHT + 2.5, DASH_HALF_WIDTH + 2.0);
            List<Target> found = new ArrayList<>();
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> DioManager.validTarget(caster, t))) {
                Vec3 c = t.getBoundingBox().getCenter();
                double f = Mth.clamp(c.subtract(start).dot(path) / plen2, 0.0, 1.0);
                Vec3 on = start.add(path.scale(f)).add(0, caster.getBbHeight() * 0.5, 0);
                double dx = c.x - on.x, dz = c.z - on.z;
                double hw = DASH_HALF_WIDTH + t.getBbWidth() * 0.5;
                if (dx * dx + dz * dz > hw * hw) continue;
                if (Math.abs(c.y - on.y) > DASH_HALF_HEIGHT + t.getBbHeight() * 0.5) continue;
                found.add(new Target(t, f * length));
            }
            found.sort(Comparator.comparingDouble(t -> t.along));
            for (int i = 0; i < found.size() && i < DASH_MAX_TARGETS; i++) targets.add(found.get(i));

            Vec3 sp = start.subtract(dir.scale(1.0));
            spawnStand(sp, SunFX.yawOf(dir), TheWorldEntity.POSE_DASH);
            summonFx(level, sp.add(0, 1.4, 0), 1.5f);
            MartialFX.sound(level, start, ModSounds.SP_SUMMON.get(), 1.0f, 1.4f);
            actionbar(caster, "Yare yare daze...");
            // everything caught freezes until the world catches up
            for (Target t : targets) {
                DioHold.hold(t.e, END);
                t.e.setDeltaMovement(Vec3.ZERO);
                t.e.hurtMarked = true;
            }
        }

        private void impact(LivingEntity caster) {
            RandomSource rng = level.random;
            impacted = true;
            float flatEach = DASH_FLAT / DASH_HITS;
            float pctEach = DASH_PERCENT / DASH_HITS;
            Vec3 path = end.subtract(start);
            double plen2 = Math.max(1.0E-4, path.lengthSqr());
            for (Target tg : targets) {
                LivingEntity t = tg.e;
                if (!t.isAlive()) continue;
                Vec3 c = t.getBoundingBox().getCenter();
                // twelve blows, all landing now
                for (int i = 0; i < DASH_HITS && t.isAlive(); i++) {
                    DioManager.strike(level, caster, spell, t, flatEach, pctEach, 1f, false);
                    Vec3 p = randomOn(rng, t);
                    FX.one(level, ModParticles.SP_HIT.get(), p, v(0.7 + rng.nextDouble() * 0.4, 0, 0));
                    FX.one(level, ModParticles.SP_FIST.get(), p, v(0.5 + rng.nextDouble() * 0.3, 0, 0));
                    if (i % 3 == 0) FX.one(level, ModParticles.SP_RING.get(), p, v(0.9, 0, 0));
                }
                // every outline flashes white at once
                outline(level, t, true);
                FX.one(level, ModParticles.SP_GLOW.get(), c, v(2.2, 0, 0));
                FX.one(level, ModParticles.SP_STAR.get(), c, v(1.8, 0, 0));
                FX.one(level, ModParticles.SP_RING.get(), c, v(2.8, 0, 0));
                for (int i = 0; i < 20; i++) {
                    FX.one(level, ModParticles.SP_SPARK.get(), c, MartialFX.randomDir(rng).scale(0.2 + rng.nextDouble() * 0.4));
                }
                MartialFX.sound(level, c, ModSounds.SP_SMASH.get(), 0.7f, 1.5f);
                MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
                DioHold.release(t);
                if (t.isAlive()) {
                    // thrown away from the dash path; the strong only stagger
                    double f = Mth.clamp(c.subtract(start).dot(path) / plen2, 0.0, 1.0);
                    Vec3 on = start.add(path.scale(f));
                    Vec3 out = c.subtract(on);
                    if (out.x * out.x + out.z * out.z < 0.04) out = MartialFX.rightOf(caster);
                    boolean strong = DariusFX.isBoss(t);
                    MartialFX.shove(t, out.x, out.z, strong ? 0.55 : 2.3, strong ? 0.2 : 0.75);
                }
            }
            // a burst of purple energy around the whole run
            Vec3 mid = start.add(path.scale(0.5)).add(0, 1.0, 0);
            DariusFX.shake(level, mid, 28, 10);
            FX.one(level, ModParticles.SP_RING.get(), end.add(0, 1.2, 0), v(3.0, 0, 0));
            FX.one(level, ModParticles.SP_RING_FLAT.get(), end.add(0, 0.1, 0), v(4.0, 0, 0));
        }

        @Override
        void cleanup() {
            for (Target t : targets) DioHold.release(t.e);
            super.cleanup();
        }
    }
}
