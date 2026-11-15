package com.example.jjkspells.learner;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * config/jjk_spells-skill_learner.toml: what the Divine Skill Learner asks for each spell scroll.
 * Each spell has a "materials" list of "item*count" strings (use "#tag:name*count" for an item tag) and the scroll "level".
 */
public final class LearnerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_FREE;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_BOSS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_BASE_SCROLLS;
    public static final ForgeConfigSpec.DoubleValue CREDIT_RADIUS;
    private static final Map<String, ForgeConfigSpec.ConfigValue<List<? extends String>>> MATERIALS = new HashMap<>();
    private static final Map<String, ForgeConfigSpec.IntValue> LEVELS = new HashMap<>();
    private static final Map<String, List<String>> DEFAULTS = new HashMap<>();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Divine Skill Learner").push("general");
        CREATIVE_FREE = b.comment("Creative-mode players learn every spell without paying materials.").define("creative_players_free", false);
        REQUIRE_BOSS = b.comment("A section is locked until the player has killed its boss (see SkillGroups / the learner's edit mode).").define("require_boss_kill", true);
        REQUIRE_BASE_SCROLLS = b.comment("PRIME spells: the scrolls of the spells they evolve from must be handed over to learn them (they are consumed). false = those requirements are ignored.").define("require_base_scrolls", true);
        CREDIT_RADIUS = b.comment("0 = only the player who lands the killing blow gets credit. Above 0: every player within this many blocks of the boss also gets it.").defineInRange("boss_credit_radius", 0.0, 0.0, 512.0);
        b.pop();
        b.comment("Materials needed per spell scroll. Format: \"namespace:item*count\" or \"#namespace:tag*count\". Empty list = free.",
                "A wrong item id makes that spell impossible to learn until fixed. Use /spellids to see spell ids.").push("spells");
        for (int gi = 0; gi < SkillGroups.ALL.size(); gi++) {
            SkillGroups.Group g = SkillGroups.ALL.get(gi);
            for (int si = 0; si < g.spells().size(); si++) {
                String sp = g.spells().get(si);
                String id = key(g, sp);
                List<String> def = defaults(g, si);
                DEFAULTS.put(id, def);
                b.push(g.id()).push(sp);
                MATERIALS.put(id, b.comment(g.title() + " - materials").defineListAllowEmpty(List.of("materials"), () -> def, o -> o instanceof String));
                LEVELS.put(id, b.comment("Spell level of the scroll that is given (clamped to the spell's own range)").defineInRange("level", 1, 1, 100));
                b.pop().pop();
            }
        }
        b.pop();
        SPEC = b.build();
    }

    /** Later spells of a section cost a bit more than the first. */
    private static List<String> defaults(SkillGroups.Group g, int index) {
        List<String> out = new ArrayList<>();
        if (SkillGroups.isPrime(g)) {
            for (String base : SkillGroups.PRIME_BASE.getOrDefault(g.spells().get(index), List.of())) out.add("scroll:jjk_spells:" + base + "*1");
        }
        double mult = g.id().equals("relics") || g.id().equals("vergil") ? 1.0 : 1.0 + 0.35 * index;
        for (String m : g.defaultMaterials()) {
            int star = m.lastIndexOf('*');
            int base = Integer.parseInt(m.substring(star + 1));
            int n = Math.max(1, (int) Math.round(base * mult));
            out.add(m.substring(0, star) + "*" + n);
        }
        return out;
    }

    private static String key(SkillGroups.Group g, String spell) {
        return g.id() + "/" + spell;
    }

    public static List<Mat> materials(int group, String spell) {
        String spellId = key(SkillGroups.ALL.get(group), spell);
        List<String> raw;
        List<String> ov = LearnerOverrides.materials(spellId);
        if (ov != null) {
            List<Mat> res = new ArrayList<>();
            for (String s : ov) {
                if (s.trim().startsWith("scroll:") && !requireBaseScrolls()) continue;
                res.add(Mat.parse(s));
            }
            return res;
        }
        try {
            raw = new ArrayList<>(MATERIALS.get(spellId).get());
        } catch (RuntimeException e) {   // config not loaded yet
            raw = DEFAULTS.getOrDefault(spellId, List.of());
        }
        List<Mat> out = new ArrayList<>();
        for (String s : raw) {
            if (s.trim().startsWith("scroll:") && !requireBaseScrolls()) continue;
            out.add(Mat.parse(s));
        }
        return out;
    }

    private static boolean requireBaseScrolls() {
        try {
            return REQUIRE_BASE_SCROLLS.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Saves a new material list for one spell. Applies at once and is written to the overrides file (no restart needed). */
    public static void setMaterials(int group, String spell, List<String> list) {
        LearnerOverrides.setMaterials(key(SkillGroups.ALL.get(group), spell), list);
    }

    public static void setLevel(int group, String spell, int level) {
        LearnerOverrides.setLevel(key(SkillGroups.ALL.get(group), spell), Math.max(1, Math.min(100, level)));
    }

    /** The boss entity id a section needs, or "" for none. */
    public static String boss(int group) {
        String id = SkillGroups.ALL.get(group).id();
        String ov = LearnerOverrides.boss(id);
        if (ov != null) return ov;
        return SkillGroups.DEFAULT_BOSS.getOrDefault(id, "");
    }

    public static void setBoss(int group, String boss) {
        LearnerOverrides.setBoss(SkillGroups.ALL.get(group).id(), boss == null ? "" : boss.trim());
    }

    public static int level(int group, String spell) {
        String spellId = key(SkillGroups.ALL.get(group), spell);
        Integer ov = LearnerOverrides.level(spellId);
        if (ov != null) return ov;
        try {
            return LEVELS.get(spellId).get();
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private LearnerConfig() {
    }
}
