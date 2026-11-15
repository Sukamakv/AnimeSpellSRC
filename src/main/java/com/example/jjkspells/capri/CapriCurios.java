package com.example.jjkspells.capri;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/** Reads the curios the entity wears (by reflection, so the mod does not need Curios at compile time). */
public final class CapriCurios {
    private static java.lang.reflect.Method getInventory, getEquipped;
    private static boolean lookedUp, available;

    private CapriCurios() {
    }

    private static void lookUp() {
        lookedUp = true;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            getInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            getEquipped = handler.getMethod("getEquippedCurios");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    public static List<ItemStack> equipped(LivingEntity e) {
        List<ItemStack> out = new ArrayList<>();
        if (!lookedUp) {
            lookUp();
        }
        if (!available) {
            return out;
        }
        try {
            Object opt = getInventory.invoke(null, e);
            if (opt instanceof LazyOptional<?> lazy) {
                Object handler = lazy.resolve().orElse(null);
                if (handler != null && getEquipped.invoke(handler) instanceof IItemHandler eq) {
                    for (int i = 0; i < eq.getSlots(); i++) {
                        ItemStack st = eq.getStackInSlot(i);
                        if (!st.isEmpty()) {
                            out.add(st);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // curios missing or changed: nothing is worn
        }
        return out;
    }

    public static boolean hasCapri(LivingEntity e) {
        for (ItemStack st : equipped(e)) {
            if (st.getItem() instanceof CapriRegistry.CapriItem) {
                return true;
            }
        }
        return false;
    }
}
