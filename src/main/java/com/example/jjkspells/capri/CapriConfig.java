package com.example.jjkspells.capri;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Server rules (jjk_spells-capri.toml) and the player's own client settings (jjk_spells-capri-client.toml). */
public final class CapriConfig {
    public static final ForgeConfigSpec SERVER_SPEC, CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, ALLOW_ATTRIBUTES, ALLOW_AUTOCAST;
    public static final ForgeConfigSpec.IntValue MAX_SPELLS, CAST_INTERVAL, MIN_ATTR_PERCENT, CAST_WARNING_TICKS;
    public static final ForgeConfigSpec.DoubleValue AUTOCAST_RANGE;

    public static final ForgeConfigSpec.DoubleValue VOICE_VOLUME, HUD_OPACITY, AVATAR_SCALE, LOCK_RANGE, LOCK_TRACKING, LOCK_LOS_SECONDS;
    public static final ForgeConfigSpec.IntValue LOCK_BREAK, ONS_RANGE, ONS_CIRCLE, CHATTER_MINUTES, VERBOSITY, AVATAR_CORNER, DIAG_FIELDS;
    public static final ForgeConfigSpec.BooleanValue SUBTITLES, VOICE, AVATAR, AVATAR_IDLE, AVATAR_ANIM, REDUCED_MOTION, REDUCED_FLASH, IDLE_CHATTER, ANNOUNCE, GREETING, DIAGNOSTICS,
            LOCK_ENABLED, KEEP_RUNNING_UNFOCUSED, BOOT_ANIM;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> INPUT_ROWS;

    static {
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        s.push("capri");
        ENABLED = s.comment("Master switch: Capri works for players wearing her in an Absolute Relic slot").define("enabled", true);
        ALLOW_AUTOCAST = s.define("allow_auto_cast", true);
        ALLOW_ATTRIBUTES = s.comment("Allow the stat governor (it can only ever LOWER a stat)").define("allow_stat_governor", true);
        MAX_SPELLS = s.comment("Spells that can be authorized for auto-cast (the design is 3)").defineInRange("max_auto_spells", 3, 1, 3);
        CAST_INTERVAL = s.comment("Minimum ticks between two auto-casts").defineInRange("cast_interval_ticks", 20, 5, 200);
        CAST_WARNING_TICKS = s.comment("Ticks between the announcement of an auto-cast and the cast").defineInRange("cast_warning_ticks", 10, 0, 60);
        AUTOCAST_RANGE = s.comment("How far away a target may be for an auto-cast").defineInRange("auto_cast_range", 40.0, 4.0, 128.0);
        MIN_ATTR_PERCENT = s.comment("The lowest a stat can be set to, in percent of its real value").defineInRange("min_stat_percent", 10, 1, 100);
        s.pop();
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("voice");
        VOICE = c.define("voice", true);
        VOICE_VOLUME = c.defineInRange("volume", 0.8, 0.0, 1.0);
        SUBTITLES = c.define("subtitles", true);
        ANNOUNCE = c.comment("Automatic spoken announcements (locks, thresholds, casts)").define("announcements", true);
        VERBOSITY = c.comment("0 = critical only, 1 = concise, 2 = verbose").defineInRange("verbosity", 1, 0, 2);
        IDLE_CHATTER = c.define("idle_chatter", true);
        CHATTER_MINUTES = c.comment("Average minutes between idle conversations (3..8 suggested)").defineInRange("chatter_minutes", 5, 1, 30);
        GREETING = c.define("greeting", true);
        c.pop();
        c.push("avatar");
        AVATAR = c.comment("Capri may appear on screen as a 2D hologram").define("avatar", true);
        AVATAR_IDLE = c.define("avatar_idle_appearances", true);
        AVATAR_ANIM = c.define("avatar_animation", true);
        AVATAR_SCALE = c.defineInRange("avatar_scale", 1.0, 0.5, 2.0);
        AVATAR_CORNER = c.comment("0 bottom-left, 1 bottom-right, 2 top-left, 3 top-right").defineInRange("avatar_corner", 0, 0, 3);
        c.pop();
        c.push("hud");
        HUD_OPACITY = c.defineInRange("opacity", 0.85, 0.2, 1.0);
        REDUCED_MOTION = c.define("reduced_motion", false);
        REDUCED_FLASH = c.define("reduced_flashing", false);
        BOOT_ANIM = c.define("boot_animation", true);
        DIAGNOSTICS = c.define("target_diagnostics", true);
        DIAG_FIELDS = c.comment("Bit mask: 1 hp, 2 armor, 4 effects, 8 aggro, 16 distance").defineInRange("diagnostic_fields", 31, 0, 31);
        c.pop();
        c.push("targeting");
        LOCK_ENABLED = c.define("lock_enabled", true);
        LOCK_RANGE = c.comment("How far away you can START a lock").defineInRange("lock_range", 48.0, 8.0, 300.0);
        LOCK_TRACKING = c.comment("How strongly the camera follows the locked target, 0 = only the brackets").defineInRange("tracking_strength", 0.5, 0.0, 1.0);
        LOCK_BREAK = c.comment("The lock drops by itself when the target is this many blocks away (10..300)").defineInRange("break_distance", 90, 10, 300);
        ONS_RANGE = c.comment("Onslaught Mode: how far it detects and locks enemies (3..75 blocks)").defineInRange("onslaught_range", 10, 3, 75);
        ONS_CIRCLE = c.comment("Onslaught Mode, strategic: distance kept while circling the target (2..25 blocks)").defineInRange("onslaught_circle", 5, 2, 25);
        LOCK_LOS_SECONDS = c.comment("Seconds without line of sight before the lock breaks").defineInRange("lost_sight_seconds", 3.0, 0.5, 20.0);
        c.pop();
        c.push("input");
        KEEP_RUNNING_UNFOCUSED = c.comment("Do not pause when the window loses focus while an automation is running").define("keep_running_when_unfocused", false);
        INPUT_ROWS = c.comment("Automation rows: action|mode|rate|enabled").defineList("rows", List.of("attack|REPEAT|4|false", "use|HOLD|4|false", "jump|HOLD|4|false"), o -> o instanceof String);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    /** A lock always breaks beyond this many blocks (server and client both enforce it). */
    public static final double LOCK_BREAK_DISTANCE = 90.0;

    private CapriConfig() {
    }

    public static boolean serverEnabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int maxSpells() {
        try {
            return MAX_SPELLS.get();
        } catch (IllegalStateException e) {
            return 3;
        }
    }

    public static int minAttr() {
        try {
            return MIN_ATTR_PERCENT.get();
        } catch (IllegalStateException e) {
            return 10;
        }
    }
}
