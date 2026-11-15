package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.entity.LaevatainFxEntity;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.jjkspells.entity.LaevatainFxEntity.BURST;
import static com.example.jjkspells.entity.LaevatainFxEntity.CRACKS;
import static com.example.jjkspells.entity.LaevatainFxEntity.CRESCENT;
import static com.example.jjkspells.entity.LaevatainFxEntity.FRAGMENT;
import static com.example.jjkspells.entity.LaevatainFxEntity.PILLAR;
import static com.example.jjkspells.entity.LaevatainFxEntity.SWORD;
import static com.example.jjkspells.entity.LaevatainFxEntity.WALL;

/**
 * Runs the four spells of Laevatain on the server: Smouldering Fire, Seethe, Sviga Lævi and Twilight.
 * Every spell is a "sequence" ticked once per server tick. A sequence ends by itself, or is aborted (its effect
 * entities removed, invulnerability and hovering undone) the moment its caster dies, logs out or changes dimension.
 * All damage is dealt here; the clients only draw what the LaevatainFxEntity tells them to.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class LaevatainManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final float TAU = (float) (Math.PI * 2);

    private LaevatainManager() {
    }

    // =================================================================================================================
    //  Sequence plumbing
    // =================================================================================================================
    private abstract static class Seq {
        final ServerLevel level;
        final UUID casterId;
        final AbstractSpell spell;
        final int lvl;
        final float sc;
        int age = 0;
        final List<LaevatainFxEntity> owned = new ArrayList<>();

        Seq(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.lvl = lvl;
            this.sc = LaevatainFX.scale(spell, lvl, caster);
        }

        LaevatainFxEntity own(LaevatainFxEntity e) {
            owned.add(e);
            return e;
        }

        /** @return true once the whole sequence is over */
        abstract boolean tick(LivingEntity caster);

        /** The caster is gone (death, logout, dimension change) or the world is closing: remove everything we made. */
        void abort(@Nullable LivingEntity caster) {
            for (LaevatainFxEntity e : owned) {
                if (!e.isRemoved()) e.discard();
            }
        }
    }

    private static final List<Seq> ACTIVE = new ArrayList<>();
    /** Casters that cannot be hurt right now (Twilight). */
    private static final Set<UUID> INVULNERABLE = new HashSet<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LaevatainFX.tickCombustion();
        if (ACTIVE.isEmpty()) return;
        Iterator<Seq> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Seq s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            LivingEntity caster = e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() ? le : null;
            boolean done;
            try {
                if (caster == null) {
                    s.abort(null);
                    INVULNERABLE.remove(s.casterId);
                    it.remove();
                    continue;
                }
                done = s.tick(caster);
            } catch (Exception ex) {
                LOGGER.error("A Laevatain spell failed, cancelling it", ex);
                s.abort(caster);
                INVULNERABLE.remove(s.casterId);
                it.remove();
                continue;
            }
            s.age++;
            if (done) {
                INVULNERABLE.remove(s.casterId);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!INVULNERABLE.isEmpty() && INVULNERABLE.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        INVULNERABLE.clear();
        LaevatainFX.clear();
    }

    public static void startFragment(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
        ACTIVE.add(new FireSeq(level, caster, spell, lvl));
    }

    public static void startSeethe(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
        ACTIVE.add(new SeetheSeq(level, caster, spell, lvl));
    }

    public static void startSviga(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
        ACTIVE.add(new SvigaSeq(level, caster, spell, lvl));
    }

    public static void startTwilight(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
        ACTIVE.add(new TwilightSeq(level, caster, spell, lvl));
    }

    // =================================================================================================================
    //  Small shared things
    // =================================================================================================================
    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(-(to.x - from.x), to.z - from.z) * 57.29577951308232);
    }

    private static Vec3 dirOf(float yawDeg) {
        double a = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(a), 0, Math.cos(a));
    }

    private static void flames(ServerLevel level, Vec3 p, int count, double spread, double speed) {
        FX.emit(level, ModParticles.FUGA_FLAME.get(), p.x, p.y, p.z, count, spread, spread, spread, speed);
    }

    private static void embers(ServerLevel level, Vec3 p, int count, double spread, double speed) {
        FX.emit(level, ModParticles.FUGA_EMBER.get(), p.x, p.y, p.z, count, spread, spread, spread, speed);
    }

    private static void smoke(ServerLevel level, Vec3 p, int count, double spread) {
        FX.emit(level, ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, count, spread, spread, spread, 0.03);
    }

    private static void debris(ServerLevel level, Vec3 p, int count, double speed) {
        var st = level.getBlockState(net.minecraft.core.BlockPos.containing(p.x, p.y - 0.6, p.z));
        if (st.isAir()) st = Blocks.BASALT.defaultBlockState();
        for (int i = 0; i < count; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double k = 0.4 + level.random.nextDouble();
            FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), p.add(Math.cos(a) * 0.8, 0.3, Math.sin(a) * 0.8),
                    new Vec3(Math.cos(a) * speed * k, speed * (0.5 + level.random.nextDouble()), Math.sin(a) * speed * k));
        }
    }

    private static Vec3 center(LivingEntity e) {
        return e.getBoundingBox().getCenter();
    }

    /** Keeps a player or mob where it is (horizontally) and moves it gently to {@code y} by velocity when hovering. */
    private static void hold(LivingEntity c, Vec3 pos, double y, boolean hover) {
        double dx = c.getX() - pos.x, dz = c.getZ() - pos.z;
        if (dx * dx + dz * dz > 0.12) {
            if (c instanceof ServerPlayer sp) {
                sp.connection.teleport(pos.x, c.getY(), pos.z, sp.getYRot(), sp.getXRot());
            } else {
                c.setPos(pos.x, c.getY(), pos.z);
            }
        }
        double vy = hover ? Mth.clamp((y - c.getY()) * 0.3, -1.0, 1.0) : Math.min(0, c.getDeltaMovement().y);
        c.setDeltaMovement(0, vy, 0);
        c.hurtMarked = true;
        c.fallDistance = 0;
    }

    private static void snapTo(LivingEntity c, Vec3 p) {
        if (c instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, sp.getYRot(), sp.getXRot());
        } else {
            c.setPos(p.x, p.y, p.z);
        }
        c.setDeltaMovement(0, 0, 0);
        c.hurtMarked = true;
        c.fallDistance = 0;
    }

    /** Picks up to {@code count} eruption spots inside the circle: under enemies first, then random ones kept apart. */
    private static List<Vec3> spots(ServerLevel level, LivingEntity caster, Vec3 centre, double radius, int count) {
        List<Vec3> out = new ArrayList<>();
        for (LivingEntity e : LaevatainFX.enemiesAround(level, caster, centre, radius, 6)) {
            if (out.size() >= count) break;
            boolean near = false;
            for (Vec3 o : out) {
                if (Math.abs(o.x - e.getX()) < 1.5 && Math.abs(o.z - e.getZ()) < 1.5) near = true;
            }
            if (!near) out.add(new Vec3(e.getX(), LaevatainFX.groundY(level, e.getX(), e.getY() + 1, e.getZ(), 8, e.getY()), e.getZ()));
        }
        int tries = 0;
        while (out.size() < count && tries++ < count * 12) {
            double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * radius * 0.92;
            double x = centre.x + Math.cos(a) * d, z = centre.z + Math.sin(a) * d;
            boolean apart = true;
            double minGap = radius > 10 ? 3.8 : 2.4;
            for (Vec3 o : out) {
                if ((o.x - x) * (o.x - x) + (o.z - z) * (o.z - z) < minGap * minGap) apart = false;
            }
            if (apart) out.add(new Vec3(x, LaevatainFX.groundY(level, x, centre.y + 5, z, 14, centre.y), z));
        }
        return out;
    }

    // =================================================================================================================
    //  1. SMOULDERING FIRE: MAGMA FRAGMENT
    // =================================================================================================================
    private static final class Frag {
        LaevatainFxEntity e;
        UUID target;
        float angle;
        int strikeAt;
        int state;           // 0 orbit, 1 dash, 2 collapse
        boolean hit;
        Vec3 cur = Vec3.ZERO;
        Vec3 dashFrom = Vec3.ZERO;
        Vec3 collapseFrom = Vec3.ZERO;
    }

    private static final class FireSeq extends Seq {
        static final int FORM_TICKS = 10;
        static final double SPEED_STEP = 0.5;   // four sub-steps of this = 2 blocks per tick
        static final double MAX_RANGE = 24.0;

        final List<LaevatainFxEntity> orbit = new ArrayList<>();
        LaevatainFxEntity main;
        UUID targetId;
        Vec3 pos = Vec3.ZERO, dir = new Vec3(0, 0, 1), impact = Vec3.ZERO;
        double travelled;
        int impactAge = -1;
        boolean overload;
        final List<Frag> frags = new ArrayList<>();

        FireSeq(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
            super(level, caster, spell, lvl);
        }

        @Override
        boolean tick(LivingEntity c) {
            if (age < FORM_TICKS) {
                form(c);
                return false;
            }
            if (impactAge < 0) {
                fly(c);
                return false;
            }
            return aftermath(c);
        }

        private void form(LivingEntity c) {
            Vec3 hand = DariusFX.handPos(c);
            if (age == 0) {
                for (int i = 0; i < 5; i++) {
                    orbit.add(own(LaevatainFX.spawn(level, FRAGMENT, hand, 30, 0.34f, 0f, 0f, 0f, 0f)));
                }
                LaevatainFX.sound(level, hand, SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
                LaevatainFX.sound(level, hand, SoundEvents.FIRE_AMBIENT, 1.4f, 0.7f);
            }
            float k = FxEase.ease(age / (float) (FORM_TICKS - 1));
            float radius = Mth.lerp(k, 1.5f, 0.12f);
            for (int i = 0; i < orbit.size(); i++) {
                float a = age * 0.6f + i * TAU / 5f;
                orbit.get(i).setPos(hand.x + Mth.cos(a) * radius, hand.y + Mth.sin(a * 0.8f) * 0.35f * radius, hand.z + Mth.sin(a) * radius);
            }
            if (age % 2 == 0) {
                flames(level, hand, 2, 0.25, 0.03);
                embers(level, hand, 2, 0.5, 0.05);
            }
            if (age == FORM_TICKS - 1) {
                for (LaevatainFxEntity e : orbit) e.discard();
                orbit.clear();
                LivingEntity t = GuillotineManager.pickTarget(level, c, MAX_RANGE);
                targetId = t == null ? null : t.getUUID();
                dir = c.getLookAngle();
                pos = hand;
                main = own(LaevatainFX.spawn(level, FRAGMENT, hand, 90, 1.1f, 1f, 0f, 0f, 0f));
                LaevatainFX.sound(level, hand, SoundEvents.BLAZE_SHOOT, 1.6f, 0.5f);
                LaevatainFX.sound(level, hand, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.6f);
            }
        }

        private void fly(LivingEntity c) {
            Entity te = targetId == null ? null : level.getEntity(targetId);
            if (te instanceof LivingEntity t && t.isAlive()) {
                Vec3 want = center(t).subtract(pos);
                if (want.lengthSqr() > 1.0E-4) {
                    dir = dir.scale(0.68).add(want.normalize().scale(0.32)).normalize();   // it homes in on its target
                }
            }
            for (int i = 0; i < 4; i++) {
                Vec3 next = pos.add(dir.scale(SPEED_STEP));
                BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
                if (bh.getType() == HitResult.Type.BLOCK) {   // it cannot pass through solid blocks
                    impactAt(bh.getLocation(), null, c);
                    return;
                }
                LivingEntity hit = firstEntity(c, pos, next);
                if (hit != null) {
                    impactAt(center(hit), hit, c);
                    return;
                }
                pos = next;
                travelled += SPEED_STEP;
                if (i % 2 == 0) {
                    flames(level, pos, 1, 0.2, 0.02);
                    smoke(level, pos, 1, 0.25);
                }
                if (travelled >= MAX_RANGE) {
                    impactAt(pos, null, c);
                    return;
                }
            }
            if (main != null) main.setPos(pos.x, pos.y, pos.z);
            embers(level, pos, 2, 0.4, 0.06);
        }

        @Nullable
        private LivingEntity firstEntity(LivingEntity c, Vec3 a, Vec3 b) {
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity e : LaevatainFX.enemiesIn(level, c, new AABB(a, b).inflate(2.2))) {
                AABB box = e.getBoundingBox().inflate(0.9);
                if (box.contains(b) || box.clip(a, b).isPresent()) {
                    double d = e.distanceToSqr(a);
                    if (d < bestD) {
                        best = e;
                        bestD = d;
                    }
                }
            }
            return best;
        }

        private void impactAt(Vec3 p, @Nullable LivingEntity direct, LivingEntity c) {
            impact = p;
            impactAge = 0;
            if (main != null) main.discard();
            if (direct != null) {
                overload = LaevatainFX.isBurning(direct);
                LaevatainFX.strike(c, spell, direct, 45f * sc, 0.04f);
                if (direct.isAlive()) LaevatainFX.ignite(level, c, spell, direct, 120, sc);
            }
            LaevatainFX.spawn(level, BURST, p, 14, overload ? 4.5f : 3.2f, 0f, 0f, 0f, 0f);
            LaevatainFX.sound(level, p, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.9f);
            LaevatainFX.sound(level, p, SoundEvents.FIRECHARGE_USE, 1.4f, 0.6f);
            flames(level, p, 18, 0.7, 0.12);
            embers(level, p, 20, 1.0, 0.2);
            debris(level, p, 8, 0.5);

            int n = overload ? 8 : 5;
            List<LivingEntity> near = LaevatainFX.enemiesAround(level, c, p, 9.0, 5.0);
            for (int i = 0; i < n; i++) {
                Frag f = new Frag();
                f.angle = i * TAU / n;
                f.strikeAt = 6 + i * 3;
                f.target = near.isEmpty() ? null : near.get(i % near.size()).getUUID();   // spread over different enemies first
                f.cur = p.add(Math.cos(f.angle) * 1.2, 1.0, Math.sin(f.angle) * 1.2);
                f.e = own(LaevatainFX.spawn(level, FRAGMENT, f.cur, 80, 0.55f, 0f, 0f, 0f, 0f));
                frags.add(f);
            }
        }

        private boolean aftermath(LivingEntity c) {
            int t = impactAge++;
            double orbitR = overload ? 3.2 : 2.4;
            for (Frag f : frags) {
                if (f.e.isRemoved()) continue;
                if (t >= 30) {
                    if (f.state != 2) {
                        f.state = 2;
                        f.collapseFrom = f.cur;
                    }
                    double k = Mth.clamp((t - 29) / 8.0, 0.0, 1.0);
                    f.cur = f.collapseFrom.lerp(impact.add(0, 1.0, 0), k * k);
                } else if (f.state == 0 && t == f.strikeAt && f.target != null) {
                    f.state = 1;
                    f.dashFrom = f.cur;
                }
                if (f.state == 0 && t < 30) {
                    float a = f.angle + t * 0.35f;
                    f.cur = impact.add(Math.cos(a) * orbitR, 1.3 + 0.5 * Math.sin(t * 0.4 + f.angle), Math.sin(a) * orbitR);
                } else if (f.state == 1) {
                    Entity te = level.getEntity(f.target);
                    if (te instanceof LivingEntity target && target.isAlive()) {
                        double k = Mth.clamp((t - f.strikeAt + 1) / 5.0, 0.0, 1.0);
                        f.cur = f.dashFrom.lerp(center(target), k);
                        if (k >= 1.0 && !f.hit) {
                            f.hit = true;
                            LaevatainFX.strike(c, spell, target, 12f * sc, 0.005f);
                            if (target.isAlive()) LaevatainFX.ignite(level, c, spell, target, 120, sc);
                            LaevatainFX.spawn(level, BURST, f.cur, 8, 1.7f, 0f, 0f, 0f, 0f);
                            LaevatainFX.sound(level, f.cur, SoundEvents.FIRECHARGE_USE, 0.9f, 1.3f);
                            embers(level, f.cur, 10, 0.4, 0.15);
                            f.state = 0;
                            f.angle = (float) Math.atan2(f.cur.z - impact.z, f.cur.x - impact.x);
                        }
                    } else {
                        f.state = 0;   // its target is gone
                    }
                }
                f.e.setPos(f.cur.x, f.cur.y, f.cur.z);
                if (t % 3 == 0) embers(level, f.cur, 1, 0.15, 0.03);
            }

            if (t == 38) {
                // Combustion Detonation at the original impact spot
                double radius = overload ? 7.0 : 5.0;
                for (Frag f : frags) f.e.discard();
                for (LivingEntity e : LaevatainFX.enemiesAround(level, c, impact, radius, 5.0)) {
                    LaevatainFX.strike(c, spell, e, (35f + (overload ? 20f : 0f)) * sc, 0.03f);
                    if (e.isAlive()) {
                        LaevatainFX.ignite(level, c, spell, e, 120, sc);
                        LaevatainFX.blast(e, impact, 0.8, 0.3);
                    }
                }
                LaevatainFX.spawn(level, BURST, impact, 20, (float) radius * 1.25f, 0f, 0f, 0f, overload ? 1f : 2f);
                if (overload) LaevatainFX.spawn(level, BURST, impact, 26, (float) radius * 1.7f, 1f, 0f, 0f, 0f);
                LaevatainFX.sound(level, impact, SoundEvents.GENERIC_EXPLODE, 2.2f, 0.7f);
                LaevatainFX.sound(level, impact, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.8f, 0.8f);
                flames(level, impact, 40, radius * 0.4, 0.2);
                embers(level, impact, 40, radius * 0.5, 0.3);
                debris(level, impact, 14, 0.7);
            }
            return t >= 40;
        }
    }

    // =================================================================================================================
    //  2. SEETHE: ERUPTION OF THE SCORCHED EARTH
    // =================================================================================================================
    private static final class SeetheSeq extends Seq {
        static final double RANGE = 20.0, RADIUS = 8.0;
        static final int WIND_UP = 12, ERUPT_EVERY = 3, END = 60, BLAST_AT = 56;

        Vec3 target = Vec3.ZERO;
        List<Vec3> slots = new ArrayList<>();
        final List<Vec3> extras = new ArrayList<>();
        final Map<UUID, Integer> pillarHits = new HashMap<>();
        final Set<UUID> affected = new HashSet<>();

        SeetheSeq(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
            super(level, caster, spell, lvl);
        }

        @Override
        boolean tick(LivingEntity c) {
            if (age == 0) {
                target = LaevatainFX.groundTarget(level, c, RANGE);
                Vec3 from = c.position();
                float yaw = yawTo(from, target);
                double dist = Math.max(1.0, Math.sqrt((target.x - from.x) * (target.x - from.x) + (target.z - from.z) * (target.z - from.z)));
                // the sword goes into the ground in front of her, and the cracks race out towards the target
                Vec3 plant = from.add(dirOf(c.getYRot()).scale(1.3));
                own(LaevatainFX.spawn(level, SWORD, plant.add(0, 3.2, 0), 26, 4.2f, 0f, 0f, c.getYRot(), 178f));
                own(LaevatainFX.spawn(level, BURST, plant, 16, 5.0f, 0f, 0f, 0f, 0f));
                own(LaevatainFX.spawn(level, WALL, from, 70, 1.3f, (float) dist, 1f, yaw, 0f));
                own(LaevatainFX.spawn(level, CRACKS, target, 100, (float) RADIUS + 0.8f, 0f, WIND_UP, 0f, 0f));
                LaevatainFX.sound(level, plant, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
                LaevatainFX.sound(level, plant, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.8f);
                flames(level, plant, 16, 0.6, 0.15);
                debris(level, plant, 8, 0.45);
            }
            if (age > 0 && age < WIND_UP && age % 3 == 0) {
                double a = level.random.nextDouble() * Math.PI * 2, d = level.random.nextDouble() * RADIUS;
                Vec3 p = target.add(Math.cos(a) * d, 0.2, Math.sin(a) * d);
                flames(level, p, 2, 0.2, 0.05);
                embers(level, p, 2, 0.3, 0.08);
            }
            if (age == WIND_UP) {
                slots = spots(level, c, target, RADIUS, 8);
                // Chain Combustion: enemies that are already burning get their own small pillar
                for (LivingEntity e : LaevatainFX.enemiesAround(level, c, target, RADIUS, 6)) {
                    if (extras.size() >= 6) break;
                    if (LaevatainFX.isBurning(e)) {
                        extras.add(new Vec3(e.getX(), LaevatainFX.groundY(level, e.getX(), e.getY() + 1, e.getZ(), 8, e.getY()), e.getZ()));
                    }
                }
            }
            if (age >= WIND_UP) {
                int i = (age - WIND_UP) / ERUPT_EVERY;
                if ((age - WIND_UP) % ERUPT_EVERY == 0 && i < slots.size()) {
                    erupt(c, slots.get(i), false);
                }
                int j = (age - WIND_UP - 1) / ERUPT_EVERY;
                if (age - WIND_UP >= 1 && (age - WIND_UP - 1) % ERUPT_EVERY == 0 && j < extras.size()) {
                    erupt(c, extras.get(j), true);
                }
            }
            if (age == BLAST_AT) {
                for (LivingEntity e : LaevatainFX.enemiesAround(level, c, target, RADIUS, 6)) {
                    LaevatainFX.strike(c, spell, e, 65f * sc, 0.06f);
                    if (e.isAlive()) {
                        LaevatainFX.ignite(level, c, spell, e, 160, sc);
                        LaevatainFX.blast(e, target, 1.1, 0.45);
                    }
                }
                LaevatainFX.spawn(level, BURST, target, 24, 11f, 0f, 0f, 0f, 0f);
                LaevatainFX.spawn(level, BURST, target, 30, 14f, 1f, 0f, 0f, 0f);
                LaevatainFX.sound(level, target, SoundEvents.GENERIC_EXPLODE, 2.4f, 0.65f);
                LaevatainFX.sound(level, target, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.8f, 0.7f);
                flames(level, target.add(0, 1, 0), 60, 4.5, 0.25);
                embers(level, target.add(0, 1, 0), 50, 5.0, 0.35);
                debris(level, target, 20, 0.9);
            }
            return age >= END;
        }

        private void erupt(LivingEntity c, Vec3 p, boolean extra) {
            float vis = extra ? 0.8f : 1.35f;
            float height = extra ? 4.5f : 7.5f;
            int life = Math.max(10, END - age);
            LaevatainFX.spawn(level, PILLAR, p, life, vis, height, Math.min(24, life - 4), level.random.nextFloat() * 360f, 0f);
            LaevatainFX.sound(level, p, SoundEvents.GENERIC_EXPLODE, extra ? 0.8f : 1.2f, extra ? 1.3f : 1.0f);
            LaevatainFX.sound(level, p, SoundEvents.LAVA_POP, 1.0f, 0.7f);
            flames(level, p.add(0, 1, 0), 10, 0.6, 0.18);
            debris(level, p, 6, 0.55);
            for (LivingEntity e : LaevatainFX.enemiesAround(level, c, p, 2.0, 3.5)) {
                int n = pillarHits.getOrDefault(e.getUUID(), 0);
                if (n >= 4) continue;
                pillarHits.put(e.getUUID(), n + 1);
                affected.add(e.getUUID());
                LaevatainFX.strike(c, spell, e, (extra ? 25f : 35f) * sc, extra ? 0.02f : 0.03f);
                if (e.isAlive()) {
                    LaevatainFX.ignite(level, c, spell, e, 160, sc);
                    MartialFX.shove(e, 0, 0, 0, LaevatainFX.isBoss(e) ? 0.2 : 0.62);    // thrown about 2 blocks up
                }
            }
        }
    }

    // =================================================================================================================
    //  3. SVIGA LÆVI: BLADE OF THE BURNING HORIZON
    // =================================================================================================================
    private static final class SvigaSeq extends Seq {
        static final float LENGTH = 11f;
        static final int MANIFEST = 16, SWING = 10, LAUNCH_AT = 21, ERUPT_DELAY = 14;
        static final double RANGE = 28.0, SPEED = 2.5;

        LaevatainFxEntity sword, crescent;
        float startYaw;
        final Set<UUID> swingHit = new HashSet<>();
        final Set<UUID> crescentHit = new HashSet<>();
        boolean crescentDone;
        Vec3 cDir = new Vec3(0, 0, 1), cPos = Vec3.ZERO, pathStart = Vec3.ZERO;
        double travelled;
        int launchAge = -1;
        double groundAtStart;

        SvigaSeq(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
            super(level, caster, spell, lvl);
        }

        @Override
        boolean tick(LivingEntity c) {
            Vec3 hand = DariusFX.handPos(c);
            if (age == 0) {
                startYaw = c.getYRot();
                sword = own(LaevatainFX.spawn(level, SWORD, hand, 90, 1f, 0f, 0f, startYaw, 28f));
                LaevatainFX.sound(level, hand, SoundEvents.BLAZE_AMBIENT, 1.6f, 0.5f);
                LaevatainFX.sound(level, hand, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.6f);
            }
            if (sword != null && !sword.isRemoved() && age < LAUNCH_AT + ERUPT_DELAY + 8) sword.setPos(hand.x, hand.y, hand.z);

            if (age < MANIFEST) {
                startYaw = c.getYRot();
                float k = FxEase.ease((age + 1) / (float) MANIFEST);
                sword.scale(LENGTH * k).angle(26f + 8f * k).heading(startYaw);
                if (age % 2 == 0) {
                    flames(level, hand.add(0, LENGTH * 0.3f * k, 0), 2, 0.5, 0.05);
                    embers(level, hand.add(0, LENGTH * 0.4f * k, 0), 2, 0.8, 0.06);
                }
                if (age == MANIFEST - 2) c.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 28, 2, false, true));
            } else if (age < MANIFEST + SWING) {
                swing(c, age - MANIFEST);
            }

            if (age == LAUNCH_AT) launch(c);
            if (launchAge >= 0 && !crescentDone) moveCrescent(c);
            if (launchAge >= 0 && age == launchAge + ERUPT_DELAY) erupt(c);
            if (launchAge >= 0 && age == launchAge + ERUPT_DELAY + 8) aftershock(c);
            return launchAge >= 0 && age >= launchAge + ERUPT_DELAY + 22;
        }

        private void swing(LivingEntity c, int s) {
            float rel = Mth.lerp((s + 1) / (float) SWING, 80f, -80f);
            sword.heading(startYaw + rel).angle(90f).roll(Mth.lerp((s + 1) / (float) SWING, -18f, 18f));
            if (s == 0) LaevatainFX.sound(level, c.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 2.0f, 0.5f);
            if (s == 2) LaevatainFX.sound(level, c.position(), SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
            // heat trail where the blade passes
            Vec3 tip = c.position().add(dirOf(startYaw + rel).scale(6.0)).add(0, 1.4, 0);
            flames(level, tip, 6, 0.8, 0.1);
            embers(level, tip, 4, 1.0, 0.15);

            Vec3 origin = c.position();
            for (LivingEntity e : LaevatainFX.enemiesAround(level, c, origin, 7.0, 3.0)) {
                if (swingHit.contains(e.getUUID())) continue;
                float er = Mth.wrapDegrees(yawTo(origin, e.position()) - startYaw);
                if (er > 86f || er < rel - 6f) continue;       // the blade has not reached it yet, or it is outside the 160 degree arc
                swingHit.add(e.getUUID());
                boolean weak = e.getHealth() < e.getMaxHealth() * 0.30f;
                LaevatainFX.strike(c, spell, e, 100f * sc, 0.08f);
                LaevatainFX.blast(e, origin, 1.6, 0.45);
                Vec3 p = center(e);
                LaevatainFX.spawn(level, BURST, p, 8, 2.4f, 0f, 0f, 0f, 0f);
                LaevatainFX.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
                if (weak) {
                    // Molten Execution: bonus damage and a crimson explosion (never an instant kill)
                    if (e.isAlive()) {
                        LaevatainFX.strike(c, spell, e, 50f * sc, 0f);
                        LaevatainFX.ignite(level, c, spell, e, 200, sc);
                    }
                    for (LivingEntity o : LaevatainFX.enemiesAround(level, c, e.position(), 3.0, 3.0)) {
                        if (o != e) LaevatainFX.strike(c, spell, o, 25f * sc, 0f);
                    }
                    LaevatainFX.spawn(level, BURST, p, 16, 4.5f, 1f, 0f, 0f, 0f);
                    LaevatainFX.sound(level, p, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.9f);
                    flames(level, p, 24, 0.8, 0.2);
                }
            }
        }

        private void launch(LivingEntity c) {
            launchAge = age;
            cDir = dirOf(startYaw);
            cPos = c.position().add(cDir.scale(1.5)).add(0, 2.4, 0);
            pathStart = cPos;
            groundAtStart = c.getY();
            crescent = own(LaevatainFX.spawn(level, CRESCENT, cPos, 60, 6f, 6f, 0f, startYaw, 0f));
            LaevatainFX.sound(level, cPos, SoundEvents.ENDER_DRAGON_SHOOT, 1.6f, 0.6f);
            LaevatainFX.sound(level, cPos, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
        }

        private void moveCrescent(LivingEntity c) {
            Vec3 a = cPos;
            Vec3 next = a.add(cDir.scale(SPEED));
            BlockHitResult bh = level.clip(new ClipContext(a, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
            if (bh.getType() == HitResult.Type.BLOCK) {
                next = bh.getLocation();
                crescentDone = true;
            }
            travelled += a.distanceTo(next);
            if (travelled >= RANGE) crescentDone = true;
            cPos = next;

            // swept box: everything between the old and new position, 12 wide and 6 tall
            Vec3 side = new Vec3(-cDir.z, 0, cDir.x);
            double cy = a.y;
            for (LivingEntity e : LaevatainFX.enemiesIn(level, c, new AABB(a, next).inflate(7.5, 4.0, 7.5))) {
                if (crescentHit.contains(e.getUUID())) continue;
                Vec3 rel = e.position().subtract(a);
                double along = rel.x * cDir.x + rel.z * cDir.z;
                double lat = Math.abs(rel.x * side.x + rel.z * side.z);
                double segLen = a.distanceTo(next);
                if (along < -1.2 - e.getBbWidth() * 0.5 || along > segLen + 1.2 + e.getBbWidth() * 0.5) continue;
                if (lat > 6.0 + e.getBbWidth() * 0.5) continue;
                if (e.getY() + e.getBbHeight() < cy - 3.0 || e.getY() > cy + 3.0) continue;
                crescentHit.add(e.getUUID());
                LaevatainFX.strike(c, spell, e, 75f * sc, 0.06f);
                Vec3 p = center(e);
                LaevatainFX.spawn(level, BURST, p, 8, 2.2f, 0f, 0f, 0f, 0f);
                LaevatainFX.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
            }
            if (crescent != null && !crescent.isRemoved()) {
                crescent.setPos(cPos.x, cPos.y, cPos.z);
                if (crescentDone) crescent.life(crescent.tickCount + 5);
            }
            flames(level, cPos, 6, 3.0, 0.12);
            embers(level, cPos, 4, 3.0, 0.15);
        }

        private void erupt(LivingEntity c) {
            Vec3 end = cPos;
            double dx = end.x - pathStart.x, dz = end.z - pathStart.z;
            double length = Math.max(2.0, Math.sqrt(dx * dx + dz * dz));
            double gy = LaevatainFX.groundY(level, pathStart.x, pathStart.y, pathStart.z, 12, groundAtStart);
            LaevatainFX.spawn(level, WALL, new Vec3(pathStart.x, gy, pathStart.z), 110, 2.4f, (float) length, 0f, startYaw, 0f);
            Vec3 side = new Vec3(-cDir.z, 0, cDir.x);
            AABB box = new AABB(pathStart, end).inflate(4.0, 6.0, 4.0);
            for (LivingEntity e : LaevatainFX.enemiesIn(level, c, box)) {
                Vec3 rel = e.position().subtract(pathStart);
                double along = rel.x * cDir.x + rel.z * cDir.z;
                double lat = Math.abs(rel.x * side.x + rel.z * side.z);
                if (along < -1.0 || along > length + 1.0 || lat > 2.6 + e.getBbWidth() * 0.5) continue;
                if (e.getY() < gy - 3 || e.getY() > gy + 6.5) continue;
                LaevatainFX.strike(c, spell, e, 40f * sc, 0.03f);
                if (e.isAlive()) LaevatainFX.blast(e, e.position().subtract(cDir.scale(1)), 0.3, 0.5);
            }
            for (double d = 0; d < length; d += 2.0) {
                Vec3 p = new Vec3(pathStart.x + cDir.x * d, gy + 0.5, pathStart.z + cDir.z * d);
                flames(level, p.add(0, 1, 0), 5, 0.5, 0.14);
                embers(level, p.add(0, 1, 0), 3, 0.6, 0.12);
                if (((int) d) % 4 == 0) LaevatainFX.sound(level, p, SoundEvents.GENERIC_BURN, 0.8f, 0.8f);
            }
            LaevatainFX.sound(level, new Vec3(pathStart.x + cDir.x * length * 0.5, gy, pathStart.z + cDir.z * length * 0.5), SoundEvents.GENERIC_EXPLODE, 2.0f, 0.7f);
        }

        private void aftershock(LivingEntity c) {
            Vec3 end = cPos;
            double dx = end.x - pathStart.x, dz = end.z - pathStart.z;
            double length = Math.max(2.0, Math.sqrt(dx * dx + dz * dz));
            double gy = LaevatainFX.groundY(level, pathStart.x, pathStart.y, pathStart.z, 12, groundAtStart);
            for (double d = 2; d < length; d += 7.0) {
                Vec3 p = new Vec3(pathStart.x + cDir.x * d, gy, pathStart.z + cDir.z * d);
                LaevatainFX.spawn(level, BURST, p, 14, 4.2f, 1f, 0f, 0f, 0f);
                debris(level, p, 5, 0.5);
            }
            LaevatainFX.sound(level, new Vec3(end.x, gy, end.z), SoundEvents.GENERIC_EXPLODE, 1.6f, 0.9f);
            if (sword != null && !sword.isRemoved()) sword.life(sword.tickCount + 12);   // the sword breaks up into embers
        }
    }

    // =================================================================================================================
    //  4. TWILIGHT: THE WORLD IN FLAMES
    // =================================================================================================================
    private static final class TwilightSeq extends Seq {
        // phase boundaries, in ticks (9 seconds in all)
        static final int P1_END = 30, P2_START = 30, STRIKE_LEN = 8, P3_START = 80, P4_START = 110, SWING_START = 128, IMPACT = 134, P5_START = 140, END_CONTROL = 180, FIELD_END = IMPACT + 100;
        static final double AREA = 12.0;
        static final float FINAL_PITCH = 134f;

        Vec3 lock = Vec3.ZERO, dir = new Vec3(0, 0, 1), centre = Vec3.ZERO;
        float yawC;
        boolean empowered;
        boolean hovering;
        double hover;
        LaevatainFxEntity sword;
        List<Vec3> pillarSpots = new ArrayList<>();
        final Map<UUID, Integer> pillarHits = new HashMap<>();
        boolean impacted;

        TwilightSeq(ServerLevel level, LivingEntity caster, AbstractSpell spell, int lvl) {
            super(level, caster, spell, lvl);
        }

        private float dmgMul() {
            return empowered ? 1.2f : 1f;
        }

        @Override
        void abort(@Nullable LivingEntity caster) {
            super.abort(caster);
            if (caster != null && hovering) caster.setNoGravity(false);
        }

        @Override
        boolean tick(LivingEntity c) {
            if (age == 0) init(c);
            if (age < END_CONTROL) {
                double y = lock.y + hover;
                hold(c, lock, y, hovering);
            } else if (age == END_CONTROL) {
                INVULNERABLE.remove(casterId);
                if (empowered) c.heal(c.getMaxHealth() * 0.2f);     // Scorching Heart: the flames feed her
            }

            if (age < P1_END) phase1(c);
            else if (age < P3_START) phase2(c);
            else if (age < P4_START) phase3(c);
            else if (age < P5_START) phase4(c);
            else phase5(c);

            if (impacted) field(c);
            return age >= FIELD_END;
        }

        private Vec3 pivot() {
            if (age < P1_END) return lock.add(dir.scale(-1.0)).add(0, 2.4, 0);
            return lock.add(0, 3.0 + hover, 0).add(dir.scale(0.6));
        }

        private void init(LivingEntity c) {
            lock = c.position();
            yawC = c.getYRot();
            dir = dirOf(yawC);
            double cy = LaevatainFX.groundY(level, lock.x + dir.x * 10, lock.y + 3, lock.z + dir.z * 10, 10, lock.y);
            centre = new Vec3(lock.x + dir.x * 10, cy, lock.z + dir.z * 10);
            empowered = c.getHealth() < c.getMaxHealth() * 0.40f;
            INVULNERABLE.add(casterId);
            if (empowered) c.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 3, false, true));
            sword = own(LaevatainFX.spawn(level, SWORD, pivot(), 340, 0.5f, empowered ? 1f : 0f, 0f, yawC, 0f));
            LaevatainFX.sound(level, lock, SoundEvents.BEACON_ACTIVATE, 3.0f, 0.5f);
            LaevatainFX.sound(level, lock, SoundEvents.WITHER_SPAWN, 1.6f, 0.5f);
        }

        // ---- Phase 1: the awakening -----------------------------------------------------------------------
        private void phase1(LivingEntity c) {
            float k = FxEase.ease((age + 1) / (float) P1_END);
            sword.setPos(pivot().x, pivot().y, pivot().z);
            sword.scale(15f * k).angle(0f).heading(yawC);
            if (age == 2) {
                for (LivingEntity e : LaevatainFX.enemiesAround(level, c, lock, AREA, 6.0)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, true));
                    LaevatainFX.strike(c, spell, e, 20f * sc, 0f);
                }
                own(LaevatainFX.spawn(level, BURST, lock, 22, 13f, 1f, 0f, 0f, 0f));
                own(LaevatainFX.spawn(level, CRACKS, lock, 200, (float) AREA + 1f, 0f, 24f, 0f, 0f));
                LaevatainFX.sound(level, lock, SoundEvents.GENERIC_EXPLODE, 2.0f, 0.6f);
            }
            if (age % 3 == 0) {
                flames(level, lock.add(0, 1, 0), 10, 1.2, 0.3);
                smoke(level, lock.add(0, 1.5, 0), 3, 1.2);
                embers(level, lock.add(0, 2, 0), 8, 2.5, 0.3);
            }
            if (age % 10 == 5) own(LaevatainFX.spawn(level, BURST, lock, 12, 6f + age * 0.3f, 0f, 0f, 0f, 0f));
        }

        // ---- Phase 2: six great strikes -------------------------------------------------------------------
        private static final float[] FLAT = {40f, 40f, 50f, 60f, 70f, 90f};
        private static final float[] PCT = {0.02f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f};

        private void phase2(LivingEntity c) {
            Vec3 pv = pivot();
            sword.setPos(pv.x, pv.y, pv.z);
            int rel = age - P2_START;
            int k = Math.min(5, rel / STRIKE_LEN);
            int start = P2_START + k * STRIKE_LEN;
            float p = Mth.clamp((age - start + 1) / (float) STRIKE_LEN, 0f, 1f);
            float heading = yawC, pitch = 90f, roll = 0f;
            switch (k) {
                case 0 -> heading = yawC + Mth.lerp(p, 90f, -90f);
                case 1 -> heading = yawC + Mth.lerp(p, -90f, 90f);
                case 2 -> {
                    heading = yawC + Mth.lerp(p, -40f, 40f);
                    pitch = Mth.lerp(p, 150f, 30f);
                    roll = 40f;
                }
                case 3 -> pitch = Mth.lerp(p, 10f, 120f);
                case 4 -> heading = yawC + Mth.lerp(p, 0f, 360f);
                default -> pitch = Mth.lerp(p, 0f, 132f);
            }
            sword.scale(15f).heading(heading).angle(pitch).roll(roll);
            if (age == start) LaevatainFX.sound(level, lock, SoundEvents.PLAYER_ATTACK_SWEEP, 2.4f, 0.4f + 0.05f * k);

            if (age == start + STRIKE_LEN / 2 && rel < 6 * STRIKE_LEN) {
                for (LivingEntity e : LaevatainFX.enemiesAround(level, c, lock, AREA, 6.0)) {
                    LaevatainFX.strike(c, spell, e, FLAT[k] * sc * dmgMul(), PCT[k] * dmgMul());
                    if (e.isAlive()) {
                        LaevatainFX.ignite(level, c, spell, e, 120, sc);
                        LaevatainFX.blast(e, lock, 0.5, 0.2);       // a short burst; they stay inside the area
                    }
                }
                int arms = k == 4 ? 4 : 1;
                for (int i = 0; i < arms; i++) {
                    float h = k == 4 ? yawC + i * 90f : heading;
                    Vec3 d = dirOf(h);
                    LaevatainFX.spawn(level, CRESCENT, lock.add(0, 2.2, 0).add(d.scale(2.0)), 12, 7f, 5f, 0f, h, 0f).drift(d.scale(1.1));
                    LaevatainFX.spawn(level, BURST, lock.add(d.scale(9.0)), 10, 4.0f, 0f, 0f, 0f, 0f);
                }
                LaevatainFX.sound(level, lock, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.8f);
                flames(level, lock.add(0, 1.5, 0), 30, 4.0, 0.3);
                embers(level, lock.add(0, 1.5, 0), 24, 5.0, 0.35);
            }
        }

        // ---- Phase 3: the molten cataclysm -------------------------------------------------------------------
        private void phase3(LivingEntity c) {
            if (age == P3_START) {
                hovering = true;
                c.setNoGravity(true);
                pillarSpots = spots(level, c, lock, AREA, 8);
                LaevatainFX.sound(level, lock, SoundEvents.WITHER_SPAWN, 1.8f, 0.6f);
            }
            float k = FxEase.ease((age - P3_START) / 18f);
            hover = 3.0 * k;
            Vec3 pv = pivot();
            sword.setPos(pv.x, pv.y, pv.z);
            sword.scale(Mth.lerp(FxEase.ease((age - P3_START) / 24f), 15f, 21f)).angle(0f).roll(0f).heading(yawC);

            int i = (age - (P3_START + 3)) / 3;
            if (age >= P3_START + 3 && (age - (P3_START + 3)) % 3 == 0 && i < pillarSpots.size()) {
                Vec3 p = pillarSpots.get(i);
                int life = IMPACT + 4 - age;
                LaevatainFX.spawn(level, PILLAR, p, life, 2.4f, 13f, 14f, level.random.nextFloat() * 360f, 0f);
                LaevatainFX.sound(level, p, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.7f);
                flames(level, p.add(0, 2, 0), 16, 1.0, 0.25);
                debris(level, p, 10, 0.7);
                for (LivingEntity e : LaevatainFX.enemiesAround(level, c, p, 3.2, 4.0)) {
                    int n = pillarHits.getOrDefault(e.getUUID(), 0);
                    if (n >= 3) continue;
                    pillarHits.put(e.getUUID(), n + 1);
                    LaevatainFX.strike(c, spell, e, 50f * sc, 0.03f);
                    if (e.isAlive()) MartialFX.shove(e, 0, 0, 0, LaevatainFX.isBoss(e) ? 0.25 : 0.7);
                }
            }
            if (age % 4 == 0) embers(level, lock.add(0, 4, 0), 6, 4.0, 0.2);
        }

        // ---- Phase 4: the final twilight ---------------------------------------------------------------------
        private void phase4(LivingEntity c) {
            Vec3 pv = pivot();
            sword.setPos(pv.x, pv.y, pv.z);
            float L = Mth.lerp(FxEase.ease((age - P4_START) / 14f), 21f, 25f);
            float pitch = 0f;
            if (age >= SWING_START) {
                float s = Mth.clamp((age - SWING_START + 1) / (float) (IMPACT - SWING_START), 0f, 1f);
                pitch = FINAL_PITCH * s * s;
            }
            if (age == SWING_START) LaevatainFX.sound(level, lock, SoundEvents.ENDER_DRAGON_GROWL, 3.0f, 0.5f);
            sword.scale(L).angle(pitch).roll(0f).heading(yawC);
            if (age < SWING_START && age % 2 == 0) {
                flames(level, lock.add(0, 6 + hover, 0), 8, 1.5, 0.3);
                embers(level, lock.add(0, 8 + hover, 0), 6, 2.0, 0.3);
            }
            if (age == IMPACT) impact(c);
        }

        private void impact(LivingEntity c) {
            impacted = true;
            hovering = false;
            hover = 0;
            c.setNoGravity(false);
            snapTo(c, lock);                       // the caster comes down with the blade
            c.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 160, 0, false, true));

            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            Set<UUID> direct = new HashSet<>();
            for (LivingEntity e : LaevatainFX.enemiesAround(level, c, centre, 18.0, 10.0)) {
                Vec3 rel = e.position().subtract(lock);
                double along = rel.x * dir.x + rel.z * dir.z;
                double lat = Math.abs(rel.x * side.x + rel.z * side.z);
                if (along >= -1.0 && along <= 19.0 && lat <= 2.5 + e.getBbWidth() * 0.5 && Math.abs(e.getY() - lock.y) <= 9.0) {
                    direct.add(e.getUUID());
                    LaevatainFX.strike(c, spell, e, 200f * sc * dmgMul(), 0.15f * dmgMul());
                }
            }
            for (LivingEntity e : LaevatainFX.enemiesAround(level, c, centre, 18.0, 10.0)) {
                if (!e.isAlive()) continue;
                float flat = 125f * sc + (empowered ? 75f * sc : 0f);
                float pct = 0.10f + (empowered ? 0.05f : 0f);
                LaevatainFX.strike(c, spell, e, flat, pct);
                if (!e.isAlive()) continue;
                if (e.position().distanceTo(centre) <= 6.0) LaevatainFX.strike(c, spell, e, 50f * sc, 0f);
                if (e.isAlive()) {
                    LaevatainFX.ignite(level, c, spell, e, 200, sc);
                    LaevatainFX.blast(e, centre, 0.6, LaevatainFX.isBoss(e) ? 0.3 : 0.95);   // ~5 blocks up; bosses barely move
                }
            }
            own(LaevatainFX.spawn(level, BURST, centre, 34, 20f, 2f, 0f, 0f, 0f));
            own(LaevatainFX.spawn(level, BURST, centre, 40, 14f, 1f, 0f, 0f, 0f));
            own(LaevatainFX.spawn(level, CRACKS, centre, 130, 18.5f, 1f, 8f, 0f, 0f));
            LaevatainFX.sound(level, centre, SoundEvents.GENERIC_EXPLODE, 4.0f, 0.5f);
            LaevatainFX.sound(level, centre, SoundEvents.DRAGON_FIREBALL_EXPLODE, 3.0f, 0.5f);
            LaevatainFX.sound(level, centre, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 0.6f);
            LaevatainFX.sound(level, centre, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0f, 0.5f);
            FX.emit(level, ModParticles.FUGA_BLAST.get(), centre.x, centre.y + 2, centre.z, 6, 6.0, 2.0, 6.0, 0.1);
            flames(level, centre.add(0, 1, 0), 120, 9.0, 0.4);
            embers(level, centre.add(0, 1, 0), 100, 10.0, 0.5);
            debris(level, centre, 40, 1.1);
        }

        // ---- Phase 5: the dying embers ---------------------------------------------------------------------
        private void phase5(LivingEntity c) {
            if (age == P5_START && sword != null && !sword.isRemoved()) sword.life(sword.tickCount + 30);
            if (age % 3 == 0) {
                Vec3 p = centre.add(dir.scale(-4)).add(0, 3 + (age - P5_START) * 0.15, 0);
                embers(level, p, 6, 6.0, 0.12);
                flames(level, centre.add(0, 0.6, 0), 4, 6.0, 0.05);
            }
        }

        /** Burning ground: 10 damage a second to everything standing in it, for 5 seconds after the impact. */
        private void field(LivingEntity c) {
            int t = age - IMPACT;
            if (t > 100) return;
            if (t % 4 == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * 14.0;
                    Vec3 p = centre.add(Math.cos(a) * d, 0.3, Math.sin(a) * d);
                    flames(level, p, 2, 0.3, 0.05);
                    smoke(level, p.add(0, 0.5, 0), 1, 0.3);
                }
            }
            if (t > 0 && t % 20 == 0) {
                for (LivingEntity e : LaevatainFX.enemiesAround(level, c, centre, 14.0, 4.0)) {
                    e.invulnerableTime = 0;
                    DamageSources.applyDamage(e, 10f * sc, spell.getDamageSource(c));
                }
            }
        }
    }

    /** Easing curve shared by the sequences. */
    private static final class FxEase {
        static float ease(float t) {
            t = Mth.clamp(t, 0f, 1f);
            float k = 1f - t;
            return 1f - k * k * k;
        }
    }
}
