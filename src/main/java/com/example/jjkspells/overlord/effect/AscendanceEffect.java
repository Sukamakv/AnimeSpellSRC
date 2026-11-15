package com.example.jjkspells.overlord.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker effect on the caster after "The Goal of All Life Is Death": Grasp Heart can then kill boss-type creatures. */
public class AscendanceEffect extends MobEffect {
    public AscendanceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB01030);
    }
}
