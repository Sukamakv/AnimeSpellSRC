package com.example.jjkspells.velgrynd.entity;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.registry.*;
import com.example.jjkspells.velgrynd.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Dimensional Scorch - the Flame That Hunts Eternity. fx type 1 = a strike {x, y, z, portal, k}, type 3 = the current position of the target {x, y, z}. */
public class PursuitEntity extends SeqEntity {
    private LivingEntity mark;
    private Vec3 tpos;
    private int done;
    private int hits;

    public PursuitEntity(EntityType<? extends PursuitEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 90;
    }

    public static PursuitEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.PURSUIT;
        PursuitEntity e = new PursuitEntity(ModEntities.PURSUIT.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(10.0);
    }

    private double markMult(LivingEntity e) {
        return e == mark ? 1.0 + VgrConfig.PURSUIT.markBonusPercent.get() / 100.0 : 1.0;
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.PURSUIT;
        Vec3 p = position();
        if (age == 0) {
            tpos = p;
            double best = 144;
            for (LivingEntity e : Util.enemies(level(), p, 12, o)) {
                double d = e.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    mark = e;
                }
            }
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 4f, 0.6f);
        }
        if (mark != null && mark.isAlive()) {
            tpos = mark.position();
        }
        if (age % 4 == 0 && age < tk(9.0)) {
            pushFx(3, tpos.x, tpos.y, tpos.z);
        }
        if (in(age, 0.0, 1.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.RUNE.get(), tpos.x, tpos.y + 3, tpos.z, 6, 0.5, 0.5, 0.5, 0.03);
        }
        if (at(age, 1.0)) {
            if (mark != null && mark.isAlive()) {
                Util.hurt(o, mark, c.brandFlat.get(), c.brandPercent.get());
                track(mark);
                mark.addEffect(new MobEffectInstance(ModEffects.BRAND.get(), tk(9.0), 0, false, false, true));
            }
            Util.sparkBurst(level(), tpos.x, tpos.y + 2, tpos.z, 6, 100);
            Util.sound(level(), tpos.x, tpos.y, tpos.z, ModSounds.TEAR.get(), 5f, 0.8f);
        }
        if (in(age, 1.0, 2.5) && age % 5 == 0) {
            Util.fx(level(), ModParticles.CRACK.get(), tpos.x, tpos.y + 3, tpos.z, 4, c.radius.get() * 0.4, 3, c.radius.get() * 0.4, 0);
        }
        int n = c.strikeCount.get(), t0 = tk(2.5), span = tk(3.5);
        while (done < n) {
            double u = n <= 1 ? 0.0 : done / (double) (n - 1);
            int when = t0 + (int) Math.round(span * (1.0 - Math.pow(1.0 - u, 1.7)));
            if (age < when) {
                break;
            }
            strike(o, c, done);
            done++;
        }
        if (in(age, 6.0, 8.0) && age % 4 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), tpos.x, tpos.y + 4, tpos.z, 14, 3, 3, 3, 0.4);
        }
        if (at(age, 7.0)) {
            Util.sound(level(), tpos.x, tpos.y, tpos.z, ModSounds.CHARGE.get(), 7f, 0.6f);
        }
        if (at(age, 8.0)) {
            for (LivingEntity e : Util.enemies(level(), tpos, c.finalRadius.get(), o)) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), markMult(e), 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                track(e);
            }
            pushFx(1, tpos.x, tpos.y, tpos.z, -1, n);
            Util.sparkBurst(level(), tpos.x, tpos.y + 3, tpos.z, 30, 420);
            Util.ring(level(), ModParticles.SPARK.get(), tpos.add(0, 0.7, 0), 160, 3.6, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), tpos.add(0, 0.5, 0), 120, 2.8, 0.05);
            Util.sound(level(), tpos.x, tpos.y, tpos.z, ModSounds.COLLAPSE.get(), 10f, 0.5f);
        }
    }

    private void strike(LivingEntity o, VgrConfig.Pursuit c, int k) {
        double mult = 1.0 + Math.min(c.accelMaxPercent.get(), hits * c.accelPercent.get()) / 100.0;
        boolean any = false;
        for (LivingEntity e : Util.enemies(level(), tpos, c.strikeRadius.get(), o)) {
            Util.hurt(o, e, c.strikeFlat.get(), 0, mult * markMult(e), 1.0);
            track(e);
            any = true;
        }
        if (any) {
            hits++;
        }
        pushFx(1, tpos.x, tpos.y, tpos.z, k % 8, k);
        Util.sparkBurst(level(), tpos.x, tpos.y + 1.5, tpos.z, 5, 80);
        Util.sound(level(), tpos.x, tpos.y, tpos.z, ModSounds.BOOM.get(), 4f, 0.9f + k * 0.04f);
    }
}
