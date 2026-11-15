package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.effect.StunEffect;
import com.example.jjkspells.particle.MoonRibbonOptions;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Runs every active Darkin Blade: wind-up (the sword drawn back, crimson energy streaming along it), the slam (fissure,
 * spikes, the strike zone resolved at that instant), and recovery.
 *
 * The strike zone is on the ground in front of Aatrox: a rectangle RANGE long and WIDTH wide, with a circle at its centre.
 * Whatever is inside is hit once; the circle is the more dangerous part.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class DarkinBladeManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static class Swing {
        final ServerLevel level;
        final UUID casterId;
        final DarkinBladeSpell spell;
        final int spellLevel;
        int age = 0;

        Swing(ServerLevel level, LivingEntity caster, DarkinBladeSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
        }
    }

    private static final List<Swing> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, DarkinBladeSpell spell, int spellLevel) {
        ACTIVE.add(new Swing(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Swing> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Swing s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done;
            try {
                done = caster == null || tick(s, caster);
            } catch (Exception ex) {
                LOGGER.error("The Darkin Blade failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Swing s, LivingEntity caster) {
        if (s.age < DarkinBladeSpell.WINDUP_TICKS) {
            windup(s, caster);
        } else if (s.age == DarkinBladeSpell.WINDUP_TICKS) {
            slam(s, caster);
        }
        return s.age >= DarkinBladeSpell.WINDUP_TICKS + DarkinBladeSpell.RECOVERY_TICKS;
    }

    /** The sword goes back over the shoulder and crimson energy gathers along the blade. */
    private static void windup(Swing s, LivingEntity caster) {
        ServerLevel level = s.level;
        if (s.age == 0) {
            MartialFX.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
            MartialFX.sound(level, caster.position(), SoundEvents.WITHER_AMBIENT, 0.7f, 0.5f);
        }
        Vec3 hand = DariusFX.handPos(caster);
        Vec3 blade = hand.add(0, 0.9, 0).subtract(AatroxFX.forward(caster).scale(0.5));   // up and back over the shoulder
        DariusFX.gather(level, blade, 1.5, 3 + s.age / 2);
        FX.one(level, ModParticles.DR_GLOW.get(), blade, new Vec3(0.5 + s.age * 0.06, 0, 0));
        AatroxFX.smoke(level, caster.position().add(0, 0.2, 0), 1, 0.6);
    }

    private static void slam(Swing s, LivingEntity caster) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        float size = AatroxFX.sizeScale(caster);
        float range = DarkinBladeSpell.RANGE * size;
        float halfWidth = DarkinBladeSpell.WIDTH * size * 0.5f;
        float scale = DariusFX.levelScale(s.spellLevel);

        // direction: where he faces, nudged toward an enemy that is nearly in line
        Vec3 look = caster.getLookAngle();
        Vec3 f = new Vec3(look.x, 0, look.z);
        f = f.lengthSqr() < 1.0E-4 ? AatroxFX.forward(caster) : f.normalize();
        f = aimAssist(level, caster, f, range);
        Vec3 side = new Vec3(-f.z, 0, f.x);

        // he steps into the slam
        caster.setDeltaMovement(caster.getDeltaMovement().add(f.scale(0.45)));
        caster.hurtMarked = true;

        Vec3 origin = caster.position();
        double gy = MoonFX.groundAt(level, origin.x, origin.z, origin.y);
        Vec3 ground = new Vec3(origin.x, gy, origin.z);
        double centerAlong = range * 0.6;
        float centerRadius = DarkinBladeSpell.CENTER_RADIUS * size;

        // ---- the strike: the sword over his head and down, then the fissure and spikes along the zone
        Vec3 chest = origin.add(0, caster.getBbHeight() * 0.6, 0);
        Vec3 up = new Vec3(0, 1, 0);
        DariusFX.ribbon(level, chest, f, up, 3.4f * size, 3.4f * size, 0f, 2.6f, 0.15f, 1.6f * size, 11, MoonRibbonOptions.BRIGHT_RED);
        DariusFX.ribbon(level, chest, f, up, 3.7f * size, 3.7f * size, 0.5f, 2.5f, 0.2f, 0.6f * size, 10, MoonRibbonOptions.BLOOD);
        AatroxFX.fissure(level, ground.add(f.scale(0.7)).add(0, 0.08, 0), f, range - 0.5f, 0.7f * size);
        for (int i = 1; i <= 6; i++) {
            Vec3 base = ground.add(f.scale(range * i / 7.0)).add(side.scale((rng.nextDouble() - 0.5) * 1.6));
            AatroxFX.spike(level, base, side.scale(rng.nextBoolean() ? 1 : -1).add(f.scale(0.4)), 1.6f + rng.nextFloat() * 1.4f, 0.45f, 11,
                    (i & 1) == 0 ? MoonRibbonOptions.BRIGHT_RED : MoonRibbonOptions.BLOOD);
        }
        Vec3 centre = ground.add(f.scale(centerAlong));
        FX.one(level, ModParticles.DR_GLOW.get(), centre.add(0, 0.5, 0), new Vec3(2.4 * size, 0, 0));
        FX.one(level, ModParticles.DR_RING_FLAT.get(), centre.add(0, 0.07, 0), new Vec3(centerRadius * 1.8, 0, 0));
        FX.one(level, ModParticles.MA_CRACKS.get(), centre.add(0, 0.05, 0), new Vec3(range * 0.55, 0, 0));
        BlockState under = level.getBlockState(BlockPos.containing(centre.x, gy - 0.5, centre.z));
        if (under.isAir()) under = Blocks.STONE.defaultBlockState();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, under);
        for (int i = 0; i < 20; i++) {
            Vec3 p = ground.add(f.scale(rng.nextDouble() * range)).add(side.scale((rng.nextDouble() - 0.5) * halfWidth * 2)).add(0, 0.2, 0);
            FX.one(level, debris, p, new Vec3((rng.nextDouble() - 0.5) * 0.15, 0.28 + rng.nextDouble() * 0.3, (rng.nextDouble() - 0.5) * 0.15));
        }
        AatroxFX.blood(level, centre.add(0, 0.5, 0), 10, 0.3);
        MartialFX.sound(level, centre, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
        MartialFX.sound(level, centre, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
        MartialFX.sound(level, centre, SoundEvents.IRON_GOLEM_DAMAGE, 1.0f, 0.5f);
        level.playSound(null, centre.x, centre.y, centre.z, under.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.4f, 0.5f);
        DariusFX.shake(level, centre, 18, 6);

        // ---- the damage: everything in the zone, more at the centre
        AABB box = new AABB(ground, ground.add(f.scale(range))).inflate(halfWidth + 1.5, 3.0, halfWidth + 1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            Vec3 rel = new Vec3(e.getX() - ground.x, 0, e.getZ() - ground.z);
            double along = rel.dot(f), across = rel.dot(side);
            double pad = e.getBbWidth() * 0.5;
            if (along < -0.6 || along > range + pad || Math.abs(across) > halfWidth + pad) continue;
            if (e.getBoundingBox().minY > ground.y + 2.5 || e.getBoundingBox().maxY < ground.y - 1.5) continue;

            boolean centreHit = Math.hypot(along - centerAlong, across) <= centerRadius + pad;
            float flat = (centreHit ? DarkinBladeSpell.CENTER_DAMAGE : DarkinBladeSpell.EDGE_DAMAGE) * scale;
            float pct = centreHit ? DarkinBladeSpell.CENTER_PERCENT : DarkinBladeSpell.EDGE_PERCENT;
            if (!ViegoFX.strike(caster, s.spell, e, flat, pct)) continue;   // the percent part ignores damage caps

            Vec3 p = e.getBoundingBox().getCenter();
            if (e.isAlive()) {
                if (centreHit) MartialFX.shove(e, f.x, f.z, 0.35, 1.15);          // launched upward
                else MartialFX.shove(e, f.x, f.z, 1.5, 0.3);                       // knocked back
                if (DariusFX.isImmovable(e)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                } else {
                    StunEffect.apply(e, 12);                                       // the brief stagger
                }
            }
            DariusFX.redBurst(level, p, centreHit ? 2.0f : 1.3f);
            AatroxFX.blood(level, p, centreHit ? 12 : 6, 0.3);
            MartialFX.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.55f);
        }
    }

    /** If an enemy is nearly in line (within about 20 degrees and in range), turn the strike toward it. */
    private static Vec3 aimAssist(ServerLevel level, LivingEntity caster, Vec3 f, float range) {
        double cosMax = Math.cos(Math.toRadians(20));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 pos = caster.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range + 1, 2, range + 1),
                t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            Vec3 to = new Vec3(e.getX() - pos.x, 0, e.getZ() - pos.z);
            double d = to.length();
            if (d < 0.5 || d > range) continue;
            if (to.dot(f) / d < cosMax) continue;
            if (d < bestDist) {
                best = e;
                bestDist = d;
            }
        }
        if (best == null) return f;
        Vec3 to = new Vec3(best.getX() - pos.x, 0, best.getZ() - pos.z);
        return to.normalize();
    }
}
