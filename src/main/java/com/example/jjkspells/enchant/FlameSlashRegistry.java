package com.example.jjkspells.enchant;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FlameSlashRegistry {
    private static final DeferredRegister<Enchantment> ENCHANTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, JJKSpells.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<Enchantment> FLAME_SLASH = ENCHANTS.register("flame_slash", FlameSlashEnchantment::new);

    public static final RegistryObject<EntityType<FlameSlashEntity>> SLASH = ENTITIES.register("flame_slash",
            () -> EntityType.Builder.<FlameSlashEntity>of(FlameSlashEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave().build(JJKSpells.id("flame_slash").toString()));

    private FlameSlashRegistry() {}

    /** True for an item or enchanted book carrying Flame Slash. */
    public static boolean hasFlameSlash(net.minecraft.world.item.ItemStack s) {
        if (s.isEmpty()) return false;
        // getEnchantments reads StoredEnchantments for enchanted books too
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(s).containsKey(FLAME_SLASH.get());
    }

    public static void register(IEventBus bus) {
        ENCHANTS.register(bus);
        ENTITIES.register(bus);
    }

    /** Swords only. */
    public static class FlameSlashEnchantment extends Enchantment {
        public FlameSlashEnchantment() {
            super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        }

        @Override
        public boolean isTreasureOnly() { return true; }

        @Override
        public boolean isDiscoverable() { return false; }

        @Override
        public boolean isTradeable() { return false; }

        @Override
        public boolean canApplyAtEnchantingTable(net.minecraft.world.item.ItemStack stack) { return false; }

        @Override
        public int getMaxLevel() {
            return 1;
        }

        @Override
        public int getMinCost(int level) {
            return 20;
        }

        @Override
        public int getMaxCost(int level) {
            return 50;
        }

        @Override
        public boolean canEnchant(net.minecraft.world.item.ItemStack stack) {
            return stack.getItem() instanceof net.minecraft.world.item.SwordItem;
        }
    }
}
