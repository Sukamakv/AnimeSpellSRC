package com.example.jjkspells.spells;

import com.example.jjkspells.entity.LaevatainFxEntity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared helpers of Laevatain's four spells: damage, Combustion, targeting, spawning the visual entity. */
public final class LaevatainFX {
    private LaevatainFX() {
    }

    // ------------------------------------------------------------------------------------------------
    //  Damage and targets
    // ------------------------------------------------------------------------------------------------

    /** Multiplier for flat damage from the caster's spell power (1.0 with no bonus) and the spell level. */
    public static float scale(AbstractSpell spell, int spellLevel, LivingEntity caster) {
        float s = DariusFX.levelScale(spellLevel);
        try {
            float with = spell.getSpellPower(spellLevel, caster);
            float without = spell.getSpellPower(spellLevel, null);
            if (without > 0.001f) {
                s *= Mth.clamp(with / without, 0.5f, 6f);
            }
        } catch (RuntimeException ignored) {
            // no spell power available: plain level scaling
        }
        return s;
    }

    public static boolean valid(LivingEntity caster, LivingEntity e) {
        if (e == caster || !e.isAlive() || e.isSpectator()) return false;
        if (caster != null && (e.isAlliedTo(caster) || DamageSources.isFriendlyFireBetween(caster, e))) return false;
        return true;
    }

    public static List<LivingEntity> enemiesIn(ServerLevel level, LivingEntity caster, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> valid(caster, e));
    }

    /** Enemies within {@code radius} (horizontal, taking their width into account) and within dy vertically, nearest first. */
    public static List<LivingEntity> enemiesAround(ServerLevel level, LivingEntity caster, Vec3 p, double radius, double dy) {
        AABB box = new AABB(p.x - radius - 2, p.y - dy - 2, p.z - radius - 2, p.x + radius + 2, p.y + dy + 4, p.z + radius + 2);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : enemiesIn(level, caster, box)) {
            double hx = e.getX() - p.x, hz = e.getZ() - p.z;
            double r = radius + e.getBbWidth() * 0.5;
            if (hx * hx + hz * hz > r * r) continue;
            if (e.getY() > p.y + dy + 2 || e.getY() + e.getBbHeight() < p.y - dy) continue;
            out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out;
    }

    /** Flat damage (already scaled by the caller) plus a share of the target's maximum health. */
    public static boolean strike(LivingEntity caster, AbstractSpell spell, LivingEntity target, float flat, float percent) {
        return ViegoFX.strike(caster, spell, target, flat, percent);
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Throws the target outward from {@code from}; bosses are shoved much less. */
    public static void blast(LivingEntity e, Vec3 from, double strength, double lift) {
        double k = isBoss(e) ? 0.3 : 1.0;
        MartialFX.shove(e, e.getX() - from.x, e.getZ() - from.z, strength * k, lift * k);
    }

    // ------------------------------------------------------------------------------------------------
    //  Aiming
    // ------------------------------------------------------------------------------------------------

    /** The ground point the caster is aiming at within {@code range} (x, ground height, z). */
    public static Vec3 groundTarget(ServerLevel level, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 hit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        boolean onTop = bh.getType() == HitResult.Type.BLOCK && bh.getDirection() == Direction.UP;
        double y = onTop ? hit.y : groundY(level, hit.x, hit.y + 0.5, hit.z, 64, Math.min(hit.y, caster.getY()));
        return new Vec3(hit.x, y, hit.z);
    }

    public static double groundY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        if (!level.hasChunkAt(p)) return fallback;
        for (int i = 0; i < maxDown; i++) {
            if (level.isOutsideBuildHeight(p)) return fallback;
            var shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty()) return p.getY() + shape.max(Direction.Axis.Y);
            p.move(Direction.DOWN);
        }
        return fallback;
    }

    // ------------------------------------------------------------------------------------------------
    //  Visual entity + sound
    // ------------------------------------------------------------------------------------------------

    public static LaevatainFxEntity spawn(ServerLevel level, int kind, Vec3 p, int life, float scale, float aux, float aux2, float yaw, float angle) {
        LaevatainFxEntity e = new LaevatainFxEntity(level, kind, life, scale);
        e.aux(aux).aux2(aux2).angle(angle).heading(yaw);
        e.setPos(p.x, p.y, p.z);
        level.addFreshEntity(e);
        return e;
    }

    public static void sound(ServerLevel level, Vec3 p, SoundEvent ev, float volume, float pitch) {
        level.playSound(null, p.x, p.y, p.z, ev, SoundSource.PLAYERS, volume, pitch);
    }

    // ------------------------------------------------------------------------------------------------
    //  Combustion: 6 damage a second, with smoke and little flames, for as long as it lasts
    // ------------------------------------------------------------------------------------------------
    private static final class Burn {
        final ServerLevel level;
        final UUID casterId;
        final AbstractSpell spell;
        int ticks;
        int sinceHit;
        final float damage;

        Burn(ServerLevel level, UUID casterId, AbstractSpell spell, int ticks, float damage) {
            this.level = level;
            this.casterId = casterId;
            this.spell = spell;
            this.ticks = ticks;
            this.damage = damage;
        }
    }

    private static final Map<UUID, Burn> BURNING = new HashMap<>();

    public static final float COMBUSTION_DAMAGE = 6f;

    /** Sets the target alight with Combustion for {@code ticks} (a longer existing one is kept). */
    public static void ignite(ServerLevel level, @Nullable LivingEntity caster, AbstractSpell spell, LivingEntity target, int ticks, float scale) {
        if (!target.isAlive()) return;
        Burn old = BURNING.get(target.getUUID());
        if (old != null && old.ticks >= ticks) return;
        BURNING.put(target.getUUID(), new Burn(level, caster == null ? null : caster.getUUID(), spell, ticks, COMBUSTION_DAMAGE * scale));
    }

    public static boolean isBurning(LivingEntity e) {
        return e.isOnFire() || BURNING.containsKey(e.getUUID());
    }

    public static void clear() {
        BURNING.clear();
    }

    static void tickCombustion() {
        if (BURNING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Burn>> it = BURNING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Burn> en = it.next();
            Burn b = en.getValue();
            Entity ent = b.level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()) {
                it.remove();
                continue;
            }
            if (--b.ticks <= 0) {
                it.remove();
                continue;
            }
            if (b.ticks % 3 == 0) {
                double w = target.getBbWidth() * 0.4, h = target.getBbHeight();
                FX.emit(b.level, ParticleTypes.FLAME, target.getX(), target.getY() + h * 0.5, target.getZ(), 2, w, h * 0.35, w, 0.02);
                FX.emit(b.level, ParticleTypes.LARGE_SMOKE, target.getX(), target.getY() + h * 0.8, target.getZ(), 1, w, h * 0.2, w, 0.02);
            }
            if (++b.sinceHit >= 20) {
                b.sinceHit = 0;
                Entity ce = b.casterId == null ? null : b.level.getEntity(b.casterId);
                target.invulnerableTime = 0;
                if (ce instanceof LivingEntity caster) {
                    DamageSources.applyDamage(target, b.damage, b.spell.getDamageSource(caster));
                } else {
                    DamageSource src = b.level.damageSources().onFire();
                    target.hurt(src, b.damage);
                }
            }
        }
    }
}
