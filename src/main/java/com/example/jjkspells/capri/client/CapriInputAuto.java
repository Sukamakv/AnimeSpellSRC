package com.example.jjkspells.capri.client;

import com.example.jjkspells.capri.CapriConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Input automation: Capri sets the game's own key states (the same thing your keyboard does), so it works exactly as long as the
 * game keeps ticking. If the window is unfocused and the game pauses (pause on lost focus), nothing can run: turning
 * "keep running when unfocused" on only stops the game from pausing itself. It cannot send keys to other programs and
 * never injects OS level input. Always stops on: screens opening, death, unlinking, the emergency key.
 */
public final class CapriInputAuto {
    public static final String[] ACTIONS = {"attack", "use", "jump", "sneak", "sprint", "forward"};
    public static final String[] MODES = {"HOLD", "REPEAT", "TOGGLE"};

    public static final class Row {
        public String action;
        public String mode;
        public int rate;
        public boolean active;
        int cd;

        Row(String a, String m, int r) {
            action = a;
            mode = m;
            rate = r;
        }

        String encode() {
            return action + "|" + mode + "|" + rate + "|false";
        }
    }

    public static final List<Row> ROWS = new ArrayList<>();
    /** Toggled by its own key: keeps the attack key (left click) held. */
    public static boolean holdLeft;
    private static boolean savedPause;
    private static boolean overridePause;

    private CapriInputAuto() {
    }

    public static void load() {
        ROWS.clear();
        for (String s : CapriConfig.INPUT_ROWS.get()) {
            String[] p = s.split("\\|");
            if (p.length >= 3) {
                try {
                    ROWS.add(new Row(p[0].toLowerCase(Locale.ROOT), p[1].toUpperCase(Locale.ROOT), Math.max(1, Integer.parseInt(p[2].trim()))));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (ROWS.isEmpty()) {
            ROWS.add(new Row("attack", "REPEAT", 4));
        }
    }

    public static void save() {
        List<String> out = new ArrayList<>();
        for (Row r : ROWS) {
            out.add(r.encode());
        }
        CapriConfig.INPUT_ROWS.set(out);
        CapriConfig.INPUT_ROWS.save();
    }

    public static boolean any() {
        if (holdLeft) {
            return true;
        }
        for (Row r : ROWS) {
            if (r.active) {
                return true;
            }
        }
        return false;
    }

    public static void stopAll(boolean announce) {
        boolean was = any();
        holdLeft = false;
        for (Row r : ROWS) {
            r.active = false;
            r.cd = 0;
        }
        release();
        restorePause();
        if (was && announce) {
            CapriDialogue.event("input_stop", "");
        }
    }

    private static KeyMapping key(Options o, String a) {
        return switch (a) {
            case "attack" -> o.keyAttack;
            case "use" -> o.keyUse;
            case "jump" -> o.keyJump;
            case "sneak" -> o.keyShift;
            case "sprint" -> o.keySprint;
            case "forward" -> o.keyUp;
            default -> null;
        };
    }

    private static void release() {
        Minecraft mc = Minecraft.getInstance();
        for (String a : ACTIONS) {
            KeyMapping k = key(mc.options, a);
            if (k != null) {
                k.setDown(false);
            }
        }
    }

    private static void restorePause() {
        if (overridePause) {
            Minecraft.getInstance().options.pauseOnLostFocus = savedPause;
            overridePause = false;
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (CapriOnslaught.on) {
            return;
        }
        if (!any()) {
            restorePause();
            return;
        }
        if (mc.player == null || mc.level == null || !CapriClientState.linked || mc.screen != null || !mc.player.isAlive()) {
            if (mc.player == null || !CapriClientState.linked || !mc.player.isAlive()) {
                stopAll(false);
            } else {
                release();
            }
            return;
        }
        if (CapriConfig.KEEP_RUNNING_UNFOCUSED.get() && !overridePause) {
            savedPause = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
            overridePause = true;
        } else if (!CapriConfig.KEEP_RUNNING_UNFOCUSED.get()) {
            restorePause();
        }
        if (holdLeft) {
            mc.options.keyAttack.setDown(true);
        }
        for (Row r : ROWS) {
            if (!r.active) {
                continue;
            }
            KeyMapping k = key(mc.options, r.action);
            if (k == null) {
                continue;
            }
            switch (r.mode) {
                case "HOLD" -> k.setDown(true);
                case "TOGGLE" -> {
                    if (r.cd-- <= 0) {
                        r.cd = Math.max(2, r.rate);
                        k.setDown(!k.isDown());
                    }
                }
                default -> { // REPEAT: a press every `rate` ticks
                    if (r.cd-- <= 0) {
                        r.cd = Math.max(1, r.rate);
                        k.setDown(true);
                        KeyMapping.click(k.getKey());
                    } else {
                        k.setDown(false);
                    }
                }
            }
        }
    }
}
