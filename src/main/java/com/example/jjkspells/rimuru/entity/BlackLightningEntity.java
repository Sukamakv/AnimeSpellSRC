package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.registry.ModEntities;
import com.example.jjkspells.rimuru.registry.ModParticles;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.util.Util;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Black Lightning - Storm of Destruction. One clock (age = tickCount - 1):
 * <pre>
 *  0..COMPRESS  a black sphere gathers, then compresses
 *  RELEASE      hitscan strike on the primary target (paralysis)
 *  CHAIN0..     the lightning jumps from enemy to enemy every 2 ticks
 *  BLAST        detonation around the primary point
 * </pre>
 * Visual events (fx list): type 1 = primary beam {from, to}, 2 = chain arc {from, to}, 3 = blast {pos, radius}.
 */
public class BlackLightningEntity extends RimEntityBase {
    public static final int COMPRESS = 12, RELEASE = 22, CHAIN0 = 28, BLAST = 44, END = 50;

    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(BlackLightningEntity.class, EntityDataSerializers.INT);

    private final Set<UUID> struck = new HashSet<>();
    private final List<LivingEntity> chain = new ArrayList<>();
    private Vec3 point;
    private double chainMult = 1.0;

    public BlackLightningEntity(EntityType<? extends BlackLightningEntity> type, Level level) {
        super(type, level);
    }

    public static BlackLightningEntity create(Level level, LivingEntity caster, LivingEntity target) {
        BlackLightningEntity e = new BlackLightningEntity(ModEntities.LIGHTNING.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_TARGET, target == null ? -1 : target.getId());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TARGET, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.rimuru.client.ClientFx.tickLightning(this));
            return;
        }
        var c = RimConfig.LIGHTNING;
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (age < END && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        if (age < END) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
            freeze(owner);
        }
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.CHARGE.get(), 1.3f, 0.8f);
        }
        if (age < RELEASE && age % 3 == 0) {
            Vec3 h = owner.getEyePosition().add(owner.getLookAngle().scale(0.7)).add(0, -0.3, 0);
            Util.fx(level(), ModParticles.SPARK.get(), h.x, h.y, h.z, 2, 0.3, 0.3, 0.3, 0.05);
        }
        if (age == RELEASE) {
            release(owner, c);
        }
        if (age >= CHAIN0 && age < BLAST && (age - CHAIN0) % 2 == 0) {
            int k = (age - CHAIN0) / 2;
            if (k < chain.size()) {
                LivingEntity t = chain.get(k);
                Vec3 from = k == 0 ? point : chain.get(k - 1).position().add(0, chain.get(k - 1).getBbHeight() * 0.5, 0);
                if (t.isAlive()) {
                    Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
                    Util.hurt(owner, t, c.chainFlat.get(), 0, chainMult, 1.0);
                    pushFx(2, from.x, from.y, from.z, to.x, to.y, to.z);
                    Util.sparkBurst(level(), to.x, to.y, to.z, 1.0, 8);
                    Util.sound(level(), to.x, to.y, to.z, ModSounds.CRACKLE.get(), 0.9f, 1.0f + k * 0.04f);
                }
            }
        }
        if (age == BLAST) {
            blast(owner, c);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private void release(LivingEntity owner, RimConfig.Lightning c) {
        double range = c.range.get();
        LivingEntity primary = byId(entityData.get(DATA_TARGET));
        if (primary == null || primary.distanceTo(owner) > range * 1.15) {
            primary = null;
            HitResult hr = Utils.raycastForEntity(level(), owner, (float) range, true, 0.3f);
            if (hr instanceof EntityHitResult er && er.getEntity() instanceof LivingEntity le && Util.isEnemy(owner, le)) {
                primary = le;
            }
        }
        Vec3 src = owner.getEyePosition().add(owner.getLookAngle().scale(0.7)).add(0, -0.3, 0);
        if (primary != null) {
            point = primary.position().add(0, primary.getBbHeight() * 0.5, 0);
        } else {
            point = Util.aimPoint(level(), owner, range);
        }
        pushFx(1, src.x, src.y, src.z, point.x, point.y, point.z);
        Util.sound(level(), src.x, src.y, src.z, ModSounds.LASER.get(), 1.6f, 0.9f);
        Util.sound(level(), point.x, point.y, point.z, ModSounds.THUNDER.get(), 1.8f, 0.8f);
        Util.sparkBurst(level(), point.x, point.y, point.z, 2.0, 24);
        if (primary == null) {
            return;
        }
        struck.add(primary.getUUID());
        Util.hurt(owner, primary, c.primaryFlat.get(), c.primaryPercent.get());
        if (primary.isAlive()) {
            int t = (int) Math.round(c.paralysisSeconds.get() * 20);
            Util.slowPct(primary, c.paralysisSlowPercent.get(), t);
            Util.atkSlow(primary, c.paralysisAtkPercent.get(), t);
        }
        boolean water = primary.isInWater();
        double r = water ? c.waterChainRadius.get() : c.chainRadius.get();
        int max = water ? c.waterChainMax.get() : c.chainMax.get();
        chainMult = water ? 1.0 + c.waterBonusPercent.get() / 100.0 : 1.0;
        Vec3 cur = point;
        for (int i = 0; i < max; i++) {
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : Util.enemies(level(), cur, r, owner)) {
                if (struck.contains(e.getUUID())) {
                    continue;
                }
                double d = e.distanceToSqr(cur);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (best == null) {
                break;
            }
            struck.add(best.getUUID());
            chain.add(best);
            cur = best.position().add(0, best.getBbHeight() * 0.5, 0);
        }
    }

    private void blast(LivingEntity owner, RimConfig.Lightning c) {
        if (point == null) {
            return;
        }
        boolean overload = struck.size() >= c.overloadCount.get();
        double mult = overload ? 1.0 + c.overloadBonusPercent.get() / 100.0 : 1.0;
        double radius = c.blastRadius.get();
        for (LivingEntity e : Util.enemies(level(), point, radius, owner)) {
            Util.hurt(owner, e, c.blastFlat.get(), c.blastPercent.get(), mult, 1.0);
            Util.knock(e, point, 2.5, 0.25);
        }
        pushFx(3, point.x, point.y, point.z, radius);
        Util.sparkBurst(level(), point.x, point.y, point.z, radius * 0.6, 60);
        Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 1.8f, 0.9f);
        if (overload && owner.isAlive()) {
            Util.hastePct(owner, c.overloadSpeedPercent.get(), (int) Math.round(c.overloadSpeedSeconds.get() * 20));
        }
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        releaseCommon(normal);
    }
}
