package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.entity.GateWeaponEntity;
import com.example.jjkspells.gil.entity.GilFxEntity;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.GoldTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Gate of Babylon: The King's Treasury (3.5 s).
 *   0-12    one arm rises, six portals open behind the shoulders, weapon tips appear
 *   12-28   first volley: six weapons, one after another
 *   28-48   twelve more portals in a half circle; all eighteen fire: twelve standard and four heavy weapons
 *   48-70   a great portal opens over the target, an ornamental sword emerges, hangs, plunges: Royal Judgment
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class GateManager {
    private static final int FORM = 8;
    private static final int FINAL_AT = 66;
    private static final int END = 72;

    private static final class Session {
        final ServerLevel level;
        final UUID casterId;
        UUID targetId;
        Vec3 aim;
        final Vec3 start;
        final Vec3 fwd, right;
        final List<Vec3> portals = new ArrayList<>();
        final List<Integer> opens = new ArrayList<>();
        boolean moved;
        int age;
        Vec3 ground;
        final int[] types = new int[40];
        int spawned;

        Session(ServerLevel level, LivingEntity c, LivingEntity target, Vec3 aim) {
            this.level = level;
            this.casterId = c.getUUID();
            this.targetId = target == null ? null : target.getUUID();
            this.aim = aim;
            this.start = c.position();
            Vec3 look = c.getLookAngle();
            this.fwd = GilUtil.flat(look);
            this.right = GilUtil.rightOf(fwd);
            // six portals behind the shoulders
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(-75 + 150.0 * i / 5.0);
                double d = 2.3 + (i % 2) * 0.5;
                portals.add(start.add(fwd.scale(-Math.cos(a) * d)).add(right.scale(Math.sin(a) * d)).add(0, 2.0 + (i % 2) * 1.1, 0));
                opens.add(i * 2);
            }
            // twelve more in a semicircle, two rows
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(-100 + 200.0 * i / 11.0);
                double d = 3.8 + (i % 2) * 0.9;
                portals.add(start.add(fwd.scale(-Math.cos(a) * d)).add(right.scale(Math.sin(a) * d)).add(0, 1.6 + (i % 3) * 1.15, 0));
                opens.add(24 + i);
            }
            for (int i = 0; i < types.length; i++) types[i] = level.random.nextInt(GateWeaponEntity.TYPES);
        }
    }

    private static final List<Session> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster) {
        double range = GilConfig.GATE.range.get();
        LivingEntity target = markedFirst(level, caster, range);
        Vec3 aim = target != null ? target.getBoundingBox().getCenter() : GilDmg.aim(level, caster, range);
        ACTIVE.add(new Session(level, caster, target, aim));
    }

    /** Enemies marked by Merodach are preferred; otherwise the creature being looked at. */
    private static LivingEntity markedFirst(ServerLevel level, LivingEntity caster, double range) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range), x -> x.hasEffect(GilEffects.RESONANCE.get()))) {
            if (!GilDmg.canHit(caster, e)) continue;
            double d = e.distanceToSqr(caster);
            if (d < bd && d <= range * range) {
                bd = d;
                best = e;
            }
        }
        return best != null ? best : GilDmg.target(level, caster, range);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Session> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            if (!(e instanceof LivingEntity c) || !c.isAlive()) {
                it.remove();
                continue;
            }
            boolean done;
            try {
                done = tick(s, c);
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getLogger().error("Gate of Babylon failed", ex);
                done = true;
            }
            s.age++;
            if (done) it.remove();
        }
    }

    private static boolean arrogant(Session s) {
        return !s.moved;
    }

    private static boolean tick(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        int t = s.age;
        var cfg = GilConfig.GATE;
        if (c.position().subtract(s.start).horizontalDistanceSqr() > 0.36) s.moved = true;

        // keep the target / aim fresh
        LivingEntity target = null;
        if (s.targetId != null && level.getEntity(s.targetId) instanceof LivingEntity le && le.isAlive()) target = le;
        if (target != null) {
            s.aim = target.getBoundingBox().getCenter();
        } else if (t % 10 == 0 && t < 48) {
            s.aim = GilDmg.aim(level, c, cfg.range.get());
        }

        if (t == 0) {
            GilUtil.sound(level, c.position(), ModSounds.GATE_OPEN.get(), 1.4f, 0.9f);
            GilUtil.announce(c, Component.literal("Gate of Babylon").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        // golden dust gathers at the shoulders and falls from the portal edges
        if (t < 30 && t % 2 == 0) {
            for (int i = 0; i < s.portals.size(); i++) {
                if (s.opens.get(i) > t) continue;
                Vec3 p = s.portals.get(i);
                FX.one(level, ModParticles.GOLD_SPARK.get(), p.add((level.random.nextDouble() - 0.5) * 1.6, -0.9 + level.random.nextDouble() * 0.4, (level.random.nextDouble() - 0.5) * 1.6), new Vec3(0, -0.05, 0));
            }
        }
        // portals open (position follows the original spot; they face the aim)
        for (int i = 0; i < s.portals.size(); i++) {
            if (s.opens.get(i) != t) continue;
            Vec3 p = s.portals.get(i);
            GilUtil.portal(level, p, s.aim.subtract(p), 1.15 + (i % 2) * 0.1 + (i >= 6 ? 0.15 : 0), true, 1.0f + i * 0.04f);
        }

        // ---- first volley: weapons launch one by one from 12 to 28
        int open = cfg.opening.i();
        for (int i = 0; i < open; i++) {
            int launch = 12 + (int) Math.round(i * 16.0 / Math.max(1, open));
            if (launch - FORM == t) spawn(s, c, target, i % 6, s.types[i % s.types.length], false);
        }
        // ---- second volley: all eighteen portals, standard + heavy, 28 to 48
        int std = cfg.standard.i(), hv = cfg.heavyCount.i();
        int total = std + hv;
        for (int k = 0; k < total; k++) {
            int launch = 28 + (int) Math.round(k * 19.0 / Math.max(1, total));
            if (launch - FORM != t) continue;
            int step = hv > 0 ? Math.max(1, total / hv) : Integer.MAX_VALUE;
            boolean heavy = hv > 0 && k % step == step - 1 && (k + 1) / step <= hv;
            int ty = heavy ? (k % 2 == 0 ? 2 : 6) : s.types[(open + k) % s.types.length];
            spawn(s, c, target, k % s.portals.size(), ty, heavy);
        }

        // ---- Royal Judgment
        if (t == 48) {
            GilUtil.sound(level, c.position(), ModSounds.GATE_OPEN.get(), 2.0f, 0.6f);
            Vec3 gp = target != null ? target.position() : s.aim;
            double gy = GoldTarget.groundY(level, gp.x, gp.y + 2, gp.z, 24, gp.y);
            s.ground = new Vec3(gp.x, gy, gp.z);
            double radius = arrogant(s) ? cfg.arrogantRadius.get() : cfg.finalRadius.get();
            Vec3 top = s.ground.add(0, 17, 0);
            GilUtil.portal(level, top, new Vec3(0, -1, 0), 5.5, true, 0.5f);
            GilFxEntity circle = GilFxEntity.create(level, GilFxEntity.CIRCLE, 0, 24, (float) radius, s.ground.add(0, 0.06, 0)).path(Vec3.ZERO, new Vec3(0, 1, 0), 0, 0);
            level.addFreshEntity(circle);
            GilFxEntity sword = GilFxEntity.create(level, GilFxEntity.GIANT, 0, 32, 14f, top).path(top, s.ground, 12, 6).burstAtEnd();
            level.addFreshEntity(sword);
            GilUtil.announce(c, Component.literal("Royal Judgment").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        }
        if (s.ground != null && t > 48 && t < FINAL_AT && t % 3 == 0) {
            GilUtil.flatRing(level, s.ground.add(0, 0.1, 0), 3.0 + (FINAL_AT - t) * 0.1);
            FX.one(level, ModParticles.GOLD_RUNE.get(), s.ground.add((level.random.nextDouble() - 0.5) * 4, 0.2, (level.random.nextDouble() - 0.5) * 4), new Vec3(0, 0.04, 0));
        }
        if (t == FINAL_AT && s.ground != null) judgment(s, c);
        return t >= END;
    }

    private static void spawn(Session s, LivingEntity c, LivingEntity target, int portal, int type, boolean heavy) {
        ServerLevel level = s.level;
        var cfg = GilConfig.GATE;
        float mult = arrogant(s) ? 1f + cfg.arrogance.f() : 1f;
        double speed = cfg.speed.get() * (arrogant(s) ? 1.0 + cfg.arrogantSpeed.get() : 1.0);
        GateWeaponEntity.Params p = heavy
                ? new GateWeaponEntity.Params(cfg.heavy.f(), 0f, mult, true, 3, 0.025f, speed * 0.9, 0f, true)
                : new GateWeaponEntity.Params(cfg.weapon.f(), 0f, mult, false, 0, 0.07f, speed, 0f, true);
        Vec3 at = s.portals.get(Math.floorMod(portal, s.portals.size()));
        level.addFreshEntity(new GateWeaponEntity(level, c, target, s.aim, at, type, FORM, p));
    }

    /** Royal Judgment lands: 60 + 5% max health, a 5 (7) block blast, launched upward, a golden shockwave. */
    private static void judgment(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        var cfg = GilConfig.GATE;
        double radius = arrogant(s) ? cfg.arrogantRadius.get() : cfg.finalRadius.get();
        float mult = arrogant(s) ? 1f + cfg.arrogance.f() : 1f;
        Vec3 g = s.ground;
        GilUtil.sound(level, g, SoundEvents.GENERIC_EXPLODE, 3f, 0.6f);
        GilUtil.sound(level, g, ModSounds.WEAPON_IMPACT.get(), 3f, 0.5f);
        GilUtil.sound(level, g, SoundEvents.ANVIL_LAND, 2f, 0.5f);
        FX.one(level, ModParticles.GOLD_GLOW.get(), g.add(0, 1, 0), new Vec3(radius * 2.4, 0, 0));
        FX.one(level, ModParticles.GOLD_RING.get(), g.add(0, 1, 0), new Vec3(radius * 1.6, 0, 0));
        for (int i = 0; i < 3; i++) GilUtil.flatRing(level, g.add(0, 0.12 + i * 0.05, 0), radius * (0.8 + i * 0.55));
        GilUtil.sparks(level, g.add(0, 0.5, 0), 70, 1.4);
        for (LivingEntity e : GilDmg.around(level, c, g, radius)) {
            GilDmg.hit(level, c, e, cfg.finalFlat.f(), cfg.finalPercent.f(), mult, true, null);
            GilUtil.launch(e, g, 0.5, 1.15);
        }
    }
}
