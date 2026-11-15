package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

/**
 * Client side of Za Warudo. The server tells us (TimeStopPacket) what stage the time stop is in:
 *   chant   - a faint golden glow gathers.
 *   flash   - the whole frame is shown in NEGATIVE colour.
 *   stopped - the frame drains to dark grey monochrome; everything except the caster stops moving here too
 *             (entity ticks are cancelled, projectiles held), and a frozen local player cannot move, look or act.
 *   resume  - one more brief negative flash, then the colour returns.
 * The negative / grey looks are vanilla post-processing chains (assets/jjk_spells/shaders/post/*.json).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public class TimeStopClient {
    private static int state = 0;
    private static int casterId = -1;
    private static boolean freezePlayers = true;
    private static int ticksLeft = 0;
    private static int stateTick = 0;
    private static int clientTicks = 0;
    private static int lastUpdate = 0;
    private static int endFlash = 0;
    private static float punch = 0f;
    private static String applied = null;

    private static boolean locked = false;
    private static float lockedYaw, lockedPitch;

    public static boolean isStopped() {
        return state == 3;
    }

    /** Called from the network packet. */
    public static void receive(int newState, int caster, int ticks, boolean freezePlayersToo) {
        lastUpdate = clientTicks;
        casterId = caster;
        freezePlayers = freezePlayersToo;
        if (newState == state) {
            if (newState == 3) ticksLeft = ticks;
            return;
        }
        int old = state;
        state = newState;
        stateTick = 0;
        if (newState == 3) {
            ticksLeft = ticks;
            punch = 1f;
        } else if (newState == 0 && old == 3) {
            endFlash = 6;
            punch = 0.7f;
        } else if (newState == 2) {
            punch = 0.6f;
        }
        refreshEffect();
    }

    private static void reset() {
        state = 0;
        endFlash = 0;
        refreshEffect();
    }

    private static void refreshEffect() {
        String want = null;
        if (endFlash > 0 || state == 2) want = "time_negative";
        else if (state == 3) want = "time_gray";
        apply(want);
    }

    private static void apply(String chain) {
        if (Objects.equals(chain, applied)) return;
        applied = chain;
        try {
            GameRenderer gr = Minecraft.getInstance().gameRenderer;
            if (chain == null) gr.shutdownEffect();
            else gr.loadEffect(new ResourceLocation(JJKSpells.MODID, "shaders/post/" + chain + ".json"));
        } catch (Exception ignored) {
            // post-processing unavailable (for example some shader packs): the overlay below still tints the screen
        }
    }

    /** Is this entity held still by the current time stop? */
    private static boolean frozen(Entity e) {
        if (state != 3 || e == null || e.getId() == casterId || e.isSpectator()) return false;
        if (e instanceof Player && !freezePlayers) return false;
        return !(e instanceof LivingEntity l && l.hasEffect(ModEffects.SIMPLE_DOMAIN.get()));
    }

    // ---- ticking -------------------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            if (state == 3 && mc.level != null) {
                // projectiles, items and the like hang motionless
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (!(e instanceof LivingEntity) && frozen(e)) e.setDeltaMovement(Vec3.ZERO);
                }
            }
            LocalPlayer p = mc.player;
            if (p != null && frozen(p)) {
                if (!locked) {
                    locked = true;
                    lockedYaw = p.getYRot();
                    lockedPitch = p.getXRot();
                }
                applyLock(p);
            } else {
                locked = false;
            }
            return;
        }
        clientTicks++;
        stateTick++;
        punch *= 0.78f;
        if (mc.level == null || mc.player == null) {
            if (state != 0 || endFlash > 0) reset();
            return;
        }
        // lost contact with the server (it ends the state itself; this is only a safety net)
        if (state != 0 && clientTicks - lastUpdate > 100) reset();
        if (state == 3 && ticksLeft > 0) ticksLeft--;
        if (endFlash > 0 && --endFlash == 0) refreshEffect();
    }

    private static void applyLock(LocalPlayer p) {
        p.setYRot(lockedYaw);
        p.setXRot(lockedPitch);
        p.yRotO = lockedYaw;
        p.xRotO = lockedPitch;
        p.yHeadRot = lockedYaw;
        p.yHeadRotO = lockedYaw;
    }

    /** Nothing living moves, animates or thinks while time is stopped. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide && frozen(e)) event.setCanceled(true);
    }

    // ---- the frozen player cannot do anything ----------------------------------------------------
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (locked && p != null && frozen(p)) applyLock(p);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!locked || p == null || !frozen(p)) return;
        float pt = (float) event.getPartialTick();
        float sign = Minecraft.getInstance().options.getCameraType().isMirrored() ? -1f : 1f;
        event.setYaw(event.getYaw() + Mth.wrapDegrees(lockedYaw - p.getViewYRot(pt)));
        event.setPitch(event.getPitch() + sign * (lockedPitch - p.getViewXRot(pt)));
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!frozen(event.getEntity())) return;
        var in = event.getInput();
        in.leftImpulse = 0;
        in.forwardImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (frozen(Minecraft.getInstance().player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // ---- looks -----------------------------------------------------------------------------------
    /** The camera jolts inward at the flash and when time stops / resumes. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (punch > 0.01f) event.setFOV(event.getFOV() * (1.0 - 0.14 * punch));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (state == 0 && endFlash == 0)) return;
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        var g = event.getGuiGraphics();
        if (state == 1) {
            // a golden glow gathers at the screen edges while the clock is chanted into being
            float k = Mth.clamp(stateTick / 80f, 0f, 1f);
            int a = (int) (70 * k);
            g.fillGradient(0, 0, w, h, (a << 24) | 0xFFC040, ((int) (a * 0.35f) << 24) | 0xFFA020);
        } else if (state == 3) {
            // the world is dim: a dark grey veil, heavier at the top and bottom
            float pulse = 0.9f + 0.1f * Mth.sin((mc.player.tickCount + event.getPartialTick()) * 0.12f);
            int top = (int) (95 * pulse), bottom = (int) (80 * pulse);
            g.fillGradient(0, 0, w, h, (top << 24) | 0x0C0C10, (bottom << 24) | 0x0C0C10);
            // remaining time: a thin golden bar
            int bw = 120;
            int x = (w - bw) / 2, y = 12;
            float frac = Mth.clamp(ticksLeft / (float) Math.max(1, ticksLeft + stateTick), 0f, 1f);
            g.fill(x - 1, y - 1, x + bw + 1, y + 5, 0x99000000);
            g.fill(x, y, x + (int) (bw * frac), y + 4, 0xFFE8B84A);
            g.drawCenteredString(mc.font, String.format("%.1f", ticksLeft / 20f), w / 2, y + 8, 0xFFE8B84A);
        }
    }
}
