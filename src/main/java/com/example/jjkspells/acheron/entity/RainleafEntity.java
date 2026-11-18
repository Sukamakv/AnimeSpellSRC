package com.example.jjkspells.acheron.entity;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.registry.*;
import com.example.jjkspells.acheron.util.Nihility;
import com.example.jjkspells.acheron.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.vfx.sprite.SpriteFx;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Atop Rainleaf Hangs Oneness: every enemy in the area is tied to the caster by a red thread and slowly gathers Crimson Knots, which
 * hurt it every second. A marked enemy that dies passes its knots on to the nearest enemy.
 * fx 1 = start {cx,cy,cz, yaw}, 2 = marked {entityId}, 3 = transfer {ax,ay,az, bx,by,bz}, 4 = full knots {entityId}.
 */
public class RainleafEntity extends SeqEntity {
    private final Map<UUID, Long> marked = new LinkedHashMap<>();
    private boolean full;

    public RainleafEntity(EntityType<? extends RainleafEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 280;
    }

    public static RainleafEntity create(Level level, LivingEntity caster) {
        var c = AcheronConfig.RAIN;
        Vec3 p = aimCenter(level, caster, c.range.get(), c.markRadius.get());
        RainleafEntity e = new RainleafEntity(ModEntities.RAINLEAF.get(), level);
        e.dreamAmt = c.dreams.get();
        e.begin(caster, p, c.markRadius.get(), 0, Util.yawTo(caster.position(), p));
        return e;
    }

    @Override
    public int total() {
        return tk(AcheronConfig.RAIN.durationSeconds.get()) + 10;
    }

    private void mark(LivingEntity o, LivingEntity e, int knots) {
        if (marked.size() >= 24 && !marked.containsKey(e.getUUID())) {
            return;
        }
        if (marked.putIfAbsent(e.getUUID(), (long) tickCount) == null) {
            Nihility.MARKED.put(e.getUUID(), getUUID());
            landed++;
            pushFx(2, e.getId());
        }
        Nihility.addKnots(e, knots, AcheronConfig.RAIN.maxKnots.get());
    }

    /** Called when a marked enemy dies: its knots go to the nearest other enemy. */
    public void transfer(LivingEntity dead) {
        LivingEntity o = owner();
        if (o == null || finished) {
            return;
        }
        int k = Nihility.knots(dead);
        if (k <= 0) {
            return;
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Util.enemies(level(), dead.position(), r1(), o)) {
            if (e != dead && e.isAlive()) {
                double d = e.distanceToSqr(dead);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        if (best == null) {
            return;
        }
        Vec3 a = Nihility.chest(dead), b = Nihility.chest(best);
        mark(o, best, k);
        pushFx(3, a.x, a.y, a.z, b.x, b.y, b.z);
        Util.arc(level(), ModParticles.ARC_R.get(), a, b, 0.5);
        SpriteFx.beam(sv(), a, b, SpriteFx.Palette.CRIMSON, 0.3f, 8);
        SpriteFx.tendrils(sv(), b, SpriteFx.Palette.CRIMSON, 1.4f);
        Util.sound(level(), b.x, b.y, b.z, ModSounds.RAINLEAF_TRANSFER.get(), 1.2f, 1.0f);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = AcheronConfig.RAIN;
        Vec3 p = position();
        if (age < tk(1.0)) {
            Util.LOCKED.add(ownerId);
            freeze(o);
        } else {
            Util.LOCKED.remove(ownerId);
        }
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.RAINLEAF_CAST.get(), 1.4f, 1.0f);
            pushFx(1, p.x, p.y, p.z, yaw());
            Util.swing(o);
            SpriteFx.petals(sv(), p.add(0, 2.5, 0), SpriteFx.Palette.CRIMSON, Math.min(6f, r1() / 2f));
            SpriteFx.cracks(sv(), p, SpriteFx.Palette.CRIMSON, Math.min(6f, r1() * 2f / 7f));
        }
        if (at(age, 1.5)) {
            award(o, c.dreams.get());
        }
        int dur = tk(c.durationSeconds.get());
        if (age < dur && (age == tk(0.5) || age % 30 == 0)) {
            for (LivingEntity e : foes(o, c.markRadius.get())) {
                if (!marked.containsKey(e.getUUID())) {
                    mark(o, e, 1);
                }
            }
        }
        if (age >= dur) {
            return;
        }
        int step = Math.max(1, tk(c.knotInterval.get()));
        Iterator<Map.Entry<UUID, Long>> it = marked.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            LivingEntity e = find(en.getKey());
            if (e == null) {
                continue;
            }
            int k = Nihility.knots(e);
            Util.slowPct(e, c.slowPercent.get(), 30);
            if (age > tk(1.0) && age % step == 0 && k < c.maxKnots.get()) {
                Nihility.addKnots(e, 1, c.maxKnots.get());
                k++;
                if (k >= c.maxKnots.get() && !full) {
                    full = true;
                    pushFx(4, e.getId());
                    SpriteFx.fracture(sv(), e.position().add(0, e.getBbHeight() * 0.5, 0), SpriteFx.Palette.CRIMSON, 3.0f);
                    SpriteFx.shards(sv(), e.position().add(0, e.getBbHeight() * 0.5, 0), SpriteFx.Palette.CRIMSON, 1.8f);
                }
            }
            if (age % 20 == 10 && k > 0) {
                Util.sound(level(), e.getX(), e.getY(), e.getZ(), ModSounds.RAINLEAF_TICK.get(), 0.5f, 0.8f + 0.06f * k);
                strike(o, e, c.tickFlat.get() * k, c.tickPercent.get() * k, 1.0, 0, 0);
                Util.fx(level(), ModParticles.PETAL.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2 + k / 2, 0.3, 0.5, 0.3, 0.03);
                if (age % 40 == 10) {
                    SpriteFx.petals(sv(), e.position().add(0, e.getBbHeight() * 0.6, 0), SpriteFx.Palette.CRIMSON, 0.7f + 0.1f * k);
                }
                track(e);
            }
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        for (UUID id : marked.keySet()) {
            Nihility.MARKED.remove(id, getUUID());
        }
        marked.clear();
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
