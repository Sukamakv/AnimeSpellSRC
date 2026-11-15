package com.example.jjkspells.irontomb;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.entity.PhainonEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** /phainon (op level 2): test his attacks and manage who has slain him (the key to Irontomb). */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class PhainonEvents {
    private PhainonEvents() {
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("phainon").requires(s -> s.hasPermission(2))
                .then(Commands.literal("attack").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.copyOfRange(PhainonAttack.NAMES, 1, PhainonAttack.NAMES.length), b))
                        .executes(c -> attack(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
                        .executes(c -> phase(c, IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("unlock").then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> flag(c, true))))
                .then(Commands.literal("lock").then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> flag(c, false))))
                .then(Commands.literal("seal").then(Commands.argument("targets", EntityArgument.players())
                        .executes(PhainonEvents::seal))));
    }

    private static PhainonEntity nearest(CommandContext<CommandSourceStack> c) {
        var src = c.getSource();
        List<PhainonEntity> l = src.getLevel().getEntitiesOfClass(PhainonEntity.class, new AABB(src.getPosition(), src.getPosition()).inflate(400));
        return l.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(src.getPosition()))).orElse(null);
    }

    private static int attack(CommandContext<CommandSourceStack> c, String name) {
        PhainonEntity p = nearest(c);
        int id = PhainonAttack.byName(name);
        if (p == null) {
            c.getSource().sendFailure(Component.literal("No Phainon nearby."));
            return 0;
        }
        if (id < 0) {
            c.getSource().sendFailure(Component.literal("Unknown attack. Try: " + String.join(", ", Arrays.copyOfRange(PhainonAttack.NAMES, 1, PhainonAttack.NAMES.length))));
            return 0;
        }
        if (!p.isAwake()) {
            c.getSource().sendFailure(Component.literal("Phainon is still dormant: walk within 36 blocks of him first."));
            return 0;
        }
        p.forceAttack(id);
        c.getSource().sendSuccess(() -> Component.literal("Phainon: " + PhainonAttack.TITLES[id]), false);
        return 1;
    }

    private static int phase(CommandContext<CommandSourceStack> c, int n) {
        PhainonEntity p = nearest(c);
        if (p == null) {
            c.getSource().sendFailure(Component.literal("No Phainon nearby."));
            return 0;
        }
        p.forcePhase(n);
        c.getSource().sendSuccess(() -> Component.literal("Phainon is now in phase " + n), false);
        return 1;
    }

    private static int flag(CommandContext<CommandSourceStack> c, boolean v) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : ps) {
            PhainonGate.set(p, v);
        }
        c.getSource().sendSuccess(() -> Component.literal((v ? "Unlocked Irontomb for " : "Re-locked Irontomb for ") + ps.size() + " player(s)"), true);
        return ps.size();
    }

    private static int seal(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : ps) {
            ItemStack s = new ItemStack(PhainonTypes.seal());
            if (!p.getInventory().add(s)) {
                p.drop(s, false);
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("Gave the Dawnbreaker's Seal to " + ps.size() + " player(s)"), true);
        return ps.size();
    }
}
