package com.example.jjkspells.relic;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/jjk_spells-relics.toml */
public final class RelicConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED, CREATIVE_BYPASS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Casting a character's spells needs that character's Relic worn in a Relic curio slot (2 slots).").define("enabled", true);
        CREATIVE_BYPASS = b.comment("Creative-mode players can cast everything without Relics (handy for testing).").define("creative_bypass", false);
        SPEC = b.build();
    }

    private RelicConfig() {}

    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean creativeBypass() {
        try {
            return CREATIVE_BYPASS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
