package com.example.jjkspells.capri;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Capri's packets. Everything gameplay related is decided on the server; the client only asks and displays. */
public final class CapriNet {
    public static final int OP_AUTOCAST = 1, OP_SET_SPELLS = 2, OP_SET_ATTR = 3, OP_LOCK = 4, OP_SYNC = 5, OP_RESET_ATTRS = 6, OP_SET_INTERVAL = 7, OP_SET_BREAK = 8, OP_ONS = 9, OP_ONS_MODE = 10;

    private CapriNet() {
    }

    public static void register(SimpleChannel ch, int firstId) {
        ch.registerMessage(firstId, Action.class, Action::encode, Action::decode, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        ch.registerMessage(firstId + 1, Sync.class, Sync::encode, Sync::decode, Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(firstId + 2, Say.class, Say::encode, Say::decode, Say::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(firstId + 3, Target.class, Target::encode, Target::decode, Target::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(firstId + 4, Ons.class, Ons::encode, Ons::decode, Ons::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        com.example.jjkspells.network.ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    // ------------------------------------------------------------------ client -> server

    public static final class Action {
        public final int op, a;
        public final String s;
        public final List<String> list;

        public Action(int op, String s, int a, List<String> list) {
            this.op = op;
            this.s = s == null ? "" : s;
            this.a = a;
            this.list = list == null ? List.of() : list;
        }

        public static Action of(int op) {
            return new Action(op, "", 0, null);
        }

        public static void encode(Action m, FriendlyByteBuf b) {
            b.writeByte(m.op);
            b.writeUtf(m.s, 200);
            b.writeInt(m.a);
            b.writeVarInt(m.list.size());
            for (String x : m.list) {
                b.writeUtf(x, 200);
            }
        }

        public static Action decode(FriendlyByteBuf b) {
            int op = b.readByte(), n0;
            String s = b.readUtf(200);
            int a = b.readInt();
            n0 = Math.min(16, b.readVarInt());
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n0; i++) {
                l.add(b.readUtf(200));
            }
            return new Action(op, s, a, l);
        }

        public static void handle(Action m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) {
                    CapriServer.handle(p, m);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ server -> client

    /** One spell Capri could use: status 0 ready, 1 cooldown, 2 not enough mana, 3 relic missing, 4 casting. */
    public record Pool(String id, int level, int mana, int cooldown, int status, String note) {
    }

    public static final class Sync {
        public final boolean linked, auto;
        public final List<String> spells;
        public final Map<String, Integer> attrs, intervals;
        public final List<Pool> pool;

        public Sync(boolean linked, boolean auto, List<String> spells, Map<String, Integer> attrs, Map<String, Integer> intervals, List<Pool> pool) {
            this.linked = linked;
            this.auto = auto;
            this.spells = spells;
            this.attrs = attrs;
            this.intervals = intervals;
            this.pool = pool;
        }

        public static void encode(Sync m, FriendlyByteBuf b) {
            b.writeBoolean(m.linked);
            b.writeBoolean(m.auto);
            b.writeVarInt(m.spells.size());
            for (String s : m.spells) {
                b.writeUtf(s, 200);
            }
            b.writeVarInt(m.attrs.size());
            for (Map.Entry<String, Integer> e : m.attrs.entrySet()) {
                b.writeUtf(e.getKey(), 200);
                b.writeVarInt(e.getValue());
            }
            b.writeVarInt(m.intervals.size());
            for (Map.Entry<String, Integer> e : m.intervals.entrySet()) {
                b.writeUtf(e.getKey(), 200);
                b.writeVarInt(e.getValue());
            }
            b.writeVarInt(m.pool.size());
            for (Pool p : m.pool) {
                b.writeUtf(p.id(), 200);
                b.writeVarInt(p.level());
                b.writeVarInt(p.mana());
                b.writeVarInt(p.cooldown());
                b.writeByte(p.status());
                b.writeUtf(p.note(), 200);
            }
        }

        public static Sync decode(FriendlyByteBuf b) {
            boolean linked = b.readBoolean(), auto = b.readBoolean();
            int n = b.readVarInt();
            List<String> sp = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                sp.add(b.readUtf(200));
            }
            n = b.readVarInt();
            Map<String, Integer> at = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                at.put(b.readUtf(200), b.readVarInt());
            }
            n = b.readVarInt();
            Map<String, Integer> iv = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                iv.put(b.readUtf(200), b.readVarInt());
            }
            n = b.readVarInt();
            List<Pool> pool = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                pool.add(new Pool(b.readUtf(200), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByte(), b.readUtf(200)));
            }
            return new Sync(linked, auto, sp, at, iv, pool);
        }

        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.capri.client.CapriClientState.apply(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** The server tells Capri something happened (she decides what to say). */
    public static final class Say {
        public final String event, arg;

        public Say(String event, String arg) {
            this.event = event;
            this.arg = arg == null ? "" : arg;
        }

        public static void encode(Say m, FriendlyByteBuf b) {
            b.writeUtf(m.event, 100);
            b.writeUtf(m.arg, 200);
        }

        public static Say decode(FriendlyByteBuf b) {
            return new Say(b.readUtf(100), b.readUtf(200));
        }

        public static void handle(Say m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.capri.client.CapriDialogue.fromServer(m.event, m.arg)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Who the locked target is attacking: an entity id, -1 nobody, -2 unknown. */
    public static final class Target {
        public final int locked, aggro;

        public Target(int locked, int aggro) {
            this.locked = locked;
            this.aggro = aggro;
        }

        public static void encode(Target m, FriendlyByteBuf b) {
            b.writeInt(m.locked);
            b.writeInt(m.aggro);
        }

        public static Target decode(FriendlyByteBuf b) {
            return new Target(b.readInt(), b.readInt());
        }

        public static void handle(Target m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.capri.client.CapriClientState.target(m.locked, m.aggro)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Onslaught state: on/off, mode (0 psycho, 1 strategic) and ticks left. */
    public static final class Ons {
        public final boolean on;
        public final int mode, left;

        public Ons(boolean on, int mode, int left) {
            this.on = on;
            this.mode = mode;
            this.left = left;
        }

        public static void encode(Ons m, FriendlyByteBuf b) {
            b.writeBoolean(m.on);
            b.writeByte(m.mode);
            b.writeVarInt(m.left);
        }

        public static Ons decode(FriendlyByteBuf b) {
            return new Ons(b.readBoolean(), b.readByte(), b.readVarInt());
        }

        public static void handle(Ons m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.capri.client.CapriOnslaught.apply(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
