package com.example.jjkspells.enchant;

import com.example.jjkspells.JJKSpells;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/** Every swing of an enchanted sword throws a flame slash. Several triggers feed one guarded method so a swing fires once. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class FlameSlashEvents {
    private FlameSlashEvents() {}

    /** One slash per swing. The sources never fire for the same swing (air / entity). */
    public static void trigger(LivingEntity who, InteractionHand hand) {
        if (!(who.level() instanceof ServerLevel level)) return;
        ItemStack s = who.getItemInHand(hand);
        if (s.isEmpty() || EnchantmentHelper.getItemEnchantmentLevel(FlameSlashRegistry.FLAME_SLASH.get(), s) <= 0) return;
        FlameSlashEntity.spawn(level, who);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        trigger(e.getEntity(), InteractionHand.MAIN_HAND);
    }
}
