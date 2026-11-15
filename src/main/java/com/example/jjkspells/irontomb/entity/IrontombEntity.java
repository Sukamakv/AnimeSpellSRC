package com.example.jjkspells.irontomb.entity;

import com.example.jjkspells.irontomb.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Irontomb, Anti-Nous, Funeral of Gnosis: a hovering colossus that fights through two satellites,
 * the Manipulated Logos and the Imprisoned Mythos. All state that the client needs to draw the fight
 * (attack id + tick, cutscene id + tick, hatred, target, lock) is synced entity data.
 */
public class IrontombEntity extends Mob {
    private static final EntityDataAccessor<Integer> D_PHASE = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_ATTACK = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_ATK_T = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_CUT = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_CUT_T = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_TARGET = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_LOCK_ID = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_LOCK_LEFT = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_ERASE = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> D_HATRED = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> D_AIM = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> D_AWAKE = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> D_RESIST = SynchedEntityData.defineId(IrontombEntity.class, EntityDataSerializers.BOOLEAN);

    private static final UUID ERUDITION_ID = UUID.fromString("5d2c9b1e-7a41-4f0e-9d63-1c7a0b2e4f11");

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Irontomb, Anti-Nous, Funeral of Gnosis"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    private UUID logosId, mythosId;
    private int cooldown = 60;
    private int queued, queuedTarget = -1, queuedFrom = -1;
    private int resistCd = 700;
    private boolean wasBothGrave;

    /** Hit boxes of the body, {x, y, z, width, height} in the body frame (the boss' facing rotates them): head and shoulders. The arms and blades have none on purpose. */
    private static final float[][] HITBOXES = {
            {0f, 45f, 0f, 38f, 32f},
            {-19f, 26f, 0f, 20f, 18f},
            {19f, 26f, 0f, 20f, 18f}
            };
    private final IrontombPart[] parts;

    public IrontombEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        parts = new IrontombPart[HITBOXES.length];
        for (int i = 0; i < parts.length; i++) {
            float[] h = HITBOXES[i];
            parts[i] = new IrontombPart(this, h[0], h[1], h[2], h[3], h[4]);
        }
        this.setNoGravity(true);
        this.xpReward = 1000;
        this.setPersistenceRequired();
    }

    @Override public boolean isMultipartEntity() { return true; }
    @Override public net.minecraftforge.entity.PartEntity<?>[] getParts() { return parts; }

    /** Moves every hit box to where that piece of the body is drawn (same on the client and the server). */
    private void updateParts() {
        double yaw = -Math.toRadians(getYRot());
        double cs = Math.cos(yaw), sn = Math.sin(yaw);
        for (IrontombPart p : parts) {
            double x = p.ox * cs + p.oz * sn;
            double z = -p.ox * sn + p.oz * cs;
            p.setPos(getX() + x, getY() + p.oy - p.getBbHeight() / 2.0, getZ() + z);
            p.xo = p.xOld = p.getX();
            p.yo = p.yOld = p.getY();
            p.zo = p.zOld = p.getZ();
        }
    }

    private int faceCd;
    private LivingEntity faceTarget;

    /** The body turns to the current target (or the nearest player or mob): its facing is what everyone sees and what the hit boxes follow. */
    private void faceTick() {
        Entity tgt = getAttack() != 0 ? level().getEntity(getTargetId()) : null;
        LivingEntity f = tgt instanceof LivingEntity le && le.isAlive() ? le : null;
        if (f == null) {
            if (--faceCd <= 0 || faceTarget == null || !faceTarget.isAlive()) {
                faceCd = 10;
                double best = Double.MAX_VALUE;
                faceTarget = null;
                for (LivingEntity e : foes(150)) {
                    double d = e.distanceToSqr(this) - (e instanceof Player ? 400.0 : 0.0);
                    if (d < best) {
                        best = d;
                        faceTarget = e;
                    }
                }
            }
            f = faceTarget;
        }
        if (f == null) {
            return;
        }
        double dx = f.getX() - getX(), dz = f.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0) {
            return;
        }
        float want = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
        float turned = Mth.approachDegrees(getYRot(), want, 6f);
        setYRot(turned);
        yBodyRot = turned;
        yHeadRot = turned;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, IrontombBalance.NORM)
                .add(Attributes.ARMOR, IrontombBalance.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, IrontombBalance.ARMOR_TOUGHNESS)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 100.0);
    }

    // ------------------------------------------------------------------ synced data
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(D_PHASE, 1);
        entityData.define(D_ATTACK, 0);
        entityData.define(D_ATK_T, 0);
        entityData.define(D_CUT, 0);
        entityData.define(D_CUT_T, 0);
        entityData.define(D_TARGET, -1);
        entityData.define(D_LOCK_ID, -1);
        entityData.define(D_LOCK_LEFT, 0);
        entityData.define(D_HATRED, 0f);
        entityData.define(D_ERASE, 0);
        entityData.define(D_AIM, new Vector3f());
        entityData.define(D_AWAKE, false);
        entityData.define(D_RESIST, false);
    }

    public int getPhase() { return entityData.get(D_PHASE); }
    public int getAttack() { return entityData.get(D_ATTACK); }
    public int getAtkT() { return entityData.get(D_ATK_T); }
    public int getCut() { return entityData.get(D_CUT); }
    public int getCutT() { return entityData.get(D_CUT_T); }
    public int getTargetId() { return entityData.get(D_TARGET); }
    public int getLockId() { return entityData.get(D_LOCK_ID); }
    public int getLockLeft() { return entityData.get(D_LOCK_LEFT); }
    /** Ticks left of the Erasure countdown (0 = the Sword of Erasure is not forming). */
    public int getErase() { return entityData.get(D_ERASE); }
    public boolean isErasing() { return getErase() > 0; }
    public boolean isSpecialAttack() {
        int a = getAttack();
        return a == IrontombAttack.GENESIS || a == IrontombAttack.SINGULARITY || a == IrontombAttack.ERASURE;
    }
    public float getHatred() { return entityData.get(D_HATRED); }
    public boolean isAwake() { return entityData.get(D_AWAKE); }
    public boolean isResist() { return entityData.get(D_RESIST); }
    public Vec3 getAim() { Vector3f v = entityData.get(D_AIM); return new Vec3(v.x(), v.y(), v.z()); }
    private void setAim(Vec3 v) { entityData.set(D_AIM, new Vector3f((float) v.x, (float) v.y, (float) v.z)); }
    public void setHatred(float v) { entityData.set(D_HATRED, Mth.clamp(v, 0f, 100f)); }
    public boolean cutStandalone() { return IrontombCut.standalone(getCut()); }

    // ------------------------------------------------------------------ behaviour overrides
    @Override public boolean isPushable() { return false; }
    @Override public void knockback(double s, double x, double z) { }
    @Override public boolean canBeAffected(MobEffectInstance e) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public void checkDespawn() { }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 300.0 * 300.0; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(200.0, 130.0, 200.0); }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bar.removePlayer(p);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", getPhase());
        t.putBoolean("Awake", isAwake());
        t.putFloat("Hatred", getHatred());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(D_PHASE, Math.max(1, t.getInt("Phase")));
        entityData.set(D_AWAKE, t.getBoolean("Awake"));
        setHatred(t.getFloat("Hatred"));
        if (hasCustomName()) {
            bar.setName(getDisplayName());
        }
    }

    // ------------------------------------------------------------------ damage
    public float reduction() {
        int g = graveCount();
        if (g >= 2) {
            return 0f;
        }
        if (isResist()) {
            return IrontombBalance.RESIST_REDUCTION;
        }
        return IrontombBalance.BASE_REDUCTION * (g == 1 ? 0.5f : 1f);
    }

    @Override
    public boolean hurt(DamageSource s, float amt) {
        if (level().isClientSide) {
            return false;
        }
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(s, amt);
        }
        return applyShared(s, amt);
    }

    /** Re-applies max health and armor from the config to this boss (keeps the same health fraction). */
    private int cfgVersion = -1;

    private float guardHp = -1f;

    public void applyConfig() {
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            float frac = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1f;
            hp.setBaseValue(IrontombBalance.NORM);
            setHealth(Math.max(1f, frac * getMaxHealth()));
        }
        var md = getAttribute(com.example.jjkspells.ModAttributes.MAX_HP_DAMAGE.get());
        if (md != null) {
            md.setBaseValue(IrontombBalance.MAX_HP_DAMAGE);
        }
        var mr = getAttribute(com.example.jjkspells.ModAttributes.MAX_HP_RESIST.get());
        if (mr != null) {
            mr.setBaseValue(IrontombBalance.MAX_HP_RESIST);
        }
        var ar = getAttribute(Attributes.ARMOR);
        if (ar != null) {
            ar.setBaseValue(IrontombBalance.ARMOR);
        }
        var tg = getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (tg != null) {
            tg.setBaseValue(IrontombBalance.ARMOR_TOUGHNESS);
        }
    }

    /** Damage that reached the boss directly or through a satellite (Thirty Million Cycles of Sin). */
    public boolean applyShared(DamageSource s, float amt) {
        if (!isAwake() || cutStandalone() || isResist() || isDeadOrDying()) {
            return false;
        }
        // amt is real damage; the entity only carries NORM health points, so convert against the configured pool
        double real = amt * (1.0 - reduction());
        double pool = Math.max(1.0, IrontombBalance.MAX_HEALTH);
        real = Math.min(real, IrontombBalance.MAX_HIT_FRACTION * pool);
        float units = (float) (real * IrontombBalance.NORM / pool);
        if (units <= 0f) {
            return false;
        }
        this.invulnerableTime = 0;
        return super.hurt(s, units);
    }

    @Override
    public void die(DamageSource s) {
        super.die(s);
        if (!level().isClientSide) {
            unlockAll();
            entityData.set(D_ATTACK, 0);
            entityData.set(D_RESIST, false);
            setCut(IrontombCut.DEATH);
            for (IrontombSatellite sat : satellites()) {
                sat.discard();
            }
        }
    }

    /** Death plays the DEATH cutscene instead of the vanilla fall-over. */
    @Override
    protected void tickDeath() {
        if (level().isClientSide) {
            return;
        }
        int t = getCutT() + 1;
        entityData.set(D_CUT_T, t);
        setDeltaMovement(Vec3.ZERO);
        if (t == 20) {
            sound(SoundEvents.WITHER_DEATH, 4f, 0.6f);
            sound(IrontombSounds.ROAR, 14f, 0.6f);
        }
        if (t == 100) {
            sound(SoundEvents.GENERIC_EXPLODE, 4f, 0.5f);
        }
        if (t >= IrontombCut.LENGTH[IrontombCut.DEATH]) {
            if (level() instanceof ServerLevel sl) {
                net.minecraft.world.entity.ExperienceOrb.award(sl, position(), xpReward);
            }
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            bar.removeAllPlayers();
            for (Player p : level().players()) {
                clearErudition(p);
                IronsCompat.unlock(p);
            }
            for (IrontombSatellite sat : satellites()) {
                sat.discard();
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ main tick
    @Override
    public net.minecraft.network.chat.Component getTypeName() {
        return net.minecraft.network.chat.Component.literal("Irontomb");
    }

    @Override
    public void tick() {
        super.tick();
        updateParts();
        if (level().isClientSide || isDeadOrDying()) {
            return;
        }
        faceTick();
        if (cfgVersion != IrontombBalance.VERSION) {
            cfgVersion = IrontombBalance.VERSION;
            applyConfig();
        }
        setDeltaMovement(Vec3.ZERO);
        // burst guard: caps the health lost per tick (also catches percent-health spells that never reach hurt())
        float floor = guardHp - (float) (IrontombBalance.BURST_LOSS_PER_TICK * IrontombBalance.NORM);
        if (isAwake() && guardHp > 0f && getHealth() < floor && getHealth() > 0f) {
            setHealth(Math.max(1f, floor));
        }
        guardHp = getHealth();
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
        if (tickCount % 10 == 0) {
            // tooltip mods (Jade, WTHIT...) only see the normalised 1000 health points, so the real numbers go into the name
            double pool = Math.max(1.0, IrontombBalance.MAX_HEALTH);
            double cur = Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f) * pool;
            String hp = fmt(cur) + " / " + fmt(pool) + " HP";
            Component nm = Component.literal("Irontomb  [" + hp + "]");
            if (!nm.equals(getCustomName())) {
                setCustomName(nm);
                setCustomNameVisible(false);
            }
            bar.setName(Component.literal("Irontomb, Anti-Nous, Funeral of Gnosis  [" + hp + "]"));
        }
        bar.setVisible(isAwake());

        if (!isAwake()) {
            // a sleeping Irontomb cannot be hurt at all, not even by percent-health spells that skip hurt()
            if (getHealth() < getMaxHealth()) {
                setHealth(getMaxHealth());
            }
            List<LivingEntity> near = foes(48);
            if (!near.isEmpty()) {
                // Phainon gate: it only wakes for someone who has already slain Phainon
                boolean worthy = !IrontombBalance.PH_REQUIRE;
                for (LivingEntity le : near) {
                    if (le instanceof Player pl && PhainonGate.unlocked(pl)) {
                        worthy = true;
                        break;
                    }
                }
                if (worthy) {
                    entityData.set(D_AWAKE, true);
                    ensureSatellites();
                    setCut(IrontombCut.INTRO);
                } else if (tickCount % 80 == 0) {
                    for (LivingEntity le : near) {
                        if (le instanceof Player pl) {
                            pl.displayClientMessage(Component.literal("§6Irontomb does not stir. It will not wake for those who have not slain Phainon."), true);
                        }
                    }
                }
            }
            return;
        }
        if (tickCount % 20 == 0) {
            ensureSatellites();
        }

        tickLock();
        if (tickCount % 10 == 0) {
            tickErudition();
        }

        int both = graveCount();
        if (both >= 2 && !wasBothGrave && !isSpecialAttack() && !cutStandalone()) {
            startAttack(IrontombAttack.STAGGER, null);
        }
        wasBothGrave = both >= 2;

        if (getCut() != 0 && cutStandalone()) {
            tickCutscene();
            return;
        }

        // a full hatred bar unleashes the special of the current phase
        if (!isSpecialAttack() && !isErasing() && getHatred() >= 99.9f) {
            if (getPhase() >= 3) {
                beginErasure();
            } else {
                startAttack(getPhase() == 2 ? IrontombAttack.SINGULARITY : IrontombAttack.GENESIS, null);
            }
        } else if (!isSpecialAttack() && !isErasing()) {
            setHatred(getHatred() + IrontombBalance.HATRED_PASSIVE);
        }
        if (isErasing()) {
            tickErasure();
        }

        // phase changes
        float frac = getHealth() / getMaxHealth();
        if (getPhase() == 1 && frac <= IrontombBalance.PHASE2_AT) {
            enterPhase(2);
            return;
        }
        if (getPhase() == 2 && frac <= IrontombBalance.PHASE3_AT) {
            enterPhase(3);
            return;
        }

        if (getAttack() == IrontombAttack.NONE) {
            if (cooldown > 0) {
                cooldown--;
            }
            if (getPhase() >= 3) {
                resistCd--;
            }
            if (cooldown <= 0 && !foes(90).isEmpty()) {
                chooseAttack();
            }
        } else {
            int t = getAtkT() + 1;
            entityData.set(D_ATK_T, t);
            if (IrontombCut.standalone(getCut()) == false && getCut() != 0) {
                entityData.set(D_CUT_T, t);
            }
            int atk = getAttack();
            double sc = IrontombAttack.scale(atk);
            if (sc <= 1.0001) {
                runAttack(atk, t);
            } else {
                // compressed attack: step through every authored tick this real tick covers, so no event is skipped
                int len = IrontombAttack.LENGTH[atk];
                int from = (int) Math.floor((t - 1) * sc) + 1;
                int to = t >= len ? IrontombAttack.AUTH_LENGTH[atk] : (int) Math.floor(t * sc);
                for (int ta = from; ta <= to; ta++) {
                    runAttack(atk, ta);
                }
            }
            if (t >= IrontombAttack.LENGTH[getAttack()]) {
                endAttack();
            }
        }
    }

    // ------------------------------------------------------------------ cutscenes
    private void setCut(int id) {
        entityData.set(D_CUT, id);
        entityData.set(D_CUT_T, 0);
    }

    public void forceCutscene(int id) {
        wake();
        entityData.set(D_ATTACK, 0);
        entityData.set(D_RESIST, false);
        setCut(id);
        if (id == IrontombCut.IMMOLATE) {
            startAttack(IrontombAttack.IMMOLATE, null);
        } else if (id == IrontombCut.GENESIS) {
            startAttack(IrontombAttack.GENESIS, null);
        } else if (id == IrontombCut.SINGULARITY) {
            startAttack(IrontombAttack.SINGULARITY, null);
        } else if (id == IrontombCut.ERASURE) {
            startAttack(IrontombAttack.ERASURE, null);
        }
    }

    private void tickCutscene() {
        int id = getCut();
        int t = getCutT() + 1;
        entityData.set(D_CUT_T, t);
        if (id == IrontombCut.INTRO) {
            if (t == 10) sound(SoundEvents.BEACON_ACTIVATE, 5f, 0.5f);
            if (t == 100) sound(SoundEvents.WITHER_SPAWN, 5f, 0.6f);
            if (t == 100) sound(IrontombSounds.ROAR, 16f, 0.7f);
            if (t == 118) sound(SoundEvents.END_PORTAL_SPAWN, 5f, 0.5f);
        } else if (id == IrontombCut.PHASE2 || id == IrontombCut.PHASE3) {
            if (t == 5) sound(SoundEvents.ENDER_DRAGON_GROWL, 5f, 0.5f);
            if (t == 5) sound(IrontombSounds.ROAR, 14f, 0.75f);
            if (t == 60) {
                sound(SoundEvents.GENERIC_EXPLODE, 5f, 0.5f);
                for (IrontombSatellite s : satellites()) {
                    s.reforge(getPhase());
                }
                setHatred(Math.max(0f, getHatred() - 15f));
            }
        }
        if (t >= IrontombCut.LENGTH[id]) {
            entityData.set(D_CUT, 0);
            entityData.set(D_CUT_T, 0);
            cooldown = 50;
        }
    }

    private void enterPhase(int n) {
        entityData.set(D_PHASE, n);
        entityData.set(D_ATTACK, 0);
        entityData.set(D_RESIST, false);
        queued = 0;
        resistCd = 600;
        unlockAll();
        setCut(n == 2 ? IrontombCut.PHASE2 : IrontombCut.PHASE3);
    }

    public void forcePhase(int n) {
        wake();
        float f = n == 1 ? 1f : n == 2 ? 0.65f : 0.32f;
        setHealth(getMaxHealth() * f);
        guardHp = getHealth();
        entityData.set(D_PHASE, n);
        if (n > 1) {
            setCut(n == 2 ? IrontombCut.PHASE2 : IrontombCut.PHASE3);
        }
        for (IrontombSatellite s : satellites()) {
            s.reforge(n);
        }
    }

    private void wake() {
        if (!isAwake()) {
            entityData.set(D_AWAKE, true);
            ensureSatellites();
        }
    }

    // ------------------------------------------------------------------ attack selection
    private void chooseAttack() {
        if (queued != 0) {
            int a = queued;
            Entity t = queuedTarget >= 0 ? level().getEntity(queuedTarget) : null;
            LivingEntity p = t instanceof LivingEntity pl && pl.isAlive() && isFoe(pl) ? pl : null;
            clearQueue();
            startAttack(a, p);
            return;
        }
        if (getPhase() >= 3 && resistCd <= 0) {
            resistCd = 900;
            startAttack(IrontombAttack.RESIST, null);
            return;
        }
        int r = random.nextInt(100);
        int a;
        if (getPhase() == 1) {
            a = r < 50 ? IrontombAttack.CAST_DOWN : IrontombAttack.WEAVE;
        } else if (getPhase() == 2) {
            a = r < 40 ? IrontombAttack.CAST_DOWN : r < 80 ? IrontombAttack.WEAVE : IrontombAttack.CROWN;
        } else {
            a = r < 35 ? IrontombAttack.CAST_DOWN : r < 70 ? IrontombAttack.WEAVE : IrontombAttack.CROWN;
        }
        startAttack(a, null);
    }

    public void forceAttack(int id) {
        wake();
        entityData.set(D_CUT, 0);
        clearQueue();
        startAttack(id, null);
    }

    private void clearQueue() {
        queued = 0;
        queuedTarget = -1;
        queuedFrom = -1;
        for (IrontombSatellite s : satellites()) {
            s.clearMark();
        }
    }

    /** Called by a satellite that has locked on: the boss follows up on its next action. */
    public void queueFromSatellite(int satType, int targetId) {
        if (queued != 0) {
            return;
        }
        boolean p1 = getPhase() == 1;
        queued = satType == 0 ? (p1 ? IrontombAttack.YIELD : IrontombAttack.CROWN) : (p1 ? IrontombAttack.MIRE : IrontombAttack.PRISON);
        queuedTarget = targetId;
        queuedFrom = satType;
    }

    public boolean hasQueued() { return queued != 0; }

    private void startAttack(int id, LivingEntity target) {
        entityData.set(D_ATTACK, id);
        entityData.set(D_ATK_T, 0);
        if (target == null) {
            List<LivingEntity> l = foes(90);
            if (!l.isEmpty()) {
                target = l.get(random.nextInt(l.size()));
            }
        }
        if (target != null) {
            entityData.set(D_TARGET, target.getId());
            setAim(target.position());
        } else {
            entityData.set(D_TARGET, -1);
            setAim(position());
        }
        if (id == IrontombAttack.IMMOLATE) {
            setCut(IrontombCut.IMMOLATE);
        } else if (id == IrontombAttack.GENESIS) {
            setCut(IrontombCut.GENESIS);
        } else if (id == IrontombAttack.SINGULARITY) {
            setCut(IrontombCut.SINGULARITY);
        } else if (id == IrontombAttack.ERASURE) {
            setCut(IrontombCut.ERASURE);
            entityData.set(D_ERASE, 0);
        } else if (!cutStandalone()) {
            entityData.set(D_CUT, 0);
        }
        if (id == IrontombAttack.RESIST) {
            entityData.set(D_RESIST, true);
            for (IrontombSatellite s : satellites()) {
                s.reforge(getPhase());
            }
        } else if (id != IrontombAttack.STAGGER) {
            entityData.set(D_RESIST, false);
        }
        if (id == IrontombAttack.STAGGER) {
            entityData.set(D_RESIST, false);
            sound(SoundEvents.ANVIL_LAND, 5f, 0.4f);
        }
    }

    private void endAttack() {
        int a = getAttack();
        entityData.set(D_ATTACK, 0);
        entityData.set(D_ATK_T, 0);
        entityData.set(D_RESIST, false);
        if (getCut() == IrontombCut.IMMOLATE || getCut() == IrontombCut.GENESIS || getCut() == IrontombCut.SINGULARITY || getCut() == IrontombCut.ERASURE) {
            entityData.set(D_CUT, 0);
            entityData.set(D_CUT_T, 0);
        }
        cooldown = (getPhase() == 1 ? 55 : getPhase() == 2 ? 42 : 32) + random.nextInt(20);
        if (a == IrontombAttack.RESIST) {
            startAttack(IrontombAttack.IMMOLATE, null);
        } else if (a == IrontombAttack.GENESIS) {
            setHatred(0f);
            cooldown = 200;
        } else if (a == IrontombAttack.SINGULARITY) {
            setHatred(0f);
            cooldown = 240;
        } else if (a == IrontombAttack.ERASURE) {
            setHatred(0f);
            cooldown = 300;
        } else if (a == IrontombAttack.STAGGER) {
            cooldown = 40;
        }
    }

    // ------------------------------------------------------------------ the attacks
    private void runAttack(int atk, int t) {
        Entity te = level().getEntity(getTargetId());
        LivingEntity target = te instanceof LivingEntity le && le.isAlive() ? le : null;
        switch (atk) {
            case IrontombAttack.CAST_DOWN -> {
                if (target != null && t < IrontombAttack.CAST_DOWN_FREEZE) {
                    setAim(target.position());
                }
                if (t == 4) sound(SoundEvents.WARDEN_ATTACK_IMPACT, 4f, 0.5f);
                if (t == 1) sound(IrontombSounds.CHARGE, 6f, 1.8f);
                if (t == IrontombAttack.CAST_DOWN_HIT) {
                    Vec3 a = getAim();
                    impact(a, IrontombAttack.CAST_DOWN_RADIUS, IrontombBalance.CAST_DOWN, 1f, IrontombFxServer.RED);
                    addHatred(3f);
                }
            }
            case IrontombAttack.WEAVE -> {
                if (t == 8) sound(SoundEvents.ENDER_DRAGON_GROWL, 4f, 0.7f);
                if (t == 2) sound(IrontombSounds.CHARGE, 6f, 1.8f);
                for (int i = 0; i < 3; i++) {
                    if (t == IrontombAttack.WEAVE_TICKS[i]) {
                        weaveRing(IrontombAttack.WEAVE_RADII[i]);
                    }
                }
                if (t == IrontombAttack.WEAVE_TICKS[2]) {
                    addHatred(4f);
                }
            }
            case IrontombAttack.YIELD -> {
                if (target != null && t < 30) {
                    setAim(target.position());
                }
                if (t == 6) sound(SoundEvents.BEACON_POWER_SELECT, 4f, 0.6f);
                if (t == 4) sound(IrontombSounds.CHARGE, 6f, 1.5f);
                if (t == IrontombAttack.YIELD_HIT && target != null) {
                    sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 1.4f);
                    soundAt(target.position(), IrontombSounds.BOLT, 8f, 0.8f);
                    soundAt(target.position(), IrontombSounds.IMPACT, 6f, 1.3f);
                    for (LivingEntity p : areaPlayers(target, IrontombAttack.YIELD_RADIUS)) {
                        hitOne(p, IrontombBalance.YIELD, IrontombFxServer.VIOLET);
                        if (p instanceof ServerPlayer sp) {
                            IronsCompat.addMana(sp, -IrontombBalance.MANA_DRAIN_YIELD);
                        }
                    }
                    addHatred(4f);
                }
            }
            case IrontombAttack.MIRE -> {
                if (target != null && t < 30) {
                    setAim(target.position());
                }
                if (t == IrontombAttack.MIRE_HIT && target != null) {
                    sound(SoundEvents.BELL_RESONATE, 4f, 0.6f);
                    soundAt(target.position(), IrontombSounds.BELL, 8f, 0.9f);
                    for (LivingEntity p : areaPlayers(target, IrontombAttack.MIRE_RADIUS)) {
                        hitOne(p, IrontombBalance.MIRE, IrontombFxServer.GOLD);
                    }
                    lockPlayer(target, IrontombAttack.MIRE_LOCK);
                    addHatred(4f);
                }
            }
            case IrontombAttack.CROWN -> {
                if (t == 10) sound(SoundEvents.END_PORTAL_SPAWN, 5f, 0.5f);
                if (t == 24) sound(IrontombSounds.CHARGE, 9f, 1.0f);
                if (t == 60) sound(SoundEvents.BEACON_DEACTIVATE, 5f, 0.5f);
                if (t == IrontombAttack.CROWN_HIT) {
                    sound(SoundEvents.GENERIC_EXPLODE, 6f, 0.5f);
                    sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 6f, 0.6f);
                    sound(IrontombSounds.SHATTER, 12f, 0.9f);
                    sound(IrontombSounds.IMPACT, 12f, 0.8f);
                    for (LivingEntity p : foes(IrontombAttack.CROWN_RADIUS)) {
                        dmg(p, IrontombBalance.CROWN, 1f);
                        if (p instanceof ServerPlayer sp) {
                            IronsCompat.addMana(sp, -IrontombBalance.MANA_DRAIN_CROWN);
                        }
                        if (level() instanceof ServerLevel sl) {
                            IrontombFxServer.burst(sl, p.position().add(0, 1, 0), IrontombFxServer.VIOLET, 40, 0.6, 0.15);
                        }
                    }
                    addHatred(6f);
                }
            }
            case IrontombAttack.PRISON -> {
                if (target != null && t < IrontombAttack.PRISON_FREEZE) {
                    setAim(target.position());
                }
                if (target != null && t >= IrontombAttack.PRISON_FREEZE && t < IrontombAttack.PRISON_HIT) {
                    holdStill(target);
                }
                if (t == 1) sound(IrontombSounds.CHARGE, 6f, 1.8f);
                if (t == IrontombAttack.PRISON_FREEZE) sound(SoundEvents.IRON_DOOR_CLOSE, 5f, 0.4f);
                if (t == IrontombAttack.PRISON_HIT && target != null) {
                    sound(SoundEvents.GENERIC_EXPLODE, 5f, 0.7f);
                    soundAt(target.position(), IrontombSounds.IMPACT, 8f, 0.8f);
                    soundAt(target.position(), IrontombSounds.SHATTER, 6f, 1.3f);
                    for (LivingEntity p : areaPlayers(target, IrontombAttack.PRISON_RADIUS)) {
                        hitOne(p, IrontombBalance.PRISON, IrontombFxServer.GOLD);
                    }
                    lockPlayer(target, IrontombAttack.PRISON_LOCK);
                    addHatred(6f);
                }
            }
            case IrontombAttack.IMMOLATE -> {
                if (t == 4) sound(SoundEvents.ENDER_DRAGON_GROWL, 6f, 0.4f);
                if (t == 4) sound(IrontombSounds.ROAR, 12f, 0.8f);
                if (t == 90) sound(IrontombSounds.CHARGE, 14f, 1.0f);
                if (t == 60) sound(SoundEvents.BEACON_ACTIVATE, 6f, 0.4f);
                if (t == 100) sound(SoundEvents.WITHER_SPAWN, 6f, 0.5f);
                if (t == IrontombAttack.IMMOLATE_HIT) {
                    sound(SoundEvents.GENERIC_EXPLODE, 8f, 0.4f);
                    sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.5f);
                    sound(IrontombSounds.SHATTER, 16f, 0.75f);
                    sound(IrontombSounds.IMPACT, 16f, 0.65f);
                    boolean mythosDown = isGrave(1), logosDown = isGrave(0);
                    List<LivingEntity> list = foes(IrontombAttack.IMMOLATE_RADIUS);
                    for (LivingEntity p : list) {
                        dmg(p, IrontombBalance.IMMOLATE, 1f);
                        p.setSecondsOnFire(6);
                        if (!logosDown && p instanceof ServerPlayer sp) {
                            IronsCompat.addMana(sp, -IrontombBalance.MANA_DRAIN_IMMOLATE);
                        }
                    }
                    if (!mythosDown && !list.isEmpty()) {
                        lockPlayer(list.get(random.nextInt(list.size())), 140);
                    }
                    addHatred(8f);
                }
            }
            case IrontombAttack.GENESIS -> {
                if (t == 4) sound(SoundEvents.ENDER_DRAGON_GROWL, 8f, 0.3f);
                if (t == 4) sound(IrontombSounds.ROAR, 16f, 0.65f);
                if (t == IrontombAttack.GENESIS_HIT - (int) (109 * IrontombAttack.scale(IrontombAttack.GENESIS))) sound(IrontombSounds.GENESIS, 20f, 1.0f);
                if (t == 60) sound(SoundEvents.END_PORTAL_SPAWN, 8f, 0.4f);
                if (t == 150) sound(SoundEvents.WITHER_SPAWN, 8f, 0.4f);
                if (t == IrontombAttack.GENESIS_HIT) {
                    sound(SoundEvents.GENERIC_EXPLODE, 10f, 0.3f);
                    sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 10f, 0.4f);
                    for (LivingEntity p : foes(IrontombAttack.GENESIS_RADIUS)) {
                        dmg(p, IrontombBalance.GENESIS, 1f);
                        if (IrontombBalance.GENESIS_FORCED_KILL && p.isAlive() && !(p instanceof Player pp && (pp.isCreative() || pp.isSpectator()))) {
                            var gk = level().damageSources().genericKill();
                            p.setHealth(0f);
                            p.die(gk);
                        }
                    }
                }
            }
            case IrontombAttack.SINGULARITY -> runSingularity(t);
            case IrontombAttack.ERASURE -> runErasure(t);
            case IrontombAttack.RESIST -> {
                if (t == 3) sound(SoundEvents.ENDER_DRAGON_GROWL, 6f, 0.5f);
                if (t == 3) sound(IrontombSounds.ROAR, 12f, 0.9f);
                if (t == 8) sound(SoundEvents.BEACON_ACTIVATE, 6f, 0.5f);
                if (getAtkT() > 12 && graveCount() >= 2) {
                    // Resist and Destroy is dispelled: both satellites are in Grave Wound at once
                    sound(SoundEvents.GLASS_BREAK, 6f, 0.5f);
                    entityData.set(D_RESIST, false);
                    entityData.set(D_ATTACK, 0);
                    entityData.set(D_ATK_T, 0);
                    cooldown = 40;
                    startAttack(IrontombAttack.STAGGER, null);
                }
            }
            case IrontombAttack.STAGGER -> { }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ specials: phase 2 Singularity, phase 3 Erasure
    /** Where reality is pressed together: between the boss' hands, in front of it (body frame 0, 31, 72). */
    public Vec3 sphereCenter() {
        double yaw = -Math.toRadians(getYRot());
        double oz = 72.0;
        return new Vec3(getX() + oz * Math.sin(yaw), getY() + 31.0, getZ() + oz * Math.cos(yaw));
    }

    /** Every entity of this level that the specials act on (never the boss, its satellites, hit boxes or spectators). */
    private List<Entity> everything(ServerLevel sl) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : sl.getAllEntities()) {
            if (e == this || e instanceof IrontombEntity || e instanceof IrontombSatellite || e instanceof net.minecraftforge.entity.PartEntity<?> || !e.isAlive()) {
                continue;
            }
            if (e instanceof Player p && p.isSpectator()) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** A stable pseudo-random point inside a sphere of radius r, different for each entity. */
    private static Vec3 scatter(Entity e, double r) {
        long h = e.getUUID().getLeastSignificantBits() * 0x9E3779B97F4A7C15L;
        double a = ((h >>> 11) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        double b = ((h >>> 27) & 0xFFFF) / 65535.0 * 2.0 - 1.0;
        double c = ((h >>> 43) & 0xFFFFF) / 1048575.0;
        double s = Math.sqrt(1.0 - b * b), d = Math.cbrt(c) * r;
        return new Vec3(Math.cos(a) * s * d, b * d, Math.sin(a) * s * d);
    }

    /** Drags entities a fraction k of the way to their own spot around target (all of them, or only players). */
    private void gather(ServerLevel sl, Vec3 target, double k, double spread, boolean onlyPlayers) {
        boolean step = (tickCount & 1) == 0;
        for (Entity e : everything(sl)) {
            if (e.isPassenger() || onlyPlayers && !(e instanceof Player)) {
                continue;
            }
            Vec3 goal = target.add(scatter(e, spread));
            Vec3 p = e.position();
            e.fallDistance = 0f;
            if (e instanceof ServerPlayer sp) {
                if (step) {
                    double kk = 1.0 - (1.0 - k) * (1.0 - k);
                    Vec3 np = p.add(goal.subtract(p).scale(kk));
                    double dx = target.x - np.x, dz = target.z - np.z, dy = target.y - np.y;
                    float yaw = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
                    float pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
                    sp.connection.teleport(np.x, np.y, np.z, yaw, pitch);
                    sp.setDeltaMovement(Vec3.ZERO);
                }
            } else {
                Vec3 np = p.add(goal.subtract(p).scale(k));
                e.setDeltaMovement(Vec3.ZERO);
                e.moveTo(np.x, np.y, np.z, e.getYRot(), e.getXRot());
            }
        }
    }

    private void broadcast(ServerLevel sl, String msg) {
        for (ServerPlayer p : sl.players()) {
            p.displayClientMessage(Component.literal(msg), true);
        }
    }

    private void runSingularity(int t) {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        if (t == 3) sound(IrontombSounds.ROAR, 16f, 0.6f);
        if (t == 30) sound(IrontombSounds.CHARGE, 20f, 0.7f);
        if (t == 60) sound(IrontombSounds.BELL, 20f, 0.5f);
        if (t == IrontombAttack.SINGULARITY_HIT - (int) (140 * IrontombAttack.scale(IrontombAttack.SINGULARITY))) sound(IrontombSounds.SUCK, 24f, 1.0f);
        if (t == IrontombAttack.SINGULARITY_PULL) {
            sound(SoundEvents.END_PORTAL_SPAWN, 16f, 0.4f);
            broadcast(sl, "All of reality is being pulled toward Irontomb...");
        }
        if (t >= IrontombAttack.SINGULARITY_PULL && t < IrontombAttack.SINGULARITY_HIT) {
            float k = Fx_ramp(t, IrontombAttack.SINGULARITY_PULL + 5, 220);
            double pull = 0.02 + 0.30 * k * k;
            // the cluster tightens while the hands close
            double spread = 17.0 - 13.0 * Fx_ramp(t, 230, 326);
            gather(sl, sphereCenter(), pull, spread, false);
        }
        if (t == IrontombAttack.SINGULARITY_HIT - 4) {
            sound(IrontombSounds.GRAVE, 24f, 0.4f);
        }
        if (t == IrontombAttack.SINGULARITY_HIT) {
            sound(SoundEvents.GENERIC_EXPLODE, 20f, 0.3f);
            sound(IrontombSounds.SHATTER, 24f, 0.55f);
            sound(IrontombSounds.IMPACT, 24f, 0.45f);
            sound(IrontombSounds.ROAR, 24f, 0.5f);
            blastAll(sl);
            addHatred(0f);
        }
    }

    private static float Fx_ramp(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    /** The crush: every entity in the level takes the Singularity damage, anything that is not alive is destroyed. */
    private void blastAll(ServerLevel sl) {
        var gk = sl.damageSources().genericKill();
        for (Entity e : everything(sl)) {
            if (e instanceof LivingEntity le) {
                le.invulnerableTime = 0;
                IrontombDamage.deal(this, this, le, IrontombBalance.SINGULARITY, 1f);
                if (le.isAlive()) {
                    le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
                }
            } else if (!(e instanceof Player)) {
                e.hurt(gk, 1.0e9f);
            }
        }
    }

    /** Phase 3 at full hatred: the Sword of Erasure starts to form. The players have the countdown to put both satellites in Grave Wound at once. */
    private void beginErasure() {
        entityData.set(D_ERASE, IrontombBalance.ERASURE_COUNTDOWN);
        if (level() instanceof ServerLevel sl) {
            sound(IrontombSounds.ROAR, 20f, 0.5f);
            sound(IrontombSounds.BELL, 20f, 0.4f);
            broadcast(sl, "The Sword of Erasure is forming! Break BOTH satellites into Grave Wound at the same time!");
        }
    }

    private void tickErasure() {
        int left = getErase() - 1;
        if (graveCount() >= 2) {
            // the seal holds: both satellites are wounded together
            entityData.set(D_ERASE, 0);
            setHatred(IrontombBalance.ERASURE_RESET_HATRED);
            sound(SoundEvents.GLASS_BREAK, 16f, 0.4f);
            sound(IrontombSounds.SHATTER, 20f, 0.8f);
            if (level() instanceof ServerLevel sl) {
                broadcast(sl, "The Sword of Erasure shatters!");
            }
            return;
        }
        if (left <= 0) {
            entityData.set(D_ERASE, 0);
            startAttack(IrontombAttack.ERASURE, null);
            return;
        }
        entityData.set(D_ERASE, left);
        if (left % 200 == 0 && level() instanceof ServerLevel sl) {
            broadcast(sl, "Erasure in " + (left / 20) + "s - both satellites must be in Grave Wound together!");
            sound(IrontombSounds.BELL, 16f, 0.6f);
        }
    }

    private void runErasure(int t) {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        if (t == 3) sound(IrontombSounds.ROAR, 20f, 0.45f);
        if (t == 40) sound(IrontombSounds.BELL, 20f, 0.4f);
        if (t == IrontombAttack.ERASURE_HIT - (int) (160 * IrontombAttack.scale(IrontombAttack.ERASURE))) sound(IrontombSounds.ERASE, 28f, 1.0f);
        // everyone is drawn to a viewing spot in front of the boss, so that every player sees it
        if (t >= 10 && t < IrontombAttack.ERASURE_HIT) {
            double yaw = -Math.toRadians(getYRot());
            Vec3 view = new Vec3(getX() + 130.0 * Math.sin(yaw), getY() + 6.0, getZ() + 130.0 * Math.cos(yaw));
            double k = 0.02 + 0.18 * Fx_ramp(t, 10, 130);
            gather(sl, view, k, 26.0, true);
        }
        int n = IrontombBalance.ERASURE_KILLS;
        if (t >= IrontombAttack.ERASURE_HIT && t < IrontombAttack.ERASURE_HIT + 2 * n && (t - IrontombAttack.ERASURE_HIT) % 2 == 0) {
            int i = (t - IrontombAttack.ERASURE_HIT) / 2;
            if (i == 0) {
                sound(SoundEvents.GENERIC_EXPLODE, 28f, 0.25f);
                sound(IrontombSounds.SHATTER, 28f, 0.5f);
            }
            sound(IrontombSounds.IMPACT, 28f, 0.35f + 0.04f * i);
            if (IrontombBalance.ERASURE_KILL) {
                eraseEverything();
            } else if (i == 0) {
                blastAll(sl);
            }
        }
    }

    /** One pass of  /kill @e  as the server: everything loaded in every dimension dies (the boss and its satellites are spared unless configured). */
    private void eraseEverything() {
        net.minecraft.server.MinecraftServer srv = level().getServer();
        if (srv == null) {
            return;
        }
        String sel = IrontombBalance.ERASURE_KILLS_BOSS ? "@e" : "@e[type=!jjk_spells:irontomb,type=!jjk_spells:irontomb_satellite]";
        try {
            srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), "kill " + sel);
        } catch (RuntimeException ex) {
            // never let the command break the fight loop
        }
    }

    private void weaveRing(double r) {
        sound(SoundEvents.ANVIL_LAND, 4f, 0.5f);
        sound(IrontombSounds.SLASH, 8f, r < 20 ? 0.95f : r < 35 ? 0.8f : 0.65f);
        sound(IrontombSounds.IMPACT, 6f, r < 20 ? 1.5f : r < 35 ? 1.3f : 1.1f);
        Vec3 c = position();
        for (LivingEntity p : foes(r + 6)) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - r) <= IrontombAttack.WEAVE_HALF_WIDTH && Math.abs(p.getY() - c.y) < 5.0) {
                dmg(p, IrontombBalance.WEAVE, 1f);
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.9, 0));
                p.hurtMarked = true;
            }
        }
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.ring(sl, IrontombFxServer.dust(IrontombFxServer.RED, 2.4f), c, r, (int) (r * 6), 0.0);
        }
    }

    private void impact(Vec3 a, double radius, float[] f, float mult, org.joml.Vector3f color) {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        sound(SoundEvents.GENERIC_EXPLODE, 5f, 0.6f);
        soundAt(a, IrontombSounds.IMPACT, 7f, 1.0f);
        IrontombFxServer.burst(sl, a.add(0, 1, 0), color, 90, 1.6, 0.35);
        for (LivingEntity p : foes(90)) {
            double dx = p.getX() - a.x, dz = p.getZ() - a.z;
            if (dx * dx + dz * dz <= radius * radius && Math.abs(p.getY() - a.y) < 4) {
                dmg(p, f, mult);
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.7, 0));
                p.hurtMarked = true;
            }
        }
    }

    /** Every living player within radius blocks (horizontally) of the target, the target included. */
    private List<LivingEntity> areaPlayers(LivingEntity center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity p : foes(120)) {
            double dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
            if (p == center || (dx * dx + dz * dz <= radius * radius && Math.abs(p.getY() - center.getY()) < 6)) {
                out.add(p);
            }
        }
        return out;
    }

    private void hitOne(LivingEntity v, float[] f, org.joml.Vector3f color) {
        dmg(v, f, 1f);
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.burst(sl, v.position().add(0, 1, 0), color, 60, 0.8, 0.25);
        }
    }

    private void dmg(LivingEntity v, float[] f, float mult) {
        IrontombDamage.deal(this, this, v, f, mult);
    }

    // ------------------------------------------------------------------ action lock
    private void lockPlayer(LivingEntity v, int ticks) {
        soundAt(v.position(), IrontombSounds.LOCK, 7f, 1.0f);
        IronsCompat.lock(v, ticks);
        entityData.set(D_LOCK_ID, v.getId());
        entityData.set(D_LOCK_LEFT, ticks);
    }

    private void tickLock() {
        int left = getLockLeft();
        if (left <= 0) {
            return;
        }
        Entity e = level().getEntity(getLockId());
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            entityData.set(D_LOCK_LEFT, 0);
            return;
        }
        holdStill(le);
        entityData.set(D_LOCK_LEFT, left - 1);
        if (left - 1 <= 0) {
            IronsCompat.unlock(le);
        }
    }

    private void holdStill(LivingEntity v) {
        v.setSprinting(false);
        Vec3 m = v.getDeltaMovement();
        v.setDeltaMovement(0, Math.min(m.y, 0), 0);
        v.hurtMarked = true;
    }

    public void unlockAll() {
        entityData.set(D_LOCK_LEFT, 0);
        for (Player p : level().players()) {
            IronsCompat.unlock(p);
        }
    }

    // ------------------------------------------------------------------ satellites
    public List<IrontombSatellite> satellites() {
        List<IrontombSatellite> out = new ArrayList<>(2);
        if (level() instanceof ServerLevel sl) {
            for (UUID id : new UUID[]{logosId, mythosId}) {
                if (id != null && sl.getEntity(id) instanceof IrontombSatellite s && s.isAlive()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public boolean isGrave(int type) {
        for (IrontombSatellite s : satellites()) {
            if (s.getSatType() == type && s.isGrave()) {
                return true;
            }
        }
        return false;
    }

    public int graveCount() {
        int n = 0;
        for (IrontombSatellite s : satellites()) {
            if (s.isGrave()) {
                n++;
            }
        }
        return n;
    }

    private void ensureSatellites() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        boolean hasL = logosId != null && sl.getEntity(logosId) instanceof IrontombSatellite l && l.isAlive();
        boolean hasM = mythosId != null && sl.getEntity(mythosId) instanceof IrontombSatellite m && m.isAlive();
        if (!hasL) {
            logosId = spawnSat(sl, 0);
        }
        if (!hasM) {
            mythosId = spawnSat(sl, 1);
        }
    }

    private UUID spawnSat(ServerLevel sl, int type) {
        IrontombSatellite s = IrontombTypes.SATELLITE.create(sl);
        if (s == null) {
            return null;
        }
        s.bind(this, type);
        s.moveTo(getX() + (type == 0 ? 10 : -10), getY() + 7, getZ(), 0f, 0f);
        s.reforge(getPhase());
        sl.addFreshEntity(s);
        return s.getUUID();
    }

    /** A satellite entered Grave Wound: Action Lock is dispelled, the party is relieved, queued follow-ups are cancelled. */
    public void onGrave(IrontombSatellite s) {
        unlockAll();
        setHatred(getHatred() - IrontombBalance.GRAVE_HATRED_RELIEF);
        if (queuedFrom == s.getSatType()) {
            clearQueue();
        }
        sound(SoundEvents.ANVIL_DESTROY, 4f, 0.6f);
        sound(IrontombSounds.GRAVE, 10f, 0.9f);
        for (Player p : targets(120)) {
            if (s.getSatType() == 0 && p instanceof ServerPlayer sp) {
                IronsCompat.addMana(sp, IrontombBalance.GRAVE_MANA_RELIEF);
            } else if (s.getSatType() == 1) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1));
                cooldown += 30;
            }
        }
    }

    public void addHatred(float v) {
        int n = Math.max(1, foes(90).size());
        float scale = Math.min(1f, 0.4f + 0.2f * n);
        setHatred(getHatred() + v * scale * IrontombBalance.HATRED_GAIN);
    }

    // ------------------------------------------------------------------ Amphorean Hatred -> "The Fall of Erudition"
    private void tickErudition() {
        float amt = -IrontombBalance.ERUDITION_MAX * getHatred() / 100f;
        for (Player p : level().players()) {
            if (p.distanceToSqr(this) > 140.0 * 140.0) {
                clearErudition(p);
                continue;
            }
            AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
            if (inst == null) {
                continue;
            }
            AttributeModifier m = inst.getModifier(ERUDITION_ID);
            if (Math.abs(amt) < 0.01f) {
                if (m != null) {
                    inst.removeModifier(ERUDITION_ID);
                }
                continue;
            }
            if (m == null || Math.abs(m.getAmount() - amt) > 0.01) {
                inst.removeModifier(ERUDITION_ID);
                inst.addTransientModifier(new AttributeModifier(ERUDITION_ID, "irontomb_erudition", amt, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (p.getHealth() > p.getMaxHealth()) {
                p.setHealth(p.getMaxHealth());
            }
        }
    }

    private static void clearErudition(Player p) {
        AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
        if (inst != null && inst.getModifier(ERUDITION_ID) != null) {
            inst.removeModifier(ERUDITION_ID);
        }
    }

    // ------------------------------------------------------------------ helpers
    private List<Player> targets(double r) {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(r, 140, r),
                p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) <= r * r + 140 * 140);
    }

    /** Can this entity be fought? Players, plus (if enabled) other mobs such as the ones from Mob Battle. */
    private boolean isFoe(LivingEntity e) {
        if (e instanceof Player p) {
            return p.isAlive() && !p.isSpectator();
        }
        return IrontombBalance.FIGHT_MOBS && e instanceof Mob && e.isAlive() && e != this
                && !(e instanceof IrontombEntity) && !(e instanceof IrontombSatellite) && !(e instanceof PhainonEntity);
    }

    /** Players and (if enabled) mobs within r blocks, everything the boss can attack. */
    private List<LivingEntity> foes(double r) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 140, r),
                e -> isFoe(e) && e.distanceToSqr(this) <= r * r + 140 * 140);
    }

    private static String fmt(double v) {
        String[] u = {"", "K", "M", "B", "T", "Qa"};
        int i = 0;
        while (v >= 1000.0 && i < u.length - 1) {
            v /= 1000.0;
            i++;
        }
        return i == 0 ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.2f%s", v, u[i]);
    }

    public boolean hasTargets() {
        return !foes(90).isEmpty();
    }

    public List<LivingEntity> pickables() {
        return foes(90);
    }

    private void soundAt(Vec3 p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        IrontombFxServer.sound((ServerLevel) level(), p.x, p.y + 1, p.z, s, vol, pitch);
    }

    private void sound(net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        IrontombFxServer.sound((ServerLevel) level(), getX(), getY() + 4, getZ(), s, vol, pitch);
    }
}
