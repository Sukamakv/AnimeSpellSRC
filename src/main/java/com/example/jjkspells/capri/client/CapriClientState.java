package com.example.jjkspells.capri.client;

import com.example.jjkspells.capri.CapriNet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the client knows about Capri. Only ever written from server packets and the lock controller. */
public final class CapriClientState {
    public static boolean linked, auto;
    public static List<String> spells = new ArrayList<>();
    public static Map<String, Integer> attrs = new LinkedHashMap<>(), intervals = new LinkedHashMap<>();
    public static List<CapriNet.Pool> pool = new ArrayList<>();
    public static int lockId = -1, aggroId = -2;
    public static long linkedAt;

    private CapriClientState() {
    }

    public static void apply(CapriNet.Sync m) {
        if (m.linked && !linked) {
            linkedAt = System.currentTimeMillis();
        }
        if (!m.linked) {
            lockId = -1;
            aggroId = -2;
        }
        if (m.auto != auto && linked && m.linked) {
            CapriDialogue.event(m.auto ? "auto_on" : "auto_off", "");
        }
        linked = m.linked;
        auto = m.auto;
        spells = new ArrayList<>(m.spells);
        attrs = new LinkedHashMap<>(m.attrs);
        intervals = new LinkedHashMap<>(m.intervals);
        pool = new ArrayList<>(m.pool);
    }

    public static void target(int locked, int aggro) {
        if (locked < 0) {
            lockId = -1;
        }
        aggroId = aggro;
    }

    public static void clear() {
        linked = false;
        auto = false;
        spells = new ArrayList<>();
        attrs = new LinkedHashMap<>();
        intervals = new LinkedHashMap<>();
        pool = new ArrayList<>();
        lockId = -1;
        aggroId = -2;
    }
}
