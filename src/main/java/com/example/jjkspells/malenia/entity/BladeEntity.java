package com.example.jjkspells.malenia.entity;

import com.example.jjkspells.malenia.config.MelConfig;
import com.example.jjkspells.malenia.registry.ModEffects;
import com.example.jjkspells.malenia.registry.ModEntities;
import com.example.jjkspells.malenia.registry.ModParticles;
import com.example.jjkspells.malenia.registry.ModSounds;
import com.example.jjkspells.malenia.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Miquella's Blade - The Undefeated Swordsman. One clock (age = tickCount - 1):
 * <pre>
 *  0..DASH0   stance
 *  DASH0..DASH1  opening dash
 *  SL[0..3]   four slashes (right to left, reverse diagonal, upward cut, spin)
 *  THRUST     the thrust, pushes back
 *  LEAP..EXEC leap, then the downward execution
 *  END        done
 * </pre>
 */
public class BladeEntity extends MelEntityBase {
    public static final int DASH0 = 8, DASH1 = 15, THRUST = 46, LEAP = 52, EXEC = 62, END = 80;
    public static final int[] SL = {20, 26, 32, 39};

    private static final EntityDataAccessor<Integer> DATA_COUNT = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SX = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SY = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SZ = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TX = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TY = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TZ = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_PERFECT = SynchedEntityData.defineId(BladeEntity.class, EntityDataSerializers.BOOLEAN);

    // client only
    public final List<double[]> fx = new ArrayList<>();
    public final List<Vec3> trail = new ArrayList<>();
    public int seen;

    private UUID targetId;
    private Vec3 start, center, leapFrom;
    private int primaryHits;

    public BladeEntity(EntityType<? extends BladeEntity> type, Level level) {
        super(type, level);
    }

    public static BladeEntity create(Level level, LivingEntity caster, LivingEntity target) {
        BladeEntity e = new BladeEntity(ModEntities.BLADE.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.targetId = target == null ? null : target.getUUID();
        return e;
    }

    public int getCount() {
        return entityData.get(DATA_COUNT);
    }

    /** 0..3 the four slashes, 4 the thrust, 5 the execution, 6 the opening dash. */
    public int getKind() {
        return entityData.get(DATA_KIND);
    }

    public float getSlashYaw() {
        return entityData.get(DATA_YAW);
    }

    public Vec3 getSlashPos() {
        return new Vec3(entityData.get(DATA_SX), entityData.get(DATA_SY), entityData.get(DATA_SZ));
    }

    public Vec3 getTargetPos() {
        return new Vec3(entityData.get(DATA_TX), entityData.get(DATA_TY), entityData.get(DATA_TZ));
    }

    public boolean isPerfect() {
        return entityData.get(DATA_PERFECT);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COUNT, 0);
        entityData.define(DATA_KIND, 0);
        entityData.define(DATA_YAW, 0f);
        entityData.define(DATA_SX, 0f);
        entityData.define(DATA_SY, 0f);
        entityData.define(DATA_SZ, 0f);
        entityData.define(DATA_TX, 0f);
        entityData.define(DATA_TY, 0f);
        entityData.define(DATA_TZ, 0f);
        entityData.define(DATA_PERFECT, false);
    }

    private LivingEntity target() {
        if (targetId != null && level() instanceof ServerLevel sl && sl.getEntity(targetId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malenia.client.ClientFx.tickBlade(this));
            return;
        }
        var c = MelConfig.BLADE;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            start = owner.position();
            LivingEntity t = target();
            center = t != null ? t.position() : start.add(flat(owner.getLookAngle()).scale(4.0));
            lsCap = c.lifestealCap.get();
            Util.LOCKED.add(owner.getUUID());
            Util.reduce(owner, c.evasiveReductionPercent.get() / 100.0, END + 4);
            AttributeInstance kb = owner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(KB_ID);
                kb.addTransientModifier(new AttributeModifier(KB_ID, "malenia_blade_kb", c.evasiveKbResistPercent.get() / 100.0, AttributeModifier.Operation.ADDITION));
            }
            Util.FALL_IMMUNE.put(owner.getUUID(), owner.level().getGameTime() + END + 60);
            Util.sound(level(), start.x, start.y, start.z, ModSounds.RING.get(), 1.0f, 1.3f);
        }
        owner.setSprinting(false);
        LivingEntity t = target();
        if (age < EXEC + 2) {
            if (t == null) {
                t = WaterfowlEntity.nearest(level(), owner, owner.position(), 12.0);
                targetId = t == null ? null : t.getUUID();
            }
            if (t != null) {
                center = center.lerp(t.position(), 0.6);
            }
        }
        entityData.set(DATA_TX, (float) center.x);
        entityData.set(DATA_TY, (float) center.y);
        entityData.set(DATA_TZ, (float) center.z);
        setPos(owner.getX(), owner.getY(), owner.getZ());
        move(owner, age);
        if (age < DASH0 && age % 2 == 0) {
            Vec3 p = owner.position();
            Util.fx(level(), ModParticles.GOLD.get(), p.x, p.y + 1.2, p.z, 2, 0.4, 0.6, 0.4, 0.03);
        }
        if (age == DASH1) {
            dashHit(owner, t);
        }
        for (int i = 0; i < SL.length; i++) {
            if (age == SL[i]) {
                slash(owner, t, i);
            }
        }
        if (age == THRUST) {
            thrust(owner, t);
        }
        if (age == EXEC) {
            execute(owner);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private Vec3 around(double ang, double r, double up) {
        return new Vec3(center.x + Math.cos(ang) * r, center.y + up, center.z + Math.sin(ang) * r);
    }

    private void move(LivingEntity owner, int age) {
        Vec3 here = owner.position();
        double fromAng = Math.atan2(start.z - center.z, start.x - center.x);
        Vec3 p;
        if (age < DASH0) {
            p = start;
        } else if (age < DASH1) {
            double u = smooth((age - DASH0 + 1) / (double) (DASH1 - DASH0));
            p = start.lerp(ground(around(fromAng, 1.9, 0)), u);
        } else if (age < THRUST - 6) {
            // the four slashes: she slides from the right of the target to its left
            double u = smooth((age - DASH1) / (double) (SL[3] - DASH1 + 4));
            double ang = fromAng + Math.toRadians(70 - 140 * u);
            p = ground(around(ang, 1.9, 0));
        } else if (age < THRUST) {
            double u = (age - (THRUST - 6)) / 6.0;
            double ang = fromAng - Math.toRadians(70);
            double r = u < 0.6 ? 1.9 + 1.4 * (u / 0.6) : 3.3 - 1.6 * ((u - 0.6) / 0.4);
            p = ground(around(ang, r, 0));
        } else if (age < LEAP) {
            double ang = fromAng - Math.toRadians(70);
            p = ground(around(ang, 1.7, 0));
        } else if (age < EXEC) {
            if (leapFrom == null) {
                leapFrom = here;
            }
            double u = (age - LEAP + 1) / (double) (EXEC - LEAP);
            Vec3 apex = new Vec3(center.x, center.y + 4.5, center.z);
            Vec3 land = ground(new Vec3(center.x, center.y, center.z));
            p = u < 0.6 ? leapFrom.lerp(apex, smooth(u / 0.6)) : apex.lerp(land.add(0, 0.1, 0), smooth((u - 0.6) / 0.4));
        } else {
            p = ground(new Vec3(center.x, center.y, center.z));
        }
        float yaw = Util.yawTo(p, new Vec3(center.x, p.y, center.z));
        hover(owner, p, yaw, age >= LEAP && age < EXEC ? 45f : 8f);
        if (age >= END - 6) {
            owner.setNoGravity(false);
        }
    }

    private Vec3 ground(Vec3 p) {
        Double g = Util.findGround(level(), p.x, p.y + 2, p.z);
        return g == null ? p : new Vec3(p.x, g, p.z);
    }

    private void mark(LivingEntity owner, int kind, Vec3 at) {
        entityData.set(DATA_KIND, kind);
        entityData.set(DATA_YAW, owner.getYRot());
        entityData.set(DATA_SX, (float) at.x);
        entityData.set(DATA_SY, (float) at.y);
        entityData.set(DATA_SZ, (float) at.z);
        entityData.set(DATA_COUNT, entityData.get(DATA_COUNT) + 1);
    }

    private void dashHit(LivingEntity owner, LivingEntity t) {
        var c = MelConfig.BLADE;
        Vec3 p = owner.position().add(0, owner.getBbHeight() * 0.55, 0);
        mark(owner, 6, p);
        Util.swing(owner);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.DASH.get(), 1.1f, 1.2f);
        Util.bladeBurst(level(), p.x, p.y, p.z, 8);
        boolean any = false;
        Set<UUID> done = new HashSet<>();
        Vec3 line = start.add(0, 1, 0);
        for (LivingEntity e : Util.enemies(level(), center, 4.0, owner)) {
            boolean primary = t != null && e == t;
            if (!primary && e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(p) > 3.0 && distSeg(e.position().add(0, 1, 0), line, p) > 1.8) {
                continue;
            }
            if (done.add(e.getUUID())) {
                Util.hurt(owner, e, c.dashFlat.get(), 0);
                any = true;
                if (primary) {
                    primaryHits++;
                }
            }
        }
        if (any) {
            steal(c.lifestealDash.get());
        }
    }

    private static double distSeg(Vec3 pt, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l = ab.lengthSqr();
        double k = l < 1.0E-6 ? 0 : Math.max(0, Math.min(1, pt.subtract(a).dot(ab) / l));
        return pt.distanceTo(a.add(ab.scale(k)));
    }

    private void slash(LivingEntity owner, LivingEntity t, int i) {
        var c = MelConfig.BLADE;
        Vec3 p = owner.position().add(0, owner.getBbHeight() * 0.55, 0);
        Vec3 face = flat(center.subtract(owner.position()));
        Vec3 sp = p.add(face.scale(1.3));
        mark(owner, i, sp);
        Util.swing(owner);
        Util.sound(level(), sp.x, sp.y, sp.z, ModSounds.SLASH.get(), 1.0f, 0.9f + 0.1f * i);
        Util.bladeBurst(level(), sp.x, sp.y, sp.z, 5);
        boolean any = false;
        for (LivingEntity e : Util.enemies(level(), center, 3.2, owner)) {
            Util.hurt(owner, e, c.slashFlat.get(), 0);
            any = true;
            Vec3 ec = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Util.bladeBurst(level(), ec.x, ec.y, ec.z, 4);
            if (t != null && e == t) {
                primaryHits++;
            }
            if (e.isAlive() && i == 2) {
                Util.knock(e, owner.position(), 0.5, 0.25);
            }
        }
        if (any) {
            steal(c.lifestealSlash.get());
        }
    }

    private void thrust(LivingEntity owner, LivingEntity t) {
        var c = MelConfig.BLADE;
        Vec3 p = owner.position().add(0, owner.getBbHeight() * 0.55, 0);
        Vec3 face = flat(center.subtract(owner.position()));
        Vec3 sp = p.add(face.scale(1.6));
        mark(owner, 4, sp);
        Util.swing(owner);
        Util.sound(level(), sp.x, sp.y, sp.z, ModSounds.STAB.get(), 1.4f, 1.0f);
        Util.ring(level(), ModParticles.SILVER.get(), sp, 12, 0.3, 0.0);
        Util.bladeBurst(level(), sp.x, sp.y, sp.z, 10);
        boolean any = false;
        for (LivingEntity e : Util.enemies(level(), center, 3.2, owner)) {
            double mult = 1.0;
            if (e.getHealth() < e.getMaxHealth() * c.weakThresholdPercent.get() / 100.0) {
                mult += c.weakBonusPercent.get() / 100.0;
            }
            Util.hurt(owner, e, c.thrustFlat.get(), c.thrustPercent.get(), mult, 1.0, c.armorIgnorePercent.get() / 100.0);
            any = true;
            if (t != null && e == t) {
                primaryHits++;
            }
            if (e.isAlive()) {
                Util.knock(e, owner.position(), c.thrustKnock.get(), 0.1);
            }
        }
        if (any) {
            steal(c.lifestealThrust.get());
        }
    }

    private void execute(LivingEntity owner) {
        var c = MelConfig.BLADE;
        boolean perfect = primaryHits >= 6;
        entityData.set(DATA_PERFECT, perfect);
        Vec3 g = ground(center);
        Vec3 sp = g.add(0, 1.0, 0);
        mark(owner, 5, sp);
        Util.swing(owner);
        Util.sound(level(), g.x, g.y, g.z, ModSounds.BOOM.get(), 1.5f, 1.2f);
        Util.sound(level(), g.x, g.y, g.z, ModSounds.RING.get(), 1.3f, 0.9f);
        Util.ring(level(), ModParticles.SILVER.get(), g.add(0, 0.4, 0), 36, 0.5, 0.05);
        Util.ring(level(), ModParticles.GOLD.get(), g.add(0, 0.8, 0), 24, 0.35, 0.08);
        Util.fxDir(level(), ModParticles.RING.get(), g.x, g.y + 0.5, g.z, 0, 0, 0);
        Util.fx(level(), ModParticles.FLASH.get(), g.x, g.y + 1, g.z, 1, 0, 0, 0, 0);
        double mult = perfect ? 1.0 + c.perfectBonusPercent.get() / 100.0 : 1.0;
        for (LivingEntity e : Util.enemies(level(), g, c.radius.get(), owner)) {
            Util.hurt(owner, e, c.executeFlat.get(), c.executePercent.get(), mult, 1.0);
            steal(c.lifestealExecute.get());
            if (e.isAlive()) {
                Util.knock(e, g, 3.0, 0.3);
                Util.stagger(e, 14);
            }
        }
        if (perfect) {
            Util.hastePct(owner, c.perfectSpeedPercent.get(), (int) (c.perfectSpeedSeconds.get() * 20));
            owner.heal((float) (double) c.perfectHeal.get());
            owner.addEffect(new MobEffectInstance(ModEffects.SWIFT.get(), (int) (c.perfectSpeedSeconds.get() * 20), 0, false, false, true));
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
