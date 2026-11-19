package com.example.jjkspells;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/jjk_spells-balance.toml - which spells wound (stop healing) and stun, and for how long.
 * Every spell has {@code antiHealSeconds}, {@code stunSeconds} and {@code windowSeconds} (how long after the cast
 * the spell's hits carry the debuffs). Each target is debuffed once per cast, on the first hit it takes.
 */
public final class BalanceConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue PLAYER_SCALE, BOSS_STUN_SCALE, PLAYER_STUN_CAP;

    public record Rule(ForgeConfigSpec.DoubleValue antiHeal, ForgeConfigSpec.DoubleValue stun, ForgeConfigSpec.DoubleValue window) {}

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();

    /** id, anti-heal seconds, stun seconds, window seconds. Spells that already stun/wound on their own have 0 for that part. */
    private static final String[] TABLE = {
            // Moon Breathing
            "moon_whirl,4,0,4", "moon_rainbow,6,0,5",
            // Darius (already wound on hit; the stun is new)
            "decimate,6,0,3", "crippling_strike,5,0,3", "apprehend,6,0,3", "noxian_guillotine,8,0,3",
            // Garen (Decisive Strike already stuns)
            "judgment,5,0,4", "demacian_justice,8,0,5",
            // Sun Breathing
            "sun_dance,5,0,4", "sun_radiance,6,0,5", "sun_setting,6,0,4", "sun_eternal,10,0,8",
            // Gojo
            "lapse_blue,0,0,3", "reversal_red,6,0,3", "hollow_purple,8,0,3", "unlimited_void,10,0,12", "hollow_purple_nine_ropes,8,0,4",
            // Sukuna
            "malevolent_shrine,8,0,12", "fuga,8,0,4", "world_cutting_slash,8,0,4",
            // Viego (Spectral Maw already stuns)
            "blade_of_the_ruined_king,5,0,3", "spectral_maw,6,0,4", "harrowed_path,6,0,6", "sovereigns_domination,8,0,6", "heartbreaker,8,0,4",
            // Madara
            "azure_orb_barrage,4,0,6", "azure_colossus,6,0,6", "gale_barrage,4,0,5", "storm_lords_wrath,8,0,6", "tengai_shinsei,10,0,8",
            // Aatrox (Darkin Blade and Infernal Chains already stun)
            "darkin_blade,6,0,3", "infernal_chains,10,0,4", "umbral_dash,5,0,2", "world_ender,8,0,12", "oblivions_descent,10,0,3",
            // Dio
            "dio_muda,5,0,4", "dio_beam,5,0,3", "dio_impact,6,0,3", "dio_roller,6,0,3",
            // Jotaro
            "jotaro_ora,5,0,4", "jotaro_star_finger,5,0,3", "jotaro_onslaught,8,0,5",
            // Ainz (Grasp Heart already stuns)
            "cry_of_the_banshee,4,0,4", "black_hole,8,0,6", "goal_of_all_life_is_death,10,0,5", "fallen_down,6,0,5", "reality_slash,8,0,3",
            "grasp_heart,8,0,3", "black_goat_sacrifice,5,0,20",
            // Hu Tao
            "guide_to_afterlife,6,0,9", "secret_spear_of_wangsheng,6,0,3", "funeral_of_a_thousand_butterflies,8,0,8",
            // Gilgamesh (Enkidu's Chains already stun)
            "gate_of_babylon,5,0,4", "enkidu_chains_of_heaven,6,0,5", "vimana,6,0,7", "merodach,8,0,5", "enuma_elish,10,0,12",
            // Rimuru
            "predator,5,0,3", "black_lightning,6,0,3", "megiddo,6,0,5", "absolute_defense,4,0,3", "spatial_domination,8,0,4",
            "beelzebuth,10,0,6", "imaginary_collapse,10,0,7", "void_god_azathoth,12,0,14",
            // Diablo
            "nether_apocalypse,6,0,6", "primordial_dominion,8,0,5", "dimensional_funeral,8,0,5", "absolute_negation,4,0,4",
            "despair_time,10,0,8", "temptation_world,10,0,10", "abyssal_genesis,10,0,8", "temptation_king_azazel,12,0,14",
            // True Rimuru
            "world_devouring_abyss,10,0,8", "civilization_storm,8,0,6", "heavenly_execution_array,8,0,6", "absolute_law_of_protection,4,0,4",
            "fracture_of_infinite_worlds,10,0,7", "abyss_that_consumes_all,10,0,7", "birth_of_nothingness,10,0,7", "end_of_all_creation,12,0,14",
            // Veldora
            "storm_dragons_wrath,8,0,6", "black_thunder_apocalypse,8,0,6", "dragon_breath,6,0,4", "storm_dragons_fist,6,0,4",
            "law_of_impossible_fate,4,0,4", "storm_dragons_ascension,6,0,6", "dragon_nova,10,0,7", "end_of_the_eternal_storm,12,0,14",
            // Velgrynd
            "scorch_dragons_breath,6,0,4", "cardinal_inferno,8,0,6", "cardinal_acceleration,4,0,4", "scorch_dragons_dominion,8,0,6",
            "parallel_existence,6,0,5", "dimensional_scorch,10,0,7", "cardinal_cataclysm,10,0,8", "flame_god_cthugha,12,0,14",
            // Zegion
            "kaisers_impact,6,0,4", "mist_lords_domain,8,0,12", "obsidian_kaiser,4,0,4", "phantom_kaiser,8,0,5", "mephistos_labyrinth,10,0,12",
            "void_carapace,8,0,10", "kaisers_final_form,6,0,4", "illusion_king_mephisto,12,0,20",
            // Shadow
            "shadows_garden,8,0,10", "nameless_king,8,0,8", "slime_armament,6,0,8", "perfect_counter,6,0,4", "i_am_atomic,12,0,14",
            // Ronova
            "death_wraiths,5,0,8", "crimson_wrath,8,0,6", "crimson_ray,8,0,4", "true_dragon_ascension,6,0,20",
            // Laevatain
            "smouldering_fire,4,0,4", "seethe,5,0,5", "sviga_laevi,6,0,4", "twilight_world_in_flames,10,0,8",
            // Pantheon
            "comet_spear,5,0,4", "shield_vault,5,0,4", "aegis_storm,6,0,6", "starfall_execution,8,0,5", "grand_starfall,10,0,8",
            // Malphite
            "seismic_shard,4,0,4", "thunderclap,6,0,4", "brutal_strikes,6,0,5", "granite_shield,4,0,4", "unstoppable_force,10,0,6",
    };

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        ENABLED = b.comment("Master switch for the wound / stun riders on spells").define("enabled", true);
        PLAYER_SCALE = b.comment("Durations are multiplied by this when a PLAYER casts (bosses and mobs always use 1.0)")
                .defineInRange("player_duration_scale", 0.6, 0.0, 10.0);
        BOSS_STUN_SCALE = b.comment("Stun duration multiplier against bosses (entities in the forge:bosses tag)")
                .defineInRange("boss_stun_scale", 0.35, 0.0, 1.0);
        PLAYER_STUN_CAP = b.comment("Longest stun, in seconds, that can land on a player")
                .defineInRange("max_stun_on_players_seconds", 2.5, 0.0, 60.0);
        b.pop();
        b.comment("Per spell: antiHealSeconds = no healing at all (plus Alex's Caves Irradiated), stunSeconds = cannot move, turn, attack, cast.",
                "windowSeconds = how long after the cast its hits carry these. Set both to 0 to remove the riders.").push("spells");
        for (String row : TABLE) {
            String[] p = row.split(",");
            b.push(p[0]);
            RULES.put(p[0], new Rule(
                    b.defineInRange("antiHealSeconds", Double.parseDouble(p[1]), 0.0, 600.0),
                    b.defineInRange("stunSeconds", Double.parseDouble(p[2]), 0.0, 60.0),
                    b.defineInRange("windowSeconds", Double.parseDouble(p[3]), 0.0, 600.0)));
            b.pop();
        }
        b.pop();
        SPEC = b.build();
    }

    private BalanceConfig() {}

    @Nullable
    public static Rule rule(ResourceLocation spellId) {
        return spellId != null && JJKSpells.MODID.equals(spellId.getNamespace()) ? RULES.get(spellId.getPath()) : null;
    }
}
