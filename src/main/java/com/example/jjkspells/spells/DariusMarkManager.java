package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Keeps red marks above heads and red outlines around bodies alive for a while.
 * A mark is the crimson sigil above a target (Crippling Strike's hit, Noxian Guillotine's lock-on);
 * an outline is a red wireframe around the body (Apprehend's hooked enemies).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class DariusMarkManager {
    public static final int MARK = 0;
    public static final int OUTLINE = 1;

    private static class Entry {
        final ServerLevel level;
        final UUID id;
        final int type;
        int ticksLeft;

        Entry(ServerLevel level, UUID id, int type, int ticks) {
            this.level = level;
            this.id = id;
            this.type = type;
            this.ticksLeft = ticks;
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();

    public static void mark(ServerLevel level, LivingEntity e, int ticks, int type) {
        ACTIVE.removeIf(en -> en.id.equals(e.getUUID()) && en.type == type);
        ACTIVE.add(new Entry(level, e.getUUID(), type, ticks));
    }

    /** Removes every mark and outline on this entity. */
    public static void clear(UUID id) {
        ACTIVE.removeIf(en -> en.id.equals(id));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry en = it.next();
            Entity e = en.level.getEntity(en.id);
            if (e == null || !e.isAlive() || en.ticksLeft-- <= 0) {
                it.remove();
                continue;
            }
            if (en.type == MARK) {
                double bob = Math.sin((en.level.getGameTime() + en.id.hashCode()) * 0.35) * 0.08;
                FX.one(en.level, ModParticles.DR_MARK.get(), new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.6 + bob, e.getZ()), Vec3.ZERO);
            } else {
                outline(en, e);
            }
        }
    }

    /** The 12 edges of the entity's bounding box, as thin red lines. */
    private static void outline(Entry en, Entity e) {
        AABB b = e.getBoundingBox().inflate(0.08);
        double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        ServerLevel l = en.level;
        for (double y : new double[]{y0, y1}) {
            line(l, new Vec3(x0, y, z0), new Vec3(dx, 0, 0));
            line(l, new Vec3(x0, y, z1), new Vec3(dx, 0, 0));
            line(l, new Vec3(x0, y, z0), new Vec3(0, 0, dz));
            line(l, new Vec3(x1, y, z0), new Vec3(0, 0, dz));
        }
        line(l, new Vec3(x0, y0, z0), new Vec3(0, dy, 0));
        line(l, new Vec3(x1, y0, z0), new Vec3(0, dy, 0));
        line(l, new Vec3(x0, y0, z1), new Vec3(0, dy, 0));
        line(l, new Vec3(x1, y0, z1), new Vec3(0, dy, 0));
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 vec) {
        FX.one(level, ModParticles.DR_LINE.get(), from, vec);
    }
}
