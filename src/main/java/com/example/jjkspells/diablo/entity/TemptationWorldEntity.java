package com.example.jjkspells.diablo.entity;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.registry.*;
import com.example.jjkspells.diablo.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Temptation World: invitation, dome, kingdom, twelve spears, verdict, collapse. fx type 1 = a spear {x, y, z}. */
public class TemptationWorldEntity extends SeqEntity {
    private Random rand;

    public TemptationWorldEntity(EntityType<? extends TemptationWorldEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 24;
    }

    public static TemptationWorldEntity create(Level level, LivingEntity caster) {
        var c = DiaConfig.WORLD;
        TemptationWorldEntity e = new TemptationWorldEntity(ModEntities.WORLD.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.radius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(12.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = DiaConfig.WORLD;
        Vec3 p = position();
        double R = c.radius.get();
        if (rand == null) {
            rand = new Random(getId() * 104729L + 3);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BELL.get(), 4f, 0.6f);
        }
        if (at(age, 1.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 5f, 0.5f);
        }
        if (at(age, 2.0)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.openFlat.get(), c.openPercent.get());
                Util.knock(e, p, 5, 0.2);
            }
            Util.ring(level(), ModParticles.EMBER.get(), p.add(0, 0.5, 0), 96, 2.6, 0.04);
            Util.voidBurst(level(), p.x, p.y + 2, p.z, 16, 160);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 5f, 0.6f);
        }
        // the king's authority: the caster inside the domain
        if (age >= tk(1.0) && age < total() - 1 && age % 10 == 0 && o.distanceToSqr(p.x, p.y, p.z) <= R * R) {
            Util.spellBonus(o, c.selfSpellPercent.get(), 25);
            Util.reduce(o, c.selfReductionPercent.get() / 100.0, 25);
            Util.kbImmune(o, 25);
            o.addEffect(new MobEffectInstance(ModEffects.AUTHORITY.get(), 30, 0, false, false, true));
        }
        if (age >= tk(2.5) && age < tk(10.0) && age % 10 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.slowPct(e, c.slowPercent.get(), 30);
                Util.outDamage(e, c.outPercent.get(), 30);
                Util.healCut(e, c.healCutPercent.get(), 30);
                Util.vulnerable(e, c.vulnPercent.get(), 30);
                e.addEffect(new MobEffectInstance(ModEffects.DOMAIN.get(), 40, 0, false, false, true));
            }
        }
        // world suppression: ordinary mobs cannot leave the domain
        if (age >= tk(2.5) && age < tk(10.0) && age % 4 == 0) {
            for (LivingEntity e : Util.enemies(level(), p, R * 1.25 + 4, o)) {
                if (e instanceof Mob m && !Util.isBoss(m)) {
                    double dx = m.getX() - p.x, dz = m.getZ() - p.z;
                    if (dx * dx + dz * dz > (R - 1) * (R - 1)) {
                        Vec3 back = new Vec3(-dx, 0, -dz).normalize().scale(0.6);
                        m.setDeltaMovement(back.x, m.getDeltaMovement().y, back.z);
                        m.hurtMarked = true;
                        m.getNavigation().stop();
                    }
                }
            }
        }
        int s0 = tk(5.0), s1 = tk(8.0), n = c.spearCount.get(), step = Math.max(1, (s1 - s0) / Math.max(1, n));
        if (n > 0 && age >= s0 && age < s1 && (age - s0) % step == 0 && (age - s0) / step < n) {
            List<LivingEntity> foes = foes(o, R);
            Vec3 q;
            LivingEntity target = null;
            if (!foes.isEmpty()) {
                target = foes.get(rand.nextInt(foes.size()));
                q = target.position();
            } else {
                q = spot(rand, R * 0.8);
            }
            if (target != null) {
                Util.hurt(o, target, c.spearFlat.get(), 0);
            }
            for (LivingEntity e : Util.enemies(level(), q, 4, o)) {
                Util.hurt(o, e, c.spearFlat.get() * 0.5, 0);
            }
            pushFx(1, q.x, q.y, q.z);
            Util.flameBurst(level(), q.x, q.y, q.z, 3, 40);
            Util.sound(level(), q.x, q.y, q.z, ModSounds.SNAP.get(), 3f, 0.7f);
        }
        if (at(age, 8.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BELL.get(), 5f, 0.4f);
        }
        if (in(age, 8.0, 10.0) && age % 5 == 0) {
            Util.fx(level(), ModParticles.SOUL.get(), p.x, p.y + 2, p.z, 12, R * 0.35, 3, R * 0.35, 0.04);
        }
        if (at(age, 10.0)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0);
            }
            Util.voidBurst(level(), p.x, p.y + 3, p.z, 20, 260);
            Util.ring(level(), ModParticles.FLAME.get(), p.add(0, 0.5, 0), 96, 2.8, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.5f);
        }
    }
}
