package com.example.jjkspells.afterlife.entity;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModEntities;
import com.example.jjkspells.afterlife.registry.ModParticles;
import com.example.jjkspells.afterlife.registry.ModSounds;
import com.example.jjkspells.afterlife.util.Blossom;
import com.example.jjkspells.afterlife.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Funeral of a Thousand Butterflies. One controller entity drives the whole 7 second ceremony from a single clock
 * (age = tickCount - 1). All damage happens on the server; the client only derives visuals from the same clock.
 * <pre>
 *  P1  0..20   the funeral begins (circle, butterflies, lanterns, darkness)
 *  P2 20..60   dance of the departed (12 teleporting spear strikes)
 *  P3 60..90   gate of the afterlife (hover, portal, pull, slow, gate damage)
 *  P4 90..110  thousand butterfly cremation (giant ghost dives, final explosion at 104)
 *  P5 110..140 the director's farewell (descend, heal, regeneration)
 * </pre>
 */
public class FuneralEntity extends Entity {
    public static final int T2 = 20, T3 = 60, T4 = 90, T_BOOM = 104, T5 = 110, T_END = 140, DURATION = 156;

    private static final UUID SLOW_ID = UUID.fromString("0f1a2b3c-4d5e-4f60-8a9b-a1f7e2110001");

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(FuneralEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CRIMSON = SynchedEntityData.defineId(FuneralEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(FuneralEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HOVER = SynchedEntityData.defineId(FuneralEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_R1 = SynchedEntityData.defineId(FuneralEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_R2 = SynchedEntityData.defineId(FuneralEntity.class, EntityDataSerializers.FLOAT);

    // server-side state
    private UUID ownerId;
    private Vec3 center = Vec3.ZERO, origin = Vec3.ZERO;
    private float originYaw, originPitch;
    private double hoverActual;
    private int strikeIdx;
    private boolean finished;
    private final List<LivingEntity> victims = new ArrayList<>();
    private final List<LivingEntity> slowed = new ArrayList<>();

    public FuneralEntity(EntityType<? extends FuneralEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static FuneralEntity create(Level level, LivingEntity caster) {
        var c = AfterlifeConfig.FUNERAL;
        FuneralEntity e = new FuneralEntity(ModEntities.FUNERAL.get(), level);
        e.ownerId = caster.getUUID();
        Vec3 ctr = caster.position();
        if (c.range.get() > 0) {
            Vec3 aim = Util.aimPoint(level, caster, c.range.get());
            if (aim.distanceTo(caster.position()) > 3.0) {
                Double gy = Util.findGround(level, aim.x, aim.y, aim.z);
                if (gy != null) {
                    ctr = new Vec3(aim.x, gy, aim.z);
                }
            }
        }
        e.center = ctr;
        e.origin = caster.position();
        e.originYaw = caster.getYRot();
        e.originPitch = caster.getXRot();
        e.hoverActual = Math.max(2.0, Math.min(c.hoverHeight.get(), Util.clearAbove(level, ctr, c.hoverHeight.get())));
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_OWNER, caster.getId());
        e.entityData.set(DATA_CRIMSON, caster.getHealth() < caster.getMaxHealth() * c.crimsonThresholdPercent.get() / 100.0);
        e.entityData.set(DATA_YAW, caster.getYRot());
        e.entityData.set(DATA_HOVER, (float) e.hoverActual);
        e.entityData.set(DATA_R1, c.strikeRadius.get().floatValue());
        e.entityData.set(DATA_R2, c.boomRadius.get().floatValue());
        return e;
    }

    // ------------------------------------------------------------ shared getters (client uses them for visuals)

    public Entity getOwnerEntity() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    public boolean isCrimson() {
        return entityData.get(DATA_CRIMSON);
    }

    public float getCasterYaw() {
        return entityData.get(DATA_YAW);
    }

    public float getHover() {
        return entityData.get(DATA_HOVER);
    }

    public float getR1() {
        return entityData.get(DATA_R1);
    }

    public float getR2() {
        return entityData.get(DATA_R2);
    }

    /** Centre of the spirit gate relative to this entity: behind the caster and above the hovering height. */
    public Vec3 portalOffset() {
        double yaw = getCasterYaw() * Mth.DEG_TO_RAD;
        return new Vec3(Math.sin(yaw) * 6.0, getHover() + 3.0, -Math.cos(yaw) * 6.0);
    }

    public boolean isLocking() {
        return tickCount - 1 < T_END;
    }

    // ------------------------------------------------------------ server logic

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_OWNER, -1);
        entityData.define(DATA_CRIMSON, false);
        entityData.define(DATA_YAW, 0f);
        entityData.define(DATA_HOVER, 8f);
        entityData.define(DATA_R1, 10f);
        entityData.define(DATA_R2, 14f);
    }

    private LivingEntity owner() {
        if (level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.afterlife.client.ClientFx.tickFuneral(this));
            return;
        }
        LivingEntity owner = owner();
        if (owner == null) {
            discard();
            return;
        }
        int age = tickCount - 1;
        var c = AfterlifeConfig.FUNERAL;

        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.FALL_IMMUNE.put(owner.getUUID(), level().getGameTime() + T_END + 100);
            Util.sound(level(), getX(), getY(), getZ(), ModSounds.PORTAL.get(), 3.0f, 0.7f);
            if (owner instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("msg.jjk_spells.afterlife.farewell"), true);
            }
        }
        if (age < T2) {
            owner.setDeltaMovement(0, Math.min(0.0, owner.getDeltaMovement().y), 0);
            phase1(age);
        } else if (age < T3) {
            if (age == T2) {
                startDance(owner);
            }
            owner.setDeltaMovement(Vec3.ZERO);
            int rel = age - T2;
            while (strikeIdx < c.strikes.get() && rel >= Math.round(strikeIdx * (T3 - T2) / (double) c.strikes.get())) {
                strike(owner, strikeIdx++);
            }
        } else if (age < T4) {
            if (age == T3) {
                startGate(owner);
            }
            owner.setDeltaMovement(Vec3.ZERO);
            gate(owner, age);
        } else if (age < T5) {
            if (age == T4) {
                Util.sound(level(), getX(), getY() + 6, getZ(), ModSounds.GHOST_CRY.get(), 5.0f, 0.85f);
                releaseSlow();
            }
            owner.setDeltaMovement(Vec3.ZERO);
            dive(age);
            if (age == T_BOOM) {
                boom(owner);
            }
        } else if (age < T_END) {
            if (age == T5) {
                land(owner);
            }
            farewell(owner, age);
        }
        if (age == T_END) {
            owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, (int) Math.round(c.fireImmuneSeconds.get() * 20), 0, false, false, true));
            finish(true);
        }
        if (age >= DURATION) {
            discard();
        }
    }

    private void phase1(int age) {
        Vec3 p = position();
        double r1 = getR1();
        var rnd = random;
        if (age % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r1;
                var t = i % 3 == 0 ? ModParticles.BUTTERFLY_RED.get() : i % 3 == 1 ? ModParticles.BUTTERFLY_ORANGE.get() : ModParticles.BUTTERFLY_GOLD.get();
                Util.fxDir(level(), t, p.x + Math.cos(a) * d, p.y + 0.2, p.z + Math.sin(a) * d, 0, 0.05 + rnd.nextDouble() * 0.04, 0);
            }
        }
        if (age % 4 == 1) {
            for (int i = 0; i < 2; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * (r1 - 3);
                Util.fxDir(level(), ModParticles.LANTERN.get(), p.x + Math.cos(a) * d, p.y + 0.5, p.z + Math.sin(a) * d, 0, 0.03, 0);
                Util.fxDir(level(), ModParticles.WISP.get(), p.x + Math.cos(a + 1) * d, p.y + 0.5, p.z + Math.sin(a + 1) * d, 0, 0.04, 0);
            }
        }
        Util.fx(level(), ModParticles.SPIRIT_FLAME.get(), p.x, p.y + 0.1, p.z, 3, r1 * 0.5, 0.05, r1 * 0.5, 0.02);
    }

    private void startDance(LivingEntity owner) {
        Vec3 from = owner.position();
        Util.fx(level(), ModParticles.BUTTERFLY_RED.get(), from.x, from.y + 1.0, from.z, 40, 0.5, 0.8, 0.5, 0.25);
        Util.fx(level(), ModParticles.BUTTERFLY_ORANGE.get(), from.x, from.y + 1.0, from.z, 40, 0.5, 0.8, 0.5, 0.25);
        Util.fx(level(), ModParticles.FLASH.get(), from.x, from.y + 1.0, from.z, 1, 0, 0, 0, 0);
        Util.UNTOUCHABLE.add(owner.getUUID());
        owner.setNoGravity(true);
        setPos(center.x, center.y, center.z);
        Vec3 dest = Util.safeNear(level(), owner, center, 0.0);
        Util.snapTo(owner, dest == null ? center : dest, owner.getYRot(), 0f);
        Util.sound(level(), center.x, center.y, center.z, ModSounds.STRIKE.get(), 1.2f, 0.6f);
    }

    private void strike(LivingEntity owner, int i) {
        var c = AfterlifeConfig.FUNERAL;
        List<LivingEntity> es = Util.enemies(level(), center, c.strikeRadius.get(), owner);
        es.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center.x, center.y, center.z)));
        Vec3 prev = owner.position();
        Util.fx(level(), ModParticles.BUTTERFLY_RED.get(), prev.x, prev.y + 1.0, prev.z, 12, 0.3, 0.6, 0.3, 0.12);
        Util.fxDir(level(), ModParticles.AFTERIMAGE.get(), prev.x, prev.y + 1.0, prev.z, 0, 0, 0);
        Util.fx(level(), ModParticles.SPIRIT_FLAME.get(), prev.x, prev.y + 1.0, prev.z, 8, 0.3, 0.5, 0.3, 0.03);
        if (es.isEmpty()) {
            double a = i * Math.PI / 6.0;
            Vec3 p = center.add(Math.cos(a) * 3.0, 0, Math.sin(a) * 3.0);
            Vec3 ok = Util.safeNear(level(), owner, p, 0.0);
            Util.snapTo(owner, ok == null ? prev : ok, Util.yawTo(p, center), 0f);
            return;
        }
        LivingEntity t = es.get(i % es.size());
        Vec3 tp = t.position();
        Vec3 spot = Util.safeNear(level(), owner, tp, 2.0);
        if (spot != null) {
            Util.snapTo(owner, spot, Util.yawTo(spot, tp), 0f);
        }
        double mult = isCrimson() ? 1.0 + c.crimsonStrikeBonusPercent.get() / 100.0 : 1.0;
        Util.hurt(owner, t, c.strikeFlat.get(), c.strikePercent.get(), mult);
        if (!victims.contains(t)) {
            victims.add(t);
        }
        if (t.isAlive()) {
            Blossom.apply(owner, t, Blossom.ticksOf(c.strikeBlossomSeconds.get()));
        }
        Vec3 q = t.getBoundingBox().getCenter();
        Util.fx(level(), ModParticles.SLASH.get(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.FLASH.get(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.PETAL.get(), q.x, q.y, q.z, 14, 0.4, 0.5, 0.4, 0.14);
        Util.fx(level(), ModParticles.EMBER.get(), q.x, q.y, q.z, 10, 0.3, 0.4, 0.3, 0.12);
        Util.sound(level(), q.x, q.y, q.z, ModSounds.STRIKE.get(), 1.1f, 0.8f + random.nextFloat() * 0.5f);
    }

    private void startGate(LivingEntity owner) {
        Vec3 up = center.add(0, hoverActual, 0);
        Vec3 from = owner.position();
        Util.fx(level(), ModParticles.BUTTERFLY_GOLD.get(), from.x, from.y + 1.0, from.z, 24, 0.4, 0.8, 0.4, 0.2);
        Util.snapTo(owner, up, originYaw, -20f);
        Util.UNTOUCHABLE.add(owner.getUUID());
        owner.setNoGravity(true);
        Util.sound(level(), center.x, center.y + 8, center.z, ModSounds.PORTAL.get(), 4.0f, 1.0f);
        var c = AfterlifeConfig.FUNERAL;
        for (LivingEntity e : Util.enemies(level(), center, c.strikeRadius.get(), owner)) {
            slow(e);
        }
    }

    private void slow(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(SLOW_ID) == null) {
            double f = AfterlifeConfig.FUNERAL.portalSlowPercent.get() / 100.0;
            inst.addTransientModifier(new AttributeModifier(SLOW_ID, "afterlife_gate", -f, AttributeModifier.Operation.MULTIPLY_TOTAL));
            slowed.add(e);
        }
    }

    private void releaseSlow() {
        for (LivingEntity e : slowed) {
            AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null) {
                inst.removeModifier(SLOW_ID);
            }
        }
        slowed.clear();
    }

    private void gate(LivingEntity owner, int age) {
        var c = AfterlifeConfig.FUNERAL;
        Vec3 portal = position().add(portalOffset());
        for (LivingEntity e : Util.enemies(level(), center, c.strikeRadius.get(), owner)) {
            slow(e);
            Vec3 to = new Vec3(center.x - e.getX(), 0, center.z - e.getZ());
            double d = to.length();
            if (d > 0.8) {
                Vec3 pull = to.normalize().scale(Math.min(0.32, d * 0.08));
                Vec3 m = e.getDeltaMovement();
                e.setDeltaMovement(m.x * 0.5 + pull.x, m.y, m.z * 0.5 + pull.z);
                e.hurtMarked = true;
            }
        }
        if ((age - T3) % 10 == 0) {
            for (LivingEntity e : Util.enemies(level(), center, c.strikeRadius.get(), owner)) {
                double mult = Blossom.has(e) ? 1.0 + c.portalBlossomBonusPercent.get() / 100.0 : 1.0;
                Util.hurt(owner, e, c.portalFlat.get(), c.portalPercent.get(), mult);
                if (!victims.contains(e)) {
                    victims.add(e);
                }
                Vec3 q = e.getBoundingBox().getCenter();
                Util.fx(level(), ModParticles.EMBER.get(), q.x, q.y, q.z, 8, 0.3, 0.4, 0.3, 0.08);
            }
        }
        // spirits spiralling out of the gate, rings of butterflies, floating talismans
        double yaw = getCasterYaw() * Mth.DEG_TO_RAD;
        for (int i = 0; i < 3; i++) {
            double a = age * 0.5 + i * Math.PI * 2 / 3;
            double r = 3.0 + (age - T3) * 0.1;
            Vec3 pp = portal.add(Math.cos(a) * r * Math.cos(yaw), Math.sin(a) * r, Math.cos(a) * r * Math.sin(yaw));
            Util.fxDir(level(), ModParticles.WISP.get(), pp.x, pp.y, pp.z, (center.x - pp.x) * 0.02, (center.y + 2 - pp.y) * 0.02, (center.z - pp.z) * 0.02);
        }
        for (int i = 0; i < 4; i++) {
            double a = age * 0.25 + i * Math.PI / 2;
            double r = 10.0 - (age - T3) * 0.1;
            var t = i % 2 == 0 ? ModParticles.BUTTERFLY_RED.get() : ModParticles.BUTTERFLY_GOLD.get();
            Util.fxDir(level(), t, center.x + Math.cos(a) * r, center.y + 1.5 + (age - T3) * 0.1, center.z + Math.sin(a) * r, -Math.sin(a) * 0.12, 0.02, Math.cos(a) * 0.12);
        }
        if (age % 3 == 0) {
            Util.fxDir(level(), ModParticles.TALISMAN.get(), portal.x + (random.nextDouble() - 0.5) * 12, portal.y + (random.nextDouble() - 0.5) * 10, portal.z + (random.nextDouble() - 0.5) * 12, 0, 0.01, 0);
        }
        Util.fx(level(), ModParticles.SPIRIT_FLAME.get(), center.x, center.y + 0.2, center.z, 4, c.strikeRadius.get() * 0.4, 0.1, c.strikeRadius.get() * 0.4, 0.04);
    }

    /** Giant ghost position relative to this entity: f = 0 in the gate, f = 1 on the ground. */
    public Vec3 ghostRel(double f) {
        Vec3 from = portalOffset();
        Vec3 to = new Vec3(0, 2.5, 0);
        Vec3 p = from.lerp(to, f * f);
        return p.add(0, Math.sin(f * Math.PI) * 2.0, 0);
    }

    private void dive(int age) {
        double f = Mth.clamp((age - T4) / (double) (T_BOOM - T4), 0.0, 1.0);
        if (age >= T_BOOM) {
            return;
        }
        Vec3 g = position().add(ghostRel(f));
        for (int i = 0; i < 8; i++) {
            double a = age * 0.7 + i * Math.PI / 4;
            double r = 1.8 + (i % 2) * 1.2;
            var t = i % 3 == 0 ? ModParticles.BUTTERFLY_RED.get() : i % 3 == 1 ? ModParticles.BUTTERFLY_ORANGE.get() : ModParticles.BUTTERFLY_GOLD.get();
            Util.fxDir(level(), t, g.x + Math.cos(a) * r, g.y + 1.0 + i * 0.3, g.z + Math.sin(a) * r, 0, 0.02, 0);
        }
        Util.fx(level(), ModParticles.SPIRIT_FLAME.get(), g.x, g.y, g.z, 6, 1.5, 1.5, 1.5, 0.05);
        Util.fx(level(), ModParticles.EMBER.get(), g.x, g.y, g.z, 8, 1.8, 1.8, 1.8, 0.05);
    }

    private void boom(LivingEntity owner) {
        var c = AfterlifeConfig.FUNERAL;
        var l = level();
        Util.sound(l, center.x, center.y, center.z, ModSounds.ULT_BOOM.get(), 6.0f, 1.0f);
        double r2 = c.boomRadius.get();
        for (LivingEntity e : Util.enemies(l, center, r2, owner)) {
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            boolean core = Math.sqrt(dx * dx + dz * dz) <= c.coreRadius.get() + e.getBbWidth() / 2;
            double flat = c.boomFlat.get() + (core ? c.coreBonus.get() : 0.0), pct = c.boomPercent.get();
            if (isCrimson()) {
                flat += c.crimsonBoomFlat.get();
                pct += c.crimsonBoomPercent.get();
            }
            if (Blossom.consume(e)) {
                flat += c.blossomDetFlat.get();
                pct += c.blossomDetPercent.get();
                Vec3 q = e.getBoundingBox().getCenter();
                Util.fx(l, ModParticles.PETAL.get(), q.x, q.y, q.z, 24, 0.4, 0.6, 0.4, 0.2);
            }
            if (!victims.contains(e)) {
                victims.add(e);
            }
            Util.hurt(owner, e, flat, pct, 1.0);
            if (e.isAlive()) {
                double k = Util.isBoss(e) ? c.bossKnockbackFactor.get() : 1.0;
                Vec3 m = e.getDeltaMovement();
                e.setDeltaMovement(m.x * 0.3, c.launchSpeed.get() * k, m.z * 0.3);
                e.hurtMarked = true;
            }
        }
        // gigantic crimson-and-gold blast: column, shockwave of butterflies, smoke, embers
        Util.fx(l, ModParticles.FLASH.get(), center.x, center.y + 2, center.z, 4, 1.0, 1.0, 1.0, 0);
        Util.fx(l, ModParticles.SPIRIT_FLAME.get(), center.x, center.y + 2, center.z, 120, 3.0, 2.0, 3.0, 0.35);
        Util.fx(l, ModParticles.EMBER.get(), center.x, center.y + 1, center.z, 220, r2 * 0.4, 2.0, r2 * 0.4, 0.4);
        Util.fx(l, ModParticles.WISP.get(), center.x, center.y + 1, center.z, 60, r2 * 0.3, 1.5, r2 * 0.3, 0.2);
        Util.fx(l, ModParticles.BUTTERFLY_RED.get(), center.x, center.y + 2, center.z, 120, 1.0, 1.5, 1.0, 0.45);
        Util.fx(l, ModParticles.BUTTERFLY_ORANGE.get(), center.x, center.y + 2, center.z, 120, 1.0, 1.5, 1.0, 0.45);
        Util.fx(l, ModParticles.BUTTERFLY_GOLD.get(), center.x, center.y + 2, center.z, 80, 1.0, 1.5, 1.0, 0.45);
        for (int i = 0; i < 90; i++) {
            double a = i * Math.PI * 2 / 90;
            var t = i % 3 == 0 ? ModParticles.BUTTERFLY_RED.get() : i % 3 == 1 ? ModParticles.BUTTERFLY_ORANGE.get() : ModParticles.BUTTERFLY_GOLD.get();
            Util.fxDir(l, t, center.x, center.y + 0.5, center.z, Math.cos(a) * 0.7, 0.03, Math.sin(a) * 0.7);
            Util.fxDir(l, ModParticles.SPIRIT_FLAME.get(), center.x, center.y + 0.3, center.z, Math.cos(a) * 0.5, 0.02, Math.sin(a) * 0.5);
        }
        for (int k = 0; k < 14; k++) {
            Util.fxDir(l, ModParticles.SPIRIT_FLAME.get(), center.x, center.y + k * 1.5, center.z, 0, 0.15, 0);
            Util.fx(l, ModParticles.EMBER.get(), center.x, center.y + k * 1.5, center.z, 12, 1.5, 0.8, 1.5, 0.12);
        }
    }

    private void land(LivingEntity owner) {
        Util.UNTOUCHABLE.remove(owner.getUUID());
        owner.setNoGravity(false);
        owner.setDeltaMovement(0, -0.1, 0);
        owner.hurtMarked = true;
        owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, false));
        Util.sound(level(), center.x, center.y + 4, center.z, ModSounds.HEAL.get(), 2.0f, 0.8f);
    }

    private void farewell(LivingEntity owner, int age) {
        var c = AfterlifeConfig.FUNERAL;
        var l = level();
        Vec3 o = owner.position();
        if (age == T5 + 2) {
            int kills = 0;
            for (LivingEntity v : victims) {
                if (!v.isAlive()) {
                    kills++;
                }
            }
            double pct = Math.min(c.healPercent.get() + kills * c.healPerKillPercent.get(), c.healCapPercent.get());
            if (isCrimson()) {
                pct += c.crimsonExtraHealPercent.get();
            }
            owner.heal((float) (owner.getMaxHealth() * pct / 100.0));
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) Math.round(c.regenSeconds.get() * 20), 2, false, true, true));
            for (LivingEntity e : Util.enemies(l, center, c.boomRadius.get(), owner)) {
                Blossom.apply(owner, e, Blossom.ticksOf(c.finalBlossomSeconds.get()));
            }
            Util.fx(l, ModParticles.BUTTERFLY_GOLD.get(), o.x, o.y + 1.0, o.z, 40, 0.6, 0.9, 0.6, 0.2);
            Util.fx(l, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, o.x, o.y + 1.0, o.z, 30, 0.6, 0.9, 0.6, 0.05);
        }
        // slowly fading embers raining over the battlefield, a final ring of light
        if (age % 2 == 0) {
            for (int i = 0; i < 12; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * c.boomRadius.get();
                Util.fxDir(l, ModParticles.EMBER.get(), center.x + Math.cos(a) * d, center.y + 6 + random.nextDouble() * 3, center.z + Math.sin(a) * d, 0, -0.06, 0);
            }
        }
        if (age == T5 + 4) {
            for (int i = 0; i < 60; i++) {
                double a = i * Math.PI * 2 / 60;
                Util.fxDir(l, ModParticles.SPIRIT_FLAME.get(), o.x, o.y + 0.4, o.z, Math.cos(a) * 0.45, 0.01, Math.sin(a) * 0.45);
            }
        }
        if (age == T5 + 14) {
            Util.fx(l, ModParticles.BUTTERFLY_RED.get(), o.x, o.y + 2.0, o.z, 30, 0.8, 1.0, 0.8, 0.2);
        }
    }

    /** Idempotent cleanup: lets go of the caster and everything slowed. Safe to call from every exit path. */
    private void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        releaseSlow();
        if (ownerId == null) {
            return;
        }
        Util.LOCKED.remove(ownerId);
        Util.UNTOUCHABLE.remove(ownerId);
        if (level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            owner.setNoGravity(false);
            if (!normal) {
                // interrupted: never leave anybody embedded in a wall or without gravity
                if (!level().noCollision(owner, owner.getBoundingBox())) {
                    Util.snapTo(owner, origin, originYaw, originPitch);
                } else {
                    owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false, false));
                }
                Util.FALL_IMMUNE.put(owner.getUUID(), level().getGameTime() + 200);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            finish(false);
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 400.0 * 400.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
