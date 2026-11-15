package com.example.jjkspells.prime;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.effect.OverloadEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.example.jjkspells.prime.PrimeFx.*;

/**
 * Void Blue: a black hole that floats in front of the caster's aim and follows it. It drags everything in
 * (creatures, items, projectiles) far harder than Lapse Blue, grinds the ones at the core, and anything caught in it
 * cannot cast spells or attack.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class VoidBlueManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final class Hole {
        final ServerLevel level;
        final UUID caster;
        final int duration;
        Vec3 pos = Vec3.ZERO;
        boolean placed;
        int age;

        Hole(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.caster = c.getUUID();
            this.duration = PrimeConfig.VB_SECONDS.get() * 20;
        }
    }

    private static final List<Hole> ACTIVE = new ArrayList<>();

    private VoidBlueManager() {
    }

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Hole(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Hole> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Hole h = it.next();
            Entity e = h.level.getEntity(h.caster);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done;
            try {
                done = tick(h, c);
            } catch (Exception ex) {
                LOGGER.error("Void Blue failed, cancelling it", ex);
                done = true;
            }
            h.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Hole h, LivingEntity c) {
        ServerLevel level = h.level;
        RandomSource rng = level.random;
        int t = h.age;
        int D = h.duration;
        if (c == null || t >= D) {
            finish(h, c);
            return true;
        }
        double grow = smooth(Math.min(1.0, t / 14.0)) * smooth(Math.min(1.0, (D - t) / 16.0));
        float R = (float) (PrimeConfig.VB_RADIUS.get() * Math.max(0.05, grow));

        // ---- where the void is: ahead of the aim, never inside a wall -------------------------------
        Vec3 eye = c.getEyePosition();
        Vec3 look = c.getLookAngle();
        Vec3 goal = eye.add(look.scale(PrimeConfig.VB_DISTANCE.get()));
        BlockHitResult hit = level.clip(new ClipContext(eye, goal, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        if (hit.getType() != HitResult.Type.MISS) goal = hit.getLocation().subtract(look.scale(Math.min(R, 1.6)));
        h.pos = h.placed ? h.pos.add(goal.subtract(h.pos).scale(0.4)) : goal;
        h.placed = true;
        Vec3 P = h.pos;
        Basis b = Basis.of(look);

        if (t == 0) {
            sound(level, P, SoundEvents.WARDEN_SONIC_BOOM, 5f, 0.6f);
            sound(level, P, SoundEvents.END_PORTAL_SPAWN, 5f, 0.7f);
            title(level, c.position(), Component.literal("VOID BLUE").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), Component.literal("PRIME TECHNIQUE").withStyle(ChatFormatting.DARK_AQUA));
            for (int i = 0; i < 5; i++) glow(level, ModParticles.UV_RING.get(), P, 3f + i * 3f);
        }
        if (t % 24 == 1) sound(level, P, ModSounds.LAPSE_BLUE_HUM.get(), 4f, 0.55f);
        if (t % 10 == 0) sound(level, P, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 3f, 0.5f);

        // ---- the pull ---------------------------------------------------------------------------------
        double range = PrimeConfig.VB_PULL_RANGE.get() * Math.min(1.0, grow * 1.25);
        double strength = PrimeConfig.VB_PULL_STRENGTH.get();
        double maxSpeed = PrimeConfig.VB_MAX_SPEED.get();
        List<Vec3> victims = new ArrayList<>();
        List<Entity> caught = level.getEntities(c, new AABB(P, P).inflate(range), x -> x.isAlive() && !x.isSpectator());
        for (Entity e : caught) {
            boolean living = e instanceof LivingEntity;
            if (!living && !(e instanceof ItemEntity) && !(e instanceof Projectile)) continue;
            if (e instanceof Projectile pr && pr.getOwner() == c) continue;
            if (e instanceof LivingEntity le && !foe(c, le)) continue;
            if (e instanceof Player p && p.getAbilities().flying) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            Vec3 diff = P.subtract(ec);
            double d = diff.length();
            if (d > range || d < 0.05) continue;
            double closeness = 1.0 - d / range;
            Vec3 m = e.getDeltaMovement().scale(0.55).add(diff.scale(1.0 / d).scale(strength * (0.45 + 1.1 * closeness)));
            if (d < R * 1.6) m = m.scale(0.4);
            double sp = m.length();
            if (sp > maxSpeed) m = m.scale(maxSpeed / sp);
            e.setDeltaMovement(m);
            e.setOnGround(false);
            OverloadEvents.forceMove(e);
            e.hurtMarked = true;
            e.hasImpulse = true;
            e.fallDistance = 0;
            if (e instanceof LivingEntity le) {
                PrimeStatus.lock(le, 10);
                if (t % 4 == 0 && !le.hasEffect(ModEffects.SIMPLE_DOMAIN.get())) {
                    le.addEffect(new MobEffectInstance(ModEffects.OVERLOAD.get(), 14, 0, false, false, true), c);
                }
                OverloadEvents.blockHealing(le, 12);
                if (victims.size() < 10) victims.add(ec);
                // ---- the grinding ----
                if (t % 4 == 0 && d <= range * 0.75) {
                    double reach = R * 1.8 + le.getBbWidth() * 0.5;
                    float mult = d <= reach ? 1f : (float) (0.25 + 0.4 * (1.0 - d / range));
                    smite(level, ModSpells.VOID_BLUE.get(), c, le, PrimeConfig.VB_FLAT.get().floatValue() * mult, PrimeConfig.VB_PERCENT.get().floatValue() * mult);
                    if (d <= reach) flakes(level, le, ModParticles.HP_WISP_BLUE.get(), 4);
                }
            } else if (e instanceof Projectile && d < R * 1.2) {
                e.discard();
            }
        }

        // ---- visuals ----------------------------------------------------------------------------------
        glow(level, ModParticles.UV_CORE.get(), P, R);
        glow(level, ModParticles.UV_GLOW.get(), P, R * 2.6f);
        glow(level, ModParticles.HP_GLOW_BLUE.get(), P, R * 3.6f);
        if (t % 2 == 0) glow(level, ModParticles.HP_GLOW_BLUE.get(), P, R * 5.6f);
        if (t % 3 == 0) glow(level, ModParticles.HP_CORE.get(), P, R * 0.45f);
        // two tilted accretion discs, spinning in opposite directions
        for (int disc = 0; disc < 2; disc++) {
            Vec3 A = disc == 0 ? b.r() : b.r().add(b.u()).normalize();
            Vec3 B = disc == 0 ? b.f() : b.f().subtract(b.u().scale(0.5)).normalize();
            double spin = t * (disc == 0 ? 0.28 : -0.2);
            int n = 20;
            for (int i = 0; i < n; i++) {
                double a = spin + i * Math.PI * 2 / n + rng.nextDouble() * 0.2;
                double rad = R * (1.35 + 1.9 * rng.nextDouble());
                Vec3 off = A.scale(Math.cos(a)).add(B.scale(Math.sin(a)));
                Vec3 tang = A.scale(-Math.sin(a)).add(B.scale(Math.cos(a))).scale(disc == 0 ? 1 : -1);
                one(level, ModParticles.HP_WISP_BLUE.get(), P.add(off.scale(rad)), tang.scale(0.42));
            }
        }
        // everything falling inward
        int inflow = 18;
        double visRange = Math.min(range, 24);
        for (int i = 0; i < inflow; i++) {
            Vec3 d = unit(rng);
            Vec3 off = d.scale(visRange * (0.3 + 0.7 * Math.sqrt(rng.nextDouble())));
            one(level, ModParticles.BLUE_VORTEX.get(), P.add(off), off.scale(-1));
        }
        for (int i = 0; i < 6; i++) {
            Vec3 d = unit(rng);
            one(level, ModParticles.BLUE_SPARK.get(), P.add(d.scale(R * 1.4)), d.scale(0.12));
        }
        for (int i = 0; i < 4; i++) {
            Vec3 d = unit(rng);
            one(level, ModParticles.HP_VOID.get(), P.add(d.scale(R * 1.1)), d.scale(0.05));
        }
        for (int i = 0; i < 4; i++) {
            Vec3 d = unit(rng);
            Vec3 p = P.add(d.scale(8 + rng.nextDouble() * 10));
            line(level, ModParticles.UV_LINE.get(), p, P.subtract(p).scale(0.45));
        }
        if (t % 8 == 0) {
            glow(level, ModParticles.UV_RING.get(), P, R * 4.4f);
            glow(level, ModParticles.UV_RING.get(), P, R * 6.5f);
        }
        if (t % 3 == 0) for (Vec3 v : victims) bolt(level, ModParticles.UV_LINE.get(), P, v, 0.5, 3);
        return false;
    }

    private static void finish(Hole h, LivingEntity c) {
        ServerLevel level = h.level;
        RandomSource rng = level.random;
        Vec3 P = h.pos;
        sound(level, P, SoundEvents.GENERIC_EXPLODE, 6f, 0.5f);
        sound(level, P, SoundEvents.BEACON_DEACTIVATE, 5f, 0.6f);
        glow(level, ModParticles.HP_CORE.get(), P, 7f);
        glow(level, ModParticles.HP_GLOW_BLUE.get(), P, 16f);
        for (int i = 0; i < 4; i++) glow(level, ModParticles.UV_RING.get(), P, 5f + i * 4f);
        for (int i = 0; i < 70; i++) one(level, ModParticles.UV_STAR.get(), P, unit(rng).scale(0.3 + rng.nextDouble() * 1.1));
        for (int i = 0; i < 26; i++) {
            Vec3 d = unit(rng);
            line(level, ModParticles.UV_LINE.get(), P.add(d.scale(2)), d.scale(6 + rng.nextDouble() * 12));
        }
        // let go: whatever was held is thrown outward
        double range = PrimeConfig.VB_PULL_RANGE.get();
        for (Entity e : level.getEntities(c, new AABB(P, P).inflate(range), x -> x.isAlive() && x instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            PrimeStatus.unlock(le);
            if (c != null && !foe(c, le)) continue;
            Vec3 out = le.getBoundingBox().getCenter().subtract(P);
            if (out.lengthSqr() < 1.0E-4) out = new Vec3(0, 1, 0);
            out = out.normalize();
            le.setDeltaMovement(out.x * 1.6, 0.5 + out.y * 0.8, out.z * 1.6);
            OverloadEvents.forceMove(le);
            le.hurtMarked = true;
            le.hasImpulse = true;
        }
    }
}
