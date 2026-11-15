package com.example.jjkspells.capri.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.capri.CapriConfig;
import com.example.jjkspells.capri.CapriNet;
import com.example.jjkspells.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Onslaught Mode, client half. The server owns the state (on/off, mode, the two minutes, auto-cast, the Cyberpsychosis bill);
 * this class is the autopilot: it picks a target, steers the camera, drives the movement keys and holds left click.
 * The player keeps exactly two controls: toggle Onslaught and switch mode (see CapriClient).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, value = Dist.CLIENT)
public final class CapriOnslaught {
    private static final Random R = new Random();

    public static boolean on;
    public static int mode; // 0 psychopath, 1 strategic
    public static long endMs;

    private static int targetId = -1, priorityId = -1, clickTick;
    private static float fwd, strafe;
    private static boolean jump, sprint;
    private static int flipIn, dodge, dodgeSign = 1, orbit = 1, clearCool;
    private static boolean hadTarget;
    private static Double savedSens;

    private CapriOnslaught() {
    }

    public static int secondsLeft() {
        return (int) Math.max(0, (endMs - System.currentTimeMillis() + 999) / 1000);
    }

    public static String modeName() {
        return mode == 0 ? "PSYCHOPATH" : "STRATEGIC";
    }

    public static void apply(CapriNet.Ons m) {
        boolean was = on;
        on = m.on;
        mode = m.mode;
        endMs = System.currentTimeMillis() + m.left * 50L;
        if (!on) {
            reset();
        } else if (!was) {
            flipIn = 40;
            // a lock made before activating is the first thing to die, whatever it is
            priorityId = CapriClientState.lockId;
        }
    }

    public static void reset() {
        on = false;
        fwd = strafe = 0;
        jump = sprint = false;
        targetId = -1;
        priorityId = -1;
        hadTarget = false;
        dodge = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyAttack.setDown(false);
            mc.options.keySprint.setDown(false);
        }
        restoreSens();
    }

    private static void restoreSens() {
        if (savedSens != null) {
            Minecraft.getInstance().options.sensitivity().set(savedSens);
            savedSens = null;
        }
    }

    private static boolean valid(LocalPlayer p, Entity e, double r) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == p || e instanceof Player || e instanceof ArmorStand || le.isSpectator()) {
            return false;
        }
        if (le.isAlliedTo(p) || (le instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame())) {
            return false;
        }
        if (!(le instanceof Enemy) && !(le instanceof Mob m && m.getTarget() == p)) {
            return false;
        }
        return p.distanceTo(le) <= r;
    }

    private static double radius() {
        return CapriConfig.ONS_RANGE.get();
    }

    private static LivingEntity choose(Minecraft mc, LocalPlayer p) {
        if (priorityId >= 0) {
            Entity pr = mc.level.getEntity(priorityId);
            if (pr instanceof LivingEntity le && le.isAlive() && le != p && p.distanceTo(le) < 80) {
                return le;
            }
            priorityId = -1;
        }
        double r = radius();
        Entity cur = targetId >= 0 ? mc.level.getEntity(targetId) : null;
        if (cur != null && valid(p, cur, r + 1.5)) {
            return (LivingEntity) cur;
        }
        List<LivingEntity> all = mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> valid(p, e, r));
        return all.stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }

    private static void setTarget(LivingEntity t) {
        int id = t == null ? -1 : t.getId();
        if (id != targetId) {
            targetId = id;
            CapriClientState.lockId = id;
            CapriClientState.aggroId = -2;
            ModNetwork.CHANNEL.sendToServer(new CapriNet.Action(CapriNet.OP_LOCK, "", id, null));
        }
    }

    private static void look(LocalPlayer p, Vec3 at, float k) {
        Vec3 d = at.subtract(p.getEyePosition());
        float ty = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float tp = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.setYRot(p.getYRot() + Mth.wrapDegrees(ty - p.getYRot()) * k);
        p.setXRot(Mth.clamp(p.getXRot() + (tp - p.getXRot()) * k, -80f, 80f));
        p.setYHeadRot(p.getYRot());
    }

    /** Is something flying at the player? Returns the strafe side to step to, or 0. */
    private static int incoming(Minecraft mc, LocalPlayer p) {
        for (Projectile pr : mc.level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(Math.max(9, CapriConfig.ONS_CIRCLE.get())))) {
            if (pr.getOwner() == p || pr.onGround()) {
                continue;
            }
            Vec3 v = pr.getDeltaMovement();
            if (v.lengthSqr() < 0.04) {
                continue;
            }
            Vec3 u = v.normalize();
            Vec3 r = p.position().add(0, 0.9, 0).subtract(pr.position());
            double along = r.dot(u);
            if (along < 0 || along > 9) {
                continue;
            }
            Vec3 off = r.subtract(u.scale(along)); // where the player sits relative to the projectile's line
            if (off.length() > 1.6) {
                continue;
            }
            double yaw = Math.toRadians(p.getYRot());
            Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            double side = left.dot(off);
            return side >= 0 ? 1 : -1;
        }
        return 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !CapriClientState.linked || !p.isAlive()) {
            if (on) {
                reset();
            }
            return;
        }
        if (!on) {
            restoreSens();
            return;
        }
        // mouse look is the autopilot's: sensitivity 0 while playing (given back whenever a screen is open or it ends)
        if (mc.screen == null) {
            if (savedSens == null) {
                savedSens = mc.options.sensitivity().get();
            }
            mc.options.sensitivity().set(0.0);
        } else {
            restoreSens();
            fwd = strafe = 0;
            jump = false;
            return;
        }
        mc.options.keyUse.setDown(false);
        if (secondsLeft() <= 0) {
            return; // the server ends it on its next beat
        }
        LivingEntity t = choose(mc, p);
        setTarget(t);
        if (t != null && CapriClientState.lockId != t.getId() && p.tickCount % 10 == 0) {
            CapriClientState.lockId = t.getId();
            ModNetwork.CHANNEL.sendToServer(new CapriNet.Action(CapriNet.OP_LOCK, "", t.getId(), null));
        }
        if (t == null) {
            fwd = strafe = 0;
            jump = p.isInWater();
            sprint = false;
            mc.options.keyAttack.setDown(false);
            if (hadTarget && clearCool <= 0) {
                hadTarget = false;
                clearCool = 200;
                CapriDialogue.event("ons_clear", "");
            }
            if (clearCool > 0) {
                clearCool--;
            }
            return;
        }
        hadTarget = true;
        Vec3 c = t.getBoundingBox().getCenter();
        double dx = c.x - p.getX(), dz = c.z - p.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        look(p, c, 0.8f);
        // hold AND click: a held key alone only swings when the weapon mod happens to ask for it
        mc.options.keyAttack.setDown(true);
        if (++clickTick % 2 == 0) {
            net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
        }
        jump = p.isInWater() || (p.horizontalCollision && p.onGround());
        if (mode == 0) {
            // PSYCHOPATH: straight at it, never stops swinging
            fwd = h > 1.6 ? 1f : 0f;
            strafe = 0;
            sprint = h > 3.0;
            if (c.y - p.getY() > 1.4 && h < 3.0) {
                jump = true;
            }
        } else {
            // STRATEGIC: keep a ring around the target, circle it, sidestep what is thrown at us
            double want = CapriConfig.ONS_CIRCLE.get();
            fwd = (float) Mth.clamp((h - want) * 0.7, -1.0, 1.0);
            if (--flipIn <= 0) {
                orbit = -orbit;
                flipIn = 50 + R.nextInt(90);
            }
            if (p.horizontalCollision) {
                orbit = -orbit;
            }
            strafe = orbit;
            sprint = false;
            int in = dodge > 0 ? 0 : incoming(mc, p);
            if (in != 0) {
                dodge = 8;
                dodgeSign = in;
            }
            if (dodge > 0) {
                dodge--;
                strafe = dodgeSign;
                jump = jump || (dodge == 5 && p.onGround());
            }
        }
        if (clearCool > 0) {
            clearCool--;
        }
        mc.options.keySprint.setDown(sprint);
    }

    @SubscribeEvent
    public static void move(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.screen != null || e.getEntity() != mc.player) {
            return;
        }
        var in = e.getInput();
        in.forwardImpulse = fwd;
        in.leftImpulse = strafe;
        in.up = fwd > 0;
        in.down = fwd < 0;
        in.left = strafe > 0;
        in.right = strafe < 0;
        in.jumping = jump;
        in.shiftKeyDown = false;
    }

    /** Only attacking is allowed while the autopilot is in charge: no placing, using or picking. */
    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (on && !e.isAttack()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (on && Minecraft.getInstance().screen == null) {
            e.setCanceled(true);
        }
    }
}
