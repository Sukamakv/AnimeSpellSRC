package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs Oblivion's Descent.
 *
 *   WINDUP - rises and flies to hover above the marked spot (a burning circle on the ground below), wings fully spread;
 *            in the last few ticks the wings fold inward and red energy pours into the sword.
 *   PLUNGE - drives straight down onto the spot.
 *   IMPACT - the shockwave, the spikes, the towering burst, and the damage zone.
 *   EMBERS - black-red embers fall over the area for a few seconds afterwards.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class OblivionsDescentManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final double HOVER_HEIGHT = 10.0;
    private static final double FLY_SPEED = 1.3;
    private static final double PLUNGE_SPEED = 2.2;
    private static final int MAX_PLUNGE_TICKS = 14;
    private static final int FOLD_TICKS = 7;               // the wings fold during the last ticks of the wind-up
    private static final int EMBER_TICKS = 70;

    /** caster -> the spot chosen when the cast was checked (on the ground) */
    private static final Map<UUID, Vec3> SPOTS = new HashMap<>();

    public static void lock(LivingEntity caster, Vec3 groundSpot) {
        SPOTS.put(caster.getUUID(), groundSpot);
    }

    // ---- wind-up ------------------------------------------------------------------------------

    private static class Windup {
        final ServerLevel level;
        final UUID casterId;
        final Vec3 spot;
        int age = 0;

        Windup(ServerLevel level, UUID casterId, Vec3 spot) {
            this.level = level;
            this.casterId = casterId;
            this.spot = spot;
        }
    }

    private static final List<Windup> WINDUPS = new ArrayList<>();

    public static void beginWindup(ServerLevel level, LivingEntity caster) {
        Vec3 spot = SPOTS.get(caster.getUUID());
        if (spot == null) {                                       // a caster that skipped the pre-cast check (a mob): use the ground ahead
            Vec3 f = AatroxFX.forward(caster);
            Vec3 p = caster.position().add(f.scale(8));
            spot = new Vec3(p.x, MoonFX.groundAt(level, p.x, p.z, caster.getY()), p.z);
            SPOTS.put(caster.getUUID(), spot);
        }
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        WINDUPS.add(new Windup(level, caster.getUUID(), spot));
        MartialFX.sound(level, caster.position(), SoundEvents.RAVAGER_ROAR, 1.5f, 0.6f);
        MartialFX.sound(level, caster.position(), SoundEvents.WITHER_AMBIENT, 1.0f, 0.5f);
        AatroxFX.wings(level, caster, 0.4f, 1.0f, 6);
    }

    public static void cancelWindup(LivingEntity caster) {
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        SPOTS.remove(caster.getUUID());
        caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, false));   // a safe way down
    }

    // ---- plunge -------------------------------------------------------------------------------

    private static class Plunge {
        final ServerLevel level;
        final UUID casterId;
        final OblivionsDescentSpell spell;
        final int spellLevel;
        final Vec3 spot;
        int age = 0;

        Plunge(ServerLevel level, UUID casterId, OblivionsDescentSpell spell, int spellLevel, Vec3 spot) {
            this.level = level;
            this.casterId = casterId;
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.spot = spot;
        }
    }

    private static final List<Plunge> PLUNGES = new ArrayList<>();

    public static void startPlunge(ServerLevel level, LivingEntity caster, OblivionsDescentSpell spell, int spellLevel) {
        WINDUPS.removeIf(w -> w.casterId.equals(caster.getUUID()));
        Vec3 spot = SPOTS.remove(caster.getUUID());
        if (spot == null) return;
        PLUNGES.add(new Plunge(level, caster.getUUID(), spell, spellLevel, spot));
        MartialFX.sound(level, caster.position(), SoundEvents.PHANTOM_SWOOP, 1.6f, 0.4f);
    }

    // ---- embers -------------------------------------------------------------------------------

    private static class Embers {
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        int ticks = 0;

        Embers(ServerLevel level, Vec3 center, double radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
        }
    }

    private static final List<Embers> EMBERS = new ArrayList<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Iterator<Windup> it = WINDUPS.iterator(); it.hasNext(); ) {
            Windup w = it.next();
            Entity e = w.level.getEntity(w.casterId);
            if (!(e instanceof LivingEntity caster) || !caster.isAlive() || w.age > 60) {
                it.remove();
                continue;
            }
            windupTick(w, caster);
            w.age++;
        }

        for (Iterator<Plunge> it = PLUNGES.iterator(); it.hasNext(); ) {
            Plunge p = it.next();
            Entity e = p.level.getEntity(p.casterId);
            boolean done;
            try {
                done = !(e instanceof LivingEntity caster) || !caster.isAlive() || plungeTick(p, caster);
            } catch (Exception ex) {
                LOGGER.error("Oblivion's Descent failed, cancelling it", ex);
                done = true;
            }
            p.age++;
            if (done) it.remove();
        }

        for (Iterator<Embers> it = EMBERS.iterator(); it.hasNext(); ) {
            Embers m = it.next();
            AatroxFX.emberRain(m.level, m.center, m.radius, 14, 9);
            if (++m.ticks >= EMBER_TICKS) it.remove();
        }
    }

    // =============================================================================================
    //  WINDUP
    // =============================================================================================

    private static void windupTick(Windup w, LivingEntity caster) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        float size = AatroxFX.sizeScale(caster);
        float radius = OblivionsDescentSpell.RADIUS * size;

        // rise and fly to hover above the spot, then hold there
        Vec3 hover = new Vec3(w.spot.x, w.spot.y + HOVER_HEIGHT, w.spot.z);
        Vec3 to = hover.subtract(caster.position());
        double dist = to.length();
        Vec3 vel = dist < 0.4 ? Vec3.ZERO : to.scale(Math.min(FLY_SPEED, dist * 0.35 + 0.15) / dist);
        caster.setDeltaMovement(vel);
        caster.hurtMarked = true;
        caster.fallDistance = 0;

        // the wings stay fully spread, then fold inward at the end while energy floods the sword
        int total = OblivionsDescentSpell.WINDUP_TICKS;
        float spread = Mth.clamp((total - w.age) / (float) FOLD_TICKS, 0f, 1f);
        if (w.age % 2 == 0) AatroxFX.wings(level, caster, spread, size, 4);
        Vec3 blade = DariusFX.handPos(caster).add(0, 1.0, 0);
        DariusFX.gather(level, blade, 1.8, 3 + w.age / 4);
        if (w.age >= total - FOLD_TICKS) {
            FX.one(level, ModParticles.DR_GLOW.get(), blade, new Vec3(0.7 + (w.age - (total - FOLD_TICKS)) * 0.15, 0, 0));
        }

        // the circle burning on the ground where he will land
        if (w.age % 3 == 0) {
            FX.one(level, ModParticles.AT_CIRCLE.get(), w.spot.add(0, 0.06, 0), new Vec3(radius, 0, 0));
            FX.one(level, ModParticles.AT_CIRCLE.get(), w.spot.add(0, 0.07, 0), new Vec3(radius * 0.55, 0, 0));
        }
        for (int i = 0; i < 2; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            FX.one(level, ModParticles.DR_SPARK.get(), w.spot.add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius), new Vec3(0, 0.18, 0));
        }
        AatroxFX.smoke(level, caster.position().add(0, 0.5, 0), 1, 0.7);
        if (w.age == 18) MartialFX.sound(level, w.spot, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.7f);
    }

    // =============================================================================================
    //  PLUNGE
    // =============================================================================================

    /** @return true when the strike is over. */
    private static boolean plungeTick(Plunge p, LivingEntity caster) {
        ServerLevel level = p.level;
        caster.fallDistance = 0;
        Vec3 pos = caster.position();
        boolean landed = pos.y <= p.spot.y + 1.2 || caster.onGround() && p.age > 1 || p.age >= MAX_PLUNGE_TICKS;
        if (landed) {
            impact(p, caster);
            return true;
        }
        Vec3 to = new Vec3(p.spot.x - pos.x, p.spot.y + 0.3 - pos.y, p.spot.z - pos.z);
        double len = to.length();
        Vec3 v = len < 1.0E-4 ? new Vec3(0, -PLUNGE_SPEED, 0) : to.scale(Math.min(PLUNGE_SPEED, len) / len);
        caster.setDeltaMovement(v);
        caster.hurtMarked = true;
        // both hands on the hilt, the blade blazing: a long red streak above him
        FX.one(level, ModParticles.DR_LINE.get(), pos.add(0, 2.2, 0), new Vec3(0, 3.0, 0));
        FX.one(level, ModParticles.DR_GLOW.get(), pos.add(0, 2.4, 0), new Vec3(0.9, 0, 0));
        AatroxFX.smoke(level, pos.add(0, 1.0, 0), 2, 0.5);
        return false;
    }

    // =============================================================================================
    //  IMPACT
    // =============================================================================================

    private static void impact(Plunge p, LivingEntity caster) {
        ServerLevel level = p.level;
        RandomSource rng = level.random;
        float size = AatroxFX.sizeScale(caster);
        float radius = OblivionsDescentSpell.RADIUS * size;
        float scale = DariusFX.levelScale(p.spellLevel);
        Vec3 c = p.spot;
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        caster.fallDistance = 0;

        // ---- the shockwave
        DariusFX.ring(level, c, radius);
        FX.one(level, ModParticles.DR_SHOCK.get(), c.add(0, 0.8, 0), new Vec3(radius * 0.9, 0, 0));
        FX.one(level, ModParticles.DR_GLOW.get(), c.add(0, 1.0, 0), new Vec3(8.0 * size, 0, 0));
        FX.one(level, ModParticles.DR_HIT.get(), c.add(0, 1.2, 0), new Vec3(4.0 * size, 0, 0));
        FX.one(level, ModParticles.MA_CRACKS.get(), c.add(0, 0.05, 0), new Vec3(radius * 0.95, 0, 0));
        FX.one(level, ModParticles.AT_CIRCLE.get(), c.add(0, 0.08, 0), new Vec3(radius, 0, 0));

        // ---- jagged red spikes in three rings, all leaning outward
        int[] counts = {6, 10, 14};
        double[] ringR = {0.25, 0.5, 0.78};
        for (int k = 0; k < 3; k++) {
            double phase = rng.nextDouble() * Math.PI * 2;
            for (int i = 0; i < counts[k]; i++) {
                double a = phase + i * Math.PI * 2 / counts[k];
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 base = c.add(dir.scale(radius * ringR[k]));
                AatroxFX.spike(level, base, dir, (2.2f + rng.nextFloat() * 3.2f) * size, 0.5f + rng.nextFloat() * 0.25f, 12,
                        (i & 1) == 0 ? MoonRibbonOptions.BRIGHT_RED : MoonRibbonOptions.BLOOD);
            }
        }

        // ---- the towering burst of blood-red energy at the centre
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5 + rng.nextDouble() * 0.5;
            AatroxFX.spike(level, c.add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5), new Vec3(Math.cos(a), 0, Math.sin(a)),
                    (9f + rng.nextFloat() * 5f) * size, 1.5f, 16, i == 0 ? MoonRibbonOptions.BRIGHT_RED : MoonRibbonOptions.BLOOD);
        }
        AatroxFX.smoke(level, c.add(0, 1.0, 0), 24, 2.5);
        AatroxFX.blood(level, c.add(0, 1.0, 0), 34, 0.55);
        BlockState under = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (under.isAir()) under = Blocks.STONE.defaultBlockState();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, under);
        for (int i = 0; i < 45; i++) {
            double a = rng.nextDouble() * Math.PI * 2, r = rng.nextDouble() * radius * 0.8;
            FX.one(level, debris, c.add(Math.cos(a) * r, 0.2, Math.sin(a) * r),
                    new Vec3(Math.cos(a) * 0.12, 0.3 + rng.nextDouble() * 0.45, Math.sin(a) * 0.12));
        }
        EMBERS.add(new Embers(level, c, radius * 0.9));

        // ---- sound and shake
        MartialFX.sound(level, c, SoundEvents.GENERIC_EXPLODE, 2.0f, 0.55f);
        MartialFX.sound(level, c, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.8f);
        MartialFX.sound(level, c, SoundEvents.ANVIL_LAND, 1.5f, 0.4f);
        MartialFX.sound(level, c, SoundEvents.RAVAGER_ROAR, 1.5f, 0.5f);
        MartialFX.sound(level, c, SoundEvents.WITHER_BREAK_BLOCK, 1.0f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, under.getSoundType().getBreakSound(), SoundSource.BLOCKS, 2.0f, 0.4f);
        DariusFX.shake(level, c, 40, 12);

        // ---- the damage zone: everything inside the radius, thrown outward
        AABB box = new AABB(c.x - radius, c.y - 4, c.z - radius, c.x + radius, c.y + 8, c.z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d - e.getBbWidth() * 0.5 > radius) continue;
            if (!ViegoFX.strike(caster, p.spell, e, OblivionsDescentSpell.DAMAGE * scale, OblivionsDescentSpell.MAX_HEALTH_PERCENT)) continue;   // percent ignores damage caps
            Vec3 hp = e.getBoundingBox().getCenter();
            DariusFX.redBurst(level, hp, 1.5f);
            AatroxFX.blood(level, hp, 9, 0.3);
            if (e.isAlive()) {
                double strength = Mth.lerp(Math.min(1.0, d / radius), 1.9, 0.9);          // the closer, the harder
                MartialFX.shove(e, dx, dz, strength, 0.55);
            }
        }
    }
}
