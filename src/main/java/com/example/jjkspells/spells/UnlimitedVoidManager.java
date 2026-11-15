package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.entity.UnlimitedVoidDome;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Runs every active Unlimited Dom on the server.
 *
 *   CHARGE - the hand sign: a seal spins up on the ground, a galaxy of stars spirals into the caster's hands
 *            around a tiny black seed ringed with light, which grows until it bursts.
 *   ACTIVE - the dome stands. Every enemy inside gets Overload for as long as it lasts. The caster is immune.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class UnlimitedVoidManager {

    private static final int CHARGE_TICKS = 72;
    private static final int EXPAND_TICK = 66;

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private enum Phase { CHARGE, ACTIVE }

    private record Basis(Vec3 f, Vec3 r, Vec3 u) {
        static Basis of(Vec3 look) {
            Vec3 f = look.normalize();
            Vec3 r = f.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0);
            r = r.normalize();
            Vec3 u = r.cross(f).normalize();
            return new Basis(f, r, u);
        }
    }

    private static class Dom {
        final ServerLevel level;
        final UUID casterId;
        final float radius;
        final int duration;
        Phase phase = Phase.CHARGE;
        int age = 0;
        int phaseAge = 0;
        Vec3 center = Vec3.ZERO;
        UnlimitedVoidDome dome;

        Dom(ServerLevel level, LivingEntity caster, UnlimitedVoidSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.radius = spell.getRadius(spellLevel);
            this.duration = spell.getDurationTicks(spellLevel);
        }
    }

    private static final List<Dom> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, UnlimitedVoidSpell spell, int spellLevel) {
        ACTIVE.add(new Dom(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Dom> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Dom v = it.next();
            Entity e = v.level.getEntity(v.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;

            boolean done = false;
            try {
                if (v.phase == Phase.CHARGE) {
                    if (caster == null) done = true;
                    else charge(v, caster);
                } else {
                    done = active(v, caster);
                }
            } catch (Exception ex) {
                LOGGER.error("Unlimited Dom failed, cancelling it", ex);
                done = true;
            }
            if (done && v.dome != null) v.dome.discard();
            v.age++;
            v.phaseAge++;
            if (done || v.age > 2400) it.remove();
        }
    }

    // =============================================================================================
    //  CHARGE
    // =============================================================================================
    private static void charge(Dom v, LivingEntity c) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        int t = v.age;
        double k = Math.min(1.0, t / (double) EXPAND_TICK);
        double sm = smooth(k);
        float R = v.radius;

        Basis b = Basis.of(c.getLookAngle());
        Vec3 f = b.f(), r = b.r(), u = b.u();
        Vec3 feet = c.position();
        Vec3 H = c.getEyePosition().add(f.scale(1.4)).subtract(u.scale(0.45)); // hands, in front of the chest

        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        // the caster is covered by Simple Domain from the first moment of the chant
        if (t % 10 == 0) com.example.jjkspells.effect.SimpleDomainEffect.grant(c, 40);

        if (t == 0) {
            sound(level, feet, SoundEvents.BEACON_ACTIVATE, 3f, 0.5f);
            announce(level, c, Component.literal("Domain Expansion").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        if (t == 36) {
            sound(level, feet, SoundEvents.RESPAWN_ANCHOR_CHARGE, 3f, 0.5f);
            announce(level, c, Component.literal("Unlimited Dom").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        }
        if (t % 10 == 0) sound(level, H, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, (float) (0.5 + k));

        // ---- the seal on the ground: spokes, two rings and an eight-point star ---------------------
        double gy = feet.y + 0.06;
        double rho = 1.2 + (Math.min(9.0, R * 0.55) - 1.2) * sm;
        double spin = t * 0.05;
        if (t % 2 == 0) {
            for (int i = 0; i < 12; i++) {
                double a = spin + i * Math.PI * 2 / 12;
                Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 from = new Vec3(feet.x, gy, feet.z).add(d.scale(rho * 0.12));
                line(level, from, d.scale(rho * 0.76));
            }
        }
        if (t % 3 == 0) {
            polygon(level, feet, gy, rho, 28, spin * 0.6);
            polygon(level, feet, gy, rho * 0.55, 16, -spin);
            for (int i = 0; i < 8; i++) {
                double a0 = -spin * 0.8 + i * Math.PI * 2 / 8, a1 = -spin * 0.8 + (i + 3) * Math.PI * 2 / 8;
                Vec3 pa = new Vec3(feet.x + Math.cos(a0) * rho * 0.82, gy, feet.z + Math.sin(a0) * rho * 0.82);
                Vec3 pb = new Vec3(feet.x + Math.cos(a1) * rho * 0.82, gy, feet.z + Math.sin(a1) * rho * 0.82);
                line(level, pa, pb.subtract(pa));
            }
        }
        if (t % 12 == 0) ring(level, ModParticles.UV_RING_FLAT.get(), new Vec3(feet.x, gy, feet.z), (float) (rho * 1.25));

        // ---- stars of the whole sky rush toward the hands ---------------------------------------------
        int rush = 3 + t / 5;
        for (int i = 0; i < rush; i++) {
            Vec3 d = rnd(rng).normalize();
            Vec3 p = H.add(d.scale(8 + rng.nextDouble() * 14));
            one(level, ModParticles.UV_STAR_IN.get(), p, H.subtract(p).scale(1.0 / 20.0));
        }
        // a galaxy: three spiral arms winding in around the hands
        for (int arm = 0; arm < 3; arm++) {
            for (int n = 0; n < 2; n++) {
                double s = ((t * 0.06 + arm / 3.0 + n * 0.5) % 1.0);
                double rad = 0.15 + 4.2 * Math.pow(1 - s, 1.6) * (0.35 + 0.65 * sm);
                double ang = s * 6.5 + t * 0.12 + arm * Math.PI * 2 / 3;
                Vec3 p = H.add(r.scale(Math.cos(ang) * rad)).add(u.scale(Math.sin(ang) * rad)).add(f.scale((rng.nextDouble() - 0.5) * 0.15));
                one(level, ModParticles.UV_STAR_IN.get(), p, Vec3.ZERO);
            }
        }

        // ---- the void seed: a black disc ringed with light --------------------------------------------
        if (t >= 6) {
            double rs = 0.10 + 0.55 * sm + (t > EXPAND_TICK - 8 ? (t - (EXPAND_TICK - 8)) * 0.08 : 0);
            glow(level, ModParticles.UV_GLOW.get(), H, (float) (rs * 3.2));
            glow(level, ModParticles.UV_GLOW.get(), H, (float) (rs * 1.7));
            glow(level, ModParticles.UV_CORE.get(), H, (float) rs);
            if (t % 5 == 0) ring(level, ModParticles.UV_RING.get(), H, (float) (rs * 4.5));
            for (int i = 0; i < 5; i++) {
                double a = t * 0.35 + i * Math.PI * 2 / 5;
                Vec3 off = r.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
                Vec3 tang = r.scale(-Math.sin(a)).add(u.scale(Math.cos(a)));
                one(level, ModParticles.UV_STAR_IN.get(), H.add(off.scale(rs * 2.0)), tang.scale(0.14));
            }
        }

        // ---- the hand sign: two crossed bars of light --------------------------------------------------
        if (t >= 8) {
            double ang = t * 0.1;
            double len = 0.35 + 0.95 * sm;
            Vec3 d1 = r.scale(Math.cos(ang)).add(u.scale(Math.sin(ang)));
            Vec3 d2 = r.scale(-Math.sin(ang)).add(u.scale(Math.cos(ang)));
            line(level, H.subtract(d1.scale(len)), d1.scale(len * 2));
            line(level, H.subtract(d2.scale(len)), d2.scale(len * 2));
        }

        // ---- expansion ---------------------------------------------------------------------------------
        if (t >= EXPAND_TICK) expand(v, c, H);
    }

    private static void expand(Dom v, LivingEntity c, Vec3 H) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        float R = v.radius;
        Vec3 center = c.position();
        v.center = center;

        sound(level, center, SoundEvents.END_PORTAL_SPAWN, 4f, 0.8f);
        sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.5f);
        glow(level, ModParticles.UV_CORE.get(), H, 1.6f);
        glow(level, ModParticles.UV_GLOW.get(), H, 9f);
        for (int i = 0; i < 4; i++) ring(level, ModParticles.UV_RING.get(), H, R * (0.35f + i * 0.2f));
        ring(level, ModParticles.UV_RING_FLAT.get(), center.add(0, 0.08, 0), R);
        ring(level, ModParticles.UV_RING_FLAT.get(), center.add(0, 0.08, 0), R * 0.65f);
        for (int i = 0; i < 90; i++) {
            one(level, ModParticles.UV_STAR.get(), H, rnd(rng).normalize().scale(0.2 + rng.nextDouble() * 0.8));
        }
        for (int i = 0; i < 30; i++) {
            Vec3 d = rnd(rng).normalize();
            line(level, H.add(d.scale(0.5)), d.scale(3 + rng.nextDouble() * 6));
        }

        UnlimitedVoidDome dome = new UnlimitedVoidDome(level, R, v.duration);
        dome.setPos(center.x, center.y, center.z);
        level.addFreshEntity(dome);
        v.dome = dome;

        v.phase = Phase.ACTIVE;
        v.phaseAge = -1;
    }

    // =============================================================================================
    //  ACTIVE
    // =============================================================================================
    /** @return true once the domain is over */
    private static boolean active(Dom v, @Nullable LivingEntity c) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        int t = v.phaseAge;
        float R = v.radius;
        Vec3 C = v.center;

        // the domain collapses if its caster dies
        if (c == null) {
            collapse(v);
            return true;
        }
        c.removeEffect(ModEffects.OVERLOAD.get());
        // Simple Domain for as long as the domain stands (re-applied in short bursts so it ends with the domain)
        if (t % 10 == 0) com.example.jjkspells.effect.SimpleDomainEffect.grant(c, 40);

        // ---- Overload for everyone inside ----------------------------------------------------------
        if (t % 4 == 0) {
            int remaining = v.duration - t;
            AABB box = new AABB(C, C).inflate(R);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (e == c || e.isSpectator() || e.isAlliedTo(c)) continue;
                if (e.hasEffect(ModEffects.SIMPLE_DOMAIN.get())) continue;
                if (e.getBoundingBox().getCenter().distanceTo(C) > R) continue;
                MobEffectInstance cur = e.getEffect(ModEffects.OVERLOAD.get());
                if (cur == null || cur.getDuration() < remaining) {
                    e.addEffect(new MobEffectInstance(ModEffects.OVERLOAD.get(), Math.max(2, remaining), 0, false, true, true), c);
                }
                // Gojo's domain also irradiates everything inside (no healing)
                SpellDamage.irradiate(e, 60);
                // bosses that are immune to status effects: hold them by force instead (still, silent, harmless, no healing)
                if (!e.hasEffect(ModEffects.OVERLOAD.get())) {
                    DioHold.hold(e, 10);
                    com.example.jjkspells.effect.OverloadEvents.blockHealing(e, 10);
                }
            }
        }

        // ---- stars drifting through the whole dome --------------------------------------------------
        int n = 8 + (int) (R * 0.7);
        for (int i = 0; i < n; i++) {
            Vec3 p = randomInSphere(rng, R * 0.96);
            Vec3 drift = new Vec3(-p.z, 0, p.x).scale(0.02 / Math.max(4, R)).add(rnd(rng).scale(0.006));
            one(level, ModParticles.UV_STAR.get(), C.add(p), drift);
        }
        // a denser sprinkle around the caster so the effect is felt up close
        for (int i = 0; i < 6; i++) {
            Vec3 p = randomInSphere(rng, 9);
            one(level, ModParticles.UV_STAR.get(), c.position().add(p).add(0, 1, 0), rnd(rng).scale(0.01));
        }
        // ... and on the floor
        int nf = 6 + (int) (R * 0.4);
        for (int i = 0; i < nf; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * R * 0.97;
            double x = C.x + Math.cos(a) * d, z = C.z + Math.sin(a) * d;
            double y = floorY(level, x, C.y + R * 0.6, z, (int) (R * 0.6 + 12), Double.NaN);
            if (Double.isNaN(y)) continue;
            double dy = y - C.y;
            if (d * d + dy * dy > R * R) continue;
            one(level, ModParticles.UV_FLOOR.get(), new Vec3(x, y + 0.04, z), new Vec3(-(z - C.z), 0, x - C.x).scale(0.02 / Math.max(4, R)));
        }
        if (t % 30 == 0) ring(level, ModParticles.UV_RING_FLAT.get(), C.add(0, 0.08, 0), R);
        if (t % 40 == 0) sound(level, C, SoundEvents.PORTAL_AMBIENT, 3f, 0.5f);

        if (t >= v.duration) {
            collapse(v);
            return true;
        }
        return false;
    }

    private static void collapse(Dom v) {
        ServerLevel level = v.level;
        RandomSource rng = level.random;
        Vec3 C = v.center;
        sound(level, C, SoundEvents.BEACON_DEACTIVATE, 4f, 0.6f);
        ring(level, ModParticles.UV_RING_FLAT.get(), C.add(0, 0.08, 0), v.radius * 1.15f);
        for (int i = 0; i < 80; i++) {
            Vec3 d = randomInSphere(rng, v.radius * 0.9);
            one(level, ModParticles.UV_STAR.get(), C.add(d), d.normalize().scale(0.3));
        }
    }

    // =============================================================================================
    //  HELPERS
    // =============================================================================================
    private static void polygon(ServerLevel level, Vec3 feet, double gy, double rad, int segs, double spin) {
        for (int i = 0; i < segs; i++) {
            double a0 = spin + i * Math.PI * 2 / segs, a1 = spin + (i + 1) * Math.PI * 2 / segs;
            Vec3 pa = new Vec3(feet.x + Math.cos(a0) * rad, gy, feet.z + Math.sin(a0) * rad);
            Vec3 pb = new Vec3(feet.x + Math.cos(a1) * rad, gy, feet.z + Math.sin(a1) * rad);
            line(level, pa, pb.subtract(pa));
        }
    }

    private static Vec3 randomInSphere(RandomSource rng, double radius) {
        Vec3 p;
        do {
            p = rnd(rng);
        } while (p.lengthSqr() > 1);
        return p.scale(radius);
    }

    private static Vec3 rnd(RandomSource rng) {
        return new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Top of the first solid block at or below startY, or fallback. */
    private static double floorY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p)) return fallback;
        for (int i = 0; i < maxDown; i++) {
            if (level.isOutsideBuildHeight(p)) return fallback;
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty()) return p.getY() + 1.0;
            p.move(Direction.DOWN);
        }
        return fallback;
    }

    private static void one(ServerLevel level, ParticleOptions type, Vec3 pos, Vec3 vel) {
        FX.one(level, type, pos, vel);
    }

    private static void glow(ServerLevel level, ParticleOptions type, Vec3 pos, float radius) {
        FX.one(level, type, pos, new Vec3(radius, 0, 0));
    }

    private static void ring(ServerLevel level, ParticleOptions type, Vec3 pos, float radius) {
        FX.one(level, type, pos, new Vec3(radius, 0, 0));
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 vec) {
        FX.one(level, ModParticles.UV_LINE.get(), from, vec);
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void announce(ServerLevel level, LivingEntity caster, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(caster) < 96 * 96) sp.displayClientMessage(text, true);
        }
    }
}
