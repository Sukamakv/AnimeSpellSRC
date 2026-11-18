package com.example.jjkspells.zegion.entity;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.registry.ModEffects;
import com.example.jjkspells.zegion.registry.ModEntities;
import com.example.jjkspells.zegion.registry.ModParticles;
import com.example.jjkspells.zegion.registry.ModSounds;
import com.example.jjkspells.zegion.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.vfx.sprite.SpriteFx;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phantom Kaiser - The Infinite Execution. Sits on the primary target; one clock (age = tickCount - 1):
 * <pre>
 *  0..VANISH         predator's silence (locked)
 *  VANISH            Zegion vanishes, afterimages appear around the enemies
 *  VANISH..ASSAULT   up to 24 strikes, Zegion blinking from target to target
 *  ASSAULT..GATHER   the afterimages circle the primary target and charge
 *  GATHER            Zegion appears above the target
 *  DROP              the dimensional finisher
 *  BLAST             every afterimage detonates, Zegion lands in the centre
 *  END
 * </pre>
 */
public class AssaultEntity extends ZegEntityBase {
    public static final int SILENCE = 12, VANISH = 24, ASSAULT = 80, GATHER = 110, DROP = 120, BLAST = 130, END = 144;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(AssaultEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STRIKES = SynchedEntityData.defineId(AssaultEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STRIKE_AGE = SynchedEntityData.defineId(AssaultEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SX = SynchedEntityData.defineId(AssaultEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SY = SynchedEntityData.defineId(AssaultEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SZ = SynchedEntityData.defineId(AssaultEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_PERFECT = SynchedEntityData.defineId(AssaultEntity.class, EntityDataSerializers.BOOLEAN);

    private final Map<UUID, Integer> hits = new HashMap<>();
    private LivingEntity primary;
    private Vec3 centre;
    private int connected, strikeIdx;
    private boolean hid, dropped, blasted;
    private double finisherDone;

    public AssaultEntity(EntityType<? extends AssaultEntity> type, Level level) {
        super(type, level);
    }

    /** Null when there is nobody to attack. */
    public static AssaultEntity create(Level level, LivingEntity caster) {
        var c = ZegConfig.PHANTOM;
        LivingEntity t = Util.aimedEnemy(level, caster, c.range.get());
        if (t == null) {
            Vec3 aim = Util.aimPoint(level, caster, c.range.get());
            t = Util.nearest(level, aim, 10.0, caster);
        }
        if (t == null) {
            t = Util.nearest(level, caster.position(), c.radius.get(), caster);
        }
        if (t == null) {
            return null;
        }
        AssaultEntity e = new AssaultEntity(ModEntities.ASSAULT.get(), level);
        e.setOwner(caster);
        e.primary = t;
        e.setPos(t.getX(), t.getY(), t.getZ());
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        return e;
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    public int getStrikes() {
        return entityData.get(DATA_STRIKES);
    }

    public int getStrikeAge() {
        return entityData.get(DATA_STRIKE_AGE);
    }

    public Vec3 getStrikePos() {
        return new Vec3(entityData.get(DATA_SX), entityData.get(DATA_SY), entityData.get(DATA_SZ));
    }

    public boolean isPerfect() {
        return entityData.get(DATA_PERFECT);
    }

    public float getBlastRadius() {
        return (float) (ZegConfig.PHANTOM.radius.get() + (isPerfect() ? ZegConfig.PHANTOM.perfectRadiusBonus.get() : 0.0));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, 0);
        entityData.define(DATA_STRIKES, 0);
        entityData.define(DATA_STRIKE_AGE, -100);
        entityData.define(DATA_SX, 0f);
        entityData.define(DATA_SY, 0f);
        entityData.define(DATA_SZ, 0f);
        entityData.define(DATA_PERFECT, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickAssault(this));
            return;
        }
        var c = ZegConfig.PHANTOM;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        if ((primary == null || !primary.isAlive() || primary.level() != level()) && age < GATHER) {
            if (!retarget(owner)) {
                endSequence(owner);
                return;
            }
        }
        if (primary != null && primary.isAlive() && age < BLAST) {
            setPos(primary.getX(), primary.getY(), primary.getZ());
        }
        Vec3 at = position();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            centre = at;
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.HUM.get(), 1.2f, 1.4f);
        }
        if (age < VANISH && age % 2 == 0) {
            Vec3 p = owner.position();
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 0.4, p.z, 3, 0.4, 0.4, 0.4, 0.03);
            Util.fx(level(), ModParticles.VOID.get(), p.x, p.y + 0.9, p.z, 1, 0.5, 0.7, 0.5, 0.01);
        }
        if (age >= 4 && age <= GATHER) {
            Util.reduce(owner, c.reductionPercent.get() / 100.0, 4);
            Util.kbImmune(owner, 4);
            Util.slowImmune(owner, 4);
            Util.ccImmune(owner, 4);
        }
        if (age == VANISH) {
            Util.LOCKED.remove(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.BLINK.get(), 1.8f, 1.0f);
            Util.blast(level(), owner.getX(), owner.getY() + 0.3, owner.getZ(), 2.5, 40);
            SpriteFx.impact(sv(), owner.position().add(0, 1.0, 0), SpriteFx.Palette.VIOLET, 2.0f);
            SpriteFx.shards(sv(), owner.position().add(0, 1.0, 0), SpriteFx.Palette.WHITE, 2.0f);
            if (!owner.isInvisible()) {
                owner.setInvisible(true);
                hid = true;
            }
        }
        if (age >= VANISH && age < ASSAULT) {
            Util.LOCKED.add(owner.getUUID());
            int total = c.strikes.get();
            int due = (int) Math.floor((age - VANISH + 1) * total / (double) (ASSAULT - VANISH));
            while (strikeIdx < due) {
                strike(owner, at);
                strikeIdx++;
            }
        }
        if (age >= ASSAULT && age < DROP && primary != null && primary.isAlive()) {
            Util.hold(primary, 6, 70);
            if (age == ASSAULT) {
                Util.sound(level(), at.x, at.y, at.z, ModSounds.CHARGE.get(), 2.0f, 0.9f);
                entityData.set(DATA_PERFECT, connected >= c.perfectStrikes.get());
            }
            if (age % 2 == 0) {
                double k = (age - ASSAULT) / (double) (DROP - ASSAULT);
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 5.0 * (1.0 - k * 0.6);
                    Util.fxDir(level(), ModParticles.STAR.get(), at.x + Math.cos(a) * r, at.y + 0.5 + random.nextDouble() * 2.0, at.z + Math.sin(a) * r,
                            -Math.cos(a) * 0.14, 0.0, -Math.sin(a) * 0.14);
                }
            }
        }
        if (age == GATHER) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.BLINK.get(), 1.8f, 1.4f);
            Vec3 above = at.add(0, 6.0, 0);
            SpriteFx.fracture(sv(), above.add(0, 1.0, 0), SpriteFx.Palette.VIOLET, 3.5f);
            SpriteFx.shards(sv(), above.add(0, 1.0, 0), SpriteFx.Palette.WHITE, 2.0f);
            if (Util.moveTo(owner, above, owner.getYRot(), 90f)) {
                owner.setNoGravity(true);
            }
            Util.fx(level(), ModParticles.FLASH.get(), above.x, above.y + 1, above.z, 1, 0, 0, 0, 0);
            if (hid) {
                owner.setInvisible(false);
                hid = false;
            }
        }
        if (age > GATHER && age < DROP) {
            owner.setNoGravity(true);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
        }
        if (age == DROP) {
            drop(owner, at);
        }
        if (age == BLAST) {
            blast(owner, at);
        }
        if (age >= END) {
            endSequence(owner);
        }
    }

    private boolean retarget(LivingEntity owner) {
        LivingEntity n = Util.nearest(level(), centre != null ? centre : position(), ZegConfig.PHANTOM.radius.get(), owner);
        if (n != null) {
            primary = n;
            return true;
        }
        return false;
    }

    private void strike(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.PHANTOM;
        List<LivingEntity> in = Util.enemies(level(), centre != null ? centre : at, c.radius.get(), owner);
        if (in.isEmpty()) {
            return;
        }
        LivingEntity t = strikeIdx % 2 == 0 && primary != null && primary.isAlive() ? primary : in.get(random.nextInt(in.size()));
        int h = hits.getOrDefault(t.getUUID(), 0);
        if (h >= c.maxHitsPerTarget.get()) {
            for (LivingEntity o : in) {
                if (hits.getOrDefault(o.getUUID(), 0) < c.maxHitsPerTarget.get()) {
                    t = o;
                    h = hits.getOrDefault(o.getUUID(), 0);
                    break;
                }
            }
            if (h >= c.maxHitsPerTarget.get()) {
                return;
            }
        }
        hits.put(t.getUUID(), h + 1);
        Vec3 tp = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Vec3 spot = Util.safeNear(level(), owner, t.position(), 1.5 + t.getBbWidth() * 0.5);
        Vec3 from = owner.position().add(0, 1, 0);
        if (spot != null) {
            Util.moveTo(owner, spot, Util.yawTo(spot, t.position()), 0f);
        }
        Util.swing(owner);
        Util.arc(level(), ModParticles.VOID.get(), from, tp, 0.2);
        Vec3 sa = new Vec3(tp.x - from.x, 0, tp.z - from.z);
        sa = sa.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : sa.normalize();
        SpriteFx.beam(sv(), from, tp, SpriteFx.Palette.VIOLET, 0.25f, 4);
        SpriteFx.streak(sv(), tp, sa, (strikeIdx * 47) % 180 - 90f, strikeIdx % 2 == 0 ? SpriteFx.Palette.WHITE : SpriteFx.Palette.VIOLET, 3.2f);
        SpriteFx.impact(sv(), tp, SpriteFx.Palette.VIOLET, 1.2f);
        Util.fx(level(), ModParticles.FLASH.get(), tp.x, tp.y, tp.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.SHARD.get(), tp.x, tp.y, tp.z, 4, 0.3, 0.3, 0.3, 0.25);
        Util.fx(level(), ModParticles.STAR.get(), tp.x, tp.y, tp.z, 2, 0.3, 0.3, 0.3, 0.15);
        if (strikeIdx % 3 == 0) {
            Util.sound(level(), tp.x, tp.y, tp.z, ModSounds.PUNCH.get(), 0.9f, 1.1f + random.nextFloat() * 0.4f);
        } else {
            Util.sound(level(), tp.x, tp.y, tp.z, ModSounds.SLASH.get(), 0.9f, 0.9f + random.nextFloat() * 0.5f);
        }
        Util.hurt(owner, t, c.strikeDamage.get(), 0);
        connected++;
        entityData.set(DATA_STRIKES, connected);
        entityData.set(DATA_STRIKE_AGE, tickCount - 1);
        entityData.set(DATA_SX, (float) tp.x);
        entityData.set(DATA_SY, (float) tp.y);
        entityData.set(DATA_SZ, (float) tp.z);
    }

    private void drop(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.PHANTOM;
        dropped = true;
        owner.setNoGravity(false);
        Util.fallImmune(owner, 60);
        Util.moveTo(owner, at.add(0, 0.2, 0), owner.getYRot(), 20f);
        Util.swing(owner);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.PUNCH.get(), 2.4f, 0.7f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 1.8f, 0.7f);
        Util.blast(level(), at.x, at.y + 0.5, at.z, 6, 100);
        Util.fx(level(), ModParticles.CRACK.get(), at.x, at.y + 1, at.z, 12, 3, 2, 3, 0.0);
        SpriteFx.beam(sv(), at.add(0, 6.5, 0), at.add(0, 0.2, 0), SpriteFx.Palette.WHITE, 1.0f, 6);
        SpriteFx.fracture(sv(), at.add(0, 1.2, 0), SpriteFx.Palette.WHITE, 6.0f);
        SpriteFx.cracks(sv(), at, SpriteFx.Palette.VIOLET, 1.5f);
        SpriteFx.shards(sv(), at.add(0, 1.2, 0), SpriteFx.Palette.VIOLET, 3.5f);
        SpriteFx.ring(sv(), at, SpriteFx.Palette.VIOLET, 1.1f);
        if (primary != null && primary.isAlive()) {
            double mult = 1.0;
            if (connected >= c.perfectStrikes.get()) {
                mult += c.perfectFinisherBonusPercent.get() / 100.0;
            }
            if (primary.getHealth() < primary.getMaxHealth() * c.executeBelowPercent.get() / 100.0) {
                mult += c.executeBonusPercent.get() / 100.0;
            }
            Util.hurt(owner, primary, c.finisherFlat.get(), c.finisherPercent.get(), mult, 0.0);
            if (primary.isAlive()) {
                Util.launchBlocks(primary, 1.5);
            }
        }
        for (LivingEntity e : Util.enemies(level(), at, 8.0, owner)) {
            if (e != primary) {
                Util.knock(e, at, 6.0, 0.3);
            }
        }
    }

    private void blast(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.PHANTOM;
        blasted = true;
        double r = getBlastRadius();
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 2.4f, 0.8f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 2.0f, 0.7f);
        Util.blast(level(), at.x, at.y + 1.0, at.z, r * 0.5, 110);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.4, 0), 36, 0.9, 0.04);
        Util.ring(level(), ModParticles.MIRROR.get(), at.add(0, 0.8, 0), 30, 0.7, 0.2);
        SpriteFx.ring(sv(), at, SpriteFx.Palette.VIOLET, (float) r / 3f);
        SpriteFx.cracks(sv(), at, SpriteFx.Palette.VIOLET, (float) r * 2f / 7f);
        SpriteFx.fracture(sv(), at.add(0, 2.5, 0), SpriteFx.Palette.WHITE, 8.0f);
        SpriteFx.shards(sv(), at.add(0, 1.5, 0), SpriteFx.Palette.VIOLET, 5.0f);
        SpriteFx.smoke(sv(), at.add(0, 1.2, 0), SpriteFx.Smoke.VOID, 6.0f);
        SpriteFx.tendrils(sv(), at.add(0, 1.2, 0), SpriteFx.Palette.VIOLET, 5.0f);
        for (LivingEntity e : Util.enemies(level(), at, r, owner)) {
            Util.hurt(owner, e, c.detonationFlat.get(), c.detonationPercent.get());
            if (e.isAlive()) {
                Util.knock(e, at, 7.0, 0.3);
            }
        }
        Util.moveTo(owner, at, owner.getYRot(), 0f);
    }

    private void endSequence(LivingEntity owner) {
        finish(true);
        discard();
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity owner) {
            if (hid) {
                owner.setInvisible(false);
                hid = false;
            }
            owner.setNoGravity(false);
            Util.fallImmune(owner, 60);
        }
    }
}
