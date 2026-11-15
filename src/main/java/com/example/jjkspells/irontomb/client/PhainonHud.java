package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.PhainonAttack;
import com.example.jjkspells.irontomb.entity.PhainonEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Phainon's screen effects: the name card when he wakes, an attack banner, golden vignette and white-outs on the big hits. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PhainonHud {
    private static final ResourceLocation VIGNETTE = new ResourceLocation(JJKSpells.MODID, "textures/irontomb/vignette.png");

    private PhainonHud() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("phainon_hud", PhainonHud::render);
    }

    private static PhainonEntity nearest(Minecraft mc) {
        PhainonEntity best = null;
        double bd = 160.0 * 160.0;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof PhainonEntity p && p.isAwake()) {
                double d = p.distanceToSqr(mc.player);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void text(GuiGraphics g, Font font, String s, int cx, int y, float scale, int rgb, float a) {
        if (a < 0.02f) {
            return;
        }
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, s, (int) (cx / scale), (int) (y / scale), argb(a, rgb));
        g.pose().popPose();
    }

    private static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        PhainonEntity p = nearest(mc);
        if (p == null) {
            return;
        }
        Font font = mc.font;
        double dist = Math.sqrt(p.distanceToSqr(mc.player));
        float prox = Mth.clamp(1f - (float) (dist - 40.0) / 100f, 0.15f, 1f);
        int atk = p.getAttack(), ev = p.getEvent();
        float t = atk == 0 ? 0f : p.getAtkT() + pt;
        float et = ev == 0 ? 0f : p.getEvT() + pt;

        // golden vignette while a big attack gathers
        float vig = 0.12f;
        if (atk == PhainonAttack.JUDGMENT) {
            vig = 0.15f + 0.5f * Fx.window(t, 30, 60, 100, 114);
        } else if (atk == PhainonAttack.SUNFALL) {
            vig = 0.15f + 0.4f * Fx.window(t, 20, 50, 70, 82);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 0.72f, 0.2f, vig * prox);
        g.blit(VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
        g.setColor(1f, 1f, 1f, 1f);

        // name card when he wakes
        if (ev == PhainonAttack.EV_INTRO) {
            float a = Fx.window(et, 26, 40, 76, 94);
            text(g, font, "PHAINON", w / 2, (int) (h * 0.30f), 4.0f, 0xFFD770, a);
            text(g, font, "Dawnbreaker Emanator", w / 2, (int) (h * 0.30f) + 44, 1.6f, 0xFFF0C0, a);
        } else if (ev == PhainonAttack.EV_PHASE2 || ev == PhainonAttack.EV_PHASE3) {
            float a = Fx.window(et, 20, 34, 60, 76);
            text(g, font, ev == PhainonAttack.EV_PHASE2 ? "THE DARK WING UNFURLS" : "THE SUN CROWN IGNITES", w / 2, (int) (h * 0.30f), 2.4f, 0xFFD770, a);
        } else if (ev == PhainonAttack.EV_DEATH) {
            text(g, font, "THE DAWN FALLS", w / 2, (int) (h * 0.30f), 3.0f, 0xFFD770, Fx.window(et, 20, 40, 100, 130));
        }

        // attack banner
        if (atk > 0 && atk < PhainonAttack.TITLES.length) {
            float a = Fx.window(t, 0, 6, 40, 56);
            if (atk == PhainonAttack.STAGGER) {
                a = Fx.window(t, 0, 6, 60, 76);
            }
            text(g, font, PhainonAttack.TITLES[atk], w / 2, (int) (h * 0.80f), atk == PhainonAttack.JUDGMENT ? 2.4f : 1.7f, atk == PhainonAttack.STAGGER ? 0xFF9A55 : 0xFFD770, a);
        }

        // white-outs
        float flash = 0f;
        if (atk == PhainonAttack.SUNFALL) {
            flash = 0.85f * Fx.window(t, PhainonAttack.SUN_HIT, PhainonAttack.SUN_HIT + 1, PhainonAttack.SUN_HIT + 4, PhainonAttack.SUN_HIT + 16);
        } else if (atk == PhainonAttack.JUDGMENT) {
            flash = 1.0f * Fx.window(t, PhainonAttack.JUDG_HIT, PhainonAttack.JUDG_HIT + 1, PhainonAttack.JUDG_HIT + 7, PhainonAttack.JUDG_HIT + 24);
        }
        if (ev == PhainonAttack.EV_INTRO) {
            flash = Math.max(flash, 0.6f * Fx.window(et, 74, 75, 77, 92));
        } else if (ev == PhainonAttack.EV_PHASE2) {
            flash = Math.max(flash, 0.5f * Fx.window(et, 26, 27, 29, 42));
        } else if (ev == PhainonAttack.EV_PHASE3) {
            flash = Math.max(flash, 0.7f * Fx.window(et, 44, 45, 47, 64));
        } else if (ev == PhainonAttack.EV_DEATH) {
            flash = Math.max(flash, 0.95f * Fx.window(et, 96, 106, 110, 130));
        }
        if (flash > 0.01f) {
            g.fill(0, 0, w, h, argb(flash * prox, 0xFFF2C8));
        }
    }
}
