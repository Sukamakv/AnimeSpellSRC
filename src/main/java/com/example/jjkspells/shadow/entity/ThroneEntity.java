package com.example.jjkspells.shadow.entity;

import com.example.jjkspells.shadow.config.ShdConfig;
import com.example.jjkspells.shadow.registry.*;
import com.example.jjkspells.shadow.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * The Nameless King - The Throne of False Shadows. The real Shadow sits on the throne; five false ones stand around the stage and
 * answer attacks with counterattacks. fx 1 = a false shadow appears {x, y, z, yaw, pose, id}, 2 = it dissolves {id}, 3 = a counter slash
 * {x, y, z, attackerYaw}, 4 = judgment target {x, y, z}, 5 = the judgment slash {x, y, z}.
 */
public class ThroneEntity extends SeqEntity {
    private static final class Fake {
        Vec3 pos;
        float yaw;
        int pose, id;
        boolean alive = true;
    }

    private final List<Fake> fakes = new ArrayList<>();
    private final Map<UUID, Integer> touchCool = new HashMap<>();
    private Random rand;
    private int counters, nextId, spawned;
    private Vec3 throne, dir;
    private UUID primary;
    private boolean judged;

    public ThroneEntity(EntityType<? extends ThroneEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 260;
    }

    public static ThroneEntity create(Level level, LivingEntity caster) {
        ThroneEntity e = new ThroneEntity(ModEntities.THRONE.get(), level);
        e.begin(caster, caster.position(), ShdConfig.THRONE.radius.get(), 0, caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(10.0);
    }

    private LivingEntity find(UUID id) {
        if (id != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    /** Where the throne stands (client uses the same rule): two and a half blocks behind the caster's position at the cast. */
    public static Vec3 throneAt(Vec3 base, float yaw) {
        double r = Math.toRadians(yaw);
        return base.add(Math.sin(r) * 2.5, 0, -Math.cos(r) * 2.5);
    }

    private void addFake(Vec3 p, float yaw, int pose) {
        Fake f = new Fake();
        f.pos = p;
        f.yaw = yaw;
        f.pose = pose;
        f.id = nextId++;
        fakes.add(f);
        pushFx(1, p.x, p.y, p.z, yaw, pose, f.id);
        Util.fx(level(), ModParticles.SLIME.get(), p.x, p.y + 1, p.z, 8, 0.4, 0.9, 0.4, 0.02);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.ILLUSION_WHISPER.get(), 1.0f, 0.8f + rand.nextFloat() * 0.4f);
    }

    private void dissolve(Fake f) {
        f.alive = false;
        pushFx(2, f.id);
        Util.fx(level(), ModParticles.SLIME.get(), f.pos.x, f.pos.y + 1, f.pos.z, 14, 0.4, 0.9, 0.4, 0.03);
    }

    private void counter(LivingEntity o, ShdConfig.Throne c, LivingEntity attacker) {
        if (counters >= c.maxCounters.get() || !attacker.isAlive()) {
            return;
        }
        Fake near = null;
        double bd = Double.MAX_VALUE;
        for (Fake f : fakes) {
            if (f.alive) {
                double d = f.pos.distanceToSqr(attacker.position());
                if (d < bd) {
                    bd = d;
                    near = f;
                }
            }
        }
        if (near != null) {
            dissolve(near);
        }
        counters++;
        Vec3 look = flat(attacker.getLookAngle());
        Vec3 behind = attacker.position().subtract(look.scale(2.0));
        Double g = Util.findGround(level(), behind.x, behind.y + 2, behind.z);
        if (g != null) {
            behind = new Vec3(behind.x, g, behind.z);
        }
        addFake(behind, Util.yawTo(behind, attacker.position()), rand.nextInt(5));
        Util.hurt(o, attacker, c.counterDamage.get(), 0);
        track(attacker);
        pushFx(3, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(), attacker.getYRot());
        Util.sound(level(), attacker.getX(), attacker.getY(), attacker.getZ(), ModSounds.COUNTERATTACK.get(), 2.0f, 1.0f);
        if (counters >= c.maxCounters.get()) {
            Util.CATCH.remove(ownerId);
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = ShdConfig.THRONE;
        double R = c.radius.get();
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 7919L + 13);
            throne = throneAt(p, yaw());
            double r = Math.toRadians(yaw());
            dir = new Vec3(-Math.sin(r), 0, Math.cos(r));
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THRONE_MANIFEST.get(), 3.0f, 1.0f);
        }
        if (age < tk(9.0) && age % 2 == 0) {
            guard(o, 60);
        }
        if (in(age, 0.0, 1.5) && age % 4 == 0) {
            Util.fx(level(), ModParticles.SLIME.get(), p.x, p.y + 0.3, p.z, 10, R * 0.2 * (age / 30.0), 0.2, R * 0.2 * (age / 30.0), 0.01);
        }
        if (at(age, 1.5)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.shockDamage.get(), 0);
                track(e);
                Util.stagger(e, 12);
            }
            Util.LOCKED.add(ownerId);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ROYAL_AMBIENCE.get(), 2.0f, 1.0f);
        }
        if (age >= tk(1.5) && age < tk(8.0)) {
            Vec3 seat = throne.add(dir.scale(0.2)).add(0, 0.55, 0);
            hover(o, seat, yaw(), 0f);
        }
        // the five false shadows, one after another
        if (spawned < 5 && age >= tk(1.5 + spawned * 0.3)) {
            double[] ang = {-2.0, -1.0, 0.0, 1.0, 2.0};
            double a = Math.toRadians(yaw()) + ang[spawned] * 0.55 + Math.PI;
            double d = R * (0.45 + 0.3 * rand.nextDouble());
            Vec3 at = throne.add(Math.sin(a + Math.PI) * 0.0, 0, 0).add(-Math.sin(a) * d, 0, Math.cos(a) * d);
            at = new Vec3(at.x, p.y, at.z);
            Double g = Util.findGround(level(), at.x, p.y + 3, at.z);
            if (g != null) {
                at = new Vec3(at.x, g, at.z);
            }
            addFake(at, Util.yawTo(at, throne), spawned);
            spawned++;
        }
        // phase 3: counters
        if (at(age, 3.0)) {
            Util.CATCH.put(ownerId, 2);
        }
        if (age >= tk(3.0) && age < tk(7.5) && counters < c.maxCounters.get()) {
            Queue<UUID> q = Util.INCOMING.get(ownerId);
            while (q != null && !q.isEmpty() && counters < c.maxCounters.get()) {
                LivingEntity a = find(q.poll());
                if (a != null) {
                    counter(o, c, a);
                }
            }
            if (age % 4 == 0) {
                for (LivingEntity e : foes(o, R + 6)) {
                    int cd = touchCool.getOrDefault(e.getUUID(), 0);
                    if (age < cd) {
                        continue;
                    }
                    for (Fake f : fakes) {
                        if (f.alive && f.pos.distanceTo(e.position()) <= c.touchRadius.get()) {
                            touchCool.put(e.getUUID(), age + 20);
                            counter(o, c, e);
                            break;
                        }
                    }
                }
            }
        }
        if (at(age, 7.5)) {
            Util.CATCH.remove(ownerId);
            Util.INCOMING.remove(ownerId);
            LivingEntity best = null;
            for (LivingEntity e : foes(o, R + 10)) {
                if (best == null || e.getMaxHealth() > best.getMaxHealth()) {
                    best = e;
                }
            }
            if (best != null) {
                primary = best.getUUID();
                pushFx(4, best.getX(), best.getY(), best.getZ());
            }
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ILLUSION_WHISPER.get(), 2.0f, 0.6f);
        }
        if (at(age, 8.0)) {
            LivingEntity t = find(primary);
            if (t != null) {
                o.setNoGravity(false);
                Vec3 look = flat(t.getLookAngle());
                Vec3 want = t.position().subtract(look.scale(1.6));
                Vec3 safe = Util.safeNear(level(), o, want, 2.5);
                Util.snapTo(o, safe == null ? want : safe, Util.yawTo(want, t.position()), 0);
            }
        }
        if (at(age, 8.5) && !judged) {
            judged = true;
            LivingEntity t = find(primary);
            Vec3 centre = t != null ? t.position() : p;
            for (LivingEntity e : Util.enemies(level(), centre, c.finalRadius.get(), o)) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get());
                track(e);
            }
            pushFx(5, centre.x, centre.y, centre.z);
            Util.sparkBurst(level(), centre.x, centre.y + 1.2, centre.z, 5, 100);
            Util.sound(level(), centre.x, centre.y, centre.z, ModSounds.FINAL_JUDGMENT.get(), 5.0f, 1.0f);
        }
        if (at(age, 9.0)) {
            for (Fake f : fakes) {
                if (f.alive) {
                    dissolve(f);
                }
            }
        }
        if (at(age, 9.3)) {
            Vec3 back = new Vec3(throne.x, throne.y, throne.z);
            Vec3 safe = Util.safeNear(level(), o, back, 2.5);
            Util.snapTo(o, safe == null ? back : safe, yaw(), 0);
            Util.sparkBurst(level(), back.x, back.y + 1, back.z, 3, 40);
        }
        if (at(age, 9.5)) {
            Util.LOCKED.remove(ownerId);
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.CATCH.remove(ownerId);
            Util.INCOMING.remove(ownerId);
            Util.LOCKED.remove(ownerId);
            LivingEntity o = ownerAny();
            if (o != null) {
                o.setNoGravity(false);
            }
        }
    }
}
