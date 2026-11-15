package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds creatures completely still for the length of DIO's Road Roller animation. It does not use a status effect,
 * so immunity to effects does nothing against it: every tick the creature's horizontal position and motion are
 * forced back, mob AI is switched off (and restored afterwards), and it cannot attack, use items, interact, jump
 * or cast Iron's spells while held.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class DioHold {
    private DioHold() {}

    private static final class Held {
        final LivingEntity entity;
        int ticks;
        final double x, z;
        final boolean hadNoAi;

        Held(LivingEntity e, int ticks) {
            this.entity = e;
            this.ticks = ticks;
            this.x = e.getX();
            this.z = e.getZ();
            this.hadNoAi = e instanceof Mob m && m.isNoAi();
        }
    }

    private static final Map<UUID, Held> HELD = new LinkedHashMap<>();

    /** Set while DIO's own spell deals damage, so holding the caster does not cancel the caster's own hits. */
    public static boolean bypass = false;

    public static boolean isHeld(LivingEntity e) {
        return e != null && HELD.containsKey(e.getUUID());
    }

    /** Holds {@code e} where it stands for {@code ticks} more ticks (extends an existing hold, keeping its anchor). */
    public static void hold(LivingEntity e, int ticks) {
        if (e == null || e.level().isClientSide) return;
        Held h = HELD.get(e.getUUID());
        if (h != null && h.entity == e) {
            h.ticks = Math.max(h.ticks, ticks);
            return;
        }
        Held nh = new Held(e, ticks);
        HELD.put(e.getUUID(), nh);
        if (e instanceof Mob m) {
            m.setNoAi(true);
            m.getNavigation().stop();
        }
    }

    public static void release(LivingEntity e) {
        if (e == null) return;
        Held h = HELD.remove(e.getUUID());
        if (h != null) restore(h);
    }

    private static void restore(Held h) {
        if (h.entity instanceof Mob m && !h.hadNoAi) m.setNoAi(false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HELD.isEmpty()) return;
        Iterator<Held> it = HELD.values().iterator();
        while (it.hasNext()) {
            Held h = it.next();
            LivingEntity e = h.entity;
            if (e.isRemoved() || !e.isAlive() || --h.ticks < 0) {
                restore(h);
                it.remove();
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(0, Math.min(0, v.y), 0);
            e.hurtMarked = true;
            if (e instanceof Mob m) m.getNavigation().stop();
            double dx = e.getX() - h.x, dz = e.getZ() - h.z;
            if (dx * dx + dz * dz > 0.0004) {
                if (e instanceof ServerPlayer sp) {
                    sp.connection.teleport(h.x, e.getY(), h.z, e.getYRot(), e.getXRot());
                } else {
                    e.setPos(h.x, e.getY(), h.z);
                }
            }
        }
    }

    /** Frees everyone when the server stops, so no mob is left with its AI switched off in the save. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        List<Held> all = new ArrayList<>(HELD.values());
        HELD.clear();
        for (Held h : all) restore(h);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (bypass) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isHeld(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (isHeld(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && isHeld(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (isHeld(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (isHeld(e)) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x, Math.min(0, v.y), v.z);
        }
    }
}
