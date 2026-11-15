package com.example.jjkspells.zegion.registry;

import com.google.common.collect.ImmutableMultimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellDataRegistryHolder;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.UniqueSpellBook;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/** Zegion's grimoire, registered in the main mod's item list so it appears in the creative tab. */
public final class ModItems {
    private static final UUID ZEGION_UUID = UUID.fromString("7e1b9d52-3a64-4c8f-b2d0-5e9a1c7000a1");

    /** Unique spellbook pre-bound with the eight spells of the Insect Kaiser (level 1). */
    public static final RegistryObject<Item> GRIMOIRE = com.example.jjkspells.ModItems.ITEMS.register(
            "zegion_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.IMPACT, 1),
                            new SpellDataRegistryHolder(ModSpells.DOMAIN, 1),
                            new SpellDataRegistryHolder(ModSpells.OBSIDIAN, 1),
                            new SpellDataRegistryHolder(ModSpells.PHANTOM, 1),
                            new SpellDataRegistryHolder(ModSpells.LABYRINTH, 1),
                            new SpellDataRegistryHolder(ModSpells.VOID, 1),
                            new SpellDataRegistryHolder(ModSpells.FORM, 1),
                            new SpellDataRegistryHolder(ModSpells.MEPHISTO, 1)
                    },
                    8,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.ELDRITCH_SPELL_POWER.get(),
                                new AttributeModifier(ZEGION_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(ZEGION_UUID, "Weapon modifier", 400, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(ZEGION_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private ModItems() {
    }

    /** Touching this class registers the grimoire. */
    public static void init() {
    }
}
