package com.example.jjkspells.irontomb;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Right-click to open the Irontomb tuning screen. Operators (or the owner of a single-player world) only. */
public class IrontombConfiguratorItem extends Item {
    public IrontombConfiguratorItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Irontomb Configurator");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Right-click: tune Irontomb's damage and stats").withStyle(net.minecraft.ChatFormatting.GRAY));
        tip.add(Component.literal("Changes apply instantly and are saved.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (!IrontombNet.allowed(sp)) {
                sp.displayClientMessage(Component.literal("Only operators can tune Irontomb."), true);
            } else {
                IrontombNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new IrontombNet.OpenMsg(IrontombBalance.values()));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
