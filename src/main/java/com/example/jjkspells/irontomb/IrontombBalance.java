package com.example.jjkspells.irontomb;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every tunable number of the fight. Values come from  config/jjk_spells-irontomb.cfg  (created with defaults on first launch,
 * edit it by hand or generate it with the Irontomb configurator page) and can be re-read in game with  /irontomb reload.
 * Attack damage is  (flat + pct * victim max health) * damage_scale.
 */
public final class IrontombBalance {
    public static final String FILE = "jjk_spells-irontomb.cfg";
    /** Bumped every time the file is (re)read; live entities compare it to know when to re-apply stats. */
    public static volatile int VERSION = 0;
    private static long lastModified = -1L;

    // ---- boss stats
    /** Vanilla max_health is hard-capped at 1024, so the entity always carries NORM health points and the real pool below is converted into them. */
    public static final double NORM = 1000.0;
    /** The REAL health pool of the boss (any size, e.g. 5e9). Damage is divided by this and multiplied by NORM. */
    public static double MAX_HEALTH = 5.0e9;
    public static double ARMOR = 0.0, ARMOR_TOUGHNESS = 0.0;
    /** The mod's Max Health Damage stat on the boss (multiplies its percent attacks) and Max Health Resistance stat (0..1, cuts percent damage taken). */
    public static double MAX_HP_DAMAGE = 1.5, MAX_HP_RESIST = 0.98;
    /** Share of damage ignored while neither satellite is in Grave Wound (Thirty Million Cycles of Sin). */
    public static float BASE_REDUCTION = 0.60f;
    /** Reduction while Resist and Destroy is active (the original fight is not invulnerable then). */
    public static float RESIST_REDUCTION = 0.30f;
    /** One hit can never take more than this share of the boss' max health. */
    public static float MAX_HIT_FRACTION = 0.004f;
    /** Most health (share of the pool) the boss can lose in a single tick, whatever hit it - also stops percent-health spells that skip the hit events. */
    public static float BURST_LOSS_PER_TICK = 0.01f;
    /** Health fractions at which phase 2 and phase 3 begin. */
    public static float PHASE2_AT = 0.66f, PHASE3_AT = 0.33f;

    // ---- satellites
    public static double SATELLITE_HEALTH_P1 = 1.0e8, SATELLITE_HEALTH_P2 = 2.5e8;
    public static int GRAVE_TICKS = 200;
    public static float GRAVE_HATRED_RELIEF = 8f;
    public static float GRAVE_MANA_RELIEF = 60f;

    // ---- damage
    /** When true the boss also wakes for, targets and damages other mobs (Mob Battle fights). */
    public static boolean FIGHT_MOBS = true;
    /** Multiplier on all Amphorean Hatred gained from attacks and satellites, and the per-tick passive build-up. */
    public static float HATRED_GAIN = 3.0f, HATRED_PASSIVE = 0.025f;
    public static float DAMAGE_SCALE = 1.0f;
    // {flat, pct of victim max health}
    public static final float[] CAST_DOWN = {2.0e6f, 0.35f};
    public static final float[] WEAVE = {1.5e6f, 0.20f};
    public static final float[] YIELD = {2.0e6f, 0.30f};
    public static final float[] MIRE = {1.5e6f, 0.25f};
    public static final float[] CROWN = {5.0e6f, 0.60f};
    public static final float[] PRISON = {1.0e6f, 0.20f};
    public static final float[] IMMOLATE = {1.0e7f, 0.85f};
    public static final float[] LOGOS_BOLT = {1.0e6f, 0.25f};
    public static final float[] MYTHOS_BOLT = {1.0e6f, 0.20f};
    /** Phase 1 special (nerfed: it is now ordinary damage that defences and max HP resistance work on, no forced kill unless enabled). */
    public static final float[] GENESIS = {2.0e7f, 0.75f};
    public static boolean GENESIS_FORCED_KILL = false;
    /** Phase 2 special: hits EVERY entity in the level. */
    public static final float[] SINGULARITY = {5.0e7f, 0.90f};
    /** Phase 3 special: Erasure. The countdown (ticks) a full hatred bar gives the players to stop it; the satellites get tougher meanwhile. */
    public static int ERASURE_COUNTDOWN = 1200, ERASURE_GRAVE_TICKS = 100, ERASURE_KILLS = 10;
    public static double ERASURE_SATELLITE_MULT = 3.0;
    public static float ERASURE_RESET_HATRED = 25f;
    public static boolean ERASURE_KILL = true, ERASURE_KILLS_BOSS = false;

    // ---- Phainon (the boss that must be slain before Irontomb will wake)
    /** Phainon's real health pool; the entity itself only carries NORM units like Irontomb. */
    public static double PH_MAX_HEALTH = 2.0e9, PH_MAX_HP_DAMAGE = 1.25, PH_MAX_HP_RESIST = 0.95;
    public static float PH_REDUCTION = 0.5f, PH_MAX_HIT_FRACTION = 0.005f, PH_BURST = 0.012f, PH_DAMAGE_SCALE = 1f;
    public static float PH_PHASE2_AT = 0.66f, PH_PHASE3_AT = 0.33f;
    /** Irontomb refuses to wake (and cannot be hurt) until a player who slew Phainon is near. */
    public static boolean PH_REQUIRE = true;
    public static final float[] PH_LANCE = {1.0e6f, 0.25f};
    public static final float[] PH_DASH = {5.0e5f, 0.12f};
    public static final float[] PH_FEATHER = {4.0e5f, 0.10f};
    public static final float[] PH_SUNFALL = {3.0e6f, 0.55f};
    public static final float[] PH_HALO = {1.2e6f, 0.18f};
    public static final float[] PH_SWEEP = {7.0e5f, 0.15f};
    public static final float[] PH_JUDGMENT = {6.0e6f, 0.70f};

    // ---- mana and hatred
    public static float MANA_DRAIN_YIELD = 80f, MANA_DRAIN_CROWN = 120f, MANA_DRAIN_IMMOLATE = 150f;
    /** Max-health a full Amphorean Hatred bar takes away from every player ("The Fall of Erudition"). */
    public static float ERUDITION_MAX = 0.55f;

    private IrontombBalance() {
    }

    static {
        load();
    }

    /** Cheap check meant to be called about once a second: re-reads the file if it changed on disk. Returns a message or null. */
    public static synchronized String reloadIfChanged() {
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
            if (Files.exists(p) && Files.getLastModifiedTime(p).toMillis() != lastModified) {
                return load();
            }
        } catch (IOException ex) {
            // ignore, try again next time
        }
        return null;
    }

    /** Snapshot of every current value, for the configurator screen. */
    public static synchronized Map<String, Double> values() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : table().entrySet()) {
            out.put(e.getKey(), ((Number) e.getValue()).doubleValue());
        }
        return out;
    }

    /** Applies the given values immediately, saves them to the config file and tells live bosses to re-read. */
    public static synchronized void setValues(Map<String, Double> v) {
        apply(v);
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
            write(p);
            lastModified = Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ex) {
            // still applied in memory
        }
        VERSION++;
    }

    private static Map<String, Object> table() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("max_health", MAX_HEALTH);
        m.put("armor", ARMOR);
        m.put("armor_toughness", ARMOR_TOUGHNESS);
        m.put("max_hp_damage", MAX_HP_DAMAGE);
        m.put("max_hp_resist", MAX_HP_RESIST);
        m.put("damage_reduction", BASE_REDUCTION);
        m.put("resist_reduction", RESIST_REDUCTION);
        m.put("max_hit_fraction", MAX_HIT_FRACTION);
        m.put("burst_loss_per_tick", BURST_LOSS_PER_TICK);
        m.put("phase2_at", PHASE2_AT);
        m.put("phase3_at", PHASE3_AT);
        m.put("satellite_health_p1", SATELLITE_HEALTH_P1);
        m.put("satellite_health_p2", SATELLITE_HEALTH_P2);
        m.put("grave_ticks", GRAVE_TICKS);
        m.put("grave_hatred_relief", GRAVE_HATRED_RELIEF);
        m.put("grave_mana_relief", GRAVE_MANA_RELIEF);
        m.put("fight_mobs", FIGHT_MOBS ? 1.0 : 0.0);
        m.put("hatred_gain", HATRED_GAIN);
        m.put("hatred_passive", HATRED_PASSIVE);
        m.put("damage_scale", DAMAGE_SCALE);
        String[] names = {"cast_down", "weave", "yield", "mire", "crown", "prison", "immolate", "logos_bolt", "mythos_bolt", "genesis", "singularity"};
        float[][] arrs = {CAST_DOWN, WEAVE, YIELD, MIRE, CROWN, PRISON, IMMOLATE, LOGOS_BOLT, MYTHOS_BOLT, GENESIS, SINGULARITY};
        for (int i = 0; i < names.length; i++) {
            m.put(names[i] + "_flat", arrs[i][0]);
            m.put(names[i] + "_pct", arrs[i][1]);
        }
        m.put("genesis_forced_kill", GENESIS_FORCED_KILL ? 1.0 : 0.0);
        m.put("erasure_countdown", ERASURE_COUNTDOWN);
        m.put("erasure_grave_ticks", ERASURE_GRAVE_TICKS);
        m.put("erasure_satellite_mult", ERASURE_SATELLITE_MULT);
        m.put("erasure_reset_hatred", ERASURE_RESET_HATRED);
        m.put("erasure_kill", ERASURE_KILL ? 1.0 : 0.0);
        m.put("erasure_kills", ERASURE_KILLS);
        m.put("erasure_kills_boss", ERASURE_KILLS_BOSS ? 1.0 : 0.0);
        m.put("phainon_max_health", PH_MAX_HEALTH);
        m.put("phainon_damage_reduction", PH_REDUCTION);
        m.put("phainon_max_hit_fraction", PH_MAX_HIT_FRACTION);
        m.put("phainon_burst_loss_per_tick", PH_BURST);
        m.put("phainon_max_hp_resist", PH_MAX_HP_RESIST);
        m.put("phainon_max_hp_damage", PH_MAX_HP_DAMAGE);
        m.put("phainon_damage_scale", PH_DAMAGE_SCALE);
        m.put("phainon_phase2_at", PH_PHASE2_AT);
        m.put("phainon_phase3_at", PH_PHASE3_AT);
        m.put("phainon_required", PH_REQUIRE ? 1.0 : 0.0);
        String[] phn = {"phainon_lance", "phainon_dash", "phainon_feather", "phainon_sunfall", "phainon_halo", "phainon_sweep", "phainon_judgment"};
        float[][] pha = {PH_LANCE, PH_DASH, PH_FEATHER, PH_SUNFALL, PH_HALO, PH_SWEEP, PH_JUDGMENT};
        for (int i = 0; i < phn.length; i++) {
            m.put(phn[i] + "_flat", pha[i][0]);
            m.put(phn[i] + "_pct", pha[i][1]);
        }
        m.put("mana_drain_yield", MANA_DRAIN_YIELD);
        m.put("mana_drain_crown", MANA_DRAIN_CROWN);
        m.put("mana_drain_immolate", MANA_DRAIN_IMMOLATE);
        m.put("erudition_max", ERUDITION_MAX);
        return m;
    }

    private static void apply(Map<String, Double> v) {
        MAX_HEALTH = Math.max(1.0, v.getOrDefault("max_health", MAX_HEALTH));
        ARMOR = Math.max(0.0, v.getOrDefault("armor", ARMOR));
        ARMOR_TOUGHNESS = Math.max(0.0, v.getOrDefault("armor_toughness", ARMOR_TOUGHNESS));
        MAX_HP_DAMAGE = Math.max(0.0, Math.min(100.0, v.getOrDefault("max_hp_damage", MAX_HP_DAMAGE)));
        MAX_HP_RESIST = Math.max(0.0, Math.min(1.0, v.getOrDefault("max_hp_resist", MAX_HP_RESIST)));
        BASE_REDUCTION = clamp(v.getOrDefault("damage_reduction", (double) BASE_REDUCTION), 0, 0.99);
        RESIST_REDUCTION = clamp(v.getOrDefault("resist_reduction", (double) RESIST_REDUCTION), 0, 1);
        BURST_LOSS_PER_TICK = clamp(v.getOrDefault("burst_loss_per_tick", (double) BURST_LOSS_PER_TICK), 0.0001, 1);
        MAX_HIT_FRACTION = clamp(v.getOrDefault("max_hit_fraction", (double) MAX_HIT_FRACTION), 0.0001, 1);
        PHASE2_AT = clamp(v.getOrDefault("phase2_at", (double) PHASE2_AT), 0, 1);
        PHASE3_AT = clamp(v.getOrDefault("phase3_at", (double) PHASE3_AT), 0, 1);
        SATELLITE_HEALTH_P1 = Math.max(1.0, v.getOrDefault("satellite_health_p1", SATELLITE_HEALTH_P1));
        SATELLITE_HEALTH_P2 = Math.max(1.0, v.getOrDefault("satellite_health_p2", SATELLITE_HEALTH_P2));
        GRAVE_TICKS = (int) Math.max(1, v.getOrDefault("grave_ticks", (double) GRAVE_TICKS));
        GRAVE_HATRED_RELIEF = v.getOrDefault("grave_hatred_relief", (double) GRAVE_HATRED_RELIEF).floatValue();
        GRAVE_MANA_RELIEF = v.getOrDefault("grave_mana_relief", (double) GRAVE_MANA_RELIEF).floatValue();
        FIGHT_MOBS = v.getOrDefault("fight_mobs", FIGHT_MOBS ? 1.0 : 0.0) >= 0.5;
        HATRED_GAIN = clamp(v.getOrDefault("hatred_gain", (double) HATRED_GAIN), 0, 100);
        HATRED_PASSIVE = clamp(v.getOrDefault("hatred_passive", (double) HATRED_PASSIVE), 0, 5);
        DAMAGE_SCALE = (float) Math.max(0.0, v.getOrDefault("damage_scale", (double) DAMAGE_SCALE));
        String[] names = {"cast_down", "weave", "yield", "mire", "crown", "prison", "immolate", "logos_bolt", "mythos_bolt", "genesis", "singularity"};
        float[][] arrs = {CAST_DOWN, WEAVE, YIELD, MIRE, CROWN, PRISON, IMMOLATE, LOGOS_BOLT, MYTHOS_BOLT, GENESIS, SINGULARITY};
        for (int i = 0; i < names.length; i++) {
            arrs[i][0] = (float) Math.max(0.0, v.getOrDefault(names[i] + "_flat", (double) arrs[i][0]));
            arrs[i][1] = (float) Math.max(0.0, v.getOrDefault(names[i] + "_pct", (double) arrs[i][1]));
        }
        GENESIS_FORCED_KILL = v.getOrDefault("genesis_forced_kill", GENESIS_FORCED_KILL ? 1.0 : 0.0) >= 0.5;
        ERASURE_COUNTDOWN = (int) Math.max(20, v.getOrDefault("erasure_countdown", (double) ERASURE_COUNTDOWN));
        ERASURE_GRAVE_TICKS = (int) Math.max(1, v.getOrDefault("erasure_grave_ticks", (double) ERASURE_GRAVE_TICKS));
        ERASURE_SATELLITE_MULT = Math.max(0.1, v.getOrDefault("erasure_satellite_mult", ERASURE_SATELLITE_MULT));
        ERASURE_RESET_HATRED = clamp(v.getOrDefault("erasure_reset_hatred", (double) ERASURE_RESET_HATRED), 0, 100);
        ERASURE_KILL = v.getOrDefault("erasure_kill", ERASURE_KILL ? 1.0 : 0.0) >= 0.5;
        ERASURE_KILLS = (int) Math.max(1, Math.min(100, v.getOrDefault("erasure_kills", (double) ERASURE_KILLS)));
        ERASURE_KILLS_BOSS = v.getOrDefault("erasure_kills_boss", ERASURE_KILLS_BOSS ? 1.0 : 0.0) >= 0.5;
        PH_MAX_HEALTH = Math.max(1.0, v.getOrDefault("phainon_max_health", PH_MAX_HEALTH));
        PH_REDUCTION = clamp(v.getOrDefault("phainon_damage_reduction", (double) PH_REDUCTION), 0, 0.99);
        PH_MAX_HIT_FRACTION = clamp(v.getOrDefault("phainon_max_hit_fraction", (double) PH_MAX_HIT_FRACTION), 0.0001, 1);
        PH_BURST = clamp(v.getOrDefault("phainon_burst_loss_per_tick", (double) PH_BURST), 0.0001, 1);
        PH_MAX_HP_RESIST = Math.max(0.0, Math.min(1.0, v.getOrDefault("phainon_max_hp_resist", PH_MAX_HP_RESIST)));
        PH_MAX_HP_DAMAGE = Math.max(0.0, Math.min(100.0, v.getOrDefault("phainon_max_hp_damage", PH_MAX_HP_DAMAGE)));
        PH_DAMAGE_SCALE = (float) Math.max(0.0, v.getOrDefault("phainon_damage_scale", (double) PH_DAMAGE_SCALE));
        PH_PHASE2_AT = clamp(v.getOrDefault("phainon_phase2_at", (double) PH_PHASE2_AT), 0, 1);
        PH_PHASE3_AT = clamp(v.getOrDefault("phainon_phase3_at", (double) PH_PHASE3_AT), 0, 1);
        PH_REQUIRE = v.getOrDefault("phainon_required", PH_REQUIRE ? 1.0 : 0.0) >= 0.5;
        String[] phn = {"phainon_lance", "phainon_dash", "phainon_feather", "phainon_sunfall", "phainon_halo", "phainon_sweep", "phainon_judgment"};
        float[][] pha = {PH_LANCE, PH_DASH, PH_FEATHER, PH_SUNFALL, PH_HALO, PH_SWEEP, PH_JUDGMENT};
        for (int i = 0; i < phn.length; i++) {
            pha[i][0] = (float) Math.max(0.0, v.getOrDefault(phn[i] + "_flat", (double) pha[i][0]));
            pha[i][1] = (float) Math.max(0.0, v.getOrDefault(phn[i] + "_pct", (double) pha[i][1]));
        }
        MANA_DRAIN_YIELD = (float) Math.max(0.0, v.getOrDefault("mana_drain_yield", (double) MANA_DRAIN_YIELD));
        MANA_DRAIN_CROWN = (float) Math.max(0.0, v.getOrDefault("mana_drain_crown", (double) MANA_DRAIN_CROWN));
        MANA_DRAIN_IMMOLATE = (float) Math.max(0.0, v.getOrDefault("mana_drain_immolate", (double) MANA_DRAIN_IMMOLATE));
        ERUDITION_MAX = clamp(v.getOrDefault("erudition_max", (double) ERUDITION_MAX), 0, 0.95);
    }

    private static float clamp(double v, double lo, double hi) {
        return (float) Math.max(lo, Math.min(hi, v));
    }

    /** Reads the config file (writing the defaults first if there is none). Returns a short status message. */
    public static synchronized String load() {
        Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
        try {
            if (!Files.exists(p)) {
                write(p);
                lastModified = Files.getLastModifiedTime(p).toMillis();
                VERSION++;
                return "Wrote default " + FILE;
            }
            lastModified = Files.getLastModifiedTime(p).toMillis();
            Map<String, Double> v = new LinkedHashMap<>();
            int bad = 0;
            for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String line = raw;
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                try {
                    v.put(line.substring(0, eq).trim().toLowerCase(), Double.parseDouble(line.substring(eq + 1).trim()));
                } catch (NumberFormatException ex) {
                    bad++;
                }
            }
            apply(v);
            VERSION++;
            return "Loaded " + v.size() + " values from " + FILE + (bad > 0 ? " (" + bad + " unreadable lines ignored)" : "");
        } catch (IOException | RuntimeException ex) {
            return "Could not read " + FILE + ": " + ex.getMessage() + " (using defaults)";
        }
    }

    private static void write(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Irontomb configuration. One  key = value  per line. Lines starting with # are ignored.\n");
        sb.append("# Attack damage = (flat + pct * victim max health) * damage_scale.  pct 0.20 means 20% of the victim's max health.\n");
        sb.append("# Changes are picked up automatically within about a second (or run /irontomb reload). Max health, armor and damage apply to a boss that is already fighting.\n\n");
        for (Map.Entry<String, Object> e : table().entrySet()) {
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        Files.createDirectories(p.getParent());
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
    }
}
