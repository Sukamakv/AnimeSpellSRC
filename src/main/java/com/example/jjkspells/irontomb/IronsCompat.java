package com.example.jjkspells.irontomb;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Everything that touches Iron's Spells 'n Spellbooks does so through reflection, so this module compiles
 * even if that API shifts between versions. If a lookup fails the feature is silently skipped.
 *   - mana drain / relief   (MagicData.getPlayerMagicData(player).getMana()/setMana())
 *   - Action Lock blocking spell casts (SpellPreCastEvent)
 */
public final class IronsCompat {
    public static final String LOCK_KEY = "jjk_irontomb_lock";

    private static boolean resolved, manaOk;
    private static Method getData, getMana, setMana;

    private IronsCompat() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> md = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            for (Method m : md.getMethods()) {
                if (m.getName().equals("getPlayerMagicData") && m.getParameterCount() == 1) {
                    getData = m;
                }
            }
            getMana = md.getMethod("getMana");
            setMana = md.getMethod("setMana", float.class);
            manaOk = getData != null;
        } catch (Throwable t) {
            manaOk = false;
        }
    }

    /** Adds (or, when negative, removes) mana. Returns silently when Iron's Spells isn't there. */
    public static void addMana(ServerPlayer p, float amount) {
        resolve();
        if (!manaOk) {
            return;
        }
        try {
            Object data = getData.invoke(null, p);
            float cur = ((Number) getMana.invoke(data)).floatValue();
            setMana.invoke(data, Math.max(0f, cur + amount));
        } catch (Throwable ignored) {
        }
    }

    public static boolean isLocked(LivingEntity e) {
        return e.getPersistentData().getLong(LOCK_KEY) > e.level().getGameTime();
    }

    public static void lock(LivingEntity e, int ticks) {
        e.getPersistentData().putLong(LOCK_KEY, e.level().getGameTime() + ticks);
    }

    public static void unlock(LivingEntity e) {
        e.getPersistentData().remove(LOCK_KEY);
    }

    /** Called once from common setup: stops locked players from casting Iron's Spells. */
    public static void hook() {
        try {
            Class<?> c = Class.forName("io.redspace.ironsspellbooks.api.events.SpellPreCastEvent");
            Method getEntity = c.getMethod("getEntity");
            Method setCanceled = c.getMethod("setCanceled", boolean.class);
            listen(c.asSubclass(Event.class), getEntity, setCanceled);
        } catch (Throwable ignored) {
            // Iron's Spells API differs: the Action Lock then only blocks movement, melee and item use.
        }
    }

    private static <T extends Event> void listen(Class<T> type, Method getEntity, Method setCanceled) {
        Consumer<T> handler = ev -> {
            try {
                Object who = getEntity.invoke(ev);
                if (who instanceof LivingEntity le && isLocked(le)) {
                    setCanceled.invoke(ev, true);
                }
            } catch (Throwable ignored) {
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, type, handler);
    }
}
