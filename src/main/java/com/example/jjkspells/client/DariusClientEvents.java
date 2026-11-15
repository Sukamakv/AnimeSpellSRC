package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of two of Noxian Guillotine's effects. The server hands them out as hidden status effects, and the
 * local player's client acts on them:
 *   Muffled - world sounds (mobs, ambience, blocks, weather) are silenced, except the heartbeat.
 *   Shaken  - the camera shakes, fading out over the effect's last ticks.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public class DariusClientEvents {

    private static final float SHAKE_FULL_TICKS = 8f;
    private static final float SHAKE_DEGREES = 2.4f;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.hasEffect(ModEffects.MUFFLED.get())) return;
        SoundInstance sound = event.getSound();
        if (sound == null) return;
        SoundSource source = sound.getSource();
        boolean world = source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL || source == SoundSource.AMBIENT
                || source == SoundSource.BLOCKS || source == SoundSource.WEATHER;
        if (world && !sound.getLocation().getPath().contains("heartbeat")) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        MobEffectInstance shake = player.getEffect(ModEffects.SHAKEN.get());
        if (shake == null) return;
        float strength = Mth.clamp(shake.getDuration() / SHAKE_FULL_TICKS, 0f, 1f) * SHAKE_DEGREES;
        var rnd = player.getRandom();
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5f) * 2f * strength * 0.6f);
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5f) * 2f * strength);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5f) * 2f * strength * 0.8f);
    }
}
