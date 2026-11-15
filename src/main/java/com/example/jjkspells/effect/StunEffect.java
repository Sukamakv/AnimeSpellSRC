package com.example.jjkspells.effect;

import com.example.jjkspells.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Stunned: the target cannot walk, fly, jump, attack, cast spells or use items. Outside forces (knockback) still
 * move it, so Hard Strike can throw an enemy across the room and leave it dazed when it lands.
 * The behaviour lives in StunEvents; this class carries the attribute changes (no walking / flying) and a helper.
 */
public class StunEffect extends MobEffect {
    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0xF4D03F);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "b3d7c1e2-5a14-4c68-9f20-1e8a6d4b7c93", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.FLYING_SPEED, "0c5e9a47-2b61-4d3f-8a75-6f1d3b8e2c04", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** Stuns an entity for {@code ticks} ticks. Never shortens a stun that already lasts longer. */
    public static void apply(LivingEntity e, int ticks) {
        if (com.example.jjkspells.gil.GilState.stunImmune(e)) return;
        MobEffectInstance cur = e.getEffect(ModEffects.STUN.get());
        if (cur != null && cur.getDuration() >= ticks) return;
        e.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ticks, 0, false, false, true));
    }
}
