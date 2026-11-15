package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.effect.SimpleDomainEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Ticks every active Malevolent Shrine on the server. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class ShrineManager {

    // ---- Tuning -------------------------------------------------------------
    private static final int DISMANTLE_INTERVAL = 4;   // ticks between Dismantle volleys
    private static final int CLEAVE_INTERVAL = 15;     // ticks between Cleave strikes

    public static final float SLASH_DAMAGE = 10f;      // flat damage of each blood slash
    private static final int SLASH_INTERVAL = 20;      // ticks between slash volleys
    private static final int SLASH_COUNT = 4;          // slashes per volley (spread evenly around caster)
    private static final double SLASH_SPEED = 1.1;     // blocks per tick
    private static final int SLASH_LIFE = 16;          // ticks a slash flies

    private static final boolean DARK_DOME = false;    // faint dark shell over the domain (off by default; true to enable)

    private static final int SLASH_LINES_PER_TICK = 8; // straight slash lines drawn each tick

    // Charge ("Domain Expansion" gesture): ink floods the ground, a shrine gate rises behind the caster,
    // slashes converge on the clasped hands, then the domain opens.
    private static final int CHARGE_TICKS = 72;
    private static final double GATE_BACK = 5.0;       // how far behind the caster the shrine gate stands
    private static final double GATE_HALF_WIDTH = 4.0; // posts stand this far either side of the centre line
    private static final double GATE_HEIGHT = 8.0;
    /** Everything inside the domain is kept from healing for this long after the last strike (ticks). */
    private static final int NO_HEAL_TICKS = 30;

    // Ink smoke density, in puffs spawned per tick. Each puff lives ~4s, so raise these
    // for thicker smoke or lower them if the framerate drops (puffs are big and translucent).
    private static final int INK_GROUND_PER_TICK = 4;
    private static final int INK_MID_PER_TICK = 2;
    private static final int INK_WALL_PER_TICK = 4;

    /** Soft dependency: if the mod isn't installed the effect is simply skipped. */
    private static final ResourceLocation BLEEDING_ID = ResourceLocation.tryParse("dungeons_and_combat:bleeding");

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.70f, 0.02f, 0.03f), 1.5f);
    private static final DustParticleOptions BLOOD_BIG = new DustParticleOptions(new Vector3f(0.55f, 0.0f, 0.02f), 2.4f);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.04f, 0.0f, 0.06f), 3.0f);

    // ---- State --------------------------------------------------------------
    private static class Slash {
        Vec3 pos;
        final Vec3 dir;
        int life;
        final Set<UUID> hit = new HashSet<>();

        Slash(Vec3 pos, Vec3 dir, int life) {
            this.pos = pos;
            this.dir = dir;
            this.life = life;
        }
    }

    private static class Shrine {
        final ServerLevel level;
        final UUID casterId;
        Vec3 center;
        final float radius;
        final int spellLevel;
        final MalevolentShrineSpell spell;
        final int duration;
        final List<Slash> slashes = new ArrayList<>();
        int age = 0;
        boolean charging = true;
        int chargeAge = 0;
        Vec3 gateBase;
        Vec3 gateRight;

        Shrine(ServerLevel level, LivingEntity caster, MalevolentShrineSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.center = caster.position();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.radius = spell.getRadius(spellLevel);
            this.duration = spell.getDurationTicks(spellLevel);
            placeGate(this, caster);
        }
    }

    private static final List<Shrine> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, MalevolentShrineSpell spell, int spellLevel) {
        ACTIVE.add(new Shrine(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Shrine> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Shrine s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            boolean expired = s.age >= s.duration;
            if (!(e instanceof LivingEntity caster) || !caster.isAlive() || expired) {
                if (expired) endShrine(s);
                it.remove();
                continue;
            }
            if (s.charging) {
                chargeTick(s, caster);
                s.chargeAge++;
                if (s.chargeAge >= CHARGE_TICKS) openDomain(s, caster);
                continue;
            }
            SimpleDomainEffect.grant(caster, 40); // the caster is covered by Simple Domain while the Shrine stands
            tickShrine(s, caster);
            s.age++;
        }
    }

    // ---- Per-tick logic -----------------------------------------------------
    private static void tickShrine(Shrine s, LivingEntity caster) {
        ServerLevel level = s.level;
        var rng = level.random;
        double area = Math.min(2.0, Math.max(1.0, s.radius * s.radius / 144.0)); // bigger domains need more smoke
        double cx = s.center.x, cy = s.center.y, cz = s.center.z;

        // Opening: shockwave ring racing out to the edge of the domain
        if (s.age == 0) {
            level.playSound(null, cx, cy, cz, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 2.5f, 0.6f);
        }
        if (s.age < 20) {
            double r = s.radius * (s.age + 1) / 20.0;
            for (int i = 0; i < 32; i++) {
                double a = (Math.PI * 2 / 32) * i;
                level.sendParticles(BLOOD_BIG, cx + Math.cos(a) * r, cy + 0.3, cz + Math.sin(a) * r,
                        2, 0.15, 0.3, 0.15, 0);
            }
        }

        // Dark zone: flat ink smoke lying on the ground (custom particle, see InkSmokeParticle).
        // count = 0 makes the last three numbers the particle's velocity.
        var ink = ModParticles.INK_SMOKE.get();

        // Opening burst: the ink floods outward across the floor from the caster
        if (s.age == 0) {
            for (int i = 0; i < 70; i++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double v = s.radius * 0.04 * (0.4 + 0.6 * rng.nextDouble());
                spawnInk(level, ink, cx, cy, cz, 0.05 + rng.nextDouble() * 0.2, Math.cos(a) * v, 0, Math.sin(a) * v);
            }
        }

        // Layer 1: base floor across the whole domain
        for (int i = 0; i < (int) Math.round(INK_GROUND_PER_TICK * area); i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * s.radius;
            spawnInk(level, ink, cx + Math.cos(a) * d, cy, cz + Math.sin(a) * d, 0.04 + rng.nextDouble() * 0.12,
                    (rng.nextDouble() - 0.5) * 0.03, 0, (rng.nextDouble() - 0.5) * 0.03);
        }
        // Layer 2: a second sheet slightly higher up, so overlapping layers look thick and deep
        for (int i = 0; i < (int) Math.round(INK_MID_PER_TICK * area); i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * s.radius;
            spawnInk(level, ink, cx + Math.cos(a) * d, cy, cz + Math.sin(a) * d, 0.2 + rng.nextDouble() * 0.35,
                    (rng.nextDouble() - 0.5) * 0.02, 0.002, (rng.nextDouble() - 0.5) * 0.02);
        }
        // Layer 3: denser rim along the domain boundary, slowly swirling
        for (int i = 0; i < (int) Math.round(INK_WALL_PER_TICK * area); i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = s.radius * (0.82 + rng.nextDouble() * 0.18);
            double tx = -Math.sin(a) * 0.04, tz = Math.cos(a) * 0.04; // tangent: slow clockwise swirl
            spawnInk(level, ink, cx + Math.cos(a) * d, cy, cz + Math.sin(a) * d, 0.05 + rng.nextDouble() * 0.3,
                    tx, 0, tz);
        }

        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * s.radius;
            level.sendParticles(ParticleTypes.ASH, cx + Math.cos(a) * d, cy + 1 + rng.nextDouble() * 5, cz + Math.sin(a) * d,
                    3, 1.0, 1.0, 1.0, 0);
        }

        // Dark dome shell (flattened hemisphere) so the whole domain reads as a "zone"
        if (DARK_DOME) {
            for (int i = 0; i < 6; i++) {
                double theta = rng.nextDouble() * Math.PI * 2;
                double phi = rng.nextDouble() * (Math.PI / 2);
                level.sendParticles(DARK,
                        cx + Math.cos(phi) * Math.cos(theta) * s.radius,
                        cy + Math.sin(phi) * s.radius * 0.6,
                        cz + Math.cos(phi) * Math.sin(theta) * s.radius,
                        3, 0.8, 0.8, 0.8, 0);
            }
        }

        // Slash storm: straight cuts at random heights and angles all over the domain
        int lines = (int) Math.round(SLASH_LINES_PER_TICK * area);
        if (s.age % CLEAVE_INTERVAL == 0) lines += 10; // extra flurry on every Cleave strike
        drawSlashStorm(s, lines);

        // Swirling crimson boundary
        for (int i = 0; i < 12; i++) {
            double ang = (Math.PI * 2 / 12) * i + s.age * 0.05;
            double rx = cx + Math.cos(ang) * s.radius;
            double rz = cz + Math.sin(ang) * s.radius;
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, rx, cy + 0.2, rz, 3, 0.1, 1.5, 0.1, 0.01);
            level.sendParticles(BLOOD, rx, cy + 1.0, rz, 2, 0.15, 1.2, 0.15, 0);
        }

        // Blood helix spiralling up around the caster
        Vec3 cp = caster.position();
        for (int k = 0; k < 6; k++) {
            double h = k * 0.45;
            for (int strand = 0; strand < 2; strand++) {
                double a = s.age * 0.3 + h * 2.0 + strand * Math.PI;
                level.sendParticles(BLOOD, cp.x + Math.cos(a) * 1.3, cp.y + h, cp.z + Math.sin(a) * 1.3,
                        1, 0, 0, 0, 0);
            }
        }

        // The shrine gate stays standing behind where the caster opened the domain
        if (s.age % 3 == 0) drawGate(s, 1.0, 1.0);

        // Ominous heartbeat once a second
        if (s.age % 20 == 0) {
            level.playSound(null, cx, cy, cz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 3.0f, 0.7f);
        }

        // Blood slash volleys launched around the caster
        if (s.age >= 10 && s.age % SLASH_INTERVAL == 0) {
            double base = rng.nextDouble() * Math.PI * 2;
            for (int i = 0; i < SLASH_COUNT; i++) {
                double a = base + (Math.PI * 2 / SLASH_COUNT) * i;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 start = caster.position().add(0, 1.0, 0).add(dir.scale(1.0));
                s.slashes.add(new Slash(start, dir, SLASH_LIFE));
            }
            level.playSound(null, cp.x, cp.y, cp.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.6f, 0.5f);
        }
        tickSlashes(s, caster);

        // ---- Combat ----
        boolean dismantleTick = s.age % DISMANTLE_INTERVAL == 0;
        boolean cleaveTick = s.age % CLEAVE_INTERVAL == 0;
        if (!dismantleTick && !cleaveTick) return;

        if (dismantleTick) {
            level.playSound(null, cx, cy, cz,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 0.6f + rng.nextFloat() * 0.8f);
        }

        // Guaranteed hits on everything in range except the caster and allies
        AABB box = new AABB(s.center, s.center).inflate(s.radius);
        float power = s.spell.getSpellPower(s.spellLevel, caster);
        float dismantle = power * MalevolentShrineSpell.DISMANTLE_FACTOR;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == caster || target.isAlliedTo(caster) || !target.isAlive()) continue;
            if (SimpleDomainEffect.has(target)) continue; // Simple Domain: immune to the Shrine's damage and effects
            if (target.position().distanceTo(s.center) > s.radius) continue;

            // Nothing caught in the Shrine can heal, in any way
            SpellDamage.irradiate(target, 100);                       // Alex's Caves Irradiated: the effect that really blocks healing
            OverloadEvents.blockHealing(target, NO_HEAL_TICKS);       // fallback when that mod is not installed

            // Darkness closes in on anyone caught inside (refreshed every second)
            if (s.age % 20 == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
            }

            if (dismantleTick) {
                target.invulnerableTime = 0; // domain hits ignore i-frames
                DamageSources.applyDamage(target, dismantle, s.spell.getDamageSource(caster));
                applyBleeding(target, s.spellLevel);
                bloodBurst(level, target, 4);
            }
            if (cleaveTick) {
                // Cleave adapts to toughness: its max-health share is not capped
                // a flat floor, plus a share of max health that nothing caps
                target.invulnerableTime = 0;
                DamageSources.applyDamage(target, dismantle * 0.5f, s.spell.getDamageSource(caster));
                SpellDamage.percent(target, target.getMaxHealth() * s.spell.cleavePercent(s.spellLevel), caster, level.damageSources().indirectMagic(caster, caster));
                applyBleeding(target, s.spellLevel);
                bloodBurst(level, target, 16);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY(0.5), target.getZ(), 6, 0.3, 0.4, 0.3, 0.1);
            }
        }
    }

    // ---- Charge -------------------------------------------------------------------------------
    /** The gate stands behind the caster, facing the way the caster faces when the chant begins / when it opens. */
    private static void placeGate(Shrine s, LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 f = new Vec3(look.x, 0, look.z);
        f = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 base = caster.position().subtract(f.scale(GATE_BACK));
        s.gateBase = new Vec3(base.x, groundY(s.level, base.x, caster.getY(), base.z), base.z);
        s.gateRight = new Vec3(-f.z, 0, f.x);
    }

    private static void chargeTick(Shrine s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        int t = s.chargeAge;
        float R = s.radius;
        double k = t / (double) CHARGE_TICKS;
        double sm = k * k * (3 - 2 * k);

        s.center = c.position();
        placeGate(s, c);
        Vec3 p = s.center;
        Vec3 hands = c.getEyePosition().add(c.getLookAngle().scale(1.1)).add(0, -0.55, 0);

        // the caster stands still, hands clasped, and is already covered by Simple Domain
        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        if (t % 10 == 0) SimpleDomainEffect.grant(c, 40);

        // ---- stage markers: chant, sounds, ground rings ----
        if (t == 0) {
            announce(level, c, Component.literal("Domain Expansion").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            level.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 3f, 0.6f);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 3f, 0.5f);
            FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(R * 0.25, 0, 0));
        }
        if (t == 24) {
            announce(level, c, Component.literal("Malevolent Shrine").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            level.playSound(null, p.x, p.y, p.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 3.5f, 0.4f);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2f, 1.6f);
            FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(R * 0.55, 0, 0));
        }
        if (t == 38) FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(R * 0.8, 0, 0));
        if (t == 50) {
            FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(R, 0, 0));
            level.playSound(null, p.x, p.y, p.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 3.5f, 0.5f);
        }
        if (t % 10 == 5) level.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.5f, 0.6f + (float) k * 0.5f);

        // ---- the cursed energy gathering in the clasped hands ----
        FX.one(level, ModParticles.WC_GLOW.get(), hands, new Vec3(0.4 + 2.6 * sm, 0, 0));
        for (int i = 0; i < 2; i++) {
            Vec3 o = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1).normalize().scale(1.5 + rng.nextDouble() * 2);
            Vec3 from = hands.add(o);
            FX.one(level, ModParticles.FUGA_EMBER.get(), from, hands.subtract(from).scale(0.12));
        }

        // ---- the ink floods outward across the floor, wider as the chant goes on ----
        double extent = R * (0.15 + 0.85 * sm);
        int puffs = 3 + (int) (4 * k);
        var ink = ModParticles.INK_SMOKE.get();
        for (int i = 0; i < puffs; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * extent;
            spawnInk(level, ink, p.x + Math.cos(a) * d, p.y, p.z + Math.sin(a) * d, 0.04 + rng.nextDouble() * 0.15,
                    Math.cos(a) * 0.02, 0, Math.sin(a) * 0.02);
        }

        // ---- blood helix around the caster ----
        for (int h = 0; h < 5; h++) {
            for (int strand = 0; strand < 2; strand++) {
                double a = t * 0.3 + h * 0.9 + strand * Math.PI;
                level.sendParticles(BLOOD, p.x + Math.cos(a) * 1.2, p.y + h * 0.5, p.z + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
            }
        }

        // ---- stage B: pillars of blood on a ring around the caster ----
        if (t >= 24 && t % 2 == 0) {
            double gp = Math.min(1.0, (t - 24) / 22.0);
            double height = 2 + 8 * gp * gp * (3 - 2 * gp);
            double ringR = R * 0.55;
            for (int n = 0; n < 8; n++) {
                double a = n * Math.PI / 4 + t * 0.02;
                double px = p.x + Math.cos(a) * ringR, pz = p.z + Math.sin(a) * ringR;
                double gy = groundY(level, px, p.y, pz);
                for (double y = 0; y < height; y += 1.1) {
                    level.sendParticles(BLOOD_BIG, px, gy + y, pz, 1, 0.12, 0.1, 0.12, 0);
                }
            }
        }

        // ---- the shrine gate rises behind the caster: posts first, then the beams ----
        if (t >= 28 && t % 2 == 0) {
            double posts = Math.min(1.0, (t - 28) / 18.0);
            double beams = Math.min(1.0, Math.max(0, (t - 44) / 14.0));
            drawGate(s, posts, beams);
        }

        // ---- slashes converge: sparse at first, a storm by the end ----
        int lines = t < 24 ? 2 : (t < 52 ? 5 : 9);
        drawSlashStorm(s, lines, R * (0.3 + 0.7 * sm));
        if (t >= 40 && t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double d = 5 + rng.nextDouble() * 6;
                Vec3 from = p.add(Math.cos(a) * d, 0.3 + rng.nextDouble() * 5, Math.sin(a) * d);
                FX.one(level, ModParticles.WC_INK.get(), from, hands.subtract(from).scale(0.6));
            }
        }
        if (t >= 52 && t % 3 == 0) FX.one(level, ModParticles.WC_RING.get(), hands, new Vec3(4.0 - 3.0 * ((t - 52) / 20.0), 0, 0));
    }

    /** The Malevolent Shrine opens: a flash, shockwaves, and the domain proper begins. */
    private static void openDomain(Shrine s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        Vec3 p = s.center = c.position();
        Vec3 hands = c.getEyePosition().add(c.getLookAngle().scale(1.1)).add(0, -0.55, 0);
        s.charging = false;
        s.age = 0;

        announce(level, c, Component.literal("Domain Expansion: Malevolent Shrine").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        level.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4f, 0.6f);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 4f, 0.35f);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 3f, 0.7f);
        FX.one(level, ModParticles.WC_GLOW.get(), hands, new Vec3(6, 0, 0));
        FX.one(level, ModParticles.WC_GLOW.get(), p.add(0, 1, 0), new Vec3(9, 0, 0));
        for (int i = 0; i < 3; i++) {
            FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08 + i * 0.02, 0), new Vec3(s.radius * (0.8 + i * 0.25), 0, 0));
        }
        FX.one(level, ModParticles.WC_RING.get(), p.add(0, 1, 0), new Vec3(10, 0, 0));
        for (int i = 0; i < 40; i++) {
            Vec3 d = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 1.2 - 0.3, rng.nextDouble() * 2 - 1).normalize();
            FX.one(level, ModParticles.WC_INK.get(), p.add(0, 1, 0).add(d.scale(1.5)), d.scale(5 + rng.nextDouble() * 8));
        }
        drawGate(s, 1.0, 1.0);
    }

    /**
     * Draws the shrine gate (two posts, an upturned top beam and a lower crossbeam) behind the caster.
     * posts / beams are 0..1 growth values, so the same code animates the rising gate and holds it standing.
     */
    private static void drawGate(Shrine s, double posts, double beams) {
        ServerLevel level = s.level;
        Vec3 b = s.gateBase, r = s.gateRight;
        double h = GATE_HEIGHT;
        double half = GATE_HALF_WIDTH;

        if (posts > 0) {
            for (double y = 0; y <= h * posts; y += 0.8) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 q = b.add(r.scale(side * half)).add(0, y, 0);
                    level.sendParticles(BLOOD_BIG, q.x, q.y, q.z, 1, 0.1, 0.05, 0.1, 0);
                }
            }
        }
        if (beams > 0) {
            // top beam: sweeps up at both ends
            double reach = (half + 1.9) * beams;
            for (double x = -reach; x <= reach; x += 0.8) {
                double up = 0.9 * Math.pow(Math.abs(x) / (half + 1.9), 2);
                Vec3 q = b.add(r.scale(x)).add(0, h + up, 0);
                level.sendParticles(DARK, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0);
            }
            // lower crossbeam between the posts
            double lower = half * beams;
            for (double x = -lower; x <= lower; x += 0.8) {
                Vec3 q = b.add(r.scale(x)).add(0, h - 1.7, 0);
                level.sendParticles(BLOOD_BIG, q.x, q.y, q.z, 1, 0.1, 0.05, 0.1, 0);
            }
        }
    }

    private static void announce(ServerLevel level, LivingEntity caster, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(caster) < 96 * 96) sp.displayClientMessage(text, true);
        }
    }

    // ---- Blood slashes ------------------------------------------------------
    private static void tickSlashes(Shrine s, LivingEntity caster) {
        ServerLevel level = s.level;
        Iterator<Slash> it = s.slashes.iterator();
        while (it.hasNext()) {
            Slash sl = it.next();
            sl.pos = sl.pos.add(sl.dir.scale(SLASH_SPEED));
            sl.life--;

            drawSlash(level, sl);

            AABB hitBox = new AABB(sl.pos, sl.pos).inflate(1.4, 1.2, 1.4);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, hitBox)) {
                if (t == caster || t.isAlliedTo(caster) || !t.isAlive() || SimpleDomainEffect.has(t) || !sl.hit.add(t.getUUID())) continue;
                t.invulnerableTime = 0;
                DamageSources.applyDamage(t, SLASH_DAMAGE, s.spell.getDamageSource(caster));
                applyBleeding(t, s.spellLevel);
                bloodBurst(level, t, 10);
            }

            if (sl.life <= 0 || sl.pos.distanceTo(s.center) > s.radius) {
                it.remove();
            }
        }
    }

    /** Crescent-shaped blood arc: wide at the sides, trailing behind the tip. */
    private static void drawSlash(ServerLevel level, Slash sl) {
        Vec3 side = new Vec3(-sl.dir.z, 0, sl.dir.x);
        for (int i = -3; i <= 3; i++) {
            double t = i / 3.0;
            Vec3 p = sl.pos
                    .add(side.scale(t * 1.8))
                    .subtract(sl.dir.scale(t * t * 1.1))
                    .add(0, (1 - t * t) * 0.25, 0);
            level.sendParticles(BLOOD_BIG, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, sl.pos.x, sl.pos.y, sl.pos.z, 1, 0, 0, 0, 0);
    }

    // ---- Helpers ------------------------------------------------------------
    /** Draws straight slash lines at random positions, heights and angles across the domain. */
    private static void drawSlashStorm(Shrine s, int lines) {
        drawSlashStorm(s, lines, s.radius);
    }

    private static void drawSlashStorm(Shrine s, int lines, double radius) {
        ServerLevel level = s.level;
        var rng = level.random;
        var type = ModParticles.SLASH_LINE.get();

        for (int n = 0; n < lines; n++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * radius * 0.95;
            double px = s.center.x + Math.cos(a) * d;
            double pz = s.center.z + Math.sin(a) * d;
            double floor = groundY(level, px, s.center.y, pz);
            Vec3 mid = new Vec3(px, floor + 0.5 + rng.nextDouble() * 5.0, pz);

            // random direction: any azimuth, any elevation from straight down to straight up
            double az = rng.nextDouble() * Math.PI * 2;
            double el = (rng.nextDouble() - 0.5) * Math.PI;
            Vec3 dir = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));

            // mostly medium cuts, now and then a really long one
            double len = rng.nextFloat() < 0.15f ? 10 + rng.nextDouble() * 6 : 3 + rng.nextDouble() * 6;

            Vec3 start = mid.subtract(dir.scale(len / 2));
            Vec3 end = mid.add(dir.scale(len / 2));

            // keep the whole cut above the floor
            double lowest = Math.min(start.y, end.y);
            if (lowest < floor + 0.1) {
                Vec3 lift = new Vec3(0, floor + 0.1 - lowest, 0);
                start = start.add(lift);
                end = end.add(lift);
            }

            Vec3 seg = end.subtract(start);
            // count = 0: the three "velocity" numbers carry the line vector (see SlashLineParticle)
            level.sendParticles(type, start.x, start.y, start.z, 0, seg.x, seg.y, seg.z, 1.0);
        }
    }

    /** Spawns one flat ink puff resting on the terrain surface near (x, z). */
    private static void spawnInk(ServerLevel level, net.minecraft.core.particles.SimpleParticleType type,
                                 double x, double casterY, double z, double heightAboveGround,
                                 double vx, double vy, double vz) {
        level.sendParticles(type, x, groundY(level, x, casterY, z) + heightAboveGround, z, 0, vx, vy, vz, 1.0);
    }

    /** Finds the top of the floor under (x, z), searching down from just above the caster's feet. */
    private static double groundY(ServerLevel level, double x, double casterY, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(casterY) + 1, Mth.floor(z));
        if (!level.hasChunkAt(p)) return casterY;
        for (int i = 0; i < 10; i++) {
            var shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty()) return p.getY() + shape.max(Direction.Axis.Y);
            p.move(Direction.DOWN);
        }
        return casterY;
    }

    private static void applyBleeding(LivingEntity target, int spellLevel) {
        if (BLEEDING_ID == null || !ForgeRegistries.MOB_EFFECTS.containsKey(BLEEDING_ID)) return;
        MobEffect bleeding = ForgeRegistries.MOB_EFFECTS.getValue(BLEEDING_ID);
        if (bleeding == null) return;
        MobEffectInstance current = target.getEffect(bleeding);
        if (current != null && current.getDuration() > 40) return; // don't reset it every few ticks
        target.addEffect(new MobEffectInstance(bleeding, 100, Math.min(spellLevel / 2, 4)));
    }

    private static void bloodBurst(ServerLevel level, LivingEntity target, int count) {
        level.sendParticles(BLOOD, target.getX(), target.getY(0.5), target.getZ(), count, 0.35, 0.5, 0.35, 0);
    }

    /** The domain collapses in a burst of blood. */
    private static void endShrine(Shrine s) {
        ServerLevel level = s.level;
        double cx = s.center.x, cy = s.center.y, cz = s.center.z;
        level.sendParticles(BLOOD_BIG, cx, cy + 1.0, cz, 150, s.radius * 0.4, 1.5, s.radius * 0.4, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, cx, cy + 1.5, cz, 40, s.radius * 0.4, 1.5, s.radius * 0.4, 0);
        level.playSound(null, cx, cy, cz, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 2.0f, 0.6f);
    }
}
