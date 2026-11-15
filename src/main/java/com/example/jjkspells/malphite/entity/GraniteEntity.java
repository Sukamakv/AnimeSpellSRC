package com.example.jjkspells.malphite.entity;

import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.registry.ModEffects;
import com.example.jjkspells.malphite.registry.ModEntities;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Granite Shield - Heart of the Mountain. Follows the caster; one clock (age = tickCount - 1):
 * <pre>
 *  0..F        the granite forms (caster planted); at F a shockwave pushes enemies away
 *  F..F+D      the shield: damage reduction, armor, knockback immunity; every hit is stored and partly reflected (MalEvents)
 *  end / recast  Granite Detonation: damage grows with the damage stopped; the armor shatters
 * </pre>
 */
public class GraniteEntity extends MalEntityBase {
    /** Casters with an active shield -> their entity (a second cast detonates it). */
    private static final Map<UUID, GraniteEntity> ACTIVE = new ConcurrentHashMap<>();

    public static final int AFTERGLOW = 26;

    private static final EntityDataAccessor<Integer> DATA_FIN = SynchedEntityData.defineId(GraniteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_UNBROKEN = SynchedEntityData.defineId(GraniteEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_CHARGE = SynchedEntityData.defineId(GraniteEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(GraniteEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(GraniteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DUR = SynchedEntityData.defineId(GraniteEntity.class, EntityDataSerializers.INT);

    private Util.Granite state;
    private boolean detonated, release;
    private int detonatedAt = -1;

    public GraniteEntity(EntityType<? extends GraniteEntity> type, Level level) {
        super(type, level);
    }

    public static GraniteEntity create(Level level, LivingEntity caster) {
        GraniteEntity e = new GraniteEntity(ModEntities.GRANITE.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_RADIUS, MalConfig.GRANITE.radius.get().floatValue());
        e.entityData.set(DATA_FORM, MalConfig.GRANITE.formTicks.get());
        e.entityData.set(DATA_DUR, (int) Math.round(MalConfig.GRANITE.durationSeconds.get() * 20));
        return e;
    }

    /** True if the caster has a shield up (a recast then detonates it instead of starting a new one). */
    public static boolean tryRelease(LivingEntity caster) {
        GraniteEntity g = ACTIVE.get(caster.getUUID());
        if (g == null || g.isRemoved() || g.detonated) {
            ACTIVE.remove(caster.getUUID());
            return false;
        }
        g.release = true;
        return true;
    }

    public static boolean isActive(LivingEntity caster) {
        GraniteEntity g = ACTIVE.get(caster.getUUID());
        return g != null && !g.isRemoved() && !g.detonated;
    }

    public int getFormTicks() {
        return entityData.get(DATA_FORM);
    }

    public int getDuration() {
        return entityData.get(DATA_DUR);
    }

    /** Age of the detonation or -1. */
    public int getDetonationAt() {
        return entityData.get(DATA_FIN);
    }

    public boolean isUnbroken() {
        return entityData.get(DATA_UNBROKEN);
    }

    /** 0..1: how much the stored damage has loaded the stone. */
    public float getCharge() {
        return entityData.get(DATA_CHARGE);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FIN, -1);
        entityData.define(DATA_UNBROKEN, false);
        entityData.define(DATA_CHARGE, 0f);
        entityData.define(DATA_RADIUS, 10f);
        entityData.define(DATA_FORM, 30);
        entityData.define(DATA_DUR, 160);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malphite.client.ClientFx.tickGranite(this));
            return;
        }
        var c = MalConfig.GRANITE;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        int form = c.formTicks.get();
        int dur = (int) Math.round(c.durationSeconds.get() * 20);
        if (!detonated) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        if (age == 0) {
            state = new Util.Granite();
            state.block = c.reductionPercent.get() / 100.0;
            Util.GRANITE.put(owner.getUUID(), state);
            ACTIVE.put(owner.getUUID(), this);
            Util.LOCKED.add(owner.getUUID());
            AttributeInstance ar = owner.getAttribute(Attributes.ARMOR);
            if (ar != null) {
                ar.removeModifier(GRANITE_ARMOR_ID);
                ar.addTransientModifier(new AttributeModifier(GRANITE_ARMOR_ID, "malphite_granite_armor", c.armorPercent.get() / 100.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.RUMBLE.get(), 1.2f, 1.0f);
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.GRIND.get(), 1.4f, 0.9f);
        }
        if (state == null) {
            finish(false);
            discard();
            return;
        }
        if (detonated) {
            if (age >= detonatedAt + AFTERGLOW) {
                discard();
            }
            return;
        }
        Vec3 p = owner.position();
        // forming: the owner stands planted while plates lock on
        if (age < form) {
            owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            if (age % 2 == 0) {
                Util.fx(level(), ModParticles.ROCK.get(), p.x, p.y + 0.4, p.z, 3, 1.2, 0.3, 1.2, 0.05);
                Util.fx(level(), ModParticles.DUST.get(), p.x, p.y + 0.2, p.z, 2, 0.9, 0.05, 0.9, 0.03);
                Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1.0, p.z, 2, 0.6, 0.7, 0.6, 0.03);
            }
        }
        if (age == form) {
            Util.LOCKED.remove(owner.getUUID());
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ARMOR.get(), 1.6f, 0.9f);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SLAM.get(), 1.2f, 1.1f);
            Util.blast(level(), p.x, p.y, p.z, c.pushRadius.get(), 36);
            Util.fxDir(level(), ModParticles.RING.get(), p.x, p.y + 0.1, p.z, 0, 0, 0);
            for (LivingEntity e : Util.enemies(level(), p, c.pushRadius.get(), owner)) {
                Util.knock(e, p, 3.5, 0.15);
            }
        }
        // the shield
        Util.kbImmune(owner, 4);
        if (age % 3 == 0) {
            double sp = c.speedPercent.get();
            if (sp < 0) {
                Util.slowPct(owner, -sp, 8);
            } else if (sp > 0) {
                Util.hastePct(owner, sp, 8);
            }
        }
        if (age % 20 == 0) {
            owner.addEffect(new MobEffectInstance(ModEffects.GRANITE.get(), 30, 0, false, false, true));
        }
        if (age >= form) {
            if (!state.unbroken && owner.getHealth() < owner.getMaxHealth() * c.lowHealthPercent.get() / 100.0) {
                state.unbroken = true;
                state.block = c.unbrokenReductionPercent.get() / 100.0;
                entityData.set(DATA_UNBROKEN, true);
                entityData.set(DATA_RADIUS, c.unbrokenRadius.get().floatValue());
                Util.ccImmune(owner, Math.max(1, form + dur - age) + 4);
                Util.MELEE_BONUS.put(owner.getUUID(), new double[]{c.unbrokenMeleePercent.get() / 100.0, owner.level().getGameTime() + c.unbrokenMeleeSeconds.get() * 20});
                Util.sound(level(), p.x, p.y, p.z, ModSounds.CRACK.get(), 1.4f, 0.6f);
                Util.blast(level(), p.x, p.y + 1, p.z, 3, 30);
            }
            int steps = (int) Math.min(Math.floor(state.prevented / c.perStep.get()), Math.floor(c.maxBonus.get() / Math.max(0.0001, c.perStepBonus.get())));
            entityData.set(DATA_CHARGE, (float) Math.min(1.0, steps * c.perStepBonus.get() / Math.max(1.0, c.maxBonus.get())));
            if (age % 4 == 0) {
                Util.fx(level(), ModParticles.EMBER.get(), p.x, p.y + 1.0, p.z, 1, 0.5, 0.8, 0.5, 0.02);
            }
            if (release || age >= form + dur) {
                detonate(owner);
            }
        }
    }

    private void detonate(LivingEntity owner) {
        var c = MalConfig.GRANITE;
        detonated = true;
        detonatedAt = tickCount - 1;
        entityData.set(DATA_FIN, detonatedAt);
        double radius = state.unbroken ? c.unbrokenRadius.get() : c.radius.get();
        int steps = (int) Math.min(Math.floor(state.prevented / c.perStep.get()), Math.floor(c.maxBonus.get() / Math.max(0.0001, c.perStepBonus.get())));
        double flat = c.flat.get() + Math.min(c.maxBonus.get(), steps * c.perStepBonus.get());
        Vec3 at = owner.position();
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 1.5f, 1.1f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.CRACK.get(), 1.5f, 0.8f);
        Util.blast(level(), at.x, at.y + 0.6, at.z, radius * 0.5, 90);
        Util.fxDir(level(), ModParticles.RING.get(), at.x, at.y + 0.1, at.z, 0, 0, 0);
        Util.ring(level(), ModParticles.DUST.get(), at.add(0, 0.3, 0), 36, 0.7, 0.04);
        Util.ring(level(), ModParticles.ROCK.get(), at.add(0, 0.6, 0), 24, 0.55, 0.12);
        Util.fx(level(), ModParticles.SHARD.get(), at.x, at.y + 1.0, at.z, 60, 0.7, 0.9, 0.7, 0.6);
        for (LivingEntity e : Util.enemies(level(), at, radius, owner)) {
            Util.hurt(owner, e, flat, c.percent.get());
            if (e.isAlive()) {
                Util.knock(e, at, c.knockBlocks.get(), 0.2);
            }
        }
        releaseCaster(owner);
    }

    private void releaseCaster(LivingEntity owner) {
        Util.GRANITE.remove(owner.getUUID());
        ACTIVE.remove(owner.getUUID(), this);
        Util.LOCKED.remove(owner.getUUID());
        AttributeInstance ar = owner.getAttribute(Attributes.ARMOR);
        if (ar != null) {
            ar.removeModifier(GRANITE_ARMOR_ID);
        }
        Util.KB_IMMUNE.remove(owner.getUUID());
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.GRANITE.remove(ownerId);
            ACTIVE.remove(ownerId, this);
            Util.LOCKED.remove(ownerId);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            releaseCaster(owner);
        }
    }
}
