package com.example.jjkspells.vfx.sprite;

import com.example.jjkspells.JJKSpells;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * /jjkvfx play NAME [scale]   spawns one sprite effect 5 blocks in front of you
 * /jjkvfx demo [PALETTE]      spawns one of every kind in a row, for a quick visual check of every sheet
 * Operator only (permission level 2).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class SpriteVfxCommand {
    private SpriteVfxCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("jjkvfx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("effect", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SpriteFx.allNames(), b))
                                .executes(c -> play(c.getSource(), StringArgumentType.getString(c, "effect"), 1f))
                                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.05f, 20f))
                                        .executes(c -> play(c.getSource(), StringArgumentType.getString(c, "effect"),
                                                FloatArgumentType.getFloat(c, "scale"))))))
                .then(Commands.literal("demo")
                        .executes(c -> demo(c.getSource(), "fire"))
                        .then(Commands.argument("palette", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        List.of("fire", "crimson", "azure", "violet", "gold", "toxic", "white"), b))
                                .executes(c -> demo(c.getSource(), StringArgumentType.getString(c, "palette"))))));
    }

    private static int play(CommandSourceStack src, String name, float scale) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        Vec3 look = sp.getLookAngle();
        Vec3 at = sp.getEyePosition().add(look.scale(5));
        SpriteVfxApi.play((ServerLevel) sp.level(), name, at).scale(scale).dir(look).length(8f).send();
        src.sendSuccess(() -> Component.literal("jjkvfx: " + name), false);
        return 1;
    }

    private static int demo(CommandSourceStack src, String palette) {
        ServerPlayer sp = src.getPlayer();
        if (sp == null) return 0;
        ServerLevel level = (ServerLevel) sp.level();
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        String[] kinds = {SpriteFx.BURST, SpriteFx.FLAME, SpriteFx.ORB, SpriteFx.SLASH, SpriteFx.RING, SpriteFx.LIGHTNING,
                SpriteFx.IMPACT, SpriteFx.CIRCLE, SpriteFx.VORTEX, SpriteFx.BEAM, SpriteFx.SPARKS, SpriteFx.GLOW};
        Vec3 base = sp.position().add(flat.scale(10));
        for (int i = 0; i < kinds.length; i++) {
            Vec3 at = base.add(right.scale((i - kinds.length / 2.0) * 4.5)).add(0, 1.5, 0);
            SpriteVfxApi.Builder b = SpriteVfxApi.play(level, kinds[i] + "_" + palette, at).radius(128);
            switch (kinds[i]) {
                case SpriteFx.SLASH -> b.dir(flat.scale(-1)).scale(0.9f);
                case SpriteFx.LIGHTNING -> b.dir(0, -1, 0).length(6f).scale(0.6f);
                case SpriteFx.BEAM -> b.dir(0, 1, 0).length(5f).life(60).scale(0.8f);
                case SpriteFx.CIRCLE, SpriteFx.VORTEX, SpriteFx.RING -> {
                    b.scale(0.6f);
                    if (!SpriteFx.RING.equals(kinds[i])) b.life(80);
                }
                case SpriteFx.FLAME, SpriteFx.ORB, SpriteFx.GLOW -> b.life(80).scale(0.9f);
                default -> b.scale(0.9f);
            }
            b.send();
        }
        src.sendSuccess(() -> Component.literal("jjkvfx demo: " + palette), false);
        return 1;
    }
}
