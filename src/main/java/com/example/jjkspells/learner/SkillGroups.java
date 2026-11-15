package com.example.jjkspells.learner;

import java.util.List;

/** The sections of the Divine Skill Learner, weakest to strongest. Spell ids are all in the jjk_spells namespace. */
public final class SkillGroups {
    public record Group(String id, String title, int color, List<String> spells, String[] defaultMaterials) {
    }

    public static final List<Group> ALL = List.of(
            new Group("moon_breathing", "Moon Breathing", 0x8FB8FF,
                    List.of("moon_crescent_slash", "moon_whirl", "moon_rainbow"),
                    new String[]{"minecraft:iron_ingot*8", "minecraft:lapis_lazuli*8"}),
            new Group("darius", "Darius", 0xE05050,
                    List.of("decimate", "crippling_strike", "apprehend", "noxian_guillotine"),
                    new String[]{"minecraft:iron_ingot*16", "minecraft:redstone*16", "minecraft:gold_ingot*4"}),
            new Group("garen", "Garen", 0x6FD0FF,
                    List.of("decisive_strike", "judgment", "courage", "demacian_justice"),
                    new String[]{"minecraft:gold_ingot*8", "minecraft:iron_ingot*16", "minecraft:emerald*4"}),
            new Group("sun_breathing", "Sun Breathing", 0xFFA030,
                    List.of("sun_dance", "sun_radiance", "sun_setting", "sun_eternal"),
                    new String[]{"minecraft:gold_ingot*12", "minecraft:blaze_rod*6", "minecraft:magma_cream*4"}),
            new Group("gojo", "Gojo", 0xB890FF,
                    List.of("lapse_blue", "reversal_red", "simple_domain", "hollow_purple", "unlimited_void", "hollow_purple_nine_ropes"),
                    new String[]{"minecraft:diamond*8", "minecraft:lapis_lazuli*24", "minecraft:ender_pearl*8"}),
            new Group("sukuna", "Sukuna", 0xFF4040,
                    List.of("malevolent_shrine", "fuga", "world_cutting_slash"),
                    new String[]{"minecraft:diamond*12", "minecraft:ghast_tear*3", "minecraft:redstone*32"}),
            new Group("viego", "Viego", 0x40E0B0,
                    List.of("blade_of_the_ruined_king", "spectral_maw", "harrowed_path", "sovereigns_domination", "heartbreaker"),
                    new String[]{"minecraft:diamond*16", "minecraft:soul_sand*32", "minecraft:ghast_tear*4"}),
            new Group("madara", "Madara", 0x6070FF,
                    List.of("azure_orb_barrage", "azure_colossus", "gale_barrage", "storm_lords_wrath", "tengai_shinsei"),
                    new String[]{"minecraft:diamond*20", "minecraft:obsidian*32", "minecraft:ender_eye*8"}),
            new Group("aatrox", "Aatrox", 0xC02040,
                    List.of("darkin_blade", "infernal_chains", "umbral_dash", "world_ender", "oblivions_descent"),
                    new String[]{"minecraft:diamond*24", "minecraft:netherite_scrap*4", "minecraft:blaze_rod*16"}),
            new Group("dio", "Dio", 0xFFD030,
                    List.of("dio_muda", "dio_beam", "dio_impact", "dio_roller", "time_stop"),
                    new String[]{"minecraft:diamond*28", "minecraft:netherite_scrap*6", "minecraft:gold_block*4"}),
            new Group("jotaro", "Jotaro", 0xA070FF,
                    List.of("jotaro_ora", "jotaro_star_finger", "jotaro_guard", "jotaro_onslaught", "time_stop"),
                    new String[]{"minecraft:diamond_block*4", "minecraft:netherite_ingot*1", "minecraft:ender_eye*12"}),
            new Group("ainz", "Ainz Ooal Gown", 0xB040E0,
                    List.of("summon_undead", "cry_of_the_banshee", "black_hole", "goal_of_all_life_is_death", "fallen_down", "reality_slash",
                            "grasp_heart", "black_goat_sacrifice"),
                    new String[]{"minecraft:diamond_block*6", "minecraft:netherite_ingot*2", "minecraft:wither_skeleton_skull*1"}),
            new Group("hu_tao", "Hu Tao", 0xFF5030,
                    List.of("guide_to_afterlife", "secret_spear_of_wangsheng", "spirit_soother", "funeral_of_a_thousand_butterflies"),
                    new String[]{"minecraft:diamond_block*5", "minecraft:netherite_ingot*2", "minecraft:blaze_rod*24"}),
            new Group("acheron", "Acheron", 0x8A4CFF,
                    List.of("trilateral_wiltcross", "octobolt_flash", "quadrivalent_ascendance", "atop_rainleaf_hangs_oneness", "rainblade", "stygian_resurge", "slashed_dream_cries_in_red"),
                    new String[]{"minecraft:diamond_block*6", "minecraft:netherite_ingot*3", "minecraft:amethyst_shard*32"}),
            new Group("pantheon", "Pantheon", 0xFFC040,
                    List.of("comet_spear", "shield_vault", "aegis_storm", "starfall_execution", "grand_starfall"),
                    new String[]{"minecraft:diamond_block*6", "minecraft:netherite_ingot*3", "minecraft:gold_block*16"}),
            new Group("malphite", "Malphite", 0xA0724A,
                    List.of("seismic_shard", "thunderclap", "brutal_strikes", "granite_shield", "unstoppable_force"),
                    new String[]{"minecraft:diamond_block*6", "minecraft:netherite_ingot*3", "minecraft:obsidian*16"}),
            new Group("malenia", "Malenia", 0xC02030,
                    List.of("waterfowl_dance", "phantom_blossom", "scarlet_bloom", "miquellas_blade", "goddess_of_rot"),
                    new String[]{"minecraft:diamond_block*6", "minecraft:netherite_ingot*3", "minecraft:gold_block*16"}),
            new Group("gilgamesh", "Gilgamesh", 0xFFE060,
                    List.of("gate_of_babylon", "enkidu_chains_of_heaven", "vimana", "merodach", "enuma_elish"),
                    new String[]{"minecraft:gold_block*16", "minecraft:netherite_ingot*3", "minecraft:nether_star*1"}),
            new Group("rimuru", "Rimuru", 0x3A7BFF,
                    List.of("predator", "black_lightning", "megiddo", "absolute_defense", "spatial_domination", "beelzebuth", "imaginary_collapse", "void_god_azathoth"),
                    new String[]{"minecraft:diamond_block*8", "minecraft:netherite_ingot*4", "minecraft:nether_star*1"}),
            new Group("diablo", "Diablo", 0xC0102A,
                    List.of("nether_apocalypse", "primordial_dominion", "dimensional_funeral", "absolute_negation", "despair_time", "temptation_world", "abyssal_genesis", "temptation_king_azazel"),
                    new String[]{"minecraft:diamond_block*8", "minecraft:netherite_ingot*4", "minecraft:nether_star*1"}),
            new Group("true_rimuru", "True Rimuru", 0x4A78FF,
                    List.of("world_devouring_abyss", "civilization_storm", "heavenly_execution_array", "absolute_law_of_protection", "fracture_of_infinite_worlds", "abyss_that_consumes_all", "birth_of_nothingness", "end_of_all_creation"),
                    new String[]{"minecraft:netherite_block*2", "minecraft:nether_star*4", "minecraft:dragon_breath*16"}),
            new Group("veldora", "Veldora Tempest: Storm Dragon", 0x9A5BFF,
                    List.of("storm_dragons_wrath", "black_thunder_apocalypse", "dragon_breath", "storm_dragons_fist", "law_of_impossible_fate", "storm_dragons_ascension", "dragon_nova", "end_of_the_eternal_storm"),
                    new String[]{"minecraft:netherite_block*2", "minecraft:nether_star*4", "minecraft:dragon_breath*16"}),
            new Group("velgrynd", "Velgrynd: Scorch Dragon", 0xFF5A2A,
                    List.of("scorch_dragons_breath", "cardinal_inferno", "cardinal_acceleration", "scorch_dragons_dominion", "parallel_existence", "dimensional_scorch", "cardinal_cataclysm", "flame_god_cthugha"),
                    new String[]{"minecraft:netherite_block*2", "minecraft:nether_star*4", "minecraft:blaze_rod*32"}),
            new Group("zegion", "Zegion", 0x9A5CFF,
                    List.of("kaisers_impact", "mist_lords_domain", "obsidian_kaiser", "phantom_kaiser", "mephistos_labyrinth", "void_carapace", "kaisers_final_form", "illusion_king_mephisto"),
                    new String[]{"minecraft:diamond_block*8", "minecraft:netherite_ingot*4", "minecraft:amethyst_shard*48"}),
            new Group("shadow", "Shadow: Eminence in Shadow", 0x8A4DFF,
                    List.of("shadows_garden", "nameless_king", "slime_armament", "perfect_counter", "i_am_atomic"),
                    new String[]{"minecraft:netherite_block*2", "minecraft:nether_star*4", "minecraft:amethyst_block*32"}),
            new Group("vergil", "Vergil: Devil May Cry", 0x1766C2,
                    List.of("vergil_judgement_cut_end", "vergil_spiral_swords", "vergil_dimension_rush", "vergil_devil_trigger", "vergil_helm_breaker", "vergil_crescent_wave", "vergil_dimension_sever"),
                    new String[]{"minecraft:netherite_block*200"}),
            new Group("surtr", "Surtr", 0xFF7030,
                    List.of("smouldering_fire", "seethe", "sviga_laevi", "twilight_world_in_flames"),
                    new String[]{"minecraft:diamond_block*6", "minecraft:netherite_ingot*3", "minecraft:blaze_rod*32"}),
            new Group("ronova", "Ronova", 0xFF60FF,
                    List.of("death_wraiths", "crimson_wrath", "crimson_ray", "true_dragon_ascension"),
                    new String[]{"minecraft:netherite_ingot*4", "minecraft:nether_star*2", "minecraft:dragon_breath*8"}),
            new Group("yhwach", "Yhwach", 0x5CB8FF,
                    List.of("yhwach_heiliger_pfeil", "yhwach_qual_kreis", "yhwach_kirchenlied_sankt_zwinger", "yhwach_vollstreckung", "yhwach_auswaehlen", "yhwach_the_almighty"),
                    new String[]{"minecraft:netherite_ingot*4", "minecraft:nether_star*2", "minecraft:diamond_block*4", "minecraft:amethyst_shard*32"}),
            new Group("cosmic_garou", "Cosmic Garou", 0x7A5CFF,
                    List.of("garou_gamma_ray_burst", "garou_cosmic_radiation", "garou_gravity_knuckle", "garou_cosmic_portal", "nuclear_fission", "all_life_eradication_fist", "gamma_ray_burst"),
                    new String[]{"minecraft:netherite_ingot*4", "minecraft:nether_star*2", "minecraft:diamond_block*4", "minecraft:amethyst_shard*32"}),
            new Group("frieren", "Frieren", 0x9FE8D0,
                    List.of("frieren_zoltraak", "frieren_defensive_magic", "frieren_flammes_ultimate_magic", "frieren_vollzanbel", "frieren_ancient_release", "frieren_height_of_magic"),
                    new String[]{"minecraft:netherite_ingot*3", "minecraft:nether_star*2", "minecraft:diamond_block*4", "minecraft:emerald_block*4"}),
            new Group("yone", "Yone", 0xB04040,
                    List.of("yone_twin_crescents", "yone_spirit_cleave", "yone_ghost_walk", "yone_fate_sealed", "yone_horned_volley", "yone_twin_moon_eclipse", "yone_destiny_cleaved"),
                    new String[]{"minecraft:netherite_ingot*3", "minecraft:nether_star*2", "minecraft:diamond_block*4", "minecraft:gold_block*4"}),
            new Group("yasuo", "Yasuo", 0x7FD0E8,
                    List.of("yasuo_steel_tempest", "yasuo_wind_wall", "yasuo_sweeping_blade", "yasuo_windbound_duel", "yasuo_last_breath", "yasuo_reaping_cyclone", "yasuo_thousand_winds"),
                    new String[]{"minecraft:netherite_ingot*3", "minecraft:nether_star*2", "minecraft:diamond_block*4", "minecraft:gold_block*4"}),
            new Group("dante", "Dante", 0xD02030,
                    List.of("dante_million_stab", "dante_stylish_rank", "dante_royal_guard", "dante_cavaliere_ride", "dante_devil_trigger", "dante_round_trip", "dante_sparda_judgment"),
                    new String[]{"minecraft:netherite_ingot*3", "minecraft:nether_star*2", "minecraft:diamond_block*4", "minecraft:gold_block*4"}),
            new Group("wards", "Curio Wards", 0x6FE0C0,
                    List.of("item_ward_bracelet", "item_ward_charm", "item_ward_ring", "item_ward_belt", "item_ward_amulet"),
                    new String[]{"minecraft:diamond*6", "minecraft:gold_ingot*16", "minecraft:amethyst_shard*16"}),
            new Group("relics", "Relics", 0xFFD76A,
                    List.of("item_moon_breathing_relic", "item_darius_relic", "item_garen_relic", "item_sun_breathing_relic", "item_gojo_relic", "item_sukuna_relic", "item_viego_relic", "item_madara_relic", "item_aatrox_relic", "item_dio_relic", "item_jotaro_relic", "item_ainz_relic", "item_hu_tao_relic", "item_acheron_relic", "item_pantheon_relic", "item_malphite_relic", "item_malenia_relic", "item_gilgamesh_relic", "item_rimuru_relic", "item_diablo_relic", "item_true_rimuru_relic", "item_veldora_relic", "item_velgrynd_relic", "item_zegion_relic", "item_shadow_relic", "item_vergil_relic", "item_surtr_relic", "item_ronova_relic", "item_yhwach_relic", "item_cosmic_garou_relic", "item_frieren_relic", "item_yone_relic", "item_yasuo_relic", "item_dante_relic"),
                    new String[]{"minecraft:diamond_block*4", "minecraft:netherite_ingot*2", "minecraft:nether_star*1"}),
            new Group("absolute", "Absolute Relics", 0x53E4EA,
                    List.of("item_capri"),
                    new String[]{"minecraft:netherite_block*16", "minecraft:nether_star*8", "minecraft:diamond_block*16"}),
            // ---- PRIME sections (PRIME Divinity Skill Learner). Group ids start with "prime_"; the rest of the id is the base character. ----
            new Group("prime_gojo", "PRIME Gojo", 0xFF5CE0,
                    List.of("void_blue", "heavenly_red", "divine_purple", "heavenly_onslaught"),
                    new String[]{"minecraft:netherite_block*1", "minecraft:nether_star*2", "minecraft:diamond_block*8"}),
            new Group("prime_sukuna", "PRIME Sukuna", 0xD02030,
                    List.of("boundless_slash", "world_disintegration", "universe_severing_slash"),
                    new String[]{"minecraft:netherite_block*1", "minecraft:nether_star*2", "minecraft:diamond_block*8"})
    );

    /** The scrolls a PRIME spell is evolved from (consumed when you learn it; switch off with require_base_scrolls in the learner config). */
    public static final java.util.Map<String, List<String>> PRIME_BASE = java.util.Map.of(
            "void_blue", List.of("lapse_blue"),
            "heavenly_red", List.of("reversal_red"),
            "divine_purple", List.of("hollow_purple"),
            "heavenly_onslaught", List.of("unlimited_void", "hollow_purple_nine_ropes"),
            "world_disintegration", List.of("fuga"),
            "universe_severing_slash", List.of("world_cutting_slash"));

    public static final String PRIME_PREFIX = "prime_";

    public static boolean isPrime(Group g) {
        return g.id().startsWith(PRIME_PREFIX);
    }

    /** The boss that must be killed to unlock each section (group id to entity id). Sections not listed need no boss. Editable in the learner. */
    public static final java.util.Map<String, String> DEFAULT_BOSS = java.util.Map.ofEntries(
            java.util.Map.entry("moon_breathing", "jjk_bosses:kokushibo"),
            java.util.Map.entry("sun_breathing", "jjk_bosses:yoriichi"),
            java.util.Map.entry("darius", "jjk_bosses:darius"),
            java.util.Map.entry("garen", "jjk_bosses:garen"),
            java.util.Map.entry("gojo", "jjk_bosses:gojo"),
            java.util.Map.entry("sukuna", "jjk_bosses:sukuna"),
            java.util.Map.entry("viego", "jjk_bosses:viego"),
            java.util.Map.entry("madara", "jjk_bosses:madara"),
            java.util.Map.entry("aatrox", "jjk_bosses:aatrox"),
            java.util.Map.entry("dio", "jjk_bosses:dio"),
            java.util.Map.entry("jotaro", "jjk_bosses:jotaro"),
            java.util.Map.entry("ainz", "jjk_bosses:ainz"),
            java.util.Map.entry("hu_tao", "jjk_bosses:hutao"),
            java.util.Map.entry("acheron", "jjk_bosses:acheron"),
            java.util.Map.entry("pantheon", "jjk_bosses:pantheon"),
            java.util.Map.entry("malphite", "jjk_bosses:nino"),
            java.util.Map.entry("malenia", "jjk_bosses:malenia"),
            java.util.Map.entry("gilgamesh", "jjk_bosses:gilgamesh"),
            java.util.Map.entry("surtr", "jjk_bosses:surtr"),
            java.util.Map.entry("ronova", "jjk_bosses:ronova"));

    /** Entries whose id starts with this are items (not spell scrolls): "item_ward_ring" gives jjk_spells:ward_ring. */
    public static final String ITEM_PREFIX = "item_";

    public static boolean isItem(String path) {
        return path.startsWith(ITEM_PREFIX);
    }

    public static String itemPath(String path) {
        return path.substring(ITEM_PREFIX.length());
    }

    private SkillGroups() {
    }
}
