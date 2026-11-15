package com.example.jjkspells;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /spellids [filter]  prints the id of every spell of this mod in chat, as "namespace:name". Click an id to copy it.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class SpellIdCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spellids")
                .executes(ctx -> run(ctx, ""))
                .then(Commands.argument("filter", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "filter")))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String filter) {
        String f = filter.trim().toLowerCase();
        List<AbstractSpell> spells = new ArrayList<>();
        for (AbstractSpell s : SpellRegistry.REGISTRY.get().getValues()) {
            String ns = s.getSpellResource().getNamespace();
            if (!ns.equals(JJKSpells.MODID)) continue;
            if (!f.isEmpty() && !s.getSpellResource().toString().toLowerCase().contains(f)) continue;
            spells.add(s);
        }
        spells.sort(Comparator.comparing(s -> s.getSpellResource().toString()));
        CommandSourceStack src = ctx.getSource();
        if (spells.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No spell found" + (f.isEmpty() ? "." : " for \"" + f + "\".")).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(spells.size() + " spell id(s), click one to copy:").withStyle(ChatFormatting.GOLD), false);
        for (AbstractSpell s : spells) {
            String id = s.getSpellResource().toString();
            MutableComponent line = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(s.getSpellResource().toLanguageKey("spell")).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  "))
                    .append(Component.literal("\"" + id + "\"").withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy")))));
            src.sendSuccess(() -> line, false);
        }
        return spells.size();
    }
}
