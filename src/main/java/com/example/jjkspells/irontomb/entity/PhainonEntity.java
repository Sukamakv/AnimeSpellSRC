package com.example.jjkspells.irontomb.entity;

import com.example.jjkspells.irontomb.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phainon, Emanator form: a hovering swordsman with golden holy wings. The model is a normal player model with
 * his skin; everything else (wings, corona, sigils, attack effects) is drawn by the client from the synced state.
 * He is the gatekeeper of Irontomb: slaying him unlocks the Irontomb arena for the players who took part.
 */
public class PhainonEntity extends Mob {
    private static final EntityDataAccessor<Integer> D_PHASE = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_ATTACK = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_ATK_T = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_EVENT = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_EV_T = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_TARGET = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_SEED = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> D_AWAKE = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> D_AIM = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> D_FROM = SynchedEntityData.defineId(PhainonEntity.class, EntityDataSerializers.VECTOR3);

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Phainon"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

    private int cooldown = 40;
    private int lastAttack;
    private Vec3 vel = Vec3.ZERO;
    private double orbit;
    private LivingEntity tgt;
    private int tgtCd;
    private int cfgVersion = -1;
    private float guardHp = -1f;
    private double introY;
    private double holdX, holdZ;
    private int sinceJudgment;
    private boolean rewarded;
    private final Set<UUID> damagers = new HashSet<>();
    private final Map<UUID, Integer> sweepHit = new HashMap<>();
    private final List<Set<UUID>> ringHit = new ArrayList<>();
    private final List<double[]> pending = new ArrayList<>();

    public PhainonEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.xpReward = 600;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, IrontombBalance.NORM)
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
        entityData.define(D_EVENT, 0);
        entityData.define(D_EV_T, 0);
        entityData.define(D_TARGET, -1);
        entityData.define(D_SEED, 0);
        entityData.define(D_AWAKE, false);
        entityData.define(D_AIM, new Vector3f());
        entityData.define(D_FROM, new Vector3f());
    }

    public int getPhase() { return entityData.get(D_PHASE); }
    public int getAttack() { return entityData.get(D_ATTACK); }
    public int getAtkT() { return entityData.get(D_ATK_T); }
    public int getEvent() { return entityData.get(D_EVENT); }
    public int getEvT() { return entityData.get(D_EV_T); }
    public int getTargetId() { return entityData.get(D_TARGET); }
    public int getSeed() { return entityData.get(D_SEED); }
    public boolean isAwake() { return entityData.get(D_AWAKE); }
    public Vec3 getAim() { Vector3f v = entityData.get(D_AIM); return new Vec3(v.x(), v.y(), v.z()); }
    public Vec3 getFrom() { Vector3f v = entityData.get(D_FROM); return new Vec3(v.x(), v.y(), v.z()); }
    private void setAim(Vec3 v) { entityData.set(D_AIM, new Vector3f((float) v.x, (float) v.y, (float) v.z)); }
    private void setFrom(Vec3 v) { entityData.set(D_FROM, new Vector3f((float) v.x, (float) v.y, (float) v.z)); }

    // ------------------------------------------------------------------ behaviour overrides
    @Override public boolean isPushable() { return false; }
    @Override public void knockback(double s, double x, double z) { }
    @Override public boolean canBeAffected(MobEffectInstance e) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public void checkDespawn() { }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float f, float m, DamageSource s) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 260.0 * 260.0; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(90.0, 70.0, 90.0); }
    @Override public void travel(Vec3 v) { }
    @Override public Component getTypeName() { return Component.literal("Phainon"); }

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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(D_PHASE, Math.max(1, t.getInt("Phase")));
        entityData.set(D_AWAKE, t.getBoolean("Awake"));
        if (hasCustomName()) {
            bar.setName(getDisplayName());
        }
    }

    // ------------------------------------------------------------------ damage
    public float reduction() {
        return getAttack() == PhainonAttack.STAGGER ? 0f : IrontombBalance.PH_REDUCTION;
    }

    @Override
    public boolean hurt(DamageSource s, float amt) {
        if (level().isClientSide) {
            return false;
        }
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(s, amt);
        }
        if (s.is(DamageTypeTags.IS_FALL) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.DROWN) || s.is(DamageTypes.CRAMMING)) {
            return false;
        }
        if (!isAwake() || getEvent() != PhainonAttack.EV_NONE || isDeadOrDying()) {
            return false;
        }
        if (s.getEntity() instanceof Player p) {
            damagers.add(p.getUUID());
        }
        double real = amt * (1.0 - reduction());
        double pool = Math.max(1.0, IrontombBalance.PH_MAX_HEALTH);
        real = Math.min(real, IrontombBalance.PH_MAX_HIT_FRACTION * pool);
        float units = (float) (real * IrontombBalance.NORM / pool);
        if (units <= 0f) {
            return false;
        }
        this.invulnerableTime = 0;
        return super.hurt(s, units);
    }

    public void applyConfig() {
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            float frac = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1f;
            hp.setBaseValue(IrontombBalance.NORM);
            setHealth(Math.max(1f, frac * getMaxHealth()));
        }
        var md = getAttribute(com.example.jjkspells.ModAttributes.MAX_HP_DAMAGE.get());
        if (md != null) {
            md.setBaseValue(IrontombBalance.PH_MAX_HP_DAMAGE);
        }
        var mr = getAttribute(com.example.jjkspells.ModAttributes.MAX_HP_RESIST.get());
        if (mr != null) {
            mr.setBaseValue(IrontombBalance.PH_MAX_HP_RESIST);
        }
    }

    @Override
    public void die(DamageSource s) {
        super.die(s);
        if (!level().isClientSide) {
            entityData.set(D_ATTACK, 0);
            entityData.set(D_ATK_T, 0);
            entityData.set(D_EVENT, PhainonAttack.EV_DEATH);
            entityData.set(D_EV_T, 0);
            pending.clear();
        }
    }

    /** The death sequence: he sinks, the wings shatter into light, the body dissolves, the Seal falls. */
    @Override
    protected void tickDeath() {
        if (level().isClientSide) {
            return;
        }
        int t = getEvT() + 1;
        entityData.set(D_EV_T, t);
        setDeltaMovement(Vec3.ZERO);
        if (t <= 50) {
            moveTo(getX(), getY() - 0.07, getZ(), getYRot(), getXRot());
        }
        if (t == 1) {
            sound(PhainonSounds.DEATH, 14f, 1f);
            if (level() instanceof ServerLevel xsl) {
                Vec3 xg = new Vec3(getX(), groundY(getX(), getY(), getZ()), getZ());
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 4f, 108);
                com.example.jjkspells.vfx.sprite.SpriteFx.vortex(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3f, 108);
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(xsl, position().add(0, 2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 6f);
            }
        }
        if (t == 44 || t == 70 || t == 96) {
            burst(position().add(0, 2, 0), 60);
        }
        if (t == 110 && !rewarded) {
            rewarded = true;
            rewardPlayers();
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(xsl, position().add(0, 2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 14f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(xsl, position().add(0, 2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 10f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(xsl, new Vec3(getX(), groundY(getX(), getY(), getZ()), getZ()), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 10f);
            }
        }
        if (t >= PhainonAttack.EV_LENGTH[PhainonAttack.EV_DEATH]) {
            if (level() instanceof ServerLevel sl) {
                ExperienceOrb.award(sl, position(), xpReward);
            }
            remove(RemovalReason.KILLED);
        }
    }

    /** Everyone who hurt him or stood near gets the unlock flag and a Dawnbreaker's Seal. */
    private void rewardPlayers() {
        Set<ServerPlayer> who = new HashSet<>();
        if (level() instanceof ServerLevel sl) {
            for (UUID id : damagers) {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
                if (p != null && p.isAlive()) {
                    who.add(p);
                }
            }
            for (ServerPlayer p : sl.players()) {
                if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 120.0 * 120.0) {
                    who.add(p);
                }
            }
        }
        for (ServerPlayer p : who) {
            PhainonGate.set(p, true);
            ItemStack seal = new ItemStack(PhainonTypes.seal());
            if (!p.getInventory().contains(seal) && !p.getInventory().add(seal)) {
                p.drop(seal, false);
            }
            p.sendSystemMessage(Component.literal("§6Phainon falls. The Dawnbreaker's Seal answers to you now: Irontomb will wake for you."));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            bar.removeAllPlayers();
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ main tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isDeadOrDying()) {
            return;
        }
        if (cfgVersion != IrontombBalance.VERSION) {
            cfgVersion = IrontombBalance.VERSION;
            applyConfig();
        }
        // burst guard: caps health lost per tick (also catches percent-health spells that never reach hurt())
        float floor = guardHp - (float) (IrontombBalance.PH_BURST * IrontombBalance.NORM);
        if (isAwake() && guardHp > 0f && getHealth() < floor && getHealth() > 0f) {
            setHealth(Math.max(1f, floor));
        }
        guardHp = getHealth();
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
        if (tickCount % 10 == 0) {
            double pool = Math.max(1.0, IrontombBalance.PH_MAX_HEALTH);
            double cur = Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f) * pool;
            String hp = fmt(cur) + " / " + fmt(pool) + " HP";
            Component nm = Component.literal("Phainon  [" + hp + "]");
            if (!nm.equals(getCustomName())) {
                setCustomName(nm);
                setCustomNameVisible(false);
            }
            bar.setName(Component.literal("Phainon, Dawnbreaker Emanator  [" + hp + "]"));
        }
        bar.setVisible(isAwake());

        if (!isAwake()) {
            if (getHealth() < getMaxHealth()) {
                setHealth(getMaxHealth());
            }
            if (tickCount % 5 == 0 && !foes(36).isEmpty()) {
                wake();
            }
            return;
        }
        if (getPhase() >= 3) {
            sinceJudgment++;
        }
        faceTick();

        if (getEvent() != PhainonAttack.EV_NONE) {
            tickEvent();
            return;
        }

        if (getAttack() == PhainonAttack.NONE) {
            float frac = getHealth() / getMaxHealth();
            if (getPhase() == 1 && frac <= IrontombBalance.PH_PHASE2_AT) {
                startEvent(PhainonAttack.EV_PHASE2);
                return;
            }
            if (getPhase() == 2 && frac <= IrontombBalance.PH_PHASE3_AT) {
                startEvent(PhainonAttack.EV_PHASE3);
                return;
            }
            steer();
            if (cooldown > 0) {
                cooldown--;
            } else if (!foes(100).isEmpty()) {
                chooseAttack();
            }
        } else {
            int t = getAtkT() + 1;
            entityData.set(D_ATK_T, t);
            steer();
            runAttack(getAttack(), t);
            if (t >= PhainonAttack.LENGTH[getAttack()]) {
                endAttack();
            }
        }
    }

    // ------------------------------------------------------------------ waking and events
    private void wake() {
        entityData.set(D_AWAKE, true);
        introY = groundY(getX(), getY() + 1, getZ()) + 5.5;
        sinceJudgment = 400;
        startEvent(PhainonAttack.EV_INTRO);
    }

    private void startEvent(int ev) {
        entityData.set(D_ATTACK, 0);
        entityData.set(D_ATK_T, 0);
        entityData.set(D_EVENT, ev);
        entityData.set(D_EV_T, 0);
        if (ev == PhainonAttack.EV_PHASE2) {
            entityData.set(D_PHASE, 2);
        } else if (ev == PhainonAttack.EV_PHASE3) {
            entityData.set(D_PHASE, 3);
            sinceJudgment = 400;
        }
        pending.clear();
        vel = Vec3.ZERO;
    }

    private void tickEvent() {
        int ev = getEvent();
        int t = getEvT() + 1;
        entityData.set(D_EV_T, t);
        vel = Vec3.ZERO;
        setDeltaMovement(Vec3.ZERO);
        switch (ev) {
            case PhainonAttack.EV_INTRO -> {
                if (t == 1) {
                    sound(PhainonSounds.ROAR, 16f, 1f);
                    if (level() instanceof ServerLevel xsl) {
                        Vec3 xg = new Vec3(getX(), groundY(getX(), getY(), getZ()), getZ());
                        com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 26f * 2f / 7f, 75);
                        com.example.jjkspells.vfx.sprite.SpriteFx.vortex(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 26f * 2f / 7f, 75);
                    }
                }
                if (t <= 80) {
                    float k = t / 80f;
                    float e = 1f - (1f - k) * (1f - k) * (1f - k);
                    moveTo(getX(), introY + 26.0 * (1.0 - e), getZ(), getYRot(), getXRot());
                }
                if (t == 56) {
                    sound(PhainonSounds.WING, 12f, 0.8f);
                }
                if (t == 76) {
                    shockwave(26, 1.6, 0.5);
                }
            }
            case PhainonAttack.EV_PHASE2 -> {
                if (t == 1) {
                    sound(PhainonSounds.ROAR, 16f, 1.1f);
                    if (level() instanceof ServerLevel xsl) {
                        Vec3 xg = new Vec3(getX(), groundY(getX(), getY(), getZ()), getZ());
                        com.example.jjkspells.vfx.sprite.SpriteFx.burst(xsl, position().add(0, 2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 6f);
                        com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 34f * 2f / 7f, 25);
                        com.example.jjkspells.vfx.sprite.SpriteFx.vortex(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 34f * 2f / 7f, 25);
                    }
                }
                if (t == 26) {
                    shockwave(34, 2.0, 0.6);
                }
            }
            case PhainonAttack.EV_PHASE3 -> {
                if (t == 1) {
                    sound(PhainonSounds.ROAR, 18f, 0.9f);
                    if (level() instanceof ServerLevel xsl) {
                        Vec3 xg = new Vec3(getX(), groundY(getX(), getY(), getZ()), getZ());
                        com.example.jjkspells.vfx.sprite.SpriteFx.burst(xsl, position().add(0, 2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 8f);
                        com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 48f * 2f / 7f, 43);
                        com.example.jjkspells.vfx.sprite.SpriteFx.vortex(xsl, xg, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 48f * 2f / 7f, 43);
                    }
                }
                if (t == 34) {
                    sound(PhainonSounds.WING, 14f, 0.7f);
                }
                if (t == 44) {
                    shockwave(48, 2.4, 0.7);
                }
            }
            default -> {
            }
        }
        if (t >= PhainonAttack.EV_LENGTH[ev]) {
            entityData.set(D_EVENT, 0);
            entityData.set(D_EV_T, 0);
            cooldown = ev == PhainonAttack.EV_INTRO ? 30 : 24;
        }
    }

    /** Throws everything near him outward without hurting it: used by the scripted moments. */
    private void shockwave(double r, double h, double v) {
        sound(PhainonSounds.IMPACT, 16f, 1f);
        Vec3 c = position();
        for (LivingEntity e : foes(r)) {
            shove(e, c, h, v);
        }
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.ring(sl, IrontombFxServer.dust(IrontombFxServer.GOLD, 2.4f), new Vec3(c.x, groundY(c.x, c.y, c.z), c.z), 6, 40, 0.9);
            IrontombFxServer.burst(sl, c.add(0, 1.5, 0), IrontombFxServer.GOLD, 90, 3.0, 0.5);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(c.x, groundY(c.x, c.y, c.z), c.z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) r * 2f / 6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) r / 4f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, c.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) r / 5f);
        }
    }

    // ------------------------------------------------------------------ steering and facing
    private void faceTick() {
        LivingEntity f = target();
        if (f == null) {
            return;
        }
        double dx = f.getX() - getX(), dz = f.getZ() - getZ();
        if (dx * dx + dz * dz < 0.25) {
            return;
        }
        float want = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
        float turned = Mth.approachDegrees(getYRot(), want, getAttack() == PhainonAttack.JUDGMENT ? 3f : 22f);
        setYRot(turned);
        yBodyRot = turned;
        yHeadRot = turned;
        double hd = Math.sqrt(dx * dx + dz * dz);
        setXRot(Mth.clamp((float) (-Math.atan2(f.getY() + f.getBbHeight() * 0.5 - (getY() + 1.8), hd) * (180.0 / Math.PI)), -40f, 40f));
    }

    private void steer() {
        int atk = getAttack();
        if (atk == PhainonAttack.DASH) {
            vel = Vec3.ZERO;
            return;
        }
        double hover = 5.5, keep = 15.0;
        boolean hold = false;
        switch (atk) {
            case PhainonAttack.SUNFALL -> { hover = 15.0; keep = 18.0; }
            case PhainonAttack.JUDGMENT -> { hover = 30.0; hold = true; }
            case PhainonAttack.STAGGER -> { hover = 1.7; keep = 6.0; }
            case PhainonAttack.SWEEP -> { hover = 7.0; hold = true; }
            case PhainonAttack.HALO -> { hover = 4.0; hold = true; }
            case PhainonAttack.LANCE -> keep = 16.0;
            default -> {
            }
        }
        LivingEntity t = target();
        double wx = getX(), wz = getZ(), wy = getY();
        if (t != null) {
            double base = Math.max(groundY(t.getX(), t.getY() + 1, t.getZ()), t.getY());
            wy = base + hover;
            if (hold) {
                wx = holdX;
                wz = holdZ;
            } else {
                orbit += 0.013;
                wx = t.getX() + Math.cos(orbit) * keep;
                wz = t.getZ() + Math.sin(orbit) * keep;
            }
        } else if (hold) {
            wx = holdX;
            wz = holdZ;
        }
        Vec3 d = new Vec3(wx - getX(), wy - getY(), wz - getZ());
        vel = vel.scale(0.80).add(d.scale(0.055));
        double sp = vel.length();
        double max = atk == PhainonAttack.STAGGER ? 0.3 : 1.15;
        if (sp > max) {
            vel = vel.scale(max / sp);
        }
        move(MoverType.SELF, vel);
        if (horizontalCollision) {
            vel = new Vec3(vel.x * 0.4, Math.max(vel.y, 0.0) + 0.25, vel.z * 0.4);
        }
    }

    private double groundY(double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int min = level().getMinBuildHeight();
        int n = 0;
        while (m.getY() > min && n++ < 96 && !level().getBlockState(m).blocksMotion()) {
            m.move(0, -1, 0);
        }
        return m.getY() + 1.0;
    }

    private LivingEntity target() {
        if (tgt == null || !tgt.isAlive() || !isFoe(tgt) || (getAttack() == PhainonAttack.NONE && --tgtCd <= 0)) {
            tgtCd = 20;
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : foes(100)) {
                double d = e.distanceToSqr(this) - (e instanceof Player ? 400.0 : 0.0);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            tgt = best;
        }
        return tgt;
    }

    // ------------------------------------------------------------------ attack selection
    private void chooseAttack() {
        int ph = getPhase();
        if (ph >= 3 && sinceJudgment >= 700) {
            sinceJudgment = 0;
            startAttack(PhainonAttack.JUDGMENT);
            return;
        }
        int[] ids = {PhainonAttack.LANCE, PhainonAttack.DASH, PhainonAttack.FEATHER, PhainonAttack.SUNFALL, PhainonAttack.HALO, PhainonAttack.SWEEP};
        int[] w = {3, 3, 2, ph >= 2 ? 2 : 0, ph >= 2 ? 2 : 0, ph >= 3 ? 3 : 0};
        int total = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] != lastAttack) {
                total += w[i];
            }
        }
        if (total <= 0) {
            return;
        }
        int r = random.nextInt(total);
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == lastAttack) {
                continue;
            }
            r -= w[i];
            if (r < 0) {
                startAttack(ids[i]);
                return;
            }
        }
    }

    private void startAttack(int id) {
        entityData.set(D_ATTACK, id);
        entityData.set(D_ATK_T, 0);
        entityData.set(D_SEED, random.nextInt(1 << 24));
        LivingEntity t = target();
        entityData.set(D_TARGET, t == null ? -1 : t.getId());
        setAim(t == null ? position() : t.position().add(0, t.getBbHeight() * 0.5, 0));
        holdX = getX();
        holdZ = getZ();
        pending.clear();
        sweepHit.clear();
        ringHit.clear();
    }

    private void endAttack() {
        int a = getAttack();
        lastAttack = a;
        entityData.set(D_ATTACK, 0);
        entityData.set(D_ATK_T, 0);
        pending.clear();
        int ph = getPhase();
        cooldown = (ph == 1 ? 50 : ph == 2 ? 36 : 24) + random.nextInt(14);
        if (a == PhainonAttack.JUDGMENT) {
            startAttack(PhainonAttack.STAGGER);
        } else if (a == PhainonAttack.STAGGER) {
            cooldown = 30;
        }
    }

    /** Force an attack from the /phainon command. */
    public void forceAttack(int id) {
        if (!isAwake() || isDeadOrDying()) {
            return;
        }
        entityData.set(D_EVENT, 0);
        entityData.set(D_EV_T, 0);
        startAttack(id);
    }

    public void forcePhase(int ph) {
        entityData.set(D_PHASE, Mth.clamp(ph, 1, 3));
        entityData.set(D_EVENT, 0);
        entityData.set(D_ATTACK, 0);
        sinceJudgment = 400;
        float frac = ph == 1 ? 1f : ph == 2 ? IrontombBalance.PH_PHASE2_AT - 0.02f : IrontombBalance.PH_PHASE3_AT - 0.02f;
        setHealth(Math.max(1f, getMaxHealth() * Mth.clamp(frac, 0.01f, 1f)));
        guardHp = getHealth();
    }

    // ------------------------------------------------------------------ the attacks
    private void runAttack(int atk, int t) {
        LivingEntity tg = target();
        switch (atk) {
            case PhainonAttack.LANCE -> lance(t, tg);
            case PhainonAttack.DASH -> dash(t, tg);
            case PhainonAttack.FEATHER -> feather(t, tg);
            case PhainonAttack.SUNFALL -> sunfall(t, tg);
            case PhainonAttack.HALO -> halo(t);
            case PhainonAttack.SWEEP -> sweep(t);
            case PhainonAttack.JUDGMENT -> judgment(t, tg);
            case PhainonAttack.STAGGER -> {
                if (t == 1) {
                    sound(PhainonSounds.WING, 12f, 0.6f);
                }
            }
            default -> {
            }
        }
    }

    public Vec3 chest() {
        return position().add(0, 1.9, 0).add(forward().scale(0.9));
    }

    private Vec3 forward() {
        double yaw = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    // Dawn Lance --------------------------------------------------------------------------------
    private void lance(int t, LivingEntity tg) {
        if (t == 1) {
            sound(PhainonSounds.CHARGE, 12f, 1f);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.orbOn(xsl, this, 1.9f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 1.5f, PhainonAttack.LANCE_FIRE - 1);
            }
        }
        if (t <= 16 && tg != null) {
            setAim(tg.position().add(0, tg.getBbHeight() * 0.5, 0));
        }
        if (t == PhainonAttack.LANCE_FIRE) {
            Vec3 o = chest();
            Vec3 dir = getAim().subtract(o);
            if (dir.lengthSqr() < 1e-4) {
                dir = forward();
            }
            dir = dir.normalize();
            sound(PhainonSounds.LANCE, 16f, 1f);
            fireLance(o, dir);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(xsl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(xsl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 3f);
            }
            if (getPhase() >= 3) {
                fireLance(o, rotY(dir, 0.32));
                fireLance(o, rotY(dir, -0.32));
            }
        }
        if (getPhase() >= 2 && t == 17 && level() instanceof ServerLevel xsl) {
            Vec3 xa = getAim();
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, new Vec3(xa.x, groundY(xa.x, xa.y, xa.z), xa.z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 7f * 2f / 7f, 44 - 17);
        }
        if (getPhase() >= 2 && t == 44) {
            Vec3 a = getAim();
            Vec3 g = new Vec3(a.x, groundY(a.x, a.y, a.z), a.z);
            blast(g, 7.0, IrontombBalance.PH_LANCE, 0.6f, 0.8);
        }
    }

    public static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    private void fireLance(Vec3 o, Vec3 dir) {
        double len = 90.0;
        Vec3 end = o.add(dir.scale(len));
        for (LivingEntity e : foes(len + 10)) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 ap = c.subtract(o);
            double k = Mth.clamp(ap.dot(dir), 0, len);
            double d = c.distanceTo(o.add(dir.scale(k)));
            if (d <= 2.4 + e.getBbWidth() * 0.5) {
                PhainonDamage.deal(this, e, IrontombBalance.PH_LANCE, 1f);
                shove(e, o, 0.8, 0.25);
            }
        }
        if (level() instanceof ServerLevel sl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.beam(sl, o, end, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 3f, 6);
            for (int i = 0; i < 36; i++) {
                Vec3 p = o.add(dir.scale(i * 2.5));
                IrontombFxServer.send(sl, IrontombFxServer.dust(IrontombFxServer.GOLD, 2.6f), p.x, p.y, p.z, 3, 0.25, 0.25, 0.25, 0.0);
                if (i % 3 == 0) {
                    IrontombFxServer.send(sl, ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.02);
                }
            }
        }
    }

    // Daybreak Rush --------------------------------------------------------------------------------
    private void dash(int t, LivingEntity tg) {
        int n = PhainonAttack.dashes(getPhase());
        if (t < PhainonAttack.DASH_FIRST) {
            if (t == 1) {
                sound(PhainonSounds.WING, 10f, 1.4f);
            }
            return;
        }
        int k = (t - PhainonAttack.DASH_FIRST) / PhainonAttack.DASH_STEP;
        int local = (t - PhainonAttack.DASH_FIRST) % PhainonAttack.DASH_STEP;
        if (k >= n) {
            return;
        }
        if (local == 0 && tg != null) {
            Vec3 from = position();
            Vec3 dest = null;
            for (int tries = 0; tries < 8 && dest == null; tries++) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 c = tg.position().add(Math.cos(a) * 3.4, 0.4, Math.sin(a) * 3.4);
                AABB box = getBoundingBox().move(c.x - getX(), c.y - getY(), c.z - getZ());
                if (level().noCollision(this, box)) {
                    dest = c;
                }
            }
            if (dest == null) {
                dest = tg.position().add(0, 2.5, 0);
            }
            setFrom(from);
            setAim(dest);
            entityData.set(D_SEED, random.nextInt(1 << 24));
            double dx = tg.getX() - dest.x, dz = tg.getZ() - dest.z;
            float yaw = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
            moveTo(dest.x, dest.y, dest.z, yaw, 0f);
            yBodyRot = yaw;
            yHeadRot = yaw;
            vel = Vec3.ZERO;
            sound(PhainonSounds.DASH, 12f, 1f + 0.1f * k);
            if (level() instanceof ServerLevel sl) {
                Vec3 d = dest.subtract(from);
                int steps = Math.max(4, (int) (d.length() * 1.5));
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = from.add(d.scale(i / (double) steps)).add(0, 1.2, 0);
                    IrontombFxServer.send(sl, IrontombFxServer.dust(IrontombFxServer.GOLD, 1.8f), p.x, p.y, p.z, 2, 0.15, 0.3, 0.15, 0.0);
                }
                IrontombFxServer.burst(sl, from.add(0, 1.2, 0), IrontombFxServer.GOLD, 24, 0.8, 0.2);
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, from.add(0, 1.2, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, dest, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 7f * 2f / 7f, PhainonAttack.DASH_HIT_DELAY);
            }
        }
        if (local == PhainonAttack.DASH_HIT_DELAY) {
            Vec3 c = position();
            for (LivingEntity e : foes(7.0)) {
                if (e.position().distanceToSqr(c) <= 49.0) {
                    PhainonDamage.deal(this, e, IrontombBalance.PH_DASH, 1f);
                    shove(e, c, 0.7, 0.25);
                }
            }
            sound(PhainonSounds.IMPACT, 6f, 1.6f);
            if (level() instanceof ServerLevel sl) {
                IrontombFxServer.burst(sl, c.add(0, 1.3, 0), IrontombFxServer.GOLD, 30, 1.4, 0.3);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, c.add(0, 1.3, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 3.5f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 7f * 2f / 6f);
            }
        }
    }

    // Aureate Feather Rain ----------------------------------------------------------------------
    private void feather(int t, LivingEntity tg) {
        int S = PhainonAttack.spokes(getPhase());
        if (t == 1) {
            sound(PhainonSounds.WING, 14f, 1f);
        }
        if (t == PhainonAttack.FEATHER_START - 2) {
            Vec3 a = tg != null ? tg.position() : position();
            setAim(new Vec3(a.x, groundY(a.x, a.y + 1, a.z), a.z));
            entityData.set(D_SEED, random.nextInt(1 << 24));
        }
        int rel = t - PhainonAttack.FEATHER_START;
        if (rel >= 0 && rel % PhainonAttack.FEATHER_STEP == 0 && rel / PhainonAttack.FEATHER_STEP < PhainonAttack.FEATHER_VOLLEYS) {
            Vec3 a = getAim();
            sound(PhainonSounds.FEATHER, a.x, a.y + 8, a.z, 5f, 0.9f + random.nextFloat() * 0.3f);
            if (level() instanceof ServerLevel xsl) {
                for (int xs = 0; xs < S; xs++) {
                    Vec3 xp = PhainonAttack.featherPoint(getSeed(), rel / PhainonAttack.FEATHER_STEP, xs, S, a);
                    com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, xp, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 3.4f * 2f / 7f, PhainonAttack.FEATHER_FALL);
                }
            }
            // homing strike on somebody (a telegraphed gold ring, then a hit)
            if ((rel / PhainonAttack.FEATHER_STEP) % 2 == 0) {
                List<LivingEntity> l = foes(70);
                if (!l.isEmpty()) {
                    LivingEntity v = l.get(random.nextInt(l.size()));
                    pending.add(new double[]{v.getX(), groundY(v.getX(), v.getY() + 1, v.getZ()), v.getZ(), t + 14});
                    if (level() instanceof ServerLevel xsl) {
                        double[] xq = pending.get(pending.size() - 1);
                        com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, new Vec3(xq[0], xq[1], xq[2]), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3.0f * 2f / 7f, 14);
                    }
                }
            }
        }
        int rel2 = rel - PhainonAttack.FEATHER_FALL;
        if (rel2 >= 0 && rel2 % PhainonAttack.FEATHER_STEP == 0 && rel2 / PhainonAttack.FEATHER_STEP < PhainonAttack.FEATHER_VOLLEYS) {
            int v = rel2 / PhainonAttack.FEATHER_STEP;
            for (int s = 0; s < S; s++) {
                Vec3 p = PhainonAttack.featherPoint(getSeed(), v, s, S, getAim());
                featherImpact(p, 3.4);
            }
        }
        for (int i = pending.size() - 1; i >= 0; i--) {
            double[] p = pending.get(i);
            if (t < p[3]) {
                if (t % 3 == 0 && level() instanceof ServerLevel sl) {
                    IrontombFxServer.ring(sl, IrontombFxServer.dust(IrontombFxServer.GOLD, 1.4f), new Vec3(p[0], p[1], p[2]), 2.6, 18, 0.0);
                }
            } else {
                featherImpact(new Vec3(p[0], p[1], p[2]), 3.0);
                pending.remove(i);
            }
        }
    }

    private void featherImpact(Vec3 p, double r) {
        for (LivingEntity e : foes(r + 70)) {
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(e.getY() - p.y) < 6.0) {
                PhainonDamage.deal(this, e, IrontombBalance.PH_FEATHER, 1f);
            }
        }
        sound(PhainonSounds.IMPACT, p.x, p.y, p.z, 2.5f, 1.8f);
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.burst(sl, p.add(0, 0.6, 0), IrontombFxServer.GOLD, 14, 0.7, 0.18);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, p.add(0, 0.6, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) r * 0.6f);
        }
    }

    // Sunfall ----------------------------------------------------------------------------------
    private void sunfall(int t, LivingEntity tg) {
        if (t == 1) {
            sound(PhainonSounds.CHARGE, 14f, 0.8f);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.orbOn(xsl, this, 2f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 3f, PhainonAttack.SUN_THROW - 1);
            }
        }
        if (t == 18) {
            sound(PhainonSounds.SUNFALL, 18f, 1f);
        }
        if (t <= PhainonAttack.SUN_LOCK && tg != null) {
            setAim(new Vec3(tg.getX(), groundY(tg.getX(), tg.getY() + 1, tg.getZ()), tg.getZ()));
        }
        if (t == PhainonAttack.SUN_LOCK && level() instanceof ServerLevel xsl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) PhainonAttack.SUN_RADIUS * 2f / 7f, PhainonAttack.SUN_HIT - PhainonAttack.SUN_LOCK);
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) PhainonAttack.SUN_RADIUS * 2f / 7f, PhainonAttack.SUN_HIT - PhainonAttack.SUN_LOCK);
        }
        if (t == PhainonAttack.SUN_HIT) {
            blast(getAim(), PhainonAttack.SUN_RADIUS, IrontombBalance.PH_SUNFALL, 1f, 1.6);
            if (getPhase() >= 3 && level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 10f * 2f / 7f, 14);
            }
        }
        if (getPhase() >= 3 && t == PhainonAttack.SUN_HIT + 14) {
            blast(getAim(), 10.0, IrontombBalance.PH_SUNFALL, 0.5f, 1.2);
        }
    }

    // Ringing Halo -----------------------------------------------------------------------------
    private void halo(int t) {
        int N = PhainonAttack.rings(getPhase());
        if (t == 1) {
            setAim(new Vec3(getX(), groundY(getX(), getY(), getZ()), getZ()));
            for (int i = 0; i < N; i++) {
                ringHit.add(new HashSet<>());
            }
            sound(PhainonSounds.CHARGE, 12f, 1.2f);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 36f * 2f / 7f, PhainonAttack.HALO_START - 1);
            }
        }
        Vec3 o = getAim();
        for (int i = 0; i < N && i < ringHit.size(); i++) {
            int t0 = PhainonAttack.HALO_START + PhainonAttack.HALO_STEP * i;
            if (t == t0) {
                sound(PhainonSounds.HALO, o.x, o.y + 1, o.z, 14f, 1f + 0.06f * i);
                if (level() instanceof ServerLevel xsl) {
                    com.example.jjkspells.vfx.sprite.SpriteFx.ring(xsl, o, i % 2 == 0 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4f);
                    com.example.jjkspells.vfx.sprite.SpriteFx.impact(xsl, o.add(0, 1, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3f);
                }
            }
            if (t < t0) {
                continue;
            }
            double r = PhainonAttack.HALO_SPEED * (t - t0);
            if (r > 36.0) {
                continue;
            }
            for (LivingEntity e : foes(40)) {
                double dx = e.getX() - o.x, dz = e.getZ() - o.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - r) <= PhainonAttack.HALO_WIDTH + 0.7 && e.onGround() && Math.abs(e.getY() - o.y) < 40.0 && ringHit.get(i).add(e.getUUID())) {
                    PhainonDamage.deal(this, e, IrontombBalance.PH_HALO, 1f);
                    shove(e, o, 0.5, 0.35);
                }
            }
        }
    }

    // Aureate Sweep ------------------------------------------------------------------------------
    private void sweep(int t) {
        if (t == 1) {
            setAim(new Vec3(getX(), groundY(getX(), getY(), getZ()), getZ()));
            entityData.set(D_SEED, random.nextInt(1 << 24));
            sound(PhainonSounds.CHARGE, 12f, 1f);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) PhainonAttack.SWEEP_LENGTH * 2f / 7f, PhainonAttack.SWEEP_START - 1);
            }
        }
        if (t == PhainonAttack.SWEEP_START) {
            sound(PhainonSounds.SWEEP, 16f, 1f);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) PhainonAttack.SWEEP_LENGTH * 2f / 6f);
                com.example.jjkspells.vfx.sprite.SpriteFx.vortex(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4f, PhainonAttack.SWEEP_END - PhainonAttack.SWEEP_START);
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(xsl, getAim().add(0, 1, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4f);
            }
        }
        if (t < PhainonAttack.SWEEP_START || t > PhainonAttack.SWEEP_END) {
            return;
        }
        Vec3 o = getAim();
        int B = PhainonAttack.beams(getPhase());
        double base = PhainonAttack.sweepAngle(getSeed(), t);
        for (LivingEntity e : foes(PhainonAttack.SWEEP_LENGTH + 4)) {
            double dx = e.getX() - o.x, dz = e.getZ() - o.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 2.0 || d > PhainonAttack.SWEEP_LENGTH || !e.onGround()) {
                continue;
            }
            Integer last = sweepHit.get(e.getUUID());
            if (last != null && tickCount - last < 14) {
                continue;
            }
            double ang = Math.atan2(dz, dx);
            double half = Math.atan2(2.0, d) + Math.toRadians(PhainonAttack.SWEEP_DEG_PER_TICK) * 0.6;
            for (int b = 0; b < B; b++) {
                double diff = Mth.wrapDegrees((float) Math.toDegrees(ang - base - b * Math.PI * 2 / B));
                if (Math.abs(Math.toRadians(diff)) <= half) {
                    sweepHit.put(e.getUUID(), tickCount);
                    PhainonDamage.deal(this, e, IrontombBalance.PH_SWEEP, 1f);
                    shove(e, o, 0.6, 0.4);
                    break;
                }
            }
        }
    }

    // Dawnbreaker's Judgment ----------------------------------------------------------------------
    private void judgment(int t, LivingEntity tg) {
        if (t == 1) {
            sound(PhainonSounds.ROAR, 16f, 1.2f);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.orbOn(xsl, this, 2f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4f, PhainonAttack.JUDG_THROW - 1);
            }
        }
        if (t == 16) {
            sound(PhainonSounds.JUDGMENT, 20f, 1f);
        }
        if (t <= PhainonAttack.JUDG_LOCK && tg != null) {
            setAim(new Vec3(tg.getX(), groundY(tg.getX(), tg.getY() + 1, tg.getZ()), tg.getZ()));
        }
        if (t == PhainonAttack.JUDG_LOCK && level() instanceof ServerLevel xsl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) PhainonAttack.JUDG_RADIUS * 2f / 7f, PhainonAttack.JUDG_HIT - PhainonAttack.JUDG_LOCK);
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) PhainonAttack.JUDG_RADIUS * 2f / 7f, PhainonAttack.JUDG_HIT - PhainonAttack.JUDG_LOCK);
        }
        if (t == PhainonAttack.JUDG_HIT) {
            blast(getAim(), PhainonAttack.JUDG_RADIUS, IrontombBalance.PH_JUDGMENT, 1f, 2.4);
            if (level() instanceof ServerLevel xsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(xsl, getAim(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 42f * 2f / 7f, 14);
            }
        }
        if (t == PhainonAttack.JUDG_HIT + 14) {
            blast(getAim(), 42.0, IrontombBalance.PH_JUDGMENT, 0.4f, 1.6);
        }
    }

    // ------------------------------------------------------------------ shared effects
    private void blast(Vec3 c, double r, float[] f, float mult, double knock) {
        for (LivingEntity e : foes(r + 6)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(e.getY() - c.y) < r * 0.7 + 12.0) {
                PhainonDamage.deal(this, e, f, mult);
                shove(e, c, knock, 0.55);
            }
        }
        sound(PhainonSounds.IMPACT, c.x, c.y + 1, c.z, 22f, r > 20 ? 0.7f : 1f);
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.ring(sl, IrontombFxServer.dust(IrontombFxServer.GOLD, 2.6f), c, r * 0.5, 40, 0.8);
            IrontombFxServer.ring(sl, IrontombFxServer.dust(IrontombFxServer.WHITE, 2.2f), c, r * 0.25, 28, 0.6);
            IrontombFxServer.burst(sl, c.add(0, 1.5, 0), IrontombFxServer.GOLD, 120, r * 0.25, 0.6);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) r * 2f / 6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) r * 0.4f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, c.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) r * 0.3f);
        }
    }

    private void burst(Vec3 c, int n) {
        if (level() instanceof ServerLevel sl) {
            IrontombFxServer.burst(sl, c, IrontombFxServer.GOLD, n, 3.0, 0.5);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 8f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 5f);
        }
    }

    private void shove(LivingEntity e, Vec3 from, double h, double v) {
        Vec3 d = new Vec3(e.getX() - from.x, 0, e.getZ() - from.z);
        if (d.lengthSqr() < 1e-4) {
            d = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        d = d.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * h, v, d.z * h));
        e.hurtMarked = true;
    }

    // ------------------------------------------------------------------ targets
    private boolean isFoe(LivingEntity e) {
        if (e instanceof Player p) {
            return p.isAlive() && !p.isSpectator();
        }
        return IrontombBalance.FIGHT_MOBS && e instanceof Mob && e.isAlive() && e != this
                && !(e instanceof IrontombEntity) && !(e instanceof IrontombSatellite) && !(e instanceof PhainonEntity);
    }

    /** Per-tick scan cache: attacks call foes(r) many times a tick with the same r (halo rings, feather spokes), and each call is a 90-block-tall AABB scan. */
    private int foeCacheTick = -1;
    private final java.util.HashMap<Double, List<LivingEntity>> foeCache = new java.util.HashMap<>();

    private List<LivingEntity> foes(double r) {
        if (foeCacheTick != tickCount) {
            foeCache.clear();
            foeCacheTick = tickCount;
        }
        List<LivingEntity> hit = foeCache.get(r);
        if (hit == null) {
            hit = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 90, r),
                    e -> isFoe(e) && e.distanceToSqr(this) <= r * r + 90 * 90);
            foeCache.put(r, hit);
            return new java.util.ArrayList<>(hit);
        }
        // same tick, same radius: reuse the scan, but still drop anything killed since (callers expect only living foes)
        java.util.ArrayList<LivingEntity> out = new java.util.ArrayList<>(hit.size());
        for (LivingEntity e : hit) {
            if (isFoe(e)) {
                out.add(e);
            }
        }
        return out;
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

    private void sound(SoundEvent s, float vol, float pitch) {
        IrontombFxServer.sound((ServerLevel) level(), getX(), getY() + 2, getZ(), s, vol, pitch);
    }

    private void sound(SoundEvent s, double x, double y, double z, float vol, float pitch) {
        IrontombFxServer.sound((ServerLevel) level(), x, y, z, s, vol, pitch);
    }
}
