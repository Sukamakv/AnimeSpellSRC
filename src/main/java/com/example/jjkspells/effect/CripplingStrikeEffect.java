package com.example.jjkspells.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Crippling Strike ready": while it lasts, the holder's next melee hit is the Crippling Strike.
 * The amplifier is the spell level minus one. All behaviour is in CripplingStrikeEvents.
 */
public class CripplingStrikeEffect extends MobEffect {
    public CripplingStrikeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB01020);
    }
}
