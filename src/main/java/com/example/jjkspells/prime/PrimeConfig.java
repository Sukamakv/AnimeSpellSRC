package com.example.jjkspells.prime;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/jjk_spells-prime.toml: every number of the PRIME spells. Block breaking also needs allow_block_breaking in the main config. */
public final class PrimeConfig {
    public static final ForgeConfigSpec SPEC;

    // Void Blue
    public static final ForgeConfigSpec.DoubleValue VB_DISTANCE, VB_RADIUS, VB_PULL_RANGE, VB_PULL_STRENGTH, VB_MAX_SPEED, VB_FLAT, VB_PERCENT;
    public static final ForgeConfigSpec.IntValue VB_SECONDS;
    // Heavenly Red
    public static final ForgeConfigSpec.DoubleValue HR_RANGE, HR_HALF_ANGLE, HR_FLAT, HR_PERCENT, HR_KNOCKBACK;
    public static final ForgeConfigSpec.BooleanValue HR_BREAKS;
    // Divine Purple
    public static final ForgeConfigSpec.DoubleValue DP_RADIUS, DP_RANGE, DP_SPEED, DP_PERCENT;
    public static final ForgeConfigSpec.IntValue DP_SLOW_SECONDS;
    public static final ForgeConfigSpec.BooleanValue DP_BREAKS;
    // Heavenly Onslaught
    public static final ForgeConfigSpec.DoubleValue HO_RADIUS, HO_FLAT, HO_PERCENT, HO_MAXHP_LOSS, HO_BLAST_PERCENT;
    public static final ForgeConfigSpec.IntValue HO_SECONDS, HO_RAMPAGE_START, HO_MAXHP_SECONDS, HO_BLAST_RADIUS, HO_MAX_TARGETS;
    public static final ForgeConfigSpec.BooleanValue HO_IMPALE_REPEATS, HO_BREAKS;

    // Boundless Slash
    public static final ForgeConfigSpec.DoubleValue BS_RANGE, BS_SPEED, BS_HEIGHT, BS_FLAT, BS_PERCENT;
    public static final ForgeConfigSpec.IntValue BS_DISMANTLE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue BS_BREAKS;
    // World Disintegration
    public static final ForgeConfigSpec.DoubleValue WD_CHARGE_SECONDS, WD_RANGE, WD_RADIUS, WD_FLAT, WD_PERCENT, WD_REACH_UP;
    public static final ForgeConfigSpec.IntValue WD_BURN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue WD_BREAKS;
    // Universe Severing Slash
    public static final ForgeConfigSpec.DoubleValue US_SPEED, US_HOLD_SECONDS, US_FLAT, US_PERCENT, US_PROJ_FLAT, US_PROJ_PERCENT, US_PROJ_SPEED;
    public static final ForgeConfigSpec.IntValue US_RANGE, US_HALF_WIDTH, US_PROJECTILES, US_PROJ_MAX_HITS, US_DISMANTLE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue US_BREAKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("PRIME Gojo: Void Blue (Lapse Blue evolved)").push("void_blue");
        VB_DISTANCE = b.comment("How far in front of the caster's aim the void floats (blocks)").defineInRange("distance", 8.0, 2.0, 24.0);
        VB_RADIUS = b.comment("Radius of the black core").defineInRange("radius", 3.4, 1.0, 12.0);
        VB_PULL_RANGE = b.comment("Everything within this many blocks is dragged in").defineInRange("pull_range", 32.0, 6.0, 96.0);
        VB_PULL_STRENGTH = b.comment("Pull acceleration per tick (Lapse Blue is 0.14)").defineInRange("pull_strength", 0.55, 0.05, 3.0);
        VB_MAX_SPEED = b.comment("Fastest a victim is dragged (blocks per tick)").defineInRange("max_speed", 2.4, 0.3, 6.0);
        VB_FLAT = b.comment("Crush damage at the core, every 4 ticks").defineInRange("damage", 6.0, 0.0, 10000.0);
        VB_PERCENT = b.comment("Extra crush damage as a fraction of max health, every 4 ticks (0.015 = 1.5%)").defineInRange("damage_percent", 0.015, 0.0, 1.0);
        VB_SECONDS = b.comment("How long the void lasts").defineInRange("seconds", 10, 2, 60);
        b.pop();

        b.comment("PRIME Gojo: Heavenly Red (Reversal Red evolved)").push("heavenly_red");
        HR_RANGE = b.comment("Length of the blast in blocks").defineInRange("range", 64.0, 16.0, 160.0);
        HR_HALF_ANGLE = b.comment("Half angle of the blast cone in degrees").defineInRange("half_angle", 32.0, 8.0, 70.0);
        HR_FLAT = b.comment("Flat damage").defineInRange("damage", 200.0, 0.0, 100000.0);
        HR_PERCENT = b.comment("Extra damage as a fraction of max health (0.10 = 10%)").defineInRange("damage_percent", 0.10, 0.0, 1.0);
        HR_KNOCKBACK = b.comment("How hard everything is thrown").defineInRange("knockback", 4.5, 0.0, 20.0);
        HR_BREAKS = b.comment("Erase the terrain inside the blast (needs allow_block_breaking in jjk_spells-common.toml too)").define("breaks_blocks", true);
        b.pop();

        b.comment("PRIME Gojo: Divine Purple (Hollow Purple evolved)").push("divine_purple");
        DP_RADIUS = b.comment("Radius of the beam in blocks").defineInRange("radius", 5.5, 1.0, 16.0);
        DP_RANGE = b.comment("How far it reaches").defineInRange("range", 140.0, 20.0, 400.0);
        DP_SPEED = b.comment("Blocks per tick (7 = 140 blocks per second, it reads as a laser)").defineInRange("speed", 7.0, 1.0, 20.0);
        DP_PERCENT = b.comment("Damage as a fraction of max health (0.40 = 40%)").defineInRange("damage_percent", 0.40, 0.0, 1.0);
        DP_SLOW_SECONDS = b.comment("Everything standing in its path gets Slowness VI for this long").defineInRange("slow_seconds", 4, 1, 30);
        DP_BREAKS = b.comment("Erase the blocks along the path (needs allow_block_breaking too)").define("breaks_blocks", true);
        b.pop();

        b.comment("PRIME Gojo: Heavenly Onslaught (Unlimited Void + Hollow Purple: Nine Ropes combined)").push("heavenly_onslaught");
        HO_RADIUS = b.comment("Radius of the domain (Unlimited Void is 10 to 22.5)").defineInRange("radius", 36.0, 12.0, 90.0);
        HO_SECONDS = b.comment("How long the domain stands before the final explosion").defineInRange("seconds", 12, 8, 40);
        HO_RAMPAGE_START = b.comment("Second of the domain at which the caster starts charging at everyone").defineInRange("rampage_start_seconds", 7, 2, 30);
        HO_MAX_TARGETS = b.comment("Most creatures the rampage visits (the explosion still hits the rest)").defineInRange("max_targets", 40, 1, 200);
        HO_FLAT = b.comment("Flat damage of the rampage").defineInRange("damage", 200.0, 0.0, 100000.0);
        HO_PERCENT = b.comment("Extra rampage damage as a fraction of max health (0.15 = 15%)").defineInRange("damage_percent", 0.15, 0.0, 1.0);
        HO_IMPALE_REPEATS = b.comment("A creature that survives having its head torn off is impaled through the chest: true = that hit deals the damage again").define("impale_repeats_damage", true);
        HO_MAXHP_LOSS = b.comment("Survivors lose this fraction of their max health").defineInRange("max_health_loss", 0.15, 0.0, 0.9);
        HO_MAXHP_SECONDS = b.comment("...for this long").defineInRange("max_health_loss_seconds", 16, 1, 600);
        HO_BLAST_RADIUS = b.comment("Radius of the final explosion (bigger than the domain, so it hits outside too)").defineInRange("blast_radius", 72, 20, 160);
        HO_BLAST_PERCENT = b.comment("Final explosion damage as a fraction of max health (0.50 = 50%)").defineInRange("blast_percent", 0.50, 0.0, 1.0);
        HO_BREAKS = b.comment("The final explosion destroys terrain (needs allow_block_breaking too)").define("breaks_blocks", true);
        b.pop();
        b.comment("PRIME Sukuna: Boundless Slash (new)").push("boundless_slash");
        BS_RANGE = b.comment("How far the crescent flies").defineInRange("range", 64.0, 16.0, 200.0);
        BS_SPEED = b.comment("Blocks per tick").defineInRange("speed", 2.2, 0.5, 8.0);
        BS_HEIGHT = b.comment("Height of the crescent in blocks. It is a thin diagonal blade, so it is hard to land").defineInRange("height", 20.0, 6.0, 60.0);
        BS_FLAT = b.comment("Flat damage").defineInRange("damage", 150.0, 0.0, 100000.0);
        BS_PERCENT = b.comment("Extra damage as a fraction of max health (0.10 = 10%)").defineInRange("damage_percent", 0.10, 0.0, 1.0);
        BS_DISMANTLE_SECONDS = b.comment("How long the target is Dismantled (cannot melee attack)").defineInRange("dismantle_seconds", 6, 1, 120);
        BS_BREAKS = b.comment("The blade cuts a slit through the terrain (needs allow_block_breaking too)").define("breaks_blocks", true);
        b.pop();

        b.comment("PRIME Sukuna: World Disintegration (Fuga evolved)").push("world_disintegration");
        WD_CHARGE_SECONDS = b.comment("Seconds the arrow charges before it is loosed").defineInRange("charge_seconds", 2.0, 0.5, 10.0);
        WD_RANGE = b.comment("How far the arrow flies").defineInRange("range", 192.0, 32.0, 400.0);
        WD_RADIUS = b.comment("Radius of the explosion pillar").defineInRange("radius", 30.0, 6.0, 90.0);
        WD_REACH_UP = b.comment("How high the pillar hurts, in blocks above the impact (it reaches the heavens)").defineInRange("reach_up", 256.0, 20.0, 600.0);
        WD_FLAT = b.comment("Flat damage").defineInRange("damage", 200.0, 0.0, 100000.0);
        WD_PERCENT = b.comment("Extra damage as a fraction of max health (0.40 = 40%)").defineInRange("damage_percent", 0.40, 0.0, 1.0);
        WD_BURN_SECONDS = b.comment("Everything caught in the pillar burns for this long").defineInRange("burn_seconds", 8, 0, 120);
        WD_BREAKS = b.comment("The explosion erases the terrain (needs allow_block_breaking too)").define("breaks_blocks", true);
        b.pop();

        b.comment("PRIME Sukuna: Universe Severing Slash (World Cutting Slash evolved)").push("universe_severing_slash");
        US_RANGE = b.comment("How far the cuts travel").defineInRange("range", 64, 24, 160);
        US_HALF_WIDTH = b.comment("Half width of the cut field (a multiple of 8 is best)").defineInRange("half_width", 48, 16, 120);
        US_SPEED = b.comment("Blocks per tick the cut front moves").defineInRange("speed", 3.0, 1.0, 10.0);
        US_FLAT = b.comment("Flat damage of the cuts").defineInRange("damage", 120.0, 0.0, 100000.0);
        US_PERCENT = b.comment("Extra damage of the cuts as a fraction of max health").defineInRange("damage_percent", 0.10, 0.0, 1.0);
        US_HOLD_SECONDS = b.comment("Seconds the black hole waits before it explodes").defineInRange("hold_seconds", 2.0, 0.5, 10.0);
        US_PROJECTILES = b.comment("How many slashes the black hole throws out").defineInRange("projectiles", 24, 4, 80);
        US_PROJ_SPEED = b.comment("Blocks per tick of the thrown slashes").defineInRange("projectile_speed", 1.8, 0.5, 6.0);
        US_PROJ_FLAT = b.comment("Flat damage of one thrown slash").defineInRange("projectile_damage", 80.0, 0.0, 100000.0);
        US_PROJ_PERCENT = b.comment("Extra damage of one thrown slash as a fraction of max health").defineInRange("projectile_damage_percent", 0.05, 0.0, 1.0);
        US_PROJ_MAX_HITS = b.comment("Most thrown slashes that can hit the same creature").defineInRange("projectile_max_hits_per_target", 3, 1, 80);
        US_DISMANTLE_SECONDS = b.comment("How long a creature hit is Dismantled (cannot melee attack)").defineInRange("dismantle_seconds", 6, 1, 120);
        US_BREAKS = b.comment("The cuts carve the terrain (needs allow_block_breaking too)").define("breaks_blocks", true);
        b.pop();
        SPEC = b.build();
    }

    private PrimeConfig() {
    }
}
