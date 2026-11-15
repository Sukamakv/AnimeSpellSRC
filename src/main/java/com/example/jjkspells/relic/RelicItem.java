package com.example.jjkspells.relic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A character's Relic. Worn in a Relic curio slot it lets the player cast that character's spells. */
public class RelicItem extends Item {
    private final String group;
    private final String title;
    private final int color;

    public RelicItem(String group, String title, int color) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        this.group = group;
        this.title = title;
        this.color = color;
    }

    public String group() {
        return group;
    }

    public String title() {
        return title;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Allows casting " + title + "'s spells").withStyle(s -> s.withColor(TextColor.fromRgb(color))));
        tooltip.add(Component.literal("Wear it in a Relic slot (you have 2)").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Without it those spells cannot be cast").withStyle(ChatFormatting.DARK_GRAY));
    }
}
