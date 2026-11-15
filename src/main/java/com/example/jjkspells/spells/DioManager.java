package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.entity.RoadRollerEntity;
import com.example.jjkspells.entity.TheWorldEntity;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs DIO's four moves. Each cast becomes a Session that is ticked on the server until it finishes.
 *
 *   MUDAMUDAMUDA!  - The World appears behind DIO and throws 70 punches in 4 seconds, then vanishes.
 *   Stingy Eyes    - two narrow crimson streams leave DIO's eyes (no Stand), piercing up to 4 enemies.
 *   Golden Impact  - The World draws its fist back with spiralling gold energy and lunges.
 *   ROAD ROLLER DA - declaration, the roller is lifted, DIO leaps with it, it crashes down, the crushing barrage, the
 *                    final blow. The locked target (and everything caught by the landing) is held completely still.
 *
 * No blocks are ever changed: all debris is particles.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class DioManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    // ---- numbers ------------------------------------------------------------------------------
    public static final int BARRAGE_TICKS = 80, BARRAGE_HITS = 70;
    public static final float BARRAGE_FLAT = 6f, BARRAGE_PERCENT = 0.25f, BARRAGE_FINAL_FLAT = 60f, BARRAGE_FINAL_PERCENT = 1f;
    public static final double BARRAGE_REACH = 6.0;

    public static final double BEAM_RANGE = 20.0;
    public static final int BEAM_MAX_TARGETS = 4;
    public static final float BEAM_FLAT = 30f, BEAM_PERCENT = 5f;

    public static final double IMPACT_REACH = 7.0, IMPACT_RADIUS = 4.0;
    public static final float IMPACT_DIRECT_FLAT = 100f, IMPACT_DIRECT_PERCENT = 10f, IMPACT_AREA_FLAT = 70f;

    public static final double ROLLER_RANGE = 8.0;
    public static final int ROLLER_WIND = 30, ROLLER_LEAP = 16, ROLLER_CRUSH = 50, ROLLER_PAUSE = 6;
    public static final float ROLLER_RADIUS = 11.5f, ROLLER_CRUSH_RADIUS = 5.0f;
    public static final float ROLLER_HIT_FLAT = 100f, ROLLER_HIT_PERCENT = 10f;
    public static final float ROLLER_TICK_FLAT = 6f, ROLLER_CRUSH_PERCENT_TOTAL = 15f;
    /** Bosses take this fraction of the repeated (crushing) damage. */
    public static final float BOSS_REPEAT_FACTOR = 0.5f;

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

        /** @return true when finished. */
        abstract boolean tick(LivingEntity caster);

        /** Called when the session ends or is aborted (caster died / left). */
        void cleanup() {
            if (stand != null && !stand.isRemoved()) stand.vanish();
        }

        TheWorldEntity spawnStand(Vec3 pos, float yaw, int pose) {
            TheWorldEntity s = TheWorldEntity.create(level);
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
    }

    private static final List<Session> ACTIVE = new ArrayList<>();

    public static void startBarrage(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        ACTIVE.add(new Barrage(level, caster, spell));
    }

    public static void startBeam(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        ACTIVE.add(new Beam(level, caster, spell));
    }

    public static void startImpact(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        ACTIVE.add(new Impact(level, caster, spell));
    }

    public static void startRoller(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
        ACTIVE.add(new Roller(level, caster, spell));
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
                LOGGER.error("A DIO spell failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done || s.age > 400) {
                try {
                    s.cleanup();
                } catch (Exception ex) {
                    LOGGER.error("DIO cleanup failed", ex);
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
    }

    // =============================================================================================
    //  Shared helpers
    // =============================================================================================

    static boolean validTarget(LivingEntity caster, LivingEntity t) {
        if (t == caster || !t.isAlive() || t.isSpectator() || t.isAlliedTo(caster)) return false;
        return !(t instanceof Player p && p.getAbilities().invulnerable);
    }

    /**
     * One blow: {@code flat} damage through the normal damage path, then {@code percent} of max health taken off
     * directly (SpellDamage.percent: ignores damage caps). Bosses take {@code bossFactor} of both. When
     * {@code survive} is set a boss is always left with at least 1 health.
     */
    static void strike(ServerLevel level, LivingEntity caster, AbstractSpell spell, LivingEntity t,
                               float flat, float percent, float bossFactor, boolean survive) {
        if (!t.isAlive()) return;
        boolean boss = DariusFX.isBoss(t);
        float f = flat;
        float p = t.getMaxHealth() * percent / 100f;
        if (boss) {
            f *= bossFactor;
            p *= bossFactor;
            if (survive) {
                float room = Math.max(0f, t.getHealth() - 1f);
                f = Math.min(f, room);
                p = Math.min(p, Math.max(0f, room - f));
            }
        }
        DamageSource src = spell.getDamageSource(caster);
        if (f > 0f) {
            t.invulnerableTime = 0;
            DioHold.bypass = true;
            try {
                DamageSources.applyDamage(t, f, src);
            } finally {
                DioHold.bypass = false;
            }
        }
        if (p > 0f && t.isAlive()) {
            if (survive && boss) p = Math.min(p, Math.max(0f, t.getHealth() - 1f));
            SpellDamage.percent(t, p, caster, src);
        }
    }

    static Vec3 flatLook(LivingEntity c) {
        float yaw = c.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    static Vec3 behind(LivingEntity c, double dist) {
        return c.position().subtract(flatLook(c).scale(dist));
    }

    static void sound(ServerLevel l, Vec3 p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        MartialFX.sound(l, p, s, vol, pitch);
    }

    static void actionbar(LivingEntity c, String text) {
        if (c instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
    }

    // ---- visuals ------------------------------------------------------------------------------

    static Vec3 v(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    /** Golden flash with a ring: The World appearing or vanishing. */
    private static void summonFx(ServerLevel l, Vec3 p, float size) {
        RandomSource rng = l.random;
        FX.one(l, ModParticles.GOLD_GLOW.get(), p, v(size * 1.6, 0, 0));
        FX.one(l, ModParticles.GOLD_RING.get(), p, v(size * 1.3, 0, 0));
        FX.one(l, ModParticles.GOLD_RING_FLAT.get(), v(p.x, p.y - 0.8, p.z), v(size * 1.4, 0, 0));
        for (int i = 0; i < 14; i++) {
            FX.one(l, ModParticles.GOLD_SPARK.get(), p.add(0, rng.nextDouble() * 0.8 - 0.4, 0), MartialFX.randomDir(rng).scale(0.1 + rng.nextDouble() * 0.25));
        }
        for (int i = 0; i < 3; i++) {
            FX.one(l, ModParticles.DR_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.1 + rng.nextDouble() * 0.15));
        }
    }

    /** Yellow speed lines streaming along {@code dir} through a region around {@code centre}. */
    static void speedLines(ServerLevel l, Vec3 centre, Vec3 dir, int n, double spread, double len) {
        RandomSource rng = l.random;
        for (int i = 0; i < n; i++) {
            Vec3 off = MartialFX.randomDir(rng).scale(spread * rng.nextDouble());
            FX.one(l, ModParticles.GOLD_LINE.get(), centre.add(off), dir.scale(len * (0.7 + rng.nextDouble() * 0.6)));
        }
    }

    /** One punch landing: golden flash, crimson starburst, sparks and a few speed lines along the blow. */
    private static void punchFx(ServerLevel l, Vec3 p, Vec3 dir, float size) {
        RandomSource rng = l.random;
        FX.one(l, ModParticles.GOLD_GLOW.get(), p, v(size * 1.1, 0, 0));
        FX.one(l, ModParticles.DR_HIT.get(), p, v(size, 0, 0));
        for (int i = 0; i < 4; i++) {
            FX.one(l, ModParticles.GOLD_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.25));
        }
        for (int i = 0; i < 2; i++) {
            FX.one(l, ModParticles.DR_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.12 + rng.nextDouble() * 0.2));
        }
        speedLines(l, p.subtract(dir.scale(1.6)), dir, 2, 0.5, 3.0);
    }

    static void debris(ServerLevel l, Vec3 ground, double spread, int n) {
        BlockState st = l.getBlockState(BlockPos.containing(ground.x, ground.y - 0.5, ground.z));
        if (st.isAir()) st = Blocks.STONE.defaultBlockState();
        FX.emit(l, new BlockParticleOption(ParticleTypes.BLOCK, st), ground.x, ground.y + 0.3, ground.z, n, spread, 0.3, spread, 0.4);
    }

    static void dustPuffs(ServerLevel l, Vec3 ground, double radius, int n, double up) {
        RandomSource rng = l.random;
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = radius * Math.sqrt(rng.nextDouble());
            double sp = 0.05 + rng.nextDouble() * 0.12;
            FX.one(l, ModParticles.MA_DUST.get(), v(ground.x + Math.cos(a) * r, ground.y + 0.2, ground.z + Math.sin(a) * r),
                    v(Math.cos(a) * sp, up * (0.5 + rng.nextDouble()), Math.sin(a) * sp));
        }
    }

    static double groundAt(ServerLevel l, Vec3 p, double fallback) {
        return GoldTarget.groundY(l, p.x, p.y + 1.0, p.z, 12, fallback);
    }

    // =============================================================================================
    //  1. MUDAMUDAMUDA!
    // =============================================================================================

    private static final class Barrage extends Session {
        int next = 0;

        Barrage(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
        }

        @Override
        boolean tick(LivingEntity caster) {
            Vec3 standPos = behind(caster, 1.1);
            if (age == 0) {
                spawnStand(standPos, caster.getYRot(), TheWorldEntity.POSE_BARRAGE);
                summonFx(level, standPos.add(0, 1.4, 0), 1.6f);
                sound(level, caster.position(), ModSounds.DIO_BARRAGE.get(), 1.4f, 1.0f);
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BARRAGE_TICKS + 4, 2, false, false, true));
                actionbar(caster, "MUDAMUDAMUDAMUDA!");
            }
            moveStand(standPos, caster.getYRot());

            if (next < BARRAGE_HITS && (next * BARRAGE_TICKS) / BARRAGE_HITS <= age) {
                punch(caster, next == BARRAGE_HITS - 1, next);
                next++;
            }
            if (age % 6 == 0 && age < BARRAGE_TICKS) {
                speedLines(level, caster.getEyePosition().add(caster.getLookAngle().scale(2.5)), caster.getLookAngle(), 3, 1.4, 4.0);
            }
            if (age == BARRAGE_TICKS && stand != null) {
                stand.vanish();
                summonFx(level, standPos.add(0, 1.4, 0), 1.2f);
            }
            return age >= BARRAGE_TICKS + 2;
        }

        private void punch(LivingEntity caster, boolean last, int index) {
            Vec3 look = caster.getLookAngle();
            Vec3 eye = caster.getEyePosition();
            boolean right = (index & 1) == 0;
            Vec3 fist = (stand != null ? stand.position() : caster.position()).add(0, 1.8, 0)
                    .add(MartialFX.rightOf(caster).scale(right ? 0.55 : -0.55)).add(look.scale(0.9));

            LivingEntity best = null;
            double bestDot = -2;
            for (LivingEntity t : MartialFX.inFront(level, caster, eye, look, BARRAGE_REACH, 1.6)) {
                if (!validTarget(caster, t)) continue;
                double dot = t.getBoundingBox().getCenter().subtract(eye).normalize().dot(look);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = t;
                }
            }
            if (best == null) {
                Vec3 whiff = fist.add(look.scale(2.6));
                FX.one(level, ModParticles.GOLD_GLOW.get(), whiff, v(0.7, 0, 0));
                MartialFX.streak(level, fist, whiff);
                return;
            }
            Vec3 before = best.getDeltaMovement();
            strike(level, caster, spell, best, BARRAGE_FLAT, BARRAGE_PERCENT, 1f, false);
            if (last && best.isAlive()) {
                strike(level, caster, spell, best, BARRAGE_FINAL_FLAT, BARRAGE_FINAL_PERCENT, 1f, false);
            }
            Vec3 c = best.getBoundingBox().getCenter();
            RandomSource rng = level.random;
            Vec3 p = c.subtract(look.scale(best.getBbWidth() * 0.45))
                    .add((rng.nextDouble() - 0.5) * 0.6, (rng.nextDouble() - 0.5) * 0.6, (rng.nextDouble() - 0.5) * 0.6);
            if (best.isAlive()) {
                if (last) {
                    MartialFX.shove(best, look.x, look.z, 2.4, 0.75);
                } else {
                    // pin them in reach instead of knocking them away
                    best.setDeltaMovement(before.x * 0.3 + look.x * 0.07, before.y, before.z * 0.3 + look.z * 0.07);
                    best.hurtMarked = true;
                }
            }
            if (last) {
                MartialFX.streak(level, fist, p);
                punchFx(level, p, look, 2.4f);
                FX.one(level, ModParticles.GOLD_RING.get(), p, v(3.4, 0, 0));
                FX.one(level, ModParticles.DR_RING_FLAT.get(), v(p.x, best.getY() + 0.1, p.z), v(4.0, 0, 0));
                DariusFX.shake(level, p, 24, 10);
                sound(level, p, SoundEvents.GENERIC_EXPLODE, 1.0f, 1.2f);
            } else {
                punchFx(level, p, look, 0.6f);
                if ((index & 1) == 0) MartialFX.streak(level, fist, p);
                if (index % 3 == 0) {
                    sound(level, p, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 0.8f + rng.nextFloat() * 0.5f);
                }
            }
        }
    }

    // =============================================================================================
    //  2. Space Ripper Stingy Eyes
    // =============================================================================================

    private static final class Beam extends Session {
        static final int GATHER = 8;
        static final double SPEED = 5.0;
        double length;
        double head = 0, prevHead = 0;
        Vec3 dir, origin, right;
        final List<Hit> targets = new ArrayList<>();
        final Set<UUID> done = new HashSet<>();

        private record Hit(LivingEntity e, double dist) {}

        Beam(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
        }

        private Vec3 eye(LivingEntity c, int side) {
            Vec3 look = c.getLookAngle();
            return c.getEyePosition().add(look.scale(0.35)).add(MartialFX.rightOf(c).scale(0.16 * side));
        }

        @Override
        boolean tick(LivingEntity caster) {
            RandomSource rng = level.random;
            if (age == 0) {
                sound(level, caster.position(), ModSounds.DIO_BEAM.get(), 1.3f, 1.0f);
                actionbar(caster, "Space Ripper Stingy Eyes!");
            }
            if (age < GATHER) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 e = eye(caster, side);
                    for (int i = 0; i < 2; i++) {
                        Vec3 d = MartialFX.randomDir(rng);
                        FX.one(level, ModParticles.DR_GATHER.get(), e.add(d.scale(0.9)), d.scale(-0.9 * 0.13));
                    }
                    if (age % 2 == 0) FX.one(level, ModParticles.DR_GLOW.get(), e, v(0.18 + age * 0.05, 0, 0));
                }
                return false;
            }

            if (age == GATHER) fire(caster);
            if (dir == null) return true;

            prevHead = head;
            head = Math.min(length, head + SPEED);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 a = origin.add(right.scale(0.16 * side)).add(dir.scale(prevHead));
                Vec3 b = origin.add(right.scale(0.16 * side)).add(dir.scale(head));
                Vec3 stretch = b.subtract(a);
                FX.one(level, ModParticles.CR_BEAM.get(), a, stretch);
                FX.one(level, ModParticles.CR_CORE.get(), a, stretch);
                if (head > prevHead) {
                    Vec3 m = a.add(stretch.scale(0.5));
                    FX.one(level, ModParticles.DR_SPARK.get(), m, MartialFX.randomDir(rng).scale(0.05));
                }
            }
            for (Hit h : targets) {
                if (h.dist > prevHead - 0.001 && h.dist <= head + 0.001 && done.add(h.e.getUUID())) {
                    strike(level, caster, spell, h.e, BEAM_FLAT, BEAM_PERCENT, 1f, false);
                    Vec3 c = h.e.getBoundingBox().getCenter();
                    DariusFX.redBurst(level, c, 1.1f);
                    sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, 0.7f);
                }
            }
            if (head >= length && prevHead < length) {
                // the streams stop abruptly: a small flash where they end
                Vec3 end = origin.add(dir.scale(length));
                FX.one(level, ModParticles.DR_GLOW.get(), end, v(1.2, 0, 0));
                FX.one(level, ModParticles.DR_HIT.get(), end, v(0.9, 0, 0));
            }
            return head >= length && age > GATHER + 2;
        }

        private void fire(LivingEntity caster) {
            dir = caster.getLookAngle();
            origin = eye(caster, 0);
            right = MartialFX.rightOf(caster);
            Vec3 end = origin.add(dir.scale(BEAM_RANGE));
            BlockHitResult bh = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            length = bh.getType() == HitResult.Type.MISS ? BEAM_RANGE : Math.max(0.5, origin.distanceTo(bh.getLocation()));
            Vec3 tip = origin.add(dir.scale(length));
            AABB box = new AABB(origin, tip).inflate(1.5);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> validTarget(caster, t))) {
                Optional<Vec3> hit = t.getBoundingBox().inflate(0.35).clip(origin, tip);
                if (hit.isPresent()) targets.add(new Hit(t, origin.distanceTo(hit.get())));
            }
            targets.sort(Comparator.comparingDouble(Hit::dist));
            while (targets.size() > BEAM_MAX_TARGETS) targets.remove(targets.size() - 1);
            if (targets.size() == BEAM_MAX_TARGETS) {
                length = Math.min(length, targets.get(targets.size() - 1).dist + 0.7);
            }
            FX.one(level, ModParticles.DR_GLOW.get(), origin, v(1.0, 0, 0));
        }
    }

    // =============================================================================================
    //  3. The World: Golden Impact
    // =============================================================================================

    private static final class Impact extends Session {
        static final int CHARGE = 10, END = 26;
        Vec3 flat;

        Impact(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
        }

        @Override
        boolean tick(LivingEntity caster) {
            if (flat == null) flat = flatLook(caster);
            Vec3 standPos = behind(caster, 1.1);
            if (age == 0) {
                spawnStand(standPos, caster.getYRot(), TheWorldEntity.POSE_HEAVY);
                summonFx(level, standPos.add(0, 1.4, 0), 1.6f);
                sound(level, caster.position(), ModSounds.DIO_SUMMON.get(), 1.2f, 1.0f);
                actionbar(caster, "THE WORLD!");
            }
            double lunge = age < CHARGE ? 0 : Math.min(1.0, (age - CHARGE + 1) / 2.5) * 2.4;
            Vec3 pos = standPos.add(flat.scale(lunge));
            moveStand(pos, caster.getYRot());
            Vec3 fist = pos.add(0, 1.9, 0).add(MartialFX.rightOf(caster).scale(0.5)).add(flat.scale(0.3));

            if (age < CHARGE) {
                RandomSource rng = level.random;
                float k = 1f - age / (float) CHARGE;
                Vec3 fwd = flat;
                Vec3 r = MartialFX.rightOf(caster);
                for (int i = 0; i < 4; i++) {
                    double ang = age * 0.9 + i * Math.PI / 2;
                    double rad = 0.2 + 1.7 * k;
                    Vec3 p = fist.add(r.scale(Math.cos(ang) * rad)).add(0, Math.sin(ang) * rad, 0).subtract(fwd.scale(rad * 0.9));
                    FX.one(level, ModParticles.GOLD_SPARK.get(), p, fist.subtract(p).scale(0.12));
                    if (i % 2 == 0) FX.one(level, ModParticles.GOLD_GLINT.get(), p, fist.subtract(p).scale(0.08));
                }
                if (age % 2 == 0) FX.one(level, ModParticles.GOLD_GLOW.get(), fist, v(0.4 + age * 0.09, 0, 0));
                if (age == 4) sound(level, fist, SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.6f);
            }
            if (age == CHARGE) land(caster, fist);
            if (age == END - 8 && stand != null) {
                stand.vanish();
                summonFx(level, pos.add(0, 1.4, 0), 1.2f);
            }
            return age >= END;
        }

        private void land(LivingEntity caster, Vec3 fist) {
            LivingEntity direct = GoldTarget.find(level, caster, IMPACT_REACH);
            if (direct != null && !validTarget(caster, direct)) direct = null;
            Vec3 p = direct != null ? direct.getBoundingBox().getCenter() : GoldTarget.aimPoint(level, caster, IMPACT_REACH);
            Vec3 look = caster.getLookAngle();
            double gy = groundAt(level, p, p.y);
            Vec3 ground = v(p.x, gy, p.z);

            if (direct != null) strike(level, caster, spell, direct, IMPACT_DIRECT_FLAT, IMPACT_DIRECT_PERCENT, 1f, false);
            final LivingEntity d = direct;
            List<LivingEntity> hit = new ArrayList<>();
            if (d != null) hit.add(d);
            AABB box = new AABB(p, p).inflate(IMPACT_RADIUS + 3.0);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> validTarget(caster, t) && t != d)) {
                if (t.getBoundingBox().getCenter().distanceTo(p) > IMPACT_RADIUS + t.getBbWidth() * 0.5) continue;
                strike(level, caster, spell, t, IMPACT_AREA_FLAT, 0f, 1f, false);
                hit.add(t);
            }
            for (LivingEntity t : hit) {
                if (!t.isAlive()) continue;
                Vec3 out = t.position().subtract(p);
                if (t == d) out = look;
                MartialFX.shove(t, out.x, out.z, t == d ? 2.0 : 1.2, t == d ? 0.6 : 0.5);
            }

            // visuals: shockwave, rings, debris, sound
            RandomSource rng = level.random;
            sound(level, p, ModSounds.DIO_IMPACT.get(), 2.0f, 1.0f);
            sound(level, p, SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
            FX.one(level, ModParticles.GOLD_GLOW.get(), p, v(5.0, 0, 0));
            FX.one(level, ModParticles.DR_HIT.get(), p, v(3.4, 0, 0));
            FX.one(level, ModParticles.GOLD_RING.get(), p, v(6.5, 0, 0));
            FX.one(level, ModParticles.GOLD_RING_FLAT.get(), ground.add(0, 0.08, 0), v(IMPACT_RADIUS * 1.5, 0, 0));
            FX.one(level, ModParticles.GOLD_RING_FLAT.get(), ground.add(0, 0.12, 0), v(IMPACT_RADIUS * 0.9, 0, 0));
            FX.one(level, ModParticles.DR_RING_FLAT.get(), ground.add(0, 0.1, 0), v(IMPACT_RADIUS * 1.2, 0, 0));
            DariusFX.ring(level, ground, (float) IMPACT_RADIUS);
            debris(level, ground, 2.2, 40);
            dustPuffs(level, ground, IMPACT_RADIUS * 0.8, 16, 0.08);
            for (int i = 0; i < 18; i++) {
                Vec3 dv = MartialFX.randomDir(rng);
                FX.one(level, i % 2 == 0 ? ModParticles.GOLD_SHARD.get() : ModParticles.GOLD_SPARK.get(), p,
                        v(dv.x * 0.5, Math.abs(dv.y) * 0.5 + 0.1, dv.z * 0.5));
            }
            speedLines(level, p, look, 6, 1.0, 4.5);
            MartialFX.streak(level, fist, p);
            DariusFX.shake(level, p, 28, 12);
        }
    }

    // =============================================================================================
    //  4. ROAD ROLLER DA!!
    // =============================================================================================

    private static final class Roller extends Session {
        static final int LAND = ROLLER_WIND + ROLLER_LEAP - 1;       // tick of the crash
        static final int CRUSH_START = LAND + 1;
        static final int CRUSH_END = CRUSH_START + ROLLER_CRUSH - ROLLER_PAUSE - 0;   // last crushing blow lands here
        static final int FINAL = CRUSH_START + ROLLER_CRUSH;          // tick of the last blow
        static final int END = FINAL + 8;
        static final double ROOF = 4.3;                               // height of the roller's roof

        @Nullable LivingEntity target;
        @Nullable RoadRollerEntity roller;
        Vec3 start = Vec3.ZERO, dest = Vec3.ZERO, cdest = Vec3.ZERO, dirFlat = Vec3.ZERO;
        float yawToDest;
        final List<LivingEntity> caught = new ArrayList<>();
        final int crushHits = CRUSH_END - CRUSH_START;

        Roller(ServerLevel level, LivingEntity caster, AbstractSpell spell) {
            super(level, caster, spell);
        }

        @Override
        void cleanup() {
            super.cleanup();
            LivingEntity c = level.getEntity(casterId) instanceof LivingEntity le ? le : null;
            if (c != null) DioHold.release(c);
            if (target != null) DioHold.release(target);
            for (LivingEntity e : caught) DioHold.release(e);
            if (roller != null && !roller.isRemoved()) roller.vanish();
        }

        @Override
        boolean tick(LivingEntity caster) {
            RandomSource rng = level.random;

            if (age == 0) {
                LivingEntity t = GoldTarget.find(level, caster, ROLLER_RANGE);
                if (t != null && validTarget(caster, t)) {
                    target = t;
                    DioHold.hold(t, END + 4);
                }
                Vec3 sp = behind(caster, 1.2);
                spawnStand(sp, caster.getYRot(), TheWorldEntity.POSE_IDLE);
                summonFx(level, sp.add(0, 1.4, 0), 2.0f);
                sound(level, caster.position(), ModSounds.DIO_SUMMON.get(), 1.5f, 0.9f);
                actionbar(caster, "ROAD ROLLER DA!");
                DioHold.hold(caster, ROLLER_WIND);
            }

            if (age < ROLLER_WIND) {
                windup(caster, rng);
            } else if (age < LAND) {
                leap(caster, rng);
            } else if (age == LAND) {
                leap(caster, rng);
                land(caster);
            } else if (age < CRUSH_END) {
                crush(caster, rng);
            } else if (age < FINAL) {
                // the pause: arms high and trembling, the roller shuddering
                if (stand != null) stand.setPose2(TheWorldEntity.POSE_WINDUP);
                if (roller != null) roller.setShake(10);
                if (age % 2 == 0) {
                    FX.one(level, ModParticles.GOLD_GLOW.get(), dest.add(0, ROOF + 2.4, 0), v(1.2 + (age - CRUSH_END) * 0.3, 0, 0));
                    dustPuffs(level, dest, 3.5, 4, 0.05);
                }
                if (age == CRUSH_END) sound(level, dest, SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
            } else if (age == FINAL) {
                finale(caster, rng);
            }

            if (age == FINAL + 4 && stand != null) stand.vanish();
            return age >= END;
        }

        // ---- 0..29: DIO declares, the roller appears and The World lifts it ----

        private void windup(LivingEntity caster, RandomSource rng) {
            double lift = age < 10 ? 0 : 1.2 * Mth.clamp((age - 10) / 12f, 0f, 1f);
            Vec3 sp = behind(caster, 1.2).add(0, lift, 0);
            moveStand(sp, caster.getYRot());
            if (age == 10) {
                if (stand != null) stand.setPose2(TheWorldEntity.POSE_LIFT);
                RoadRollerEntity r = RoadRollerEntity.create(level);
                Vec3 rp = caster.position().add(0, 3.0, 0);
                r.setPos(rp.x, rp.y, rp.z);
                r.setYRot(caster.getYRot());
                r.yRotO = caster.getYRot();
                level.addFreshEntity(r);
                roller = r;
                FX.one(level, ModParticles.GOLD_GLOW.get(), rp.add(0, 2.2, 0), v(5.0, 0, 0));
                FX.one(level, ModParticles.GOLD_RING.get(), rp.add(0, 2.2, 0), v(5.0, 0, 0));
                sound(level, rp, SoundEvents.ANVIL_PLACE, 1.6f, 0.5f);
                sound(level, rp, SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.5f);
            }
            if (roller != null) {
                float e = Mth.clamp((age - 10) / 20f, 0f, 1f);
                float ease = e * e * (3f - 2f * e);
                Vec3 rp = caster.position().subtract(flatLook(caster).scale(0.5)).add(0, 3.0 + 1.6 * ease, 0);
                roller.setPos(rp.x, rp.y, rp.z);
                roller.setYRot(caster.getYRot());
                roller.setShake(age > 20 ? 2 : 0);
                if (age % 2 == 0) {
                    Vec3 base = rp.add(0, 2.2, 0);
                    FX.one(level, ModParticles.GOLD_SPARK.get(), rp.add((rng.nextDouble() - 0.5) * 3, 0, (rng.nextDouble() - 0.5) * 3), v(0, 0.12, 0));
                    FX.one(level, ModParticles.GOLD_GLINT.get(), base.add((rng.nextDouble() - 0.5) * 3, (rng.nextDouble() - 0.5) * 2, (rng.nextDouble() - 0.5) * 3), v(0, 0.05, 0));
                }
            }
            if (age < 10 && age % 3 == 0) {
                FX.one(level, ModParticles.GOLD_RING_FLAT.get(), caster.position().add(0, 0.06, 0), v(2.4 + age * 0.2, 0, 0));
            }
        }

        // ---- 30..45: the leap ----

        private void lockOn(LivingEntity caster) {
            start = caster.position();
            Vec3 p;
            if (target != null && target.isAlive()) {
                p = target.position();
            } else {
                p = GoldTarget.aimPoint(level, caster, ROLLER_RANGE);
            }
            double gy = GoldTarget.groundY(level, p.x, p.y + 1.0, p.z, 12, p.y);
            dest = v(p.x, gy, p.z);
            Vec3 d = v(dest.x - start.x, 0, dest.z - start.z);
            double len = d.length();
            dirFlat = len < 0.2 ? flatLook(caster) : d.scale(1.0 / len);
            double back = Math.min(2.6, len * 0.6);
            double cx = dest.x - dirFlat.x * back, cz = dest.z - dirFlat.z * back;
            double cy = GoldTarget.groundY(level, cx, dest.y + 2.0, cz, 8, dest.y);
            cdest = v(cx, cy, cz);
            yawToDest = SunFX.yawOf(dirFlat);
            DioHold.release(caster);
            sound(level, start, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.6f);
            FX.one(level, ModParticles.GOLD_RING_FLAT.get(), start.add(0, 0.06, 0), v(4.5, 0, 0));
        }

        private void leap(LivingEntity caster, RandomSource rng) {
            if (age == ROLLER_WIND) lockOn(caster);
            float u = Mth.clamp((age - ROLLER_WIND + 1) / (float) ROLLER_LEAP, 0f, 1f);
            // DIO: a quick arc to the landing spot
            Vec3 cp = v(Mth.lerp(u, start.x, cdest.x), Mth.lerp(u, start.y, cdest.y) + 2.8 * Mth.sin(u * Mth.PI), Mth.lerp(u, start.z, cdest.z));
            SunFX.place(caster, cp, caster.getYRot(), caster.getXRot(), false);
            // the roller: carried high over DIO, then it drops onto the target
            double h;
            if (u < 0.5f) {
                h = 4.6 + 3.4 * Mth.sin(u * Mth.PI);
            } else {
                float f = (u - 0.5f) * 2f;
                h = 8.0 * (1.0 - f * f);
            }
            Vec3 rp = v(Mth.lerp(u, start.x, dest.x), Mth.lerp(u, start.y, dest.y) + h, Mth.lerp(u, start.z, dest.z));
            if (roller != null) {
                roller.setPos(rp.x, rp.y, rp.z);
                roller.setYRot(yawToDest);
                roller.setShake(u < 0.5f ? 2 : 4);
            }
            moveStand(cp.subtract(dirFlat.scale(1.2)).add(0, 1.2 * (1f - u * 0.3f), 0), yawToDest);
            if (stand != null) stand.setPose2(TheWorldEntity.POSE_LIFT);
            // golden speed lines along the motion
            Vec3 dir = v(cdest.x - start.x, (u < 0.5f ? 0.6 : -0.9), cdest.z - start.z);
            if (dir.lengthSqr() > 1.0E-4) dir = dir.normalize();
            speedLines(level, cp.add(0, 1.0, 0), dir, 5, 1.4, 3.5);
            speedLines(level, rp.add(0, 2.0, 0), v(0, u < 0.5f ? 1 : -1, 0), 4, 2.2, 4.0);
            FX.one(level, ModParticles.GOLD_GLOW.get(), cp.add(0, 1.0, 0), v(1.2, 0, 0));
            if (u > 0.5f) {
                FX.one(level, ModParticles.GOLD_GLINT.get(), rp.add((rng.nextDouble() - 0.5) * 3, 0.3, (rng.nextDouble() - 0.5) * 3), v(0, 0.2, 0));
            }
        }

        // ---- 45: the crash ----

        private List<LivingEntity> within(LivingEntity caster, double radius) {
            AABB box = new AABB(dest.x - radius, dest.y - 6, dest.z - radius, dest.x + radius, dest.y + 8, dest.z + radius);
            List<LivingEntity> out = new ArrayList<>();
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> validTarget(caster, t))) {
                double dx = t.getX() - dest.x, dz = t.getZ() - dest.z;
                if (dx * dx + dz * dz <= (radius + t.getBbWidth() * 0.5) * (radius + t.getBbWidth() * 0.5)) out.add(t);
            }
            return out;
        }

        private void land(LivingEntity caster) {
            if (roller != null) {
                roller.setPos(dest.x, dest.y, dest.z);
                roller.setYRot(yawToDest);
                roller.setShake(4);
            }
            // everything caught is held still for the rest of the animation, then hit
            int remaining = END - LAND + 4;
            for (LivingEntity t : within(caster, ROLLER_RADIUS)) {
                DioHold.hold(t, remaining);
                if (!caught.contains(t)) caught.add(t);
            }
            if (target != null && !caught.contains(target)) caught.add(target);
            for (LivingEntity t : new ArrayList<>(caught)) {
                strike(level, caster, spell, t, ROLLER_HIT_FLAT, ROLLER_HIT_PERCENT, 1f, false);
            }
            DioHold.hold(caster, remaining);

            // the stand climbs onto the roof to begin hammering down
            if (stand != null) {
                stand.setPose2(TheWorldEntity.POSE_SMASH);
                moveStand(dest.add(0, ROOF, 0), yawToDest);
            }

            RandomSource rng = level.random;
            sound(level, dest, ModSounds.ROLLER_CRASH.get(), 4.0f, 1.0f);
            sound(level, dest, SoundEvents.ANVIL_LAND, 2.5f, 0.4f);
            sound(level, dest, SoundEvents.GENERIC_EXPLODE, 2.5f, 0.55f);
            FX.one(level, ModParticles.GOLD_GLOW.get(), dest.add(0, 1.2, 0), v(9.0, 0, 0));
            FX.one(level, ModParticles.DR_HIT.get(), dest.add(0, 1.2, 0), v(6.0, 0, 0));
            FX.one(level, ModParticles.GOLD_RING.get(), dest.add(0, 1.0, 0), v(ROLLER_RADIUS * 0.7, 0, 0));
            FX.one(level, ModParticles.GOLD_RING_FLAT.get(), dest.add(0, 0.08, 0), v(ROLLER_RADIUS * 1.15, 0, 0));
            FX.one(level, ModParticles.GOLD_RING_FLAT.get(), dest.add(0, 0.12, 0), v(ROLLER_RADIUS * 0.7, 0, 0));
            FX.one(level, ModParticles.DR_RING_FLAT.get(), dest.add(0, 0.1, 0), v(ROLLER_RADIUS * 0.95, 0, 0));
            DariusFX.ring(level, dest, ROLLER_RADIUS);
            debris(level, dest, 5.0, 90);
            dustPuffs(level, dest, ROLLER_RADIUS * 0.7, 40, 0.12);
            for (int i = 0; i < 40; i++) {
                Vec3 dv = MartialFX.randomDir(rng);
                FX.one(level, i % 3 == 0 ? ModParticles.GOLD_SHARD.get() : ModParticles.GOLD_SPARK.get(), dest.add(0, 0.6, 0),
                        v(dv.x * 0.7, Math.abs(dv.y) * 0.7 + 0.15, dv.z * 0.7));
            }
            speedLines(level, dest.add(0, 5, 0), v(0, -1, 0), 14, 3.5, 6.0);
            FX.one(level, ParticleTypes.EXPLOSION_EMITTER, dest.add(0, 1.0, 0), Vec3.ZERO);
            DariusFX.shake(level, dest, 56, 26);
        }

        // ---- 46..89: the crushing barrage ----

        private void crush(LivingEntity caster, RandomSource rng) {
            int i = age - CRUSH_START;                       // 0 .. crushHits-1
            float k = i / (float) Math.max(1, crushHits - 1);   // 0 .. 1: how far the barrage has built up
            if (stand != null) {
                stand.setPose2(TheWorldEntity.POSE_SMASH);
                moveStand(dest.add(0, ROOF, 0), yawToDest);
            }
            if (roller != null) {
                roller.setPos(dest.x, dest.y, dest.z);
                roller.setShake(3 + (int) (7 * k));
            }

            float pct = ROLLER_CRUSH_PERCENT_TOTAL / crushHits;
            for (LivingEntity t : within(caster, ROLLER_CRUSH_RADIUS)) {
                if (!DioHold.isHeld(t)) DioHold.hold(t, END - age + 4);
                strike(level, caster, spell, t, ROLLER_TICK_FLAT, pct, BOSS_REPEAT_FACTOR, false);
                if ((age & 1) == 0 && t.isAlive()) {
                    Vec3 c = t.getBoundingBox().getCenter().add((rng.nextDouble() - 0.5) * 0.6, (rng.nextDouble() - 0.5) * 0.6, (rng.nextDouble() - 0.5) * 0.6);
                    punchFx(level, c, v(0, -1, 0), 0.8f + 1.4f * k);
                }
            }

            // visuals that grow with the barrage
            int puffs = 2 + (int) (6 * k);
            dustPuffs(level, dest, 4.3, puffs, 0.07 + 0.05 * k);
            speedLines(level, dest.add(0, 4.5, 0), v(0, -1, 0), 2 + (int) (4 * k), 2.6, 5.0);
            if (age % 3 == 0) {
                Vec3 sp = dest.add((rng.nextDouble() - 0.5) * 6, 0.3, (rng.nextDouble() - 0.5) * 6);
                FX.one(level, ModParticles.GOLD_SHARD.get(), sp, v((rng.nextDouble() - 0.5) * 0.3, 0.25 + 0.2 * k, (rng.nextDouble() - 0.5) * 0.3));
            }
            if (age % 4 == 0) {
                FX.one(level, ModParticles.GOLD_RING_FLAT.get(), dest.add(0, 0.1, 0), v(3.0 + 5.0 * k, 0, 0));
                debris(level, dest, 3.0, 8 + (int) (14 * k));
            }
            if (age % 3 == 0) {
                sound(level, dest, SoundEvents.ANVIL_LAND, 1.3f, 0.5f + 0.5f * k);
                sound(level, dest, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f + 0.4f * k);
            }
            if (age % 8 == 0) DariusFX.shake(level, dest, 40, 10 + (int) (8 * k));
        }

        // ---- 96: the final blow ----

        private void finale(LivingEntity caster, RandomSource rng) {
            if (stand != null) {
                stand.setPose2(TheWorldEntity.POSE_SLAM);
                moveStand(dest.add(0, ROOF, 0), yawToDest);
            }
            // free everything first so the knockback can actually move them
            List<LivingEntity> victims = within(caster, ROLLER_RADIUS);
            for (LivingEntity t : caught) if (!victims.contains(t) && t.isAlive()) victims.add(t);
            for (LivingEntity t : victims) DioHold.release(t);
            for (LivingEntity t : victims) {
                strike(level, caster, spell, t, ROLLER_HIT_FLAT, ROLLER_HIT_PERCENT, 1f, true);
                if (!t.isAlive()) continue;
                Vec3 out = t.position().subtract(dest);
                MartialFX.shove(t, out.x, out.z, 2.6, 1.0);
            }
            if (roller != null) roller.vanish();

            sound(level, dest, ModSounds.ROLLER_CRASH.get(), 5.0f, 0.8f);
            sound(level, dest, SoundEvents.GENERIC_EXPLODE, 4.0f, 0.5f);
            sound(level, dest, SoundEvents.ANVIL_LAND, 3.0f, 0.3f);
            FX.one(level, ModParticles.GOLD_GLOW.get(), dest.add(0, 2.0, 0), v(14.0, 0, 0));
            FX.one(level, ModParticles.DR_GLOW.get(), dest.add(0, 2.0, 0), v(10.0, 0, 0));
            FX.one(level, ModParticles.DR_HIT.get(), dest.add(0, 2.0, 0), v(8.0, 0, 0));
            FX.one(level, ModParticles.GOLD_RING.get(), dest.add(0, 1.5, 0), v(ROLLER_RADIUS, 0, 0));
            for (int r = 0; r < 3; r++) {
                FX.one(level, ModParticles.GOLD_RING_FLAT.get(), dest.add(0, 0.08 + r * 0.05, 0), v(ROLLER_RADIUS * (1.3 - r * 0.28), 0, 0));
            }
            FX.one(level, ModParticles.DR_RING_FLAT.get(), dest.add(0, 0.2, 0), v(ROLLER_RADIUS * 1.1, 0, 0));
            DariusFX.ring(level, dest, ROLLER_RADIUS);
            DariusFX.ring(level, dest, ROLLER_RADIUS * 0.6f);
            debris(level, dest, 6.0, 120);
            dustPuffs(level, dest, ROLLER_RADIUS * 0.8, 50, 0.16);
            for (int i = 0; i < 70; i++) {
                Vec3 dv = MartialFX.randomDir(rng);
                FX.one(level, i % 3 == 0 ? ModParticles.GOLD_SHARD.get() : ModParticles.GOLD_SPARK.get(), dest.add(0, 1.0, 0),
                        v(dv.x * 0.9, Math.abs(dv.y) * 0.9 + 0.2, dv.z * 0.9));
            }
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                FX.one(level, ModParticles.GOLD_LINE.get(), dest.add(Math.cos(a) * 2, 0.5, Math.sin(a) * 2), v(Math.cos(a) * 9, 3, Math.sin(a) * 9));
            }
            speedLines(level, dest.add(0, 1, 0), v(0, 1, 0), 18, 4.0, 9.0);
            FX.one(level, ParticleTypes.EXPLOSION_EMITTER, dest.add(0, 1.5, 0), Vec3.ZERO);
            FX.one(level, ParticleTypes.EXPLOSION_EMITTER, dest.add(2.5, 0.5, 1.5), Vec3.ZERO);
            DariusFX.shake(level, dest, 64, 34);
            DioHold.release(caster);
        }
    }
}
