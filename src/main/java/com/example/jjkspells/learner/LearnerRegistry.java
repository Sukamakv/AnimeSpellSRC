package com.example.jjkspells.learner;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class LearnerRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, JJKSpells.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, JJKSpells.MODID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("divine_skill_learner", () -> new SkillLearnerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0f, 1200.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 9).noOcclusion()));

    public static final RegistryObject<Block> PRIME_BLOCK = BLOCKS.register("prime_divinity_skill_learner", () -> new SkillLearnerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(8.0f, 3600000.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 14).noOcclusion(), true));

    public static final RegistryObject<MenuType<SkillLearnerMenu>> MENU = MENUS.register("divine_skill_learner",
            () -> IForgeMenuType.create(SkillLearnerMenu::new));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        MENUS.register(bus);
    }

    private LearnerRegistry() {
    }
}
