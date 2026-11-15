package com.example.jjkspells.learner;

import com.example.jjkspells.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Left: every spell in sections. Right: the chosen spell, its boss lock, what it costs, and the Learn button.
 * Operators get an Edit mode with plain text boxes (item id, amount, level, boss id); the window shrinks to fit small screens.
 */
public class SkillLearnerScreen extends AbstractContainerScreen<SkillLearnerMenu> {
    private static final int LIST_X = 6, LIST_Y = 26, HEADER_H = 14, ROW_H = 12, EDIT_ROW = 18;
    private static final int GOLD = 0xFFE0B040;

    private int W = 330, H = 280, LIST_W = 128;

    private record Row(boolean header, int group, int entry) {
    }

    private static final class EditRow {
        String id;
        String count;

        EditRow(String id, String count) {
            this.id = id;
            this.count = count;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int selected = -1;
    private double scroll = 0;
    private int totalHeight = 0;
    private Button learn, editBtn, saveBtn, revertBtn, addBtn, heldBtn;
    private EditBox bossBox, levelBox, addBox;
    private final List<net.minecraft.client.gui.components.AbstractWidget> rowWidgets = new ArrayList<>();
    private final List<EditBox> countBoxes = new ArrayList<>();
    private final List<EditRow> edit = new ArrayList<>();
    private boolean editMode = false;
    private boolean pickMode = false;     // showing the player's inventory to pick materials from
    private int editScroll = 0;
    private String status = "";
    private int statusColor = 0xFFFF6060;
    private long statusUntil = 0;

    public SkillLearnerScreen(SkillLearnerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    // ---- layout -------------------------------------------------------------------------------------------------------------
    @Override
    protected void init() {
        W = Math.min(330, this.width - 6);
        H = Math.min(280, this.height - 6);
        LIST_W = Math.max(92, Math.min(128, (int) (W * 0.38)));
        this.imageWidth = W;
        this.imageHeight = H;
        super.init();
        rows.clear();
        rowWidgets.clear();
        countBoxes.clear();
        int lastGroup = -1;
        totalHeight = 0;
        for (int i = 0; i < menu.entries.size(); i++) {
            var e = menu.entries.get(i);
            if (e.group != lastGroup) {
                rows.add(new Row(true, e.group, -1));
                totalHeight += HEADER_H;
                lastGroup = e.group;
            }
            rows.add(new Row(false, e.group, i));
            totalHeight += ROW_H;
        }
        int px = px(), pw = pw(), ly = topPos + LIST_Y, lh = listHeight();
        learn = addRenderableWidget(Button.builder(Component.literal("Learn Scroll"), b -> {
            if (selected >= 0) press(selected);
        }).bounds(px + pw - 128, ly + lh - 22, 126, 20).build());
        learn.active = false;
        editBtn = addRenderableWidget(Button.builder(Component.literal("Edit"), b -> {
            editMode = !editMode;
            if (editMode) loadEdit();
            layoutEdit();
        }).bounds(leftPos + W - 66, topPos + 3, 60, 16).build());

        bossBox = new EditBox(font, px + 38, ly + 22, pw - 44, 14, Component.literal("boss"));
        bossBox.setMaxLength(100);
        bossBox.setHint(Component.literal("namespace:boss (empty = none)").withStyle(ChatFormatting.DARK_GRAY));
        levelBox = new EditBox(font, px + 38, ly + 40, 36, 14, Component.literal("level"));
        levelBox.setMaxLength(3);
        levelBox.setFilter(s -> s.matches("\\d*"));
        addBox = new EditBox(font, px + 6, ly + lh - 42, pw - 58, 14, Component.literal("item"));
        addBox.setMaxLength(100);
        addBox.setHint(Component.literal("item id, e.g. minecraft:diamond").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(bossBox);
        addRenderableWidget(levelBox);
        addRenderableWidget(addBox);
        addBtn = addRenderableWidget(Button.builder(Component.literal("Add"), b -> addTyped()).bounds(px + pw - 48, ly + lh - 43, 42, 16).build());
        int bw = (pw - 12 - 8) / 3;
        heldBtn = addRenderableWidget(Button.builder(Component.literal("Items"), b -> {
            pickMode = !pickMode;
            layoutEdit();
        }).bounds(px + 6, ly + lh - 22, bw, 18).build());
        saveBtn = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(px + 6 + bw + 4, ly + lh - 22, bw, 18).build());
        revertBtn = addRenderableWidget(Button.builder(Component.literal("Revert"), b -> {
            loadEdit();
            layoutEdit();
        }).bounds(px + 6 + 2 * (bw + 4), ly + lh - 22, bw, 18).build());
        layoutEdit();
    }

    private int px() {
        return leftPos + LIST_X + LIST_W + 8;
    }

    private int pw() {
        return W - (LIST_X + LIST_W + 8) - 6;
    }

    private int listHeight() {
        return H - LIST_Y - 6;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            if (id >= 250) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 250 + id / 250);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id % 250);
        }
    }

    private boolean isOp() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    private boolean isItemEntry() {
        return selected >= 0 && selected < menu.entries.size() && SkillGroups.isItem(menu.entries.get(selected).spellPath);
    }

    private boolean hasEntry() {
        return selected >= 0 && selected < menu.entries.size();
    }

    // ---- edit state ---------------------------------------------------------------------------------------------------------
    private void loadEdit() {
        edit.clear();
        editScroll = 0;
        if (!hasEntry()) return;
        var e = menu.entries.get(selected);
        for (Mat m : e.mats) edit.add(new EditRow(m.name(), String.valueOf(m.count)));
        levelBox.setValue(String.valueOf(e.level));
        bossBox.setValue(e.boss);
    }

    private int visibleRows() {
        int ly = topPos + LIST_Y, lh = listHeight();
        int y0 = ly + 72, bottom = ly + lh - 48;
        return Math.max(1, (bottom - y0) / EDIT_ROW);
    }

    /** Shows or hides the edit widgets and rebuilds the visible material rows. */
    private void layoutEdit() {
        boolean on = editMode && isOp() && hasEntry();
        for (var w : rowWidgets) removeWidget(w);
        rowWidgets.clear();
        countBoxes.clear();
        bossBox.visible = on;
        levelBox.visible = on && !isItemEntry();
        addBox.visible = on;
        addBtn.visible = heldBtn.visible = saveBtn.visible = revertBtn.visible = on;
        learn.visible = !on;
        if (!on) {
            pickMode = false;
            for (EditBox b : new EditBox[]{bossBox, levelBox, addBox}) b.setFocused(false);
            return;
        }
        heldBtn.setMessage(Component.literal(pickMode ? "Back" : "Items"));
        if (pickMode) return;   // the material rows give way to the inventory grid
        int px = px(), pw = pw(), ly = topPos + LIST_Y;
        int y0 = ly + 72;
        int vis = visibleRows();
        editScroll = Mth.clamp(editScroll, 0, Math.max(0, edit.size() - vis));
        for (int i = 0; i < vis && editScroll + i < edit.size(); i++) {
            final EditRow r = edit.get(editScroll + i);
            final int idx = editScroll + i;
            int y = y0 + i * EDIT_ROW;
            EditBox cb = new EditBox(font, px + pw - 6 - 16 - 48, y + 1, 46, 14, Component.literal("amount"));
            cb.setMaxLength(5);
            cb.setFilter(s -> s.matches("\\d*"));
            cb.setValue(r.count);
            cb.setResponder(s -> r.count = s);
            addRenderableWidget(cb);
            rowWidgets.add(cb);
            countBoxes.add(cb);
            Button x = Button.builder(Component.literal("x"), b -> {
                edit.remove(idx);
                layoutEdit();
            }).bounds(px + pw - 6 - 14, y + 1, 14, 14).build();
            addRenderableWidget(x);
            rowWidgets.add(x);
        }
    }

    private void flash(String msg, int color) {
        status = msg;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 3500;
    }

    private void addId(String raw) {
        String id = raw.trim();
        if (id.isEmpty()) return;
        boolean tag = id.startsWith("#");
        String body = tag ? id.substring(1) : id;
        if (!body.contains(":")) body = "minecraft:" + body;
        id = (tag ? "#" : "") + body;
        if (!Mat.parse(id + "*1").valid) {
            flash("Unknown item: " + id, 0xFFFF6060);
            return;
        }
        for (EditRow r : edit) {
            if (r.id.equals(id)) {
                flash("Already in the list", 0xFFFFC060);
                return;
            }
        }
        edit.add(new EditRow(id, "1"));
        editScroll = Math.max(0, edit.size() - visibleRows());
        layoutEdit();
    }

    private void addTyped() {
        addId(addBox.getValue());
        addBox.setValue("");
    }

    private void addHeld() {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack st = minecraft.player.getMainHandItem();
        if (st.isEmpty()) {
            flash("Hold an item in your main hand first", 0xFFFFC060);
            return;
        }
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (k != null) addId(k.toString());
    }

    private void save() {
        if (!hasEntry()) return;
        List<String> out = new ArrayList<>();
        for (EditRow r : edit) {
            int n;
            try {
                n = Integer.parseInt(r.count.trim());
            } catch (NumberFormatException ex) {
                n = 1;
            }
            out.add(r.id + "*" + Mth.clamp(n, 1, 99999));
        }
        int lvl = 1;
        try {
            lvl = Integer.parseInt(levelBox.getValue().trim());
        } catch (NumberFormatException ignored) {
            // keep 1
        }
        ModNetwork.CHANNEL.sendToServer(new LearnerEditPacket(selected, Mth.clamp(lvl, 1, 100), bossBox.getValue().trim(), out));
        flash("Saving...", 0xFF50E070);
    }

    // ---- text helpers -------------------------------------------------------------------------------------------------------
    private static String prettify(String path) {
        StringBuilder sb = new StringBuilder();
        for (String s : path.split("_")) {
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    private static String guideKey(String path) {
        return SkillGroups.isItem(path) ? "item.jjk_spells." + SkillGroups.itemPath(path) + ".guide" : "spell.jjk_spells." + path + ".guide";
    }

    private static Component spellName(String path) {
        if (SkillGroups.isItem(path)) {
            var it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("jjk_spells", SkillGroups.itemPath(path)));
            if (it != null) return new ItemStack(it).getHoverName();
        }
        String key = "spell.jjk_spells." + path;
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(prettify(path));
    }

    private static String bossName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return ForgeRegistries.ENTITY_TYPES.getValue(rl).getDescription().getString();
        return id;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF3A2A10);
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, GOLD);
        g.fill(x, y, x + W, y + H, 0xF0120C1C);
        g.fill(x, y, x + W, y + 22, 0xFF241838);
        g.fill(x, y + 22, x + W, y + 23, GOLD);
        g.drawString(font, Component.literal(menu.prime ? "PRIME Divinity Skill Learner" : "Divine Skill Learner").withStyle(ChatFormatting.BOLD), x + 8, y + 7, 0xFFFFD870, false);

        int lx = x + LIST_X, ly = y + LIST_Y, lh = listHeight();
        g.fill(lx - 1, ly - 1, lx + LIST_W + 1, ly + lh + 1, 0xFF4A3818);
        g.fill(lx, ly, lx + LIST_W, ly + lh, 0xFF0A0712);
        g.enableScissor(lx, ly, lx + LIST_W, ly + lh);
        int cy = ly - (int) scroll;
        for (Row r : rows) {
            int h = r.header ? HEADER_H : ROW_H;
            if (cy + h >= ly && cy <= ly + lh) {
                if (r.header) {
                    var grp = SkillGroups.ALL.get(r.group);
                    boolean open = menu.unlocked(r.group);
                    g.fill(lx, cy + 1, lx + LIST_W, cy + h - 1, 0xFF1C1430);
                    g.fill(lx, cy + 1, lx + 3, cy + h - 1, 0xFF000000 | (open ? grp.color() : 0x555555));
                    g.drawString(font, font.plainSubstrByWidth(grp.title() + (open ? "" : " [locked]"), LIST_W - 10), lx + 7, cy + 3, open ? 0xFF000000 | grp.color() : 0xFF807890, false);
                } else {
                    var e = menu.entries.get(r.entry);
                    boolean open = menu.unlocked(e.group);
                    boolean hover = mx >= lx && mx < lx + LIST_W && my >= Math.max(ly, cy) && my < Math.min(ly + lh, cy + h);
                    if (r.entry == selected) g.fill(lx, cy, lx + LIST_W - 1, cy + h, 0xFF3A2A60);
                    else if (hover) g.fill(lx, cy, lx + LIST_W - 1, cy + h, 0xFF241A3C);
                    boolean can = open && e.affordable(minecraft.player);
                    g.fill(lx + 6, cy + 5, lx + 9, cy + 8, can ? 0xFF50E070 : 0xFF503040);
                    String name = font.plainSubstrByWidth(spellName(e.spellPath).getString(), LIST_W - 22);
                    g.drawString(font, name, lx + 13, cy + 2, r.entry == selected ? 0xFFFFFFFF : (open ? 0xFFC8C0D8 : 0xFF706880), false);
                }
            }
            cy += h;
        }
        g.disableScissor();
        int max = Math.max(0, totalHeight - lh);
        if (max > 0) {
            int barH = Math.max(12, lh * lh / totalHeight);
            int barY = ly + (int) ((lh - barH) * (scroll / max));
            g.fill(lx + LIST_W - 3, barY, lx + LIST_W, barY + barH, 0xFFB08830);
        }

        int px = px(), pw = pw();
        g.fill(px - 1, ly - 1, px + pw + 1, ly + lh + 1, 0xFF4A3818);
        g.fill(px, ly, px + pw, ly + lh, 0xFF0E0A18);
        if (!hasEntry()) {
            g.drawCenteredString(font, "Pick a spell on the left", px + pw / 2, ly + lh / 2 - 4, 0xFF807890);
            return;
        }
        var e = menu.entries.get(selected);
        var grp = SkillGroups.ALL.get(e.group);
        int ty = ly + 5;
        g.drawString(font, font.plainSubstrByWidth(spellName(e.spellPath).getString(), pw - 12), px + 6, ty, 0xFFFFD870, false);
        ty += 10;
        g.drawString(font, grp.title() + (SkillGroups.isItem(e.spellPath) ? "  -  curio" : "  -  scroll level " + e.level), px + 6, ty, 0xFF000000 | grp.color(), false);
        ty += 10;
        g.drawString(font, font.plainSubstrByWidth("ID: jjk_spells:" + (SkillGroups.isItem(e.spellPath) ? SkillGroups.itemPath(e.spellPath) : e.spellPath), pw - 12), px + 6, ty, 0xFF70D8E0, false);
        if (editMode && isOp()) {
            renderEdit(g, e, px, pw, ly, lh);
            return;
        }
        ty += 11;
        boolean open = menu.unlocked(e.group);
        if (!e.boss.isEmpty()) {
            String t = open ? "Unlocked: " + bossName(e.boss) + " defeated" : "Locked: defeat " + bossName(e.boss);
            g.drawString(font, font.plainSubstrByWidth(t, pw - 12), px + 6, ty, open ? 0xFF50E070 : 0xFFFF6060, false);
            ty += 11;
        }
        String gk = guideKey(e.spellPath);
        int guideLines = Math.max(2, Math.min(5, (ly + lh - 24 - ty - 22 - 21 * Math.max(1, e.mats.size())) / 9));
        if (I18n.exists(gk)) {
            List<FormattedCharSequence> lines = font.split(FormattedText.of(I18n.get(gk)), pw - 12);
            for (int i = 0; i < Math.min(guideLines, lines.size()); i++) {
                g.drawString(font, lines.get(i), px + 6, ty, 0xFF9890A8, false);
                ty += 9;
            }
            if (lines.size() > guideLines) {
                g.drawString(font, "...", px + 6, ty, 0xFF9890A8, false);
                ty += 9;
            }
            ty += 2;
        }
        g.fill(px + 4, ty, px + pw - 4, ty + 1, 0xFF3A2A10);
        ty += 4;
        g.drawString(font, "Materials", px + 6, ty, 0xFFE0B040, false);
        ty += 11;
        if (e.mats.isEmpty()) g.drawString(font, "Free", px + 6, ty, 0xFF50E070, false);
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        matsTop = ty;
        for (Mat m : e.mats) {
            int have = m.have(minecraft.player);
            boolean ok = m.valid && have >= m.count;
            g.fill(px + 5, ty - 2, px + pw - 5, ty + 17, 0xFF181226);
            ItemStack icon = m.icon(time);
            if (!icon.isEmpty()) g.renderItem(icon, px + 8, ty - 1);
            String nm = m.valid ? (icon.isEmpty() ? m.name() : m.label(icon)) : "Invalid: " + m.name();
            String cnt = have + " / " + m.count;
            g.drawString(font, font.plainSubstrByWidth(nm, pw - 40 - font.width(cnt)), px + 28, ty + 4, m.valid ? 0xFFE8E0F0 : 0xFFFF6060, false);
            g.drawString(font, cnt, px + pw - 8 - font.width(cnt), ty + 4, ok ? 0xFF50E070 : 0xFFFF6060, false);
            ty += 21;
        }
    }

    // ---- inventory picker ---------------------------------------------------------------------------------------------------
    private int gridX(int inv) {
        return px() + 6 + (inv < 9 ? inv : (inv - 9) % 9) * 18;
    }

    private int gridY(int inv) {
        int gy = topPos + LIST_Y + 74;
        return inv < 9 ? gy + 3 * 18 + 6 : gy + ((inv - 9) / 9) * 18;
    }

    /** Which inventory slot (0..35) is under the mouse in the picker, or -1. */
    private int gridSlotAt(double mx, double my) {
        for (int i = 0; i < 36; i++) {
            int gx = gridX(i), gy = gridY(i);
            if (mx >= gx && mx < gx + 18 && my >= gy && my < gy + 18) return i;
        }
        return -1;
    }

    private void togglePicked(ItemStack st) {
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (k == null) return;
        String id = k.toString();
        for (int i = 0; i < edit.size(); i++) {
            if (edit.get(i).id.equals(id)) {
                edit.remove(i);
                return;
            }
        }
        edit.add(new EditRow(id, "1"));
    }

    private int matsTop = 0;

    private void renderEdit(GuiGraphics g, LearnerEntries.Entry e, int px, int pw, int ly, int lh) {
        g.drawString(font, "Boss", px + 6, ly + 25, 0xFFE0B040, false);
        if (!isItemEntry()) g.drawString(font, "Level", px + 6, ly + 43, 0xFFE0B040, false);
        g.drawString(font, "(applies to the whole section)", px + 82, ly + 43, 0xFF605870, false);
        if (pickMode) {
            g.drawString(font, "Your inventory  (click an item to add / remove it)", px + 6, ly + 60, 0xFFE0B040, false);
            for (int i = 0; i < 36; i++) {
                int gx = gridX(i), gy = gridY(i);
                ItemStack st = minecraft.player.getInventory().getItem(i);
                boolean in = false;
                if (!st.isEmpty()) {
                    ResourceLocation k = ForgeRegistries.ITEMS.getKey(st.getItem());
                    if (k != null) for (EditRow r : edit) if (r.id.equals(k.toString())) in = true;
                }
                g.fill(gx, gy, gx + 18, gy + 18, in ? 0xFF1E6A38 : 0xFF181226);
                g.fill(gx + 1, gy + 1, gx + 17, gy + 17, in ? 0xFF2A8A4A : 0xFF2A2240);
                if (!st.isEmpty()) g.renderItem(st, gx + 1, gy + 1);
            }
            g.drawString(font, edit.size() + " material" + (edit.size() == 1 ? "" : "s") + " selected  (set amounts under Back)", px + 6, ly + lh - 56, 0xFF807890, false);
            if (System.currentTimeMillis() < statusUntil) {
                g.drawString(font, font.plainSubstrByWidth(status, pw - 12), px + 6, ly + lh - 66, statusColor, false);
            }
            return;
        }
        g.drawString(font, "Materials  (amount in the box, x removes)", px + 6, ly + 60, 0xFFE0B040, false);
        int y0 = ly + 72;
        int vis = visibleRows();
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        if (edit.isEmpty()) g.drawString(font, "Free (no materials)", px + 8, y0 + 4, 0xFF50E070, false);
        for (int i = 0; i < vis && editScroll + i < edit.size(); i++) {
            EditRow r = edit.get(editScroll + i);
            int y = y0 + i * EDIT_ROW;
            g.fill(px + 5, y, px + pw - 5, y + EDIT_ROW - 1, 0xFF181226);
            Mat m = Mat.parse(r.id + "*1");
            ItemStack icon = m.icon(time);
            if (!icon.isEmpty()) g.renderItem(icon, px + 7, y);
            String nm = m.valid ? (icon.isEmpty() ? r.id : m.label(icon)) : r.id + " (invalid)";
            g.drawString(font, font.plainSubstrByWidth(nm, pw - 24 - 16 - 52 - 10), px + 26, y + 5, m.valid ? 0xFFE8E0F0 : 0xFFFF6060, false);
        }
        if (edit.size() > vis) {
            g.drawString(font, (editScroll + 1) + "-" + Math.min(edit.size(), editScroll + vis) + " / " + edit.size() + "  (scroll)", px + pw - 8 - font.width("00-00 / 00  (scroll)"), ly + 60, 0xFF807890, false);
        }
        if (System.currentTimeMillis() < statusUntil) {
            g.drawString(font, font.plainSubstrByWidth(status, pw - 12), px + 6, ly + lh - 55 + 0, statusColor, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // everything is drawn in renderBg
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        boolean op = isOp();
        if (!op) editMode = false;
        if (editBtn != null) {
            editBtn.visible = op;
            editBtn.setMessage(Component.literal(editMode ? "Done" : "Edit"));
        }
        boolean locked = hasEntry() && !menu.unlocked(menu.entries.get(selected).group);
        if (learn != null) {
            learn.active = hasEntry() && !locked && minecraft != null && minecraft.player != null
                    && (menu.entries.get(selected).affordable(minecraft.player)
                    || (minecraft.player.isCreative() && LearnerConfig.CREATIVE_FREE.get()));
            learn.setMessage(Component.literal(!hasEntry() ? "Pick a spell" : locked ? "Locked" : learn.active
                    ? (SkillGroups.isItem(menu.entries.get(selected).spellPath) ? "Get Curio" : "Learn Scroll") : "Missing Materials"));
        }
        super.render(g, mx, my, pt);
        if (editMode && op && pickMode && minecraft != null && minecraft.player != null) {
            int i = gridSlotAt(mx, my);
            if (i >= 0) {
                ItemStack st = minecraft.player.getInventory().getItem(i);
                if (!st.isEmpty()) g.renderTooltip(font, st, mx, my);
            }
        }
        if (editMode && op) return;
        if (hasEntry()) {
            int px = px(), pw = pw();
            var e = menu.entries.get(selected);
            int ty = matsTop;
            long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
            for (Mat m : e.mats) {
                if (mx >= px + 5 && mx < px + pw - 5 && my >= ty - 2 && my < ty + 17) {
                    ItemStack st = m.icon(time);
                    if (!st.isEmpty()) g.renderTooltip(font, st, mx, my);
                }
                ty += 21;
            }
        }
    }

    // ---- input --------------------------------------------------------------------------------------------------------------
    private EditBox focusedBox() {
        if (getFocused() instanceof EditBox b && b.isFocused() && b.visible) return b;
        return null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        EditBox f = focusedBox();
        if (f != null) {
            if (key == 256) {          // escape: leave the box first
                f.setFocused(false);
                setFocused(null);
                return true;
            }
            f.keyPressed(key, scan, mods);
            if (key == 257 && f == addBox) addTyped();
            return true;               // never let "E" close the window while typing
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        EditBox f = focusedBox();
        if (f != null) return f.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && editMode && pickMode && isOp() && minecraft != null && minecraft.player != null) {
            int i = gridSlotAt(mx, my);
            if (i >= 0) {
                ItemStack st = minecraft.player.getInventory().getItem(i);
                if (!st.isEmpty()) {
                    togglePicked(st);
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
        if (button == 0 && mx >= lx && mx < lx + LIST_W && my >= ly && my < ly + listHeight()) {
            int cy = ly - (int) scroll;
            for (Row r : rows) {
                int h = r.header ? HEADER_H : ROW_H;
                if (!r.header && my >= cy && my < cy + h) {
                    selected = r.entry;
                    if (editMode) loadEdit();
                    layoutEdit();
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    return true;
                }
                cy += h;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (editMode && isOp() && !pickMode && mx >= px() && mx < px() + pw()) {
            editScroll -= (int) Math.signum(delta);
            layoutEdit();
            return true;
        }
        int max = Math.max(0, totalHeight - listHeight());
        scroll = Mth.clamp(scroll - delta * 16, 0, max);
        return true;
    }
}
