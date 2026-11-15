package com.example.jjkspells.enchant;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the player swung at empty air (the server gets no interaction event for that). */
public class FlameSwingPacket {
    public static void encode(FlameSwingPacket p, FriendlyByteBuf buf) {}

    public static FlameSwingPacket decode(FriendlyByteBuf buf) {
        return new FlameSwingPacket();
    }

    public static void handle(FlameSwingPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp != null) FlameSlashEvents.trigger(sp, InteractionHand.MAIN_HAND);
        });
        c.setPacketHandled(true);
    }
}
