package com.example.jjkspells.spells;

import com.example.jjkspells.CursedConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.effect.OverloadEvents;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reversal: Red as a shotgun blast. The caster is the barrel: in one instant everything inside a cone in front of
 * them is damaged (more the closer it is) and thrown away. There is no projectile. The visuals (a flash at the
 * muzzle, a spray of sparks and shockwave rings racing down the cone) are staged over a few ticks.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class ReversalRedBlast {
    private static final int VISUAL_TICKS = 6;

    private static class Visual {
        final ServerLevel level;
        final Vec3 eye, look, right, up;
        final double range, tanHalf;
        int age = 0;

        Visual(ServerLevel level, Vec3 eye, Vec3 look, double range, double half) {
            this.level = level;
            this.eye = eye;
            this.look = look;
            Vec3 r = look.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0);
            this.right = r.normalize();
            this.up = this.right.cross(look).normalize();
            this.range = range;
            this.tanHalf = Math.tan(half);
        }
    }

    private static final List<Visual> ACTIVE = new ArrayList<>();

    // =============================================================================================
    //  THE BLAST
    // =============================================================================================
    public static void fire(ServerLevel level, LivingEntity c, ReversalRedSpell spell, int spellLevel) {
        var cfg = CursedConfig.RED;
        double range = spell.getExplosionRadius(spellLevel) * cfg.blastRangeMultiplier.get();
        double half = Math.toRadians(cfg.coneAngleDegrees.get());
        float damage = spell.getDamage(spellLevel, c);
        float edge = cfg.edgeDamageFraction.get().floatValue();
        double kb = spell.getKnockback(spellLevel);
        double upward = cfg.knockbackUpward.get();

        Vec3 look = c.getLookAngle().normalize();
        Vec3 eye = c.getEyePosition();
        Vec3 origin = eye.subtract(look.scale(1.2)); // pulled back so targets right beside the caster count
        AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(range * Math.tan(half) + 2.5);

        level.playSound(null, eye.x, eye.y, eye.z, ModSounds.REVERSAL_RED_IMPACT.get(), SoundSource.PLAYERS, 3f, 0.9f);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, 1.4f);

        for (Entity e : level.getEntities(c, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e instanceof LivingEntity && (e.isAlliedTo(c) || DamageSources.isFriendlyFireBetween(c, e))) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double slackWidth = e.getBbWidth() * 0.5;
            double dEye = ec.distanceTo(eye);
            if (dEye > range + slackWidth + 0.5) continue;

            // Anything within arm's reach is always caught. Further out it has to be inside the cone.
            boolean close = dEye < 2.5;
            Vec3 v = ec.subtract(origin);
            double dist = v.length();
            if (dist < 1.0E-4) continue;
            if (!close) {
                double along = v.dot(look);
                if (along <= 0) continue;
                double angle = Math.acos(Mth.clamp(along / dist, -1.0, 1.0));
                double slack = Math.atan2(slackWidth, dist);
                if (angle > half + slack) continue;
                if (!Utils.hasLineOfSight(level, eye, ec, true)) continue;
            }

            double falloff = Mth.clamp(1.0 - dEye / range, 0.0, 1.0); // 1 at the muzzle, 0 at the tip of the blast

            if (e instanceof LivingEntity living) {
                float dmg = (float) (damage * (edge + (1.0 - edge) * falloff));
                living.invulnerableTime = 0;
                DamageSources.applyDamage(living, dmg, spell.getDamageSource(c));
            }

            // Thrown away from the barrel: replace (not add to) the current motion so damage knockback can't cancel it.
            Vec3 dir = v.normalize();
            dir = new Vec3(dir.x, dir.y * 0.5, dir.z).normalize();
            double push = kb * (0.55 + 0.45 * falloff);
            double lift = upward * (0.5 + 0.5 * falloff);
            if (e instanceof LivingEntity living) {
                double resist = living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                push *= Math.max(0.0, 1.0 - resist);
                lift *= Math.max(0.0, 1.0 - resist);
            }
            e.setDeltaMovement(dir.x * push, dir.y * push + lift, dir.z * push);
            e.setOnGround(false);
            OverloadEvents.forceMove(e);
            e.hurtMarked = true;
            e.hasImpulse = true;
            e.fallDistance = 0;
        }

        // recoil: the barrel kicks back
        double recoil = cfg.recoil.get();
        Vec3 flat = new Vec3(-look.x, 0, -look.z);
        if (recoil > 0 && flat.lengthSqr() > 1.0E-4 && !(c instanceof Player p && p.getAbilities().flying)) {
            flat = flat.normalize().scale(recoil);
            c.setDeltaMovement(c.getDeltaMovement().add(flat.x, 0.08, flat.z));
            c.hurtMarked = true;
        }

        ACTIVE.add(new Visual(level, eye, look, range, half));
        muzzleFlash(level, eye, look, range, half);
    }

    // =============================================================================================
    //  VISUALS
    // =============================================================================================
    /** The instant of the shot: a flash at the muzzle and a dense spray of sparks through the whole cone. */
    private static void muzzleFlash(ServerLevel level, Vec3 eye, Vec3 look, double range, double half) {
        RandomSource rng = level.random;
        Visual v = new Visual(level, eye, look, range, half);
        Vec3 muzzle = eye.add(look.scale(0.9)).add(0, -0.25, 0);

        FX.one(level, ModParticles.RED_IMPACT.get(), muzzle, new Vec3(2.2, 0, 0));
        FX.one(level, ModParticles.RED_RING.get(), muzzle, new Vec3(1.6, 1, 0));

        // sparks leave the barrel across the cone; spark speed is tuned so the fastest reach the tip of the blast
        int sparks = 70 + (int) (range * 5);
        double base = range / 8.5; // sparks coast about 8.5x their launch speed before they fade
        for (int i = 0; i < sparks; i++) {
            Vec3 dir = coneDirection(v, rng, half * (0.15 + 0.85 * Math.sqrt(rng.nextDouble())));
            double speed = base * (0.35 + 0.75 * rng.nextDouble());
            FX.one(level, ModParticles.RED_SPARK.get(), muzzle, dir.scale(speed));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Visual> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Visual v = it.next();
            shockwave(v);
            if (++v.age >= VISUAL_TICKS) it.remove();
        }
    }

    /** Rings and sparks racing down the cone: the blast front. */
    private static void shockwave(Visual v) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        double k = (v.age + 1) / (double) VISUAL_TICKS;
        double d = v.range * (0.1 + 0.9 * (1 - (1 - k) * (1 - k))); // fast start, ends exactly at the tip
        Vec3 front = v.eye.add(v.look.scale(d));
        double ringR = Math.max(0.8, d * v.tanHalf * 0.95);

        // the wall of red pushing outward
        FX.one(level, ModParticles.RED_RING.get(), front, new Vec3(ringR, 1, 0));
        if (v.age % 2 == 0) FX.one(level, ModParticles.RED_RING.get(), v.eye.add(v.look.scale(d * 0.6)), new Vec3(ringR * 0.7, 1, 0));

        // sparks shed from the front, still travelling outward
        int n = 16 + (int) (v.range);
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double rad = ringR * Math.sqrt(rng.nextDouble());
            Vec3 p = front.add(v.right.scale(Math.cos(a) * rad)).add(v.up.scale(Math.sin(a) * rad));
            Vec3 out = p.subtract(v.eye).normalize().scale(0.25 + rng.nextDouble() * 0.5);
            FX.one(level, ModParticles.RED_SPARK.get(), p, out);
        }
        if (v.age == VISUAL_TICKS - 1) {
            FX.one(level, ModParticles.RED_IMPACT.get(), v.eye.add(v.look.scale(v.range * 0.85)), new Vec3(Math.max(2.0, ringR * 0.8), 0, 0));
        }
    }

    /** A random unit direction inside the cone, at (at most) the given angle off the axis. */
    private static Vec3 coneDirection(Visual v, RandomSource rng, double angle) {
        double phi = rng.nextDouble() * Math.PI * 2;
        double s = Math.sin(angle), co = Math.cos(angle);
        return v.look.scale(co).add(v.right.scale(Math.cos(phi) * s)).add(v.up.scale(Math.sin(phi) * s)).normalize();
    }
}
