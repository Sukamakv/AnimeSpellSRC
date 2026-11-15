package com.example.jjkspells.irontomb;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.client.ConfiguratorScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Two tiny packets for the configurator item: server -> client "open with these values", client -> server "apply these values". */
public final class IrontombNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(JJKSpells.id("irontomb_cfg"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean done;

    private IrontombNet() {
    }

    public static void init() {
        if (done) {
            return;
        }
        done = true;
        CHANNEL.registerMessage(0, OpenMsg.class, OpenMsg::encode, OpenMsg::decode, OpenMsg::handle);
        CHANNEL.registerMessage(1, ApplyMsg.class, ApplyMsg::encode, ApplyMsg::decode, ApplyMsg::handle);
    }

    /** Operators, or the owner of a single-player world, may change the boss. */
    public static boolean allowed(ServerPlayer p) {
        return p.hasPermissions(2) || (p.getServer() != null && p.getServer().isSingleplayerOwner(p.getGameProfile()));
    }

    private static void write(FriendlyByteBuf b, Map<String, Double> m) {
        b.writeVarInt(m.size());
        m.forEach((k, v) -> {
            b.writeUtf(k, 64);
            b.writeDouble(v);
        });
    }

    private static Map<String, Double> read(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 256);
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = b.readUtf(64);
            m.put(k, b.readDouble());
        }
        return m;
    }

    public static final class OpenMsg {
        final Map<String, Double> values;

        public OpenMsg(Map<String, Double> values) {
            this.values = values;
        }

        static void encode(OpenMsg m, FriendlyByteBuf b) {
            write(b, m.values);
        }

        static OpenMsg decode(FriendlyByteBuf b) {
            return new OpenMsg(read(b));
        }

        static void handle(OpenMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ConfiguratorScreen.open(m.values)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class ApplyMsg {
        final Map<String, Double> values;

        public ApplyMsg(Map<String, Double> values) {
            this.values = values;
        }

        static void encode(ApplyMsg m, FriendlyByteBuf b) {
            write(b, m.values);
        }

        static ApplyMsg decode(FriendlyByteBuf b) {
            return new ApplyMsg(read(b));
        }

        static void handle(ApplyMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null && allowed(p)) {
                    IrontombBalance.setValues(m.values);
                    p.displayClientMessage(Component.literal("Irontomb configuration applied and saved."), true);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
