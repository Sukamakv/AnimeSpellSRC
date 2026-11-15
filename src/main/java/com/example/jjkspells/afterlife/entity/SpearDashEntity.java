package com.example.jjkspells.afterlife.entity;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModEntities;
import com.example.jjkspells.afterlife.registry.ModParticles;
import com.example.jjkspells.afterlife.registry.ModSounds;
import com.example.jjkspells.afterlife.util.Blossom;
import com.example.jjkspells.afterlife.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-only controller of the Blazing Spirit Thrust. The sweep is computed once at cast time (so fast dashes cannot
 * skip anybody), the caster glides along it over {@link #DASH} ticks, hits are applied as the caster passes them and
 * the flame trail erupts progressively from the start to the end afterwards.
 */
public class SpearDashEntity extends Entity {
    private static final int DASH = 5;

    private UUID ownerId;
    private Vec3 start = Vec3.ZERO, end = Vec3.ZERO;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Vec3> pts = new ArrayList<>();
    private int[] eruptAt = new int[0];
    private int delay, span, endAge;
    private final Set<UUID> eruptHit = new HashSet<>();

    private static final class Hit {
        final LivingEntity e;
        final double s;
        final boolean center;
        boolean done;

        Hit(LivingEntity e, double s, boolean center) {
            this.e = e;
            this.s = s;
            this.center = center;
        }
    }

    public SpearDashEntity(EntityType<? extends SpearDashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void launch(Level level, LivingEntity caster) {
        var c = AfterlifeConfig.THRUST;
        Vec3 start = caster.position();
        Vec3 dir = caster.getLookAngle().normalize();
        AABB box = caster.getBoundingBox();
        double reach = 0;
        for (double s = 0.25; s <= c.range.get(); s += 0.25) {
            if (!level.noCollision(caster, box.move(dir.scale(s)))) {
                break;
            }
            reach = s;
        }
        SpearDashEntity e = new SpearDashEntity(ModEntities.DASH.get(), level);
        e.ownerId = caster.getUUID();
        e.start = start;
        e.end = start.add(dir.scale(reach));
        e.setPos(start.x, start.y, start.z);
        e.plan(caster);
        level.addFreshEntity(e);
    }

    private void plan(LivingEntity caster) {
        Vec3 a = start.add(0, 0.9, 0), b = end.add(0, 0.9, 0);
        double len = a.distanceTo(b);
        if (len > 0.05) {
            AABB sweep = new AABB(a, b).inflate(3.0);
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, sweep, x -> Util.isEnemy(caster, x))) {
                AABB bb = t.getBoundingBox().inflate(0.9);
                double s;
                if (bb.contains(a)) {
                    s = 0;
                } else {
                    Optional<Vec3> in = bb.clip(a, b);
                    if (in.isEmpty()) {
                        continue;
                    }
                    s = a.distanceTo(in.get()) / len;
                }
                boolean center = t.getBoundingBox().inflate(0.3).clip(a, b).isPresent() || t.getBoundingBox().inflate(0.3).contains(a);
                hits.add(new Hit(t, s, center));
            }
            int n = Math.max(2, (int) Math.ceil(len / 1.5) + 1);
            eruptAt = new int[n];
            delay = (int) Math.round(AfterlifeConfig.THRUST.eruptDelaySeconds.get() * 20);
            span = 10;
            for (int i = 0; i < n; i++) {
                pts.add(start.lerp(end, i / (double) (n - 1)));
                eruptAt[i] = delay + (int) Math.floor(i * span / (double) n);
            }
        }
        endAge = Math.max(DASH, delay + span) + 6;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        Entity oe = sl.getEntity(ownerId);
        if (!(oe instanceof LivingEntity owner) || !owner.isAlive()) {
            discard();
            return;
        }
        int age = tickCount - 1;
        double len = start.distanceTo(end);
        if (age == 0) {
            Util.sound(level(), start.x, start.y, start.z, ModSounds.DASH.get(), 1.8f, 1.0f);
            Util.FALL_IMMUNE.put(owner.getUUID(), level().getGameTime() + DASH + 2 + Math.round(AfterlifeConfig.THRUST.fallImmunitySeconds.get() * 20));
            if (len > 0.5) {
                owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DASH + 1, 0, false, false, false));
                // butterflies left hanging along the route, spirit copies of the caster at three points
                for (int i = 0; i <= (int) len * 2; i++) {
                    Vec3 p = start.lerp(end, i / Math.max(1.0, len * 2));
                    Util.fx(level(), i % 2 == 0 ? ModParticles.BUTTERFLY_RED.get() : ModParticles.BUTTERFLY_ORANGE.get(), p.x, p.y + 1.0, p.z, 1, 0.5, 0.5, 0.5, 0.01);
                }
                for (double f : new double[]{0.25, 0.55, 0.85}) {
                    Vec3 p = start.lerp(end, f);
                    Util.fxDir(level(), ModParticles.AFTERIMAGE.get(), p.x, p.y + 1.0, p.z, 0, 0, 0);
                }
            }
        }
        if (age >= 1 && age <= DASH && len > 0.5) {
            double p = age / (double) DASH;
            Vec3 prev = start.lerp(end, (age - 1) / (double) DASH);
            Vec3 now = start.lerp(end, p);
            Util.snapTo(owner, now, owner.getYRot(), owner.getXRot());
            for (int k = 0; k < 4; k++) {
                Vec3 q = prev.lerp(now, k / 4.0);
                Util.fxDir(level(), ModParticles.SPIRIT_FLAME.get(), q.x, q.y + 0.9, q.z, 0, 0.01, 0);
                Util.fx(level(), ModParticles.EMBER.get(), q.x, q.y + 0.9, q.z, 3, 0.3, 0.5, 0.3, 0.04);
            }
            for (Hit h : hits) {
                if (!h.done && h.s <= p + 1.0e-6) {
                    h.done = true;
                    applyHit(owner, h);
                }
            }
        }
        if (age == DASH && len > 0.5) {
            Util.fx(level(), ModParticles.FLASH.get(), end.x, end.y + 1.0, end.z, 1, 0, 0, 0, 0);
            Util.fx(level(), ModParticles.SLASH.get(), end.x, end.y + 1.0, end.z, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < eruptAt.length; i++) {
            if (eruptAt[i] == age) {
                erupt(owner, pts.get(i), i);
            }
        }
        if (age >= endAge) {
            discard();
        }
    }

    private void applyHit(LivingEntity owner, Hit h) {
        LivingEntity t = h.e;
        if (!t.isAlive()) {
            return;
        }
        var c = AfterlifeConfig.THRUST;
        boolean marked = Blossom.consume(t);
        Util.hurt(owner, t, c.flat.get() + (h.center ? c.centerBonus.get() : 0.0), c.percent.get(), 1.0);
        if (marked) {
            Blossom.detonate(owner, t, c.detFlat.get(), c.detPercent.get(), c.detRadius.get());
        }
        if (t.isAlive()) {
            Blossom.apply(owner, t, Blossom.ticksOf(c.blossomSeconds.get()));
        }
        Vec3 p = t.getBoundingBox().getCenter();
        Util.fx(level(), ModParticles.FLASH.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.SLASH.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.BUTTERFLY_RED.get(), p.x, p.y, p.z, 6, 0.3, 0.4, 0.3, 0.12);
        Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y, p.z, 14, 0.3, 0.4, 0.3, 0.14);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.STRIKE.get(), 1.0f, 0.9f + level().random.nextFloat() * 0.3f);
    }

    private void erupt(LivingEntity owner, Vec3 p, int index) {
        var c = AfterlifeConfig.THRUST;
        for (int k = 0; k < 6; k++) {
            Util.fxDir(level(), ModParticles.SPIRIT_FLAME.get(), p.x + (random.nextDouble() - 0.5) * 0.5, p.y + k * 0.35, p.z + (random.nextDouble() - 0.5) * 0.5, 0, 0.08, 0);
        }
        Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 0.5, p.z, 10, 0.4, 0.6, 0.4, 0.1);
        Util.fx(level(), ModParticles.BUTTERFLY_ORANGE.get(), p.x, p.y + 1.0, p.z, 2, 0.4, 0.5, 0.4, 0.1);
        if (index % 3 == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.GHOST_BOOM.get(), 0.5f, 1.6f);
        }
        for (LivingEntity e : Util.enemies(level(), p, c.eruptRadius.get(), owner)) {
            if (eruptHit.add(e.getUUID())) {
                Util.hurt(owner, e, c.eruptFlat.get(), c.eruptPercent.get(), 1.0);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
