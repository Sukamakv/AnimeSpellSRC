package com.example.jjkspells.irontomb;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.entity.IrontombEntity;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Action Lock enforcement plus the /irontomb test command (op level 2). */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class IrontombEvents {
    private IrontombEvents() {
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (IronsCompat.isLocked(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUse(LivingEntityUseItemEvent.Start e) {
        if (IronsCompat.isLocked(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightItem(PlayerInteractEvent.RightClickItem e) {
        if (IronsCompat.isLocked(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightBlock(PlayerInteractEvent.RightClickBlock e) {
        if (IronsCompat.isLocked(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (IronsCompat.isLocked(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntity(PlayerInteractEvent.EntityInteract e) {
        if (IronsCompat.isLocked(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    private static int cfgTimer;

    /** Watches the config file; edits made while the server runs apply within a second, no restart or command needed. */
    @SubscribeEvent
    public static void configWatch(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ++cfgTimer >= 20) {
            cfgTimer = 0;
            IrontombBalance.reloadIfChanged();
        }
    }

    // ------------------------------------------------------------------ /irontomb
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("irontomb").requires(s -> s.hasPermission(2))
                .then(Commands.literal("attack").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.asList(IrontombAttack.NAMES), b))
                        .executes(c -> attack(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("cutscene").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.asList(IrontombCut.NAMES), b))
                        .executes(c -> cutscene(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
                        .executes(c -> phase(c, IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("arena")
                        .then(Commands.literal("enter").executes(c -> arena(c, 0)))
                        .then(Commands.literal("start").executes(c -> arena(c, 1)))
                        .then(Commands.literal("leave").executes(c -> arena(c, 2))))
                .then(Commands.literal("reload").executes(IrontombEvents::reload))
                .then(Commands.literal("hatred").then(Commands.argument("v", IntegerArgumentType.integer(0, 100))
                        .executes(c -> hatred(c, IntegerArgumentType.getInteger(c, "v"))))));
    }

    private static IrontombEntity nearest(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        ServerLevel lv = src.getLevel();
        Vec3 p = src.getPosition();
        List<IrontombEntity> list = lv.getEntitiesOfClass(IrontombEntity.class, new AABB(p, p).inflate(220));
        return list.stream().min(Comparator.comparingDouble(b -> b.distanceToSqr(p))).orElse(null);
    }

    /** mode 0 = enter, 1 = enter and make sure the boss is there, 2 = leave. */
    private static int arena(CommandContext<CommandSourceStack> c, int mode) {
        net.minecraft.server.level.ServerPlayer p;
        try {
            p = c.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            return fail(c, "Only a player can use this.");
        }
        if (mode == 2) {
            return IrontombArena.leave(p) ? ok(c, "Back from the arena.") : fail(c, "You are not in the Irontomb arena.");
        }
        if (!IrontombArena.enter(p)) {
            return fail(c, "The dimension jjk_spells:irontomb_arena is missing: check data/jjk_spells/dimension in the mod.");
        }
        if (mode == 1) {
            IrontombArena.ensureBoss(IrontombArena.level(p));
        }
        return ok(c, mode == 1 ? "The Irontomb arena awaits." : "Entered the Irontomb arena.");
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int attack(CommandContext<CommandSourceStack> c, String name) {
        IrontombEntity b = nearest(c);
        int id = IrontombAttack.byName(name);
        if (b == null) {
            return fail(c, "No Irontomb within 220 blocks. Use /summon jjk_spells:irontomb");
        }
        if (id <= 0) {
            return fail(c, "Unknown attack. Try: " + String.join(", ", IrontombAttack.NAMES));
        }
        b.forceAttack(id);
        return ok(c, "Irontomb: " + IrontombAttack.TITLES[id]);
    }

    private static int cutscene(CommandContext<CommandSourceStack> c, String name) {
        IrontombEntity b = nearest(c);
        int id = IrontombCut.byName(name);
        if (b == null) {
            return fail(c, "No Irontomb within 220 blocks. Use /summon jjk_spells:irontomb");
        }
        if (id <= 0) {
            return fail(c, "Unknown cutscene. Try: " + String.join(", ", IrontombCut.NAMES));
        }
        b.forceCutscene(id);
        return ok(c, "Cutscene: " + IrontombCut.NAMES[id]);
    }

    private static int phase(CommandContext<CommandSourceStack> c, int n) {
        IrontombEntity b = nearest(c);
        if (b == null) {
            return fail(c, "No Irontomb within 220 blocks.");
        }
        b.forcePhase(n);
        return ok(c, "Irontomb phase " + n);
    }

    private static int hatred(CommandContext<CommandSourceStack> c, int v) {
        IrontombEntity b = nearest(c);
        if (b == null) {
            return fail(c, "No Irontomb within 220 blocks.");
        }
        b.setHatred(v);
        return ok(c, "Amphorean Hatred " + v + "%");
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        String msg = IrontombBalance.load();
        IrontombEntity b = nearest(c);
        if (b != null) {
            b.applyConfig();
        }
        return ok(c, msg + (b != null ? " - applied to the nearest Irontomb." : ""));
    }
}
