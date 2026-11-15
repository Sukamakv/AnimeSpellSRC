package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Runs every active "The Ruler of Death's Crimson Wrath".
 *
 *   EYES    (0-63)    the caster chants; crimson eyes open one by one in the sky and look down on them.
 *   CIRCLE  (64-99)   the aim point is locked and the huge red-and-black circle unfolds there.
 *   GLINTS  (100-123) flashes like star / sniper-scope glints flicker in the sky above the circle.
 *   RAIN    (124-283) red lasers pour onto the centre for 8 seconds; everything in the circle burns away.
 *   END     (284)     a last detonation; the eyes, circle and crimson sky fade out.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class CrimsonWrathManager {

    // ---- Timeline -----------------------------------------------------------------------------
    private static final int LOCK_TICK = 64;
    private static final int GLINT_START = 100;
    private static final int RAIN_START = 124;
    private static final int RAIN_END = RAIN_START + CrimsonWrathSpell.RAIN_TICKS; // 284
    private static final int FADE_TICKS = 45;               // how long the eyes / circle take to fade after the end
    private static final int TOTAL_LIFE = RAIN_END + FADE_TICKS;

    private static final int DAMAGE_INTERVAL = 5;           // ticks between damage pulses (5 ticks = 2.5% of max health)
    private static final double SKY_HEIGHT = 120.0;         // lasers start this high above the ground they hit
    private static final int LASERS_PER_TICK = 5;           // new lasers each tick, each landing somewhere else in the circle
    private static final double LASER_SEGMENT = 24.0;       // a laser is sent as several segments so it is drawn wherever you look

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final DustParticleOptions RED_DUST = new DustParticleOptions(new Vector3f(0.85f, 0.02f, 0.05f), 1.8f);
    private static final DustParticleOptions BLACK_DUST = new DustParticleOptions(new Vector3f(0.04f, 0.0f, 0.02f), 2.6f);

    private record EyeSpec(Vec3 offset, float width, int spawnTick) {}

    private static class Wrath {
        final ServerLevel level;
        final UUID casterId;
        final CrimsonWrathSpell spell;
        final Vec3 casterPos;
        final List<EyeSpec> eyes = new ArrayList<>();
        Vec3 target = null;
        double floorY = 0;
        int age = 0;

        Wrath(ServerLevel level, LivingEntity caster, CrimsonWrathSpell spell) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.casterPos = caster.position();
            RandomSource rng = level.random;

            // the great eye, straight above the caster
            eyes.add(new EyeSpec(new Vec3(0, 50, 0), 30f, 30));
            // a ring of smaller eyes, higher and lower, all around
            int n = 14;
            for (int i = 0; i < n; i++) {
                double a = (Math.PI * 2 / n) * i + rng.nextDouble() * 0.35;
                double d = 13 + rng.nextDouble() * 30;
                double h = 30 + rng.nextDouble() * 34;
                float w = 7 + rng.nextFloat() * 10;
                eyes.add(new EyeSpec(new Vec3(Math.cos(a) * d, h, Math.sin(a) * d), w, 6 + i * 4));
            }
        }
    }

    private static final List<Wrath> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, CrimsonWrathSpell spell) {
        ACTIVE.add(new Wrath(level, caster, spell));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Wrath> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Wrath w = it.next();
            Entity e = w.level.getEntity(w.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            // a caster who dies before the circle forms ends the spell; afterwards it carries on without them
            if (caster == null && w.age < LOCK_TICK) {
                it.remove();
                continue;
            }
            boolean done = false;
            try {
                done = tick(w, caster);
            } catch (Exception ex) {
                LOGGER.error("Crimson Wrath failed, cancelling it", ex);
                done = true;
            }
            w.age++;
            if (done) it.remove();
        }
    }

    /** @return true when the spell is over */
    private static boolean tick(Wrath w, @Nullable LivingEntity c) {
        ServerLevel level = w.level;
        int t = w.age;

        if (t < LOCK_TICK) {
            chargeTick(w, c);
        } else {
            if (t == LOCK_TICK) lockTarget(w, c);
            targetTick(w, c);
        }
        if (t >= GLINT_START && t < RAIN_START) glintTick(w);
        if (t >= RAIN_START && t < RAIN_END) rainTick(w, c);
        if (t == RAIN_END) {
            finale(w);
        }
        return t >= RAIN_END + 2;
    }

    // =============================================================================================
    //  EYES: the charge
    // =============================================================================================
    private static void chargeTick(Wrath w, @Nullable LivingEntity c) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        int t = w.age;
        Vec3 p = c.position();

        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));

        if (t == 0) {
            // the sky answers: the crimson haze begins to settle over everyone near the caster
            FX.one(level, ModParticles.CW_SKY.get(), p.add(0, 30, 0), new Vec3(0, TOTAL_LIFE + 40, 0));
            sound(level, p, SoundEvents.WARDEN_HEARTBEAT, 4f, 0.5f);
            FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(7, 0, 0));
        }

        // ---- the chant ----
        if (t == 8) announce(level, c, chant("Death"));
        if (t == 26) announce(level, c, chant("Death is mine"));
        if (t == 44) {
            announce(level, c, chant("Death is mine to command"));
            sound(level, p, SoundEvents.ENDER_DRAGON_GROWL, 4f, 0.5f);
            FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(14, 0, 0));
        }
        if (t == 60) announce(level, c, chant("Death is mine to command"));

        // ---- eyes open, one after another ----
        for (EyeSpec eye : w.eyes) {
            if (eye.spawnTick() != t) continue;
            Vec3 at = w.casterPos.add(eye.offset());
            int life = TOTAL_LIFE - t + 5;
            FX.one(level, ModParticles.CW_EYE.get(), at, new Vec3(eye.width(), life, 0));
            boolean great = eye.width() > 20;
            sound(level, p, great ? SoundEvents.WARDEN_ROAR : SoundEvents.WARDEN_NEARBY_CLOSEST, great ? 4f : 2.5f, great ? 0.6f : 0.5f + rng.nextFloat() * 0.3f);
            // a flare of red light around each eye as it opens
            FX.one(level, ModParticles.WC_GLOW.get(), at, new Vec3(eye.width() * 0.6, 0, 0));
        }

        // ---- the caster's aura: red and black smoke coiling upward, embers rising ----
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = 0.6 + rng.nextDouble() * 2.2;
            Vec3 q = p.add(Math.cos(a) * d, rng.nextDouble() * 0.4, Math.sin(a) * d);
            level.sendParticles(rng.nextBoolean() ? RED_DUST : BLACK_DUST, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
        for (int h = 0; h < 4; h++) {
            double a = t * 0.28 + h * 1.1;
            level.sendParticles(RED_DUST, p.x + Math.cos(a) * 1.4, p.y + h * 0.55, p.z + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
        }
        if (t % 2 == 0) FX.one(level, ModParticles.RED_SPARK.get(), p.add((rng.nextDouble() - 0.5) * 4, 0.2, (rng.nextDouble() - 0.5) * 4), new Vec3(0, 0.12 + rng.nextDouble() * 0.1, 0));

        // ---- dark ink pooling on the ground around the caster ----
        for (int i = 0; i < 2 + t / 16; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * (4 + t * 0.25);
            ink(level, p.x + Math.cos(a) * d, p.y, p.z + Math.sin(a) * d);
        }

        // ---- a faint marker shows where the circle will form; it is set on tick LOCK_TICK ----
        if (t >= 20 && t % 6 == 0) {
            Vec3 aim = aimPoint(level, c);
            double gy = groundY(level, aim.x, aim.y + 1, aim.z, 64, aim.y);
            FX.one(level, ModParticles.WC_RING_FLAT.get(), new Vec3(aim.x, gy + 0.08, aim.z), new Vec3(3.5, 0, 0));
            for (int i = 0; i < 4; i++) {
                FX.one(level, ModParticles.RED_SPARK.get(), new Vec3(aim.x, gy + 0.2, aim.z), new Vec3((rng.nextDouble() - 0.5) * 0.3, 0.2 + rng.nextDouble() * 0.2, (rng.nextDouble() - 0.5) * 0.3));
            }
        }
    }

    // =============================================================================================
    //  CIRCLE
    // =============================================================================================
    private static void lockTarget(Wrath w, @Nullable LivingEntity c) {
        ServerLevel level = w.level;
        Vec3 aim = aimPoint(level, c);
        double gy = groundY(level, aim.x, aim.y + 1, aim.z, 64, aim.y);
        w.target = new Vec3(aim.x, gy, aim.z);
        w.floorY = gy;
        Vec3 T = w.target;
        float R = CrimsonWrathSpell.RADIUS;
        int life = TOTAL_LIFE - LOCK_TICK;

        announce(level, c, Component.literal("The Ruler of Death's Crimson Wrath").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        announceAt(level, T, Component.literal("The Ruler of Death's Crimson Wrath").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));

        // the circle, in two layers turning in opposite directions
        FX.one(level, ModParticles.CW_CIRCLE_OUTER.get(), new Vec3(T.x, gy + 0.05, T.z), new Vec3(R, life, 0.0035));
        FX.one(level, ModParticles.CW_CIRCLE_INNER.get(), new Vec3(T.x, gy + 0.07, T.z), new Vec3(R, life, -0.0065));
        // the sky turns crimson over the target, too
        FX.one(level, ModParticles.CW_SKY.get(), T.add(0, 30, 0), new Vec3(0, life + 30, 0));

        sound(level, T, SoundEvents.BEACON_ACTIVATE, 6f, 0.5f);
        sound(level, T, SoundEvents.WARDEN_SONIC_BOOM, 6f, 0.6f);
        sound(level, T, SoundEvents.ENDER_DRAGON_GROWL, 6f, 0.5f);
        for (int i = 0; i < 3; i++) FX.one(level, ModParticles.WC_RING_FLAT.get(), new Vec3(T.x, gy + 0.1 + i * 0.02, T.z), new Vec3(R * (0.7 + i * 0.3), 0, 0));
        FX.one(level, ModParticles.WC_GLOW.get(), T.add(0, 1, 0), new Vec3(10, 0, 0));
        FX.one(level, ModParticles.RED_IMPACT.get(), T.add(0, 0.3, 0), new Vec3(R * 0.5, 0, 0));
    }

    /** Everything that lives on the circle while it stands: red motes, dark ink, sparks hugging the terrain. */
    private static void targetTick(Wrath w, @Nullable LivingEntity c) {
        if (w.target == null) return;
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        Vec3 T = w.target;
        float R = CrimsonWrathSpell.RADIUS;

        // sparks that follow the real terrain along the rings of the circle
        for (int i = 0; i < 8; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double rr = R * (i < 4 ? 0.97 : (i < 6 ? 0.82 : 0.4 + rng.nextDouble() * 0.2));
            double x = T.x + Math.cos(a) * rr, z = T.z + Math.sin(a) * rr;
            double gy = groundY(level, x, w.floorY + 12, z, 40, w.floorY);
            FX.one(level, ModParticles.RED_SPARK.get(), new Vec3(x, gy + 0.15, z), new Vec3(0, 0.07 + rng.nextDouble() * 0.08, 0));
        }
        // red motes rising from inside the circle
        for (int i = 0; i < 6; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * R * 0.95;
            double x = T.x + Math.cos(a) * d, z = T.z + Math.sin(a) * d;
            double gy = groundY(level, x, w.floorY + 12, z, 40, w.floorY);
            FX.one(level, ModParticles.RED_SPARK.get(), new Vec3(x, gy + 0.3, z), new Vec3((rng.nextDouble() - 0.5) * 0.04, 0.1 + rng.nextDouble() * 0.12, (rng.nextDouble() - 0.5) * 0.04));
        }
        // black ink low over the ground
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * R * 0.9;
            ink(level, T.x + Math.cos(a) * d, w.floorY, T.z + Math.sin(a) * d);
        }
        // the circle pulses once a second
        if ((w.age - LOCK_TICK) % 20 == 10 && w.age < RAIN_END) {
            FX.one(level, ModParticles.WC_RING_FLAT.get(), new Vec3(T.x, w.floorY + 0.1, T.z), new Vec3(R * 0.55, 0, 0));
        }
        if (w.age % 16 == 0) sound(level, T, SoundEvents.WARDEN_HEARTBEAT, 4f, 0.5f);
    }

    // =============================================================================================
    //  GLINTS
    // =============================================================================================
    /** Quick star / sniper-scope glints flashing in the sky above the circle, faster and faster. */
    private static void glintTick(Wrath w) {
        if (w.target == null) return;
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        Vec3 T = w.target;
        int k = w.age - GLINT_START;
        int n = 1 + k / 4;
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * CrimsonWrathSpell.RADIUS * 1.1;
            Vec3 p = T.add(Math.cos(a) * d, 55 + rng.nextDouble() * 60, Math.sin(a) * d);
            FX.one(level, ModParticles.CW_GLINT.get(), p, new Vec3(5 + rng.nextDouble() * 8, 0, 0));
        }
        if (k % 2 == 0) {
            sound(level, T, SoundEvents.AMETHYST_BLOCK_CHIME, 4f, 1.5f + rng.nextFloat() * 0.5f);
        }
        if (w.age == RAIN_START - 1) {
            // the final, biggest glint, straight above the centre: the aim is made
            FX.one(level, ModParticles.CW_GLINT.get(), T.add(0, SKY_HEIGHT * 0.8, 0), new Vec3(26, 0, 0));
            sound(level, T, SoundEvents.BEACON_POWER_SELECT, 6f, 1.6f);
        }
    }

    // =============================================================================================
    //  RAIN
    // =============================================================================================
    private static void rainTick(Wrath w, @Nullable LivingEntity c) {
        ServerLevel level = w.level;
        RandomSource rng = level.random;
        Vec3 T = w.target;
        if (T == null) return;
        int k = w.age - RAIN_START;
        float R = CrimsonWrathSpell.RADIUS;
        boolean breaks = JJKConfig.breaks(JJKConfig.CRIMSON_WRATH_BREAKS_BLOCKS);

        if (k == 0) {
            sound(level, T, SoundEvents.WARDEN_SONIC_BOOM, 8f, 0.5f);
            sound(level, T, SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.5f);
            FX.one(level, ModParticles.WC_GLOW.get(), T.add(0, 1, 0), new Vec3(14, 0, 0));
            FX.one(level, ModParticles.WC_RING.get(), T.add(0, 1, 0), new Vec3(18, 0, 0));
        }

        // ---- the lasers: every one comes down on a different spot, spread over the whole circle ----
        // (the first laser of the whole storm is the exception: it strikes the exact point that was aimed at)
        for (int i = 0; i < LASERS_PER_TICK; i++) {
            double x, z;
            if (k == 0 && i == 0) {
                x = T.x;
                z = T.z;
            } else {
                double a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * R * 0.97;
                x = T.x + Math.cos(a) * d;
                z = T.z + Math.sin(a) * d;
            }
            double gy = groundY(level, x, w.floorY + 14, z, 44, w.floorY);
            Vec3 impact = new Vec3(x, gy + 0.2, z);
            // they fall almost straight down, each from a slightly different direction
            Vec3 top = impact.add((rng.nextDouble() - 0.5) * 10, SKY_HEIGHT, (rng.nextDouble() - 0.5) * 10);
            laser(level, top, impact);
            impactEffects(level, impact, rng, i == 0);
            if (breaks) digImpact(level, impact, w, c, rng);
            if (i == 0) {
                // one big explosion per tick, somewhere in the circle
                FX.one(level, ModParticles.RED_IMPACT.get(), impact.add(0, 0.3, 0), new Vec3(3.5 + rng.nextDouble() * 2, 0, 0));
                sound(level, impact, SoundEvents.GENERIC_EXPLODE, 5f, 0.55f + rng.nextFloat() * 0.3f);
            }
            if (i == 1 && k % 2 == 0) FX.one(level, ModParticles.FUGA_SMOKE.get(), impact.add(0, 0.5, 0), new Vec3(0, 0.12, 0));
        }
        if (k % 10 == 0) {
            FX.one(level, ModParticles.WC_RING_FLAT.get(), new Vec3(T.x, w.floorY + 0.12, T.z), new Vec3(R * 0.6, 0, 0));
        }
        if (k % 12 == 0) sound(level, T, SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.6f);

        // ---- damage: 10% of max health per second to everything inside the circle ----
        if (k % DAMAGE_INTERVAL == 0) damagePulse(w, c);
    }

    /**
     * One laser from the sky to the ground. It is sent as several shorter segments: a single 120-block line is only
     * drawn while its start point is on screen, so you would have to look up at the sky to see it.
     */
    private static void laser(ServerLevel level, Vec3 top, Vec3 impact) {
        Vec3 total = impact.subtract(top);
        int segs = Math.max(1, (int) Math.ceil(total.length() / LASER_SEGMENT));
        Vec3 step = total.scale(1.0 / segs);
        Vec3 stretch = step.scale(1.03); // a hair of overlap so there are no gaps
        for (int i = 0; i < segs; i++) {
            FX.one(level, ModParticles.CW_LASER.get(), top.add(step.scale(i)), stretch);
        }
    }

    private static void impactEffects(ServerLevel level, Vec3 p, RandomSource rng, boolean big) {
        for (int i = 0; i < (big ? 4 : 2); i++) {
            Vec3 v = new Vec3((rng.nextDouble() - 0.5) * 1.2, 0.25 + rng.nextDouble() * 0.9, (rng.nextDouble() - 0.5) * 1.2);
            FX.one(level, ModParticles.RED_SPARK.get(), p, v);
        }
        if (big) {
            BlockState st = level.getBlockState(BlockPos.containing(p.x, p.y - 0.6, p.z));
            if (!st.isAir() && st.getFluidState().isEmpty()) {
                FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), p,
                        new Vec3((rng.nextDouble() - 0.5) * 0.8, 0.5 + rng.nextDouble() * 0.7, (rng.nextDouble() - 0.5) * 0.8));
            }
            FX.one(level, ModParticles.WC_GLOW.get(), p.add(0, 0.6, 0), new Vec3(2.2, 0, 0));
        }
    }

    /** The lasers drill: each impact removes a sphere of blocks from the current surface, so the centre sinks into a pit. */
    private static void digImpact(ServerLevel level, Vec3 impact, Wrath w, @Nullable LivingEntity c, RandomSource rng) {
        double surface = groundY(level, impact.x, w.floorY + 3, impact.z, 30, Double.NaN);
        if (Double.isNaN(surface)) return;
        if (surface < w.floorY - 18) return; // never deeper than 18 blocks
        double cy = surface - 1.0;
        double R = 2.2;
        int ext = (int) Math.ceil(R);
        ServerPlayer player = c instanceof ServerPlayer sp ? sp : null;
        BlockPos o = BlockPos.containing(impact.x, cy, impact.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -ext; x <= ext; x++) {
            for (int y = -ext; y <= ext; y++) {
                for (int z = -ext; z <= ext; z++) {
                    if (x * x + y * y + z * z > R * R) continue;
                    pos.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) continue;
                    if (st.getDestroySpeed(level, pos) < 0f || st.getBlock().getExplosionResistance() >= 1000f) continue;
                    if (player != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), st, player))) continue;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /** 10% of max health per second = 2.5% every 5 ticks, to every living thing inside the circle. Armor does not help. */
    private static void damagePulse(Wrath w, @Nullable LivingEntity c) {
        ServerLevel level = w.level;
        Vec3 T = w.target;
        double R = CrimsonWrathSpell.RADIUS;
        AABB box = new AABB(T.x - R, T.y - 40, T.z - R, T.x + R, T.y + 90, T.z + R);
        float fraction = CrimsonWrathSpell.DPS_PERCENT * DAMAGE_INTERVAL / 20f;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (c != null && (e == c || e.isAlliedTo(c))) continue;
            if (e.isSpectator() || (e instanceof Player p && p.getAbilities().invulnerable)) continue;
            double dx = e.getX() - T.x, dz = e.getZ() - T.z;
            if (dx * dx + dz * dz > R * R) continue;

            e.invulnerableTime = 0;
            var source = c != null ? level.damageSources().indirectMagic(c, c) : level.damageSources().magic();
            SpellDamage.percent(e, e.getMaxHealth() * fraction, c, source);

            Vec3 ec = e.getBoundingBox().getCenter();
            FX.one(level, ModParticles.WC_GLOW.get(), ec, new Vec3(1.6, 0, 0));
            for (int i = 0; i < 3; i++) {
                FX.one(level, ModParticles.RED_SPARK.get(), ec, new Vec3((level.random.nextDouble() - 0.5) * 0.5, 0.15 + level.random.nextDouble() * 0.3, (level.random.nextDouble() - 0.5) * 0.5));
            }
        }
    }

    // =============================================================================================
    //  END
    // =============================================================================================
    private static void finale(Wrath w) {
        ServerLevel level = w.level;
        Vec3 T = w.target;
        if (T == null) return;
        float R = CrimsonWrathSpell.RADIUS;
        sound(level, T, SoundEvents.GENERIC_EXPLODE, 10f, 0.4f);
        sound(level, T, SoundEvents.WARDEN_SONIC_BOOM, 8f, 0.4f);
        sound(level, T, SoundEvents.BEACON_DEACTIVATE, 8f, 0.5f);
        FX.one(level, ModParticles.WC_GLOW.get(), T.add(0, 2, 0), new Vec3(20, 0, 0));
        FX.one(level, ModParticles.WC_RING.get(), T.add(0, 2, 0), new Vec3(28, 0, 0));
        FX.one(level, ModParticles.RED_IMPACT.get(), T.add(0, 0.5, 0), new Vec3(R * 0.8, 0, 0));
        for (int i = 0; i < 3; i++) FX.one(level, ModParticles.WC_RING_FLAT.get(), new Vec3(T.x, w.floorY + 0.12 + i * 0.02, T.z), new Vec3(R * (1.0 + i * 0.25), 0, 0));
        RandomSource rng = level.random;
        for (int i = 0; i < 40; i++) {
            Vec3 d = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 1.4, rng.nextDouble() * 2 - 1).normalize();
            FX.one(level, ModParticles.CW_LASER.get(), T.add(0, 0.5, 0), d.scale(6 + rng.nextDouble() * 12));
        }
    }

    // =============================================================================================
    //  HELPERS
    // =============================================================================================
    /** Where the caster is aiming: the first block or creature along the line of sight, up to 100 blocks away. */
    private static Vec3 aimPoint(ServerLevel level, LivingEntity c) {
        Vec3 eye = c.getEyePosition();
        Vec3 look = c.getLookAngle();
        Vec3 end = eye.add(look.scale(CrimsonWrathSpell.RANGE));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        Vec3 hit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation().subtract(look.scale(0.3));
        AABB box = c.getBoundingBox().expandTowards(look.scale(CrimsonWrathSpell.RANGE)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, c, eye, hit, box, e -> e.isAlive() && !e.isSpectator() && e.isPickable());
        if (eh != null) hit = eh.getLocation();
        return hit;
    }

    /** Top of the floor under (x, z), searching down from startY. */
    private static double groundY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        if (!level.hasChunkAt(p)) return fallback;
        for (int i = 0; i < maxDown; i++) {
            if (level.isOutsideBuildHeight(p)) return fallback;
            var shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty()) return p.getY() + shape.max(Direction.Axis.Y);
            p.move(Direction.DOWN);
        }
        return fallback;
    }

    private static void ink(ServerLevel level, double x, double casterY, double z) {
        double gy = groundY(level, x, casterY + 2, z, 12, casterY);
        FX.one(level, ModParticles.BILLOW_SMOKE.get(), new Vec3(x, gy + 0.05 + level.random.nextDouble() * 0.2, z),
                new Vec3((level.random.nextDouble() - 0.5) * 0.03, 0, (level.random.nextDouble() - 0.5) * 0.03));
    }

    private static Component chant(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD, ChatFormatting.ITALIC);
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void announce(ServerLevel level, LivingEntity caster, Component text) {
        announceAt(level, caster.position(), text);
    }

    private static void announceAt(ServerLevel level, Vec3 where, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(where) < 128 * 128) sp.displayClientMessage(text, true);
        }
    }
}
