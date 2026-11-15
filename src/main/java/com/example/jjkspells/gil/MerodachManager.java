package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.gil.entity.GilFxEntity;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.GoldTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Merodach, the Sword of Rupture (about 4 s).
 *   0-16   a portal beside the right hand; the sword is drawn (HELD)
 *   16-40  a gold circle behind the caster, cracks run through the ground
 *   40-56  the sword is swung: a 10 wide golden slash travels 30 blocks; blade hit and energy hits
 *   56-74  portals open along the path, swords plunge around the struck enemies
 *   ~80    the swords detonate
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class MerodachManager {
    private static final int DRAW = 16, CHARGE = 40, SWING = 44, SLASH_END = 56, RAIN = 74, BOOM = 80, END = 90;

    private static final class Session {
        final ServerLevel level;
        final UUID casterId;
        final Vec3 origin, fwd, right;
        final Vec3 end;
        final Set<UUID> hitBlade = new LinkedHashSet<>();
        final Set<UUID> hitWave = new LinkedHashSet<>();
        final List<Vec3> landings = new ArrayList<>();
        GilFxEntity held;
        GilFxEntity slash;
        boolean interrupted;
        int age;

        Session(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.casterId = c.getUUID();
            this.origin = c.position();
            Vec3 look = c.getLookAngle();
            this.fwd = GilUtil.flat(look);
            this.right = GilUtil.rightOf(fwd);
            this.end = origin.add(fwd.scale(GilConfig.MERODACH.range.get()));
        }
    }

    private static final List<Session> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Session(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Session> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            if (!(e instanceof LivingEntity c) || !c.isAlive()) {
                if (s.held != null) s.held.discard();
                it.remove();
                continue;
            }
            boolean done;
            try {
                done = tick(s, c);
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getLogger().error("Merodach failed", ex);
                if (s.held != null) s.held.discard();
                done = true;
            }
            s.age++;
            if (done) it.remove();
        }
    }

    private static double along(Session s, Vec3 p) {
        return p.subtract(s.origin).dot(s.fwd);
    }

    private static boolean tick(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        int t = s.age;
        var cfg = GilConfig.MERODACH;
        if (c.hasEffect(ModEffects.STUN.get())) s.interrupted = true;

        if (t == 0) {
            Vec3 hand = c.position().add(s.right.scale(0.9)).add(s.fwd.scale(0.3)).add(0, 1.5, 0);
            GilUtil.portal(level, hand.add(0, 0.3, 0), s.right.add(s.fwd), 0.9, true, 0.8f);
            s.held = GilFxEntity.create(level, GilFxEntity.HELD, 0, SWING + 14, 1f, c.position()).path(Vec3.ZERO, Vec3.ZERO, 0, 0);
            s.held.owner(c);
            level.addFreshEntity(s.held);
            GilUtil.announce(c, Component.literal("Merodach").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        // keep the caster rooted in place while the blade is drawn and swung
        if (t < SLASH_END) {
            c.setDeltaMovement(c.getDeltaMovement().multiply(0.2, 1, 0.2));
            c.hurtMarked = true;
        }

        if (t == DRAW) {
            Vec3 back = s.origin.add(s.fwd.scale(-1.6)).add(0, 0.06, 0);
            level.addFreshEntity(GilFxEntity.create(level, GilFxEntity.CIRCLE, 0, CHARGE - DRAW + 22, 4.2f, back).path(Vec3.ZERO, new Vec3(0, 1, 0), 0, 0));
            GilUtil.sound(level, s.origin, ModSounds.GATE_OPEN.get(), 1.5f, 0.55f);
        }
        if (t >= DRAW && t < CHARGE) {
            // cracks radiate from the circle, gold rises along them
            if (t % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double d = 1 + level.random.nextDouble() * 6;
                    Vec3 p = s.origin.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
                    FX.one(level, ModParticles.GOLD_SPARK.get(), p, new Vec3(0, 0.12 + level.random.nextDouble() * 0.12, 0));
                    FX.one(level, ModParticles.EA_CRACK.get(), p, new Vec3(0.6 + level.random.nextDouble(), 0, 0));
                }
            }
            if (t % 6 == 0) GilUtil.sound(level, s.origin, ModSounds.EA_RUMBLE.get(), 0.5f, 1.6f);
        }

        // ---- the swing
        if (t == SWING) {
            Vec3 a = s.origin.add(s.fwd.scale(1.5)).add(0, 1.2, 0);
            Vec3 b = s.end.add(0, 1.2, 0);
            s.slash = GilFxEntity.create(level, GilFxEntity.SLASH, 0, 16, (float) (double) cfg.width.get(), a).path(a, b, 0, SLASH_END - SWING);
            level.addFreshEntity(s.slash);
            GilUtil.sound(level, c.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.5f);
            GilUtil.sound(level, c.position(), ModSounds.WEAPON_IMPACT.get(), 1.6f, 0.7f);
        }
        if (t >= SWING && t <= SLASH_END) {
            double front = 1.5 + (cfg.range.get() - 1.5) * (t - SWING) / (double) (SLASH_END - SWING);
            slashHits(s, c, front);
        }

        // ---- portals open along the path and swords plunge around the struck enemies
        if (t == SLASH_END + 2) rain(s, c);
        if (t == BOOM) boom(s, c);
        if (t >= SLASH_END && t < BOOM && t % 4 == 0) {
            for (Vec3 p : s.landings) FX.one(level, ModParticles.GOLD_RUNE.get(), p.add((level.random.nextDouble() - 0.5) * 2, 0.2, (level.random.nextDouble() - 0.5) * 2), new Vec3(0, 0.05, 0));
        }
        return t >= END;
    }

    private static boolean inLane(Session s, LivingEntity e, double front) {
        Vec3 p = e.position().subtract(s.origin);
        double fd = p.dot(s.fwd);
        double sd = Math.abs(p.dot(s.right));
        return fd >= -1 && fd <= front + 1 && sd <= GilConfig.MERODACH.width.get() * 0.5 + e.getBbWidth() * 0.5 && Math.abs(p.y) < 6;
    }

    private static void slashHits(Session s, LivingEntity c, double front) {
        ServerLevel level = s.level;
        var cfg = GilConfig.MERODACH;
        double w = cfg.width.get();
        Vec3 mid = s.origin.add(s.fwd.scale(front * 0.5));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(mid, mid).inflate(front * 0.5 + w, 6, front * 0.5 + w))) {
            if (!GilDmg.canHit(c, e) || !inLane(s, e, front)) continue;
            boolean near = along(s, e.position()) < 6;
            if (near && s.hitBlade.add(e.getUUID())) {
                // the blade itself
                float bonus = (e.getHealth() / e.getMaxHealth() < cfg.execute.f()) ? 1f + cfg.executeBonus.f() : 1f;
                GilDmg.hit(level, c, e, cfg.blade.f(), cfg.bladePercent.f(), bonus, false, null);
                GilUtil.sound(level, e.position(), ModSounds.WEAPON_IMPACT.get(), 1.4f, 0.8f);
                FX.one(level, ModParticles.GOLD_GLOW.get(), e.getBoundingBox().getCenter(), new Vec3(2.2, 0, 0));
                GilUtil.sparks(level, e.getBoundingBox().getCenter(), 14, 0.8);
            }
            if (s.hitWave.add(e.getUUID())) {
                {
                    GilDmg.hit(level, c, e, cfg.wave.f(), cfg.wavePercent.f());
                }
                GilDmg.effect(e, GilEffects.RESONANCE.get(), cfg.markSeconds.i() * 20, 0);
                GilUtil.launch(e, s.origin, 0.25, 0.3);
                s.landings.add(e.position());
                GilUtil.sparks(level, e.getBoundingBox().getCenter(), 10, 0.6);
            }
        }
    }

    /** Eight swords plunge: around the struck enemies, otherwise spread across the path. */
    private static void rain(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        var cfg = GilConfig.MERODACH;
        int n = cfg.swords.i();
        List<Vec3> spots = new ArrayList<>();
        if (s.landings.isEmpty()) {
            for (int i = 0; i < n; i++) {
                double d = 8 + 20.0 * i / Math.max(1, n - 1);
                double sd = ((i % 2 == 0) ? 1 : -1) * (2 + level.random.nextDouble() * 3);
                spots.add(s.origin.add(s.fwd.scale(d)).add(s.right.scale(sd)));
            }
        } else {
            for (int i = 0; i < n; i++) {
                Vec3 base = s.landings.get(i % s.landings.size());
                double a = i * 2.399963;
                double d = 1.5 + (i / s.landings.size()) * 1.4;
                spots.add(base.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            }
        }
        s.landings.clear();
        for (int i = 0; i < spots.size(); i++) {
            Vec3 p = spots.get(i);
            double gy = GoldTarget.groundY(level, p.x, p.y + 3, p.z, 24, p.y);
            Vec3 g = new Vec3(p.x, gy, p.z);
            s.landings.add(g);
            Vec3 top = g.add(s.fwd.scale(-1.2)).add(0, 12 + (i % 3), 0);
            GilUtil.portal(level, top, new Vec3(0, -1, 0), 1.6, true, 0.9f + i * 0.03f);
            GilFxEntity sword = GilFxEntity.create(level, GilFxEntity.GIANT, 0, 30, 6f, top).path(top, g, 6 + i, 5);
            level.addFreshEntity(sword);
            level.addFreshEntity(GilFxEntity.create(level, GilFxEntity.MARK, 0, 24, 1.8f, g.add(0, 0.05, 0)).path(Vec3.ZERO, g.add(0, 0.05, 0), 0, 0));
            // a gold line ties each sword to the next
            if (i > 0) {
                Vec3 q = s.landings.get(i - 1);
                FX.one(level, ModParticles.GOLD_LINE.get(), q.add(0, 0.3, 0), g.subtract(q));
            }
        }
        GilUtil.announce(c, Component.literal("Rupture").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
    }

    private static void boom(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        var cfg = GilConfig.MERODACH;
        boolean perfect = !s.interrupted;
        double r = cfg.boomRadius.get() * (perfect ? 1 + cfg.perfectRadius.get() : 1);
        float m = perfect ? 1f + cfg.perfectDamage.f() : 1f;
        Set<UUID> done = new LinkedHashSet<>();
        for (Vec3 g : s.landings) {
            GilUtil.sound(level, g, SoundEvents.GENERIC_EXPLODE, 2f, 0.7f);
            FX.one(level, ModParticles.GOLD_GLOW.get(), g.add(0, 1, 0), new Vec3(r * 1.5, 0, 0));
            FX.one(level, ModParticles.GOLD_RING.get(), g.add(0, 1, 0), new Vec3(r, 0, 0));
            GilUtil.flatRing(level, g.add(0, 0.12, 0), r * 0.8);
            GilUtil.sparks(level, g.add(0, 0.6, 0), 22, 1.0);
            for (LivingEntity e : GilDmg.around(level, c, g, r)) {
                // each enemy takes the blast once, however many swords reach it
                if (!done.add(e.getUUID())) continue;
                GilDmg.hit(level, c, e, cfg.boom.f(), cfg.boomPercent.f(), m, false, null);
                GilUtil.launch(e, g, 0.55, 0.9);
            }
        }
        if (perfect) {
            GilState.guard(c, 60, cfg.perfectReduction.f(), cfg.perfectReduction.f(), false, false);
            GilUtil.sound(level, c.position(), ModSounds.GATE_FIRE.get(), 1.2f, 1.4f);
            FX.one(level, ModParticles.GOLD_RING.get(), c.position().add(0, 1, 0), new Vec3(2.4, 0, 0));
            GilUtil.announce(c, Component.literal("Perfect Release").withStyle(ChatFormatting.GOLD));
        }
    }
}
