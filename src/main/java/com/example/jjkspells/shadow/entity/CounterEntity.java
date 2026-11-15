package com.example.jjkspells.shadow.entity;

import com.example.jjkspells.shadow.config.ShdConfig;
import com.example.jjkspells.shadow.registry.*;
import com.example.jjkspells.shadow.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Queue;
import java.util.UUID;

/**
 * The Perfect Counter. The caster stands still with the sword lowered; every attack on them is caught (no damage) and answered by one
 * counter: in the first part of the window a normal one, in the last part a Perfect one. fx 1 = the counter {perfect, x, y, z, entityId,
 * casterX, casterY, casterZ}, 2 = the slash {x, y, z, yaw, perfect}, 3 = nobody came {}.
 */
public class CounterEntity extends SeqEntity {
    private int counterAt = -1, endAt = -1;
    private boolean perfect, struck, missed;
    private UUID victim;

    public CounterEntity(EntityType<? extends CounterEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 140;
    }

    public static CounterEntity create(Level level, LivingEntity caster) {
        CounterEntity e = new CounterEntity(ModEntities.COUNTER.get(), level);
        e.begin(caster, caster.position(), ShdConfig.COUNTER.range.get(), 0, caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(6.0);
    }

    private LivingEntity find(UUID id) {
        if (id != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = ShdConfig.COUNTER;
        int window = tk(c.windowSeconds.get());
        int perfectFrom = window - tk(c.perfectSeconds.get());
        Vec3 p = position();
        if (age == 0) {
            Util.CATCH.put(ownerId, 1);
            Util.INCOMING.remove(ownerId);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SWORD_LOWER.get(), 1.0f, 0.9f);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HEARTBEAT.get(), 0.8f, 1.0f);
        }
        boolean stance = endAt < 0 || age < endAt;
        if (stance) {
            Util.LOCKED.add(ownerId);
            guard(o, 40);
            freeze(o);
        }
        if (age == tk(0.6)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COUNTER_READY.get(), 1.0f, 1.0f);
        }
        if (age == perfectFrom) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HEARTBEAT.get(), 1.2f, 1.25f);
        }
        // the window
        if (counterAt < 0 && age < window) {
            Queue<UUID> q = Util.INCOMING.get(ownerId);
            LivingEntity a = null;
            while (q != null && !q.isEmpty() && a == null) {
                a = find(q.poll());
                if (a != null && a.distanceTo(o) > c.range.get() + 6) {
                    a = null;
                }
            }
            if (a != null) {
                Util.CATCH.remove(ownerId);
                Util.INCOMING.remove(ownerId);
                counterAt = age;
                perfect = age >= perfectFrom;
                victim = a.getUUID();
                endAt = Math.max(age + tk(1.7), tk(4.0));
                Util.UNTOUCHABLE.add(ownerId);
                Util.stagger(a, 10);
                pushFx(1, perfect ? 1 : 0, a.getX(), a.getY() + a.getBbHeight() * 0.5, a.getZ(), a.getId(), o.getX(), o.getY(), o.getZ());
                Util.sound(level(), a.getX(), a.getY(), a.getZ(), perfect ? ModSounds.PERFECT_DODGE.get() : ModSounds.COUNTER_READY.get(), 2.0f, 1.0f);
            }
        }
        if (counterAt < 0 && age == window && !missed) {
            missed = true;
            Util.CATCH.remove(ownerId);
            Util.INCOMING.remove(ownerId);
            endAt = tk(4.0);
            pushFx(3);
            Util.refundFraction(o, ModSpells.COUNTER.get(), c.refundPercent.get() / 100.0);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SWORD_LOWER.get(), 0.8f, 1.2f);
        }
        // the answer: behind the attacker, a single cut
        if (counterAt >= 0 && age == counterAt + 2) {
            LivingEntity a = find(victim);
            if (a != null) {
                Vec3 look = flat(a.getLookAngle());
                Vec3 want = a.position().subtract(look.scale(1.5));
                Vec3 safe = Util.safeNear(level(), o, want, 2.5);
                Util.snapTo(o, safe == null ? want : safe, Util.yawTo(want, a.position()), 0);
            }
        }
        if (counterAt >= 0 && age == counterAt + 6 && !struck) {
            struck = true;
            LivingEntity a = find(victim);
            if (a != null) {
                Vec3 at = a.position().add(0, a.getBbHeight() * 0.5, 0);
                Util.swing(o);
                Util.hurt(o, a, c.counterFlat.get() + (perfect ? c.perfectBonus.get() : 0), c.counterPercent.get());
                track(a);
                if (a.isAlive()) {
                    if (perfect) {
                        a.stopUsingItem();
                        Util.hold(a, tk(c.interruptSeconds.get()), 80);
                    }
                    Util.knock(a, o.position(), perfect ? 3.5 : 1.8, 0.2);
                }
                pushFx(2, at.x, at.y, at.z, o.getYRot(), perfect ? 1 : 0);
                Util.sound(level(), at.x, at.y, at.z, ModSounds.COUNTER_SLASH.get(), 3.0f, perfect ? 1.0f : 0.9f);
                Util.sparkBurst(level(), at.x, at.y, at.z, perfect ? 2.5 : 1.2, perfect ? 30 : 12);
            }
        }
        if (counterAt >= 0 && age == counterAt + 16) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.SWORD_LOWER.get(), 1.0f, 0.9f);
        }
        if (endAt >= 0 && age >= endAt) {
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
            finish(true);
            discard();
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.CATCH.remove(ownerId);
            Util.INCOMING.remove(ownerId);
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
        }
    }
}
