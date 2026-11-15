package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-player (client side) preferences. /irontomb_cutscenes on|off  turns the camera takeover, letterbox and title cards of
 * Irontomb's cutscenes off for this player only; the fight itself is unchanged. The choice is saved in
 * config/jjk_spells-irontomb-client.cfg.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class IrontombPrefs {
    private static final String FILE = "jjk_spells-irontomb-client.cfg";
    public static volatile boolean cutscenes = load();

    private IrontombPrefs() {
    }

    private static boolean load() {
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
            if (Files.exists(p)) {
                for (String l : Files.readAllLines(p)) {
                    if (l.trim().startsWith("cutscenes=")) {
                        return !l.trim().substring(10).trim().equalsIgnoreCase("false");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private static void save() {
        try {
            Files.writeString(FMLPaths.CONFIGDIR.get().resolve(FILE), "# Irontomb client options\ncutscenes=" + cutscenes + "\n");
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent e) {
        var set = Commands.literal("irontomb_cutscenes")
                .executes(c -> say("Irontomb cutscenes are " + (cutscenes ? "ON" : "OFF") + ". Use /irontomb_cutscenes on|off"))
                .then(Commands.literal("on").executes(c -> {
                    cutscenes = true;
                    save();
                    return say("Irontomb cutscenes ON");
                }))
                .then(Commands.literal("off").executes(c -> {
                    cutscenes = false;
                    save();
                    return say("Irontomb cutscenes OFF (camera stays with you, no letterbox or title cards)");
                }))
                .then(Commands.literal("toggle").executes(c -> {
                    cutscenes = !cutscenes;
                    save();
                    return say("Irontomb cutscenes " + (cutscenes ? "ON" : "OFF"));
                }));
        e.getDispatcher().register(set);
    }

    private static int say(String m) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(m), false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
