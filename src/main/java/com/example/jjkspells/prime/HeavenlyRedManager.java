package com.example.jjkspells.prime;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.effect.OverloadEvents;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Heavenly Red: Reversal Red evolved. A red sun compresses in the caster's hands, then a colossal cone of repulsion
 * tears out of it: everything inside is hit for 200 + 10% max health and hurled away, and the terrain in the cone is erased.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class HeavenlyRedManager {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int CHARGE = 20, BLAST = 22, LINGER = 40;

    private static final class Red {
        final ServerLevel level;
        final UUID caster;
        final PrimeBlocks blocks;
        final Set<UUID> hit = new HashSet<>();
        int age;
        Vec3 origin = Vec3.ZERO, dir = new Vec3(0, 0, 1);
        Basis basis;
        double range, tan;
        boolean breaks;
        Vec3 tip = Vec3.ZERO;

        Red(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.caster = c.getUUID();
            this.blocks = new PrimeBlocks(level, c);
        }
    }

    private static final List<Red> ACTIVE = new ArrayList<>();

    private HeavenlyRedManager() {
    }

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Red(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Red> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Red r = it.next();
            Entity e = r.level.getEntity(r.caster);
            LivingEntity c = e instanceof LivingEntity le && le.isAlive() ? le : null;
            boolean done = false;
            try {
                if (r.age < CHARGE) {
                    if (c == null) done = true;
                    else charge(r, c);
                } else if (r.age == CHARGE) {
                    if (c == null) done = true;
                    else fire(r, c);
                } else if (r.age <= CHARGE + BLAST) {
                    blast(r, c, r.age - CHARGE - 1);
                } else {
                    done = linger(r, r.age - CHARGE - BLAST - 1);
                }
            } catch (Exception ex) {
                LOGGER.error("Heavenly Red failed, cancelling it", ex);
                done = true;
            }
            r.age++;
            if (done) it.remove();
        }
    }

    // ------------------------------------------------------------------------------------------ charge
    private static void charge(Red r, LivingEntity c) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        int t = r.age;
        double k = t / (double) CHARGE;
        double sm = smooth(k);
        Vec3 look = c.getLookAngle();
        Vec3 C = c.getEyePosition().add(look.scale(3.0));
        float R = (float) (0.4 + 2.8 * sm);
        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        if (t == 0) {
            sound(level, C, SoundEvents.RESPAWN_ANCHOR_CHARGE, 6f, 0.5f);
            sound(level, C, SoundEvents.BEACON_ACTIVATE, 4f, 0.5f);
            title(level, c.position(), Component.literal("HEAVENLY RED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.literal("PRIME TECHNIQUE").withStyle(ChatFormatting.DARK_RED));
            one(level, ModParticles.RED_IMPACT.get(), C, new Vec3(3.0, 0, 0));
        }
        if (t % 4 == 0) sound(level, C, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, (float) (0.4 + k));
        glow(level, ModParticles.HP_GLOW_RED.get(), C, R * 2.8f);
        glow(level, ModParticles.HP_GLOW_RED.get(), C, R * 1.7f);
        glow(level, ModParticles.HP_CORE.get(), C, R * 0.8f);
        for (int i = 0; i < 8; i++) {
            Vec3 d = unit(rng);
            one(level, ModParticles.HP_WISP_RED.get(), C.add(d.scale(R)), d.scale(0.3 + 0.3 * k));
            one(level, ModParticles.RED_SPARK.get(), C.add(d.scale(R * 0.8)), d.scale(0.45 + 0.4 * rng.nextDouble()));
        }
        for (int i = 0; i < 3; i++) {
            Vec3 d = unit(rng);
            line(level, ModParticles.FUGA_LINE.get(), C.add(d.scale(R * 1.1)), d.scale(2 + 6 * k));
        }
        if (t % 3 == 0) redRing(level, C, (float) (2 + 7 * k), false);
        // the air around is being squeezed in
        for (int i = 0; i < 4; i++) {
            Vec3 d = unit(rng);
            Vec3 p = C.add(d.scale(10 + rng.nextDouble() * 8));
            line(level, ModParticles.FUGA_LINE.get(), p, C.subtract(p).scale(0.4));
        }
    }

    // ------------------------------------------------------------------------------------------ fire
    private static void fire(Red r, LivingEntity c) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        r.origin = c.getEyePosition();
        r.dir = c.getLookAngle().normalize();
        r.basis = Basis.of(r.dir);
        r.range = PrimeConfig.HR_RANGE.get();
        r.tan = Math.tan(Math.toRadians(PrimeConfig.HR_HALF_ANGLE.get()));
        r.breaks = JJKConfig.breaks(PrimeConfig.HR_BREAKS);
        r.tip = r.origin.add(r.dir.scale(r.range));
        Vec3 M = r.origin.add(r.dir.scale(1.2));

        sound(level, M, ModSounds.REVERSAL_RED_IMPACT.get(), 8f, 0.5f);
        sound(level, M, SoundEvents.GENERIC_EXPLODE, 10f, 0.4f);
        sound(level, M, SoundEvents.WARDEN_SONIC_BOOM, 8f, 0.6f);
        sound(level, M, SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.5f);
        one(level, ModParticles.RED_IMPACT.get(), M, new Vec3(7.0, 0, 0));
        glow(level, ModParticles.HP_GLOW_RED.get(), M, 18f);
        glow(level, ModParticles.HP_CORE.get(), M, 8f);
        for (int i = 0; i < 6; i++) redRing(level, M.add(r.dir.scale(i * 2.0)), 5f + i * 3f, false);
        for (int i = 0; i < 100; i++) {
            Vec3 d = r.dir.add(unit(rng).scale(0.9)).normalize();
            one(level, ModParticles.RED_SPARK.get(), M, d.scale(0.8 + rng.nextDouble() * 2.2));
        }
        c.setDeltaMovement(c.getDeltaMovement().add(-r.dir.x * 1.3, 0.25, -r.dir.z * 1.3));
        c.hurtMarked = true;
    }

    // ------------------------------------------------------------------------------------------ blast
    private static double ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x);
    }

    private static void blast(Red r, LivingEntity c, int k) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        double dPrev = r.range * ease(k / (double) BLAST);
        double dCur = r.range * ease((k + 1) / (double) BLAST);
        Vec3 O = r.origin, D = r.dir;
        Basis b = r.basis;
        double rim = Math.max(1.5, dCur * r.tan);
        Vec3 F = O.add(D.scale(dCur));
        r.blocks.newTick();

        if (k % 3 == 0) sound(level, O.add(D.scale(dCur * 0.5)), SoundEvents.GENERIC_EXPLODE, 9f, 0.45f + rng.nextFloat() * 0.2f);

        // ---- terrain ------------------------------------------------------------------------------
        if (r.breaks) {
            double step = 0.8;
            for (double along = dPrev; along < dCur + 1e-6; along += step) {
                double rr = along * r.tan + 1.6;
                Vec3 axis = O.add(D.scale(along));
                for (double a = -rr; a <= rr; a += step) {
                    for (double bb = -rr; bb <= rr; bb += step) {
                        double d2 = a * a + bb * bb;
                        if (d2 > rr * rr) continue;
                        Vec3 p = axis.add(b.r().scale(a)).add(b.u().scale(bb));
                        int x = (int) Math.floor(p.x), y = (int) Math.floor(p.y), z = (int) Math.floor(p.z);
                        if (d2 > rr * rr * 0.68 && PrimeBlocks.jag(x, y, z) > 0.5) continue;
                        r.blocks.erase(x, y, z, O, 1.4);
                    }
                }
            }
        }

        // ---- creatures ----------------------------------------------------------------------------
        AABB box = new AABB(O, F).inflate(rim + 3);
        float flat = PrimeConfig.HR_FLAT.get().floatValue(), pct = PrimeConfig.HR_PERCENT.get().floatValue();
        double kb = PrimeConfig.HR_KNOCKBACK.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!foe(c, e) || r.hit.contains(e.getUUID())) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            Vec3 rel = ec.subtract(O);
            double along = rel.dot(D);
            if (along < -2.5 || along > dCur + e.getBbWidth()) continue;
            double dist = rel.length();
            double lateral = Math.sqrt(Math.max(0, dist * dist - along * along));
            if (lateral > Math.max(along, 0) * r.tan + e.getBbWidth() * 0.5 + 2.2) continue;
            if (!r.breaks && dist > 3 && !Utils.hasLineOfSight(level, O, ec, true)) continue;
            r.hit.add(e.getUUID());
            smite(level, ModSpells.HEAVENLY_RED.get(), c, e, flat, pct);
            one(level, ModParticles.RED_IMPACT.get(), ec, new Vec3(3.0, 0, 0));
            for (int i = 0; i < 20; i++) one(level, ModParticles.RED_SPARK.get(), ec, unit(rng).scale(0.5 + rng.nextDouble()));
            if (e.isAlive() || true) {
                Vec3 out = rel.lengthSqr() < 1.0E-4 ? D : rel.normalize();
                out = new Vec3(out.x, out.y * 0.5, out.z).normalize();
                double push = kb * (0.55 + 0.45 * Math.max(0, 1 - dist / r.range));
                push *= Math.max(0.0, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                e.setDeltaMovement(out.x * push, out.y * push + 0.9, out.z * push);
                e.setOnGround(false);
                OverloadEvents.forceMove(e);
                e.hurtMarked = true;
                e.hasImpulse = true;
                e.fallDistance = 0;
            }
        }

        // ---- the show -----------------------------------------------------------------------------
        redRing(level, F, (float) (rim * 0.95), false);
        redRing(level, F, (float) (rim * 0.6), false);
        if (k % 2 == 0) redRing(level, O.add(D.scale(dCur * 0.55)), (float) (rim * 0.55), false);
        for (double along = dPrev; along < dCur; along += 3.0) {
            glow(level, ModParticles.HP_GLOW_RED.get(), O.add(D.scale(along)), (float) Math.min(18, 4 + along * 0.18));
            if (rng.nextBoolean()) glow(level, ModParticles.HP_CORE.get(), O.add(D.scale(along)), (float) Math.min(8, 2 + along * 0.08));
        }
        int sparks = 40 + (int) (rim);
        for (int i = 0; i < sparks; i++) {
            double ang = rng.nextDouble() * Math.PI * 2, rad = rim * Math.sqrt(rng.nextDouble());
            Vec3 p = F.add(b.r().scale(Math.cos(ang) * rad)).add(b.u().scale(Math.sin(ang) * rad));
            Vec3 out = p.subtract(O).normalize();
            one(level, ModParticles.RED_SPARK.get(), p, out.scale(0.5 + rng.nextDouble() * 1.2));
            if (i % 2 == 0) one(level, ModParticles.HP_WISP_RED.get(), p, out.scale(0.6 + rng.nextDouble() * 1.0));
        }
        for (int i = 0; i < 16; i++) {
            double along = dPrev + rng.nextDouble() * Math.max(1, dCur - dPrev);
            double ang = rng.nextDouble() * Math.PI * 2;
            Vec3 radial = b.r().scale(Math.cos(ang)).add(b.u().scale(Math.sin(ang)));
            Vec3 A = O.add(D.scale(along));
            line(level, ModParticles.FUGA_BEAM.get(), A, radial.scale(along * r.tan * (0.4 + 0.6 * rng.nextDouble())));
        }
        for (int i = 0; i < 2; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            Vec3 radial = b.r().scale(Math.cos(ang)).add(b.u().scale(Math.sin(ang)));
            bolt(level, ModParticles.FUGA_LINE.get(), O.add(D.scale(dPrev * 0.4)), F.add(radial.scale(rim * 0.9)), 1.8, 6);
        }
        if (k == BLAST - 1) {
            Vec3 T = r.tip;
            sound(level, T, SoundEvents.GENERIC_EXPLODE, 12f, 0.35f);
            sound(level, T, SoundEvents.WARDEN_SONIC_BOOM, 10f, 0.5f);
            for (int i = 0; i < 6; i++) {
                one(level, ModParticles.RED_IMPACT.get(), T.add(unit(rng).scale(rim * 0.35)), new Vec3(5.0 + rng.nextDouble() * 5.0, 0, 0));
            }
            glow(level, ModParticles.HP_GLOW_RED.get(), T, 40f);
            glow(level, ModParticles.HP_CORE.get(), T, 18f);
            redRing(level, floor(level, T).add(0, 0.2, 0), (float) (rim * 1.4), true);
        }
    }

    private static boolean linger(Red r, int k) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        if (k >= LINGER) return true;
        double rim = Math.max(3, r.range * r.tan);
        Vec3 T = floor(level, r.tip);
        // a pillar of red torn up through the sky where the blast ends
        for (int i = 0; i < 10; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = rng.nextDouble() * 7;
            one(level, ModParticles.HP_WISP_RED.get(), T.add(Math.cos(a) * d, rng.nextDouble() * 3, Math.sin(a) * d), new Vec3(0, 0.7 + rng.nextDouble() * 1.4, 0));
        }
        if (k % 5 == 0) {
            one(level, ModParticles.RED_IMPACT.get(), T.add((rng.nextDouble() - 0.5) * rim, 1, (rng.nextDouble() - 0.5) * rim), new Vec3(4 + rng.nextDouble() * 4, 0, 0));
            redRing(level, T.add(0, 0.2, 0), (float) (rim * (0.5 + k * 0.03)), true);
        }
        for (int i = 0; i < 12; i++) {
            Vec3 p = T.add((rng.nextDouble() - 0.5) * rim, rng.nextDouble() * 6, (rng.nextDouble() - 0.5) * rim);
            one(level, ModParticles.RED_SPARK.get(), p, new Vec3(0, 0.3 + rng.nextDouble() * 0.4, 0));
        }
        return false;
    }
}
