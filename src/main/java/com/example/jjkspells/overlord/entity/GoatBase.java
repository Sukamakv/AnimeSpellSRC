package com.example.jjkspells.overlord.entity;

import com.example.jjkspells.overlord.registry.ModSpells;
import com.example.jjkspells.overlord.util.Magic;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared behaviour of the Black Goat and its Young: they rampage against hostile mobs and sweep-hit groups. */
public abstract class GoatBase extends OverlordSummon {
    /** The Black Goat and its Young have this many times the health, damage and speed set in the config. */
    public static final double HEALTH_MULT = 27.0;
    public static final double DAMAGE_MULT = 9.0;
    public static final double SPEED_MULT = 1.4;
    /** While any of the caster's goats is alive the caster takes this much less damage. */
    public static final float CASTER_REDUCTION = 0.25f;

    /** Caster UUID -> number of their goats currently alive. */
    public static final Map<UUID, Integer> GUARDED = new ConcurrentHashMap<>();

    private UUID guardedOwner;

    /** Damage multiplier for something that may be under the goats' protection (1 = no protection). */
    public static float damageFactor(Entity e) {
        Integer n = GUARDED.get(e.getUUID());
        return n != null && n > 0 ? 1f - CASTER_REDUCTION : 1f;
    }

    protected void guardOwner(LivingEntity owner) {
        if (guardedOwner == null) {
            guardedOwner = owner.getUUID();
            GUARDED.merge(guardedOwner, 1, Integer::sum);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        if (guardedOwner != null) {
            GUARDED.computeIfPresent(guardedOwner, (k, v) -> v <= 1 ? null : v - 1);
            guardedOwner = null;
        }
        super.onRemovedFromWorld();
    }

    protected GoatBase(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected AbstractSpell sourceSpell() {
        return ModSpells.BLACK_GOAT_SACRIFICE.get();
    }

    protected abstract double sweepRadius();

    @Override
    protected void registerExtraTargets() {
        this.targetSelector.addGoal(6, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e instanceof Enemy && !(e instanceof IMagicSummon) && !DamageSources.isFriendlyFireBetween(this, e)));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        double r = sweepRadius();
        if (hit && r > 0 && !this.level().isClientSide) {
            Entity owner = getSummoner() == null ? this : getSummoner();
            List<LivingEntity> near = this.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r),
                    e -> e != target && e != this && Magic.isEnemy(owner, e) && !(e instanceof GoatBase) && !isAlliedTo(e));
            for (LivingEntity e : near) {
                if (e.distanceToSqr(target) <= r * r) {
                    DamageSources.applyDamage(e, (float) this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                            sourceSpell().getDamageSource(this, owner));
                }
            }
        }
        return hit;
    }
}
