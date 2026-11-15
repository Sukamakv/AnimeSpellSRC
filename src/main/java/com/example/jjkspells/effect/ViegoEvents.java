package com.example.jjkspells.effect;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.spells.FX;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Viego inside his Black Mist hits twice as hard in melee (spells are not doubled). */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class ViegoEvents {

    private static boolean isMelee(DamageSource src, LivingEntity attacker) {
        return (src.is(DamageTypes.PLAYER_ATTACK) || src.is(DamageTypes.MOB_ATTACK)) && src.getDirectEntity() == attacker;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !(attacker.level() instanceof ServerLevel level)) return;
        if (!attacker.hasEffect(ModEffects.VIEGO_MIST.get()) || !isMelee(event.getSource(), attacker)) return;
        LivingEntity target = event.getEntity();
        if (target == attacker || target.isAlliedTo(attacker)) return;
        event.setAmount(event.getAmount() * 2.0f);
        FX.one(level, ModParticles.VG_HIT.get(), target.getBoundingBox().getCenter(), new Vec3(0.9, 0, 0));
    }
}
