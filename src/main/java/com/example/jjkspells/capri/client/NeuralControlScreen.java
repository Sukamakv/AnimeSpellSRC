package com.example.jjkspells.capri.client;

import com.example.jjkspells.capri.CapriConfig;
import com.example.jjkspells.capri.CapriNet;
import com.example.jjkspells.capri.CapriServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The Neural Control Panel: Spell AI, Targeting, Attributes, Input, Personality & Voice. Opened with the panel key (default Right Alt). */
public final class NeuralControlScreen extends Screen {
    private static final String[] TABS = {"Spell AI", "Targeting", "Attributes", "Input", "Personality & Voice"};
    private static final Set<String> NS = Set.of("minecraft", "irons_spellbooks", "apothic_attributes", "forge", "jjk_spells", "attributeslib");
    private static int tab;
    private int page;
    private int px, py, pw, ph;
    private long lastSync;

    public NeuralControlScreen() {
        super(Component.literal("Neural Control Panel"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 20, 360);
        ph = Math.min(height - 40, 236);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + 8;
        int tw = pw / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            SciButton tb = new SciButton(px + i * tw, py, tw - 1, 16, Component.literal(TABS[i]), b -> {
                tab = idx;
                page = 0;
                searchFocus = false;
                rebuild();
            });
            tb.accent = i == tab;
            addRenderableWidget(tb);
        }
        content();
        CapriClient.send(CapriNet.Action.of(CapriNet.OP_SYNC));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    /** Flat, angular, cyan-edged button. */
    private static final class SciButton extends Button {
        boolean accent;

        SciButton(int x, int y, int w, int h, Component msg, OnPress press) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = active && isHoveredOrFocused();
            int edge = !active ? 0x44566A : (accent || hov) ? CapriHud.ORANGE : CapriHud.CYAN;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, hov ? 0xF0182233 : accent ? 0xF02A1812 : 0xF00B1220);
            g.fill(x, y, x + w, y + 1, 0xFF000000 | edge);
            g.fill(x, y + h - 1, x + w, y + h, 0xFF000000 | edge);
            g.fill(x, y, x + 2, y + h, 0xFF000000 | edge);
            g.fill(x + w - 5, y + h - 1, x + w, y + h - 2, 0xFF000000 | CapriHud.ORANGE);
            var f = Minecraft.getInstance().font;
            String t = f.plainSubstrByWidth(getMessage().getString(), w - 8);
            g.drawString(f, t, x + 5, y + (h - 8) / 2, 0xFF000000 | (!active ? 0x7C8A9C : hov || accent ? 0xFFE6D8 : 0xD8F8FA), false);
        }
    }

    /** Shift = steps of 5. */
    private void stepOns(boolean range, int dir) {
        int d = dir * (hasShiftDown() ? 5 : 1);
        if (range) {
            CapriConfig.ONS_RANGE.set(Math.max(3, Math.min(75, CapriConfig.ONS_RANGE.get() + d)));
            CapriConfig.ONS_RANGE.save();
        } else {
            CapriConfig.ONS_CIRCLE.set(Math.max(2, Math.min(25, CapriConfig.ONS_CIRCLE.get() + d)));
            CapriConfig.ONS_CIRCLE.save();
        }
        rebuild();
    }

    private Button btn(int x, int y, int w, String label, Button.OnPress p) {
        return addRenderableWidget(new SciButton(x, y, w, 14, Component.literal(label), p));
    }

    private void content() {
        int x = px + 8, y = py + 24;
        switch (tab) {
            case 0 -> spellTab(x, y);
            case 1 -> targetTab(x, y);
            case 2 -> attrTab(x, y);
            case 3 -> inputTab(x, y);
            default -> voiceTab(x, y);
        }
    }

    // ------------------------------------------------------------------ tab 0

    private static String spellName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? id : Component.translatable("spell." + rl.getNamespace() + "." + rl.getPath()).getString();
    }

    private void spellTab(int x, int y) {
        btn(x, y, 150, "Auto-cast: " + (CapriClientState.auto ? "ON" : "OFF"), b -> CapriClient.send(new CapriNet.Action(CapriNet.OP_AUTOCAST, "", CapriClientState.auto ? 0 : 1, null)));
        // priority list: top is cast first; each spell has its own minimum wait between casts
        List<String> sel = CapriClientState.spells;
        for (int i = 0; i < sel.size() && i < 3; i++) {
            final int idx = i;
            final String id = sel.get(i);
            int iv = CapriClientState.intervals.getOrDefault(id, 0);
            int yy = y + 18 + i * 16;
            int lw = pw - 16 - 4 * 27;
            SciButton lab = new SciButton(x, yy, lw, 14, Component.literal("#" + (i + 1) + " " + spellName(id) + "  wait " + (iv == 0 ? "none" : String.format(Locale.ROOT, "%.1fs", iv / 20f))), b -> {
            });
            lab.active = false;
            addRenderableWidget(lab);
            Button up = btn(x + lw + 1, yy, 26, "^", b -> move(idx, -1));
            up.active = i > 0;
            Button dn = btn(x + lw + 28, yy, 26, "v", b -> move(idx, 1));
            dn.active = i < sel.size() - 1;
            btn(x + lw + 55, yy, 26, "-", b -> setInterval(id, iv - 10));
            btn(x + lw + 82, yy, 26, "+", b -> setInterval(id, iv + 10));
        }
        int listY = y + 18 + 3 * 16 + 4;
        int per = Math.max(2, (ph - 24 - (listY - y) - 16) / 16);
        List<CapriNet.Pool> pool = CapriClientState.pool;
        int pages = Math.max(1, (pool.size() + per - 1) / per);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= pool.size()) {
                break;
            }
            CapriNet.Pool p = pool.get(idx);
            boolean on = sel.contains(p.id());
            String st = switch (p.status()) {
                case 0 -> "ready";
                case 1 -> "cooldown";
                case 2 -> "no mana";
                case 3 -> "RELIC MISSING" + (p.note().isEmpty() ? "" : " (" + p.note() + ")");
                default -> "casting";
            };
            Button b = btn(x, listY + i * 16, pw - 16, (on ? "[X] " : "[  ] ") + spellName(p.id()) + "  Lv" + p.level() + "  " + p.mana() + " mana  - " + st, bb -> toggleSpell(p.id()));
            b.active = p.status() != 3;
            ((SciButton) b).accent = on;
        }
        if (pages > 1) {
            btn(px + pw - 100, y, 44, "<", b -> {
                page = Math.max(0, page - 1);
                rebuild();
            });
            btn(px + pw - 52, y, 44, ">", b -> {
                page++;
                rebuild();
            });
        }
    }

    private void move(int i, int d) {
        List<String> l = new ArrayList<>(CapriClientState.spells);
        int j = i + d;
        if (j < 0 || j >= l.size()) {
            return;
        }
        java.util.Collections.swap(l, i, j);
        CapriClientState.spells = l;
        CapriClient.send(new CapriNet.Action(CapriNet.OP_SET_SPELLS, "", 0, l));
        CapriDialogue.event("priority_set", "");
        rebuild();
    }

    private void setInterval(String id, int ticks) {
        ticks = Math.max(0, Math.min(2400, ticks));
        CapriClientState.intervals.put(id, ticks);
        CapriClient.send(new CapriNet.Action(CapriNet.OP_SET_INTERVAL, id, ticks, null));
        CapriDialogue.event("interval_set", "");
        rebuild();
    }

    private void toggleSpell(String id) {
        List<String> l = new ArrayList<>(CapriClientState.spells);
        if (!l.remove(id)) {
            if (l.size() >= CapriConfig.MAX_SPELLS.get()) {
                CapriDialogue.say("Three spells max, partner. Untick one first.", CapriDialogue.NORMAL, false);
                return;
            }
            l.add(id);
        }
        CapriClientState.spells = l;
        CapriClient.send(new CapriNet.Action(CapriNet.OP_SET_SPELLS, "", 0, l));
        CapriDialogue.event("spells_set", "");
        rebuild();
    }

    // ------------------------------------------------------------------ tab 1

    private void toggle(ForgeConfigSpec.BooleanValue v, int x, int y, int w, String label) {
        btn(x, y, w, label + ": " + (v.get() ? "ON" : "OFF"), b -> {
            v.set(!v.get());
            v.save();
            rebuild();
        });
    }

    private void cycle(ForgeConfigSpec.DoubleValue v, double[] vals, int x, int y, int w, String label) {
        btn(x, y, w, label + ": " + String.format(Locale.ROOT, "%.2f", v.get()), b -> {
            int best = 0;
            for (int i = 0; i < vals.length; i++) {
                if (Math.abs(vals[i] - v.get()) < Math.abs(vals[best] - v.get())) {
                    best = i;
                }
            }
            v.set(vals[(best + 1) % vals.length]);
            v.save();
            rebuild();
        });
    }

    private void cycleInt(ForgeConfigSpec.IntValue v, int[] vals, int x, int y, int w, String label) {
        btn(x, y, w, label + ": " + v.get(), b -> {
            int cur = 0;
            for (int i = 0; i < vals.length; i++) {
                if (vals[i] == v.get()) {
                    cur = i;
                }
            }
            v.set(vals[(cur + 1) % vals.length]);
            v.save();
            rebuild();
        });
    }

    private void targetTab(int x, int y) {
        int w = (pw - 24) / 2;
        btn(x, y, w, CapriClientState.lockId >= 0 ? "Release lock" : "Lock target in sights", b -> {
            if (CapriClientState.lockId >= 0) {
                CapriClient.unlock(false);
            } else {
                onClose();
                CapriDialogue.say("Close this and press the lock key while aiming.", CapriDialogue.NORMAL, false);
                return;
            }
            rebuild();
        });
        toggle(CapriConfig.LOCK_ENABLED, x + w + 8, y, w, "Brackets");
        cycle(CapriConfig.LOCK_TRACKING, new double[]{0, 0.25, 0.5, 0.75, 1}, x, y + 18, w, "Camera assist");
        cycle(CapriConfig.LOCK_RANGE, new double[]{16, 32, 48, 64, 96, 128, 200, 300}, x + w + 8, y + 18, w, "Lock range");
        int[] brk = {10, 20, 30, 50, 75, 90, 120, 150, 200, 300};
        btn(x, y + 54, w * 2 + 8, "Auto-untarget at " + CapriConfig.LOCK_BREAK.get() + " blocks (click to cycle 10 to 300)", b -> {
            int cur = 0;
            for (int i = 0; i < brk.length; i++) {
                if (brk[i] <= CapriConfig.LOCK_BREAK.get()) {
                    cur = i;
                }
            }
            CapriConfig.LOCK_BREAK.set(brk[(cur + 1) % brk.length]);
            CapriConfig.LOCK_BREAK.save();
            CapriClient.sendBreak();
            rebuild();
        });
        cycle(CapriConfig.LOCK_LOS_SECONDS, new double[]{1, 3, 5, 10}, x, y + 36, w, "Lost-sight secs");
        toggle(CapriConfig.DIAGNOSTICS, x + w + 8, y + 36, w, "Target card");
        String[] names = {"HP", "Armor", "Effects", "Aggro", "Distance"};
        for (int i = 0; i < 5; i++) {
            final int bit = 1 << i;
            boolean on = (CapriConfig.DIAG_FIELDS.get() & bit) != 0;
            btn(x + (i % 2) * (w + 8), y + 76 + (i / 2) * 18, w, "Show " + names[i] + ": " + (on ? "ON" : "OFF"), b -> {
                CapriConfig.DIAG_FIELDS.set(CapriConfig.DIAG_FIELDS.get() ^ bit);
                CapriConfig.DIAG_FIELDS.save();
                rebuild();
            });
        }
    }

    // ------------------------------------------------------------------ tab 2

    private List<Attribute> attrList() {
        List<Attribute> out = new ArrayList<>();
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return out;
        }
        for (Attribute a : ForgeRegistries.ATTRIBUTES) {
            ResourceLocation k = ForgeRegistries.ATTRIBUTES.getKey(a);
            if (k != null && NS.contains(k.getNamespace()) && p.getAttribute(a) != null && CapriServer.governable(k.toString())) {
                out.add(a);
            }
        }
        out.sort((a, b) -> Component.translatable(a.getDescriptionId()).getString().compareToIgnoreCase(Component.translatable(b.getDescriptionId()).getString()));
        return out;
    }

    private static String search = "";
    private static boolean searchFocus;

    private void attrTab(int x, int y) {
        String q = search.toLowerCase(Locale.ROOT).trim();
        List<Attribute> all = attrList();
        List<Attribute> list = new ArrayList<>();
        for (Attribute a : all) {
            String nm = Component.translatable(a.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
            String id = String.valueOf(ForgeRegistries.ATTRIBUTES.getKey(a));
            if (q.isEmpty() || nm.contains(q) || id.contains(q)) {
                list.add(a);
            }
        }
        int per = Math.max(3, (ph - 70) / 16);
        int pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.min(page, pages - 1);
        btn(x, y, 96, "Restore ALL", b -> {
            CapriClient.send(CapriNet.Action.of(CapriNet.OP_RESET_ATTRS));
            CapriClientState.attrs.clear();
            CapriDialogue.event("attr_reset", "");
            rebuild();
        });
        EditBox box = new EditBox(font, x + 100, y, 150, 14, Component.literal("search"));
        box.setMaxLength(40);
        box.setHint(Component.literal("search stat...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        box.setValue(search);
        box.setResponder(v -> {
            if (!v.equals(search)) {
                search = v;
                page = 0;
                searchFocus = true;
                rebuild();
            }
        });
        addRenderableWidget(box);
        if (searchFocus) {
            setFocused(box);
            box.setFocused(true);
        }
        if (pages > 1) {
            btn(px + pw - 100, y, 44, "<", b -> {
                page = Math.max(0, page - 1);
                searchFocus = false;
                rebuild();
            });
            btn(px + pw - 52, y, 44, ">", b -> {
                page++;
                searchFocus = false;
                rebuild();
            });
        }
        if (list.isEmpty()) {
            Button none = btn(x, y + 20, pw - 16, "No stat matches '" + search + "'", b -> {
            });
            none.active = false;
        }
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= list.size()) {
                break;
            }
            Attribute a = list.get(idx);
            String id = String.valueOf(ForgeRegistries.ATTRIBUTES.getKey(a));
            int pct = CapriClientState.attrs.getOrDefault(id, 100);
            AttributeInstance inst = Minecraft.getInstance().player.getAttribute(a);
            int yy = y + 20 + i * 16;
            Button row = btn(x, yy, pw - 16 - 100, f(Component.translatable(a.getDescriptionId()).getString(), 22) + "  " + String.format(Locale.ROOT, "%.2f", inst.getValue()) + "  (" + pct + "%)", b -> {
            });
            row.active = false;
            btn(x + pw - 16 - 96, yy, 30, "-10", b -> setAttr(id, pct - 10));
            btn(x + pw - 16 - 64, yy, 30, "+10", b -> setAttr(id, pct + 10));
            btn(x + pw - 16 - 32, yy, 32, "100", b -> setAttr(id, 100));
        }
    }

    private static String f(String s, int n) {
        return s.length() > n ? s.substring(0, n - 1) + "." : s;
    }

    private void setAttr(String id, int pct) {
        pct = Math.max(CapriConfig.MIN_ATTR_PERCENT.get(), Math.min(100, pct));
        CapriClientState.attrs.put(id, pct);
        CapriClient.send(new CapriNet.Action(CapriNet.OP_SET_ATTR, id, pct, null));
        CapriDialogue.event("attr_set", "");
        rebuild();
    }

    // ------------------------------------------------------------------ tab 3

    private void inputTab(int x, int y) {
        int w = pw - 16;
        for (int i = 0; i < CapriInputAuto.ROWS.size() && i < 6; i++) {
            CapriInputAuto.Row r = CapriInputAuto.ROWS.get(i);
            int yy = y + i * 18;
            btn(x, yy, 90, r.action + ": " + (r.active ? "RUN" : "off"), b -> {
                r.active = !r.active;
                CapriDialogue.event(CapriInputAuto.any() ? "input_on" : "input_off", "");
                rebuild();
            });
            btn(x + 94, yy, 70, r.mode, b -> {
                int m = 0;
                for (int j = 0; j < CapriInputAuto.MODES.length; j++) {
                    if (CapriInputAuto.MODES[j].equals(r.mode)) {
                        m = j;
                    }
                }
                r.mode = CapriInputAuto.MODES[(m + 1) % CapriInputAuto.MODES.length];
                CapriInputAuto.save();
                rebuild();
            });
            btn(x + 168, yy, 24, "-", b -> {
                r.rate = Math.max(1, r.rate - 1);
                CapriInputAuto.save();
                rebuild();
            });
            btn(x + 194, yy, 70, "every " + r.rate + "t", b -> {
            }).active = false;
            btn(x + 268, yy, 24, "+", b -> {
                r.rate = Math.min(100, r.rate + 1);
                CapriInputAuto.save();
                rebuild();
            });
        }
        int ya = y + 6 * 18 + 4;
        btn(x, ya, 150, "STOP ALL INPUT", b -> {
            CapriInputAuto.stopAll(true);
            rebuild();
        });
        toggle(CapriConfig.KEEP_RUNNING_UNFOCUSED, x + 156, ya, w - 156, "Keep running unfocused");
        btn(x, ya + 18, 150, "ONSLAUGHT: " + (CapriOnslaught.on ? "ON" : "off"), b -> {
            CapriClient.send(new CapriNet.Action(CapriNet.OP_ONS, "", 2, null));
        });
        int yo = ya + 36, half = (w - 6) / 2;
        btn(x, yo, 20, "-", b -> stepOns(true, -1));
        btn(x + 22, yo, half - 44, "Range " + CapriConfig.ONS_RANGE.get() + " (3-75)", b -> {
        }).active = false;
        btn(x + half - 20, yo, 20, "+", b -> stepOns(true, 1));
        btn(x + half + 6, yo, 20, "-", b -> stepOns(false, -1));
        btn(x + half + 28, yo, half - 44, "Circle " + CapriConfig.ONS_CIRCLE.get() + " (2-25)", b -> {
        }).active = false;
        btn(x + w - 20, yo, 20, "+", b -> stepOns(false, 1));
        btn(x + 156, ya + 18, w - 156, "Mode: " + CapriOnslaught.modeName(), b -> {
            CapriClient.send(new CapriNet.Action(CapriNet.OP_ONS_MODE, "", -1, null));
        });
    }

    // ------------------------------------------------------------------ tab 4

    private void voiceTab(int x, int y) {
        int w = (pw - 24) / 2;
        toggle(CapriConfig.VOICE, x, y, w, "Voice blips");
        cycle(CapriConfig.VOICE_VOLUME, new double[]{0, 0.25, 0.5, 0.8, 1}, x + w + 8, y, w, "Volume");
        toggle(CapriConfig.SUBTITLES, x, y + 18, w, "Subtitles");
        toggle(CapriConfig.ANNOUNCE, x + w + 8, y + 18, w, "Announcements");
        cycleInt(CapriConfig.VERBOSITY, new int[]{0, 1, 2}, x, y + 36, w, "Verbosity");
        toggle(CapriConfig.IDLE_CHATTER, x + w + 8, y + 36, w, "Idle chatter");
        cycleInt(CapriConfig.CHATTER_MINUTES, new int[]{3, 5, 8, 15}, x, y + 54, w, "Chatter minutes");
        toggle(CapriConfig.AVATAR, x + w + 8, y + 54, w, "Avatar");
        toggle(CapriConfig.AVATAR_IDLE, x, y + 72, w, "Avatar idle pop-in");
        toggle(CapriConfig.AVATAR_ANIM, x + w + 8, y + 72, w, "Avatar anim");
        cycleInt(CapriConfig.AVATAR_CORNER, new int[]{0, 1, 2, 3}, x, y + 90, w, "Avatar corner");
        cycle(CapriConfig.AVATAR_SCALE, new double[]{0.5, 0.75, 1, 1.5, 2}, x + w + 8, y + 90, w, "Avatar size");
        toggle(CapriConfig.REDUCED_MOTION, x, y + 108, w, "Reduced motion");
        toggle(CapriConfig.REDUCED_FLASH, x + w + 8, y + 108, w, "Reduced flashing");
        cycle(CapriConfig.HUD_OPACITY, new double[]{0.4, 0.6, 0.85, 1}, x, y + 126, w, "HUD opacity");
        toggle(CapriConfig.GREETING, x + w + 8, y + 126, w, "Greeting");
    }

    // ------------------------------------------------------------------ draw

    @Override
    public void tick() {
        if (tab == 0 || tab == 2) {
            long n = System.currentTimeMillis();
            if (n - lastSync > 1000) {
                lastSync = n;
                CapriClient.send(CapriNet.Action.of(CapriNet.OP_SYNC));
            }
        }
        if (!CapriClientState.linked) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cy = 0xFF000000 | CapriHud.CYAN, or = 0xFF000000 | CapriHud.ORANGE;
        g.fill(px - 3, py - 14, px + pw + 3, py + ph + 3, 0xF2070A11);
        g.fill(px - 3, py - 14, px + pw + 3, py - 13, cy);
        g.fill(px - 3, py + ph + 2, px + pw + 3, py + ph + 3, cy);
        g.fill(px - 3, py - 14, px - 2, py + ph + 3, cy);
        g.fill(px + pw + 2, py - 14, px + pw + 3, py + ph + 3, cy);
        g.fill(px - 3, py - 14, px + 22, py - 12, or);
        g.fill(px + pw - 19, py + ph + 1, px + pw + 3, py + ph + 3, or);
        // faint scanlines
        for (int yy = py - 12; yy < py + ph + 2; yy += 3) {
            g.fill(px - 2, yy, px + pw + 2, yy + 1, 0x0C53E4EA);
        }
        g.drawString(font, "CAPRI // NEURAL CONTROL", px + 4, py - 11, or, false);
        String foot = switch (tab) {
            case 0 -> "Top = cast first. Wait = extra seconds between casts of that spell. Only castable spells are listed.";
            case 1 -> "Lock key aims brackets and camera assist. Lock breaks at 90 blocks.";
            case 2 -> "Lower only. Percent of your REAL stat. Restore puts everything back.";
            case 3 -> "Presses the game's own keys. Stops on screens, death, unlink, End key.";
            default -> "Talk to me in chat: @capri <message>";
        };
        g.drawString(font, font.plainSubstrByWidth(foot, pw - 8), px + 4, py + ph - 10, cy, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int sc, int mod) {
        if (CapriClient.PANEL.matches(key, sc)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, sc, mod);
    }
}
