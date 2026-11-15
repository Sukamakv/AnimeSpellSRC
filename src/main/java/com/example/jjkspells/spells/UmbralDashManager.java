package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
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

/**
 * Runs every active Umbral Dash: a controlled burst along a straight line (not a teleport), 1 block per tick for 5 ticks,
 * stopping early at a wall. Wings flare open, a crimson afterimage is left at every step, and enemies in the way are cut once.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class UmbralDashManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static class Dash {
        final ServerLevel level;
        final UUID casterId;
        final UmbralDashSpell spell;
        final int spellLevel;
        final Vec3 dir;
        final double step;
        final float size;
        double remaining;
        Vec3 last;
        final Set<UUID> hit = new HashSet<>();
        int age = 0;

        Dash(ServerLevel level, LivingEntity caster, UmbralDashSpell spell, int spellLevel, Vec3 dir, double distance, float size) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.dir = dir;
            this.size = size;
            this.remaining = distance;
            this.step = distance / UmbralDashSpell.DURATION_TICKS;
            this.last = caster.position();
        }
    }

    private static final List<Dash> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, UmbralDashSpell spell, int spellLevel) {
        // the direction he is moving in, or the way he faces if he is standing still
        Vec3 v = caster.getDeltaMovement();
        Vec3 dir = new Vec3(v.x, 0, v.z);
        if (dir.length() < 0.06) {
            Vec3 look = caster.getLookAngle();
            dir = new Vec3(look.x, 0, look.z);
        }
        dir = dir.lengthSqr() < 1.0E-4 ? AatroxFX.forward(caster) : dir.normalize();

        float size = AatroxFX.sizeScale(caster);
        double distance = UmbralDashSpell.DISTANCE * size;
        Vec3 from = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
        var hit = level.clip(new ClipContext(from, from.add(dir.scale(distance + 0.6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() != HitResult.Type.MISS) distance = Math.max(0, hit.getLocation().distanceTo(from) - 0.7);
        if (distance < 0.5) return;

        caster.addEffect(new MobEffectInstance(ModEffects.UMBRAL_STANCE.get(), UmbralDashSpell.DURATION_TICKS + 4, 0, false, false, false));
        MartialFX.sound(level, caster.position(), SoundEvents.PHANTOM_SWOOP, 1.2f, 0.6f);
        MartialFX.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        AatroxFX.wings(level, caster, 1.0f, size, 8);
        Dash d = new Dash(level, caster, spell, spellLevel, dir, distance, size);
        ACTIVE.add(d);
        setVelocity(d, caster);      // starts this very tick
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Dash> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Dash d = it.next();
            Entity e = d.level.getEntity(d.casterId);
            boolean done;
            try {
                done = !(e instanceof LivingEntity caster) || !caster.isAlive() || tick(d, caster);
            } catch (Exception ex) {
                LOGGER.error("Umbral Dash failed, cancelling it", ex);
                done = true;
            }
            d.age++;
            if (done) it.remove();
        }
    }

    private static void setVelocity(Dash d, LivingEntity caster) {
        double s = Math.min(d.step, d.remaining);
        if (s <= 0.01) return;
        // moved by position, not velocity: a player's own client would overwrite a velocity push almost at once
        Vec3 from = caster.position();
        Vec3 mid = from.add(0, caster.getBbHeight() * 0.5, 0);
        var wall = d.level.clip(new ClipContext(mid, mid.add(d.dir.scale(s + 0.4)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (wall.getType() != HitResult.Type.MISS) s = Math.max(0, wall.getLocation().distanceTo(mid) - 0.5);
        if (s <= 0.01) return;
        SunFX.place(caster, from.add(d.dir.scale(s)), caster.getYRot(), caster.getXRot(), false);
        caster.setDeltaMovement(0, Math.min(0, caster.getDeltaMovement().y), 0);
        caster.hurtMarked = true;
    }

    /** @return true when the dash is over. */
    private static boolean tick(Dash d, LivingEntity caster) {
        ServerLevel level = d.level;
        Vec3 now = caster.position();
        double moved = now.subtract(d.last).horizontalDistance();

        cut(d, caster, d.last, now);
        // crimson afterimage, wings, red-black trail
        AatroxFX.afterimage(level, caster);
        if (d.age % 2 == 0) AatroxFX.wings(level, caster, 1.0f, d.size, 5);
        AatroxFX.smoke(level, now.add(0, 0.6, 0), 2, 0.5);
        FX.one(level, ModParticles.DR_LINE.get(), d.last.add(0, 1.0, 0), now.subtract(d.last));
        FX.one(level, ModParticles.DR_SPARK.get(), now.add(0, 0.3, 0), new Vec3(-d.dir.x * 0.1, 0.1, -d.dir.z * 0.1));

        d.remaining -= moved;
        boolean blocked = d.age > 0 && moved < 0.2;
        d.last = now;
        if (d.remaining <= 0.15 || blocked || d.age > 9) {
            finish(d, caster);
            return true;
        }
        setVelocity(d, caster);
        return false;
    }

    /** Feet planted, the sword swung up into a ready stance. */
    private static void finish(Dash d, LivingEntity caster) {
        ServerLevel level = d.level;
        Vec3 v = caster.getDeltaMovement();
        caster.setDeltaMovement(0, v.y, 0);
        caster.hurtMarked = true;
        Vec3 side = new Vec3(-d.dir.z, 0, d.dir.x);
        Vec3 vv = side.scale(Math.cos(0.5)).add(0, Math.sin(0.5), 0);
        Vec3 c = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
        DariusFX.ribbon(level, c, d.dir, vv, 2.6f * d.size, 2.8f * d.size, 0f, -1.2f, 1.2f, 1.1f * d.size, 12, MoonRibbonOptions.BRIGHT_RED);
        DariusFX.ribbon(level, c, d.dir, vv, 2.9f * d.size, 3.1f * d.size, 0.3f, -1.0f, 1.1f, 0.45f * d.size, 11, MoonRibbonOptions.BLOOD);
        DariusFX.ring(level, caster.position(), 2.4f * d.size);
        MartialFX.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.55f);
    }

    /** Cuts every enemy near the stretch of path just travelled, once each. */
    private static void cut(Dash d, LivingEntity caster, Vec3 a, Vec3 b) {
        ServerLevel level = d.level;
        double width = UmbralDashSpell.CUT_WIDTH * d.size;
        AABB box = new AABB(a, b).inflate(width + 1, 1.5, width + 1);
        Vec3 seg = new Vec3(b.x - a.x, 0, b.z - a.z);
        double segLen2 = Math.max(seg.lengthSqr(), 1.0E-6);
        float damageBase = UmbralDashSpell.DAMAGE * DariusFX.levelScale(d.spellLevel);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster) || d.hit.contains(e.getUUID())) continue;
            double px = e.getX() - a.x, pz = e.getZ() - a.z;
            double t = Math.max(0, Math.min(1, (px * seg.x + pz * seg.z) / segLen2));
            double dx = px - seg.x * t, dz = pz - seg.z * t;
            if (Math.sqrt(dx * dx + dz * dz) > width + e.getBbWidth() * 0.5) continue;
            d.hit.add(e.getUUID());

            if (!ViegoFX.strike(caster, d.spell, e, damageBase, UmbralDashSpell.MAX_HEALTH_PERCENT)) continue;   // percent ignores damage caps
            Vec3 p = e.getBoundingBox().getCenter();
            DariusFX.redBurst(level, p, 1.3f);
            AatroxFX.blood(level, p, 7, 0.28);
            MartialFX.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        }
    }
}
