package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.IrontombConfigSpec;
import com.example.jjkspells.irontomb.IrontombConfigSpec.Field;
import com.example.jjkspells.irontomb.IrontombNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-game Irontomb tuning screen (opened by the Irontomb Configurator item). */
public class ConfiguratorScreen extends Screen {
    private final Map<String, Double> vals;
    private final Map<String, EditBox> boxes = new HashMap<>();
    private int tab;
    private int statusTicks;

    private ConfiguratorScreen(Map<String, Double> vals) {
        super(Component.literal("Irontomb Configurator"));
        this.vals = new LinkedHashMap<>(vals);
    }

    public static void open(Map<String, Double> values) {
        Minecraft.getInstance().setScreen(new ConfiguratorScreen(values));
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** Reads the visible boxes into the value map (clamped to each field's range). */
    private void commit() {
        for (Field f : IrontombConfigSpec.FIELDS) {
            EditBox b = boxes.get(f.key());
            if (b == null) {
                continue;
            }
            try {
                double v = Double.parseDouble(b.getValue().trim());
                vals.put(f.key(), Math.max(f.min(), Math.min(f.max(), v)));
            } catch (NumberFormatException ex) {
                // keep the previous value
            }
        }
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        commit();
        super.resize(mc, w, h);
    }

    @Override
    protected void init() {
        boxes.clear();
        int n = IrontombConfigSpec.TABS.length;
        int gap = 4, tw = Math.min(104, (this.width - 16) / n - gap), x0 = this.width / 2 - (n * tw + (n - 1) * gap) / 2;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Button t = Button.builder(Component.literal((i == tab ? "> " : "") + IrontombConfigSpec.TABS[i]), b -> {
                commit();
                tab = idx;
                rebuildWidgets();
            }).bounds(x0 + i * (tw + gap), 22, tw, 20).build();
            addRenderableWidget(t);
        }
        int row = 0, perCol = 6;
        int colX0 = this.width / 2 - 215, colX1 = this.width / 2 + 5;
        for (Field f : IrontombConfigSpec.FIELDS) {
            if (f.tab() != tab) {
                continue;
            }
            int col = row / perCol, r = row % perCol;
            int x = (col == 0 ? colX0 : colX1) + 128, y = 58 + r * 24;
            EditBox b = new EditBox(this.font, x, y, 80, 18, Component.literal(f.label()));
            b.setMaxLength(14);
            b.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
            b.setValue(fmt(vals.getOrDefault(f.key(), f.def())));
            addRenderableWidget(b);
            boxes.put(f.key(), b);
            row++;
        }
        int by = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply()).bounds(this.width / 2 - 160, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Tab defaults"), b -> {
            for (Field f : IrontombConfigSpec.FIELDS) {
                if (f.tab() == tab) {
                    vals.put(f.key(), f.def());
                }
            }
            rebuildWidgets();
        }).bounds(this.width / 2 - 50, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(this.width / 2 + 60, by, 100, 20).build());
    }

    private void apply() {
        commit();
        IrontombNet.CHANNEL.sendToServer(new IrontombNet.ApplyMsg(new LinkedHashMap<>(vals)));
        statusTicks = 80;
    }

    @Override
    public void tick() {
        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFF7A66);
        int row = 0, perCol = 6;
        int colX0 = this.width / 2 - 215, colX1 = this.width / 2 + 5;
        for (Field f : IrontombConfigSpec.FIELDS) {
            if (f.tab() != tab) {
                continue;
            }
            int col = row / perCol, r = row % perCol;
            int x = col == 0 ? colX0 : colX1;
            g.drawString(this.font, f.label(), x, 63 + r * 24, 0xE8D8DA);
            row++;
        }
        // live preview for a 20 HP player on the attack tabs
        if (tab == 1 || tab == 2) {
            commit();
            double scale = vals.getOrDefault("damage_scale", 1.0);
            StringBuilder sb = new StringBuilder("vs 20 HP: ");
            String[] ks = tab == 1 ? new String[]{"cast_down", "weave", "yield", "mire"} : new String[]{"crown", "prison", "immolate", "logos_bolt", "mythos_bolt"};
            for (String k : ks) {
                double d = (vals.getOrDefault(k + "_flat", 0.0) + vals.getOrDefault(k + "_pct", 0.0) * 20.0) * scale;
                sb.append(k.replace('_', ' ')).append(' ').append(Math.round(d * 10.0) / 10.0).append("   ");
            }
            g.drawCenteredString(this.font, sb.toString(), this.width / 2, this.height - 46, 0xA58B92);
        }
        if (statusTicks > 0) {
            g.drawCenteredString(this.font, "Sent to the server: applied instantly and saved", this.width / 2, this.height - 58, 0x6FE3A1);
        }
        super.render(g, mx, my, pt);
    }
}
