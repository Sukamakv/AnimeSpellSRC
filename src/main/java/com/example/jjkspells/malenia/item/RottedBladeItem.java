package com.example.jjkspells.malenia.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Malenia's Devine Rotted Blade. Damage 24, speed 2.4, reach 5, unbreakable, +550 max mana and x1.3 mana regen while held.
 */
public class RottedBladeItem extends SwordItem {
    public static final double DAMAGE = 24.0, SPEED = 2.4, REACH = 5.0, MANA = 550.0, REGEN = 1.3;
    private static final UUID MANA_UUID = UUID.fromString("a1d3c6e8-2b47-4f09-8e15-7c3b9d0a5e62");
    private static final UUID REGEN_UUID = UUID.fromString("0e8b7f24-6a19-4c3d-9b50-d4a2e61f8c37");
    private static final UUID REACH_UUID = UUID.fromString("93c5a7d1-4e08-4b6f-a2d9-1f7e3b8c6a05");

    /** Zero durability = never breaks; no tier bonus so the numbers below are exact. */
    private static final Tier TIER = new Tier() {
        public int getUses() { return 0; }
        public float getSpeed() { return 8.0f; }
        public float getAttackDamageBonus() { return 0f; }
        public int getLevel() { return 4; }
        public int getEnchantmentValue() { return 15; }
        public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    private final Multimap<Attribute, AttributeModifier> mods;

    public RottedBladeItem() {
        // SwordItem adds the player's 1.0 base on top: 23 + 1 = 24 damage, -2.05 + 4.0 = 1.95 speed
        super(TIER, (int) (DAMAGE - 1.0), (float) (SPEED - 4.0), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(super.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND));
        b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID, "Weapon modifier", REACH - 3.0, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MAX_MANA.get(), new AttributeModifier(MANA_UUID, "Weapon modifier", MANA, AttributeModifier.Operation.ADDITION));
        b.put(AttributeRegistry.MANA_REGEN.get(), new AttributeModifier(REGEN_UUID, "Weapon modifier", REGEN - 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
        this.mods = b.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? mods : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.jjk_spells.rotted_blade.desc").withStyle(net.minecraft.ChatFormatting.DARK_RED));
        super.appendHoverText(stack, level, tip, flag);
    }

    /** True if the entity holds the sword in either hand. */
    public static boolean holds(net.minecraft.world.entity.LivingEntity e) {
        return e.getMainHandItem().getItem() instanceof RottedBladeItem || e.getOffhandItem().getItem() instanceof RottedBladeItem;
    }
}
