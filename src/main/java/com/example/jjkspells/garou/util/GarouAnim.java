package com.example.jjkspells.garou.util;

import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;

/**
 * Finds one of Iron's built-in player animations by field name at runtime, so a name missing from a given Iron's
 * version can never break the build (you just get null and the spell keeps its default animation).
 * Own copy for the Garou package so characters stay independent of each other.
 */
public final class GarouAnim {
    private GarouAnim() {}

    public static AnimationHolder find(String... names) {
        Class<?> c = SpellAnimations.class;
        for (String n : names) {
            try {
                Object o = c.getField(n).get(null);
                if (o instanceof AnimationHolder h) return h;
            } catch (ReflectiveOperationException ignored) {
                // not in this version, try the next name
            }
        }
        return null;
    }
}
