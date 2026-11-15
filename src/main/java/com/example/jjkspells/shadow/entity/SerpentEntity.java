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
 * Black Serpent's Requiem. The route is {@link SerpentPath}. fx 1 = start {targetX, targetY, targetZ, poolX, poolY, poolZ},
 * 2 = the spear lands {x, y, z}, 3 = a tendril strike {x, y, z, entityId, headX, headY, headZ}, 4 = the coil closes {x, y, z, entityId},
 * 5 = the constriction crushes {x, y, z}.
 */
public class SerpentEntity extends SeqEntity {
    /** Built by the renderer on the client from the start event. */
    public SerpentPath clientPath;
    private SerpentPath path;
    private Vec3 target;
    private UUID bound;
    private int strikes, strikeIdx;
    private boolean pierced, crushed;

    public SerpentEntity(EntityType<? extends SerpentEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 220;
    }

    public static SerpentEntity create(Level level, LivingEntity caster) {
        SerpentEntity e = new SerpentEntity(ModEntities.SERPENT.get(), level);
        Vec3 t = groundPoint(level, caster, ShdConfig.SERPENT.range.get());
        e.target = t;
        e.begin(caster, caster.position(), ShdConfig.SERPENT.range.get(), ShdConfig.SERPENT.radius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(8.0);
    }

    private LivingEntity find(UUID id) {
        if (id != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    /** Distance of a point from the segment a-b. */
    private static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double u = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / l2));
        return p.distanceTo(a.add(ab.scale(u)));
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = ShdConfig.SERPENT;
        double R = c.radius.get();
        Vec3 p = position();
        if (path == null) {
            Vec3 dir = flat(target.subtract(p));
            Vec3 pool = p.add(dir.scale(2.5));
            Double g = Util.findGround(level(), pool.x, pool.y + 3, pool.z);
            pool = new Vec3(pool.x, g == null ? p.y : g, pool.z);
            path = new SerpentPath(pool.x, pool.y, pool.z, target.x, target.y, target.z, R);
            pushFx(1, target.x, target.y, target.z, pool.x, pool.y, pool.z);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SLIME_MORPH.get(), 2.5f, 1.0f);
        }
        double t = age / 20.0;
        if (age < tk(4.0)) {
            Util.LOCKED.add(ownerId);
        }
        if (age < tk(7.0) && age % 2 == 0) {
            guard(o, 50);
        }
        if (age == tk(4.0)) {
            Util.LOCKED.remove(ownerId);
        }
        if (in(age, 0.0, 1.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SLIME.get(), path.bx, path.by + 0.2, path.bz, 5, 0.9, 0.1, 0.9, 0.01);
        }
        if (at(age, 2.0)) {
            double[] h = path.head(t);
            Util.sound(level(), h[0], h[1], h[2], ModSounds.SLIME_SERPENT_SCREECH.get(), 3.0f, 1.0f);
        }
        if (at(age, 3.0)) {
            Util.sound(level(), target.x, target.y, target.z, ModSounds.SLIME_SPEAR_PIERCE.get(), 3.0f, 1.0f);
        }
        // Piercing Form: everything along the line the spear travels
        if (age == tk(3.3) && !pierced) {
            pierced = true;
            double[] h1 = path.head(SerpentPath.REAR_B);
            double[] d = path.strikePoint();
            Vec3 a = new Vec3(h1[0], h1[1], h1[2]), b = new Vec3(d[0], d[1], d[2]);
            Vec3 ext = b.add(b.subtract(a).normalize().scale(2.5));
            double w = c.pierceWidth.get() * 0.5;
            Vec3 mid = a.add(ext).scale(0.5);
            double reach = a.distanceTo(ext) * 0.5 + w + 2;
            for (LivingEntity e : Util.enemies(level(), mid, reach, o)) {
                Vec3 q = e.position().add(0, e.getBbHeight() * 0.5, 0);
                if (segDist(q, a, ext) <= w + e.getBbWidth() * 0.5) {
                    Util.hurt(o, e, c.pierceDamage.get(), 0);
                    track(e);
                    Util.stagger(e, 14);
                    Util.knock(e, a, 2.0, 0.25);
                }
            }
            pushFx(2, d[0], d[1], d[2]);
            Util.sparkBurst(level(), d[0], d[1], d[2], 3, 40);
        }
        // Predator Form: tendrils lash out, one enemy after another
        if (age >= tk(4.1) && age < tk(5.5) && age % 6 == 0 && strikes < 5) {
            List<LivingEntity> in = Util.enemies(level(), target, R, o);
            if (!in.isEmpty()) {
                in.sort(Comparator.comparingDouble(x -> x.distanceToSqr(target)));
                LivingEntity e = in.get(strikeIdx++ % in.size());
                double[] h = path.head(t);
                Util.hurt(o, e, c.strikeDamage.get(), 0);
                track(e);
                Util.knock(e, new Vec3(h[0], h[1], h[2]), 0.8, 0.1);
                pushFx(3, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), e.getId(), h[0], h[1], h[2]);
                Util.sound(level(), e.getX(), e.getY(), e.getZ(), ModSounds.SLIME_TENDRIL_WHIP.get(), 1.6f, 0.9f + 0.1f * strikes);
                strikes++;
            }
        }
        // Constriction: the strongest enemy in the circle is wrapped and crushed
        if (at(age, 5.45)) {
            LivingEntity best = null;
            for (LivingEntity e : Util.enemies(level(), target, R, o)) {
                if (best == null || e.getMaxHealth() > best.getMaxHealth()) {
                    best = e;
                }
            }
            if (best != null) {
                bound = best.getUUID();
                path.setCoil(best.getX(), best.getY(), best.getZ());
                pushFx(4, best.getX(), best.getY(), best.getZ(), best.getId());
                Util.sound(level(), best.getX(), best.getY(), best.getZ(), ModSounds.SLIME_CONSTRICTION.get(), 3.0f, 1.0f);
            }
        }
        if (bound != null && age >= tk(5.45) && age < tk(6.9)) {
            LivingEntity e = find(bound);
            if (e != null) {
                Util.hold(e, 6, c.bindSlowPercent.get());
                Util.slowPct(e, c.bindSlowPercent.get(), 8);
                e.addEffect(new MobEffectInstance(ModEffects.BIND.get(), 30, 0, false, false, true));
            }
        }
        if (age == tk(6.9) && bound != null && !crushed) {
            crushed = true;
            LivingEntity e = find(bound);
            if (e != null) {
                Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get());
                track(e);
                pushFx(5, at.x, at.y, at.z);
                Util.sparkBurst(level(), at.x, at.y, at.z, 2.5, 40);
            }
        }
        if (at(age, 7.0)) {
            double[] h = path.head(7.0);
            Util.sound(level(), h[0], h[1], h[2], ModSounds.SLIME_REFORMATION.get(), 2.5f, 1.0f);
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
