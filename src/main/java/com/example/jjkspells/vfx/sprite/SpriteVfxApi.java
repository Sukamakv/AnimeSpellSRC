package com.example.jjkspells.vfx.sprite;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side entry point for sprite effects.
 * <pre>
 *   SpriteVfxApi.play(level, "fire_burst_fire", pos).scale(2.5f).send();
 *   SpriteVfxApi.play(level, SpriteFx.id...)...
 * </pre>
 * Nothing is sent immediately: effects are queued and flushed once per server tick as ONE packet per nearby player, instead of
 * one packet per effect (spells used to spawn dozens of particle packets per tick). Safe to call from any spell code on the
 * server thread. Common code, no client classes.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class SpriteVfxApi {
    private SpriteVfxApi() {}

    private static final Map<ServerLevel, List<SpriteVfxPacket.Entry>> QUEUE = new HashMap<>();
    /** Per tick safety valve so a runaway spell cannot flood players. */
    private static final int MAX_QUEUED_PER_LEVEL = 600;

    public static Builder play(ServerLevel level, String name, Vec3 pos) {
        return new Builder(level, JJKSpells.id(name), pos.x, pos.y, pos.z);
    }

    public static Builder play(ServerLevel level, String name, double x, double y, double z) {
        return new Builder(level, JJKSpells.id(name), x, y, z);
    }

    public static Builder play(ServerLevel level, ResourceLocation id, double x, double y, double z) {
        return new Builder(level, id, x, y, z);
    }

    public static final class Builder {
        private final ServerLevel level;
        private final SpriteVfxPacket.Entry e = new SpriteVfxPacket.Entry();

        private Builder(ServerLevel level, ResourceLocation id, double x, double y, double z) {
            this.level = level;
            e.id = id;
            e.x = x;
            e.y = y;
            e.z = z;
        }

        public Builder scale(float s) {
            e.scale = s;
            e.flags |= SpriteVfxPacket.F_SCALE;
            return this;
        }

        public Builder velocity(double x, double y, double z) {
            e.vx = (float) x;
            e.vy = (float) y;
            e.vz = (float) z;
            e.flags |= SpriteVfxPacket.F_VEL;
            return this;
        }

        /** Direction for beams and lightning, plane normal for slashes and circles. */
        public Builder dir(Vec3 d) {
            return dir(d.x, d.y, d.z);
        }

        public Builder dir(double x, double y, double z) {
            e.dx = (float) x;
            e.dy = (float) y;
            e.dz = (float) z;
            e.flags |= SpriteVfxPacket.F_DIR;
            return this;
        }

        public Builder length(float blocks) {
            e.length = blocks;
            e.flags |= SpriteVfxPacket.F_LENGTH;
            return this;
        }

        public Builder life(int ticks) {
            e.life = ticks;
            e.flags |= SpriteVfxPacket.F_LIFE;
            return this;
        }

        public Builder tint(float r, float g, float b) {
            e.tr = r;
            e.tg = g;
            e.tb = b;
            e.flags |= SpriteVfxPacket.F_TINT;
            return this;
        }

        public Builder roll(float degrees) {
            e.roll = degrees;
            e.flags |= SpriteVfxPacket.F_ROLL;
            return this;
        }

        /** The effect follows this entity (position + yOffset) until it ends or the entity is gone. */
        public Builder attach(Entity entity, float yOffset) {
            e.attachId = entity.getId();
            e.attachYOff = yOffset;
            e.flags |= SpriteVfxPacket.F_ATTACH;
            return this;
        }

        /** Marks the effect as purely decorative: clients on low 'decorDensity' may skip it. */
        public Builder decor() {
            e.flags |= SpriteVfxPacket.F_DECOR;
            return this;
        }

        /** Only players within this many blocks receive it (default 96). */
        public Builder radius(double blocks) {
            e.radius = (float) blocks;
            return this;
        }

        public void send() {
            List<SpriteVfxPacket.Entry> q = QUEUE.computeIfAbsent(level, k -> new ArrayList<>());
            if (q.size() < MAX_QUEUED_PER_LEVEL) q.add(e);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || QUEUE.isEmpty()) return;
        for (Map.Entry<ServerLevel, List<SpriteVfxPacket.Entry>> en : QUEUE.entrySet()) {
            List<SpriteVfxPacket.Entry> all = en.getValue();
            if (all.isEmpty()) continue;
            for (ServerPlayer sp : en.getKey().players()) {
                List<SpriteVfxPacket.Entry> mine = null;
                for (int i = 0, n = all.size(); i < n; i++) {
                    SpriteVfxPacket.Entry e = all.get(i);
                    double dx = sp.getX() - e.x, dy = sp.getY() - e.y, dz = sp.getZ() - e.z;
                    if (dx * dx + dy * dy + dz * dz <= (double) e.radius * e.radius) {
                        if (mine == null) mine = new ArrayList<>();
                        mine.add(e);
                        if (mine.size() >= SpriteVfxPacket.MAX_PER_PACKET) {
                            send(sp, mine);
                            mine = null;
                        }
                    }
                }
                if (mine != null && !mine.isEmpty()) send(sp, mine);
            }
        }
        QUEUE.clear();
    }

    private static void send(ServerPlayer sp, List<SpriteVfxPacket.Entry> list) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SpriteVfxPacket(list));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        QUEUE.clear();
    }
}
