package com.example.jjkspells.irontomb;

import java.util.ArrayList;
import java.util.List;

/** The fields shown in the Irontomb configurator screen (key = name in the .cfg file). */
public final class IrontombConfigSpec {
    public record Field(String key, String label, double def, double min, double max, int tab) { }

    public static final String[] TABS = {"Boss", "Attacks I", "Attacks II", "Satellites / Mana", "Specials", "Phainon", "Phainon Atk"};
    public static final List<Field> FIELDS = new ArrayList<>();

    private static void f(String key, String label, double def, double min, double max, int tab) {
        FIELDS.add(new Field(key, label, def, min, max, tab));
    }

    static {
        f("max_health", "Max health (real pool)", 5.0e9, 1, 1.0e15, 0);
        f("damage_reduction", "Damage resist (0-0.99)", 0.60, 0, 0.99, 0);
        f("resist_reduction", "Resist in Resist&Destroy", 0.30, 0, 1, 0);
        f("max_hit_fraction", "Max single hit (of HP)", 0.004, 0.0001, 1, 0);
        f("burst_loss_per_tick", "Max HP loss per tick", 0.01, 0.0001, 1, 0);
        f("max_hp_resist", "Max HP Resist stat (0-1)", 0.98, 0, 1, 0);
        f("max_hp_damage", "Max HP Damage stat (x)", 1.5, 0, 100, 0);
        f("armor", "Armor", 0, 0, 30, 0);
        f("armor_toughness", "Armor toughness", 0, 0, 20, 0);
        f("fight_mobs", "Fight other mobs (0/1)", 1, 0, 1, 0);
        f("phase2_at", "Phase 2 at health", 0.66, 0, 1, 0);
        f("phase3_at", "Phase 3 at health", 0.33, 0, 1, 0);

        f("hatred_gain", "Hatred gain multiplier", 3, 0, 100, 1);
        f("hatred_passive", "Hatred passive / tick", 0.025, 0, 5, 1);
        f("damage_scale", "Global damage scale", 1, 0, 100, 1);
        f("cast_down_flat", "Cast Down flat", 2.0e6, 0, 1.0e12, 1);
        f("cast_down_pct", "Cast Down % of HP", 0.35, 0, 1, 1);
        f("weave_flat", "Weave flat", 1.5e6, 0, 1.0e12, 1);
        f("weave_pct", "Weave % of HP", 0.20, 0, 1, 1);
        f("yield_flat", "Yield flat", 2.0e6, 0, 1.0e12, 1);
        f("yield_pct", "Yield % of HP", 0.30, 0, 1, 1);
        f("mire_flat", "Mire flat", 1.5e6, 0, 1.0e12, 1);
        f("mire_pct", "Mire % of HP", 0.25, 0, 1, 1);

        f("crown_flat", "Crown flat", 5.0e6, 0, 1.0e12, 2);
        f("crown_pct", "Crown % of HP", 0.60, 0, 1, 2);
        f("prison_flat", "Prison flat", 1.0e6, 0, 1.0e12, 2);
        f("prison_pct", "Prison % of HP", 0.20, 0, 1, 2);
        f("immolate_flat", "Immolate flat", 1.0e7, 0, 1.0e12, 2);
        f("immolate_pct", "Immolate % of HP", 0.85, 0, 1, 2);
        f("logos_bolt_flat", "Logos bolt flat", 1.0e6, 0, 1.0e12, 2);
        f("logos_bolt_pct", "Logos bolt % of HP", 0.25, 0, 1, 2);
        f("mythos_bolt_flat", "Mythos bolt flat", 1.0e6, 0, 1.0e12, 2);
        f("mythos_bolt_pct", "Mythos bolt % of HP", 0.20, 0, 1, 2);
        f("genesis_flat", "Genesis flat (phase 1 special)", 2.0e7, 0, 1.0e12, 4);
        f("genesis_pct", "Genesis % of HP", 0.75, 0, 1, 4);
        f("genesis_forced_kill", "Genesis kills outright (0/1)", 0, 0, 1, 4);
        f("singularity_flat", "Singularity flat (phase 2 special)", 5.0e7, 0, 1.0e12, 4);
        f("singularity_pct", "Singularity % of HP", 0.90, 0, 1, 4);
        f("erasure_kill", "Erasure kills everything (0/1)", 1, 0, 1, 4);
        f("erasure_kills", "Erasure: number of kill commands", 10, 1, 100, 4);
        f("erasure_kills_boss", "Erasure also kills Irontomb (0/1)", 0, 0, 1, 4);
        f("erasure_countdown", "Erasure countdown (ticks)", 1200, 20, 72000, 4);
        f("erasure_grave_ticks", "Grave Wound ticks during countdown", 100, 1, 24000, 4);
        f("erasure_satellite_mult", "Satellite HP x during countdown", 3, 0.1, 1000, 4);
        f("erasure_reset_hatred", "Hatred left after the seal breaks", 25, 0, 100, 4);

        f("phainon_max_health", "Phainon max HP (real)", 2.0e9, 1, 1.0e18, 5);
        f("phainon_damage_reduction", "Phainon damage reduction", 0.5, 0, 0.99, 5);
        f("phainon_max_hit_fraction", "Phainon max single hit (% of pool)", 0.005, 0.0001, 1, 5);
        f("phainon_burst_loss_per_tick", "Phainon max loss per tick", 0.012, 0.0001, 1, 5);
        f("phainon_max_hp_resist", "Phainon max HP resistance", 0.95, 0, 1, 5);
        f("phainon_max_hp_damage", "Phainon max HP damage stat", 1.25, 0, 100, 5);
        f("phainon_damage_scale", "Phainon damage multiplier", 1, 0, 1000, 5);
        f("phainon_phase2_at", "Phainon phase 2 at HP", 0.66, 0, 1, 5);
        f("phainon_phase3_at", "Phainon phase 3 at HP", 0.33, 0, 1, 5);
        f("phainon_required", "Irontomb needs Phainon slain (0/1)", 1, 0, 1, 5);
        f("phainon_lance_flat", "Dawn Lance flat", 1.0e6, 0, 1.0e12, 5);
        f("phainon_lance_pct", "Dawn Lance % of HP", 0.25, 0, 1, 5);
        f("phainon_dash_flat", "Daybreak Rush flat (per slash)", 5.0e5, 0, 1.0e12, 6);
        f("phainon_dash_pct", "Daybreak Rush % of HP", 0.12, 0, 1, 6);
        f("phainon_feather_flat", "Feather Rain flat (per feather)", 4.0e5, 0, 1.0e12, 6);
        f("phainon_feather_pct", "Feather Rain % of HP", 0.10, 0, 1, 6);
        f("phainon_sunfall_flat", "Sunfall flat", 3.0e6, 0, 1.0e12, 6);
        f("phainon_sunfall_pct", "Sunfall % of HP", 0.55, 0, 1, 6);
        f("phainon_halo_flat", "Halo rings flat", 1.2e6, 0, 1.0e12, 6);
        f("phainon_halo_pct", "Halo rings % of HP", 0.18, 0, 1, 6);
        f("phainon_sweep_flat", "Aureate Sweep flat (per hit)", 7.0e5, 0, 1.0e12, 6);
        f("phainon_sweep_pct", "Aureate Sweep % of HP", 0.15, 0, 1, 6);
        f("phainon_judgment_flat", "Judgment flat (phase 3)", 6.0e6, 0, 1.0e12, 6);
        f("phainon_judgment_pct", "Judgment % of HP", 0.70, 0, 1, 6);

        f("satellite_health_p1", "Satellite HP, phase 1", 1.0e8, 1, 1.0e15, 3);
        f("satellite_health_p2", "Satellite HP, phase 2+", 2.5e8, 1, 1.0e15, 3);
        f("grave_ticks", "Grave Wound ticks", 200, 1, 24000, 3);
        f("grave_hatred_relief", "Hatred relief (Grave)", 8, 0, 100, 3);
        f("grave_mana_relief", "Mana relief (Grave)", 60, 0, 100000, 3);
        f("mana_drain_yield", "Mana drain: Yield", 80, 0, 100000, 3);
        f("mana_drain_crown", "Mana drain: Crown", 120, 0, 100000, 3);
        f("mana_drain_immolate", "Mana drain: Immolate", 150, 0, 100000, 3);
        f("erudition_max", "Max HP lost at full hatred", 0.55, 0, 0.95, 3);
    }

    private IrontombConfigSpec() {
    }
}
