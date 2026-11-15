package com.example.jjkspells.learner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * config/jjk_spells-skill_learner_overrides.json: everything edited in the Divine Skill Learner's edit mode.
 * It is applied the moment it is written and re-read whenever the file changes on disk, so no restart is needed.
 * Wins over jjk_spells-skill_learner.toml.
 */
public final class LearnerOverrides {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<String>> MATERIALS = new HashMap<>();
    private static final Map<String, Integer> LEVELS = new HashMap<>();
    private static final Map<String, String> BOSSES = new HashMap<>();
    private static long loadedStamp = Long.MIN_VALUE, lastCheck = 0;

    private LearnerOverrides() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("jjk_spells-skill_learner_overrides.json");
    }

    private static synchronized void sync() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000 && loadedStamp != Long.MIN_VALUE) return;   // look at the file at most once a second
        lastCheck = now;
        Path p = file();
        long stamp;
        try {
            stamp = Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : -1;
        } catch (IOException e) {
            return;
        }
        if (stamp == loadedStamp) return;
        loadedStamp = stamp;
        MATERIALS.clear();
        LEVELS.clear();
        BOSSES.clear();
        if (stamp < 0) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            if (root.has("materials")) {
                for (var en : root.getAsJsonObject("materials").entrySet()) {
                    List<String> l = new ArrayList<>();
                    en.getValue().getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                    MATERIALS.put(en.getKey(), l);
                }
            }
            if (root.has("levels")) root.getAsJsonObject("levels").entrySet().forEach(en -> LEVELS.put(en.getKey(), en.getValue().getAsInt()));
            if (root.has("bosses")) root.getAsJsonObject("bosses").entrySet().forEach(en -> BOSSES.put(en.getKey(), en.getValue().getAsString()));
        } catch (Exception e) {
            LogUtils.getLogger().error("Divine Skill Learner: could not read {}", p, e);
        }
    }

    private static synchronized void write() {
        JsonObject root = new JsonObject();
        JsonObject m = new JsonObject();
        MATERIALS.forEach((k, v) -> {
            var arr = new com.google.gson.JsonArray();
            v.forEach(arr::add);
            m.add(k, arr);
        });
        JsonObject lv = new JsonObject();
        LEVELS.forEach(lv::addProperty);
        JsonObject bs = new JsonObject();
        BOSSES.forEach(bs::addProperty);
        root.add("materials", m);
        root.add("levels", lv);
        root.add("bosses", bs);
        try {
            Path p = file();
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            loadedStamp = Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            LogUtils.getLogger().error("Divine Skill Learner: could not save overrides", e);
        }
    }

    public static synchronized List<String> materials(String key) {
        sync();
        List<String> l = MATERIALS.get(key);
        return l == null ? null : new ArrayList<>(l);
    }

    public static synchronized Integer level(String key) {
        sync();
        return LEVELS.get(key);
    }

    /** null = no override (use the default); "" = no boss needed. */
    public static synchronized String boss(String group) {
        sync();
        return BOSSES.get(group);
    }

    public static synchronized void setMaterials(String key, List<String> list) {
        sync();
        MATERIALS.put(key, new ArrayList<>(list));
        write();
    }

    public static synchronized void setLevel(String key, int level) {
        sync();
        LEVELS.put(key, level);
        write();
    }

    public static synchronized void setBoss(String group, String boss) {
        sync();
        BOSSES.put(group, boss);
        write();
    }
}
