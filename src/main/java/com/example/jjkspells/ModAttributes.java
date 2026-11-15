package com.example.jjkspells;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Max Health Damage: a stat like Iron's Spell Power. 1.0 = normal. Every max-health percentage hit of this mod
 * (SpellDamage.percent) is multiplied by the attacker's value. Give it from gear/curios/commands with a MULTIPLY_BASE
 * modifier (+0.5 = +50%) or ADDITION. Present on every living entity, so bosses can have it too.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModAttributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, JJKSpells.MODID);

    public static final RegistryObject<Attribute> MAX_HP_DAMAGE = ATTRIBUTES.register("max_hp_damage",
            () -> new RangedAttribute("attribute.name.jjk_spells.max_hp_damage", 1.0, 0.0, 100.0).setSyncable(true));

    /** Max Health Resistance: 0 = none, 0.25 = 25% less max-health damage taken, 1 = immune. Stacks with the ward curios. */
    public static final RegistryObject<Attribute> MAX_HP_RESIST = ATTRIBUTES.register("max_hp_resist",
            () -> new RangedAttribute("attribute.name.jjk_spells.max_hp_resist", 0.0, 0.0, 1.0).setSyncable(true));

    private ModAttributes() {}

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
    }

    @SubscribeEvent
    public static void attach(EntityAttributeModificationEvent event) {
        for (var type : event.getTypes()) {
            if (!event.has(type, MAX_HP_DAMAGE.get())) event.add(type, MAX_HP_DAMAGE.get());
            if (!event.has(type, MAX_HP_RESIST.get())) event.add(type, MAX_HP_RESIST.get());
        }
    }

    /** The target's attribute resistance, 0..1. */
    public static float resist(LivingEntity e) {
        if (e == null) return 0f;
        var inst = e.getAttribute(MAX_HP_RESIST.get());
        return inst == null ? 0f : (float) Math.min(1.0, Math.max(0.0, inst.getValue()));
    }

    /** The attacker's multiplier (1 if it has none). */
    public static float multiplier(LivingEntity e) {
        if (e == null) return 1f;
        var inst = e.getAttribute(MAX_HP_DAMAGE.get());
        return inst == null ? 1f : (float) inst.getValue();
    }
}
