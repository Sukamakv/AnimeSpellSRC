package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every active Decimate: wind-up (dust, red energy gathers at the axe), the 360 degree sweep (the crimson arc is
 * one big ribbon that draws itself around the circle while the damage follows the blade angle for angle), then recovery.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class DecimateManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final double TWO_PI = Math.PI * 2;
    private static final int SWEEP_START = DecimateSpell.WINDUP_TICKS;
    private static final int SWEEP_END = DecimateSpell.WINDUP_TICKS + DecimateSpell.SWEEP_TICKS;   // exclusive

    private static class Swing {
        final ServerLevel level;
        final UUID casterId;
        final DecimateSpell spell;
        final int spellLevel;
        final float radius;
        final float weaponDamage;          // read when the spell is cast, from the weapon in hand right then
        int age = 0;
        double baseAngle = 0;              // direction the blade starts the sweep from
        boolean healed = false;
        final Set<UUID> hit = new HashSet<>();

        Swing(ServerLevel level, LivingEntity caster, DecimateSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.radius = spell.getRadius(spellLevel);
            this.weaponDamage = MoonFX.weaponDamage(caster) * DariusFX.levelScale(spellLevel);
        }
    }

    private static final List<Swing> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, DecimateSpell spell, int spellLevel) {
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
                done = tick(s, caster);
            } catch (Exception ex) {
                LOGGER.error("Decimate failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Swing s, @Nullable LivingEntity caster) {
        if (caster == null) return true;
        ServerLevel level = s.level;
        int age = s.age;

        if (age < SWEEP_START) {
            windup(s, caster);
        } else if (age < SWEEP_END) {
            if (age == SWEEP_START) beginSweep(s, caster);
            sweep(s, caster, age);
        } else {
            recovery(s, caster);
        }
        return age >= DecimateSpell.TOTAL_TICKS - 1;
    }

    // =============================================================================================
    //  PHASES
    // =============================================================================================

    /** Axe pulled back, knees bent: dust kicks up and red energy gathers at the axe. */
    private static void windup(Swing s, LivingEntity caster) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        Vec3 feet = caster.position();
        if (s.age == 0) {
            MartialFX.sound(level, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
            MartialFX.sound(level, feet, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.7f);
        }
        for (int i = 0; i < 2; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            FX.one(level, ModParticles.DR_DUST.get(), feet.add(Math.cos(a) * 0.9, 0.12, Math.sin(a) * 0.9),
                    new Vec3(-Math.cos(a) * 0.03, 0.04, -Math.sin(a) * 0.03));
        }
        DariusFX.gather(level, DariusFX.handPos(caster), 1.6, 3);
    }

    /** The blade starts around: the arc, the ring, the sounds. */
    private static void beginSweep(Swing s, LivingEntity caster) {
        ServerLevel level = s.level;
        Vec3 look = caster.getLookAngle();
        s.baseAngle = Math.atan2(look.z, look.x);
        Vec3 c = caster.position().add(0, caster.getBbHeight() * 0.55, 0);
        Vec3 u = new Vec3(1, 0, 0), v = new Vec3(0, 0, 1);
        float r = s.radius;
        float a0 = (float) s.baseAngle, a1 = (float) (s.baseAngle + TWO_PI);

        DariusFX.ribbon(level, c, u, v, r * 0.80f, r * 0.80f, 0f, a0, a1, 1.3f, 15, com.example.jjkspells.particle.MoonRibbonOptions.RED);          // the broad crimson arc
        DariusFX.ribbon(level, c, u, v, r * 0.93f, r * 0.93f, 0f, a0, a1, 0.55f, 14, com.example.jjkspells.particle.MoonRibbonOptions.BRIGHT_RED); // the brighter outer edge
        DariusFX.ribbon(level, c, u, v, DecimateSpell.HANDLE_RADIUS * 0.75f, DecimateSpell.HANDLE_RADIUS * 0.75f, 0f, a0, a1, 0.8f, 12, com.example.jjkspells.particle.MoonRibbonOptions.STEEL); // the heavy inner part
        DariusFX.ring(level, caster.position(), r);

        MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.55f);
        MartialFX.sound(level, c, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.6f);
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t);
    }

    /** Hits everything the blade has swept past so far. */
    private static void sweep(Swing s, LivingEntity caster, int age) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        Vec3 pos = caster.position();
        Vec3 c = pos.add(0, caster.getBbHeight() * 0.55, 0);

        double progress = ease((age - SWEEP_START + 1) / (double) DecimateSpell.SWEEP_TICKS);
        double sweepAngle = progress * TWO_PI;

        // dust and sparks fly off the blade's tip
        double tip = s.baseAngle + sweepAngle;
        for (int i = 0; i < 2; i++) {
            double a = tip - i * 0.25;
            Vec3 p = new Vec3(pos.x + Math.cos(a) * s.radius * 0.85, pos.y + 0.2, pos.z + Math.sin(a) * s.radius * 0.85);
            FX.one(level, ModParticles.DR_SPARK.get(), p, new Vec3(-Math.sin(a) * 0.25, 0.1, Math.cos(a) * 0.25));
        }

        AABB box = new AABB(pos.x - s.radius - 1, pos.y - 1.5, pos.z - s.radius - 1, pos.x + s.radius + 1, pos.y + 3.5, pos.z + s.radius + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster) || s.hit.contains(e.getUUID())) continue;
            double dx = e.getX() - pos.x, dz = e.getZ() - pos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d - e.getBbWidth() * 0.5 > s.radius) continue;                            // out of reach
            if (e.getBoundingBox().minY > pos.y + 2.6 || e.getBoundingBox().maxY < pos.y - 0.5) continue;   // too high or low
            double rel = (Math.atan2(dz, dx) - s.baseAngle) % TWO_PI;
            if (rel < 0) rel += TWO_PI;
            if (rel > sweepAngle) continue;                                               // the blade has not reached them yet
            if (!caster.hasLineOfSight(e)) continue;
            s.hit.add(e.getUUID());

            boolean outer = d >= DecimateSpell.HANDLE_RADIUS;                              // blade, or the weaker handle
            float damage = s.weaponDamage * (outer ? DecimateSpell.OUTER_MULT : DecimateSpell.INNER_MULT);
            e.invulnerableTime = 0;
            if (!DamageSources.applyDamage(e, damage, s.spell.getDamageSource(caster))) continue;
            SpellDamage.wound(e);

            Vec3 hitPos = e.getBoundingBox().getCenter();
            if (outer) {
                DariusFX.redBurst(level, hitPos, 1.1f);
                MartialFX.sound(level, hitPos, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
                if (!s.healed) {
                    s.healed = true;
                    caster.heal(caster.getMaxHealth() * DecimateSpell.HEAL_FRACTION);
                    level.sendParticles(ParticleTypes.HEART, caster.getX(), caster.getY() + caster.getBbHeight() + 0.2, caster.getZ(), 4, 0.35, 0.25, 0.35, 0.02);
                }
            } else {
                DariusFX.metalBurst(level, hitPos, 1.0f);
                MartialFX.sound(level, hitPos, SoundEvents.ANVIL_HIT, 0.8f, 0.7f);
            }
        }
    }

    /** Darius recovers and settles back into his stance. */
    private static void recovery(Swing s, LivingEntity caster) {
        ServerLevel level = s.level;
        if (s.age % 2 == 0) {
            DariusFX.gather(level, DariusFX.handPos(caster), 1.0, 2);
        }
    }
}
