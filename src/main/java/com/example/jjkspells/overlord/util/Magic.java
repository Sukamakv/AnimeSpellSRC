package com.example.jjkspells.overlord.util;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.registry.ModEffects;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Shared helpers: targeting, "true" damage that ignores damage caps, level scaling, ritual lock registry. */
public final class Magic {
    /** Server-side: casters currently locked in a stand-still ritual. */
    public static final Set<UUID> LOCKED = Collections.synchronizedSet(new HashSet<>());

    private Magic() {
    }

    public static float scale(int spellLevel) {
        return (float) (1.0 + OverlordConfig.GENERAL.levelScaling.get() * Math.max(0, spellLevel - 1));
    }

    // ------------------------------------------------------------------ targeting

    public static boolean isEnemy(Entity caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive() || e.isSpectator()) {
            return false;
        }
        if (e instanceof Player p) {
            if (p.isCreative() || !OverlordConfig.GENERAL.hitPlayers.get()) {
                return false;
            }
        }
        if (e instanceof com.example.jjkspells.overlord.entity.OverlordFxEntity) {
            return false;
        }
        return !DamageSources.isFriendlyFireBetween(caster, e);
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Point the caster is aiming at: a block hit, a looked-at entity, or the end of the ray. */
    public static Vec3 aimPoint(Level level, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult bhr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double blockDist = bhr.getType() == HitResult.Type.MISS ? range : bhr.getLocation().distanceTo(eye);
        HitResult hr = Utils.raycastForEntity(level, caster, (float) Math.min(range, blockDist), false, 0.3f);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            return new Vec3(le.getX(), le.getY(), le.getZ());
        }
        return bhr.getType() == HitResult.Type.MISS ? end : bhr.getLocation();
    }

    public static LivingEntity lookedAtLiving(Level level, LivingEntity caster, double range) {
        HitResult hr = Utils.raycastForEntity(level, caster, (float) range, true, 0.35f);
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le != caster) {
            return le;
        }
        return null;
    }

    /** Standing surface at/below (x, y, z). */
    public static double groundY(Level l, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y) + 2, Math.floor(z));
        for (int i = 0; i < 10; i++) {
            BlockPos below = p.below();
            if (!l.getBlockState(below).getCollisionShape(l, below).isEmpty() && l.getBlockState(p).getCollisionShape(l, p).isEmpty()) {
                return p.getY();
            }
            p.move(0, -1, 0);
        }
        return y;
    }

    // ------------------------------------------------------------------ damage

    /**
     * Damage that ignores armour, absorption-free caps and per-hit limits: registers the hit as an attack (so the
     * attacker is credited), then subtracts health directly. Kills go through {@link LivingEntity#die}.
     */
    public static void trueDamage(LivingEntity target, float amount, DamageSource source) {
        // all max-health / "true" damage goes through the shared cap-ignoring helper of this mod
        LivingEntity attacker = source.getEntity() instanceof LivingEntity l ? l : null;
        com.example.jjkspells.spells.SpellDamage.percent(target, amount, attacker, source);
    }

    /** Damage multiplier after the target's armour once {@code ignore} (0-1) of the armour reduction is skipped. */
    public static float armorFactor(LivingEntity target, float ignore) {
        float probe = 100f;
        float after = CombatRules.getDamageAfterAbsorb(probe, (float) target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        float factor = after / probe;
        return 1f - (1f - factor) * (1f - Math.max(0f, Math.min(1f, ignore)));
    }

    public static boolean hasAscendance(LivingEntity caster) {
        return caster.hasEffect(ModEffects.ASCENDANCE.get());
    }

    // ------------------------------------------------------------------ misc

    public static void snapTo(LivingEntity e, Vec3 p) {
        if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, sp.getYRot(), sp.getXRot());
        } else {
            e.setPos(p.x, p.y, p.z);
        }
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        e.hurtMarked = true;
    }

    public static void particles(Level level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
        }
    }
}
