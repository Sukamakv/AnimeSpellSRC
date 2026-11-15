package com.example.jjkspells.capri.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.capri.CapriConfig;
import com.example.jjkspells.capri.CapriNet;
import com.example.jjkspells.capri.CapriRegistry;
import com.example.jjkspells.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** Keybinds, the lock controller, projection capture and the "@capri" chat hook. */
public final class CapriClient {
    public static final KeyMapping PANEL = new KeyMapping("key.jjk_capri.panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_ALT, "key.categories.jjk_capri");
    public static final KeyMapping AUTO = new KeyMapping("key.jjk_capri.autocast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.jjk_capri");
    public static final KeyMapping LOCK = new KeyMapping("key.jjk_capri.lock", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.jjk_capri");
    public static final KeyMapping SWITCH = new KeyMapping("key.jjk_capri.switch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.jjk_capri");
    public static final KeyMapping HOLD = new KeyMapping("key.jjk_capri.hold", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.jjk_capri");
    public static final KeyMapping ONS = new KeyMapping("key.jjk_capri.onslaught", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.jjk_capri");
    public static final KeyMapping ONS_MODE = new KeyMapping("key.jjk_capri.onslaught_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.jjk_capri");
    public static final KeyMapping STOP = new KeyMapping("key.jjk_capri.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, "key.categories.jjk_capri");

    private static int noSight;
    private static boolean lowArmed = true;
    private static boolean breakSent;
    private static final Map<Integer, Integer> BAND = new HashMap<>();

    private CapriClient() {
    }

    public static void send(CapriNet.Action a) {
        ModNetwork.CHANNEL.sendToServer(a);
    }

    public static void sendBreak() {
        send(new CapriNet.Action(CapriNet.OP_SET_BREAK, "", CapriConfig.LOCK_BREAK.get(), null));
    }

    // ------------------------------------------------------------------ lock

    private static LivingEntity pick(LocalPlayer p, int skipId) {
        double range = CapriConfig.LOCK_RANGE.get();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = Math.cos(Math.toRadians(35));
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(range))) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isInvisible() || e.getId() == skipId || e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                continue;
            }
            Vec3 d = le.getBoundingBox().getCenter().subtract(p.getEyePosition());
            double dist = d.length();
            if (dist > range || !p.hasLineOfSight(le)) {
                continue;
            }
            double dot = d.normalize().dot(look) - dist * 0.0015;
            if (dot > bestScore) {
                bestScore = dot;
                best = le;
            }
        }
        return best;
    }

    private static void lockOn(LocalPlayer p, LivingEntity t) {
        CapriClientState.lockId = t.getId();
        CapriClientState.aggroId = -2;
        BAND.put(t.getId(), 4);
        noSight = 0;
        send(new CapriNet.Action(CapriNet.OP_LOCK, "", t.getId(), null));
        CapriDialogue.play(CapriRegistry.LOCK.get(), 1f, (float) (double) CapriConfig.VOICE_VOLUME.get() * 0.7f);
        CapriDialogue.event("lock", t.getName().getString());
    }

    public static void unlock(boolean sayLost) {
        if (CapriClientState.lockId >= 0) {
            CapriClientState.lockId = -1;
            send(new CapriNet.Action(CapriNet.OP_LOCK, "", -1, null));
            if (sayLost) {
                CapriDialogue.play(CapriRegistry.LOST.get(), 1f, (float) (double) CapriConfig.VOICE_VOLUME.get() * 0.7f);
                CapriDialogue.event("lost", "");
            }
        }
    }

    private static void lockTick(Minecraft mc, LocalPlayer p) {
        if (CapriClientState.lockId < 0) {
            return;
        }
        Entity e = mc.level.getEntity(CapriClientState.lockId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            if (e instanceof LivingEntity dead && dead.isDeadOrDying()) {
                CapriDialogue.event("dead", dead.getName().getString());
            }
            CapriClientState.lockId = -1;
            send(new CapriNet.Action(CapriNet.OP_LOCK, "", -1, null));
            return;
        }
        if (p.distanceTo(le) >= (CapriOnslaught.on ? Math.max(80, CapriConfig.LOCK_BREAK.get()) : CapriConfig.LOCK_BREAK.get())) {
            unlock(true);
            return;
        }
        if (CapriOnslaught.on) {
            noSight = 0;
        } else if (!p.hasLineOfSight(le)) {
            if (++noSight > CapriConfig.LOCK_LOS_SECONDS.get() * 20) {
                unlock(true);
                return;
            }
        } else {
            noSight = 0;
        }
        // health thresholds
        float r = le.getHealth() / Math.max(1f, le.getMaxHealth());
        int band = r <= 0.10f ? 0 : r <= 0.25f ? 1 : r <= 0.5f ? 2 : r <= 0.75f ? 3 : 4;
        int last = BAND.getOrDefault(le.getId(), 4);
        if (band < last) {
            BAND.put(le.getId(), band);
            CapriDialogue.event(band == 0 ? "dying" : band == 1 ? "t25" : band == 2 ? "t50" : "t75", le.getName().getString());
        }
        // gentle camera assist
        double k = CapriConfig.LOCK_TRACKING.get();
        if (k > 0 && mc.screen == null && !mc.player.isSpectator()) {
            Vec3 d = le.getBoundingBox().getCenter().subtract(p.getEyePosition());
            float ty = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float tp = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            float f = (float) (0.10 * k);
            p.setYRot(p.getYRot() + Mth.wrapDegrees(ty - p.getYRot()) * f);
            p.setXRot(Mth.clamp(p.getXRot() + (tp - p.getXRot()) * f, -90f, 90f));
        }
    }

    // ------------------------------------------------------------------ events

    @Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(PANEL);
            e.register(AUTO);
            e.register(LOCK);
            e.register(SWITCH);
            e.register(HOLD);
            e.register(ONS);
            e.register(ONS_MODE);
            e.register(STOP);
        }

        @SubscribeEvent
        public static void overlay(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("capri", CapriHud.INSTANCE);
        }
    }

    @Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
    public static final class Forge {
        private static boolean loaded;

        @SubscribeEvent
        public static void level(RenderLevelStageEvent e) {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                CapriHud.PROJ.set(e.getProjectionMatrix());
                CapriHud.VIEW.set(e.getPoseStack().last().pose());
                CapriHud.camPos = e.getCamera().getPosition();
                CapriHud.partial = e.getPartialTick();
                CapriHud.captured = true;
            }
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (!loaded) {
                try {
                    CapriInputAuto.load();
                    loaded = true;
                } catch (IllegalStateException ex) {
                    // config not loaded yet
                }
            }
            CapriDialogue.tick();
            if (p == null || mc.level == null) {
                return;
            }
            while (PANEL.consumeClick()) {
                if (CapriClientState.linked) {
                    mc.setScreen(new NeuralControlScreen());
                } else {
                    CapriDialogue.say("I'm not linked. Put me in an Absolute Relic slot first.", CapriDialogue.NORMAL, false);
                }
            }
            while (AUTO.consumeClick()) {
                if (CapriClientState.linked) {
                    send(new CapriNet.Action(CapriNet.OP_AUTOCAST, "", CapriClientState.auto ? 0 : 1, null));
                }
            }
            while (ONS.consumeClick()) {
                if (CapriClientState.linked && mc.screen == null) {
                    send(new CapriNet.Action(CapriNet.OP_ONS, "", 2, null));
                }
            }
            while (ONS_MODE.consumeClick()) {
                if (CapriClientState.linked && mc.screen == null) {
                    send(new CapriNet.Action(CapriNet.OP_ONS_MODE, "", -1, null));
                }
            }
            while (HOLD.consumeClick()) {
                if (CapriClientState.linked) {
                    CapriInputAuto.holdLeft = !CapriInputAuto.holdLeft;
                    if (!CapriInputAuto.holdLeft) {
                        mc.options.keyAttack.setDown(false);
                    }
                    CapriDialogue.event(CapriInputAuto.holdLeft ? "hold_on" : "hold_off", "");
                }
            }
            if (CapriClientState.linked && p.isAlive()) {
                float hr = p.getHealth() / Math.max(1f, p.getMaxHealth());
                if (hr <= 0.35f && lowArmed) {
                    lowArmed = false;
                    CapriDialogue.event("hurt", "");
                } else if (hr > 0.5f && !lowArmed) {
                    lowArmed = true;
                    CapriDialogue.event("healed", "");
                }
            }
            while (STOP.consumeClick()) {
                CapriInputAuto.stopAll(true);
            }
            while (LOCK.consumeClick()) {
                if (CapriClientState.linked && mc.screen == null) {
                    if (CapriClientState.lockId >= 0) {
                        unlock(false);
                        CapriDialogue.play(CapriRegistry.OFF.get(), 1f, (float) (double) CapriConfig.VOICE_VOLUME.get() * 0.6f);
                    } else {
                        LivingEntity t = pick(p, -1);
                        if (t != null) {
                            lockOn(p, t);
                        }
                    }
                }
            }
            while (SWITCH.consumeClick()) {
                if (CapriClientState.linked && mc.screen == null) {
                    LivingEntity t = pick(p, CapriClientState.lockId);
                    if (t != null) {
                        lockOn(p, t);
                    }
                }
            }
            if (CapriClientState.linked && !breakSent) {
                breakSent = true;
                sendBreak();
            } else if (!CapriClientState.linked) {
                breakSent = false;
            }
            if (CapriClientState.linked) {
                lockTick(mc, p);
            }
            CapriInputAuto.tick();
            if (p.tickCount % 1200 == 0) {
                BAND.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            }
        }

        @SubscribeEvent
        public static void chat(ClientChatEvent e) {
            String m = e.getMessage();
            if (m.regionMatches(true, 0, "@capri", 0, 6)) {
                e.setCanceled(true);
                Minecraft mc = Minecraft.getInstance();
                mc.gui.getChat().addRecentChat(m);
                if (!CapriClientState.linked) {
                    mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("[Capri is not linked: wear her in an Absolute Relic slot]").withStyle(net.minecraft.ChatFormatting.GRAY));
                    return;
                }
                String body = m.substring(6).trim();
                mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("<You> " + m));
                CapriDialogue.say(CapriDialogue.reply(body), CapriDialogue.HIGH, true);
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            CapriInputAuto.stopAll(false);
            CapriOnslaught.reset();
            CapriClientState.clear();
            CapriDialogue.reset();
            CapriHud.captured = false;
            BAND.clear();
            lowArmed = true;
        }
    }
}
