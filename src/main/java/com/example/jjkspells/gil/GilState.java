package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timed states of the Gilgamesh spells (guards, buffs, marks) and the rules that enforce them.
 * Everything is keyed by entity UUID and expires by game time, so nothing needs cleaning up.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class GilState {
    private GilState() {}

    /** Damage reduction a caster has until {@code until}. {@code general} applies to everything, {@code projectile} only to projectiles. */
    private record Guard(long until, float general, float projectile, boolean noKnockback, boolean noStun) {}

    private static final Map<UUID, Guard> GUARDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FALL_SAFE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SUPREMACY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DIVINE_BOUND = new ConcurrentHashMap<>();

    private static long now(Entity e) {
        return e.level().getGameTime();
    }

    // ---- guards -------------------------------------------------------------------------------------------------

    public static void guard(LivingEntity e, int ticks, float general, float projectile, boolean noKnockback, boolean noStun) {
        GUARDS.put(e.getUUID(), new Guard(now(e) + ticks, general, projectile, noKnockback, noStun));
    }

    public static void clearGuard(LivingEntity e) {
        GUARDS.remove(e.getUUID());
    }

    private static Guard guardOf(Entity e) {
        Guard g = GUARDS.get(e.getUUID());
        if (g == null) return null;
        if (now(e) > g.until) {
            GUARDS.remove(e.getUUID());
            return null;
        }
        return g;
    }

    /** True while the entity is inside an unstoppable Noble Phantasm (ordinary stuns do nothing). */
    public static boolean stunImmune(LivingEntity e) {
        Guard g = guardOf(e);
        return g != null && g.noStun;
    }

    public static void fallSafe(LivingEntity e, int ticks) {
        FALL_SAFE.put(e.getUUID(), now(e) + ticks);
    }

    // ---- buffs --------------------------------------------------------------------------------------------------

    public static void supremacy(LivingEntity e, int ticks) {
        SUPREMACY.put(e.getUUID(), now(e) + ticks);
    }

    public static boolean hasSupremacy(Entity e) {
        Long t = SUPREMACY.get(e.getUUID());
        return t != null && now(e) <= t;
    }

    /** A divine target held by Enkidu takes extra damage from Gilgamesh's other spells. */
    public static void divineBound(LivingEntity e, int ticks) {
        DIVINE_BOUND.put(e.getUUID(), now(e) + ticks);
    }

    public static void clearDivine(LivingEntity e) {
        DIVINE_BOUND.remove(e.getUUID());
    }

    public static boolean isDivineBound(Entity e) {
        Long t = DIVINE_BOUND.get(e.getUUID());
        return t != null && now(e) <= t;
    }

    // ---- rules --------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        Guard g = guardOf(v);
        DamageSource src = e.getSource();
        float cut = 0f;
        if (g != null) {
            cut = g.general;
            if (g.projectile > 0f && (src.getDirectEntity() instanceof Projectile || src.is(DamageTypeTags.IS_PROJECTILE))) {
                cut = Math.max(cut, g.projectile);
            }
        }
        if (cut > 0f) e.setAmount(e.getAmount() * (1f - Math.min(cut, 0.99f)));
        // a hit on a vessel's rider that is strong enough staggers the vessel
        if (v.getVehicle() instanceof com.example.jjkspells.gil.entity.VimanaEntity vessel && src.getMsgId().toLowerCase().contains("magic")) {
            vessel.stagger(e.getAmount());
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent e) {
        Guard g = guardOf(e.getEntity());
        if (g != null && g.noKnockback) e.setCanceled(true);
        else if (e.getEntity().getVehicle() instanceof com.example.jjkspells.gil.entity.VimanaEntity) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        LivingEntity v = e.getEntity();
        Long t = FALL_SAFE.get(v.getUUID());
        if (t != null) {
            if (now(v) <= t) e.setCanceled(true);
            else FALL_SAFE.remove(v.getUUID());
        }
        if (v.getVehicle() instanceof com.example.jjkspells.gil.entity.VimanaEntity) e.setCanceled(true);
    }

    /** Rupture of Creation: 25% less healing. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        MobEffectInstance r = e.getEntity().getEffect(GilEffects.RUPTURE.get());
        if (r != null) e.setAmount(e.getAmount() * (1f - GilConfig.EA.ruptureHeal.f()));
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        LivingEntity v = e.getEntity();
        if (v.hasEffect(GilEffects.ROOTED.get())) {
            net.minecraft.world.phys.Vec3 m = v.getDeltaMovement();
            v.setDeltaMovement(m.x, Math.min(0, m.y), m.z);
        }
    }

    /** No ender pearls, chorus fruit or enderman hops inside the spatial distortion (bosses keep their own tricks). */
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent e) {
        if (e.getEntity() instanceof LivingEntity v && v.hasEffect(GilEffects.DISTORTION.get()) && !GilDmg.isBoss(v)) {
            e.setCanceled(true);
        }
    }
}
