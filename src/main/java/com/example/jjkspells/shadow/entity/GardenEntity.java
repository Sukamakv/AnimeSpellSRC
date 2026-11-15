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
 * Shadow's Garden - The Silent Execution. The hits only store damage; everything lands when the sword is sheathed.
 * fx 1 = slash {x, y, z, index, fromX, fromY, fromZ, entityId}, 2 = afterimage {x, y, z, yaw}, 3 = the final line {x, y, z, yaw},
 * 4 = the sheathing {x, y, z, yaw}, 5 = the execution {x, y, z}.
 */
public class GardenEntity extends SeqEntity {
    private final List<UUID> targets = new ArrayList<>();
    private final Map<UUID, Double> stored = new LinkedHashMap<>();
    private UUID primary;
    private int slashed;
    private Vec3 last;
    private boolean sheathed;

    public GardenEntity(EntityType<? extends GardenEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 130;
    }

    public static GardenEntity create(Level level, LivingEntity caster) {
        GardenEntity e = new GardenEntity(ModEntities.GARDEN.get(), level);
        e.begin(caster, caster.position(), ShdConfig.GARDEN.range.get(), 0, caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(5.5);
    }

    private LivingEntity find(UUID id) {
        if (id != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    private void appearBehind(LivingEntity o, LivingEntity t) {
        Vec3 look = flat(t.getLookAngle());
        Vec3 want = t.position().subtract(look.scale(1.3));
        Vec3 safe = Util.safeNear(level(), o, want, 2.5);
        Util.snapTo(o, safe == null ? want : safe, Util.yawTo(want, t.position()), 0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = ShdConfig.GARDEN;
        if (age == 0) {
            LivingEntity p = pickTarget(o, c.range.get());
            List<LivingEntity> others = new ArrayList<>(Util.enemies(level(), o.position(), c.range.get(), o));
            if (p == null && !others.isEmpty()) {
                others.sort(Comparator.comparingDouble(x -> x.distanceToSqr(o)));
                p = others.get(0);
            }
            if (p == null) {
                Util.refundFraction(o, ModSpells.GARDEN.get(), 1.0);
                finish(true);
                discard();
                return;
            }
            primary = p.getUUID();
            others.remove(p);
            others.sort(Comparator.comparingDouble(x -> x.distanceToSqr(o)));
            for (LivingEntity x : others) {
                if (targets.size() >= c.maxTargets.get() - 1) {
                    break;
                }
                targets.add(x.getUUID());
            }
            targets.add(primary);
            last = o.position();
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.DISAPPEAR.get(), 1.2f, 1.0f);
        }
        if (age < tk(4.6)) {
            Util.UNTOUCHABLE.add(ownerId);
            Util.LOCKED.add(ownerId);
        }
        if (in(age, 0.0, 0.6) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SLIME.get(), o.getX(), o.getY() + 0.2, o.getZ(), 4, 0.5, 0.1, 0.5, 0.01);
        }
        if (at(age, 0.6)) {
            o.setInvisible(true);
            pushFx(2, o.getX(), o.getY(), o.getZ(), o.getYRot());
            Util.fx(level(), ModParticles.SLIME.get(), o.getX(), o.getY() + 1, o.getZ(), 10, 0.5, 0.8, 0.5, 0.02);
        }
        int n = targets.size();
        // phase 2: the strikes
        if (n > 0 && slashed < n && age >= tk(0.6 + slashed * 1.9 / n) && age < tk(2.6)) {
            slash(o, c, slashed);
            slashed++;
        }
        if (at(age, 2.5)) {
            LivingEntity p = find(primary);
            if (p != null) {
                appearBehind(o, p);
                stored.merge(primary, c.finalFlat.get() + c.finalPercent.get() / 100.0 * p.getMaxHealth(), Double::sum);
                pushFx(3, p.getX(), p.getY() + p.getBbHeight() * 0.55, p.getZ(), p.getYRot());
            }
            o.setInvisible(false);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.SWORD_DRAW.get(), 1.0f, 0.9f);
        }
        if (at(age, 3.0)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.SWORD_LOWER.get(), 0.8f, 1.0f);
        }
        if (at(age, 3.8)) {
            Util.snapTo(o, o.position(), o.getYRot() + 180f, 0);
            pushFx(4, o.getX(), o.getY(), o.getZ(), o.getYRot());
        }
        if (at(age, 4.5) && !sheathed) {
            sheathed = true;
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.SHEATH_CLICK.get(), 5.0f, 1.0f);
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.DELAYED_EXECUTION.get(), 4.0f, 1.0f);
            for (Map.Entry<UUID, Double> en : stored.entrySet()) {
                LivingEntity t = find(en.getKey());
                if (t == null) {
                    continue;
                }
                Vec3 at = t.position().add(0, t.getBbHeight() * 0.55, 0);
                Util.hurt(o, t, en.getValue(), 0);
                track(t);
                t.removeEffect(ModEffects.MARK.get());
                if (t.isAlive()) {
                    Util.knock(t, o.position(), c.knockback.get(), 0.3);
                }
                pushFx(5, at.x, at.y, at.z);
                Util.sparkBurst(level(), at.x, at.y, at.z, 2.5, 40);
            }
        }
    }

    private void slash(LivingEntity o, ShdConfig.Garden c, int i) {
        LivingEntity t = find(targets.get(i));
        if (t == null) {
            return;
        }
        boolean prim = targets.get(i).equals(primary);
        Vec3 from = last == null ? o.position() : last;
        pushFx(2, from.x, from.y, from.z, o.getYRot());
        appearBehind(o, t);
        Util.swing(o);
        last = o.position();
        stored.merge(targets.get(i), prim ? c.initialDamage.get() : c.secondaryDamage.get(), Double::sum);
        t.addEffect(new MobEffectInstance(ModEffects.MARK.get(), 140, 0, false, false, false));
        pushFx(1, t.getX(), t.getY() + t.getBbHeight() * 0.55, t.getZ(), i, from.x, from.y, from.z, t.getId());
        Util.sound(level(), t.getX(), t.getY(), t.getZ(), ModSounds.SWORD_SLICE.get(), 1.0f, 0.9f + 0.15f * (i % 3));
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
            LivingEntity o = ownerAny();
            if (o != null) {
                o.setInvisible(false);
            }
        }
        if (!sheathed) {
            for (UUID id : stored.keySet()) {
                LivingEntity t = find(id);
                if (t != null) {
                    t.removeEffect(ModEffects.MARK.get());
                }
            }
        }
    }
}
