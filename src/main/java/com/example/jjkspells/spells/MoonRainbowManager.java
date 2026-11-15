package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
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
 * Runs every active Moon Rainbow.
 *
 * When the spell finishes casting, the slash sites are chosen all around the caster (evenly spread, a golden-angle
 * spiral), and each one is scheduled: a few ticks before it erupts the ground cracks and rumbles, then a tall curved
 * purple slash with blue crescents bursts out of a spiky star-shaped crack, throwing up rubble and cutting upward
 * through anything near it. Everything stays visual on the ground; no blocks are changed.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class MoonRainbowManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final double GOLDEN_ANGLE = 2.399963;
    private static final int SPREAD_TICKS = 22;     // all slashes have erupted this many ticks after the first one
    private static final int FIRST_TICK = 6;        // the first slash erupts this many ticks after the cast
    private static final int HIT_GAP = 8;           // a target can be cut by the slashes at most once per this many ticks

    private static class Slash {
        Vec3 base;
        Vec3 lean;            // horizontal unit vector the slash bends toward (away from the caster)
        float r;              // radius of the curve
        float span;           // how many radians of the circle it covers (bigger = leans more)
        float lift;           // sideways bend
        float width;
        int fireTick;
        boolean telegraphed, fired;
        BlockState ground = Blocks.STONE.defaultBlockState();

        Vec3 center() { return base.add(lean.scale(r)); }
        Vec3 point(float p) { return MoonFX.curvePoint(center(), lean, new Vec3(0, 1, 0), r, r, lift, (float) Math.PI, (float) Math.PI - span, p); }
    }

    private static class Rainbow {
        final ServerLevel level;
        final UUID casterId;
        final MoonRainbowSpell spell;
        final float damage;                      // per slash, fixed when the spell is cast from the sword in hand right then
        final List<Slash> slashes = new ArrayList<>();
        final Map<UUID, Integer> lastHit = new HashMap<>();
        int age = 0;
        int lastFire = 0;

        Rainbow(ServerLevel level, LivingEntity caster, MoonRainbowSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.damage = MoonFX.weaponDamage(caster) * spell.getDamageMultiplier(spellLevel);
        }
    }

    private static final List<Rainbow> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, MoonRainbowSpell spell, int spellLevel) {
        Rainbow rb = new Rainbow(level, caster, spell, spellLevel);
        RandomSource rng = level.random;
        int n = spell.getSlashCount(spellLevel);
        float radius = spell.getRadius(spellLevel);
        Vec3 pos = caster.position();
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double ang = phase + i * GOLDEN_ANGLE + (rng.nextDouble() - 0.5) * 0.35;
            double rad = Mth.lerp(Math.sqrt((i + 0.5) / n), 2.2, radius) + (rng.nextDouble() - 0.5) * 0.8;
            double x = pos.x + Math.cos(ang) * rad, z = pos.z + Math.sin(ang) * rad;

            Slash s = new Slash();
            s.base = new Vec3(x, MoonFX.groundAt(level, x, z, pos.y), z);
            double leanAng = ang + (rng.nextDouble() - 0.5) * 0.9;   // leans outward, a little off true
            s.lean = new Vec3(Math.cos(leanAng), 0, Math.sin(leanAng));
            s.span = 0.85f + rng.nextFloat() * 0.3f;
            float height = 4.2f + rng.nextFloat() * 3.0f;            // how tall it rises
            s.r = height / Mth.sin(s.span);
            s.lift = (rng.nextFloat() - 0.5f) * 2.4f;
            s.width = 0.6f + rng.nextFloat() * 0.35f;
            s.fireTick = FIRST_TICK + (int) (i * (double) SPREAD_TICKS / n + rng.nextDouble() * 1.5);
            BlockState under = level.getBlockState(BlockPos.containing(x, s.base.y - 0.5, z));
            if (!under.isAir()) s.ground = under;
            rb.slashes.add(s);
            rb.lastFire = Math.max(rb.lastFire, s.fireTick);
        }
        ACTIVE.add(rb);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Rainbow> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Rainbow rb = it.next();
            Entity e = rb.level.getEntity(rb.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            boolean done;
            try {
                done = tick(rb, caster);
            } catch (Exception ex) {
                LOGGER.error("Moon Rainbow failed, cancelling it", ex);
                done = true;
            }
            rb.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Rainbow rb, @Nullable LivingEntity caster) {
        if (caster == null) return true;     // the caster died or left: the slashes stop
        if (rb.age == 0) {
            MartialFX.sound(rb.level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        }
        for (Slash s : rb.slashes) {
            if (!s.telegraphed && rb.age >= s.fireTick - MoonRainbowSpell.TELEGRAPH_TICKS) {
                s.telegraphed = true;
                telegraph(rb, s);
            }
            if (!s.fired && rb.age >= s.fireTick) {
                s.fired = true;
                erupt(rb, caster, s);
            }
        }
        return rb.age > rb.lastFire + 2;
    }

    // =============================================================================================
    //  EFFECTS
    // =============================================================================================

    /** The ground starts to crack where a slash is about to come up. */
    private static void telegraph(Rainbow rb, Slash s) {
        ServerLevel level = rb.level;
        RandomSource rng = level.random;
        Vec3 b = s.base;
        FX.one(level, ModParticles.MA_CRACKS.get(), b.add(0, 0.04, 0), new Vec3(1.6, 0, 0));
        for (int i = 0; i < 3; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            FX.one(level, ModParticles.MA_DUST.get(), b.add(Math.cos(ang) * 0.6, 0.15, Math.sin(ang) * 0.6),
                    new Vec3(Math.cos(ang) * 0.04, 0.05, Math.sin(ang) * 0.04));
        }
        level.playSound(null, b.x, b.y, b.z, s.ground.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.6f, 0.6f);
    }

    /** The slash bursts out of the ground. */
    private static void erupt(Rainbow rb, LivingEntity caster, Slash s) {
        ServerLevel level = rb.level;
        RandomSource rng = level.random;
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 center = s.center();
        float th0 = (float) Math.PI, th1 = (float) Math.PI - s.span;
        Vec3 b = s.base;

        // ---- the slash, a slimmer echo, and the crescents wrapped around it
        MoonFX.ribbon(level, center, s.lean, up, s.r, s.r, s.lift, th0, th1, s.width, 14);
        MoonFX.ribbon(level, center, s.lean, up, s.r * 1.03f, s.r * 1.03f, -s.lift * 0.6f, th0, th1 - s.span * 0.08f, s.width * 0.4f, 12);
        MoonFX.crescentsAlong(level, center, s.lean, up, s.r, s.r, s.lift, th0, th1, 5 + rng.nextInt(3), 0.5f, 1.1f, 0.9f,
                new Vec3(s.lean.x * 0.03, 0.1, s.lean.z * 0.03));

        // ---- the ground: spiky star, cracks, flash, rubble
        FX.one(level, ModParticles.MOON_BURST.get(), b.add(0, 0.06, 0), new Vec3(2.4, 0, 0));
        FX.one(level, ModParticles.MA_CRACKS.get(), b.add(0, 0.05, 0), new Vec3(2.9, 0, 0));
        FX.one(level, ModParticles.MOON_FLASH.get(), b.add(0, 0.6, 0), new Vec3(1.8, 0, 0));
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, s.ground);
        for (int i = 0; i < 9; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            FX.one(level, debris, b.add(Math.cos(ang) * 0.5, 0.2, Math.sin(ang) * 0.5),
                    new Vec3(Math.cos(ang) * 0.12, 0.3 + rng.nextDouble() * 0.3, Math.sin(ang) * 0.12));
        }
        for (int i = 0; i < 3; i++) {
            FX.one(level, ModParticles.MA_DUST.get(), b.add(0, 0.2, 0),
                    new Vec3((rng.nextDouble() - 0.5) * 0.2, 0.06, (rng.nextDouble() - 0.5) * 0.2));
        }
        for (int i = 0; i < 6; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.MOON_SPARK.get(), b.add(0, 0.4, 0), new Vec3(d.x * 0.25, Math.abs(d.y) * 0.4 + 0.1, d.z * 0.25));
        }
        MartialFX.sound(level, b, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.65f + rng.nextFloat() * 0.2f);
        MartialFX.sound(level, b, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9f, 0.9f + rng.nextFloat() * 0.4f);
        level.playSound(null, b.x, b.y, b.z, s.ground.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.2f, 0.55f);

        // ---- damage: anything near the base or along the slash is cut and thrown up
        Vec3[] pts = {s.point(0.0f), s.point(0.25f), s.point(0.5f), s.point(0.75f), s.point(1.0f)};
        double reachMax = s.r * 1.4 + 3;
        AABB box = new AABB(b, b).inflate(reachMax);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double range = MoonRainbowSpell.CUT_RANGE + e.getBbWidth() * 0.5;
            boolean touched = false;
            for (Vec3 p : pts) {
                if (p.distanceTo(ec) <= range) { touched = true; break; }
            }
            if (!touched) continue;
            if (!MoonFX.ready(rb.lastHit, e.getUUID(), rb.age, HIT_GAP)) continue;

            e.invulnerableTime = 0;
            if (!DamageSources.applyDamage(e, rb.damage, rb.spell.getDamageSource(caster))) continue;
            MartialFX.shove(e, s.lean.x, s.lean.z, 0.3, 0.85);    // up and a little outward, along the lean of the slash
            MoonFX.hit(level, ec, 1.0f);
        }
    }
}
