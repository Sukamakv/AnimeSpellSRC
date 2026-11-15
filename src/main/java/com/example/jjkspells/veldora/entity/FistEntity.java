package com.example.jjkspells.veldora.entity;

import com.example.jjkspells.veldora.config.VelConfig;
import com.example.jjkspells.veldora.registry.*;
import com.example.jjkspells.veldora.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Storm Dragon's Fist. Entity at the target. fx type 1 = a strike {x, y, z, index}, type 2 = the dash start {x, y, z}. */
public class FistEntity extends SeqEntity {
    private final Map<UUID, Integer> struck = new HashMap<>();
    private Random rand;
    private int combo, nextStrike;
    private Vec3 from;

    public FistEntity(EntityType<? extends FistEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 30;
    }

    public static FistEntity create(Level level, LivingEntity caster) {
        var c = VelConfig.FIST;
        FistEntity e = new FistEntity(ModEntities.FIST.get(), level);
        Vec3 p = groundPoint(level, caster, c.range.get());
        e.begin(caster, p, c.radius.get(), c.finalRadius.get(), Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(7.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VelConfig.FIST;
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 7919L + 3);
        }
        if (age == 0) {
            from = o.position();
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 4f, 0.6f);
        }
        if (age < tk(6.0) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 0, 0.8) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 1, o.getZ(), 8, 0.6, 0.8, 0.6, 0.1);
        }
        if (at(age, 0.8)) {
            Util.LOCKED.add(ownerId);
            Util.UNTOUCHABLE.add(ownerId);
            o.setInvisible(true);
            pushFx(2, o.getX(), o.getY(), o.getZ());
            Util.sparkBurst(level(), o.getX(), o.getY() + 1, o.getZ(), 5, 60);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.THUNDER.get(), 4f, 1.3f);
        }
        if (in(age, 0.8, 1.5) && age % 3 == 0) {
            Vec3 a = from == null ? o.position() : from;
            Util.arc(level(), ModParticles.SPARK.get(), a.add(0, 1, 0), p.add(0, 1, 0), 3.0);
        }
        if (at(age, 1.5)) {
            Vec3 d = FractureLike.dir(yaw());
            Vec3 want = p.subtract(d.scale(3.5));
            Double g = Util.findGround(level(), want.x, p.y + 8, want.z);
            Vec3 w2 = new Vec3(want.x, g == null ? p.y : g, want.z);
            Vec3 safe = Util.safeNear(level(), o, w2, 4);
            Util.snapTo(o, safe == null ? w2 : safe, yaw(), 0);
            o.setInvisible(false);
            Util.UNTOUCHABLE.remove(ownerId);
            for (LivingEntity e : foes(o, c.radius.get())) {
                Util.hurt(o, e, c.firstFlat.get(), c.firstPercent.get());
                track(e);
            }
            pushFx(1, p.x, p.y, p.z, -1);
            Util.sparkBurst(level(), p.x, p.y + 1, p.z, 8, 140);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.5, 0), 72, 2.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 6f, 0.9f);
        }
        int s0 = tk(1.8), s1 = tk(4.5), n = c.strikeCount.get();
        if (n > 0 && age >= s0 && age < s1 && nextStrike < n && age >= s0 + nextStrike * Math.max(1, (s1 - s0) / n)) {
            strike(o, c, nextStrike);
            nextStrike++;
        }
        if (at(age, 4.5)) {
            Util.LOCKED.remove(ownerId);
            Util.LOCKED.add(ownerId);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.CHARGE.get(), 6f, 0.7f);
        }
        if (in(age, 4.5, 5.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 1.2, o.getZ(), 12, 1.0, 1.0, 1.0, 0.3);
        }
        if (at(age, 5.5)) {
            double mult = 1.0 + Math.min(c.comboMaxPercent.get(), combo * c.comboBonusPercent.get()) / 100.0;
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), mult, 1.0, c.armorIgnorePercent.get() / 100.0, 0.0);
                track(e);
                if (e.isAlive() && !Util.isBoss(e)) {
                    Util.knock(e, p, 5, 0.4);
                }
            }
            Util.sparkBurst(level(), p.x, p.y + 3, p.z, 20, 300);
            Util.ring(level(), ModParticles.CRACK.get(), p.add(0, 0.6, 0), 120, 3.2, 0.05);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.9, 0), 120, 3.8, 0.06);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 8f, 0.6f);
        }
        if (age == tk(6.0)) {
            Util.LOCKED.remove(ownerId);
        }
    }

    private void strike(LivingEntity o, VelConfig.Fist c, int i) {
        List<LivingEntity> foes = foes(o, c.radius.get());
        Vec3 q;
        LivingEntity target = null;
        if (!foes.isEmpty()) {
            target = foes.get(rand.nextInt(foes.size()));
            q = target.position();
        } else {
            q = spot(rand, 8);
        }
        if (target != null) {
            double a = rand.nextDouble() * Math.PI * 2;
            Vec3 near = q.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
            Vec3 safe = Util.safeNear(level(), o, near, 3);
            Util.snapTo(o, safe == null ? near : safe, Util.yawTo(near, q), 0);
            Util.swing(o);
        }
        for (LivingEntity e : Util.enemies(level(), q, 5, o)) {
            Util.hurt(o, e, c.strikeFlat.get(), 0);
            track(e);
            combo++;
            int h = struck.merge(e.getUUID(), 1, Integer::sum);
            if (h == c.paralysisHits.get() && e.isAlive()) {
                int t = tk(c.paralysisSeconds.get());
                Util.slowPct(e, c.paralysisSlowPercent.get(), t);
                Util.atkSlow(e, c.paralysisAtkPercent.get(), t);
                e.addEffect(new MobEffectInstance(ModEffects.PARALYSIS.get(), t, 0, false, false, true));
            }
        }
        pushFx(1, q.x, q.y, q.z, i);
        Util.sparkBurst(level(), q.x, q.y + 1, q.z, 4, 50);
        Util.ring(level(), ModParticles.SPARK.get(), q.add(0, 0.5, 0), 24, 1.2, 0.04);
        Util.sound(level(), q.x, q.y, q.z, ModSounds.BOOM.get(), 3f, 1.0f + rand.nextFloat() * 0.4f);
    }

    /** Direction helper identical to the one the portal spells use. */
    static final class FractureLike {
        static Vec3 dir(float yaw) {
            double r = Math.toRadians(yaw);
            return new Vec3(-Math.sin(r), 0, Math.cos(r));
        }
    }

    public static Vec3 dir(float yaw) {
        return FractureLike.dir(yaw);
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
        }
    }
}
