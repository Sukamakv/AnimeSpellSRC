package com.example.jjkspells.acheron.item;

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
 * Nihility's Bane: Acheron's katana. Damage 24, speed 1.95, reach 5, unbreakable, +800 max mana and x1.3 mana regen
 * while held. Better Combat treats it as a katana (data/jjk_spells/weapon_attributes/nihilitys_bane.json).
 */
public class NihilitysBaneItem extends SwordItem {
    public static final double DAMAGE = 24.0, SPEED = 1.95, REACH = 5.0, MANA = 800.0, REGEN = 1.3;
    private static final UUID MANA_UUID = UUID.fromString("5b0d6e0a-7c31-4f6e-9a52-2e1c8d3f4a71");
    private static final UUID REGEN_UUID = UUID.fromString("c4a9e1b2-3d58-4b7a-8f06-91e2d7a3c5b8");
    private static final UUID REACH_UUID = UUID.fromString("e7f20c13-9b64-4a8d-b1c5-6a3d0f8e2b49");

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

    public NihilitysBaneItem() {
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
        tip.add(Component.translatable("item.jjk_spells.nihilitys_bane.desc").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, level, tip, flag);
    }

    /** True if the entity holds the sword in either hand. */
    public static boolean holds(net.minecraft.world.entity.LivingEntity e) {
        return e.getMainHandItem().getItem() instanceof NihilitysBaneItem || e.getOffhandItem().getItem() instanceof NihilitysBaneItem;
    }
}
