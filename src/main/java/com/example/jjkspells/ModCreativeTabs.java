package com.example.jjkspells;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** One creative tab holding every grimoire and a scroll of every spell (every level) from this mod. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JJKSpells.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jjk_spells.main"))
                    .icon(() -> new ItemStack(ModItems.SUN_GRIMOIRE.get()))
                    .displayItems((params, output) -> {
                        ModItems.ITEMS.getEntries().forEach(item -> {
                            if (item.get() instanceof com.example.jjkspells.item.PercentWardItem w && w.isGreat()) return;   // unobtainable
                            if (item.get() instanceof com.example.jjkspells.relic.RelicItem) return;   // only the Divine Skill Learner makes relics
                            output.accept(item.get());
                        });
                        for (RegistryObject<AbstractSpell> ro : ModSpells.SPELLS.getEntries()) {
                            AbstractSpell spell = ro.get();
                            for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
                                ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
                                ISpellContainer.createScrollContainer(spell, lvl, scroll);
                                output.accept(scroll);
                            }
                        }
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
