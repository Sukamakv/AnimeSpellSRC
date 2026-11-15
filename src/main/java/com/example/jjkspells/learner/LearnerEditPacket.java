package com.example.jjkspells.learner;

import com.example.jjkspells.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client to server: an operator saved an entry's materials, scroll level and section boss in the learner's edit mode. */
public class LearnerEditPacket {
    public final int entry, level;
    public final String boss;
    public final List<String> mats;

    public LearnerEditPacket(int entry, int level, String boss, List<String> mats) {
        this.entry = entry;
        this.level = level;
        this.boss = boss;
        this.mats = mats;
    }

    public static void encode(LearnerEditPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entry);
        buf.writeVarInt(p.level);
        buf.writeUtf(p.boss, 200);
        buf.writeVarInt(p.mats.size());
        for (String s : p.mats) buf.writeUtf(s, 200);
    }

    public static LearnerEditPacket decode(FriendlyByteBuf buf) {
        int e = buf.readVarInt(), lv = buf.readVarInt();
        String boss = buf.readUtf(200);
        int n = Math.min(64, buf.readVarInt());
        List<String> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(buf.readUtf(200));
        return new LearnerEditPacket(e, lv, boss, l);
    }

    public static void handle(LearnerEditPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !sp.hasPermissions(2) || !(sp.containerMenu instanceof SkillLearnerMenu m)) return;
            if (p.entry < 0 || p.entry >= m.entries.size()) return;
            var e = m.entries.get(p.entry);
            List<String> clean = new ArrayList<>();
            for (String s : p.mats) {
                Mat mat = Mat.parse(s);
                if (!mat.valid) {
                    sp.displayClientMessage(Component.literal("Not saved: unknown item " + mat.name()).withStyle(ChatFormatting.RED), true);
                    return;
                }
                clean.add(mat.toString());
            }
            String boss = p.boss.trim();
            if (!boss.isEmpty() && net.minecraft.resources.ResourceLocation.tryParse(boss) == null) {
                sp.displayClientMessage(Component.literal("Not saved: bad boss id").withStyle(ChatFormatting.RED), true);
                return;
            }
            LearnerConfig.setMaterials(e.group, e.spellPath, clean);
            if (!SkillGroups.isItem(e.spellPath)) LearnerConfig.setLevel(e.group, e.spellPath, p.level);
            LearnerConfig.setBoss(e.group, boss);
            sp.displayClientMessage(Component.literal("Saved (applies now, no restart)").withStyle(ChatFormatting.GREEN), true);
            for (ServerPlayer other : sp.server.getPlayerList().getPlayers()) {
                if (other.containerMenu instanceof SkillLearnerMenu om) {
                    for (int i = 0; i < om.entries.size(); i++) om.entries.set(i, LearnerEntries.refresh(om.entries.get(i)));
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> other), new LearnerSyncPacket(om.containerId, om.entries));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
