package com.example.jjkspells.irontomb;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.entity.IrontombEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Irontomb arena dimension (jjk_spells:irontomb_arena): an endless black plateau under a nebula sky, drifting ash and
 * crimson fog, no natural mobs. Its definition is plain datapack JSON in data/jjk_spells; this class only moves people in
 * and out and places the boss.
 */
public final class IrontombArena {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, JJKSpells.id("irontomb_arena"));
    /** Surface height of the plateau (4 layers + bedrock = y 0..5, the top block is y 5, so you stand at 6). */
    public static final double FLOOR_Y = 6.0;
    /** The boss stands at the origin, challengers arrive this far south of it, facing north. */
    public static final double ARRIVAL_Z = 48.0;
    private static final String RET = "jjk_irontomb_return";

    private IrontombArena() {
    }

    public static ServerLevel level(ServerPlayer p) {
        return p.server.getLevel(KEY);
    }

    public static boolean isIn(ServerPlayer p) {
        return p.level().dimension().equals(KEY);
    }

    /** Teleports the player into the arena, remembering where they came from. Returns false if the dimension is missing. */
    public static boolean enter(ServerPlayer p) {
        ServerLevel arena = level(p);
        if (arena == null) {
            return false;
        }
        if (!isIn(p)) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", p.level().dimension().location().toString());
            t.putDouble("x", p.getX());
            t.putDouble("y", p.getY());
            t.putDouble("z", p.getZ());
            p.getPersistentData().put(RET, t);
        }
        p.teleportTo(arena, 0.5, FLOOR_Y, ARRIVAL_Z, 180f, 0f);
        return true;
    }

    /** Brings the player back to where they entered from (or the overworld spawn). */
    public static boolean leave(ServerPlayer p) {
        if (!isIn(p)) {
            return false;
        }
        CompoundTag t = p.getPersistentData().getCompound(RET);
        ServerLevel to = p.server.overworld();
        double x, y, z;
        if (t.contains("dim")) {
            ResourceKey<Level> k = ResourceKey.create(Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(t.getString("dim")));
            ServerLevel l = p.server.getLevel(k);
            if (l != null) {
                to = l;
            }
            x = t.getDouble("x");
            y = t.getDouble("y");
            z = t.getDouble("z");
        } else {
            var sp = to.getSharedSpawnPos();
            x = sp.getX() + 0.5;
            y = sp.getY();
            z = sp.getZ() + 0.5;
        }
        p.teleportTo(to, x, y, z, p.getYRot(), p.getXRot());
        return true;
    }

    /** Makes sure the arena holds an Irontomb at its centre. Returns true if one was created. */
    public static boolean ensureBoss(ServerLevel arena) {
        AABB box = new AABB(-220, 0, -220, 220, 256, 220);
        if (!arena.getEntitiesOfClass(IrontombEntity.class, box).isEmpty()) {
            return false;
        }
        IrontombEntity b = IrontombTypes.IRONTOMB.create(arena);
        if (b == null) {
            return false;
        }
        b.moveTo(0.5, FLOOR_Y, 0.5, 0f, 0f);
        arena.addFreshEntity(b);
        return true;
    }
}
