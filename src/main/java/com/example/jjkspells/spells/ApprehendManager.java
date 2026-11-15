package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.particle.MoonRibbonOptions;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Runs every active Apprehend.
 *
 *   WINDUP - feet planted, red energy pulled in behind Darius.
 *   SWING  - the dark-red crescent sweeps out; everything in the arc is hit, outlined and hooked.
 *   PULL   - hooked enemies are dragged toward Darius (a red chain joins them to the axe) until they have come up to
 *            6 blocks closer or are in striking distance, then slowed and a burst of dust marks the landing.
 *   FINISH - Darius lifts the axe into a threatening stance.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class ApprehendManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TARGETS = 10;
    private static final double PULL_SPEED = 1.1;      // blocks per tick
    private static final int MAX_PULL_TICKS = 12;
    private static final float CRESCENT_TILT = 0.5f;     // radians

    private static class Pull {
        final UUID id;
        final double startDist;     // horizontal distance to Darius when hooked
        final double stopDist;      // where the pull ends
        int ticks = 0;

        Pull(UUID id, double startDist, double stopDist) {
            this.id = id;
            this.startDist = startDist;
            this.stopDist = stopDist;
        }
    }

    private static class Cast {
        final ServerLevel level;
        final UUID casterId;
        final ApprehendSpell spell;
        final int spellLevel;
        int age = 0;
        Vec3 facing = new Vec3(0, 0, 1);
        final List<Pull> pulls = new ArrayList<>();

        Cast(ServerLevel level, LivingEntity caster, ApprehendSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
        }
    }

    private static final List<Cast> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, ApprehendSpell spell, int spellLevel) {
        ACTIVE.add(new Cast(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Cast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cast c = it.next();
            Entity e = c.level.getEntity(c.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done;
            try {
                done = tick(c, caster);
            } catch (Exception ex) {
                LOGGER.error("Apprehend failed, cancelling it", ex);
                done = true;
            }
            c.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Cast c, @Nullable LivingEntity caster) {
        if (caster == null) return true;
        ServerLevel level = c.level;
        int age = c.age;

        if (age < ApprehendSpell.WINDUP_TICKS) {
            windup(c, caster);
        } else if (age == ApprehendSpell.WINDUP_TICKS) {
            swing(c, caster);
        } else if (age <= ApprehendSpell.WINDUP_TICKS + 5) {
            crescentWave(c, caster, age - ApprehendSpell.WINDUP_TICKS);
        }

        // drag whoever is hooked
        Iterator<Pull> pi = c.pulls.iterator();
        while (pi.hasNext()) {
            if (pullStep(c, pi.next(), caster)) pi.remove();
        }

        if (age == ApprehendSpell.WINDUP_TICKS + ApprehendSpell.FINISH_TICKS) {
            // threatening stance
            DariusFX.gather(level, DariusFX.handPos(caster), 1.4, 6);
            MartialFX.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 0.6f);
        }
        return age >= ApprehendSpell.WINDUP_TICKS + ApprehendSpell.FINISH_TICKS && c.pulls.isEmpty();
    }

    // =============================================================================================
    //  PHASES
    // =============================================================================================

    private static void windup(Cast c, LivingEntity caster) {
        ServerLevel level = c.level;
        if (c.age == 0) {
            MartialFX.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.6f);
            MartialFX.sound(level, caster.position(), SoundEvents.CHAIN_PLACE, 1.0f, 0.7f);
        }
        // red energy collects behind him, where the axe is drawn back
        Vec3 behind = caster.position().add(0, caster.getBbHeight() * 0.7, 0)
                .subtract(horizontalFacing(caster).scale(0.9)).add(MartialFX.rightOf(caster).scale(0.4));
        DariusFX.gather(level, behind, 1.5, 3);
        Vec3 feet = caster.position();
        FX.one(level, ModParticles.DR_DUST.get(), feet.add(level.random.nextDouble() - 0.5, 0.12, level.random.nextDouble() - 0.5), new Vec3(0, 0.03, 0));
    }

    private static Vec3 horizontalFacing(LivingEntity caster) {
        float yaw = caster.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** The swing: find everything in the arc, hit it, outline it, hook it. */
    private static void swing(Cast c, LivingEntity caster) {
        ServerLevel level = c.level;
        Vec3 f = horizontalFacing(caster);
        c.facing = f;
        Vec3 pos = caster.position();
        MartialFX.sound(level, pos, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.55f);
        MartialFX.sound(level, pos, SoundEvents.CHAIN_BREAK, 1.0f, 0.8f);
        DariusFX.metalBurst(level, DariusFX.handPos(caster), 0.8f);

        double cosHalf = Math.cos(Math.toRadians(ApprehendSpell.HALF_ARC_DEGREES));
        float range = ApprehendSpell.RANGE;
        AABB box = new AABB(pos.x - range - 1, pos.y - 3, pos.z - range - 1, pos.x + range + 1, pos.y + 4, pos.z + range + 1);
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            double dx = e.getX() - pos.x, dz = e.getZ() - pos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 0.3 || d - e.getBbWidth() * 0.5 > range) continue;
            if ((dx * f.x + dz * f.z) / d < cosHalf) continue;          // outside the arc
            if (!caster.hasLineOfSight(e)) continue;
            targets.add(e);
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));

        float damage = ApprehendSpell.DAMAGE * DariusFX.levelScale(c.spellLevel);
        int count = 0;
        for (LivingEntity e : targets) {
            if (count >= MAX_TARGETS) break;
            e.invulnerableTime = 0;
            if (!DamageSources.applyDamage(e, damage, c.spell.getDamageSource(caster))) continue;
            SpellDamage.wound(e);
            count++;
            DariusFX.redBurst(level, e.getBoundingBox().getCenter(), 1.0f);
            if (!e.isAlive()) continue;

            DariusMarkManager.mark(level, e, 14, DariusMarkManager.OUTLINE);   // red outline
            if (DariusFX.isImmovable(e)) {
                // bosses and anything that will not budge: a stagger and a slow instead of the pull
                Vec3 away = e.position().subtract(pos);
                MartialFX.shove(e, away.x, away.z, 0.25, 0.05);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ApprehendSpell.SLOW_TICKS, ApprehendSpell.SLOW_AMPLIFIER));
                DariusFX.metalBurst(level, e.getBoundingBox().getCenter(), 1.2f);
            } else {
                double d = Math.sqrt(Math.pow(e.getX() - pos.x, 2) + Math.pow(e.getZ() - pos.z, 2));
                double stop = Math.max(ApprehendSpell.MIN_STOP_DISTANCE, d - ApprehendSpell.PULL_DISTANCE);
                c.pulls.add(new Pull(e.getUUID(), d, stop));
            }
        }
    }

    /** The crescent: arcs of dark red sweeping outward, one tick after another. */
    private static void crescentWave(Cast c, LivingEntity caster, int step) {
        ServerLevel level = c.level;
        Vec3 f = c.facing;
        // the crescent is tilted into a rising diagonal so it reads clearly from eye height (a flat one is edge-on)
        Vec3 side = new Vec3(-f.z, 0, f.x);
        Vec3 v = side.scale(Mth.cos(CRESCENT_TILT)).add(0, Mth.sin(CRESCENT_TILT), 0);
        Vec3 centre = caster.position().add(0, caster.getBbHeight() * 0.45, 0);
        float half = (float) Math.toRadians(ApprehendSpell.HALF_ARC_DEGREES);
        float r = 2.2f + step * (ApprehendSpell.RANGE - 3f) / 5f;
        DariusFX.ribbon(level, centre, f, v, r, r + 0.8f, 0f, -half, half, 1.1f, 7, MoonRibbonOptions.DARK_RED);
        DariusFX.ribbon(level, centre, f, v, r + 0.3f, r + 1.1f, 0f, -half * 0.9f, half * 0.9f, 0.45f, 6, MoonRibbonOptions.RED);
        // dust along the ground where the crescent passes
        for (int i = -2; i <= 2; i++) {
            double a = i * half / 2.4;
            Vec3 p = caster.position().add(f.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r)).add(0, 0.15, 0);
            FX.one(level, ModParticles.DR_DUST.get(), p, new Vec3(f.x * 0.05, 0.04, f.z * 0.05));
        }
    }

    // =============================================================================================
    //  THE PULL
    // =============================================================================================

    /** @return true when this enemy's pull is over. */
    private static boolean pullStep(Cast c, Pull p, LivingEntity caster) {
        ServerLevel level = c.level;
        Entity en = level.getEntity(p.id);
        if (!(en instanceof LivingEntity e) || !e.isAlive()) return true;

        Vec3 cp = caster.position();
        Vec3 to = new Vec3(cp.x - e.getX(), 0, cp.z - e.getZ());
        double dist = to.length();
        double moved = p.startDist - dist;
        p.ticks++;
        if (dist <= p.stopDist + 0.35 || moved >= ApprehendSpell.PULL_DISTANCE || p.ticks > MAX_PULL_TICKS) {
            land(c, e);
            return true;
        }

        Vec3 dir = to.scale(1.0 / Math.max(dist, 1.0E-4));
        double speed = Mth.clamp(Math.min(dist - p.stopDist, ApprehendSpell.PULL_DISTANCE - moved), 0.2, PULL_SPEED);
        double vy;
        if (e.isNoGravity()) vy = (caster.getY() + caster.getBbHeight() * 0.4 - e.getY()) * 0.15;   // flyers are reeled in level
        else vy = e.onGround() ? 0.14 : Math.min(0, e.getDeltaMovement().y);                         // a tiny hop keeps them sliding
        e.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        e.hurtMarked = true;
        e.fallDistance = 0;
        OverloadEvents.forceMove(e);

        // the chain from the axe to the enemy
        Vec3 from = DariusFX.handPos(caster);
        Vec3 target = e.getBoundingBox().getCenter();
        FX.one(level, ModParticles.DR_CHAIN.get(), from, target.subtract(from));
        return false;
    }

    /** The enemy arrives: dust, a thud, and the slow starts now. */
    private static void land(Cast c, LivingEntity e) {
        ServerLevel level = c.level;
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(0, v.y, 0);
        e.hurtMarked = true;
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ApprehendSpell.SLOW_TICKS, ApprehendSpell.SLOW_AMPLIFIER));
        DariusFX.ring(level, e.position(), 1.8f);
        MartialFX.sound(level, e.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
        MartialFX.sound(level, e.position(), SoundEvents.GRAVEL_BREAK, 1.0f, 0.6f);
    }
}
