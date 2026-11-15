package com.example.jjkspells.enchant;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.network.ModNetwork;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tells the server about air swings with an enchanted sword. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class FlameSwingClient {
    private FlameSwingClient() {}

    @SubscribeEvent
    public static void onEmptyClick(PlayerInteractEvent.LeftClickEmpty e) {
        if (!e.getLevel().isClientSide) return;
        if (EnchantmentHelper.getItemEnchantmentLevel(FlameSlashRegistry.FLAME_SLASH.get(), e.getItemStack()) <= 0) return;
        ModNetwork.CHANNEL.sendToServer(new FlameSwingPacket());
    }
}
