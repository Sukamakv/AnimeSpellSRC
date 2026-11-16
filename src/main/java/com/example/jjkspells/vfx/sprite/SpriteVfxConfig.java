package com.example.jjkspells.vfx.sprite;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/jjk_spells-vfx-client.toml - per player cost controls for sprite effects. */
public final class SpriteVfxConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, CROSSFADE;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE;
    public static final ForgeConfigSpec.DoubleValue CULL_DISTANCE, DENSITY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("sprite_vfx");
        ENABLED = b.comment("Turn every sprite effect off (spells still work, they just look plain).").define("enabled", true);
        MAX_ACTIVE = b.comment("Hard cap on effects alive at once. New ones are dropped when full.").defineInRange("maxActive", 512, 32, 4096);
        CULL_DISTANCE = b.comment("Effects further than this many blocks from the camera are not drawn.").defineInRange("cullDistance", 96.0, 16.0, 512.0);
        DENSITY = b.comment("Share (0.1-1) of purely decorative effects that are shown. Lower this on weak machines.").defineInRange("decorDensity", 1.0, 0.1, 1.0);
        CROSSFADE = b.comment("Blend neighbouring frames for smoother animation (costs one extra quad per effect).").define("crossfade", true);
        b.pop();
        SPEC = b.build();
    }

    private SpriteVfxConfig() {}
}
