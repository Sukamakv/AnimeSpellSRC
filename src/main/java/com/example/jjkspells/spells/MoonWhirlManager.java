package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every active Moon Whirl.
 *
 * Every tick a new big purple slash (a spiralling arc, tilted differently each time and offset around the caster
 * by the golden angle, so together they form a vortex) is thrown out with small blue crescents along it.
 * Every CUT_INTERVAL ticks everything in the whirl is cut once; the last cut also throws enemies away.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class MoonWhirlManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final double GOLDEN_ANGLE = 2.399963;

    private static class Whirl {
        final ServerLevel level;
        final UUID casterId;
        final MoonWhirlSpell spell;
        final int spellLevel;
        final float radius;
        final float cutDamage;                 // fixed when the spell is cast, from the sword in hand right then
        final double phase;
        int age = 0;
        final Map<UUID, Integer> lastCut = new HashMap<>();

        Whirl(ServerLevel level, LivingEntity caster, MoonWhirlSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.radius = spell.getRadius(spellLevel);
            this.cutDamage = MoonFX.weaponDamage(caster) * spell.getDamageMultiplier(spellLevel);
            this.phase = level.random.nextDouble() * Math.PI * 2;
        }
    }

    private static final List<Whirl> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, MoonWhirlSpell spell, int spellLevel) {
        ACTIVE.add(new Whirl(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Whirl> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Whirl w = it.next();
            Entity e = w.level.getEntity(w.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done;
            try {
                done = tick(w, caster);
            } catch (Exception ex) {
                LOGGER.error("Moon Whirl failed, cancelling it", ex);
                done = true;
            }
            w.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Whirl w, @Nullable LivingEntity caster) {
        if (caster == null) return true;
        ServerLevel level = w.level;
        int age = w.age;
        Vec3 c = caster.position().add(0, caster.getBbHeight() * 0.55, 0);

        if (age == 0) {
            MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
            MartialFX.sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0f, 0.9f);
        }
        if (age == 4) groundScar(w, caster);

        if (age < MoonWhirlSpell.DURATION - 4) throwBlade(w, c, age);   // a new slash every tick: about ten are alive at once
        if (age % 3 == 0) {
            MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f + level.random.nextFloat() * 0.5f);
        }
        ambient(w, caster);

        boolean last = age == MoonWhirlSpell.DURATION - 1;
        if (age % MoonWhirlSpell.CUT_INTERVAL == MoonWhirlSpell.CUT_INTERVAL - 1 || last) {
            cut(w, caster, c, last);
        }
        if (last) finale(w, c);
        return age >= MoonWhirlSpell.DURATION - 1;
    }

    // =============================================================================================
    //  VISUALS
    // =============================================================================================

    /** One big spiralling slash plus its crescents. */
    private static void throwBlade(Whirl w, Vec3 c, int k) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        double phi = k * GOLDEN_ANGLE + w.phase;
        float tilt = (float) (Math.sin(k * 0.83 + w.phase) * 0.65);
        Vec3 u = new Vec3(Math.cos(phi), 0, Math.sin(phi));
        Vec3 flatV = new Vec3(-Math.sin(phi), 0, Math.cos(phi));
        Vec3 v = flatV.scale(Mth.cos(tilt)).add(0, Mth.sin(tilt), 0);

        float r0 = 1.6f + rng.nextFloat() * 1.0f;
        float r1 = Math.min(w.radius, r0 + 3.2f + rng.nextFloat() * 2.8f);
        float lift = (rng.nextFloat() - 0.5f) * 2.2f;
        float span = 1.3f + rng.nextFloat() * 0.4f;
        float width = 0.7f + rng.nextFloat() * 0.5f;
        Vec3 center = c.add(0, (rng.nextDouble() - 0.5) * 1.1, 0);

        MoonFX.ribbon(level, center, u, v, r0, r1, lift, -span, span, width, 10);
        if (rng.nextInt(3) == 0) {   // some blades come with a thin echo, like the layered streaks in the art
            MoonFX.ribbon(level, center, u, v, r0 * 1.12f, r1 * 1.1f, lift * 0.8f, -span * 0.85f, span, width * 0.4f, 8);
        }
        MoonFX.crescentsAlong(level, center, u, v, r0, r1, lift, -span, span, 4 + rng.nextInt(2), 0.4f, 0.95f, 0.8f, u.scale(0.07));
    }

    /** Dust and a few stray crescents between blades. */
    private static void ambient(Whirl w, LivingEntity caster) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        Vec3 feet = caster.position();
        for (int i = 0; i < 2; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double rad = 1.5 + rng.nextDouble() * (w.radius - 1.5);
            Vec3 p = new Vec3(feet.x + Math.cos(ang) * rad, feet.y + 0.15, feet.z + Math.sin(ang) * rad);
            FX.one(level, ModParticles.MA_DUST.get(), p, new Vec3(-Math.sin(ang) * 0.1, 0.05, Math.cos(ang) * 0.1));
        }
    }

    /** The floor is torn up around the caster, as in the art. */
    private static void groundScar(Whirl w, LivingEntity caster) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        Vec3 feet = caster.position();
        double gy = MoonFX.groundAt(level, feet.x, feet.z, feet.y);
        Vec3 c = new Vec3(feet.x, gy, feet.z);
        FX.one(level, ModParticles.MA_CRACKS.get(), c.add(0, 0.04, 0), new Vec3(w.radius * 0.95, 0, 0));
        FX.one(level, ModParticles.MOON_BURST.get(), c.add(0, 0.06, 0), new Vec3(w.radius * 0.45, 0, 0));
        BlockState under = level.getBlockState(net.minecraft.core.BlockPos.containing(feet.x, gy - 0.5, feet.z));
        if (under.isAir()) under = Blocks.STONE.defaultBlockState();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, under);
        for (int i = 0; i < 28; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double rad = 1.0 + rng.nextDouble() * (w.radius - 1.0);
            Vec3 p = new Vec3(c.x + Math.cos(ang) * rad, gy + 0.2, c.z + Math.sin(ang) * rad);
            FX.one(level, debris, p, new Vec3(Math.cos(ang) * 0.08, 0.25 + rng.nextDouble() * 0.3, Math.sin(ang) * 0.08));
        }
    }

    private static void finale(Whirl w, Vec3 c) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        FX.one(level, ModParticles.MOON_FLASH.get(), c, new Vec3(3.2, 0, 0));
        FX.one(level, ModParticles.MA_SHOCK_RING.get(), c, new Vec3(w.radius, 0, 0));
        for (int i = 0; i < 14; i++) {
            FX.one(level, ModParticles.MOON_SPARK.get(), c, MartialFX.randomDir(rng).scale(0.45));
        }
        MartialFX.sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.3f, 0.7f);
        MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
    }

    // =============================================================================================
    //  DAMAGE
    // =============================================================================================

    private static void cut(Whirl w, LivingEntity caster, Vec3 c, boolean last) {
        ServerLevel level = w.level;
        AABB box = new AABB(c.x - w.radius, c.y - 2.5, c.z - w.radius, c.x + w.radius, c.y + 3.0, c.z + w.radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() * 0.5 > w.radius) continue;
            if (!caster.hasLineOfSight(e)) continue;
            if (!MoonFX.ready(w.lastCut, e.getUUID(), w.age, 2)) continue;

            e.invulnerableTime = 0;
            if (!DamageSources.applyDamage(e, w.cutDamage, w.spell.getDamageSource(caster))) continue;
            if (last) {
                MartialFX.shove(e, dx, dz, 1.0, 0.5);        // the last cut throws them out of the whirl
            } else {
                MartialFX.shove(e, dx, dz, 0.15, 0.2);       // earlier cuts keep them bobbing inside it
            }
            MoonFX.hit(level, e.getBoundingBox().getCenter(), 0.8f);
        }
    }
}
