package com.example.jjkspells.spells;

import com.example.jjkspells.GarenConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side logic of Decisive Strike and Judgment (a small scheduler plus the spin tracker). */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class GarenManager {
    /** True while one of these spells is itself dealing damage (so the "next attack" bonus does not trigger on it). */
    public static boolean busy;

    public static final String EMPOWER_KEY = "jjk_decisive_empower_until";
    private static final UUID SPEED_ID = UUID.fromString("c2f4a1e8-7b36-4d59-8e0a-91b5d3c47f20");

    private GarenManager() {
    }

    // ================================================================================================
    // helpers
    // ================================================================================================

    public static float weaponDamage(LivingEntity c) {
        return c.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? (float) c.getAttributeValue(Attributes.ATTACK_DAMAGE) : 2f;
    }

    public static boolean validTarget(LivingEntity caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive() || le.isSpectator()) return false;
        if (le instanceof Player p && p.isCreative()) return false;
        return !(le.isAlliedTo(caster) || DamageSources.isFriendlyFireBetween(caster, le));
    }

    static void weaponHit(LivingEntity caster, LivingEntity target, float dmg) {
        busy = true;
        try {
            target.invulnerableTime = 0;
            DamageSource src = caster instanceof Player p ? caster.damageSources().playerAttack(p) : caster.damageSources().mobAttack(caster);
            target.hurt(src, dmg);
        } finally {
            busy = false;
        }
    }

    private static Vec3 flatLook(LivingEntity c) {
        Vec3 l = c.getLookAngle();
        Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 rightOf(Vec3 f) {
        return new Vec3(-f.z, 0, f.x).scale(-1); // caster's right-hand side
    }

    private static void line(ServerLevel l, Vec3 a, Vec3 b) {
        FX.one(l, ModParticles.GR_LINE.get(), a, b.subtract(a));
    }

    // ================================================================================================
    // scheduler
    // ================================================================================================
    private static final class Task {
        int delay;
        final Runnable run;

        Task(int d, Runnable r) { delay = d; run = r; }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    public static void later(int ticks, Runnable r) {
        TASKS.add(new Task(ticks, r));
    }

    // ================================================================================================
    // Decisive Strike
    // ================================================================================================

    public static void decisiveStrike(ServerLevel level, LivingEntity c) {
        // cleanse movement impairment
        c.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        c.setTicksFrozen(0);

        Vec3 f = flatLook(c);
        level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);

        // energy gathers along the blade while the caster plants their feet
        Vec3 r = rightOf(f);
        for (int t = 0; t < 4; t++) {
            final int tick = t;
            later(t, () -> {
                if (!c.isAlive()) return;
                Vec3 base = c.position().add(0, 1.0, 0).add(r.scale(0.5)).add(f.scale(0.2));
                for (int i = 0; i < 6; i++) {
                    double u = (tick * 6 + i) / 24.0;
                    Vec3 p = base.add(0, 0.2 + u * 1.9, 0).add(f.scale(-0.7 * u)).add(r.scale(0.3 * u));
                    FX.one(level, ModParticles.GR_MOTE.get(), p.add((level.random.nextDouble() - .5) * 0.6, (level.random.nextDouble() - .5) * 0.6, (level.random.nextDouble() - .5) * 0.6),
                            p.subtract(c.position().add(0, 1.2, 0)).scale(0.04));
                }
                FX.emit(level, ModParticles.GR_GLOW.get(), c.getX() + r.x * 0.6 - f.x * 0.6, c.getY() + 2.1, c.getZ() + r.z * 0.6 - f.z * 0.6, 0, 0.8 + tick * 0.15, 0, 0, 1.0);
            });
        }

        // lunge
        final int lungeTicks = 4;
        final double per = GarenConfig.DS_LUNGE_BLOCKS.get() / lungeTicks;
        for (int t = 0; t < lungeTicks; t++) {
            later(4 + t, () -> {
                if (!c.isAlive()) return;
                Vec3 m = c.getDeltaMovement();
                c.setDeltaMovement(f.x * per, c.onGround() ? 0.08 : m.y, f.z * per);
                c.hurtMarked = true;
                FX.emit(level, ModParticles.GR_MOTE.get(), c.getX(), c.getY() + 0.3, c.getZ(), 0, -f.x * 0.2, 0.02, -f.z * 0.2, 1.0);
            });
        }
        later(4 + lungeTicks, () -> {
            if (c.isAlive()) decisiveSlash(level, c);
        });
    }

    private static void decisiveSlash(ServerLevel level, LivingEntity c) {
        Vec3 f = flatLook(c);
        Vec3 r = rightOf(f);
        Vec3 eye = c.position().add(0, c.getBbHeight() * 0.7, 0);

        // diagonal trail: from high on the right-back down to low on the left-front
        Vec3 prev = null;
        for (int i = 0; i <= 14; i++) {
            double t = i / 14.0;
            Vec3 p = eye.add(r.scale(1.7 * (1 - 2 * t))).add(0, 1.3 - 2.6 * t, 0).add(f.scale(0.8 + 2.9 * Math.sin(Math.PI * t)));
            if (prev != null) {
                line(level, prev, p);
                line(level, prev.add(0, 0.12, 0), p.add(0, 0.12, 0));
            }
            if (i % 2 == 0) FX.emit(level, ModParticles.GR_GLOW.get(), p.x, p.y, p.z, 0, 0.6, 0, 0, 1.0);
            prev = p;
        }

        float dmg = weaponDamage(c) * GarenConfig.DS_DAMAGE_PERCENT.get().floatValue();
        int stun = (int) (GarenConfig.DS_STUN_SECONDS.get() * 20);
        Vec3 origin = c.position();
        AABB box = c.getBoundingBox().inflate(5.5, 3.0, 5.5);
        boolean hit = false;
        for (Entity e : level.getEntities(c, box, x -> validTarget(c, x))) {
            LivingEntity t = (LivingEntity) e;
            Vec3 d = t.position().subtract(origin);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            double len = flat.length();
            if (len > 4.8 + t.getBbWidth() * 0.5) continue;
            if (len > 1.5 && flat.normalize().dot(f) < 0.1) continue;
            weaponHit(c, t, dmg);
            if (stun > 0) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 6, false, true, true));
            hit = true;
            Vec3 tc = t.getBoundingBox().getCenter();
            for (int k = 0; k < 14; k++) {
                Vec3 v = new Vec3(level.random.nextGaussian(), level.random.nextDouble() * 0.8, level.random.nextGaussian()).scale(0.25);
                FX.one(level, ModParticles.GR_SPARK.get(), tc, v);
            }
        }

        // impact: sparks and a small shockwave in front of the caster
        Vec3 ip = c.position().add(f.scale(3.0));
        FX.emit(level, ModParticles.GR_GLOW.get(), ip.x, ip.y + 1.0, ip.z, 0, 3.0, 0, 0, 1.0);
        FX.emit(level, ModParticles.GR_RING_FLAT.get(), ip.x, ip.y + 0.15, ip.z, 0, 4.0, 0, 0, 1.0);
        FX.emit(level, ModParticles.GR_RING.get(), ip.x, ip.y + 1.0, ip.z, 0, 2.5, 0, 0, 1.0);
        for (int k = 0; k < 24; k++) {
            Vec3 v = new Vec3(level.random.nextGaussian(), level.random.nextDouble() * 0.9 + 0.1, level.random.nextGaussian()).scale(0.3);
            FX.one(level, ModParticles.GR_SPARK.get(), ip.add(0, 0.5, 0), v);
        }
        level.playSound(null, ip.x, ip.y, ip.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, ip.x, ip.y, ip.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, hit ? 0.7f : 0.35f, 1.5f);

        // the next melee attack is empowered for a few seconds
        if (GarenConfig.DS_EMPOWER_PERCENT.get() > 0) {
            c.getPersistentData().putLong(EMPOWER_KEY, level.getGameTime() + 100);
        }
    }

    // ================================================================================================
    // Judgment
    // ================================================================================================
    private static final class Spin {
        final ServerLevel level;
        final LivingEntity caster;
        int ticks;
        float angle;
        final Map<Integer, Integer> lastHit = new HashMap<>();

        Spin(ServerLevel l, LivingEntity c) { level = l; caster = c; }
    }

    private static final Map<UUID, Spin> SPINS = new HashMap<>();

    public static void judgment(ServerLevel level, LivingEntity c) {
        stopSpin(c, false);
        Spin s = new Spin(level, c);
        SPINS.put(c.getUUID(), s);
        AttributeInstance speed = c.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "judgment_speed", GarenConfig.JU_SPEED_BONUS.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 0.6f);
        FX.emit(level, ModParticles.GR_RING_FLAT.get(), c.getX(), c.getY() + 0.1, c.getZ(), 0, GarenConfig.JU_RADIUS.get() * 0.9, 0, 0, 1.0);
    }

    private static void stopSpin(LivingEntity c, boolean finish) {
        Spin s = SPINS.remove(c.getUUID());
        AttributeInstance speed = c.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
        if (s != null && finish && c.isAlive()) finisher(s);
    }

    private static void finisher(Spin s) {
        ServerLevel level = s.level;
        LivingEntity c = s.caster;
        double R = GarenConfig.JU_RADIUS.get();
        float dmg = weaponDamage(c) * GarenConfig.JU_DAMAGE_PERCENT.get().floatValue();
        for (Entity e : level.getEntities(c, c.getBoundingBox().inflate(R, 2.5, R), x -> validTarget(c, x))) {
            LivingEntity t = (LivingEntity) e;
            double dx = t.getX() - c.getX(), dz = t.getZ() - c.getZ();
            if (Math.sqrt(dx * dx + dz * dz) - t.getBbWidth() * 0.5 > R) continue;
            weaponHit(c, t, dmg);
            Vec3 dir = new Vec3(dx, 0, dz);
            dir = dir.lengthSqr() < 1.0E-3 ? new Vec3(0, 0, 1) : dir.normalize();
            double res = 1.0 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            t.setDeltaMovement(t.getDeltaMovement().add(dir.x * 1.1 * res, 0.45 * res, dir.z * 1.1 * res));
            t.hurtMarked = true;
        }
        // heavy finishing slash and ground ripple
        Vec3 p = c.position();
        FX.emit(level, ModParticles.GR_RING_FLAT.get(), p.x, p.y + 0.1, p.z, 0, R * 1.9, 0, 0, 1.0);
        FX.emit(level, ModParticles.GR_RING_FLAT.get(), p.x, p.y + 0.1, p.z, 0, R * 1.2, 0, 0, 1.0);
        FX.emit(level, ModParticles.GR_GLOW.get(), p.x, p.y + 1.0, p.z, 0, R * 0.9, 0, 0, 1.0);
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            line(level, p.add(Math.cos(a) * 1.5, 1.0, Math.sin(a) * 1.5), p.add(Math.cos(a) * (R + 1.2), 1.0, Math.sin(a) * (R + 1.2)));
        }
        dust(level, c, 28, 1.6);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.6f);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** Dust and grass kicked up around the caster's feet (uses the block they stand on). */
    private static void dust(ServerLevel level, LivingEntity c, int count, double spread) {
        BlockPos below = c.blockPosition().below();
        BlockState st = level.getBlockState(below);
        if (st.isAir()) st = level.getBlockState(below.below());
        if (!st.isAir()) {
            FX.emit(level, new BlockParticleOption(ParticleTypes.BLOCK, st), c.getX(), c.getY() + 0.1, c.getZ(), count, spread, 0.15, spread, 0.25);
        }
        FX.emit(level, ParticleTypes.CLOUD, c.getX(), c.getY() + 0.1, c.getZ(), Math.max(1, count / 4), spread * 0.8, 0.05, spread * 0.8, 0.04);
    }

    private static void tickSpin(Spin s) {
        ServerLevel level = s.level;
        LivingEntity c = s.caster;
        double R = GarenConfig.JU_RADIUS.get();
        s.ticks++;
        s.angle += 0.75f;
        Vec3 p = c.position();

        // the blade traces a circular white-blue arc around the caster
        double armR = Math.min(R - 0.3, 3.6);
        Vec3 prev = null;
        for (int k = 0; k <= 7; k++) {
            double a = s.angle - k * 0.26;
            Vec3 q = p.add(Math.cos(a) * armR, 1.0 + 0.05 * k, Math.sin(a) * armR);
            if (prev != null) line(level, prev, q);
            prev = q;
        }
        Vec3 tip = p.add(Math.cos(s.angle) * armR, 1.0, Math.sin(s.angle) * armR);
        FX.emit(level, ModParticles.GR_GLOW.get(), tip.x, tip.y, tip.z, 0, 0.9, 0, 0, 1.0);
        if (s.ticks % 2 == 0) {
            FX.one(level, ModParticles.GR_SPARK.get(), tip, new Vec3(-Math.sin(s.angle) * 0.3, 0.12, Math.cos(s.angle) * 0.3));
        }
        if (s.ticks % 3 == 0) {
            FX.emit(level, ModParticles.GR_RING_FLAT.get(), p.x, p.y + 0.1, p.z, 0, R, 0, 0, 1.0);
        }
        dust(level, c, 3, 1.3);
        if (s.ticks % 5 == 0) {
            level.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.8f + level.random.nextFloat() * 0.3f);
        }

        // damage: every interval, each target at most once per interval
        int interval = Math.max(1, (int) Math.round(GarenConfig.JU_INTERVAL_SECONDS.get() * 20));
        if ((s.ticks - 1) % interval == 0) {
            float dmg = weaponDamage(c) * GarenConfig.JU_DAMAGE_PERCENT.get().floatValue();
            for (Entity e : level.getEntities(c, c.getBoundingBox().inflate(R, 2.0, R), x -> validTarget(c, x))) {
                LivingEntity t = (LivingEntity) e;
                double dx = t.getX() - p.x, dz = t.getZ() - p.z;
                if (Math.sqrt(dx * dx + dz * dz) - t.getBbWidth() * 0.5 > R) continue;
                Integer last = s.lastHit.get(t.getId());
                if (last != null && s.ticks - last < interval) continue;
                s.lastHit.put(t.getId(), s.ticks);
                weaponHit(c, t, dmg);
                Vec3 c0 = t.getBoundingBox().getCenter();
                for (int k = 0; k < 6; k++) {
                    FX.one(level, ModParticles.GR_SPARK.get(), c0, new Vec3(level.random.nextGaussian(), level.random.nextDouble(), level.random.nextGaussian()).scale(0.22));
                }
            }
        }
    }

    // ================================================================================================
    // events
    // ================================================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (int i = 0; i < TASKS.size(); ) {
            Task t = TASKS.get(i);
            if (t.delay-- <= 0) {
                TASKS.remove(i);
                try {
                    t.run.run();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            } else {
                i++;
            }
        }

        if (SPINS.isEmpty()) return;
        int total = (int) Math.round(GarenConfig.JU_DURATION.get() * 20);
        for (Iterator<Map.Entry<UUID, Spin>> it = SPINS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Spin> en = it.next();
            Spin s = en.getValue();
            LivingEntity c = s.caster;
            if (c.isRemoved() || !c.isAlive() || c.level() != s.level) {
                it.remove();
                AttributeInstance speed = c.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(SPEED_ID);
                continue;
            }
            try {
                tickSpin(s);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
            if (s.ticks >= total) {
                it.remove();
                AttributeInstance speed = c.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(SPEED_ID);
                finisher(s);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
        SPINS.clear();
    }
}
