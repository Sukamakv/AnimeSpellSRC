package com.example.jjkspells.capri;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The only place that touches Iron's Spells 'n Spellbooks internals that are not used elsewhere in this mod. It goes through
 * reflection, so a changed signature fails at runtime with a log line (and "no spells found") instead of breaking the build.
 */
public final class IronsBridge {
    private static final Logger LOG = LoggerFactory.getLogger("jjk_spells");
    private static boolean warnedRead, warnedCast;

    private IronsBridge() {
    }

    /** A spell found in an item the player carries. */
    public record Found(AbstractSpell spell, int level, ItemStack source) {
    }

    private static Object first(Object o, String... names) {
        for (String n : names) {
            try {
                Method m = o.getClass().getMethod(n);
                m.setAccessible(true);
                return m.invoke(o);
            } catch (Throwable ignored) {
                // try the next name
            }
        }
        return null;
    }

    /** The spells stored in a spellbook, sword or other spell container item (scrolls are single use and left out). */
    public static List<Found> spellsOf(ItemStack st) {
        return read(st, false);
    }

    /** Same, but scrolls are read too (the PRIME learner checks which scroll you hold). */
    public static List<Found> spellsOfAny(ItemStack st) {
        return read(st, true);
    }

    private static List<Found> read(ItemStack st, boolean includeScrolls) {
        List<Found> out = new ArrayList<>();
        if (st.isEmpty()) {
            return out;
        }
        try {
            String path = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem()));
            if (path.contains("scroll") && !includeScrolls) {
                return out;
            }
            Object cont = ISpellContainer.class.getMethod("get", ItemStack.class).invoke(null, st);
            if (cont == null) {
                return out;
            }
            Object arr = first(cont, "getActiveSpells", "getAllSpells");
            Iterable<?> it = arr instanceof Object[] a ? Arrays.asList(a) : arr instanceof Iterable<?> i ? i : List.of();
            for (Object d : it) {
                if (d == null) {
                    continue;
                }
                Object sp = first(d, "getSpell");
                Object lv = first(d, "getLevel");
                if (sp instanceof AbstractSpell as && lv instanceof Integer n && !String.valueOf(as.getSpellResource()).endsWith(":none")) {
                    out.add(new Found(as, n, st));
                }
            }
        } catch (Throwable t) {
            if (!warnedRead) {
                warnedRead = true;
                LOG.warn("Capri: could not read the spells of {}: {}", st, t.toString());
            }
        }
        return out;
    }

    /** Starts a normal cast through Iron's own pipeline (mana, cooldown, relic checks and animations all apply). */
    public static boolean cast(ServerPlayer sp, AbstractSpell spell, int level, ItemStack source, boolean fromCurio) {
        try {
            for (Method m : AbstractSpell.class.getMethods()) {
                if (!m.getName().equals("attemptInitiateCast")) {
                    continue;
                }
                Class<?>[] pt = m.getParameterTypes();
                Object[] args = new Object[pt.length];
                for (int i = 0; i < pt.length; i++) {
                    if (pt[i] == ItemStack.class) {
                        args[i] = source;
                    } else if (pt[i] == int.class) {
                        args[i] = level;
                    } else if (Level.class.isAssignableFrom(pt[i])) {
                        args[i] = sp.level();
                    } else if (pt[i].isAssignableFrom(ServerPlayer.class)) {
                        args[i] = sp;
                    } else if (pt[i] == CastSource.class) {
                        args[i] = CastSource.SPELLBOOK;
                    } else if (pt[i] == boolean.class) {
                        args[i] = true;
                    } else if (pt[i] == String.class) {
                        args[i] = fromCurio ? "spellbook" : "mainhand";
                    } else {
                        throw new IllegalStateException("unexpected parameter " + pt[i]);
                    }
                }
                Object r = m.invoke(spell, args);
                return !(r instanceof Boolean b) || b;
            }
        } catch (Throwable t) {
            if (!warnedCast) {
                warnedCast = true;
                LOG.warn("Capri: could not start a cast of {}: {}", spell.getSpellResource(), t.toString());
            }
        }
        return false;
    }
}
