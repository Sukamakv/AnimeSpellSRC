package com.example.jjkspells.network;

import com.example.jjkspells.client.TimeStopClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: the state of the time stop in this dimension. */
public class TimeStopPacket {
    public static final int NONE = 0, CHANT = 1, FLASH = 2, STOPPED = 3;

    public final int state;
    public final int casterId;
    public final int ticks;
    public final boolean freezePlayers;

    public TimeStopPacket(int state, int casterId, int ticks, boolean freezePlayers) {
        this.freezePlayers = freezePlayers;
        this.state = state;
        this.casterId = casterId;
        this.ticks = ticks;
    }

    public static void encode(TimeStopPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.state);
        buf.writeInt(p.casterId);
        buf.writeInt(p.ticks);
        buf.writeBoolean(p.freezePlayers);
    }

    public static TimeStopPacket decode(FriendlyByteBuf buf) {
        return new TimeStopPacket(buf.readByte(), buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(TimeStopPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TimeStopClient.receive(p.state, p.casterId, p.ticks, p.freezePlayers)));
        ctx.get().setPacketHandled(true);
    }
}
