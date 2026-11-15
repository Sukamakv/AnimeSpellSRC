package com.example.jjkspells.learner;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server to client: fresh materials for an open Divine Skill Learner (after somebody edited them). */
public class LearnerSyncPacket {
    public final int containerId;
    public final List<LearnerEntries.Entry> entries;

    public LearnerSyncPacket(int containerId, List<LearnerEntries.Entry> entries) {
        this.containerId = containerId;
        this.entries = entries;
    }

    public static void encode(LearnerSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.containerId);
        LearnerEntries.write(buf, p.entries);
    }

    public static LearnerSyncPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        return new LearnerSyncPacket(id, LearnerEntries.read(buf));
    }

    public static void handle(LearnerSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LearnerClient.receive(p.containerId, p.entries)));
        ctx.get().setPacketHandled(true);
    }
}
