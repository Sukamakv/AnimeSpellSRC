package com.example.jjkspells.capri;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What Capri remembers about a player. Kept in the player's persistent data, which survives death and relogging. */
public final class CapriData {
    private static final String KEY = "jjk_capri";

    private CapriData() {
    }

    public static CompoundTag tag(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) {
            persisted.put(KEY, new CompoundTag());
        }
        return persisted.getCompound(KEY);
    }

    public static boolean autoCast(Player p) {
        return tag(p).getBoolean("auto");
    }

    public static void setAutoCast(Player p, boolean on) {
        tag(p).putBoolean("auto", on);
    }

    public static List<String> spells(Player p) {
        List<String> out = new ArrayList<>();
        ListTag l = tag(p).getList("spells", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    public static void setSpells(Player p, List<String> ids) {
        ListTag l = new ListTag();
        for (String s : ids) {
            l.add(StringTag.valueOf(s));
        }
        tag(p).put("spells", l);
    }

    public static Map<String, Integer> attrs(Player p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CompoundTag c = tag(p).getCompound("attr");
        for (String k : c.getAllKeys()) {
            out.put(k, c.getInt(k));
        }
        return out;
    }

    public static void setAttr(Player p, String id, int percent) {
        CompoundTag t = tag(p);
        if (!t.contains("attr", Tag.TAG_COMPOUND)) {
            t.put("attr", new CompoundTag());
        }
        CompoundTag c = t.getCompound("attr");
        if (percent >= 100) {
            c.remove(id);
        } else {
            c.putInt(id, percent);
        }
    }

    public static void clearAttrs(Player p) {
        tag(p).put("attr", new CompoundTag());
    }

    /** Minimum ticks between two auto-casts of the same spell (on top of its own cooldown). 0 = no extra wait. */
    public static Map<String, Integer> intervals(Player p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CompoundTag c = tag(p).getCompound("ival");
        for (String k : c.getAllKeys()) {
            out.put(k, c.getInt(k));
        }
        return out;
    }

    public static void setInterval(Player p, String id, int ticks) {
        CompoundTag t = tag(p);
        if (!t.contains("ival", Tag.TAG_COMPOUND)) {
            t.put("ival", new CompoundTag());
        }
        if (ticks <= 0) {
            t.getCompound("ival").remove(id);
        } else {
            t.getCompound("ival").putInt(id, ticks);
        }
    }

    /** Distance (blocks) at which a lock drops by itself; chosen by the player, 10..300, default 90. */
    public static int breakDist(Player p) {
        CompoundTag t = tag(p);
        return t.contains("brk") ? Math.max(10, Math.min(300, t.getInt("brk"))) : 90;
    }

    public static void setBreakDist(Player p, int blocks) {
        tag(p).putInt("brk", Math.max(10, Math.min(300, blocks)));
    }
}
