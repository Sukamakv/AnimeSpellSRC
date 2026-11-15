package com.example.jjkspells.capri;

import com.example.jjkspells.JJKSpells;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

/** The Capri curio (Absolute Relic slot) and her sounds. */
public final class CapriRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, JJKSpells.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<Item> CAPRI = ITEMS.register("capri", CapriItem::new);

    public static final RegistryObject<SoundEvent> BOOT = snd("boot"), SCAN = snd("scan"), LOCK = snd("lock"), LOST = snd("lost"), NOTIFY = snd("notify"),
            WARNING = snd("warning"), ON = snd("on"), OFF = snd("off"), CAST = snd("cast");
    public static final List<RegistryObject<SoundEvent>> BLIPS = List.of(snd("blip_0"), snd("blip_1"), snd("blip_2"), snd("blip_3"));

    private CapriRegistry() {
    }

    private static RegistryObject<SoundEvent> snd(String n) {
        return SOUNDS.register("capri_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("capri_" + n)));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        SOUNDS.register(bus);
        CapriEffects.register(bus);
    }

    /** An implanted AI. Worn in the Absolute Relic curio slot. */
    public static class CapriItem extends Item {
        public CapriItem() {
            super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.literal("CAPRI // NEURAL INTERFACE").withStyle(s -> s.withColor(0x53E4EA)));
            tooltip.add(Component.literal("Wear it in the Absolute Relic slot").withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(Component.literal("Auto-cast, target lock, target diagnostics, stat governor, input automation").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            tooltip.add(Component.literal("It never deals damage of its own.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
