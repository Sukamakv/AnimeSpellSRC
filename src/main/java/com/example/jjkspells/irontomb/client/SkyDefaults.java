package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.IrontombCut;

/** Sky darkening for the cutscenes that don't run their own dome (the attack classes handle their own). */
final class SkyDefaults {
    private SkyDefaults() {
    }

    static void apply(FxCtx c) {
        // permanent deep-crimson void while the boss is awake, on top of the per-cutscene windows
        float base = c.e.isAwake() ? 0.8f : 0f;
        if (c.cut == IrontombCut.INTRO) {
            base *= Fx.ramp(c.ct, 0, 50);
        } else if (c.cut == IrontombCut.DEATH) {
            base *= 1f - Fx.ramp(c.ct, 100, 160);
        }
        if (base > 0.01f) {
            SkyFx.dome(c, base, 0.05f, 0.012f, 0.025f);
        }
    }
}
