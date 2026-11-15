package com.example.jjkspells.spells;

import com.example.jjkspells.GarenConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Courage's damage reduction and visuals, and Decisive Strike's empowered next attack. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class GarenEvents {
    public static final String COURAGE_START_KEY = "jjk_courage_start";

    private GarenEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(victim.level() instanceof ServerLevel level)) return;

        // ---- Courage: reduced damage, gold flash and a metallic clang instead of a plain hit ------------
        if (victim.hasEffect(ModEffects.COURAGE.get())) {
            long elapsed = level.getGameTime() - victim.getPersistentData().getLong(COURAGE_START_KEY);
            double reduction = elapsed <= GarenConfig.CO_EARLY_SECONDS.get() * 20 ? GarenConfig.CO_EARLY_REDUCTION.get() : GarenConfig.CO_REDUCTION.get();
            event.setAmount((float) (event.getAmount() * (1.0 - reduction)));

            Entity src = event.getSource().getEntity();
            Vec3 dir = src != null ? src.position().subtract(victim.position()) : victim.getLookAngle();
            dir = new Vec3(dir.x, 0, dir.z);
            dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
            Vec3 p = victim.position().add(0, victim.getBbHeight() * 0.6, 0).add(dir.scale(0.6));
            FX.emit(level, ModParticles.GG_GLOW.get(), p.x, p.y, p.z, 0, 1.6, 0, 0, 1.0);
            FX.emit(level, ModParticles.GG_RING.get(), p.x, p.y, p.z, 0, 1.2, 0, 0, 1.0);
            for (int i = 0; i < 8; i++) {
                FX.one(level, ModParticles.GG_SHARD.get(), p, new Vec3(level.random.nextGaussian() * 0.15 + dir.x * 0.1, level.random.nextDouble() * 0.25, level.random.nextGaussian() * 0.15 + dir.z * 0.1));
            }
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.1f);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.3f, 1.9f);
        }

        // ---- Decisive Strike: the next melee attack is empowered ----------------------------------------
        Entity attacker = event.getSource().getEntity();
        if (!GarenManager.busy && attacker instanceof LivingEntity a && event.getSource().getDirectEntity() == a
                && level.getGameTime() <= a.getPersistentData().getLong(GarenManager.EMPOWER_KEY)) {
            a.getPersistentData().putLong(GarenManager.EMPOWER_KEY, 0);
            event.setAmount(event.getAmount() + GarenManager.weaponDamage(a) * GarenConfig.DS_EMPOWER_PERCENT.get().floatValue());
            Vec3 c = victim.getBoundingBox().getCenter();
            FX.emit(level, ModParticles.GR_GLOW.get(), c.x, c.y, c.z, 0, 2.0, 0, 0, 1.0);
            for (int i = 0; i < 10; i++) {
                FX.one(level, ModParticles.GR_SPARK.get(), c, new Vec3(level.random.nextGaussian(), level.random.nextDouble(), level.random.nextGaussian()).scale(0.25));
            }
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    /** Courage aura: a translucent golden-white shimmer around the armour while the effect lasts. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !(e.level() instanceof ServerLevel level)) return;
        if (e.tickCount % 2 != 0 || !e.hasEffect(ModEffects.COURAGE.get())) return;
        double h = e.getBbHeight();
        for (int i = 0; i < 4; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double y = level.random.nextDouble() * h;
            FX.one(level, ModParticles.GOLD_MOTE.get(), e.position().add(Math.cos(a) * 0.65, y, Math.sin(a) * 0.65), new Vec3(0, 0.03 + level.random.nextDouble() * 0.04, 0));
        }
        if (e.tickCount % 10 == 0) {
            FX.emit(level, ModParticles.GG_RING_FLAT.get(), e.getX(), e.getY() + 0.1, e.getZ(), 0, 1.4, 0, 0, 1.0);
        }
    }
}
