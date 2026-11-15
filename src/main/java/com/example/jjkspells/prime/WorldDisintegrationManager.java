package com.example.jjkspells.prime;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.prime.fx.PrimeFxEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.jjkspells.prime.PrimeFx.*;

/**
 * World Disintegration: a dark red flame arrow wrapped in purple fire charges for 2 seconds, then flies as a laser that tears reality
 * apart along its path. Where it lands a pillar of dark red fire rises to the heavens: radius 30, 200 + 40% max health.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class WorldDisintegrationManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int TRAVEL = 3, PILLAR_LIFE = 112, CRATER_TICKS = 12;

    private static final class Wd {
        final ServerLevel level;
        final UUID caster;
        final PrimeBlocks blocks;
        final Set<UUID> hit = new HashSet<>();
        final int charge;
        int age, fireAt = -1, blastAt = -1;
        Vec3 impact = Vec3.ZERO;

        Wd(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.caster = c.getUUID();
            this.blocks = new PrimeBlocks(level, c);
            this.charge = (int) Math.round(PrimeConfig.WD_CHARGE_SECONDS.get() * 20);
        }
    }

    private static final List<Wd> ACTIVE = new ArrayList<>();

    private WorldDisintegrationManager() {
    }

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Wd(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Wd> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Wd w = it.next();
            Entity e = w.level.getEntity(w.caster);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done = false;
            try {
                done = tick(w, c);
            } catch (Exception ex) {
                LOGGER.error("World Disintegration failed, cancelling it", ex);
                done = true;
            }
            w.age++;
            if (done || w.age > 1200) it.remove();
        }
    }

    private static boolean tick(Wd w, LivingEntity c) {
        ServerLevel level = w.level;
        int t = w.age;
        if (w.fireAt < 0) {
            if (c == null) return true;
            Vec3 feet = c.position();
            if (t == 0) {
                PrimeFxEntity.spawn(level, PrimeFxEntity.CHARGE, feet, c.getLookAngle(), null, 1f, w.charge, 0f, 0f, c.getId());
                sound(level, feet, SoundEvents.BEACON_ACTIVATE, 4f, 0.5f);
                sound(level, feet, SoundEvents.RESPAWN_ANCHOR_CHARGE, 5f, 0.5f);
                announce(level, c, Component.literal("World Disintegration").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            }
            if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 9, false, false, false));
            if (t % 8 == 0) sound(level, feet, SoundEvents.FIRE_AMBIENT, 3f, 0.5f + 0.9f * t / w.charge);
            if (t % 10 == 0) sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 0.4f + 1.1f * t / w.charge);
            if (t >= w.charge) fire(w, c);
            return false;
        }
        int fa = t - w.fireAt;
        if (fa == TRAVEL) {
            if (c == null) return true;
            w.blastAt = t;
            explode(w, c);
        }
        if (w.blastAt >= 0) {
            int ba = t - w.blastAt;
            w.blocks.newTick();
            if (ba < CRATER_TICKS && JJKConfig.breaks(PrimeConfig.WD_BREAKS)) crater(w, ba);
            if (ba % 10 == 5 && ba < 90) burnAll(w, c);
            if (ba >= PILLAR_LIFE) return true;
        }
        return false;
    }

    private static void fire(Wd w, LivingEntity c) {
        ServerLevel level = w.level;
        Vec3 eye = c.getEyePosition();
        Vec3 look = c.getLookAngle().normalize();
        double range = PrimeConfig.WD_RANGE.get();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bhit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(level, c, eye, stop, new AABB(eye, stop).inflate(1.0),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator() && x != c);
        Vec3 p = ehit != null ? ehit.getLocation() : stop;
        Vec3 origin = eye.add(look.scale(7.0)).subtract(0, 0.35, 0);
        if (p.subtract(origin).dot(look) < 1.0) p = origin.add(look.scale(1.0));
        w.impact = p;
        w.fireAt = w.age;
        PrimeFxEntity.spawn(level, PrimeFxEntity.BEAM, origin, look, p, 1f, 24, 0f, 0f, -1);
        sound(level, origin, SoundEvents.TRIDENT_THUNDER, 6f, 1.8f);
        sound(level, origin, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 6f, 0.5f);
        sound(level, origin, SoundEvents.WARDEN_SONIC_BOOM, 6f, 0.8f);
        sound(level, p, SoundEvents.END_PORTAL_SPAWN, 5f, 1.4f);
        // reality comes apart along the path: everyone near the line is dizzy
        Vec3 seg = p.subtract(origin);
        double len2 = Math.max(1.0E-4, seg.lengthSqr());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, p).inflate(10), LivingEntity::isAlive)) {
            if (!foe(c, e)) continue;
            double tt = Math.max(0, Math.min(1, e.position().subtract(origin).dot(seg) / len2));
            if (e.position().distanceTo(origin.add(seg.scale(tt))) <= 9.0) {
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0, false, false, true), c);
            }
        }
    }

    private static void explode(Wd w, LivingEntity c) {
        ServerLevel level = w.level;
        Vec3 p = w.impact;
        double R = PrimeConfig.WD_RADIUS.get();
        float reachUp = PrimeConfig.WD_REACH_UP.get().floatValue();
        PrimeFxEntity.spawn(level, PrimeFxEntity.PILLAR, p, null, null, (float) R, PILLAR_LIFE, 0f, reachUp + 120f, -1);
        SukunaFx.blast(level, p.add(0, R * 0.3, 0), (float) (R * 0.9), 1);
        sound(level, p, SoundEvents.GENERIC_EXPLODE, 12f, 0.35f);
        sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 12f, 0.3f);
        sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 10f, 0.5f);
        sound(level, p, SoundEvents.BLAZE_SHOOT, 10f, 0.4f);
        float flat = PrimeConfig.WD_FLAT.get().floatValue(), pct = PrimeConfig.WD_PERCENT.get().floatValue();
        int burn = PrimeConfig.WD_BURN_SECONDS.get();
        AABB box = new AABB(p.x - R, p.y - 20, p.z - R, p.x + R, p.y + reachUp, p.z + R);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!foe(c, e)) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double dx = ec.x - p.x, dz = ec.z - p.z;
            if (dx * dx + dz * dz > (R + e.getBbWidth() * 0.5) * (R + e.getBbWidth() * 0.5)) continue;
            w.hit.add(e.getUUID());
            smite(level, ModSpells.WORLD_DISINTEGRATION.get(), c, e, flat, pct);
            if (e.isAlive()) {
                if (burn > 0) e.setSecondsOnFire(burn);
                double hd = Math.sqrt(dx * dx + dz * dz);
                double ox = hd < 1.0E-3 ? 0 : dx / hd, oz = hd < 1.0E-3 ? 0 : dz / hd;
                double push = 1.2 + 2.6 * (1 - Math.min(1.0, hd / R));
                e.setDeltaMovement(ox * push, 1.3, oz * push);
                OverloadEvents.forceMove(e);
                e.hurtMarked = true;
            }
        }
    }

    /** Flames keep burning everything inside the pillar for a while. */
    private static void burnAll(Wd w, LivingEntity c) {
        double R = PrimeConfig.WD_RADIUS.get();
        int burn = PrimeConfig.WD_BURN_SECONDS.get();
        if (burn <= 0) return;
        Vec3 p = w.impact;
        AABB box = new AABB(p.x - R, p.y - 20, p.z - R, p.x + R, p.y + PrimeConfig.WD_REACH_UP.get(), p.z + R);
        for (LivingEntity e : w.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!foe(c, e)) continue;
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            if (dx * dx + dz * dz <= R * R) e.setSecondsOnFire(burn);
        }
    }

    /** The crater opens as an expanding shell, so a hill is eaten over a dozen ticks instead of in one lag spike. */
    private static void crater(Wd w, int i) {
        double R = PrimeConfig.WD_RADIUS.get();
        double cur = Math.min(R, (i + 1) * (R / (CRATER_TICKS - 2.0)));
        double prev = Math.min(R, i * (R / (CRATER_TICKS - 2.0)));
        Vec3 p = w.impact;
        int ext = (int) Math.ceil(cur);
        int cx = (int) Math.floor(p.x), cy = (int) Math.floor(p.y), cz = (int) Math.floor(p.z);
        double c2 = cur * cur, p2 = prev * prev;
        for (int x = -ext; x <= ext; x++) {
            for (int y = -ext; y <= ext; y++) {
                for (int z = -ext; z <= ext; z++) {
                    double d2 = x * x + y * y * 1.25 + z * z;
                    if (d2 > c2 || d2 <= p2) continue;
                    if (Math.sqrt(d2) > R * (0.9 + 0.1 * PrimeBlocks.jag(cx + x, cy + y, cz + z))) continue;
                    w.blocks.erase(cx + x, cy + y, cz + z, p, 1.4);
                }
            }
        }
    }
}
