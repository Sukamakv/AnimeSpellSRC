package com.example.jjkspells.malenia.entity;

import com.example.jjkspells.malenia.config.MelConfig;
import com.example.jjkspells.malenia.registry.ModEffects;
import com.example.jjkspells.malenia.registry.ModEntities;
import com.example.jjkspells.malenia.registry.ModParticles;
import com.example.jjkspells.malenia.registry.ModSounds;
import com.example.jjkspells.malenia.registry.ModSpells;
import com.example.jjkspells.malenia.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Goddess of Rot - The Final Aeonia. One clock (age = tickCount - 1), 12 seconds of sequence, then the Goddess of Rot state:
 * <pre>
 *  0..T_WINGS    awakening
 *  T_WINGS..T_RISE  wing transformation (red flash)
 *  T_RISE..T_DIVE   ascend 18 blocks, rune, mist, targeting circle
 *  T_DIVE..T_IMPACT dive; impact, launch, Scarlet Rot Catastrophe, Aeonian Battlefield begins
 *  ..T_BLOOM     the colossal flower grows; T_BLOOM explosion
 *  ..T_JUDG      eight sword projections slam one after another; T_JUDG judgment wave
 *  ..T_FINAL     the flower curls in, the rot gathers into a sphere; T_FINAL detonation, the buff begins
 *  ..T_END       descent; the wings stay for as long as the state lasts
 * </pre>
 */
public class GoddessEntity extends MelEntityBase {
    public static final int T_WINGS = 30, T_RISE = 60, T_DIVE = 90, T_IMPACT = 110, T_BLOOM = 140, T_SWORD0 = 144, SWORD_GAP = 4, T_JUDG = 178, T_FINAL = 220, T_END = 240;
    public static final int SWORDS = 8;

    private static final EntityDataAccessor<Float> DATA_PX = SynchedEntityData.defineId(GoddessEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PY = SynchedEntityData.defineId(GoddessEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PZ = SynchedEntityData.defineId(GoddessEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(GoddessEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WINGS_END = SynchedEntityData.defineId(GoddessEntity.class, EntityDataSerializers.INT);

    private Vec3 start, top, point;
    private int kills, buffTicks;
    private boolean killBonus;
    private long buffUntil;

    public GoddessEntity(EntityType<? extends GoddessEntity> type, Level level) {
        super(type, level);
    }

    public static GoddessEntity create(Level level, LivingEntity caster, Vec3 point) {
        GoddessEntity e = new GoddessEntity(ModEntities.GODDESS.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.point = point;
        e.entityData.set(DATA_PX, (float) point.x);
        e.entityData.set(DATA_PY, (float) point.y);
        e.entityData.set(DATA_PZ, (float) point.z);
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        e.buffTicks = (int) Math.round(MelConfig.GODDESS.buffSeconds.get() * 20);
        e.entityData.set(DATA_WINGS_END, T_FINAL + e.buffTicks);
        return e;
    }

    public Vec3 getPoint() {
        return new Vec3(entityData.get(DATA_PX), entityData.get(DATA_PY), entityData.get(DATA_PZ));
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    /** Age at which the wings (and this entity) are gone. */
    public int getWingsEnd() {
        return entityData.get(DATA_WINGS_END);
    }

    /** Where the i-th sword projection slams: deterministic so the client draws it without being told. */
    public static Vec3 swordPos(int seed, int i, Vec3 p, double maxR) {
        double a = ((seed * 31L + i * 977L) % 3600) / 3600.0 * Math.PI * 2 + i * 2.4;
        double r = (0.25 + 0.75 * (((seed * 17L + i * 131L) % 100) / 100.0)) * Math.min(maxR, 18.0);
        return new Vec3(p.x + Math.cos(a) * r, p.y, p.z + Math.sin(a) * r);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PX, 0f);
        entityData.define(DATA_PY, 0f);
        entityData.define(DATA_PZ, 0f);
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_WINGS_END, 400);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.malenia.client.ClientFx.tickGoddess(this));
            return;
        }
        var c = MelConfig.GODDESS;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        boolean casting = age < T_END;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        if (age == 0) {
            start = owner.position();
            top = start.add(0, 18, 0);
            Util.LOCKED.add(owner.getUUID());
            lsCap = Double.MAX_VALUE;
            Util.reduce(owner, c.protectReductionPercent.get() / 100.0, T_RISE + 2);
            Util.kbImmune(owner, T_END + 6);
            Util.ccImmune(owner, T_END + 6);
            Util.FALL_IMMUNE.put(owner.getUUID(), owner.level().getGameTime() + T_END + 100);
            owner.addEffect(new MobEffectInstance(ModEffects.GODDESS.get(), T_WINGS + 20, 0, false, false, true));
            Util.sound(level(), start.x, start.y, start.z, ModSounds.HUM.get(), 1.6f, 0.7f);
        }
        if (casting) {
            owner.setSprinting(false);
            setPos(owner.getX(), owner.getY(), owner.getZ());
            moveCaster(owner, age);
        }
        // awakening and transformation
        if (age < T_RISE && age % 2 == 0) {
            Vec3 p = owner.position();
            double k = age < T_WINGS ? 1.0 : 2.0;
            Util.fx(level(), ModParticles.PETAL.get(), p.x, p.y + 1.0, p.z, (int) (3 * k), 1.2, 1.0, 1.2, 0.06);
            Util.fx(level(), ModParticles.SPORE.get(), p.x, p.y + 0.5, p.z, (int) (2 * k), 1.0, 0.6, 1.0, 0.05);
            Util.fx(level(), ModParticles.BUTTERFLY.get(), p.x, p.y + 1.4, p.z, 1, 1.0, 1.0, 1.0, 0.03);
        }
        if (age == T_WINGS) {
            Vec3 p = owner.position();
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TRANSFORM.get(), 1.8f, 1.0f);
            Util.fx(level(), ModParticles.FLASH.get(), p.x, p.y + 1.2, p.z, 3, 0.1, 0.1, 0.1, 0);
            Util.ring(level(), ModParticles.PETAL.get(), p.add(0, 1.0, 0), 40, 0.5, 0.05);
        }
        if (age == T_WINGS + 14) {
            Vec3 p = owner.position();
            Util.sound(level(), p.x, p.y, p.z, ModSounds.WINGS.get(), 1.6f, 0.9f);
        }
        if (age >= T_RISE && age < T_IMPACT && age % 3 == 0) {
            Vec3 p = owner.position();
            Util.fx(level(), ModParticles.PETAL.get(), p.x, p.y + 1.0, p.z, 3, 0.8, 0.9, 0.8, 0.05);
            Util.fx(level(), ModParticles.ROT.get(), point.x, point.y + 0.3, point.z, 4, c.impactRadius.get() * 0.5, 0.1, c.impactRadius.get() * 0.5, 0.02);
            Util.fx(level(), ModParticles.SPORE.get(), point.x, point.y + 0.4, point.z, 4, c.impactRadius.get() * 0.5, 0.3, c.impactRadius.get() * 0.5, 0.04);
        }
        if (age == T_RISE) {
            Util.sound(level(), start.x, start.y, start.z, ModSounds.WINGS.get(), 1.8f, 0.7f);
        }
        if (age == T_IMPACT) {
            impact(owner);
        }
        if (age >= T_IMPACT && age < T_BLOOM && age % 2 == 0) {
            double u = smooth((age - T_IMPACT) / (double) (T_BLOOM - T_IMPACT));
            double r = c.bloomRadius.get() * u;
            for (int i = 0; i < 5; i++) {
                double a = level().random.nextDouble() * Math.PI * 2;
                Util.fxDir(level(), level().random.nextBoolean() ? ModParticles.PETAL.get() : ModParticles.SPORE.get(), point.x + Math.cos(a) * r, point.y + 0.3, point.z + Math.sin(a) * r,
                        Math.cos(a) * 0.06, 0.14, Math.sin(a) * 0.06);
            }
        }
        if (age == T_IMPACT + 4) {
            Util.sound(level(), point.x, point.y, point.z, ModSounds.BLOOM.get(), 1.8f, 0.8f);
        }
        if (age == T_BLOOM) {
            bloomBlast(owner);
        }
        for (int k = 0; k < SWORDS; k++) {
            if (age == T_SWORD0 + k * SWORD_GAP) {
                swordSlam(owner, k);
            }
        }
        if (age == T_JUDG) {
            judgment(owner);
        }
        if (age > T_JUDG && age < T_FINAL && age % 2 == 0) {
            // the rot is pulled into a sphere
            double u = (age - T_JUDG) / (double) (T_FINAL - T_JUDG);
            double r = c.bloomRadius.get() * (1.0 - u);
            Vec3 core = point.add(0, 3.5, 0);
            for (int i = 0; i < 4; i++) {
                double a = level().random.nextDouble() * Math.PI * 2;
                double px = point.x + Math.cos(a) * r, pz = point.z + Math.sin(a) * r;
                Vec3 d = new Vec3(core.x - px, core.y - point.y - 0.3, core.z - pz).normalize().scale(0.35);
                Util.fxDir(level(), level().random.nextBoolean() ? ModParticles.ROT.get() : ModParticles.SPORE.get(), px, point.y + 0.3, pz, d.x, d.y, d.z);
            }
        }
        if (age == T_FINAL - 14) {
            Util.sound(level(), point.x, point.y, point.z, ModSounds.HUM.get(), 1.8f, 1.4f);
        }
        if (age == T_FINAL) {
            finalBlast(owner);
        }
        if (age >= T_IMPACT && age < T_IMPACT + (int) (c.battlefieldSeconds.get() * 20) && (age - T_IMPACT) % 20 == 10) {
            battlefield(owner);
        }
        if (age == T_END) {
            releaseCommon(true);
        }
        // the Goddess of Rot state: watch for the kill bonus; end when it runs out
        if (age >= T_FINAL) {
            if (!killBonus && kills >= c.killCount.get()) {
                undefeated(owner);
            }
            if (age > T_END && owner.level().getGameTime() > buffUntil) {
                finish(true);
                discard();
            }
        }
    }

    private void moveCaster(LivingEntity owner, int age) {
        Vec3 p;
        Vec3 look = point;
        if (age < T_RISE) {
            p = start;
        } else if (age < T_DIVE) {
            p = start.lerp(top, smooth((age - T_RISE) / (double) (T_DIVE - T_RISE)));
        } else if (age < T_IMPACT) {
            double u = (age - T_DIVE + 1) / (double) (T_IMPACT - T_DIVE);
            p = top.lerp(point, u * u);
        } else if (age < T_JUDG) {
            p = point;
        } else if (age < T_FINAL) {
            p = point.add(0, 3.0 * smooth((age - T_JUDG) / 20.0), 0);
        } else {
            double u = smooth((age - T_FINAL) / (double) (T_END - T_FINAL));
            p = point.add(0, 3.0 * (1.0 - u), 0);
        }
        float yaw = owner.getYRot();
        if (age < T_DIVE) {
            yaw = Util.yawTo(p, new Vec3(look.x, p.y, look.z));
        }
        hover(owner, p, yaw, age >= T_RISE && age < T_IMPACT ? 55f : 0f);
        if (age >= T_END - 4) {
            owner.setNoGravity(false);
        }
    }

    private double bossFactor(LivingEntity e) {
        return Util.isBoss(e) ? MelConfig.GODDESS.bossPercentFactor.get() : 1.0;
    }

    /** One damaging blast: everything inside takes flat + percent, then the catastrophe rot. Returns the number hit. */
    private int blast(LivingEntity owner, Vec3 at, double radius, double flat, double pct, boolean rot) {
        var c = MelConfig.GODDESS;
        int n = 0;
        for (LivingEntity e : Util.enemies(level(), at, radius, owner)) {
            boolean dead = Util.hurt(owner, e, flat, pct, 1.0, bossFactor(e));
            n++;
            if (dead) {
                kills++;
            } else if (rot) {
                catastrophe(owner, e);
            }
        }
        return n;
    }

    private void catastrophe(LivingEntity owner, LivingEntity e) {
        var c = MelConfig.GODDESS;
        Util.rot(owner, e, c.rotDps.get(), c.rotSeconds.get(), c.rotHealCutPercent.get(), c.rotSlowPercent.get());
    }

    private void impact(LivingEntity owner) {
        var c = MelConfig.GODDESS;
        Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 2.0f, 0.7f);
        Util.bloomBurst(level(), point.x, point.y, point.z, c.impactRadius.get(), 120);
        Util.ring(level(), ModParticles.PETAL.get(), point.add(0, 0.5, 0), 64, 0.8, 0.08);
        Util.ring(level(), ModParticles.ROT.get(), point.add(0, 0.5, 0), 40, 0.6, 0.04);
        Util.fxDir(level(), ModParticles.RING.get(), point.x, point.y + 0.5, point.z, 0, 0, 0);
        Util.fx(level(), ModParticles.FLASH.get(), point.x, point.y + 1.0, point.z, 3, 0.2, 0.2, 0.2, 0);
        for (LivingEntity e : Util.enemies(level(), point, c.impactRadius.get(), owner)) {
            boolean dead = Util.hurt(owner, e, c.impactFlat.get(), c.impactPercent.get(), 1.0, bossFactor(e));
            if (dead) {
                kills++;
            } else {
                Util.launchBlocks(e, 5.0);
                catastrophe(owner, e);
            }
        }
    }

    private void bloomBlast(LivingEntity owner) {
        var c = MelConfig.GODDESS;
        Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 2.0f, 0.9f);
        Util.bloomBurst(level(), point.x, point.y, point.z, c.bloomRadius.get(), 140);
        Util.ring(level(), ModParticles.PETAL.get(), point.add(0, 0.8, 0), 80, 1.0, 0.1);
        Util.ring(level(), ModParticles.GOLD.get(), point.add(0, 1.0, 0), 40, 0.6, 0.1);
        Util.fxDir(level(), ModParticles.RING.get(), point.x, point.y + 0.5, point.z, 0, 0, 0);
        blast(owner, point, c.bloomRadius.get(), c.bloomFlat.get(), c.bloomPercent.get(), true);
    }

    private void swordSlam(LivingEntity owner, int k) {
        var c = MelConfig.GODDESS;
        Vec3 q = swordPos(getSeed(), k, point, c.bloomRadius.get());
        Double g = Util.findGround(level(), q.x, q.y + 4, q.z);
        if (g != null) {
            q = new Vec3(q.x, g, q.z);
        }
        Util.sound(level(), q.x, q.y, q.z, ModSounds.STAB.get(), 1.6f, 0.7f + 0.05f * k);
        Util.sound(level(), q.x, q.y, q.z, ModSounds.BOOM.get(), 1.0f, 1.2f);
        Util.bloomBurst(level(), q.x, q.y, q.z, c.swordRadius.get(), 30);
        Util.bladeBurst(level(), q.x, q.y + 1, q.z, 12);
        Util.fxDir(level(), ModParticles.RING.get(), q.x, q.y + 0.4, q.z, 0, 0, 0);
        blast(owner, q, c.swordRadius.get(), c.swordFlat.get(), 0, false);
    }

    private void judgment(LivingEntity owner) {
        var c = MelConfig.GODDESS;
        Util.sound(level(), point.x, point.y, point.z, ModSounds.RING.get(), 2.0f, 0.7f);
        Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 1.8f, 0.8f);
        Util.ring(level(), ModParticles.SILVER.get(), point.add(0, 0.8, 0), 80, 0.9, 0.04);
        Util.ring(level(), ModParticles.GOLD.get(), point.add(0, 1.0, 0), 50, 0.7, 0.06);
        Util.fxDir(level(), ModParticles.RING.get(), point.x, point.y + 0.6, point.z, 0, 0, 0);
        Util.fx(level(), ModParticles.FLASH.get(), point.x, point.y + 1.0, point.z, 2, 0.2, 0.2, 0.2, 0);
        blast(owner, point, c.judgmentRadius.get(), c.judgmentFlat.get(), c.judgmentPercent.get(), true);
    }

    private void finalBlast(LivingEntity owner) {
        var c = MelConfig.GODDESS;
        Vec3 core = point.add(0, 3.5, 0);
        Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 2.5f, 0.6f);
        Util.sound(level(), point.x, point.y, point.z, ModSounds.TRANSFORM.get(), 2.0f, 0.8f);
        Util.bloomBurst(level(), core.x, core.y, core.z, c.bloomRadius.get() * 0.8, 160);
        Util.ring(level(), ModParticles.PETAL.get(), point.add(0, 1.0, 0), 100, 1.2, 0.1);
        Util.ring(level(), ModParticles.ROT.get(), point.add(0, 0.8, 0), 60, 0.9, 0.05);
        Util.ring(level(), ModParticles.BUTTERFLY.get(), point.add(0, 1.2, 0), 24, 0.3, 0.1);
        Util.fxDir(level(), ModParticles.RING.get(), point.x, point.y + 0.6, point.z, 0, 0, 0);
        Util.fx(level(), ModParticles.FLASH.get(), core.x, core.y, core.z, 4, 0.3, 0.3, 0.3, 0);
        blast(owner, point, c.bloomRadius.get(), c.finalFlat.get(), c.finalPercent.get(), true);
        // the Goddess of Rot state
        applyBuff(owner, buffTicks);
    }

    private void applyBuff(LivingEntity owner, int ticks) {
        var c = MelConfig.GODDESS;
        long now = owner.level().getGameTime();
        buffUntil = now + ticks;
        Util.MELEE_BONUS.put(owner.getUUID(), new double[]{c.buffMeleePercent.get() / 100.0, buffUntil});
        Util.hastePct(owner, c.buffSpeedPercent.get(), ticks);
        Util.lifestealBonus(owner, c.buffLifestealPercent.get(), ticks);
        Util.reduce(owner, c.buffReductionPercent.get() / 100.0, ticks);
        owner.addEffect(new MobEffectInstance(ModEffects.GODDESS.get(), ticks, 0, false, false, true));
        entityData.set(DATA_WINGS_END, tickCount - 1 + ticks + 20);
    }

    private void battlefield(LivingEntity owner) {
        var c = MelConfig.GODDESS;
        List<LivingEntity> inside = Util.enemies(level(), point, c.bloomRadius.get(), owner);
        for (LivingEntity e : inside) {
            if (Util.hurt(owner, e, c.battlefieldDps.get(), 0)) {
                kills++;
                continue;
            }
            Util.slowPct(e, c.battlefieldSlowPercent.get(), 30);
        }
        Util.sound(level(), point.x, point.y, point.z, ModSounds.ROT.get(), 0.7f, 0.8f);
        Util.fx(level(), ModParticles.ROT.get(), point.x, point.y + 0.3, point.z, 6, c.bloomRadius.get() * 0.6, 0.1, c.bloomRadius.get() * 0.6, 0.01);
    }

    /** Undefeated Goddess: five kills reset Waterfowl Dance, heal and lengthen the state (once). */
    private void undefeated(LivingEntity owner) {
        var c = MelConfig.GODDESS;
        killBonus = true;
        Util.refundFraction(owner, ModSpells.WATERFOWL.get(), 1.0);
        owner.heal((float) (owner.getMaxHealth() * c.killHealPercent.get() / 100.0));
        long left = Math.max(0, buffUntil - owner.level().getGameTime());
        applyBuff(owner, (int) left + (int) Math.round(c.killExtraSeconds.get() * 20));
        Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.TRANSFORM.get(), 1.4f, 1.3f);
        Util.fx(level(), ModParticles.BUTTERFLY.get(), owner.getX(), owner.getY() + 1.2, owner.getZ(), 12, 1.0, 1.0, 1.0, 0.05);
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        releaseCommon(normal);
        if (!normal && ownerId != null) {
            LivingEntity o = ownerAny();
            if (o != null && !o.isAlive()) {
                Util.MELEE_BONUS.remove(ownerId);
                Util.LS_BONUS.remove(ownerId);
            }
        }
    }
}
