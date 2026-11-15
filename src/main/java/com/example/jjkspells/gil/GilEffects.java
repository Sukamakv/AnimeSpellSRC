package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.effect.SimpleStatusEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Status effects of the Gilgamesh spells. */
public final class GilEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Chains of Heaven on a boss: -70% movement speed, -30% attack speed. Never freezes. */
    public static final RegistryObject<MobEffect> HEAVEN_BOUND = E.register("heaven_bound", () -> new Slow(MobEffectCategory.HARMFUL, 0xFFD24A,
            -0.70, -0.30, "8b1f4c36-52d7-4e0a-9c63-1d7e2a5f8b40", "3e9a7d12-6c85-4b21-a0f4-5d8c1b7e2a93"));
    /** Rooted by a secondary chain: cannot walk or jump, can still act. */
    public static final RegistryObject<MobEffect> ROOTED = E.register("rooted", () -> new Slow(MobEffectCategory.HARMFUL, 0xE8C35A,
            -1.0, 0.0, "c2d84a67-1b39-4f50-8e72-9a3f6b0d5e18", "7a1e5c93-4d20-4b86-b3f7-0e8d2a6c9b51"));
    /** Spatial distortion (Enuma Elish): -40% movement speed, no teleporting. */
    public static final RegistryObject<MobEffect> DISTORTION = E.register("distortion", () -> new Slow(MobEffectCategory.HARMFUL, 0xB0101C,
            -0.40, 0.0, "5f3b9e21-8a47-4c0d-b6e5-2c1a7d4f8093", "d9a06c48-3e17-4a52-8b1f-7e5c0a2d6b34"));
    /** Rupture of Creation: -20% movement speed, -25% healing (see GilState). */
    public static final RegistryObject<MobEffect> RUPTURE = E.register("rupture", () -> new Slow(MobEffectCategory.HARMFUL, 0x7A0A14,
            -0.20, 0.0, "a4e71b35-9d02-4c68-8f13-6b5d0c2e7a49", "1c8f5a72-0b36-4d94-a7e1-3f9d6b8c2054"));
    /** Noble Phantasm Resonance: marked by Merodach's slash. */
    public static final RegistryObject<MobEffect> RESONANCE = E.register("resonance", () -> new SimpleStatusEffect(MobEffectCategory.HARMFUL, 0xFFE070));

    private GilEffects() {}

    public static void register(IEventBus bus) {
        E.register(bus);
    }

    /** An effect that scales movement speed and attack speed. */
    public static class Slow extends MobEffect {
        Slow(MobEffectCategory cat, int color, double move, double attack, String moveId, String attackId) {
            super(cat, color);
            this.addAttributeModifier(Attributes.MOVEMENT_SPEED, moveId, move, AttributeModifier.Operation.MULTIPLY_TOTAL);
            if (move <= -1.0) {
                this.addAttributeModifier(Attributes.FLYING_SPEED, attackId, -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
            } else if (attack != 0.0) {
                this.addAttributeModifier(Attributes.ATTACK_SPEED, attackId, attack, AttributeModifier.Operation.MULTIPLY_TOTAL);
            }
        }
    }
}
