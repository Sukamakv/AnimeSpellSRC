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

/** Storm Dragon's Ascension - the Calamity Incarnate. The entity follows the caster. fx type 1 = the final cataclysm {x, y, z}. */
public class AscendEntity extends SeqEntity {
    private int endAt, kills;
    private boolean extended, active, done;

    public AscendEntity(EntityType<? extends AscendEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 60;
    }

    public static AscendEntity create(Level level, LivingEntity caster) {
        var c = VelConfig.ASCEND;
        AscendEntity e = new AscendEntity(ModEntities.ASCEND.get(), level);
        e.begin(caster, caster.position(), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        e.endAt = tk(6.0) + tk(c.durationSeconds.get());
        return e;
    }

    @Override
    public int total() {
        return endAt + tk(2.0);
    }

    private void apply(LivingEntity o, int ticks, double damagePercent) {
        var c = VelConfig.ASCEND;
        Util.spellBonus(o, damagePercent, ticks);
        Util.MELEE_BONUS.put(ownerId, new double[]{damagePercent / 100.0, o.level().getGameTime() + ticks});
        Util.hastePct(o, c.speedPercent.get(), ticks);
        Util.reduce(o, c.reductionPercent.get() / 100.0, ticks);
        Util.kbImmune(o, ticks);
        Util.FALL_IMMUNE.merge(ownerId, o.level().getGameTime() + ticks, Math::max);
        double[] old = Util.ASC.get(ownerId);
        double cnt = old == null ? 0 : old[2], kl = old == null ? 0 : old[8];
        Util.ASC.put(ownerId, new double[]{o.level().getGameTime() + ticks, c.meleeFlat.get(), cnt, c.shockEvery.get(), c.shockRadius.get(), c.shockFlat.get(), c.shockPercent.get(), c.projectileReducePercent.get() / 100.0, kl});
        o.addEffect(new MobEffectInstance(ModEffects.ASCEND.get(), ticks, 0, false, false, true));
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VelConfig.ASCEND;
        setPos(o.getX(), o.getY(), o.getZ());
        Vec3 p = position();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 5f, 0.4f);
        }
        if (age < tk(6.0)) {
            if (age % 2 == 0) {
                guard(o, c.reductionPercent.get());
            }
            Util.LOCKED.add(ownerId);
            freeze(o);
        }
        if (in(age, 0, 2.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 0.3, p.z, 8, 1.2, 0.3, 1.2, 0.15);
        }
        if (at(age, 1.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THUNDER.get(), 6f, 0.5f);
        }
        if (at(age, 3.0)) {
            for (LivingEntity e : foes(o, c.radius.get())) {
                Util.hurt(o, e, c.firstFlat.get(), c.firstPercent.get());
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 2, p.z, 14, 220);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.5, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 7f, 0.6f);
        }
        if (in(age, 4.0, 5.5)) {
            o.setDeltaMovement(0, 0.08, 0);
            o.hurtMarked = true;
        }
        if (at(age, 6.0)) {
            Util.LOCKED.remove(ownerId);
            active = true;
            apply(o, endAt - age, c.damageBonusPercent.get());
            Util.sparkBurst(level(), p.x, p.y + 1, p.z, 10, 120);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THUNDER.get(), 6f, 0.7f);
        }
        if (active && age > tk(6.0) && age < endAt) {
            if (age % 4 == 0) {
                guard(o, c.reductionPercent.get());
                o.fallDistance = 0;
            }
            if (age % 10 == 0) {
                Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 1, o.getZ(), 6, 0.6, 0.9, 0.6, 0.1);
            }
            double[] as = Util.ASC.get(ownerId);
            if (!extended && as != null && as[8] >= c.overdriveKills.get()) {
                extended = true;
                endAt += tk(c.overdriveSeconds.get());
                apply(o, endAt - age, c.damageBonusPercent.get() + c.overdriveDamagePercent.get());
                Util.flameBurst(level(), o.getX(), o.getY(), o.getZ(), 6, 80);
                Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.THUNDER.get(), 5f, 1.0f);
            }
        }
        if (age == endAt && !done) {
            done = true;
            Util.ASC.remove(ownerId);
            o.removeEffect(ModEffects.ASCEND.get());
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get());
                track(e);
            }
            pushFx(1, p.x, p.y, p.z);
            Util.sparkBurst(level(), p.x, p.y + 3, p.z, 24, 340);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 144, 3.8, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.9, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 8f, 0.5f);
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
            if (!normal) {
                Util.ASC.remove(ownerId);
            }
        }
    }
}
