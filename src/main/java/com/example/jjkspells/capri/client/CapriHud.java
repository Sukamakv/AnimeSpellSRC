package com.example.jjkspells.capri.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.capri.CapriConfig;
import com.example.jjkspells.capri.CapriNet;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Client-only presentation. Palette follows the Capri spec: cyan #53E4EA on #090F19, hostile red #FF526A, mint #60E0C3. */
public final class CapriHud implements IGuiOverlay {
    public static final CapriHud INSTANCE = new CapriHud();
    static final int CYAN = 0x53E4EA, DEEP = 0x090F19, RED = 0xFF526A, CRIT = 0xFF6878, MINT = 0x60E0C3, ORANGE = 0xFF5A3C, PURPLE = 0x8A5CFF;
    static final ResourceLocation BUST = JJKSpells.id("textures/gui/capri/portrait_bust.png");
    static final ResourceLocation FACE = JJKSpells.id("textures/gui/capri/portrait_face.png");
    static final ResourceLocation SCENE = JJKSpells.id("textures/gui/capri/portrait_scene.png");
    static final ResourceLocation FULL = JJKSpells.id("textures/gui/capri/portrait_full.png");
    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();

    static final Matrix4f PROJ = new Matrix4f(), VIEW = new Matrix4f();
    static Vec3 camPos = Vec3.ZERO;
    static float partial;
    static boolean captured;
    private static final long[] slotPulse = new long[3];

    /** Many flat quads in ONE draw call (GuiGraphics.fill flushes after every call, which made the glitch effect expensive). */
    private static final class Batch {
        private final BufferBuilder bb = Tesselator.getInstance().getBuilder();
        private final Matrix4f m;

        Batch(GuiGraphics g) {
            m = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        void rect(float x1, float y1, float x2, float y2, int argb) {
            int a = argb >>> 24 & 255, r = argb >> 16 & 255, gg = argb >> 8 & 255, b = argb & 255;
            if (a == 0 || x2 <= x1 || y2 <= y1) {
                return;
            }
            bb.vertex(m, x1, y2, 0f).color(r, gg, b, a).endVertex();
            bb.vertex(m, x2, y2, 0f).color(r, gg, b, a).endVertex();
            bb.vertex(m, x2, y1, 0f).color(r, gg, b, a).endVertex();
            bb.vertex(m, x1, y1, 0f).color(r, gg, b, a).endVertex();
        }

        void end() {
            BufferUploader.drawWithShader(bb.end());
        }
    }

    private CapriHud() {
    }

    public static void pulseSlot(int i) {
        if (i >= 0 && i < 3) {
            slotPulse[i] = System.currentTimeMillis();
        }
    }

    static int argb(int rgb, float a) {
        return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float opacity() {
        return (float) (double) CapriConfig.HUD_OPACITY.get();
    }

    /** World point to GUI coordinates, or null if behind the camera. */
    static float[] project(Vec3 w, int sw, int sh) {
        if (!captured) {
            return null;
        }
        Vector4f v = new Vector4f((float) (w.x - camPos.x), (float) (w.y - camPos.y), (float) (w.z - camPos.z), 1f);
        v.mul(VIEW);
        v.mul(PROJ);
        if (v.w <= 0.01f) {
            return null;
        }
        return new float[]{(v.x / v.w * 0.5f + 0.5f) * sw, (0.5f - v.y / v.w * 0.5f) * sh};
    }

    private static java.lang.reflect.Method manaM;
    private static boolean manaLooked;

    /** Reflection so the mod still compiles and runs if Iron's moves this client helper. */
    private static float clientMana() throws Exception {
        if (!manaLooked) {
            manaLooked = true;
            try {
                manaM = Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData").getMethod("getPlayerMana");
            } catch (Throwable t) {
                manaM = null;
            }
        }
        return manaM == null ? 0f : ((Number) manaM.invoke(null)).floatValue();
    }

    private static ResourceLocation icon(String id) {
        return ICONS.computeIfAbsent(id, k -> {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(k);
                var sp = io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get().getValue(rl);
                return sp == null ? null : sp.getSpellIconResource();
            } catch (Throwable t) {
                return null;
            }
        });
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean motion = !CapriConfig.REDUCED_MOTION.get();
        float op = opacity();
        Font f = mc.font;

        drawLowHealth(g, f, sw, sh, now, mc);
        if (CapriClientState.linked) {
            long since = now - CapriClientState.linkedAt;
            if (CapriConfig.BOOT_ANIM.get() && since < 2200) {
                float k = since / 2200f;
                int w = 130;
                int x = sw / 2 - w / 2, y = sh / 2 + 40;
                g.fill(x - 4, y - 16, x + w + 4, y + 8, argb(DEEP, 0.8f * op));
                g.fill(x, y, x + w, y + 2, argb(CYAN, 0.25f * op));
                g.fill(x, y, x + (int) (w * k), y + 2, argb(CYAN, op));
                g.drawCenteredString(f, "CAPRI // NEURAL LINK " + (int) (k * 100) + "%", sw / 2, y - 12, 0xFF000000 | CYAN);
            }
            drawRam(g, f, now, op, mc);
            drawSlots(g, f, sh, now, op);
            drawTarget(g, f, sw, sh, now, motion, op, mc);
        }
        drawAvatarAndSubtitle(g, f, sw, sh, now, motion, op);
    }

    /** Top-left "RAM" block: your mana as a segmented bar, plus Capri's state flags. */
    private void drawRam(GuiGraphics g, Font f, long now, float op, Minecraft mc) {
        float max = 1f, cur = 0f;
        try {
            max = (float) mc.player.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
            cur = clientMana();
        } catch (Throwable ignored) {
        }
        int segs = 16, filled = Math.round(Mth.clamp(cur / Math.max(1f, max), 0f, 1f) * segs);
        int x = 8, y = 8;
        g.drawString(f, "RAM " + filled + "/" + segs, x, y, 0xFF000000 | CYAN, false);
        Batch b = new Batch(g);
        for (int i = 0; i < segs; i++) {
            int sx = x + i * 8;
            b.rect(sx, y + 11, sx + 6, y + 17, argb(CYAN, (i < filled ? 0.95f : 0.18f) * op));
        }
        b.end();
        String flags = (CapriClientState.auto ? "AUTO " : "") + (CapriClientState.lockId >= 0 ? "LOCK " : "") + (CapriInputAuto.any() ? "INPUT " : "");
        if (flags.isEmpty()) {
            flags = "LINK OK";
        }
        g.drawString(f, flags.trim(), x, y + 21, 0xFF000000 | (CapriClientState.auto ? MINT : ORANGE), false);
    }

    /** Bottom-left: the three auto-cast slots, in priority order. */
    private void drawSlots(GuiGraphics g, Font f, int sh, long now, float op) {
        int x0 = 8, y0 = sh - 38;
        for (int i = 0; i < 3; i++) {
            int x = x0 + i * 34;
            int col = 0x4A5568;
            String id = i < CapriClientState.spells.size() ? CapriClientState.spells.get(i) : null;
            float cdFrac = 0f;
            if (id != null) {
                col = CYAN;
                for (CapriNet.Pool p : CapriClientState.pool) {
                    if (p.id().equals(id)) {
                        col = switch (p.status()) {
                            case 0 -> MINT;
                            case 1 -> 0xE8C060;
                            case 2 -> 0x6080FF;
                            case 3 -> RED;
                            default -> CYAN;
                        };
                        if (p.status() == 1) {
                            cdFrac = Math.min(1f, p.cooldown() / 200f);
                        }
                    }
                }
            }
            g.fill(x, y0, x + 30, y0 + 30, argb(DEEP, 0.78f * op));
            g.fill(x, y0, x + 30, y0 + 1, argb(col, op));
            g.fill(x, y0 + 29, x + 30, y0 + 30, argb(col, op));
            g.fill(x, y0, x + 1, y0 + 30, argb(col, op));
            g.fill(x + 29, y0, x + 30, y0 + 30, argb(col, op));
            ResourceLocation ic = id == null ? null : icon(id);
            if (ic != null) {
                RenderSystem.enableBlend();
                g.blit(ic, x + 3, y0 + 3, 24, 24, 0f, 0f, 16, 16, 16, 16);
            }
            if (cdFrac > 0) {
                g.fill(x + 1, y0 + 1, x + 29, y0 + 1 + (int) (28 * cdFrac), argb(0x000000, 0.55f));
            }
            float pulse = Math.max(0f, 1f - (now - slotPulse[i]) / 500f);
            if (pulse > 0) {
                g.fill(x + 1, y0 + 1, x + 29, y0 + 29, argb(CYAN, 0.4f * pulse * op));
            }
            g.drawString(f, String.valueOf(i + 1), x + 3, y0 + 21, 0xFF000000 | col, true);
        }
    }

    private static boolean lowHp;
    private static float lowFrac = 1f;

    public static boolean isLow() {
        return lowHp;
    }

    /** Robot-malfunction glitch: low health (35 % and below), Onslaught Mode, and Cyberpsychosis. Computed fresh every frame from live state: nothing can stay stuck. */
    private void drawLowHealth(GuiGraphics g, Font f, int sw, int sh, long now, Minecraft mc) {
        var p = mc.player;
        float r = p.getHealth() / Math.max(1f, p.getMaxHealth());
        lowFrac = r;
        lowHp = CapriClientState.linked && p.isAlive() && r <= 0.35f;
        float k = lowHp ? Mth.clamp((0.35f - r) / 0.35f, 0f, 1f) * 0.65f + 0.35f : 0f;
        String label = "!! VITALS CRITICAL !!";
        if (CapriClientState.linked && CapriOnslaught.on) {
            k = Math.max(k, 0.55f);
            int sl = CapriOnslaught.secondsLeft();
            label = "ONSLAUGHT // " + CapriOnslaught.modeName() + " // " + (sl / 60) + ":" + String.format("%02d", sl % 60);
        }
        var cy = p.getEffect(com.example.jjkspells.capri.CapriEffects.CYBER.get());
        if (cy != null) {
            k = 1f;
            label = "!! CYBERPSYCHOSIS // " + (cy.getDuration() / 20 + 1) + "s !!";
        }
        if (k <= 0f || !p.isAlive()) {
            return;
        }
        boolean calm = CapriConfig.REDUCED_FLASH.get() || CapriConfig.REDUCED_MOTION.get();
        Random rnd = new Random(now / (calm ? 160 : 70));
        Batch b = new Batch(g);
        for (int y = 0; y < sh; y += 4) {
            b.rect(0, y, sw, y + 1, argb(0x000000, 0.09f * k));
        }
        float breathe = 0.5f + 0.5f * (float) Math.sin(now / 260.0);
        int edge = (int) (sh * 0.07f);
        for (int i = 0; i < 4; i++) {
            float a = (0.18f * k * breathe) * (1f - i / 4f);
            b.rect(0, i * edge / 4f, sw, (i + 1) * edge / 4f, argb(0xFF2030, a));
            b.rect(0, sh - (i + 1) * edge / 4f, sw, sh - i * edge / 4f, argb(0xFF2030, a));
        }
        int bands = (int) ((calm ? 2 : 4) + k * (calm ? 2 : 6));
        for (int i = 0; i < bands; i++) {
            int y = rnd.nextInt(sh), h = 2 + rnd.nextInt(calm ? 5 : 14);
            int shift = rnd.nextInt(calm ? 6 : 40) - (calm ? 3 : 20);
            b.rect(Math.max(0, shift), y, sw + Math.min(0, shift), y + h, argb(i % 2 == 0 ? 0xFF2050 : 0x00E8FF, (0.07f + rnd.nextFloat() * 0.12f) * k));
        }
        int blocks = (int) (calm ? 4 : 8 + k * 14);
        for (int i = 0; i < blocks; i++) {
            int w = 4 + rnd.nextInt(24), h = 2 + rnd.nextInt(6);
            int x = rnd.nextInt(Math.max(1, sw - w)), y = rnd.nextInt(Math.max(1, sh - h));
            int c = switch (rnd.nextInt(4)) {
                case 0 -> 0xFF2050;
                case 1 -> 0x00E8FF;
                case 2 -> 0x8A5CFF;
                default -> 0x10141C;
            };
            b.rect(x, y, x + w, y + h, argb(c, 0.22f * k));
        }
        if (!calm && (now / 180) % 11 == 0) {
            int y = rnd.nextInt(sh - 30);
            b.rect(0, y, sw, y + 6 + rnd.nextInt(24), argb(0x05070C, 0.55f * k));
        }
        b.end();
        String txt = label;
        int tx = sw / 2 - f.width(txt) / 2, ty = 22;
        if (calm || (now / 130) % 5 != 0) {
            int j = calm ? 0 : rnd.nextInt(3) - 1;
            g.drawString(f, txt, tx - 1 + j, ty, 0xCC00E8FF, false);
            g.drawString(f, txt, tx + 1 - j, ty, 0xCCFF2050, false);
            g.drawString(f, txt, tx, ty, 0xFFFF6878, false);
        }
    }

    private void drawTarget(GuiGraphics g, Font f, int sw, int sh, long now, boolean motion, float op, Minecraft mc) {
        if (CapriClientState.lockId < 0 || !CapriConfig.LOCK_ENABLED.get()) {
            return;
        }
        Entity e = mc.level.getEntity(CapriClientState.lockId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            return;
        }
        double ix = Mth.lerp(partial, le.xo, le.getX()) - le.getX(), iy = Mth.lerp(partial, le.yo, le.getY()) - le.getY(), iz = Mth.lerp(partial, le.zo, le.getZ()) - le.getZ();
        AABB bb = le.getBoundingBox().move(ix, iy, iz);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean ok = false;
        for (int i = 0; i < 8; i++) {
            float[] p = project(new Vec3((i & 1) == 0 ? bb.minX : bb.maxX, (i & 2) == 0 ? bb.minY : bb.maxY, (i & 4) == 0 ? bb.minZ : bb.maxZ), sw, sh);
            if (p == null) {
                continue;
            }
            ok = true;
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        if (!ok) {
            return;
        }
        int pad = 3 + (motion ? (int) (Math.sin(now / 160.0) * 1.5) : 0);
        int x1 = (int) minX - pad, y1 = (int) minY - pad, x2 = (int) maxX + pad, y2 = (int) maxY + pad;
        boolean lowHp = le.getHealth() / Math.max(1f, le.getMaxHealth()) < 0.25f;
        int col = argb(lowHp ? CRIT : RED, op);
        int len = Math.max(6, Math.min(16, (x2 - x1) / 3));
        // corner brackets: a red outline square around the target
        corner(g, x1, y1, len, 1, 1, col);
        corner(g, x2, y1, len, -1, 1, col);
        corner(g, x1, y2, len, 1, -1, col);
        corner(g, x2, y2, len, -1, -1, col);
        g.drawString(f, "LOCK", x1, y1 - 10, col, false);

        if (CapriConfig.DIAGNOSTICS.get()) {
            drawDiag(g, f, le, Math.min(sw - 128, x2 + 6), Mth.clamp(y1, 18, sh - 90), op, mc);
        }
    }

    private static void corner(GuiGraphics g, int x, int y, int len, int dx, int dy, int c) {
        g.fill(Math.min(x, x + dx * len), y, Math.max(x, x + dx * len) + 1, y + 1, c);
        g.fill(x, Math.min(y, y + dy * len), x + 1, Math.max(y, y + dy * len) + 1, c);
    }

    private void drawDiag(GuiGraphics g, Font f, LivingEntity le, int x, int y, float op, Minecraft mc) {
        int mask = CapriConfig.DIAG_FIELDS.get();
        int h = 14 + (((mask & 1) != 0) ? 12 : 0) + (((mask & 2) != 0) ? 10 : 0) + (((mask & 8) != 0) ? 10 : 0) + (((mask & 16) != 0) ? 10 : 0);
        java.util.List<MobEffectInstance> fx = new java.util.ArrayList<>();
        if ((mask & 4) != 0) {
            fx.addAll(le.getActiveEffects());
            h += Math.min(4, fx.size()) * 10;
        }
        g.fill(x, y, x + 124, y + h, argb(DEEP, 0.78f * op));
        g.fill(x, y, x + 124, y + 1, argb(RED, op));
        g.drawString(f, f.plainSubstrByWidth(le.getDisplayName().getString(), 116), x + 4, y + 3, 0xFFFFFFFF, false);
        int cy = y + 14;
        if ((mask & 1) != 0) {
            float r = Mth.clamp(le.getHealth() / Math.max(1f, le.getMaxHealth()), 0f, 1f);
            g.fill(x + 4, cy, x + 120, cy + 5, argb(0x30384A, op));
            g.fill(x + 4, cy, x + 4 + (int) (116 * r), cy + 5, argb(r < 0.25f ? CRIT : r < 0.5f ? 0xE8C060 : MINT, op));
            g.drawString(f, String.format(Locale.ROOT, "%.0f / %.0f", le.getHealth(), le.getMaxHealth()), x + 4, cy + 6, 0xFFB8C4D0, false);
            cy += 12;
        }
        if ((mask & 2) != 0) {
            g.drawString(f, "ARMOR " + (int) le.getAttributeValue(Attributes.ARMOR) + "  TGH " + (int) le.getAttributeValue(Attributes.ARMOR_TOUGHNESS), x + 4, cy, 0xFFB8C4D0, false);
            cy += 10;
        }
        if ((mask & 8) != 0) {
            String t = "AIMING AT: ";
            if (CapriClientState.aggroId == -2) {
                t += "?";
            } else if (CapriClientState.aggroId == -1) {
                t += "nobody";
            } else {
                Entity a = mc.level.getEntity(CapriClientState.aggroId);
                t += a == null ? "?" : a == mc.player ? "YOU" : a.getName().getString();
            }
            g.drawString(f, f.plainSubstrByWidth(t, 116), x + 4, cy, CapriClientState.aggroId == (mc.player == null ? -9 : mc.player.getId()) ? 0xFF000000 | RED : 0xFFB8C4D0, false);
            cy += 10;
        }
        if ((mask & 16) != 0) {
            g.drawString(f, String.format(Locale.ROOT, "DIST %.1f m", (float) mc.player.distanceTo(le)), x + 4, cy, 0xFFB8C4D0, false);
            cy += 10;
        }
        for (int i = 0; i < Math.min(4, fx.size()); i++) {
            MobEffectInstance m = fx.get(i);
            String n = Component.translatable(m.getDescriptionId()).getString() + (m.getAmplifier() > 0 ? " " + (m.getAmplifier() + 1) : "") + " " + (m.isInfiniteDuration() ? "inf" : (m.getDuration() / 20) + "s");
            g.drawString(f, f.plainSubstrByWidth(n, 116), x + 4, cy, 0xFF000000 | (m.getEffect().isBeneficial() ? MINT : 0xFF8090), false);
            cy += 10;
        }
    }

    private static final int[][] POSE_TEX = {{208, 420}, {256, 256}, {270, 360}, {299, 168}};

    private void drawAvatarAndSubtitle(GuiGraphics g, Font f, int sw, int sh, long now, boolean motion, float op) {
        boolean avatarOn = CapriConfig.AVATAR.get() && now < CapriDialogue.avatarUntil;
        float scale = (float) (double) CapriConfig.AVATAR_SCALE.get();
        int pose = Mth.clamp(CapriDialogue.pose, 0, 3);
        int aw, ah;
        switch (pose) {
            case 1 -> {
                ah = (int) (sh * 0.44f * scale);
                aw = ah;
            }
            case 2 -> {
                ah = (int) (sh * 0.52f * scale);
                aw = ah * 270 / 360;
            }
            case 3 -> {
                aw = Math.min(300, (int) (sw * 0.22f * scale));
                ah = aw * 168 / 299;
            }
            default -> {
                ah = (int) (sh * 0.62f * scale);
                aw = ah * 208 / 420;
            }
        }
        int corner = CapriConfig.AVATAR_CORNER.get();
        boolean left = (corner & 1) == 0;
        long leftMs = CapriDialogue.avatarUntil - now;
        float a = avatarOn ? Math.min(1f, leftMs / 350f) : 0f;
        float slide = avatarOn && motion ? Math.min(1f, popAge(now) / 380f) : 1f;
        int ax = left ? 6 : sw - aw - 6;
        int ay = (corner & 2) == 0 ? sh - ah - 4 : 28;
        ax += (int) ((1f - slide) * (left ? -(aw + 20) : (aw + 20)));
        Random rnd = new Random(now / 90);

        String text = CapriDialogue.shown;
        if (CapriConfig.SUBTITLES.get() && !text.isEmpty()) {
            int w = avatarOn ? Math.min(sw - aw - 60, 260) : Math.min(sw - 40, 280);
            java.util.List<net.minecraft.util.FormattedCharSequence> lines = f.split(Component.literal(text), Math.max(80, w));
            int h = lines.size() * 11 + 10;
            int x = avatarOn ? (left ? ax + aw + 4 : ax - w - 14) : sw / 2 - w / 2 - 5;
            int y = sh - 70 - h;
            x = Mth.clamp(x, 4, Math.max(4, sw - w - 16));
            int bx2 = x + w + 12, by2 = y + h;
            g.fill(x, y, bx2, by2, 0xE6080B12);
            int bc = 0xFF000000 | ORANGE;
            g.fill(x, y, bx2, y + 1, bc);
            g.fill(x, by2 - 1, bx2, by2, bc);
            g.fill(x, y, x + 1, by2, bc);
            g.fill(bx2 - 1, y, bx2, by2, bc);
            g.fill(bx2 - 8, by2 + 1, bx2, by2 + 2, bc);
            g.fill(x, y - 2, x + 8, y - 1, bc);
            int tw = f.width("CAPRI // LINK") + 14;
            g.fill(x + 6, y - 6, x + 6 + tw, y + 5, 0xF0080B12);
            g.fill(x + 6, y - 6, x + 6 + tw, y - 5, bc);
            g.fill(x + 6, y - 6, x + 7, y + 5, bc);
            g.fill(x + 6 + tw - 1, y - 6, x + 6 + tw, y + 5, bc);
            g.fill(x + 10, y - 2, x + 14, y + 2, bc);
            g.drawString(f, "CAPRI", x + 17, y - 4, bc, false);
            g.drawString(f, "// LINK", x + 17 + f.width("CAPRI "), y - 4, 0xFF8A93A6, false);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(f, lines.get(i), x + 7, y + 7 + i * 11, 0xFFFFFFFF, true);
            }
        }
        if (!avatarOn) {
            return;
        }
        float al = Math.min(1f, a);
        ResourceLocation tex = pose == 1 ? BUST : pose == 2 ? FACE : pose == 3 ? SCENE : FULL;
        int tw = POSE_TEX[pose][0], th = POSE_TEX[pose][1];
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float bob = motion ? (float) Math.sin(now / 420.0) * 2.5f : 0f;
        g.pose().pushPose();
        if (motion && (pose == 1 || pose == 2)) {
            g.pose().translate(ax + aw / 2f, ay + ah / 2f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(CapriDialogue.tilt * 0.6f + (float) Math.sin(now / 700.0) * 1.2f));
            g.pose().translate(-(ax + aw / 2f), -(ay + ah / 2f), 0);
        }
        g.pose().translate(0, bob, 0);
        if (pose == 3) {
            // camera-feed frame
            g.fill(ax - 3, ay - 12, ax + aw + 3, ay + ah + 3, 0xE0080B12);
            g.fill(ax - 3, ay - 12, ax + aw + 3, ay - 11, 0xFF000000 | ORANGE);
            g.drawString(f, "LIVE FEED // CAPRI", ax, ay - 10, 0xFF000000 | ORANGE, false);
            g.setColor(1f, 1f, 1f, al);
            g.blit(tex, ax, ay, aw, ah, 0f, 0f, tw, th, tw, th);
            g.setColor(1f, 1f, 1f, 1f);
            Batch sb = new Batch(g);
            for (int ly = ay + (int) ((now / 60) % 4); ly < ay + ah; ly += 4) {
                sb.rect(ax, ly, ax + aw, ly + 1, argb(0x000000, 0.18f * al));
            }
            sb.end();
        } else {
            // soft light under her so she never reads dark
            g.fillGradient(ax - 4, ay + ah / 3, ax + aw + 4, ay + ah, argb(0xBFEFFF, 0f), argb(0xBFEFFF, 0.16f * al));
            // purple chromatic ghost
            int off = motion ? (4 + (int) (Math.sin(now / 110.0) * 2)) : 4;
            g.setColor(0.72f, 0.55f, 1f, 0.55f * al);
            g.blit(tex, ax + (left ? off : -off), ay, aw, ah, 0f, 0f, tw, th, tw, th);
            // main body, bright, in glitchy strips
            g.setColor(1f, 1f, 1f, al);
            int strip = Math.max(8, ah / 22);
            for (int sy = 0; sy < ah; sy += strip) {
                int hh = Math.min(strip, ah - sy);
                int shift = 0;
                if (motion && !CapriConfig.REDUCED_FLASH.get() && rnd.nextInt(16) == 0) {
                    shift = rnd.nextInt(22) - 11;
                }
                int v0 = sy * th / ah, vh = Math.max(1, hh * th / ah);
                g.blit(tex, ax + shift, ay + sy, aw, hh, 0f, (float) v0, tw, vh, tw, th);
            }
            // additive pass: lifts her brightness without touching the art
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            g.setColor(1f, 0.92f, 0.96f, 0.30f * al);
            g.blit(tex, ax, ay, aw, ah, 0f, 0f, tw, th, tw, th);
            RenderSystem.defaultBlendFunc();
            g.setColor(1f, 1f, 1f, 1f);
            if (motion) {
                Batch sb = new Batch(g);
                for (int i = 0; i < 22; i++) {
                    int px = ax + 6 + rnd.nextInt(Math.max(1, aw - 12)), py = ay + 6 + rnd.nextInt(Math.max(1, ah - 12));
                    sb.rect(px, py, px + 2, py + 2, argb(0xFFFFFF, 0.6f * al));
                }
                int sy = ay + (int) ((now / 12) % Math.max(1, ah));
                sb.rect(ax, sy, ax + aw, sy + 1, argb(PURPLE, 0.35f * al));
                sb.end();
            }
        }
        g.pose().popPose();
        RenderSystem.disableBlend();
    }

    private static long popStart;
    private static long lastUntil;

    private static long popAge(long now) {
        if (CapriDialogue.avatarUntil != lastUntil) {
            if (now - popStart > 8000 || lastUntil < now) {
                popStart = now;
            }
            lastUntil = CapriDialogue.avatarUntil;
        }
        return now - popStart;
    }
}
