package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.registry.ModEntities;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Imaginary Collapse - Nihility Break. One clock (age = tickCount - 1), the entity stands at the impact point:
 * <pre>
 *  0..COMPRESS   a void sphere forms at the hand, then compresses
 *  RELEASE       it flies to the point (arrives at IMPACT)
 *  IMPACT        void impact (radius 14), slight pull afterwards
 *  PULL0..BLAST2 gravity pull inside the rupture radius
 *  BLAST2        second explosion
 *  RUPTURE       nihility rupture (radius 22, 26 with the overload)
 * </pre>
 * Visual events (fx list): 1 = projectile {from hand}.
 */
public class CollapseEntity extends RimEntityBase {
    public static final int COMPRESS = 20, RELEASE = 40, IMPACT = 60, PULL0 = 80, BLAST2 = 106, RUPTURE = 124, CASTER_END = 62, END = 140;

    private static final EntityDataAccessor<Boolean> DATA_OVER = SynchedEntityData.defineId(CollapseEntity.class, EntityDataSerializers.BOOLEAN);

    private Vec3 point;
    private final Set<UUID> firstHit = new HashSet<>();
    private boolean overload;

    public CollapseEntity(EntityType<? extends CollapseEntity> type, Level level) {
        super(type, level);
    }

    public static CollapseEntity create(Level level, LivingEntity caster, Vec3 point) {
        CollapseEntity e = new CollapseEntity(ModEntities.COLLAPSE.get(), level);
        e.setOwner(caster);
        e.setPos(point.x, point.y, point.z);
        e.point = point;
        return e;
    }

    public boolean isOverload() {
        return entityData.get(DATA_OVER);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OVER, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.rimuru.client.ClientFx.tickCollapse(this));
            return;
        }
        var c = RimConfig.COLLAPSE;
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (age < END && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        double armor = c.armorIgnorePercent.get() / 100.0, reduce = c.reduceIgnorePercent.get() / 100.0;
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.VOID.get(), 1.4f, 0.9f);
        }
        if (age < CASTER_END && owner != null) {
            freeze(owner);
        }
        if (age == RELEASE) {
            Vec3 h = owner.getEyePosition().add(owner.getLookAngle().scale(0.7)).add(0, -0.3, 0);
            pushFx(1, h.x - point.x, h.y - point.y, h.z - point.z);
            Util.sound(level(), h.x, h.y, h.z, ModSounds.CHARGE.get(), 1.4f, 1.3f);
        }
        if (age == CASTER_END) {
            Util.LOCKED.remove(ownerId);
        }
        if (age == IMPACT) {
            double r = c.radius.get();
            for (LivingEntity e : Util.enemies(level(), point, r, owner)) {
                firstHit.add(e.getUUID());
                Util.hurt(owner, e, c.impactFlat.get(), c.impactPercent.get(), 1.0, 1.0, armor, reduce);
                if (e.isAlive()) {
                    int t = (int) Math.round(c.slowSeconds.get() * 20);
                    Util.slowPct(e, c.slowPercent.get(), t);
                    Util.outDamage(e, c.outPercent.get(), t);
                }
            }
            overload = firstHit.size() >= c.overloadCount.get();
            entityData.set(DATA_OVER, overload);
            if (overload && owner != null && owner.isAlive()) {
                Util.spellBonus(owner, c.overloadSpellPercent.get(), (int) Math.round(c.overloadSpellSeconds.get() * 20));
            }
            Util.voidBurst(level(), point.x, point.y + 0.5, point.z, r * 0.4, 60);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 1.8f, 0.7f);
        }
        if (age > IMPACT && age < PULL0 && owner != null) {
            for (LivingEntity e : Util.enemies(level(), point, c.radius.get(), owner)) {
                Util.pull(e, point, c.pullStrength.get() * 0.4, 1.5);
            }
        }
        double rr = overload ? c.overloadRuptureRadius.get() : c.ruptureRadius.get();
        if (age >= PULL0 && age < BLAST2 && owner != null) {
            for (LivingEntity e : Util.enemies(level(), point, c.ruptureRadius.get(), owner)) {
                Util.pull(e, point, c.pullStrength.get(), 1.5);
            }
            if (age % 6 == 0) {
                Util.sound(level(), point.x, point.y, point.z, ModSounds.COLLAPSE.get(), 0.9f, 0.6f + (age - PULL0) * 0.01f);
            }
        }
        if (age == BLAST2) {
            double mult = overload ? 1.0 + c.overloadBonusPercent.get() / 100.0 : 1.0;
            for (LivingEntity e : Util.enemies(level(), point, c.radius.get(), owner)) {
                Util.hurt(owner, e, c.blastFlat.get(), c.blastPercent.get(), mult, 1.0, armor, reduce);
                Util.knock(e, point, 2.0, 0.3);
            }
            Util.voidBurst(level(), point.x, point.y + 0.5, point.z, c.radius.get() * 0.5, 80);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 2.0f, 0.6f);
        }
        if (age == RUPTURE) {
            for (LivingEntity e : Util.enemies(level(), point, rr, owner)) {
                double mult = Util.isBoss(e) ? 1.0 + c.bossBonusPercent.get() / 100.0 : 1.0;
                Util.hurt(owner, e, c.ruptureFlat.get(), c.rupturePercent.get(), mult, 1.0, armor, reduce);
                Util.knock(e, point, 3.5, 0.3);
            }
            Util.voidBurst(level(), point.x, point.y + 0.5, point.z, rr * 0.5, 100);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.VOID.get(), 2.0f, 0.7f);
        }
        if (age >= END) {
            finish(true);
            discard();
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
