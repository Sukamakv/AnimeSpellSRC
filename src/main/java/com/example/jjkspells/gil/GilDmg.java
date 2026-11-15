package com.example.jjkspells.gil;

import com.example.jjkspells.spells.GoldTarget;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Damage, targeting and classification helpers shared by the five Gilgamesh spells. */
public final class GilDmg {
    private GilDmg() {}

    public static final TagKey<EntityType<?>> DIVINE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("jjk_spells", "divine"));

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    public static boolean isDivine(LivingEntity e) {
        if (e.getType().is(DIVINE)) return true;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        for (String s : GilConfig.G.divine.get()) {
            if (s.startsWith("#")) {
                try {
                    if (e.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(s.substring(1))))) return true;
                } catch (RuntimeException ignored) {
                    // bad tag id in the config
                }
            } else if (id != null && id.toString().equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** True if {@code caster} may hurt {@code t}. */
    public static boolean canHit(@Nullable LivingEntity caster, LivingEntity t) {
        if (t == null || !t.isAlive() || t.isSpectator()) return false;
        if (t instanceof Player p && (p.getAbilities().invulnerable || !GilConfig.G.hitPlayers.get())) return false;
        if (caster == null) return true;
        if (t == caster || t.isAlliedTo(caster)) return false;
        return !DamageSources.isFriendlyFireBetween(caster, t);
    }

    /** Everything alive within {@code r} blocks of {@code c} that the caster may hurt. */
    public static List<LivingEntity> around(ServerLevel level, @Nullable LivingEntity caster, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r))) {
            if (!canHit(caster, e)) continue;
            if (e.getBoundingBox().getCenter().distanceTo(c) <= r + e.getBbWidth() * 0.5) out.add(e);
        }
        return out;
    }

    /** Damage multiplier from the caster's and target's states. {@code gate} is true for Gate of Babylon weapons. */
    public static float mult(@Nullable LivingEntity caster, LivingEntity target, boolean gate) {
        float m = GilConfig.G.damageMultiplier.f();
        if (caster != null && GilState.hasSupremacy(caster)) m *= 1f + GilConfig.EA.supremacyBonus.f();
        if (target != null && GilState.isDivineBound(target)) m *= 1f + GilConfig.ENKIDU.divineBonus.f();
        if (gate && target != null && target.hasEffect(GilEffects.RESONANCE.get())) m *= 1f + GilConfig.MERODACH.markBonus.f();
        return m;
    }

    /**
     * One hit: {@code flat} damage plus {@code pct} of the target's MAX health (through SpellDamage.percent, so it ignores caps),
     * both scaled by {@code scale} and the state multipliers. Returns true if it landed.
     */
    public static boolean hit(ServerLevel level, @Nullable LivingEntity caster, LivingEntity t, float flat, float pct, float scale, boolean gate, @Nullable Entity direct) {
        if (!canHit(caster, t)) return false;
        com.example.jjkspells.spells.Debuffs.hit(caster, t);
        float m = scale * mult(caster, t, gate);
        DamageSource src = caster != null ? level.damageSources().indirectMagic(direct != null ? direct : caster, caster) : level.damageSources().magic();
        if (flat > 0f) {
            t.invulnerableTime = 0;
            t.hurt(src, flat * m);
        }
        if (pct > 0f && t.isAlive()) {
            t.invulnerableTime = 0;
            SpellDamage.percent(t, t.getMaxHealth() * pct * m, caster, src);
        }
        return true;
    }

    public static boolean hit(ServerLevel level, @Nullable LivingEntity caster, LivingEntity t, float flat, float pct) {
        return hit(level, caster, t, flat, pct, 1f, false, null);
    }

    // ---- targeting ----------------------------------------------------------------------------------------------

    /** The creature a caster is attacking: a mob's own target, otherwise what a player looks at. */
    @Nullable
    public static LivingEntity target(ServerLevel level, LivingEntity caster, double range) {
        if (caster instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive() && m.distanceTo(m.getTarget()) <= range + 8) {
            return m.getTarget();
        }
        return GoldTarget.find(level, caster, range);
    }

    /** Where the caster is aiming: its target, otherwise the first thing along its line of sight. */
    public static Vec3 aim(ServerLevel level, LivingEntity caster, double range) {
        LivingEntity t = target(level, caster, range);
        if (t != null) return t.getBoundingBox().getCenter();
        return GoldTarget.aimPoint(level, caster, range);
    }

    /** Applies an effect refreshing it only when it would run out. */
    public static void effect(LivingEntity e, net.minecraft.world.effect.MobEffect eff, int ticks, int amp) {
        MobEffectInstance cur = e.getEffect(eff);
        if (cur == null || cur.getDuration() < ticks - 4 || cur.getAmplifier() != amp) {
            e.addEffect(new MobEffectInstance(eff, ticks, amp, false, false, true));
        }
    }
}
