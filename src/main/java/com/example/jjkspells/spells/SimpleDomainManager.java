package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Runs every active Simple Domain: follows the caster and keeps them (and allies in the circle) immune to Overload. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class SimpleDomainManager {

    private static class Domain {
        final ServerLevel level;
        final UUID casterId;
        final float radius;
        int age = 0;

        Domain(ServerLevel level, LivingEntity caster, float radius) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.radius = radius;
        }
    }

    private static final List<Domain> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, SimpleDomainSpell spell, int spellLevel) {
        // one per caster
        ACTIVE.removeIf(d -> d.casterId.equals(caster.getUUID()));
        Domain d = new Domain(level, caster, spell.getRadius(spellLevel));
        ACTIVE.add(d);

        caster.removeEffect(ModEffects.OVERLOAD.get());
        caster.addEffect(new MobEffectInstance(ModEffects.SIMPLE_DOMAIN.get(), SimpleDomainSpell.DURATION_TICKS, 0, false, false, true));

        Vec3 p = caster.position();
        level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 1.6f);
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(caster) < 64 * 64) {
                sp.displayClientMessage(Component.literal("Simple Domain").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
        FX.one(level, ModParticles.UV_GLOW.get(), caster.position().add(0, 1, 0), new Vec3(3.5, 0, 0));
        FX.one(level, ModParticles.SD_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(d.radius, 0, 0));
        FX.one(level, ModParticles.SD_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(d.radius * 0.6, 0, 0));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Domain> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Domain d = it.next();
            Entity e = d.level.getEntity(d.casterId);
            if (!(e instanceof LivingEntity c) || !c.isAlive() || d.age >= SimpleDomainSpell.DURATION_TICKS) {
                if (e instanceof LivingEntity c2 && d.age >= SimpleDomainSpell.DURATION_TICKS) {
                    Vec3 p = c2.position();
                    d.level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, 1.5f);
                    FX.one(d.level, ModParticles.SD_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(d.radius * 1.3, 0, 0));
                }
                it.remove();
                continue;
            }
            tick(d, c);
            d.age++;
        }
    }

    private static void tick(Domain d, LivingEntity c) {
        ServerLevel level = d.level;
        RandomSource rng = level.random;
        Vec3 p = c.position();
        float R = d.radius;

        c.removeEffect(ModEffects.OVERLOAD.get());

        // allies standing inside the circle are covered too
        if (d.age % 5 == 0) {
            AABB box = new AABB(p, p).inflate(R);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (e == c || !e.isAlliedTo(c)) continue;
                if (e.position().distanceTo(p) > R) continue;
                e.removeEffect(ModEffects.OVERLOAD.get());
                e.addEffect(new MobEffectInstance(ModEffects.SIMPLE_DOMAIN.get(), 12, 0, false, false, true));
            }
        }

        // the circle on the ground: a ring of lights that slowly turns
        double spin = d.age * 0.04;
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            Vec3 edge = new Vec3(p.x + Math.cos(a) * R, p.y + 0.06, p.z + Math.sin(a) * R);
            FX.one(level, ModParticles.SD_STAR.get(), edge, new Vec3(0, 0.03, 0));
        }
        int dots = 24;
        if (d.age % 4 == 0) {
            for (int i = 0; i < dots; i++) {
                double a = spin + i * Math.PI * 2 / dots;
                Vec3 edge = new Vec3(p.x + Math.cos(a) * R, p.y + 0.06, p.z + Math.sin(a) * R);
                FX.one(level, ModParticles.UV_GLOW.get(), edge, new Vec3(0.18, 0, 0));
            }
        }
        // a faint shimmering shell
        for (int i = 0; i < 4; i++) {
            Vec3 dir = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble(), rng.nextDouble() * 2 - 1).normalize();
            FX.one(level, ModParticles.SD_STAR.get(), p.add(dir.scale(R)), dir.scale(0.01));
        }
        if (d.age % 25 == 0) {
            FX.one(level, ModParticles.SD_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(R, 0, 0));
        }
    }
}
