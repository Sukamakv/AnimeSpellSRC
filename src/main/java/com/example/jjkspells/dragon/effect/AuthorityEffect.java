package com.example.jjkspells.dragon.effect;

import com.example.jjkspells.dragon.config.DragonConfig;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.UUID;

/**
 * True Dragon Authority: +spell power, +attack damage, creative flight. (The melee max-HP bonus and the immunity to
 * negative effects are handled in DragonEvents.) Modifiers are added at runtime so we never touch another mod's
 * registry objects during our own registration.
 */
public class AuthorityEffect extends MobEffect {
    private static final UUID SPELL_POWER_ID = UUID.fromString("7c1d2f0e-3a55-4f4e-9a6b-1d0a7e000001");
    private static final UUID ATTACK_ID = UUID.fromString("7c1d2f0e-3a55-4f4e-9a6b-1d0a7e000002");

    public AuthorityEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD060);
    }

    @Override
    public void addAttributeModifiers(LivingEntity e, AttributeMap map, int amplifier) {
        super.addAttributeModifiers(e, map, amplifier);
        put(map, AttributeRegistry.SPELL_POWER.get(), SPELL_POWER_ID, "True Dragon spell power", DragonConfig.BUFF.spellPowerBonus.get());
        put(map, Attributes.ATTACK_DAMAGE, ATTACK_ID, "True Dragon attack", DragonConfig.BUFF.attackDamageBonus.get());
        if (!e.level().isClientSide) {
            grantFlight(e);
            if (DragonConfig.BUFF.immuneToNegativeEffects.get()) {
                cleanse(e);
            }
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity e, AttributeMap map, int amplifier) {
        super.removeAttributeModifiers(e, map, amplifier);
        AttributeInstance sp = map.getInstance(AttributeRegistry.SPELL_POWER.get());
        if (sp != null) {
            sp.removeModifier(SPELL_POWER_ID);
        }
        AttributeInstance atk = map.getInstance(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.removeModifier(ATTACK_ID);
        }
        if (!e.level().isClientSide && e instanceof Player p) {
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, false, true)); // soft landing
            }
        }
    }

    private static void put(AttributeMap map, Attribute attr, UUID id, String name, double amount) {
        AttributeInstance inst = map.getInstance(attr);
        if (inst != null) {
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (!e.level().isClientSide) {
            grantFlight(e); // re-assert in case something reset the abilities
            if (DragonConfig.BUFF.immuneToNegativeEffects.get()) {
                cleanse(e);
            }
        }
    }

    public static void grantFlight(LivingEntity e) {
        if (DragonConfig.BUFF.creativeFlight.get() && e instanceof Player p && !p.getAbilities().mayfly) {
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
        }
    }

    public static void cleanse(LivingEntity e) {
        for (MobEffectInstance inst : new ArrayList<>(e.getActiveEffects())) {
            if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                e.removeEffect(inst.getEffect());
            }
        }
        e.clearFire();
        e.setTicksFrozen(0);
    }
}
