package com.example.jjkspells.relic;

import com.example.jjkspells.JJKSpells;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Relics can never come out of loot: this global loot modifier removes them from every loot roll (chests, mobs, fishing, Lootr and
 * other loot-integration mods all go through loot tables). They are only obtainable from the Divine Skill Learner.
 */
public final class RelicLoot {
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> GLM =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, JJKSpells.MODID);

    public static final RegistryObject<Codec<StripRelics>> STRIP = GLM.register("strip_relics", StripRelics.CODEC);

    private RelicLoot() {}

    /** Anything of this mod, and any scroll, book or gear that carries one of its spells or enchants, may never come out of loot. */
    static boolean ours(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        try {
            var key = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (key != null && key.getNamespace().equals(JJKSpells.MODID)) {
                return true;
            }
            if (s.getItem() instanceof RelicItem || com.example.jjkspells.enchant.FlameSlashRegistry.hasFlameSlash(s)) {
                return true;
            }
            return s.hasTag() && s.getTag().toString().contains(JJKSpells.MODID + ":");
        } catch (Throwable t) {
            return false;
        }
    }

    public static void register(IEventBus bus) {
        GLM.register(bus);
    }

    public static class StripRelics extends LootModifier {
        public static final Supplier<Codec<StripRelics>> CODEC = Suppliers.memoize(() ->
                RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, StripRelics::new)));

        public StripRelics(LootItemCondition[] conditions) {
            super(conditions);
        }

        @NotNull
        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            // this mod's own tables (the learner block's drop) are left alone
            if (context.getQueriedLootTableId().getNamespace().equals(JJKSpells.MODID)) {
                return loot;
            }
            loot.removeIf(RelicLoot::ours);
            return loot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC.get();
        }
    }
}
