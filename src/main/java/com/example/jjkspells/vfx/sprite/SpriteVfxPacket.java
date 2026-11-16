package com.example.jjkspells.vfx.sprite;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: a batch of sprite effects to start. One packet carries every effect a player should see this tick, and each
 * entry only writes the fields it uses (flags), so a plain burst is about 30 bytes.
 */
public class SpriteVfxPacket {
    public static final int F_DECOR = 1, F_SCALE = 2, F_VEL = 4, F_DIR = 8, F_LENGTH = 16, F_LIFE = 32, F_TINT = 64, F_ROLL = 128,
            F_ATTACH = 256, F_FOREIGN_NS = 512;
    public static final int MAX_PER_PACKET = 64;

    public static final class Entry {
        public ResourceLocation id;
        public double x, y, z;
        public int flags;
        public float scale = 1f, vx, vy, vz, dx, dy, dz, length, tr = 1f, tg = 1f, tb = 1f, roll, attachYOff;
        public int life, attachId = -1;
        /** Server side only, not sent: how far from the effect a player still gets it. */
        public transient float radius = 96f;
    }

    public final List<Entry> entries;

    public SpriteVfxPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public static void encode(SpriteVfxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            boolean foreign = !"jjk_spells".equals(e.id.getNamespace());
            int fl = e.flags | (foreign ? F_FOREIGN_NS : 0);
            buf.writeShort(fl);
            if (foreign) buf.writeResourceLocation(e.id);
            else buf.writeUtf(e.id.getPath(), 64);
            buf.writeFloat((float) e.x).writeFloat((float) e.y).writeFloat((float) e.z);
            if ((fl & F_SCALE) != 0) buf.writeFloat(e.scale);
            if ((fl & F_VEL) != 0) buf.writeFloat(e.vx).writeFloat(e.vy).writeFloat(e.vz);
            if ((fl & F_DIR) != 0) buf.writeFloat(e.dx).writeFloat(e.dy).writeFloat(e.dz);
            if ((fl & F_LENGTH) != 0) buf.writeFloat(e.length);
            if ((fl & F_LIFE) != 0) buf.writeVarInt(e.life);
            if ((fl & F_TINT) != 0) buf.writeFloat(e.tr).writeFloat(e.tg).writeFloat(e.tb);
            if ((fl & F_ROLL) != 0) buf.writeFloat(e.roll);
            if ((fl & F_ATTACH) != 0) buf.writeVarInt(e.attachId).writeFloat(e.attachYOff);
        }
    }

    public static SpriteVfxPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Entry e = new Entry();
            int fl = buf.readShort() & 0xFFFF;
            e.flags = fl;
            e.id = (fl & F_FOREIGN_NS) != 0 ? buf.readResourceLocation() : new ResourceLocation("jjk_spells", buf.readUtf(64));
            e.x = buf.readFloat();
            e.y = buf.readFloat();
            e.z = buf.readFloat();
            if ((fl & F_SCALE) != 0) e.scale = buf.readFloat();
            if ((fl & F_VEL) != 0) {
                e.vx = buf.readFloat();
                e.vy = buf.readFloat();
                e.vz = buf.readFloat();
            }
            if ((fl & F_DIR) != 0) {
                e.dx = buf.readFloat();
                e.dy = buf.readFloat();
                e.dz = buf.readFloat();
            }
            if ((fl & F_LENGTH) != 0) e.length = buf.readFloat();
            if ((fl & F_LIFE) != 0) e.life = buf.readVarInt();
            if ((fl & F_TINT) != 0) {
                e.tr = buf.readFloat();
                e.tg = buf.readFloat();
                e.tb = buf.readFloat();
            }
            if ((fl & F_ROLL) != 0) e.roll = buf.readFloat();
            if ((fl & F_ATTACH) != 0) {
                e.attachId = buf.readVarInt();
                e.attachYOff = buf.readFloat();
            }
            list.add(e);
        }
        return new SpriteVfxPacket(list);
    }

    public static void handle(SpriteVfxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpriteVfxManager.receive(p.entries)));
        ctx.get().setPacketHandled(true);
    }
}
