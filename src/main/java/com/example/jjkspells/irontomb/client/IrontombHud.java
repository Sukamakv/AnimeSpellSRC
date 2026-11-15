package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.IrontombAttack;
import com.example.jjkspells.irontomb.IrontombCut;
import com.example.jjkspells.irontomb.entity.IrontombEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cinematic HUD: letterbox, vignette, title cards, attack banners, Amphorean Hatred bar, flashes and crack overlay. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class IrontombHud {
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(JJKSpells.MODID, "irontomb_hud");
    private static final ResourceLocation VIGNETTE = new ResourceLocation(JJKSpells.MODID, "textures/irontomb/vignette.png");
    private static final ResourceLocation CRACK = new ResourceLocation(JJKSpells.MODID, "textures/irontomb/crack.png");

    private IrontombHud() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll(OVERLAY_ID.getPath(), IrontombHud::render);
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void tinted(GuiGraphics g, ResourceLocation tex, int texSize, int w, int h, float a, float r, float gr, float b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(r, gr, b, a);
        g.blit(tex, 0, 0, w, h, 0, 0, texSize, texSize, texSize, texSize);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        IrontombEntity b = CutsceneCamera.boss();
        if (b == null || mc.player == null) {
            return;
        }
        Font font = mc.font;
        int cut = b.getCut();
        float ct = (float) ((b.getCutT() + pt) * IrontombCut.scale(b.getCut()));
        float at = (float) ((b.getAtkT() + pt) * IrontombAttack.scale(b.getAttack()));
        boolean active = CutsceneCamera.isActive();

        // vignette: constant faint red in fights, heavy during cutscenes
        float vig = active ? 0.85f : 0.28f + (b.getPhase() - 1) * 0.1f;
        tinted(g, VIGNETTE, 256, w, h, vig, 0.55f, 0.02f, 0.05f);

        // letterbox
        if (active) {
            int len = IrontombCut.AUTH_LENGTH[Mth.clamp(cut, 0, 8)];
            float in = Fx.ramp(ct, 0, 14), out = 1f - Fx.ramp(ct, len - 12, len);
            int bar = (int) (h * 0.115f * Math.min(in, out));
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            g.fill(0, bar, w, bar + 1, argb(0.7f * Math.min(in, out), 0xC8A24A));
            g.fill(0, h - bar - 1, w, h - bar, argb(0.7f * Math.min(in, out), 0xC8A24A));
        }

        // title cards
        if (!IrontombPrefs.cutscenes) {
            // cutscenes disabled by this player: no cards
        } else if (cut == IrontombCut.INTRO) {
            float a = Fx.window(ct, 100, 112, 148, 164);
            card(g, font, w, h, "IRONTOMB", "Anti-Creator  —  Hatred Inundate", a, 0xFFD36A);
        } else if (cut == IrontombCut.PHASE2) {
            card(g, font, w, h, "ACT II", "The Fall of Erudition", Fx.window(ct, 55, 66, 95, 110), 0xFF5A5A);
        } else if (cut == IrontombCut.PHASE3) {
            card(g, font, w, h, "ACT III", "Resist and Destroy", Fx.window(ct, 55, 66, 120, 136), 0xC88CFF);
        } else if (cut == IrontombCut.DEATH) {
            card(g, font, w, h, "IRONTOMB", "Return to Nothingness", Fx.window(ct, 85, 100, 140, 158), 0xFFFFFF);
        } else if (cut == IrontombCut.SINGULARITY) {
            card(g, font, w, h, "SINGULARITY", "Heaven Pressed Into a Single Point", Fx.window(ct, 24, 40, 100, 124), 0xC8A0FF);
        } else if (cut == IrontombCut.ERASURE) {
            card(g, font, w, h, "ERASURE", "Erase the Existence of All", Fx.window(ct, 70, 90, 150, 176), 0xFF5A5A);
            card(g, font, w, h, "ERASED", "Nothing remains.", Fx.window(ct, 498, 512, 526, 538), 0xFF3A4A);
        }

        // attack banner
        int atk = b.getAttack();
        if (atk > 0 && atk < IrontombAttack.TITLES.length && !active) {
            float a = Fx.window(at, 0, 8, 38, 56);
            if (a > 0.01f && !IrontombAttack.TITLES[atk].isEmpty()) {
                String t = IrontombAttack.TITLES[atk];
                int tw = font.width(t);
                g.pose().pushPose();
                g.pose().translate(w / 2f, h * 0.2f, 0);
                g.pose().scale(1.6f, 1.6f, 1f);
                g.fill(-tw / 2 - 14, -4, tw / 2 + 14, 13, argb(0.45f * a, 0x000000));
                g.drawCenteredString(font, Component.literal(t), 0, 0, argb(a, 0xFFD9A0));
                g.pose().popPose();
            }
        }

        // Sword of Erasure countdown: shown to everyone while the seal can still be broken
        if (!active && b.getErase() > 0) {
            int bw = Math.min(300, w - 40), x = (w - bw) / 2, y = 52;
            float prog = SpecialFx.eraseProgress(b);
            float blink = 0.6f + 0.4f * Mth.sin((b.tickCount + pt) * 0.4f);
            g.fill(x - 2, y - 2, x + bw + 2, y + 8, 0xAA000000);
            g.fill(x, y, x + bw, y + 6, 0xFF1A0508);
            g.fill(x, y, x + (int) (bw * prog), y + 6, argb(1f, 0xFF2A3A));
            g.drawCenteredString(font, Component.literal("SWORD OF ERASURE  " + (b.getErase() / 20 + 1) + "s"), w / 2, y + 10, argb(blink, 0xFF5060));
            g.drawCenteredString(font, Component.literal("Put BOTH satellites into Grave Wound together to shatter it"), w / 2, y + 22, 0xFFD0D0);
        }

        // hatred bar
        if (!active) {
            int bw = Math.min(260, w - 40), x = (w - bw) / 2, y = 26;
            float hat = b.getHatred() / 100f;
            g.fill(x - 2, y - 2, x + bw + 2, y + 8, 0xAA000000);
            g.fill(x, y, x + bw, y + 6, 0xFF22060A);
            int fill = (int) (bw * hat);
            int col = hat > 0.8f ? 0xFFFF3030 : 0xFFC01830;
            g.fill(x, y, x + fill, y + 6, col);
            g.fill(x, y, x + fill, y + 2, 0x66FFFFFF);
            g.drawCenteredString(font, Component.literal("Amphorean Hatred  " + (int) b.getHatred() + "%"), w / 2, y + 10, 0xFFE8B0);
        }

        // locked: cage tint and crack overlay
        if (b.getLockLeft() > 0 && b.getLockId() == mc.player.getId()) {
            tinted(g, VIGNETTE, 256, w, h, 0.7f, 0.9f, 0.7f, 0.15f);
            g.drawCenteredString(font, Component.literal("ACTION LOCK  " + (b.getLockLeft() / 20 + 1) + "s"), w / 2, h - 60, 0xFFD36A);
        }
        float crack = 0f;
        if (atk == IrontombAttack.IMMOLATE) {
            crack = Fx.ramp(at, 140, 156) * (1f - Fx.ramp(at, 176, 210));
        } else if (atk == IrontombAttack.GENESIS) {
            crack = Fx.ramp(at, 228, 236) * (1f - Fx.ramp(at, 262, 300));
        } else if (atk == IrontombAttack.SINGULARITY) {
            crack = Fx.ramp(at, 329, 335) * (1f - Fx.ramp(at, 360, 405));
        }
        if (crack > 0.01f) {
            tinted(g, CRACK, 1024, w, h, crack, 1f, 1f, 1f);
        }

        // flashes: white on impact moments
        float flash = 0f;
        if (atk == IrontombAttack.CAST_DOWN) {
            flash = Fx.window(at, 33, 34, 35, 46) * 0.5f;
        } else if (atk == IrontombAttack.CROWN) {
            flash = Fx.window(at, 83, 84, 86, 104) * 0.8f;
        } else if (atk == IrontombAttack.IMMOLATE) {
            flash = Fx.window(at, 148, 150, 152, 190) * 1.0f;
        } else if (atk == IrontombAttack.GENESIS) {
            flash = Fx.window(at, 230, 232, 236, 290) * 1.0f;
        } else if (atk == IrontombAttack.SINGULARITY) {
            flash = Fx.window(at, 328, 331, 336, 372) * 1.0f;
        } else if (atk == IrontombAttack.ERASURE) {
            // total white-out while the world is erased, then it drains away into the void
            flash = Fx.window(at, 395, 399, 468, 512) * 1.0f;
        }
        if (cut == IrontombCut.PHASE2 || cut == IrontombCut.PHASE3) {
            flash = Math.max(flash, Fx.window(ct, 58, 61, 63, 80) * 0.8f);
        } else if (cut == IrontombCut.DEATH) {
            flash = Math.max(flash, Fx.window(ct, 100, 106, 112, 135) * 0.9f);
        }
        if (flash > 0.01f) {
            g.fill(0, 0, w, h, argb(flash, 0xFFF4E0));
        }
    }

    private static void card(GuiGraphics g, Font font, int w, int h, String title, String sub, float a, int rgb) {
        if (a < 0.02f) {
            return;
        }
        int cy = (int) (h * 0.72f);
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy, 0);
        g.pose().scale(4f, 4f, 1f);
        g.drawCenteredString(font, Component.literal(title), 0, -9, argb(a, rgb));
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy + 24, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawCenteredString(font, Component.literal(sub), 0, 0, argb(a, 0xFFFFFF));
        g.pose().popPose();
        int lw = (int) (w * 0.18f * a);
        g.fill(w / 2 - lw, cy + 16, w / 2 + lw, cy + 17, argb(a, rgb));
    }
}
