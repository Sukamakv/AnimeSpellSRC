package com.example.jjkspells.effect;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.spells.AatroxFX;
import com.example.jjkspells.spells.DariusFX;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.WorldEnderSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What World Ender does while it lasts:
 *   - everything its holder damages is hurt 50% more
 *   - 20% of the damage dealt is healed back, up to a cap on the total healed per activation
 *   - wings, a crimson aura and rising embers; players nearby see the world darken
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class AatroxEvents {

    /** Total health that lifesteal can restore during one activation, as a fraction of maximum health. */
    public static final float HEAL_CAP_FRACTION = 0.35f;
    public static final float DAMAGE_BONUS = 1.5f;
    public static final float LIFESTEAL = 0.20f;

    /** caster -> health already restored during this activation */
    private static final Map<UUID, Float> HEALED = new HashMap<>();

    public static void resetHealing(LivingEntity caster) {
        HEALED.put(caster.getUUID(), 0f);
    }

    /** +50% damage dealt. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide) return;
        if (!attacker.hasEffect(ModEffects.WORLD_ENDER.get()) || event.getEntity() == attacker) return;
        event.setAmount(event.getAmount() * DAMAGE_BONUS);
    }

    /** Lifesteal on the damage that actually lands (overkill does not count), subject to the cap. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide) return;
        if (!attacker.hasEffect(ModEffects.WORLD_ENDER.get()) || event.getEntity() == attacker) return;
        float dealt = Math.min(event.getAmount(), event.getEntity().getHealth());
        if (dealt <= 0) return;
        float cap = attacker.getMaxHealth() * HEAL_CAP_FRACTION;
        float healed = HEALED.getOrDefault(attacker.getUUID(), 0f);
        float heal = Math.min(dealt * LIFESTEAL, cap - healed);
        if (heal <= 0) return;
        HEALED.put(attacker.getUUID(), healed + heal);
        attacker.heal(heal);
        if (attacker.level() instanceof ServerLevel level) {
            AatroxFX.blood(level, event.getEntity().getBoundingBox().getCenter(), 2, 0.2);
        }
    }

    /** Wings, aura and darkness while it lasts. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level)) return;
        MobEffectInstance inst = e.getEffect(ModEffects.WORLD_ENDER.get());
        if (inst == null) return;

        int total = Math.round(WorldEnderSpell.DURATION_TICKS * DariusFX.levelScale(inst.getAmplifier() + 1));
        int elapsed = Math.max(0, total - inst.getDuration());
        if (e.tickCount % 3 == 0) {
            float spread = Mth.clamp(elapsed / 10f, 0f, 1f);                  // the wings expand dramatically at the start
            AatroxFX.wings(level, e, spread, 1.0f, 5);
        }
        if (e.tickCount % 2 == 0) {
            Vec3 pos = e.position();
            AatroxFX.smoke(level, pos.add(0, 0.6, 0), 2, 0.7);
            FX.one(level, ModParticles.DR_SPARK.get(), pos.add((level.random.nextDouble() - 0.5) * 0.8, 0.2, (level.random.nextDouble() - 0.5) * 0.8), new Vec3(0, 0.12, 0));
            if (e.tickCount % 6 == 0) {
                FX.one(level, ModParticles.DR_GLOW.get(), pos.add(0, e.getBbHeight() * 0.6, 0), new Vec3(1.5, 0, 0));
                AatroxFX.blood(level, pos.add(0, e.getBbHeight() * 0.5, 0), 1, 0.12);
            }
        }
        if (e.tickCount % 5 == 0) {                                           // the world darkens for players around him
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(e) <= 24 * 24) {
                    p.addEffect(new MobEffectInstance(ModEffects.AT_DARKNESS.get(), 12, 0, false, false, false));
                }
            }
        }
    }
}
