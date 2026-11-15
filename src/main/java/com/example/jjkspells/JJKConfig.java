package com.example.jjkspells;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common config: config/jjk_spells-common.toml */
public class JJKConfig {
    public static final ForgeConfigSpec SPEC;
    /** Master switch: when false, none of the spells in this mod change any blocks. */
    public static final ForgeConfigSpec.BooleanValue SPELLS_BREAK_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue HOLLOW_PURPLE_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue FUGA_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue NINE_ROPES_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.IntValue NINE_ROPES_CRATER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue WORLD_CUTTING_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue CRIMSON_WRATH_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue TIME_STOP_FREEZES_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue TIME_STOP_FREEZES_DAYTIME;
    public static final ForgeConfigSpec.BooleanValue TENGAI_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.IntValue TENGAI_METEOR_CRATER_RADIUS;
    public static final ForgeConfigSpec.IntValue TENGAI_NUKE_CRATER_RADIUS;
    public static final ForgeConfigSpec.DoubleValue TENGAI_METEOR_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue TENGAI_NUKE_HEALTH_PERCENT;
    public static final ForgeConfigSpec.BooleanValue CRIMSON_RAY_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue CRIMSON_RAY_PERCENT_PER_TICK;
    public static final ForgeConfigSpec.IntValue DEATH_WRAITH_COUNT;
    public static final ForgeConfigSpec.DoubleValue DEATH_WRAITH_PERCENT;
    public static final ForgeConfigSpec.DoubleValue SUN_DAMAGE_SCALE;
    public static final ForgeConfigSpec.DoubleValue SUN_ERUPTION_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue SUN_SETS_FIRE;

    /** True when the master switch AND the spell's own switch both allow block breaking. */
    public static boolean breaks(ForgeConfigSpec.BooleanValue own) {
        return SPELLS_BREAK_BLOCKS.get() && own.get();
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        SPELLS_BREAK_BLOCKS = b
                .comment("Master switch (OFF by default: set to true to allow block breaking). If false, NO spell in this mod breaks or changes blocks (Fuga, Hollow Purple, Nine Ropes,",
                        "World Cutting Slash, Crimson Wrath), whatever their own settings below say. Damage and effects are unaffected.")
                .define("allow_block_breaking", false);
        b.pop();
        b.push("hollow_purple");
        HOLLOW_PURPLE_BREAKS_BLOCKS = b
                .comment("If true, Hollow Purple erases every block along its path. If false it stops at the first block it meets.")
                .define("breaks_blocks", false);
        b.pop();
        b.push("fuga");
        FUGA_BREAKS_BLOCKS = b
                .comment("If true, Fuga's explosion destroys terrain.")
                .define("breaks_blocks", false);
        b.pop();
        b.push("hollow_purple_nine_ropes");
        NINE_ROPES_BREAKS_BLOCKS = b
                .comment("If true, the Nine Ropes Hollow Purple explosion destroys terrain.")
                .define("breaks_blocks", false);
        NINE_ROPES_CRATER_RADIUS = b
                .comment("Radius in blocks of the crater (default 48 = 3x Fuga). Lower this if the explosion lags your game.")
                .defineInRange("crater_radius", 48, 8, 128);
        b.pop();
        b.push("world_cutting_slash");
        WORLD_CUTTING_BREAKS_BLOCKS = b
                .comment("If true, World Cutting Slash carves its grid of cuts through the terrain.")
                .define("breaks_blocks", false);
        b.pop();
        b.push("crimson_wrath");
        CRIMSON_WRATH_BREAKS_BLOCKS = b
                .comment("If true, the lasers of The Ruler of Death's Crimson Wrath drill a pit into the terrain at the centre of the circle.")
                .define("breaks_blocks", false);
        b.pop();
        b.push("time_stop");
        TIME_STOP_FREEZES_PLAYERS = b
                .comment("If false, other players are NOT frozen by Za Warudo (only mobs, projectiles and other entities).")
                .define("freezes_players", true);
        TIME_STOP_FREEZES_DAYTIME = b
                .comment("If true, the time of day also stands still while time is stopped.")
                .define("freezes_daytime", true);
        b.pop();
        b.push("tengai_shinsei");
        TENGAI_BREAKS_BLOCKS = b
                .comment("If true, the meteors and the final explosion of Tengai Shinsei crater the terrain.")
                .define("breaks_blocks", false);
        TENGAI_METEOR_CRATER_RADIUS = b
                .comment("Radius in blocks of the crater the first meteor leaves when it lands.")
                .defineInRange("meteor_crater_radius", 22, 4, 64);
        TENGAI_NUKE_CRATER_RADIUS = b
                .comment("Radius in blocks of the crater left by the final explosion. Lower this if the explosion lags your game.")
                .defineInRange("nuke_crater_radius", 36, 8, 96);
        TENGAI_METEOR_DAMAGE = b
                .comment("Damage the first meteor's landing shockwave deals to every entity it hits (the caster is spared).")
                .defineInRange("meteor_damage", 95.0, 0.0, 100000.0);
        TENGAI_NUKE_HEALTH_PERCENT = b
                .comment("Fraction of MAX health the final explosion removes from every entity it catches (0.40 = 40%).")
                .defineInRange("nuke_health_percent", 0.40, 0.0, 1.0);
        b.pop();
        b.push("crimson_ray");
        CRIMSON_RAY_BREAKS_BLOCKS = b
                .comment("If true, the Crimson Ray drills into the terrain where it lands.")
                .define("breaks_blocks", false);
        CRIMSON_RAY_PERCENT_PER_TICK = b
                .comment("Fraction of MAX health the ray removes EVERY TICK from everything it touches (0.005 = 0.5% per tick = 10% per second).")
                .defineInRange("percent_per_tick", 0.005, 0.0, 1.0);
        b.pop();
        b.push("death_wraiths");
        DEATH_WRAITH_COUNT = b
                .comment("How many wraiths the spell calls up.")
                .defineInRange("count", 16, 1, 64);
        DEATH_WRAITH_PERCENT = b
                .comment("Fraction of MAX health each wraith removes when it reaches its target (0.015 = 1.5%).")
                .defineInRange("percent_per_wraith", 0.015, 0.0, 1.0);
        b.pop();
        b.push("sun_breathing");
        SUN_DAMAGE_SCALE = b.comment("Multiplier on every Sun Breathing hit (each hit is a multiple of your held weapon's damage).").defineInRange("damage_scale", 1.0, 0.0, 100.0);
        SUN_ERUPTION_DAMAGE = b.comment("Extra flat damage of the Thirteenth Form's final solar eruption.").defineInRange("eruption_damage", 50.0, 0.0, 100000.0);
        SUN_SETS_FIRE = b.comment("If true, Sun Breathing sets what it hits on fire.").define("sets_fire", true);
        b.pop();
        CursedConfig.define(b);
        SPEC = b.build();
    }
}
