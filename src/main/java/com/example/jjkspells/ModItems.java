package com.example.jjkspells;

import com.google.common.collect.ImmutableMultimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellDataRegistryHolder;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.UniqueSpellBook;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JJKSpells.MODID);

    private static final UUID BOOK_UUID = UUID.fromString("667ad88f-901d-4691-b2a2-3664e42026d3");

    /** Unique spellbook: comes pre-bound with Malevolent Shrine and Fuga (cannot be edited). */
    public static final RegistryObject<Item> KING_OF_CURSES_GRIMOIRE = ITEMS.register(
            "king_of_curses_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.MALEVOLENT_SHRINE, 5),
                            new SpellDataRegistryHolder(ModSpells.FUGA, 5),
                            new SpellDataRegistryHolder(ModSpells.WORLD_CUTTING_SLASH, 5),
                            new SpellDataRegistryHolder(ModSpells.CRIMSON_WRATH, 1),
                            new SpellDataRegistryHolder(ModSpells.CRIMSON_RAY, 1),
                            new SpellDataRegistryHolder(ModSpells.DEATH_WRAITHS, 1),
                            new SpellDataRegistryHolder(ModSpells.TIME_STOP, 5),
                            new SpellDataRegistryHolder(ModSpells.STORM_LORDS_WRATH, 1),
                            new SpellDataRegistryHolder(ModSpells.GALE_BARRAGE, 1),
                            new SpellDataRegistryHolder(ModSpells.TENGAI_SHINSEI, 1),
                            new SpellDataRegistryHolder(ModSpells.AZURE_COLOSSUS, 1),
                            new SpellDataRegistryHolder(ModSpells.AZURE_ORB_BARRAGE, 1)
                    },
                    12, // max spell slots
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.BLOOD_SPELL_POWER.get(),
                                new AttributeModifier(BOOK_UUID, "Weapon modifier", 0.20, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(BOOK_UUID, "Weapon modifier", 300, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(BOOK_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final UUID HONORED_UUID = UUID.fromString("a1c3b6d2-5e7f-4c18-9b42-3d0e8f6a7c15");

    /** Unique spellbook pre-bound with Lapse: Blue (5), Reversal: Red (3), Hollow Purple (6), Unlimited Void (6), Simple Domain (3) and Hollow Purple: Nine Ropes (1). */
    public static final RegistryObject<Item> HONORED_ONE_GRIMOIRE = ITEMS.register(
            "honored_one_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.LAPSE_BLUE, 5),
                            new SpellDataRegistryHolder(ModSpells.REVERSAL_RED, 3),
                            new SpellDataRegistryHolder(ModSpells.HOLLOW_PURPLE, 6),
                            new SpellDataRegistryHolder(ModSpells.UNLIMITED_VOID, 6),
                            new SpellDataRegistryHolder(ModSpells.SIMPLE_DOMAIN, 3),
                            new SpellDataRegistryHolder(ModSpells.NINE_ROPES, 1)
                    },
                    6,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.ENDER_SPELL_POWER.get(),
                                new AttributeModifier(HONORED_UUID, "Weapon modifier", 0.20, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(HONORED_UUID, "Weapon modifier", 400, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(HONORED_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final UUID FIST_UUID = UUID.fromString("5d2e7b94-1c36-4a8f-b7e0-9f4a2c6d8e13");

    /** Unique spellbook pre-bound with Barrage of Punches (5), Ground Smash (5) and Hard Strike (5). */
    public static final RegistryObject<Item> FIST_GRIMOIRE = ITEMS.register(
            "fist_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.EPIC,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.PUNCH_BARRAGE, 5),
                            new SpellDataRegistryHolder(ModSpells.GROUND_SMASH, 5),
                            new SpellDataRegistryHolder(ModSpells.HARD_STRIKE, 5)
                    },
                    2,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.EVOCATION_SPELL_POWER.get(),
                                new AttributeModifier(FIST_UUID, "Weapon modifier", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(FIST_UUID, "Weapon modifier", 200, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(FIST_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final UUID MOON_UUID = UUID.fromString("a7c41e58-3b92-4d0f-8e61-2f5d9b7c0a34");

    /** Unique spellbook pre-bound with Crescent Slash (5), Whirl (4) and Rainbow (3). */
    public static final RegistryObject<Item> MOON_GRIMOIRE = ITEMS.register(
            "moon_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.EPIC,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.MOON_CRESCENT_SLASH, 5),
                            new SpellDataRegistryHolder(ModSpells.MOON_WHIRL, 4),
                            new SpellDataRegistryHolder(ModSpells.MOON_RAINBOW, 3)
                    },
                    2,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.EVOCATION_SPELL_POWER.get(),
                                new AttributeModifier(MOON_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(MOON_UUID, "Weapon modifier", 250, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(MOON_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID GILGAMESH_UUID = java.util.UUID.fromString("7c2e9d41-3b58-4f6a-9e13-a5d0c4b8e2f7");

    /** Unique spellbook pre-bound with the moveset of the King of Heroes. */
    public static final RegistryObject<Item> GILGAMESH_GRIMOIRE = ITEMS.register(
            "gilgamesh_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.GATE_OF_BABYLON, 1),
                            new SpellDataRegistryHolder(ModSpells.ENKIDU_CHAINS_OF_HEAVEN, 1),
                            new SpellDataRegistryHolder(ModSpells.VIMANA, 1),
                            new SpellDataRegistryHolder(ModSpells.MERODACH, 1),
                            new SpellDataRegistryHolder(ModSpells.ENUMA_ELISH, 1)
                    },
                    5,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.HOLY_SPELL_POWER.get(),
                                new AttributeModifier(GILGAMESH_UUID, "Weapon modifier", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(GILGAMESH_UUID, "Weapon modifier", 500, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(GILGAMESH_UUID, "Weapon modifier", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    // ---- Relics (curios): one per character group; worn in a Relic slot they unlock that character's spells ----
    public static final java.util.Map<String, RegistryObject<Item>> RELICS = new java.util.LinkedHashMap<>();

    static {
        for (com.example.jjkspells.learner.SkillGroups.Group g : com.example.jjkspells.learner.SkillGroups.ALL) {
            if (g.id().equals("wards") || g.id().equals("relics") || g.id().equals("absolute") || com.example.jjkspells.learner.SkillGroups.isPrime(g)) continue;
            RELICS.put(g.id(), ITEMS.register(g.id() + "_relic", () -> new com.example.jjkspells.relic.RelicItem(g.id(), g.title(), g.color())));
        }
    }

    private static final java.util.UUID SUN_UUID = java.util.UUID.fromString("a41f6c27-8d3e-4b95-b7e0-2c9a15d6f348");

    /** Unique spellbook pre-bound with the Sun Breathing forms. */
    public static final RegistryObject<Item> SUN_GRIMOIRE = ITEMS.register(
            "sun_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.SUN_DANCE, 5),
                            new SpellDataRegistryHolder(ModSpells.SUN_RADIANCE, 5),
                            new SpellDataRegistryHolder(ModSpells.SUN_SETTING, 5),
                            new SpellDataRegistryHolder(ModSpells.SUN_ETERNAL, 1)
                    },
                    4,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.FIRE_SPELL_POWER.get(),
                                new AttributeModifier(SUN_UUID, "Weapon modifier", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(SUN_UUID, "Weapon modifier", 400, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(SUN_UUID, "Weapon modifier", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID DARIUS_UUID = java.util.UUID.fromString("c3f81a27-6d05-4e9b-a4b8-71e2d09f5c68");

    public static final RegistryObject<Item> DARIUS_GRIMOIRE = ITEMS.register(
            "darius_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.DECIMATE, 1),
                            new SpellDataRegistryHolder(ModSpells.CRIPPLING_STRIKE, 1),
                            new SpellDataRegistryHolder(ModSpells.APPREHEND, 1),
                            new SpellDataRegistryHolder(ModSpells.NOXIAN_GUILLOTINE, 1)
                    },
                    4,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.BLOOD_SPELL_POWER.get(),
                                new AttributeModifier(DARIUS_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(DARIUS_UUID, "Weapon modifier", 250, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(DARIUS_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID GAREN_UUID = java.util.UUID.fromString("e9a2d514-7b3c-4f08-8c61-3d5f0b92a7e4");

    public static final RegistryObject<Item> GAREN_GRIMOIRE = ITEMS.register(
            "garen_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.DECISIVE_STRIKE, 1),
                            new SpellDataRegistryHolder(ModSpells.JUDGMENT, 1),
                            new SpellDataRegistryHolder(ModSpells.COURAGE, 1),
                            new SpellDataRegistryHolder(ModSpells.DEMACIAN_JUSTICE, 1)
                    },
                    4,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.HOLY_SPELL_POWER.get(),
                                new AttributeModifier(GAREN_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(GAREN_UUID, "Weapon modifier", 250, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(GAREN_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID DIO_UUID = java.util.UUID.fromString("5b7e1c92-0a4d-4d36-9f58-b2c61e0d3a77");

    public static final RegistryObject<Item> DIO_GRIMOIRE = ITEMS.register(
            "dio_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.DIO_MUDA, 1),
                            new SpellDataRegistryHolder(ModSpells.DIO_BEAM, 1),
                            new SpellDataRegistryHolder(ModSpells.DIO_IMPACT, 1),
                            new SpellDataRegistryHolder(ModSpells.DIO_ROLLER, 1)
                    },
                    4,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.BLOOD_SPELL_POWER.get(),
                                new AttributeModifier(DIO_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(DIO_UUID, "Weapon modifier", 300, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(DIO_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID AATROX_UUID = java.util.UUID.fromString("f4b2d6a0-19c7-4e83-b5a1-6d0e93c7a812");

    /** Unique spellbook pre-bound with Aatrox's five spells (level 1 = the exact numbers of the design). */
    public static final RegistryObject<Item> AATROX_GRIMOIRE = ITEMS.register(
            "aatrox_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.DARKIN_BLADE, 1),
                            new SpellDataRegistryHolder(ModSpells.INFERNAL_CHAINS, 1),
                            new SpellDataRegistryHolder(ModSpells.UMBRAL_DASH, 1),
                            new SpellDataRegistryHolder(ModSpells.WORLD_ENDER, 1),
                            new SpellDataRegistryHolder(ModSpells.OBLIVIONS_DESCENT, 1)
                    },
                    5,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.BLOOD_SPELL_POWER.get(),
                                new AttributeModifier(AATROX_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(AATROX_UUID, "Weapon modifier", 300, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(AATROX_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID JOTARO_UUID = java.util.UUID.fromString("c41d8e07-6b2a-4f93-a5e0-37d9b81f4c26");

    /** Unique spellbook pre-bound with Jotaro's four spells. */
    public static final RegistryObject<Item> JOTARO_GRIMOIRE = ITEMS.register(
            "jotaro_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.JOTARO_ORA, 1),
                            new SpellDataRegistryHolder(ModSpells.JOTARO_STAR_FINGER, 1),
                            new SpellDataRegistryHolder(ModSpells.JOTARO_GUARD, 1),
                            new SpellDataRegistryHolder(ModSpells.JOTARO_ONSLAUGHT, 1)
                    },
                    4,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.ENDER_SPELL_POWER.get(),
                                new AttributeModifier(JOTARO_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(JOTARO_UUID, "Weapon modifier", 300, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(JOTARO_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID OVERLORD_UUID = java.util.UUID.fromString("8d3a6f41-2c7b-4e95-b0a8-14f9c5e27d03");

    /** Unique spellbook pre-bound with the eight spells of the Sorcerer King. */
    public static final RegistryObject<Item> OVERLORD_GRIMOIRE = ITEMS.register(
            "overlord_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.SUMMON_UNDEAD, 5),
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.CRY_OF_THE_BANSHEE, 5),
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.BLACK_HOLE, 5),
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.GOAL_OF_ALL_LIFE_IS_DEATH, 5),
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.FALLEN_DOWN, 5),
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.REALITY_SLASH, 5),
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.BLACK_GOAT_SACRIFICE, 5),
                            new SpellDataRegistryHolder(com.example.jjkspells.overlord.registry.ModSpells.GRASP_HEART, 5)
                    },
                    8,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.ELDRITCH_SPELL_POWER.get(),
                                new AttributeModifier(OVERLORD_UUID, "Weapon modifier", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.BLOOD_SPELL_POWER.get(),
                                new AttributeModifier(OVERLORD_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(OVERLORD_UUID, "Weapon modifier", 400, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(OVERLORD_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    private static final java.util.UUID VIEGO_UUID = java.util.UUID.fromString("d1a96e33-84f7-4c0b-a2d5-3e7b18c04f95");

    /** Unique spellbook pre-bound with Viego's five spells (level 1 = the exact numbers of the design). */
    public static final RegistryObject<Item> VIEGO_GRIMOIRE = ITEMS.register(
            "viego_grimoire",
            () -> new UniqueSpellBook(
                    SpellRarity.LEGENDARY,
                    new SpellDataRegistryHolder[]{
                            new SpellDataRegistryHolder(ModSpells.BLADE_OF_THE_RUINED_KING, 1),
                            new SpellDataRegistryHolder(ModSpells.SPECTRAL_MAW, 1),
                            new SpellDataRegistryHolder(ModSpells.HARROWED_PATH, 1),
                            new SpellDataRegistryHolder(ModSpells.SOVEREIGNS_DOMINATION, 1),
                            new SpellDataRegistryHolder(ModSpells.HEARTBREAKER, 1)
                    },
                    2,
                    () -> {
                        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                        builder.put(AttributeRegistry.ELDRITCH_SPELL_POWER.get(),
                                new AttributeModifier(VIEGO_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        builder.put(AttributeRegistry.MAX_MANA.get(),
                                new AttributeModifier(VIEGO_UUID, "Weapon modifier", 300, AttributeModifier.Operation.ADDITION));
                        builder.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                                new AttributeModifier(VIEGO_UUID, "Weapon modifier", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
                        return builder.build();
                    }
            )
    );

    // ---- Wards (curios): reduce max-health percentage damage, 40% at most in total ----
    public static final RegistryObject<Item> WARD_RING = ITEMS.register("ward_ring",
            () -> new com.example.jjkspells.item.PercentWardItem(0.06f, net.minecraft.world.item.Rarity.UNCOMMON));

    public static final RegistryObject<Item> WARD_AMULET = ITEMS.register("ward_amulet",
            () -> new com.example.jjkspells.item.PercentWardItem(0.1f, net.minecraft.world.item.Rarity.RARE));

    public static final RegistryObject<Item> WARD_BELT = ITEMS.register("ward_belt",
            () -> new com.example.jjkspells.item.PercentWardItem(0.06f, net.minecraft.world.item.Rarity.UNCOMMON));

    public static final RegistryObject<Item> WARD_BRACELET = ITEMS.register("ward_bracelet",
            () -> new com.example.jjkspells.item.PercentWardItem(0.04f, net.minecraft.world.item.Rarity.UNCOMMON));

    public static final RegistryObject<Item> WARD_CHARM = ITEMS.register("ward_charm",
            () -> new com.example.jjkspells.item.PercentWardItem(0.04f, net.minecraft.world.item.Rarity.RARE));

    // ---- Great wards: unobtainable (no recipe, no loot, not in the creative tab; /give only). They sit outside the 40% pool. ----
    public static final RegistryObject<Item> AEGIS_SIGIL = ITEMS.register("aegis_sigil",
            () -> new com.example.jjkspells.item.PercentWardItem(0.25f, net.minecraft.world.item.Rarity.EPIC, true));

    public static final RegistryObject<Item> AEGIS_SEAL = ITEMS.register("aegis_seal",
            () -> new com.example.jjkspells.item.PercentWardItem(0.50f, net.minecraft.world.item.Rarity.EPIC, true));

    public static final RegistryObject<Item> AEGIS_HEART = ITEMS.register("aegis_heart",
            () -> new com.example.jjkspells.item.PercentWardItem(0.75f, net.minecraft.world.item.Rarity.EPIC, true));

    // ---- Divine Skill Learner (block item) ----
    public static final RegistryObject<Item> SKILL_LEARNER = ITEMS.register("divine_skill_learner",
            () -> new net.minecraft.world.item.BlockItem(com.example.jjkspells.learner.LearnerRegistry.BLOCK.get(),
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ---- PRIME Divinity Skill Learner (block item) ----
    public static final RegistryObject<Item> PRIME_SKILL_LEARNER = ITEMS.register("prime_divinity_skill_learner",
            () -> new net.minecraft.world.item.BlockItem(com.example.jjkspells.learner.LearnerRegistry.PRIME_BLOCK.get(),
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));
}
