package com.example.jjkspells.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** A status effect that does nothing by itself; other code (often client side) checks for it. */
public class SimpleStatusEffect extends MobEffect {
    public SimpleStatusEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
