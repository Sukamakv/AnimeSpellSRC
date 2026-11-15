package com.example.jjkspells.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A curio that softens max-health percentage damage (everything dealt through SpellDamage.percent). Each worn piece
 * removes its share; all pieces together never remove more than {@link #MAX_REDUCTION} (40%): 10% max health damage
 * becomes 6%.
 */
public class PercentWardItem extends Item {
    public static final float MAX_REDUCTION = 0.40f;

    private final float reduction;
    private final boolean great;

    public PercentWardItem(float reduction, Rarity rarity) {
        this(reduction, rarity, false);
    }

    /** A "great" ward is not part of the 40% pool: it multiplies on top of it (and of the other great wards). */
    public PercentWardItem(float reduction, Rarity rarity, boolean great) {
        super(new Item.Properties().stacksTo(1).rarity(rarity).fireResistant());
        this.reduction = reduction;
        this.great = great;
    }

    public boolean isGreat() {
        return great;
    }

    public float reduction() {
        return reduction;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("-" + Math.round(reduction * 100) + "% max-health percentage damage taken").withStyle(ChatFormatting.GOLD));
        if (great) {
            tooltip.add(Component.literal("Not limited by the " + Math.round(MAX_REDUCTION * 100) + "% cap of the lesser wards").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.literal("All lesser wards together: at most -" + Math.round(MAX_REDUCTION * 100) + "%").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // Curios is reached by reflection, so this mod compiles without Curios on the classpath (it is needed at runtime only).
    private static java.lang.reflect.Method getInventory, getEquipped;
    private static boolean lookedUp = false, available = false;

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

    /** Total share of percentage damage removed for {@code e} by the curios they wear (0 to 1). */
    public static float reductionOf(LivingEntity e) {
        if (e == null) return 0f;
        if (!lookedUp) lookUp();
        if (!available) return 0f;
        float total = 0f, remaining = 1f;
        try {
            Object opt = getInventory.invoke(null, e);
            if (opt instanceof LazyOptional<?> lazy) {
                Object handler = lazy.resolve().orElse(null);
                if (handler != null && getEquipped.invoke(handler) instanceof IItemHandler eq) {
                    for (int i = 0; i < eq.getSlots(); i++) {
                        if (eq.getStackInSlot(i).getItem() instanceof PercentWardItem w) {
                            if (w.great) remaining *= 1f - w.reduction;
                            else total += w.reduction;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // curios missing or changed: no reduction rather than a crash
        }
        return 1f - (1f - Math.min(MAX_REDUCTION, total)) * remaining;
    }
}
