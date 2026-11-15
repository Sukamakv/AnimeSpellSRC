package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.*;

/**
 * Finds one of Iron's built-in player animations by name (e.g. "SLASH_ANIMATION"). Looked up at runtime so that a
 * name missing from a particular Iron's version can never break the build: you just get {@code null} and the
 * spell keeps its default cast animation.
 */
public final class SpellAnimLookup {
    private SpellAnimLookup() {}

    public static AnimationHolder find(String... names) {
        try {
            Class<?> c = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellAnimations");
            for (String n : names) {
                try {
                    Object o = c.getField(n).get(null);
                    if (o instanceof AnimationHolder h) return h;
                } catch (ReflectiveOperationException ignored) {
                    // not in this version, try the next name
                }
            }
        } catch (Throwable ignored) {
            // no animation class at all
        }
        return null;
    }
}
