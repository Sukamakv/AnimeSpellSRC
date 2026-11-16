package com.example.jjkspells.irontomb.entity;

import com.example.jjkspells.irontomb.IrontombBalance;
import com.example.jjkspells.irontomb.IrontombFxServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The Manipulated Logos (type 0, Quantum) and the Imprisoned Mythos (type 1, Imaginary).
 * They cannot die: a killing blow sends them into Grave Wound, and whatever hits them also hits the boss.
 */
public class IrontombSatellite extends Mob {
    private static final EntityDataAccessor<Integer> D_TYPE = SynchedEntityData.defineId(IrontombSatellite.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> D_GRAVE = SynchedEntityData.defineId(IrontombSatellite.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> D_MARK = SynchedEntityData.defineId(IrontombSatellite.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_BOLT_T = SynchedEntityData.defineId(IrontombSatellite.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_BOLT_TARGET = SynchedEntityData.defineId(IrontombSatellite.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_BOSS = SynchedEntityData.defineId(IrontombSatellite.class, EntityDataSerializers.INT);

    public static final int BOLT_FIRE = 24;

    private UUID bossId;
    private int graveT;
    private int cooldown = 80;

    public IrontombSatellite(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, IrontombBalance.NORM)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(D_TYPE, 0);
        entityData.define(D_GRAVE, false);
        entityData.define(D_MARK, -1);
        entityData.define(D_BOLT_T, -1);
        entityData.define(D_BOLT_TARGET, -1);
        entityData.define(D_BOSS, -1);
    }

    public int getSatType() { return entityData.get(D_TYPE); }
    public boolean isGrave() { return entityData.get(D_GRAVE); }
    public int getMark() { return entityData.get(D_MARK); }
    public int getBoltT() { return entityData.get(D_BOLT_T); }
    public int getBoltTarget() { return entityData.get(D_BOLT_TARGET); }
    public int getBossId() { return entityData.get(D_BOSS); }

    public void bind(IrontombEntity boss, int type) {
        this.bossId = boss.getUUID();
        entityData.set(D_TYPE, type);
        entityData.set(D_BOSS, boss.getId());
    }

    /** (Re)forges the satellite for a phase: full health, Grave Wound cleared. */
    public void reforge(int phase) {
        cfgPhase = phase;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(IrontombBalance.NORM);
        setHealth((float) IrontombBalance.NORM);
        var mr = getAttribute(com.example.jjkspells.ModAttributes.MAX_HP_RESIST.get());
        if (mr != null) {
            mr.setBaseValue(0.9);
        }
        entityData.set(D_GRAVE, false);
        graveT = 0;
        clearMark();
    }

    public void clearMark() {
        entityData.set(D_MARK, -1);
    }

    private IrontombEntity boss() {
        if (bossId != null && level() instanceof ServerLevel sl && sl.getEntity(bossId) instanceof IrontombEntity b && b.isAlive()) {
            return b;
        }
        return null;
    }

    @Override public boolean isPushable() { return false; }
    @Override public void knockback(double s, double x, double z) { }
    @Override public boolean canBeAffected(MobEffectInstance e) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public void checkDespawn() { }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 300.0 * 300.0; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(40.0); }
    @Override public boolean isPickable() { return true; }
    @Override public void addAdditionalSaveData(CompoundTag t) { }
    @Override public void readAdditionalSaveData(CompoundTag t) { discard(); }

    @Override
    public boolean hurt(DamageSource s, float amt) {
        if (level().isClientSide) {
            return false;
        }
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(s, amt);
        }
        IrontombEntity b = boss();
        if (b == null || !b.isAwake() || b.cutStandalone()) {
            return false;
        }
        b.applyShared(s, amt);
        if (isGrave()) {
            return false;
        }
        double pool = Math.max(1.0, cfgPhase == 1 ? IrontombBalance.SATELLITE_HEALTH_P1 : IrontombBalance.SATELLITE_HEALTH_P2);
        if (b.isErasing()) {
            pool *= IrontombBalance.ERASURE_SATELLITE_MULT;
        }
        float a = (float) (amt * (1f - b.reduction()) * IrontombBalance.NORM / pool);
        float nh = getHealth() - a;
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.burst(sl, position().add(0, 0.8, 0), getSatType() == 0 ? IrontombFxServer.VIOLET : IrontombFxServer.GOLD, 10, 0.5, 0.1);
        }
        if (nh <= 0.5f) {
            setHealth(1f);
            enterGrave(b);
        } else {
            setHealth(nh);
        }
        return true;
    }

    private void enterGrave(IrontombEntity b) {
        entityData.set(D_GRAVE, true);
        entityData.set(D_BOLT_T, -1);
        clearMark();
        if (level() instanceof ServerLevel xsl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(xsl, position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 7f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(xsl, position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 5f);
        }
        graveT = b.isErasing() ? Math.min(IrontombBalance.GRAVE_TICKS, IrontombBalance.ERASURE_GRAVE_TICKS) : IrontombBalance.GRAVE_TICKS;
        b.onGrave(this);
    }

    private void recover() {
        entityData.set(D_GRAVE, false);
        setHealth(getMaxHealth());
        playSound(SoundEvents.BEACON_ACTIVATE, 3f, 0.8f);
        if (level() instanceof ServerLevel xsl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(xsl, position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 3f);
        }
    }

    private int cfgVersion = -1;
    private int cfgPhase = 1;

    @Override
    public net.minecraft.network.chat.Component getTypeName() {
        return net.minecraft.network.chat.Component.literal("Grave Wound Satellite");
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            return;
        }
        if (cfgVersion != IrontombBalance.VERSION) {
            cfgVersion = IrontombBalance.VERSION;
        }
        IrontombEntity b = boss();
        if (b == null) {
            discard();
            return;
        }
        orbit(b);
        if (isGrave()) {
            if (--graveT <= 0) {
                recover();
            }
            return;
        }
        if (!b.isAwake() || b.cutStandalone() || !b.hasTargets()) {
            return;
        }
        int bt = getBoltT();
        if (bt >= 0) {
            bt++;
            entityData.set(D_BOLT_T, bt);
            if (bt == BOLT_FIRE) {
                fire(b);
            }
            if (bt > BOLT_FIRE + 10) {
                entityData.set(D_BOLT_T, -1);
            }
            return;
        }
        if (getMark() >= 0) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = 90 + random.nextInt(70);
        List<LivingEntity> l = b.pickables();
        if (l.isEmpty()) {
            return;
        }
        LivingEntity t = l.get(random.nextInt(l.size()));
        if (!b.hasQueued() && random.nextFloat() < 0.45f && !b.isSpecialAttack()) {
            entityData.set(D_MARK, t.getId());
            playSound(SoundEvents.BELL_BLOCK, 4f, getSatType() == 0 ? 1.4f : 0.6f);
            b.queueFromSatellite(getSatType(), t.getId());
        } else {
            entityData.set(D_BOLT_TARGET, t.getId());
            entityData.set(D_BOLT_T, 0);
            if (level() instanceof ServerLevel xsl) {
                // telegraph the bolt's zone for the BOLT_FIRE ticks before it lands
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, t.position(), getSatType() == 0 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, getSatType() == 0 ? 9f / 7f : 0.7f, BOLT_FIRE);
            }
            playSound(SoundEvents.BEACON_POWER_SELECT, 3f, getSatType() == 0 ? 1.5f : 0.7f);
        }
    }

    private void orbit(IrontombEntity b) {
        double ang = tickCount * 0.028 * (getSatType() == 0 ? 1 : -1) + getSatType() * Math.PI;
        double r = isGrave() ? 26.0 : 38.0;
        double y = b.getY() + (isGrave() ? 12.0 : 26.0 + Math.sin(tickCount * 0.05) * 3.0);
        setPos(b.getX() + Math.cos(ang) * r, y, b.getZ() + Math.sin(ang) * r);
    }

    private void fire(IrontombEntity b) {
        Entity te = level().getEntity(getBoltTarget());
        if (!(te instanceof LivingEntity t) || !t.isAlive()) {
            return;
        }
        boolean logos = getSatType() == 0;
        playSound(logos ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.BELL_RESONATE, 4f, logos ? 1.6f : 0.7f);
        playSound(logos ? com.example.jjkspells.irontomb.IrontombSounds.BOLT : com.example.jjkspells.irontomb.IrontombSounds.BELL, 8f, logos ? 1.0f : 1.4f);
        float[] f = logos ? IrontombBalance.LOGOS_BOLT : IrontombBalance.MYTHOS_BOLT;
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(logos ? 4.5 : 0.5),
                p -> p.isAlive() && (p instanceof Player pl ? !pl.isSpectator() : IrontombBalance.FIGHT_MOBS && p instanceof Mob
                        && !(p instanceof IrontombEntity) && !(p instanceof IrontombSatellite)));
        for (LivingEntity p : near) {
            p.invulnerableTime = 0;
            IrontombEntity own = boss();
            com.example.jjkspells.irontomb.IrontombDamage.deal(this, own != null ? own : this, p, f, 1f);
            if (!logos) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
            }
        }
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.burst(sl, t.position().add(0, 1, 0), logos ? IrontombFxServer.VIOLET : IrontombFxServer.GOLD, 30, 0.6, 0.2);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, 1, 0), logos ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, logos ? 2.5f : 1.6f);
        }
        b.addHatred(2f);
    }
}
