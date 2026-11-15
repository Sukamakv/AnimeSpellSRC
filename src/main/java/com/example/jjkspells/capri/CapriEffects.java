package com.example.jjkspells.capri;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Cyberpsychosis: the bill for Onslaught Mode. -30 % max HP for 20 seconds, restored by vanilla when it ends. */
public final class CapriEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    public static final RegistryObject<MobEffect> CYBER = E.register("cyberpsychosis", Cyber::new);

    private CapriEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }

    public static final class Cyber extends MobEffect {
        public Cyber() {
            super(MobEffectCategory.HARMFUL, 0xFF2050);
            addAttributeModifier(Attributes.MAX_HEALTH, "5c0e1b0a-7d3e-4b6f-9a21-c4a9e07b1d33", -0.30, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }

        /** Vanilla does not pull current health down when max health shrinks. */
        @Override
        public void applyEffectTick(LivingEntity e, int amplifier) {
            if (e.getHealth() > e.getMaxHealth()) {
                e.setHealth(e.getMaxHealth());
            }
        }
    }
}
