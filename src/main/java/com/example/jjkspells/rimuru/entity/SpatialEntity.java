package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.registry.ModEffects;
import com.example.jjkspells.rimuru.registry.ModEntities;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Spatial Domination - Severance of Dimensions. One clock (age = tickCount - 1):
 * <pre>
 *  0..STEP      the space around the caster distorts
 *  STEP         the caster vanishes (untargetable), at APPEAR reappears behind the target
 *  SLASH        the severance slash travels 18 blocks
 *  CRACK0 +4k   four cracks around the target cut
 *  COLLAPSE     the space contracts, RUPTURE explodes
 * </pre>
 * Visual events (fx list): 1 = step out {pos}, 2 = step in {pos}, 3 = slash {origin, dir, length}, 4 = crack {pos}, 5 = collapse {pos, radius}.
 */
public class SpatialEntity extends RimEntityBase {
    public static final int STEP = 10, APPEAR = 20, SLASH = 22, CRACK0 = 38, COLLAPSE = 60, RUPTURE = 72, END = 80;

    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(SpatialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PERFECT = SynchedEntityData.defineId(SpatialEntity.class, EntityDataSerializers.BOOLEAN);

    private boolean perfect, vanished;
    private Vec3 slashFrom = Vec3.ZERO, slashDir = new Vec3(0, 0, 1), centre = Vec3.ZERO;
    private final Set<UUID> cut = new HashSet<>();

    public SpatialEntity(EntityType<? extends SpatialEntity> type, Level level) {
        super(type, level);
    }

    public static SpatialEntity create(Level level, LivingEntity caster, LivingEntity target) {
        SpatialEntity e = new SpatialEntity(ModEntities.SPATIAL.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_TARGET, target.getId());
        e.centre = target.position();
        return e;
    }

    public boolean isPerfect() {
        return entityData.get(DATA_PERFECT);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TARGET, -1);
        entityData.define(DATA_PERFECT, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.rimuru.client.ClientFx.tickSpatial(this));
            return;
        }
        var c = RimConfig.SPATIAL;
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (age < END && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        if (age < END && !vanished) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.TEAR.get(), 1.4f, 0.9f);
        }
        if (age < END) {
            freeze(owner);
        }
        LivingEntity target = byId(entityData.get(DATA_TARGET));
        if (target != null) {
            centre = target.position();
        }
        if (age == STEP) {
            Vec3 p = owner.position();
            pushFx(1, p.x, p.y, p.z);
            Util.voidBurst(level(), p.x, p.y, p.z, 1.5, 12);
            Util.HIDDEN.add(ownerId);
            Util.UNTOUCHABLE.add(ownerId);
            owner.setInvisible(true);
            vanished = true;
        }
        if (age == APPEAR) {
            teleport(owner, target);
        }
        if (age == SLASH) {
            pushFx(3, slashFrom.x, slashFrom.y, slashFrom.z, slashDir.x, slashDir.y, slashDir.z, c.slashRange.get());
            Util.sound(level(), slashFrom.x, slashFrom.y, slashFrom.z, ModSounds.TEAR.get(), 1.6f, 0.7f);
        }
        if (age >= SLASH && age < SLASH + 8 && !vanished) {
            // the slash sweeps along its line, hurting each enemy once
            double len = c.slashRange.get();
            double reach = len * (age - SLASH + 1) / 8.0;
            for (LivingEntity e : Util.enemies(level(), slashFrom.add(slashDir.scale(reach * 0.5)), reach * 0.5 + 3.5, owner)) {
                if (cut.contains(e.getUUID())) {
                    continue;
                }
                Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(slashFrom);
                double along = rel.dot(slashDir);
                double off = rel.subtract(slashDir.scale(along)).length();
                if (along >= -0.5 && along <= reach && off <= 2.6 + e.getBbWidth() * 0.5) {
                    cut.add(e.getUUID());
                    Util.hurt(owner, e, c.slashFlat.get(), c.slashPercent.get(), perfect ? 1.0 + c.perfectBonusPercent.get() / 100.0 : 1.0, 1.0, c.armorIgnorePercent.get() / 100.0);
                    if (e.isAlive() && e == target) {
                        int t = (int) Math.round(c.lockSeconds.get() * 20);
                        Util.slowPct(e, c.lockSlowPercent.get(), t);
                        e.addEffect(new MobEffectInstance(ModEffects.SPATIAL.get(), t, 0, false, false, true));
                        pushFx(4, e.getX(), e.getY() + 0.1, e.getZ());
                    }
                    Util.voidBurst(level(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1.0, 8);
                }
            }
        }
        if (age >= CRACK0 && age < CRACK0 + 16 && (age - CRACK0) % 4 == 0) {
            int k = (age - CRACK0) / 4;
            double a = Math.PI / 4 + k * Math.PI / 2;
            Vec3 p = centre.add(Math.cos(a) * 3.0, 0, Math.sin(a) * 3.0);
            double cr = c.cutRadius.get();
            pushFx(4, p.x, p.y + 0.1, p.z);
            for (LivingEntity e : Util.enemies(level(), p, cr, owner)) {
                Util.hurt(owner, e, c.cutFlat.get(), 0, 1.0, 1.0, c.armorIgnorePercent.get() / 100.0);
            }
            Util.sparkBurst(level(), p.x, p.y + 1.0, p.z, 1.5, 12);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 1.0f, 1.3f);
        }
        if (age == COLLAPSE) {
            double r = perfect ? c.perfectRuptureRadius.get() : c.ruptureRadius.get();
            pushFx(5, centre.x, centre.y, centre.z, r);
            Util.sound(level(), centre.x, centre.y, centre.z, ModSounds.COLLAPSE.get(), 1.4f, 1.0f);
        }
        if (age >= COLLAPSE && age < RUPTURE) {
            double r = (perfect ? c.perfectRuptureRadius.get() : c.ruptureRadius.get()) * 1.5;
            for (LivingEntity e : Util.enemies(level(), centre, r, owner)) {
                Util.pull(e, centre, 0.12, 1.5);
            }
        }
        if (age == RUPTURE) {
            double r = perfect ? c.perfectRuptureRadius.get() : c.ruptureRadius.get();
            for (LivingEntity e : Util.enemies(level(), centre, r, owner)) {
                Util.hurt(owner, e, c.ruptureFlat.get(), c.rupturePercent.get(), 1.0, 1.0, c.armorIgnorePercent.get() / 100.0);
                Util.knock(e, centre, 3.0, 0.3);
            }
            Util.voidBurst(level(), centre.x, centre.y + 0.5, centre.z, r * 0.5, 50);
            Util.sound(level(), centre.x, centre.y, centre.z, ModSounds.BOOM.get(), 1.7f, 0.8f);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    /** Dimensional step: the caster reappears right behind the target (or at the nearest safe place) and faces it. */
    private void teleport(LivingEntity owner, LivingEntity target) {
        Vec3 look = target != null ? flat(target.getLookAngle()) : flat(owner.getLookAngle());
        Vec3 base = target != null ? target.position() : owner.position();
        Vec3 behind = base.subtract(look.scale(2.2));
        Vec3 spot = behind;
        perfect = target != null;
        if (!level().noCollision(owner, owner.getBoundingBox().move(behind.subtract(owner.position())))) {
            Vec3 safe = Util.safeNear(level(), owner, base, 2.2);
            spot = safe != null ? safe : owner.position();
            perfect = false;
        }
        entityData.set(DATA_PERFECT, perfect);
        Vec3 aimAt = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : spot.add(look);
        Util.HIDDEN.remove(ownerId);
        Util.UNTOUCHABLE.remove(ownerId);
        owner.setInvisible(false);
        vanished = false;
        Util.snapTo(owner, spot, Util.yawTo(spot, aimAt), 0f);
        Util.FALL_IMMUNE.merge(ownerId, level().getGameTime() + 40, Math::max);
        pushFx(2, spot.x, spot.y, spot.z);
        Util.voidBurst(level(), spot.x, spot.y, spot.z, 1.5, 14);
        slashFrom = spot.add(0, owner.getBbHeight() * 0.6, 0);
        slashDir = aimAt.subtract(slashFrom);
        slashDir = slashDir.lengthSqr() < 1.0E-4 ? look : slashDir.normalize();
        Util.sound(level(), spot.x, spot.y, spot.z, ModSounds.TEAR.get(), 1.4f, 1.4f);
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            LivingEntity o = ownerAny();
            if (o != null) {
                o.setInvisible(false);
            }
        }
        releaseCommon(normal);
    }
}
