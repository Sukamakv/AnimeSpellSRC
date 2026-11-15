package com.example.jjkspells.entity;

import com.example.jjkspells.JJKSpells;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bond between a caster and their {@link AzureColossus}.
 * <p>
 * For as long as the colossus is alive and active, the caster takes {@link #REDUCTION} (85%) less damage from every
 * source. When the colossus is killed, expires or is dismissed, the bond ends at once.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class ColossusBond {
    /** Fraction of incoming damage that is removed while bonded. */
    public static final float REDUCTION = 0.85f;

    /** owner uuid -> colossus uuid */
    private static final Map<UUID, UUID> BONDS = new ConcurrentHashMap<>();

    private ColossusBond() {
    }

    public static void bind(LivingEntity owner, AzureColossus colossus) {
        BONDS.put(owner.getUUID(), colossus.getUUID());
    }

    public static void unbind(AzureColossus colossus) {
        UUID owner = colossus.getOwnerId();
        if (owner != null) {
            BONDS.remove(owner, colossus.getUUID());
        }
    }

    /** The owner's living colossus, or null when the owner has none (no bond). */
    public static AzureColossus get(LivingEntity owner) {
        UUID id = BONDS.get(owner.getUUID());
        if (id == null || !(owner.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity e = level.getEntity(id);
        if (e instanceof AzureColossus c && c.isAlive() && !c.isRemoved()) {
            return c;
        }
        BONDS.remove(owner.getUUID(), id);
        return null;
    }

    public static boolean isBonded(LivingEntity owner) {
        return get(owner) != null;
    }

    /**
     * Runs last (lowest priority) so the 85% is taken off whatever damage is left after armour, enchantments and other
     * mods have done their part. Applies to every damage source.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || BONDS.isEmpty()) {
            return;
        }
        if (isBonded(victim)) {
            event.setAmount(event.getAmount() * (1f - REDUCTION));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BONDS.clear();
    }
}
