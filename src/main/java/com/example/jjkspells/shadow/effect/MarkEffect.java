package com.example.jjkspells.shadow.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** A status effect that does nothing by itself; the spells and ShdEvents act on it and the client draws it over the host. */
public class MarkEffect extends MobEffect {
    public MarkEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
