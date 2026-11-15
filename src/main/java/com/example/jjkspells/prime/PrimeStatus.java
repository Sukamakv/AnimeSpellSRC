package com.example.jjkspells.prime;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two small server-side states used by the PRIME spells:
 *  - LOCK: the creature cannot cast spells, attack, or use items (works even against creatures immune to status effects);
 *  - SHRUNK: maximum health lowered by a fraction for a while (a backup for creatures that ignore the Prime Rend effect).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class PrimeStatus {
    private static final Map<UUID, Long> LOCK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SHRUNK = new ConcurrentHashMap<>();
    private static final UUID SHRINK_ID = UUID.fromString("b7a3d9e1-5c42-4f08-8e6a-1d2c3b4a5f60");

    private PrimeStatus() {
    }

    public static void lock(LivingEntity e, int ticks) {
        if (e == null || e.level().isClientSide) return;
        long until = e.level().getGameTime() + ticks;
        LOCK.merge(e.getUUID(), until, Math::max);
    }

    public static void unlock(LivingEntity e) {
        if (e != null) LOCK.remove(e.getUUID());
    }

    public static boolean locked(LivingEntity e) {
        if (e == null) return false;
        Long u = LOCK.get(e.getUUID());
        return u != null && e.level().getGameTime() <= u;
    }

    /** Lowers max health by {@code fraction} for {@code ticks}: the Prime Rend effect, or a plain attribute modifier if the effect did not take. */
    public static void shrink(LivingEntity e, double fraction, int ticks) {
        if (e == null || e.level().isClientSide || fraction <= 0) return;
        e.addEffect(new MobEffectInstance(ModEffects.PRIME_REND.get(), ticks, 0, false, true, true));
        if (e.hasEffect(ModEffects.PRIME_REND.get())) return;
        AttributeInstance a = e.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return;
        a.removeModifier(SHRINK_ID);
        a.addTransientModifier(new AttributeModifier(SHRINK_ID, "jjk_prime_shrink", -fraction, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
        SHRUNK.put(e.getUUID(), e.level().getGameTime() + ticks);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide || SHRUNK.isEmpty()) return;
        Long until = SHRUNK.get(e.player.getUUID());
        if (until != null && e.player.level().getGameTime() > until) {
            clearShrink(e.player);
        }
    }

    @SubscribeEvent
    public static void onMobTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide) return;
        if (!SHRUNK.isEmpty() && !(le instanceof net.minecraft.world.entity.player.Player)) {
            Long until = SHRUNK.get(le.getUUID());
            if (until != null && le.level().getGameTime() > until) clearShrink(le);
        }
        if (!LOCK.isEmpty() && le instanceof Mob m && locked(m) && m.getTarget() != null) {
            m.setTarget(null);
        }
    }

    private static void clearShrink(LivingEntity e) {
        SHRUNK.remove(e.getUUID());
        AttributeInstance a = e.getAttribute(Attributes.MAX_HEALTH);
        if (a != null) a.removeModifier(SHRINK_ID);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        if (!LOCK.isEmpty() && e.getSource().getEntity() instanceof LivingEntity a && locked(a)) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerAttack(AttackEntityEvent e) {
        if (!LOCK.isEmpty() && locked(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(LivingEntityUseItemEvent.Start e) {
        if (!LOCK.isEmpty() && locked(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCast(SpellPreCastEvent e) {
        if (!LOCK.isEmpty() && locked(e.getEntity())) e.setCanceled(true);
    }
}
