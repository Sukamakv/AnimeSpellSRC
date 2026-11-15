package com.example.jjkspells.prime;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModSpells;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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
 * Boundless Slash: a crescent 20 blocks tall, a thin diagonal blade of black and white. It is hard to aim and cuts through everything
 * on its line: 150 + 10% max health and Dismantled (no melee) for 6 seconds.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class BoundlessSlashManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int WINDUP = 10;
    private static final double TILT = 0.62;

    private static final class Sl {
        final ServerLevel level;
        final UUID caster;
        final PrimeBlocks blocks;
        final Set<UUID> hit = new HashSet<>();
        int age, flown;
        boolean fired;
        Vec3 origin = Vec3.ZERO, look = new Vec3(0, 0, 1), r = new Vec3(1, 0, 0), u = new Vec3(0, 1, 0);
        final double speed, range, size;

        Sl(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.caster = c.getUUID();
            this.blocks = new PrimeBlocks(level, c);
            this.speed = PrimeConfig.BS_SPEED.get();
            this.range = PrimeConfig.BS_RANGE.get();
            this.size = PrimeConfig.BS_HEIGHT.get();
        }
    }

    private static final List<Sl> ACTIVE = new ArrayList<>();

    private BoundlessSlashManager() {
    }

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Sl(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Sl> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Sl s = it.next();
            Entity e = s.level.getEntity(s.caster);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done = false;
            try {
                if (!s.fired) {
                    if (c == null) done = true;
                    else windup(s, c);
                } else {
                    done = fly(s, c);
                }
            } catch (Exception ex) {
                LOGGER.error("Boundless Slash failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done || s.age > 600) it.remove();
        }
    }

    private static void windup(Sl s, LivingEntity c) {
        ServerLevel level = s.level;
        Vec3 look = c.getLookAngle();
        Vec3 hand = c.getEyePosition().add(look.scale(1.6)).subtract(0, 0.4, 0);
        if (s.age == 0) {
            sound(level, c.position(), SoundEvents.TRIDENT_RIPTIDE_3, 3f, 0.6f);
            sound(level, c.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 0.5f);
            announce(level, c, Component.literal("Boundless Slash").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        }
        if (s.age == 4) SukunaFx.blast(level, hand, 3.5f, 2);
        if (s.age % 2 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 9, false, false, false));
        if (s.age >= WINDUP) fire(s, c);
    }

    private static void fire(Sl s, LivingEntity c) {
        ServerLevel level = s.level;
        s.fired = true;
        s.look = c.getLookAngle().normalize();
        s.r = SukunaFx.rightOf(s.look);
        s.u = SukunaFx.upOf(s.look, s.r);
        s.origin = c.getEyePosition().add(s.look.scale(2.2)).add(0, 1.0, 0);
        int life = (int) Math.ceil(s.range / s.speed) + 6;
        com.example.jjkspells.prime.fx.PrimeFxEntity.spawn(level, com.example.jjkspells.prime.fx.PrimeFxEntity.SLASH, s.origin, s.look, null,
                (float) s.size, life, (float) s.speed, (float) TILT, -1);
        SukunaFx.blast(level, s.origin, 8f, 2);
        sound(level, s.origin, SoundEvents.WARDEN_SONIC_BOOM, 4f, 1.7f);
        sound(level, s.origin, SoundEvents.GLASS_BREAK, 5f, 0.5f);
        sound(level, s.origin, SoundEvents.TRIDENT_THUNDER, 4f, 1.6f);
    }

    /** @return true when the blade has flown its range */
    private static boolean fly(Sl s, LivingEntity c) {
        ServerLevel level = s.level;
        if (c == null) return true;
        s.flown++;
        s.blocks.newTick();
        double d1 = s.speed * s.flown, d0 = s.speed * (s.flown - 1);
        float flat = PrimeConfig.BS_FLAT.get().floatValue(), pct = PrimeConfig.BS_PERCENT.get().floatValue();
        boolean breaks = JJKConfig.breaks(PrimeConfig.BS_BREAKS);
        double reach = s.size * 0.62;
        for (double d = d0 + 0.5; d <= d1 + 0.5; d += 1.0) {
            Vec3 centre = s.origin.add(s.look.scale(d));
            AABB box = new AABB(centre, centre).inflate(reach);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (!foe(c, e) || s.hit.contains(e.getUUID())) continue;
                if (!SukunaFx.touches(e, centre, s.look, s.r, s.u, TILT, s.size, 0.3)) continue;
                s.hit.add(e.getUUID());
                Vec3 ec = e.getBoundingBox().getCenter();
                smite(level, ModSpells.BOUNDLESS_SLASH.get(), c, e, flat, pct);
                SukunaFx.dismantle(e, c, PrimeConfig.BS_DISMANTLE_SECONDS.get());
                SukunaFx.blast(level, ec, 7f, 2);
                SukunaFx.text(level, ec.add(0, e.getBbHeight() * 0.5 + 1.8, 0), 1f);
                sound(level, ec, SoundEvents.PLAYER_ATTACK_CRIT, 5f, 0.5f);
                sound(level, ec, SoundEvents.GLASS_BREAK, 4f, 0.7f);
                if (e.isAlive()) {
                    e.setDeltaMovement(e.getDeltaMovement().add(s.look.scale(0.7)).add(0, 0.25, 0));
                    e.hurtMarked = true;
                }
            }
            if (breaks) carve(s, centre);
        }
        if (s.flown % 6 == 0) sound(level, s.origin.add(s.look.scale(d1)), SoundEvents.TRIDENT_RIPTIDE_1, 2f, 1.6f);
        return d1 >= s.range;
    }

    /** Erases the thin slit the blade cuts through the terrain. */
    private static void carve(Sl s, Vec3 centre) {
        int ext = (int) Math.ceil(s.size * 0.62);
        int cx = (int) Math.floor(centre.x), cy = (int) Math.floor(centre.y), cz = (int) Math.floor(centre.z);
        for (int x = -ext; x <= ext; x++) {
            for (int y = -ext; y <= ext; y++) {
                for (int z = -ext; z <= ext; z++) {
                    Vec3 rel = new Vec3(cx + x + 0.5 - centre.x, cy + y + 0.5 - centre.y, cz + z + 0.5 - centre.z);
                    if (Math.abs(rel.dot(s.look)) > 0.85) continue;
                    if (!SukunaFx.inBlade(rel, s.r, s.u, TILT, s.size, 0.35)) continue;
                    s.blocks.erase(cx + x, cy + y, cz + z, centre, 0.5);
                }
            }
        }
    }
}
