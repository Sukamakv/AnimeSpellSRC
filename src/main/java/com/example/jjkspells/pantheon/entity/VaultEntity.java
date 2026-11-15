package com.example.jjkspells.pantheon.entity;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.registry.ModEntities;
import com.example.jjkspells.pantheon.registry.ModParticles;
import com.example.jjkspells.pantheon.registry.ModSounds;
import com.example.jjkspells.pantheon.util.Util;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shield Vault - The Unyielding Assault. Controller at the caster, one clock (age = tickCount - 1):
 * <pre>
 *  lock      0..L        raise the shield, a golden mark appears under the target
 *  leap      L..L+N      shallow arc onto the target (60% damage reduction), never a teleport into it
 *  collision +0          shield slam: 35 damage, stun, shockwave pushes neighbours away
 *  combo     +5, gap     three spear strikes of 18 (faster after a Perfect Engagement)
 *  bash      last+gap+w  the heavy shield bash: 50 + 4%, frontal arc at 60%, knockback, shieldbreaker
 * </pre>
 */
public class VaultEntity extends PanEntityBase {
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(VaultEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PERFECT = SynchedEntityData.defineId(VaultEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLL = SynchedEntityData.defineId(VaultEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BASH = SynchedEntityData.defineId(VaultEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LOCK = SynchedEntityData.defineId(VaultEntity.class, EntityDataSerializers.INT);

    private UUID targetId;
    private boolean perfect;
    private Vec3 start = Vec3.ZERO, end = Vec3.ZERO, faceDir = new Vec3(0, 0, 1);
    private int leapTicks, collisionAt = -1, bashAt = -1, strikesDone;
    private double arcHeight;
    private boolean bashed;
    private final Set<UUID> pushed = new HashSet<>();

    public VaultEntity(EntityType<? extends VaultEntity> type, Level level) {
        super(type, level);
    }

    /** The enemy in the crosshair, else the nearest enemy inside a narrow cone, within range. Null when there is none. */
    public static LivingEntity findTarget(Level level, LivingEntity caster) {
        double range = PanConfig.VAULT.range.get();
        HitResult hr = Utils.raycastForEntity(level, caster, (float) range, true, 0.35f);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && Util.isEnemy(caster, le)) {
            return le;
        }
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range), x -> Util.isEnemy(caster, x))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < 0.5) {
                continue;
            }
            double cos = to.normalize().dot(look);
            if (cos < Math.cos(Math.toRadians(28))) {
                continue;
            }
            if (!caster.hasLineOfSight(e)) {
                continue;
            }
            double score = (1.0 - cos) * 40.0 + d * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    public static VaultEntity create(Level level, LivingEntity caster) {
        LivingEntity target = findTarget(level, caster);
        if (target == null) {
            return null;
        }
        VaultEntity e = new VaultEntity(ModEntities.VAULT.get(), level);
        e.setOwner(caster);
        e.targetId = target.getUUID();
        Vec3 to = target.getBoundingBox().getCenter().subtract(caster.getEyePosition());
        double ang = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(caster.getLookAngle()), -1, 1)));
        e.perfect = ang <= PanConfig.VAULT.perfectAngle.get();
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(DATA_TARGET, target.getId());
        e.entityData.set(DATA_PERFECT, e.perfect);
        e.entityData.set(DATA_LOCK, PanConfig.VAULT.lockTicks.get());
        return e;
    }

    public int getTargetId() {
        return entityData.get(DATA_TARGET);
    }

    public boolean isPerfect() {
        return entityData.get(DATA_PERFECT);
    }

    /** Age at which the shield slam landed, or -1. */
    public int getCollisionAt() {
        return entityData.get(DATA_COLL);
    }

    /** Age of the final shield bash, or -1. */
    public int getBashAt() {
        return entityData.get(DATA_BASH);
    }

    public int getLockTicks() {
        return entityData.get(DATA_LOCK);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TARGET, -1);
        entityData.define(DATA_PERFECT, false);
        entityData.define(DATA_COLL, -1);
        entityData.define(DATA_BASH, -1);
        entityData.define(DATA_LOCK, 5);
    }

    private LivingEntity target() {
        if (targetId != null && level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(targetId) instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.pantheon.client.ClientFx.tickVault(this));
            return;
        }
        LivingEntity owner = owner();
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        var c = PanConfig.VAULT;
        int age = tickCount - 1;
        int lock = c.lockTicks.get();

        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            LivingEntity t = target();
            if (t == null) {
                finish(false);
                discard();
                return;
            }
            Vec3 d = new Vec3(t.getX() - owner.getX(), 0, t.getZ() - owner.getZ());
            faceDir = d.lengthSqr() < 1.0E-6 ? owner.getLookAngle() : d.normalize();
            Util.sound(level(), owner.getX(), owner.getY() + 1, owner.getZ(), ModSounds.CLANG.get(), 1.0f, 1.3f);
            Util.fxDir(level(), ModParticles.RING.get(), t.getX(), t.getY() + 0.1, t.getZ(), 0, 0, 0);
            Util.fx(level(), ModParticles.GLYPH.get(), t.getX(), t.getY() + t.getBbHeight() + 0.7, t.getZ(), 1, 0, 0, 0, 0);
        }
        if (age < lock) {
            // shield raised in front of the body, spear held back
            Vec3 p = owner.position().add(faceDir.x * 0.9, 1.2, faceDir.z * 0.9);
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y, p.z, 2, 0.3, 0.4, 0.3, 0.03);
            owner.setDeltaMovement(0, owner.getDeltaMovement().y > 0 ? 0 : owner.getDeltaMovement().y, 0);
            owner.hurtMarked = true;
            return;
        }
        if (age == lock) {
            LivingEntity t = target();
            if (t == null) {
                finish(false);
                discard();
                return;
            }
            start = owner.position();
            Vec3 tp = t.position();
            Vec3 d = new Vec3(tp.x - start.x, 0, tp.z - start.z);
            double dist = d.length();
            faceDir = dist < 1.0E-6 ? faceDir : d.normalize();
            double stand = Math.min(dist, 1.5 + t.getBbWidth() * 0.5);
            end = new Vec3(tp.x - faceDir.x * stand, tp.y, tp.z - faceDir.z * stand);
            leapTicks = Math.max(c.leapMaxTicks.get(), (int) Math.ceil(Math.max(0, dist - stand) / 2.2));
            arcHeight = Mth.clamp(Math.max(0, dist - stand) * 0.12, 0.4, 2.5);
            owner.setNoGravity(true);
            Util.reduce(owner, c.leapReduction.get() / 100.0, leapTicks + 4);
            Util.FALL_IMMUNE.put(owner.getUUID(), level().getGameTime() + 200);
            Util.sound(level(), owner.getX(), owner.getY() + 1, owner.getZ(), ModSounds.LEAP.get(), 1.0f, 1.0f);
        }
        if (age >= lock && age < lock + leapTicks) {
            int k = age - lock + 1;
            double f = k / (double) leapTicks;
            Vec3 p = start.lerp(end, f).add(0, Math.sin(Math.PI * f) * arcHeight, 0);
            float yaw = Util.yawTo(start, end);
            if (!Util.moveTo(owner, p, yaw, 8f) && k == leapTicks) {
                Util.snapTo(owner, end, yaw, 8f);
            }
            // golden streaks following the leap
            Vec3 back = owner.position().subtract(faceDir.scale(0.8)).add(0, 1.0, 0);
            Util.fx(level(), ModParticles.STREAK.get(), back.x, back.y, back.z, 2, 0.25, 0.35, 0.25, 0.02);
            Util.fx(level(), ModParticles.SPARK.get(), back.x, back.y, back.z, 3, 0.3, 0.4, 0.3, 0.05);
            return;
        }
        int collide = lock + leapTicks;
        if (age == collide) {
            collisionAt = age;
            entityData.set(DATA_COLL, age);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
            collision(owner);
        }
        // spear combo, then the shield bash
        int gap = perfect ? c.perfectStrikeGap.get() : c.strikeGap.get();
        int combo0 = collide + 5;
        int n = c.strikes.get();
        owner.setDeltaMovement(0, 0, 0);
        owner.hurtMarked = true;
        if (age >= combo0 && strikesDone < n && (age - combo0) % gap == 0) {
            strike(owner, strikesDone);
            strikesDone++;
        }
        int bashTime = combo0 + Math.max(0, n - 1) * gap + gap + c.finalWindup.get();
        if (n == 0) {
            bashTime = combo0 + c.finalWindup.get();
        }
        if (!bashed && age == bashTime - 3) {
            entityData.set(DATA_BASH, age + 3);
            Util.sound(level(), owner.getX(), owner.getY() + 1, owner.getZ(), ModSounds.LEAP.get(), 0.9f, 0.7f);
        }
        if (!bashed && age >= bashTime) {
            bashed = true;
            bashAt = age;
            bash(owner);
        }
        if (bashed && age >= bashAt + 6) {
            finish(true);
            discard();
        }
    }

    private void collision(LivingEntity owner) {
        var c = PanConfig.VAULT;
        LivingEntity t = target();
        Vec3 at = owner.position().add(faceDir.scale(1.1)).add(0, 1.0, 0);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BASH.get(), 1.3f, 1.0f);
        Util.blast(level(), at.x, at.y - 0.3, at.z, c.impactRadius.get(), 14);
        Util.fxDir(level(), ModParticles.RING.get(), owner.getX() + faceDir.x, owner.getY() + 0.1, owner.getZ() + faceDir.z, 0, 0, 0);
        Util.ring(level(), ModParticles.SPARK.get(), new Vec3(owner.getX() + faceDir.x, owner.getY() + 0.2, owner.getZ() + faceDir.z), 20, 0.3, 0.03);
        if (t != null) {
            Util.hurt(owner, t, c.impactFlat.get(), 0);
            if (t.isAlive()) {
                Util.stun(t, (int) Math.round((perfect ? c.perfectStunSeconds.get() : c.stunSeconds.get()) * 20));
            }
        }
        for (LivingEntity e : Util.enemies(level(), owner.position().add(faceDir.scale(1.1)), c.impactRadius.get(), owner)) {
            if (t != null && e == t) {
                continue;
            }
            Util.knock(e, owner.position(), 2.0, 0.2);
        }
        if (owner instanceof ServerPlayer sp) {
            // a short shake for the caster
            sp.hurtMarked = true;
        }
    }

    private void strike(LivingEntity owner, int i) {
        var c = PanConfig.VAULT;
        LivingEntity t = target();
        Util.swing(owner);
        Vec3 from = owner.position().add(faceDir.scale(0.8)).add(0, 1.2, 0);
        if (t != null) {
            Vec3 tc = t.position().add(0, t.getBbHeight() * 0.55, 0);
            Util.hurt(owner, t, c.strikeFlat.get(), 0);
            Util.sound(level(), tc.x, tc.y, tc.z, ModSounds.THRUST.get(), 1.0f, 0.9f + 0.1f * i);
            Util.fx(level(), ModParticles.METAL.get(), tc.x, tc.y, tc.z, 4, 0.25, 0.25, 0.25, 0.15);
            Util.fx(level(), ModParticles.SPARK.get(), tc.x, tc.y, tc.z, i == 2 ? 24 : 12, 0.3, 0.3, 0.3, i == 2 ? 0.45 : 0.25);
            Util.fx(level(), ModParticles.FLASH.get(), tc.x, tc.y, tc.z, 1, 0, 0, 0, 0);
            Util.arc(level(), ModParticles.STREAK.get(), from, tc, 0.0);
        } else {
            Util.sound(level(), from.x, from.y, from.z, ModSounds.THRUST.get(), 0.8f, 0.9f);
        }
    }

    private void bash(LivingEntity owner) {
        var c = PanConfig.VAULT;
        LivingEntity t = target();
        Vec3 center = owner.position();
        Vec3 face = faceDir;
        if (t != null) {
            Vec3 d = new Vec3(t.getX() - owner.getX(), 0, t.getZ() - owner.getZ());
            if (d.lengthSqr() > 1.0E-6) {
                face = d.normalize();
            }
        }
        Util.swing(owner);
        Util.sound(level(), center.x, center.y + 1, center.z, ModSounds.BASH.get(), 1.5f, 0.8f);
        Util.sound(level(), center.x, center.y + 1, center.z, ModSounds.BOOM.get(), 0.6f, 1.4f);
        Vec3 fp = center.add(face.scale(2.0)).add(0, 1.0, 0);
        Util.blast(level(), fp.x, fp.y, fp.z, 3.0, 22);
        // the golden crescent along the shield's path
        for (int i = -6; i <= 6; i++) {
            double a = Math.toRadians(i * (c.arcDegrees.get() / 12.0));
            double cs = Math.cos(a), sn = Math.sin(a);
            Vec3 dir = new Vec3(face.x * cs - face.z * sn, 0, face.x * sn + face.z * cs);
            Util.fxDir(level(), ModParticles.SPARK.get(), center.x + dir.x * 2.2, center.y + 1.1, center.z + dir.z * 2.2, dir.x * 0.4, 0.02, dir.z * 0.4);
            Util.fxDir(level(), ModParticles.FLAME.get(), center.x + dir.x * 1.8, center.y + 0.9, center.z + dir.z * 1.8, dir.x * 0.25, 0.02, dir.z * 0.25);
        }
        double cosHalf = Math.cos(Math.toRadians(c.arcDegrees.get() / 2.0));
        Set<UUID> done = new HashSet<>();
        if (t != null) {
            hitBash(owner, t, 1.0, center, true);
            done.add(t.getUUID());
        }
        for (LivingEntity e : Util.enemies(level(), center, c.arcRadius.get(), owner)) {
            if (done.contains(e.getUUID())) {
                continue;
            }
            Vec3 to = new Vec3(e.getX() - center.x, 0, e.getZ() - center.z);
            if (to.lengthSqr() > 1.0E-6 && to.normalize().dot(face) < cosHalf) {
                continue;
            }
            done.add(e.getUUID());
            hitBash(owner, e, c.arcFactor.get(), center, false);
        }
        if (perfect) {
            Util.fxDir(level(), ModParticles.RING.get(), center.x, center.y + 0.1, center.z, 0, 0, 0);
            Util.ring(level(), ModParticles.SPARK.get(), center.add(0, 0.2, 0), 36, 0.5, 0.05);
            Util.ring(level(), ModParticles.FLAME.get(), center.add(0, 0.2, 0), 24, 0.3, 0.03);
            for (LivingEntity e : Util.enemies(level(), center, c.perfectRadius.get(), owner)) {
                Util.hurt(owner, e, c.perfectFlat.get(), 0);
            }
        }
    }

    private void hitBash(LivingEntity owner, LivingEntity e, double factor, Vec3 center, boolean main) {
        var c = PanConfig.VAULT;
        double mult = factor;
        if (e.isBlocking()) {
            mult *= 1.0 + c.shieldbreakBonus.get() / 100.0;
            if (e instanceof Player p) {
                int ticks = (int) Math.round(c.shieldDisableSeconds.get() * 20);
                if (ticks > 0) {
                    p.getCooldowns().addCooldown(p.getUseItem().getItem(), ticks);
                    p.stopUsingItem();
                    level().broadcastEntityEvent(p, (byte) 30);
                }
            }
        }
        Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get(), mult, 1.0);
        if (e.isAlive()) {
            Util.knock(e, center, c.knockbackBlocks.get(), 0.25);
        }
        Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
        Util.fx(level(), ModParticles.FLASH.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        Util.fx(level(), ModParticles.METAL.get(), p.x, p.y, p.z, main ? 8 : 4, 0.3, 0.3, 0.3, 0.2);
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        LivingEntity owner = owner();
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
        if (owner != null) {
            owner.setNoGravity(false);
            Util.clearReduce(owner);
            Util.FALL_IMMUNE.put(owner.getUUID(), level().getGameTime() + 60);
            if (!normal && !level().noCollision(owner, owner.getBoundingBox())) {
                Vec3 safe = Util.safeNear(level(), owner, owner.position(), 1.0);
                if (safe != null) {
                    Util.snapTo(owner, safe, owner.getYRot(), owner.getXRot());
                }
            }
        }
    }
}
