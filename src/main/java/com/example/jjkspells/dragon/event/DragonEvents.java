package com.example.jjkspells.dragon.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dragon.config.DragonConfig;
import com.example.jjkspells.dragon.entity.AscensionFxEntity;
import com.example.jjkspells.dragon.registry.ModEffects;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class DragonEvents {
    private DragonEvents() {
    }

    private static boolean hasAuthority(LivingEntity e) {
        return e != null && e.hasEffect(ModEffects.AUTHORITY.get());
    }

    /** Each melee hit adds a percentage of the target's max HP. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity attacker && e.getSource().getDirectEntity() == attacker && hasAuthority(attacker)) {
            double pct = DragonConfig.BUFF.meleeMaxHealthPercent.get() / 100.0;
            // the max-health part ignores damage caps (taken straight off health)
            com.example.jjkspells.spells.SpellDamage.percent(e.getEntity(), (float) (e.getEntity().getMaxHealth() * pct), attacker, e.getSource());
        }
    }

    /** Immune to every harmful potion effect. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable e) {
        if (DragonConfig.BUFF.immuneToNegativeEffects.get() && hasAuthority(e.getEntity())
                && e.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            e.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        LivingEntity le = e.getEntity();
        if (!le.level().isClientSide && DragonConfig.BUFF.immuneToNegativeEffects.get() && le.tickCount % 5 == 0 && hasAuthority(le)) {
            com.example.jjkspells.dragon.effect.AuthorityEffect.cleanse(le);
        }
    }

    /** No casting while the ascension plays. */
    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!e.getEntity().level().isClientSide && AscensionFxEntity.ASCENDING.contains(e.getEntity().getUUID())) {
            e.setCanceled(true);
        }
    }
}
