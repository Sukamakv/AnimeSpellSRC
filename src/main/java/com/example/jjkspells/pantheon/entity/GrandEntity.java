package com.example.jjkspells.pantheon.entity;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.registry.ModEffects;
import com.example.jjkspells.pantheon.registry.ModEntities;
import com.example.jjkspells.pantheon.registry.ModParticles;
import com.example.jjkspells.pantheon.registry.ModSounds;
import com.example.jjkspells.pantheon.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grand Starfall - The God Who Defied the Heavens. One controller entity standing on the landing point; one clock
 * (age = tickCount - 1). All damage is dealt here, on the server, phase by phase:
 * <pre>
 *  P1   0..30    the sigil, the star above, the spear thrust into the sky at 28
 *  P2  30..60    ascent along the pillar of light, at 50 the flash: the caster is hidden above the landing point
 *  P3  60..100   the targeting formation on the ground, beams, the meteor falls (70..100)
 *  P4 100        IMPACT (core radius full damage, shockwave 65%), everyone launched, the battlefield burns for 8 s
 *  P5 120..150   six giant spears slam into the ground one after another, then the Celestial Wave (to 25 blocks)
 *  P6 150..180   the spear is raised (150), driven in (160), the dome expands, collapses and detonates (176)
 * </pre>
 */
public class GrandEntity extends PanEntityBase {
    public static final int T_THRUST = 28, T_UP = 30, T_HIDE = 50, T_P3 = 60, T_METEOR = 70, T_IMPACT = 100, T_P5 = 120, T_SLAM0 = 122, SLAM_GAP = 5,
            T_WAVE = 150, WAVE_TICKS = 14, T_RAISE = 150, T_DRIVE = 160, T_COLLAPSE = 168, T_FINAL = 176, T_END = 180, BEAM_TICKS = 40;
    public static final double SPEAR_RING = 10.0;

    private static final EntityDataAccessor<BlockPos> DATA_START = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_SKY = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_R_CORE = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_R_IMPACT = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_R_WAVE = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_R_FINAL = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ZONE = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPEARS = SynchedEntityData.defineId(GrandEntity.class, EntityDataSerializers.INT);

    private Vec3 landing = Vec3.ZERO, takeoff = Vec3.ZERO;
    private double skyY;
    private boolean released, hidden, refunded;
    private int kills, slams;
    private final Set<UUID> impactHit = new HashSet<>(), waveHit = new HashSet<>(), finalHit = new HashSet<>();

    public GrandEntity(EntityType<? extends GrandEntity> type, Level level) {
        super(type, level);
    }

    public static GrandEntity create(Level level, LivingEntity caster) {
        var c = PanConfig.GRAND;
        GrandEntity e = new GrandEntity(ModEntities.GRAND.get(), level);
        e.setOwner(caster);
        Vec3 aim = Util.aimPoint(level, caster, c.range.get());
        Double gy = Util.findGround(level, aim.x, aim.y, aim.z);
        e.landing = gy != null ? new Vec3(aim.x, gy, aim.z) : aim;
        e.takeoff = caster.position();
        e.skyY = Math.min(e.landing.y + 90.0, level.getMaxBuildHeight() - 3.0);
        e.setPos(e.landing.x, e.landing.y, e.landing.z);
        e.entityData.set(DATA_START, BlockPos.containing(caster.position()));
        e.entityData.set(DATA_SKY, (int) e.skyY);
        e.entityData.set(DATA_R_CORE, c.coreRadius.get().floatValue());
        e.entityData.set(DATA_R_IMPACT, c.impactRadius.get().floatValue());
        e.entityData.set(DATA_R_WAVE, c.waveRadius.get().floatValue());
        e.entityData.set(DATA_R_FINAL, c.finalRadius.get().floatValue());
        e.entityData.set(DATA_ZONE, c.battlefieldTicks.get());
        e.entityData.set(DATA_SPEARS, c.spearStrikes.get());
        return e;
    }

    public Vec3 getTakeoff() {
        BlockPos p = entityData.get(DATA_START);
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    public double getSkyY() {
        return entityData.get(DATA_SKY);
    }

    public float getRCore() {
        return entityData.get(DATA_R_CORE);
    }

    public float getRImpact() {
        return entityData.get(DATA_R_IMPACT);
    }

    public float getRWave() {
        return entityData.get(DATA_R_WAVE);
    }

    public float getRFinal() {
        return entityData.get(DATA_R_FINAL);
    }

    public int getZoneTicks() {
        return entityData.get(DATA_ZONE);
    }

    public int getSpears() {
        return entityData.get(DATA_SPEARS);
    }

    /** Angle (radians) of the i-th giant spear around the landing point. */
    public static double spearAngle(int i, int n) {
        return Math.PI * 2 * i / Math.max(1, n) + 0.35;
    }

    public static int slamTime(int i) {
        return T_SLAM0 + SLAM_GAP * i;
    }

    public static int totalTicks(int zone) {
        return Math.max(T_FINAL + BEAM_TICKS, T_IMPACT + zone) + 4;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_START, BlockPos.ZERO);
        entityData.define(DATA_SKY, 100);
        entityData.define(DATA_R_CORE, 6f);
        entityData.define(DATA_R_IMPACT, 18f);
        entityData.define(DATA_R_WAVE, 25f);
        entityData.define(DATA_R_FINAL, 15f);
        entityData.define(DATA_ZONE, 160);
        entityData.define(DATA_SPEARS, 6);
    }

    // ------------------------------------------------------------------ damage helper

    private void dmg(LivingEntity owner, LivingEntity e, double flat, double pct, double factor) {
        double mult = factor;
        if (Util.isBoss(e)) {
            mult *= 1.0 + PanConfig.GRAND.bossBonusPercent.get() / 100.0;
        }
        if (Util.hurt(owner, e, flat, pct, mult, 1.0)) {
            kills++;
            var c = PanConfig.GRAND;
            if (!refunded && kills >= c.killsForRefund.get()) {
                refunded = true;
                Util.refund(owner, com.example.jjkspells.pantheon.registry.ModSpells.GRAND.get(), c.refundSeconds.get() * 20);
                if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.jjk_spells.pantheon.refund"), true);
                }
            }
        }
    }

    private void bossSlow(LivingEntity e) {
        var c = PanConfig.GRAND;
        if (Util.isBoss(e)) {
            Util.slowPct(e, c.bossSlowPercent.get(), c.bossSlowTicks.get());
        }
    }

    // ------------------------------------------------------------------ the sequence

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.pantheon.client.ClientFx.tickGrand(this));
            return;
        }
        LivingEntity owner = owner();
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        var c = PanConfig.GRAND;
        int age = tickCount - 1;
        long now = level().getGameTime();

        if (age == 0) {
            takeoff = owner.position();
            Util.LOCKED.add(owner.getUUID());
            Util.UNTOUCHABLE.add(owner.getUUID());
            Util.FALL_IMMUNE.put(owner.getUUID(), now + T_END + 400);
            Util.sound(level(), takeoff.x, takeoff.y, takeoff.z, ModSounds.CHARGE.get(), 1.6f, 0.8f);
            Util.fxDir(level(), ModParticles.RING.get(), takeoff.x, takeoff.y + 0.1, takeoff.z, 0, 0, 0);
        }

        if (age < T_UP) {
            phase1(owner, age);
        } else if (age < T_P3) {
            phase2(owner, age);
        } else if (age < T_IMPACT) {
            phase3(owner, age);
        }
        if (age == T_IMPACT) {
            impact(owner);
        }
        if (age > T_IMPACT && age < T_END && !hidden) {
            owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
        }
        if (age >= T_SLAM0 && slams < c.spearStrikes.get() && age >= slamTime(slams)) {
            slam(owner, slams);
            slams++;
        }
        if (age >= T_WAVE && age <= T_WAVE + WAVE_TICKS) {
            wave(owner, age);
        }
        phase6(owner, age);
        if (age >= T_IMPACT && age < T_IMPACT + c.battlefieldTicks.get()) {
            battlefield(owner, age, now);
        }
        if (age == T_END) {
            mortalWill(owner, now);
            release(owner);
        }
        if (age >= totalTicks(c.battlefieldTicks.get())) {
            finish(true);
            discard();
        }
    }

    private void phase1(LivingEntity owner, int age) {
        Vec3 s = takeoff;
        owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
        owner.hurtMarked = true;
        if (age % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 1.5 + random.nextDouble() * 5.0;
                Util.fxDir(level(), random.nextInt(3) == 0 ? ModParticles.FLAME.get() : ModParticles.SPARK.get(), s.x + Math.cos(a) * d, s.y + 0.1, s.z + Math.sin(a) * d,
                        0, 0.1 + random.nextDouble() * 0.18, 0);
            }
        }
        if (age == 6) {
            Vec3 st = s.add(0, 14, 0);
            Util.sound(level(), st.x, st.y, st.z, ModSounds.STAR.get(), 1.6f, 0.9f);
            Util.fx(level(), ModParticles.STAR.get(), st.x, st.y, st.z, 8, 0.6, 0.6, 0.6, 0.02);
            Util.fx(level(), ModParticles.FLASH.get(), st.x, st.y, st.z, 1, 0, 0, 0, 0);
        }
        if (age == T_THRUST) {
            Util.swing(owner);
            Util.sound(level(), s.x, s.y + 1, s.z, ModSounds.BEAM.get(), 1.8f, 1.0f);
            Util.fx(level(), ModParticles.FLASH.get(), s.x, s.y + 2, s.z, 1, 0, 0, 0, 0);
        }
        if (age >= T_THRUST) {
            Util.column(level(), s.x, s.y, s.z, 46, 30);
        }
    }

    private void phase2(LivingEntity owner, int age) {
        Vec3 s = takeoff;
        if (age < T_HIDE) {
            double f = (age - T_UP + 1) / (double) (T_HIDE - T_UP);
            double top = Math.min(s.y + 40.0, level().getMaxBuildHeight() - 3.0);
            Util.snapTo(owner, new Vec3(s.x, Mth.lerp(f * f, s.y, top), s.z), owner.getYRot(), -50f);
            owner.setNoGravity(true);
            Util.column(level(), s.x, s.y, s.z, 46, 22);
            Util.fx(level(), ModParticles.FLAME.get(), owner.getX(), owner.getY(), owner.getZ(), 5, 0.4, 0.2, 0.4, 0.02);
            Util.fx(level(), ModParticles.SPARK.get(), owner.getX(), owner.getY() + 1, owner.getZ(), 6, 0.6, 0.8, 0.6, 0.05);
            if (age == T_UP) {
                Util.sound(level(), s.x, s.y, s.z, ModSounds.FLAME.get(), 1.6f, 0.8f);
            }
        }
        if (age == T_HIDE) {
            // the blinding flash, then the caster waits unseen above the landing point
            Vec3 up = new Vec3(owner.getX(), owner.getY(), owner.getZ());
            Util.fx(level(), ModParticles.FLASH.get(), up.x, up.y, up.z, 1, 0, 0, 0, 0);
            Util.fx(level(), ModParticles.STAR.get(), up.x, up.y, up.z, 16, 1.0, 1.0, 1.0, 0.25);
            Util.sound(level(), up.x, up.y, up.z, ModSounds.STAR.get(), 2.0f, 1.3f);
            Util.sound(level(), up.x, up.y, up.z, ModSounds.BOOM.get(), 1.0f, 1.8f);
            hide(owner);
        }
        if (age > T_HIDE) {
            keepHidden(owner, age);
        }
    }

    private void hide(LivingEntity owner) {
        hidden = true;
        Util.HIDDEN.add(owner.getUUID());
        owner.setInvisible(true);
        owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
        owner.setNoGravity(true);
        Util.snapTo(owner, new Vec3(landing.x, skyY, landing.z), owner.getYRot(), 60f);
    }

    private void keepHidden(LivingEntity owner, int age) {
        if (age % 10 == 0) {
            owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
        }
        Vec3 want = new Vec3(landing.x, skyY, landing.z);
        if (owner.position().distanceToSqr(want) > 4.0) {
            Util.snapTo(owner, want, owner.getYRot(), 60f);
        } else {
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
        }
    }

    private void phase3(LivingEntity owner, int age) {
        keepHidden(owner, age);
        if (age == T_P3 + 2) {
            Util.sound(level(), landing.x, landing.y + 20, landing.z, ModSounds.DIVE.get(), 3.0f, 0.7f);
            Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.AEGIS.get(), 2.0f, 0.6f);
        }
        if (age == T_METEOR + 4) {
            Util.sound(level(), landing.x, landing.y + 30, landing.z, ModSounds.BEAM.get(), 3.0f, 0.8f);
        }
        if (age >= T_METEOR) {
            double f = (age - T_METEOR) / (double) (T_IMPACT - T_METEOR);
            double e = f * f;
            double y = landing.y + (skyY - landing.y) * (1.0 - e);
            Util.fx(level(), ModParticles.FLAME.get(), landing.x, y, landing.z, 10, 1.2, 1.2, 1.2, 0.05);
            Util.fx(level(), ModParticles.SPARK.get(), landing.x, y, landing.z, 12, 1.6, 1.6, 1.6, 0.2);
            Util.column(level(), landing.x, y, landing.z, 14, 10);
        }
    }

    private void impact(LivingEntity owner) {
        var c = PanConfig.GRAND;
        hidden = false;
        Util.HIDDEN.remove(owner.getUUID());
        owner.setInvisible(false);
        owner.removeEffect(MobEffects.INVISIBILITY);
        owner.setNoGravity(false);
        Util.snapTo(owner, landing.add(0, 0.02, 0), owner.getYRot(), 45f);
        Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.BOOM.get(), 4.0f, 0.7f);
        Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.DIVINE.get(), 4.0f, 0.9f);
        Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.BEAM.get(), 3.0f, 1.0f);
        Util.blast(level(), landing.x, landing.y, landing.z, 12.0, 120);
        Util.fxDir(level(), ModParticles.RING.get(), landing.x, landing.y + 0.1, landing.z, 0, 0, 0);
        for (int k = 0; k < 4; k++) {
            Util.ring(level(), ModParticles.SPARK.get(), landing.add(0, 0.2, 0), 72, 0.6 + k * 0.45, 0.04);
        }
        Util.ring(level(), ModParticles.FLAME.get(), landing.add(0, 0.2, 0), 60, 0.8, 0.05);
        Util.ring(level(), ModParticles.DUST.get(), landing.add(0, 0.2, 0), 48, 1.0, 0.04);
        Util.column(level(), landing.x, landing.y, landing.z, 60, 240);
        for (int i = 0; i < 40; i++) {
            Util.fxDir(level(), ModParticles.SHARD.get(), landing.x + (random.nextDouble() - 0.5) * 8, landing.y + 0.2, landing.z + (random.nextDouble() - 0.5) * 8,
                    (random.nextDouble() - 0.5) * 0.9, 0.4 + random.nextDouble() * 0.7, (random.nextDouble() - 0.5) * 0.9);
        }
        double core = c.coreRadius.get();
        for (LivingEntity e : Util.enemies(level(), landing, c.impactRadius.get(), owner)) {
            if (!impactHit.add(e.getUUID())) {
                continue;
            }
            double d = Math.hypot(e.getX() - landing.x, e.getZ() - landing.z);
            double factor = d <= core ? 1.0 : c.outerFactor.get();
            dmg(owner, e, c.impactFlat.get(), c.impactPercent.get(), factor);
            if (e.isAlive()) {
                Util.launch(e, c.launchSpeed.get());
                if (Util.isBoss(e)) {
                    bossSlow(e);
                } else {
                    Util.stun(e, 20);
                }
            }
        }
    }

    private void slam(LivingEntity owner, int i) {
        var c = PanConfig.GRAND;
        double a = spearAngle(i, c.spearStrikes.get());
        Vec3 p = new Vec3(landing.x + Math.cos(a) * SPEAR_RING, landing.y, landing.z + Math.sin(a) * SPEAR_RING);
        Double g = Util.findGround(level(), p.x, p.y + 2, p.z);
        if (g != null) {
            p = new Vec3(p.x, g, p.z);
        }
        Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 1.6f, 1.0f + 0.05f * i);
        Util.sound(level(), p.x, p.y, p.z, ModSounds.THRUST.get(), 1.2f, 0.6f);
        Util.blast(level(), p.x, p.y, p.z, c.spearBlastRadius.get() * 1.4, 40);
        Util.fxDir(level(), ModParticles.RING.get(), p.x, p.y + 0.1, p.z, 0, 0, 0);
        Util.column(level(), p.x, p.y, p.z, 16, 50);
        for (LivingEntity e : Util.enemies(level(), p, c.spearBlastRadius.get(), owner)) {
            dmg(owner, e, c.spearBlastFlat.get(), 0, 1.0);
        }
    }

    private void wave(LivingEntity owner, int age) {
        var c = PanConfig.GRAND;
        double f = Mth.clamp((age - T_WAVE + 1) / (double) WAVE_TICKS, 0.0, 1.0);
        double r = c.waveRadius.get() * f;
        if (age == T_WAVE) {
            Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.DIVINE.get(), 4.0f, 1.2f);
        }
        int n = (int) (8 + r * 3);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + age;
            Util.fxDir(level(), i % 2 == 0 ? ModParticles.FLAME.get() : ModParticles.SPARK.get(), landing.x + Math.cos(a) * r, landing.y + 0.2, landing.z + Math.sin(a) * r,
                    Math.cos(a) * 0.25, 0.05, Math.sin(a) * 0.25);
        }
        for (LivingEntity e : Util.enemies(level(), landing, r, owner)) {
            if (waveHit.add(e.getUUID())) {
                dmg(owner, e, c.waveFlat.get(), c.wavePercent.get(), 1.0);
                if (e.isAlive()) {
                    Util.knock(e, landing, 2.0, 0.25);
                    bossSlow(e);
                }
            }
        }
    }

    private void phase6(LivingEntity owner, int age) {
        var c = PanConfig.GRAND;
        if (age < T_RAISE || age > T_END) {
            return;
        }
        Vec3 pos = owner.position();
        if (age == T_RAISE) {
            Util.swing(owner);
            Util.sound(level(), pos.x, pos.y, pos.z, ModSounds.CHARGE.get(), 2.5f, 1.2f);
        }
        if (age < T_DRIVE) {
            // the surrounding flames rush towards the spearhead
            Vec3 head = pos.add(0, 2.6, 0);
            for (int i = 0; i < 12; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 6 + random.nextDouble() * 9;
                Vec3 p = new Vec3(pos.x + Math.cos(a) * d, pos.y + 0.3 + random.nextDouble() * 4, pos.z + Math.sin(a) * d);
                Vec3 v = head.subtract(p).scale(0.11);
                Util.fxDir(level(), random.nextInt(3) == 0 ? ModParticles.SPARK.get() : ModParticles.FLAME.get(), p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
        if (age == T_DRIVE) {
            Util.swing(owner);
            Util.sound(level(), pos.x, pos.y, pos.z, ModSounds.BASH.get(), 2.5f, 0.7f);
            Util.sound(level(), pos.x, pos.y, pos.z, ModSounds.AEGIS.get(), 2.0f, 0.5f);
            Util.fx(level(), ModParticles.FLASH.get(), pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
            Util.fxDir(level(), ModParticles.RING.get(), pos.x, pos.y + 0.1, pos.z, 0, 0, 0);
        }
        if (age >= T_DRIVE && age < T_COLLAPSE) {
            double r = c.finalRadius.get() * (age - T_DRIVE + 1) / (double) (T_COLLAPSE - T_DRIVE);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24 + age * 0.3;
                Util.fxDir(level(), ModParticles.SPARK.get(), landing.x + Math.cos(a) * r, landing.y + 0.3, landing.z + Math.sin(a) * r, Math.cos(a) * 0.1, 0.18, Math.sin(a) * 0.1);
            }
        }
        if (age >= T_COLLAPSE && age < T_FINAL) {
            double r = c.finalRadius.get() * (1.0 - (age - T_COLLAPSE + 1) / (double) (T_FINAL - T_COLLAPSE));
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24 - age * 0.3;
                Util.fxDir(level(), ModParticles.FLAME.get(), landing.x + Math.cos(a) * r, landing.y + 0.3, landing.z + Math.sin(a) * r, -Math.cos(a) * 0.3, 0.12, -Math.sin(a) * 0.3);
            }
        }
        if (age == T_FINAL) {
            Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.BOOM.get(), 4.5f, 0.6f);
            Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.DIVINE.get(), 4.0f, 0.7f);
            Util.sound(level(), landing.x, landing.y, landing.z, ModSounds.BEAM.get(), 4.0f, 0.9f);
            Util.blast(level(), landing.x, landing.y, landing.z, 14.0, 140);
            Util.fxDir(level(), ModParticles.RING.get(), landing.x, landing.y + 0.1, landing.z, 0, 0, 0);
            for (int k = 0; k < 3; k++) {
                Util.ring(level(), ModParticles.SPARK.get(), landing.add(0, 0.3, 0), 72, 0.7 + k * 0.5, 0.05);
            }
            Util.column(level(), landing.x, landing.y, landing.z, 70, 260);
            for (LivingEntity e : Util.enemies(level(), landing, c.finalRadius.get(), owner)) {
                if (finalHit.add(e.getUUID())) {
                    dmg(owner, e, c.finalFlat.get(), c.finalPercent.get(), 1.0);
                    if (e.isAlive()) {
                        Util.launch(e, 0.6);
                        if (Util.isBoss(e)) {
                            bossSlow(e);
                        } else {
                            Util.stun(e, 20);
                        }
                    }
                }
            }
        }
        if (age > T_FINAL && age < T_FINAL + BEAM_TICKS && age % 3 == 0) {
            Util.column(level(), landing.x, landing.y, landing.z, 60, 24);
        }
    }

    /** Celestial Battlefield: fire all around, burning, slowing and cutting healing of everything that is not the caster's. */
    private void battlefield(LivingEntity owner, int age, long now) {
        var c = PanConfig.GRAND;
        double r = c.waveRadius.get();
        if (age % 4 == 0) {
            for (int i = 0; i < 14; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                Util.fxDir(level(), ModParticles.FLAME.get(), landing.x + Math.cos(a) * d, landing.y + 0.1, landing.z + Math.sin(a) * d, 0, 0.04 + random.nextDouble() * 0.05, 0);
            }
        }
        if (age % 10 == 0 || age == T_IMPACT) {
            for (LivingEntity e : Util.enemies(level(), landing, r, owner)) {
                Util.HEAL_CUT.put(e.getUUID(), new double[]{c.zoneHealReduction.get() / 100.0, now + 14});
                Util.slowPct(e, c.zoneSlowPercent.get(), 14);
            }
        }
        if ((age - T_IMPACT) % 20 == 10) {
            for (LivingEntity e : Util.enemies(level(), landing, r, owner)) {
                dmg(owner, e, c.zoneDamage.get(), 0, 1.0);
                Util.fx(level(), ModParticles.FLAME.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 5, 0.3, 0.4, 0.3, 0.03);
            }
        }
    }

    private void mortalWill(LivingEntity owner, long now) {
        var c = PanConfig.GRAND;
        int t = c.buffTicks.get();
        if (t <= 0) {
            return;
        }
        Util.reduce(owner, c.buffReduction.get() / 100.0, t);
        Util.MELEE_BONUS.put(owner.getUUID(), new double[]{c.buffMelee.get() / 100.0, now + t});
        Util.KB_IMMUNE.put(owner.getUUID(), now + t);
        owner.addEffect(new MobEffectInstance(ModEffects.UNBROKEN.get(), t, 0, false, false, true));
        Util.sound(level(), owner.getX(), owner.getY() + 1, owner.getZ(), ModSounds.STAR.get(), 1.5f, 0.8f);
        Util.fx(level(), ModParticles.FLASH.get(), owner.getX(), owner.getY() + 1, owner.getZ(), 1, 0, 0, 0, 0);
    }

    private void release(LivingEntity owner) {
        released = true;
        Util.LOCKED.remove(owner.getUUID());
        Util.UNTOUCHABLE.remove(owner.getUUID());
        Util.HIDDEN.remove(owner.getUUID());
        owner.setNoGravity(false);
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
            Util.HIDDEN.remove(ownerId);
        }
        if (released) {
            return;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            owner.setNoGravity(false);
            owner.setInvisible(false);
            owner.removeEffect(MobEffects.INVISIBILITY);
            Util.FALL_IMMUNE.put(owner.getUUID(), sl.getGameTime() + 400);
            // interrupted while up in the sky: come down to the landing point, never into a wall
            if (hidden || owner.getY() > landing.y + 3.0 || !owner.level().noCollision(owner, owner.getBoundingBox())) {
                Vec3 safe = Util.safeNear(owner.level(), owner, landing.add(0, 0.5, 0), 1.0);
                Util.snapTo(owner, safe != null ? safe : landing.add(0, 0.5, 0), owner.getYRot(), owner.getXRot());
            }
        }
    }
}
