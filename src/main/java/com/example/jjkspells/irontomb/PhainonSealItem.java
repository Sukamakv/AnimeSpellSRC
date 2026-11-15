package com.example.jjkspells.irontomb;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dawnbreaker's Seal: dropped by Phainon. Right-click opens the way to the Irontomb arena (sneak + right-click while
 * inside brings you back). It only answers to someone who has slain Phainon, so it cannot be handed to a friend.
 */
public class PhainonSealItem extends Item {
    public PhainonSealItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Dawnbreaker's Seal");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Proof that the Dawnbreaker has fallen.").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Right-click: step through to Irontomb's arena").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Sneak + right-click inside: return").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (!PhainonGate.has(sp) && !sp.isCreative()) {
                sp.displayClientMessage(Component.literal("§6The seal does not answer you. Only the one who slew Phainon can use it."), true);
            } else if (IrontombArena.isIn(sp)) {
                if (sp.isShiftKeyDown()) {
                    IrontombArena.leave(sp);
                    sp.getCooldowns().addCooldown(this, 60);
                } else {
                    sp.displayClientMessage(Component.literal("§7Sneak + right-click to leave the arena."), true);
                }
            } else {
                ServerLevel arena = IrontombArena.level(sp);
                if (arena == null || !IrontombArena.enter(sp)) {
                    sp.displayClientMessage(Component.literal("The Irontomb arena dimension is missing (data pack not loaded)."), true);
                } else {
                    IrontombArena.ensureBoss(arena);
                    sp.getCooldowns().addCooldown(this, 60);
                    arena.playSound(null, sp.getX(), sp.getY(), sp.getZ(), PhainonSounds.ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.3f);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
