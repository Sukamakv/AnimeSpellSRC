package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * Runs every Sovereign's Domination: green tendrils join the target to Viego's chest, the target's soul (a translucent
 * green silhouette) is torn out and drawn into him, and when it arrives the curse, the heal and the power surge land.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class DominationManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int SOUL_START = 2;

    /** caster -> the target chosen when the cast was checked */
    private static final Map<UUID, UUID> LOCKS = new HashMap<>();

    public static void lock(LivingEntity caster, LivingEntity target) {
        LOCKS.put(caster.getUUID(), target.getUUID());
    }

    private static class Pull {
        final ServerLevel level;
        final UUID casterId;
        final UUID targetId;
        int age = 0;

        Pull(ServerLevel level, UUID casterId, UUID targetId) {
            this.level = level;
            this.casterId = casterId;
            this.targetId = targetId;
        }
    }

    private static final List<Pull> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster) {
        UUID targetId = LOCKS.remove(caster.getUUID());
        if (targetId == null) {
            // a caster that skipped the pre-cast check (a mob): pick a weakened target now
            LivingEntity t = GuillotineManager.pickTarget(level, caster, SovereignsDominationSpell.RANGE, ViegoFX::hasWeaknessII);
            if (t == null) return;
            targetId = t.getUUID();
        }
        ACTIVE.add(new Pull(level, caster.getUUID(), targetId));
        MartialFX.sound(level, caster.position(), SoundEvents.SOUL_ESCAPE, 1.5f, 0.6f);
        MartialFX.sound(level, caster.position(), SoundEvents.CHAIN_PLACE, 1.0f, 0.5f);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Pull> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Pull p = it.next();
            Entity ce = p.level.getEntity(p.casterId);
            Entity te = p.level.getEntity(p.targetId);
            LivingEntity caster = (ce instanceof LivingEntity c && c.isAlive()) ? c : null;
            LivingEntity target = (te instanceof LivingEntity t && t.isAlive()) ? t : null;
            boolean done;
            try {
                done = caster == null || target == null || tick(p, caster, target);
            } catch (Exception ex) {
                LOGGER.error("Sovereign's Domination failed, cancelling it", ex);
                done = true;
            }
            p.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Pull p, LivingEntity caster, LivingEntity target) {
        ServerLevel level = p.level;
        Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.7, 0);
        Vec3 core = target.getBoundingBox().getCenter();
        int total = SovereignsDominationSpell.PULL_TICKS;

        chains(level, chest, core, p.age);

        // the soul: a translucent green silhouette that leaves the body and is drawn into his chest
        if (p.age >= SOUL_START && p.age < total - 1) {
            double t = (p.age - SOUL_START) / (double) (total - 1 - SOUL_START);
            double e = t * t * (3 - 2 * t);
            Vec3 pos = core.add(chest.subtract(core).scale(e));
            double size = Mth.lerp(t, 1.0, 0.5);
            FX.one(level, ModParticles.VG_SOUL.get(), pos, new Vec3(size, 0, 0));
            ViegoFX.mist(level, pos, 1, 0.3);
            FX.one(level, ModParticles.VG_SPARK.get(), pos, MartialFX.randomDir(level.random).scale(0.08));
        }
        if (p.age < 3) {
            ViegoFX.gather(level, core, 1.2, 4);
        }

        if (p.age == total - 1) {
            absorb(p, caster, target, chest);
            return true;
        }
        return false;
    }

    /** Two wavering green strands from the target to Viego's chest. */
    private static void chains(ServerLevel level, Vec3 chest, Vec3 core, int age) {
        Vec3 chord = chest.subtract(core);
        double len = chord.length();
        if (len < 0.5) return;
        Vec3 dir = chord.scale(1.0 / len);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        int segs = Math.max(4, (int) (len / 1.2));
        for (int strand = 0; strand < 2; strand++) {
            Vec3 prev = core;
            for (int i = 1; i <= segs; i++) {
                double t = i / (double) segs;
                double amp = Math.sin(Math.PI * t) * 0.45;
                double ph = age * 0.7 + strand * Math.PI + t * 7;
                Vec3 pt = core.add(chord.scale(t)).add(side.scale(Math.sin(ph) * amp)).add(up.scale(Math.cos(ph) * amp));
                FX.one(level, ModParticles.VG_TENDRIL.get(), prev, pt.subtract(prev));
                prev = pt;
            }
        }
    }

    /** The soul arrives: the curse on the target, the heal and the power surge on Viego. */
    private static void absorb(Pull p, LivingEntity caster, LivingEntity target, Vec3 chest) {
        ServerLevel level = p.level;
        target.addEffect(new MobEffectInstance(ModEffects.SOUL_TORN.get(), SovereignsDominationSpell.CURSE_TICKS, 0, false, true, true));
        caster.heal(caster.getMaxHealth() * SovereignsDominationSpell.HEAL_FRACTION);
        caster.addEffect(new MobEffectInstance(ModEffects.SOVEREIGN_POWER.get(), SovereignsDominationSpell.POWER_TICKS, 0, false, true, true));

        ViegoFX.burst(level, chest, 1.8f);
        ViegoFX.ring(level, caster.position(), 3.5f);
        ViegoFX.flames(level, caster.position().add(0, 0.2, 0), 14, 0.7);
        ViegoFX.burst(level, target.getBoundingBox().getCenter(), 1.0f);       // the hollowed-out body flinches
        MartialFX.sound(level, chest, SoundEvents.SOUL_ESCAPE, 1.6f, 0.9f);
        MartialFX.sound(level, chest, SoundEvents.WITHER_SPAWN, 0.5f, 1.8f);
        MartialFX.sound(level, target.position(), SoundEvents.SOUL_ESCAPE, 1.2f, 0.5f);
    }
}
