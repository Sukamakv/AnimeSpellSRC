package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.entity.AzureOrbProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Fires the volleys of Azure Orb Barrage over several ticks (a small server-side scheduler).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class AzureBarrage {
    private static final class Task {
        int delay;
        final Runnable action;

        Task(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private AzureBarrage() {
    }

    /** Starts a barrage from {@code caster}: {@code volleys} volleys, alternating the right and left hand. */
    public static void cast(ServerLevel level, LivingEntity caster, int volleys, float radius) {
        for (int i = 0; i < volleys; i++) {
            final boolean rightHand = i % 2 == 0;
            TASKS.add(new Task(i * AzureOrbBarrageSpell.VOLLEY_INTERVAL_TICKS, () -> volley(level, caster, rightHand, radius)));
        }
    }

    private static void volley(ServerLevel level, LivingEntity caster, boolean rightHand, float radius) {
        if (!caster.isAlive() || caster.level() != level) return;

        Vec3 look = caster.getLookAngle();
        float yaw = caster.getYHeadRot() * ((float) Math.PI / 180f);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 origin = caster.getEyePosition().add(right.scale(rightHand ? 0.8 : -0.8)).add(look.scale(0.9)).add(0, -0.45, 0);

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.6f);
        FX.emit(level, ModParticles.AZURE_GLOW.get(), origin.x, origin.y, origin.z, 0, 1.6, 0, 0, 1.0);
        FX.emit(level, ModParticles.AZURE_RING.get(), origin.x, origin.y, origin.z, 0, 2.2, 0, 0, 1.0);

        float[] fan = {-0.2f, 0f, 0.2f}; // fan of about +-11 degrees
        for (int i = 0; i < AzureOrbBarrageSpell.ORBS_PER_VOLLEY; i++) {
            AzureOrbProjectile orb = new AzureOrbProjectile(level, caster);
            orb.setPos(origin.x, origin.y, origin.z);
            orb.launch(look.yRot(fan[i % fan.length]), 1.3f).setRadius(radius).setMaxLife(70);
            level.addFreshEntity(orb);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;
        for (int i = 0; i < TASKS.size(); ) {
            Task t = TASKS.get(i);
            if (t.delay-- <= 0) {
                TASKS.remove(i);
                try {
                    t.action.run();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            } else {
                i++;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }
}
