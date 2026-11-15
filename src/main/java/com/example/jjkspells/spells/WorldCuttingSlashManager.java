package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
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
 * Runs every active World Cutting Slash.
 *
 *   CHARGE - the chant. "Scales of the Dragon": rows of scales spread over the ground. "Repulsion": a pulse
 *            throws everything outward. "Paired Falling Stars": two stars fall in front of the caster.
 *   CUT    - the world is cut. A front sweeps forward along a square grid of slashes (aligned to the direction
 *            the caster faces): slits are carved through the terrain, ink-and-white cut lines hang in the air,
 *            slabs of ground are left standing between them, and everything the front passes is cut.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class WorldCuttingSlashManager {

    // ---- Tuning -------------------------------------------------------------------------------
    private static final int REPULSION_TICK = 24;
    private static final int STARS_TICK = 48;
    private static final int LAND_TICK = 70;
    private static final int CUT_TICK = 72;
    private static final int SPACING = 7;             // distance between cuts
    private static final int START = 4;               // the grid starts this far in front of the caster
    private static final int DOWN = 24;               // cuts reach this far below the caster ...
    private static final int UP = 40;                 // ... and this far above
    private static final double FRONT_SPEED = 6.0;    // blocks per tick the cut front travels
    private static final int LINGER = 40;
    private static final double NEAR = 3.0;           // the damage zone reaches this far behind the caster
    private static final double NEAR_RADIUS = 6.0;    // anything this close to the caster is always cut

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private enum Phase { CHARGE, CUT }

    private static class Slash {
        final ServerLevel level;
        final UUID casterId;
        final WorldCuttingSlashSpell spell;
        final int spellLevel;
        final int range;
        final int half;                // half width of the grid, a multiple of SPACING
        Phase phase = Phase.CHARGE;
        int age = 0;
        int cutAge = 0;
        Vec3 origin = Vec3.ZERO;       // caster's feet when the cut starts
        Vec3 F = new Vec3(0, 0, 1);    // forward axis (snapped to a cardinal direction)
        Vec3 L = new Vec3(1, 0, 0);    // lateral axis
        final Set<UUID> hit = new HashSet<>();

        Slash(ServerLevel level, LivingEntity caster, WorldCuttingSlashSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.range = spell.getRange(spellLevel);
            this.half = Math.max(SPACING, (range / 2 / SPACING) * SPACING);
        }
    }

    private static final List<Slash> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, WorldCuttingSlashSpell spell, int spellLevel) {
        Slash s = new Slash(level, caster, spell, spellLevel);
        snapAxes(s, caster);
        ACTIVE.add(s);
    }

    private static void snapAxes(Slash s, LivingEntity c) {
        Direction d = c.getDirection(); // horizontal facing
        s.F = new Vec3(d.getStepX(), 0, d.getStepZ());
        s.L = new Vec3(-s.F.z, 0, s.F.x);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Slash> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Slash s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;

            if (caster == null && s.phase == Phase.CHARGE) {
                it.remove();
                continue;
            }
            boolean done = false;
            try {
                if (s.phase == Phase.CHARGE) charge(s, caster);
                else done = cut(s, caster);
            } catch (Exception ex) {
                LOGGER.error("World Cutting Slash failed, cancelling it", ex);
                done = true;
            }
            s.age++;
            if (done || s.age > 1200) it.remove();
        }
    }

    // =============================================================================================
    //  CHARGE
    // =============================================================================================
    private static void charge(Slash s, LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        int t = s.age;
        Vec3 feet = c.position();
        Vec3 F = s.F, L = s.L;
        double gy = feet.y + 0.07;

        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));
        s.origin = feet;

        // ---- "Scales of the Dragon.": overlapping rows of scales spreading over the ground ------------------
        if (t == 0) {
            sound(level, feet, SoundEvents.ENDER_DRAGON_GROWL, 3f, 0.7f);
            announce(level, c, Component.literal("Scales of the Dragon.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
        }
        if (t % 6 == 0 && t < CUT_TICK) {
            int rows = Math.min(5, 1 + t / 9);
            for (int j = 1; j <= rows; j++) {
                double rad = 2.4 * j;
                int n = 6 * j;
                double a0 = (j % 2 == 0 ? 0.5 : 0.0) * Math.PI * 2 / n + t * 0.004 * (j % 2 == 0 ? -1 : 1);
                for (int i = 0; i < n; i++) {
                    double a = a0 + i * Math.PI * 2 / n;
                    scale(level, new Vec3(feet.x + Math.cos(a) * rad, gy, feet.z + Math.sin(a) * rad), a, 1.25 + j * 0.05);
                }
            }
        }

        // ---- "Repulsion.": a pulse throws everything outward -----------------------------------------------
        if (t == REPULSION_TICK) {
            announce(level, c, Component.literal("Repulsion.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            sound(level, feet, SoundEvents.GENERIC_EXPLODE, 3f, 1.4f);
            sound(level, feet, SoundEvents.ENDER_DRAGON_FLAP, 3f, 0.6f);
            ring(level, ModParticles.WC_RING_FLAT.get(), new Vec3(feet.x, gy + 0.05, feet.z), 16f);
            ring(level, ModParticles.WC_RING_FLAT.get(), new Vec3(feet.x, gy + 0.05, feet.z), 10f);
            ring(level, ModParticles.WC_RING.get(), feet.add(0, 1, 0), 9f);
            glow(level, ModParticles.WC_GLOW.get(), feet.add(0, 1, 0), 4f);
            for (int i = 0; i < 70; i++) {
                double a = rng.nextDouble() * Math.PI * 2, d = 1 + rng.nextDouble() * 4;
                Vec3 p = new Vec3(feet.x + Math.cos(a) * d, gy, feet.z + Math.sin(a) * d);
                BlockState st = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
                if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), p,
                        new Vec3(Math.cos(a) * (0.5 + rng.nextDouble() * 0.7), 0.25 + rng.nextDouble() * 0.5, Math.sin(a) * (0.5 + rng.nextDouble() * 0.7)));
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(10), LivingEntity::isAlive)) {
                if (e == c || e.isAlliedTo(c)) continue;
                Vec3 out = e.position().subtract(feet);
                out = new Vec3(out.x, 0, out.z);
                if (out.lengthSqr() < 1.0E-4) out = new Vec3(1, 0, 0);
                out = out.normalize();
                e.setDeltaMovement(out.x * 1.3, 0.35, out.z * 1.3);
                com.example.jjkspells.effect.OverloadEvents.forceMove(e);
                e.hurtMarked = true;
            }
        }

        // ---- "Paired Falling Stars.": two stars come down ahead of the caster -----------------------------------
        double lateral = 7.0;
        double fwd = START + 8.0;
        Vec3 land1 = feet.add(F.scale(fwd)).add(L.scale(lateral));
        Vec3 land2 = feet.add(F.scale(fwd)).add(L.scale(-lateral));
        if (t == STARS_TICK) {
            announce(level, c, Component.literal("Paired Falling Stars.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            sound(level, feet, SoundEvents.FIREWORK_ROCKET_LAUNCH, 3f, 0.5f);
        }
        if (t >= STARS_TICK && t <= LAND_TICK) {
            double u = (t - STARS_TICK) / (double) (LAND_TICK - STARS_TICK);
            double h = 70 * (1 - u * u);
            starAt(level, land1.add(0, h, 0), rng);
            starAt(level, land2.add(0, h, 0), rng);
            // the stars drift toward each other as they fall
        }
        if (t == LAND_TICK) {
            for (Vec3 land : new Vec3[]{land1, land2}) {
                double y = floorY(level, land.x, land.y + 8, land.z, 30, land.y);
                Vec3 p = new Vec3(land.x, y, land.z);
                sound(level, p, SoundEvents.FIREWORK_ROCKET_BLAST_FAR, 4f, 0.6f);
                glow(level, ModParticles.WC_GLOW.get(), p.add(0, 1, 0), 5f);
                ring(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.1, 0), 9f);
                ring(level, ModParticles.WC_RING.get(), p.add(0, 1, 0), 6f);
                for (int i = 0; i < 40; i++) {
                    Vec3 d = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 0.8 + 0.2, rng.nextDouble() * 2 - 1).normalize();
                    one(level, ModParticles.FUGA_BEAM.get(), p.add(0, 0.3, 0), d.scale(2 + rng.nextDouble() * 5));
                }
                for (int i = 0; i < 12; i++) {
                    one(level, ModParticles.FUGA_SMOKE.get(), p.add((rng.nextDouble() - 0.5) * 3, 0.5, (rng.nextDouble() - 0.5) * 3), new Vec3(0, 0.05, 0));
                }
            }
        }

        if (t >= CUT_TICK - 1) beginCut(s, c);
    }

    /** One falling star: a bright head, a long trail of light and sparks. */
    private static void starAt(ServerLevel level, Vec3 p, RandomSource rng) {
        glow(level, ModParticles.WC_GLOW.get(), p, 1.8f);
        glow(level, ModParticles.FUGA_GLOW.get(), p, 0.8f);
        for (int i = 0; i < 3; i++) {
            Vec3 o = new Vec3((rng.nextDouble() - 0.5) * 0.5, 0, (rng.nextDouble() - 0.5) * 0.5);
            line(level, ModParticles.FUGA_BEAM.get(), p.add(o), new Vec3(0, 12 + rng.nextDouble() * 10, 0));
        }
        one(level, ModParticles.FUGA_EMBER.get(), p, new Vec3((rng.nextDouble() - 0.5) * 0.2, 0.1, (rng.nextDouble() - 0.5) * 0.2));
    }

    /** A single dragon scale: a half circle of ink-and-white line, opening toward the centre. */
    private static void scale(ServerLevel level, Vec3 centre, double angle, double rad) {
        int segs = 5;
        for (int i = 0; i < segs; i++) {
            double a0 = angle + Math.PI * 0.5 + i * Math.PI / segs;
            double a1 = angle + Math.PI * 0.5 + (i + 1) * Math.PI / segs;
            Vec3 p0 = centre.add(Math.cos(a0) * rad * 0.5, 0, Math.sin(a0) * rad * 0.5);
            Vec3 p1 = centre.add(Math.cos(a1) * rad * 0.5, 0, Math.sin(a1) * rad * 0.5);
            line(level, ModParticles.WC_INK.get(), p0, p1.subtract(p0));
        }
    }

    // =============================================================================================
    //  CUT
    // =============================================================================================
    private static void beginCut(Slash s, LivingEntity c) {
        ServerLevel level = s.level;
        s.phase = Phase.CUT;
        s.cutAge = 0;
        // the grid is anchored to the block grid under the caster so the cuts line up with the world
        s.origin = new Vec3(Math.floor(c.getX()) + 0.5, Math.floor(c.getY()), Math.floor(c.getZ()) + 0.5);
        Vec3 p = s.origin;
        sound(level, p, SoundEvents.GLASS_BREAK, 5f, 0.5f);
        sound(level, p, SoundEvents.TRIDENT_THUNDER, 5f, 1.2f);
        sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 4f, 1.5f);
        glow(level, ModParticles.WC_GLOW.get(), p.add(s.F.scale(START)).add(0, 1.5, 0), 9f);
        ring(level, ModParticles.WC_RING.get(), p.add(s.F.scale(START)).add(0, 1.5, 0), 12f);
    }

    /** @return true once everything has settled */
    private static boolean cut(Slash s, @Nullable LivingEntity c) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        int i = s.cutAge;
        int F1 = s.range;
        double prev = START + i * FRONT_SPEED - (i == 0 ? 1 : 0);
        double cur = Math.min(F1, START + (i + 1) * FRONT_SPEED);
        boolean sweeping = prev < F1;

        if (sweeping) {
            drawLattice(s, prev, cur);
            if (JJKConfig.breaks(JJKConfig.WORLD_CUTTING_BREAKS_BLOCKS)) carve(s, c, prev, cur);
            damageFront(s, c, cur);
            rubble(s, prev, cur);
            if (i % 2 == 0) sound(level, s.origin.add(s.F.scale((prev + cur) * 0.5)), SoundEvents.GLASS_BREAK, 3f, 0.6f + rng.nextFloat() * 0.5f);
        } else if (i < (F1 - START) / FRONT_SPEED + LINGER) {
            // settling: slabs shed dust and chips
            if (i % 2 == 0) rubble(s, START, F1);
        }

        s.cutAge++;
        return i >= (F1 - START) / FRONT_SPEED + LINGER;
    }

    private static boolean onAcross(int f) {
        return Math.floorMod(f - START, SPACING) == 0;
    }

    /** Carves the slits the front crosses this tick: grid lines along the forward direction and across it. */
    private static void carve(Slash s, @Nullable LivingEntity c, double prev, double cur) {
        ServerLevel level = s.level;
        int fa = (int) Math.floor(prev) + 1, fb = (int) Math.floor(cur);
        if (prev <= START) fa = START;
        ServerPlayer player = c instanceof ServerPlayer sp ? sp : null;
        BlockPos o = BlockPos.containing(s.origin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int fx = (int) s.F.x, fz = (int) s.F.z, lx = (int) s.L.x, lz = (int) s.L.z;

        for (int f = fa; f <= fb; f++) {
            boolean across = onAcross(f);
            // ragged: every cut is sometimes two blocks wide
            boolean acrossWide = Math.floorMod(f - START, SPACING) == 1 && (hash(f, 7, 3) % 3 == 0);
            for (int l = -s.half; l <= s.half; l++) {
                boolean lat = Math.floorMod(l + s.half, SPACING) == 0;
                boolean latWide = Math.floorMod(l + s.half, SPACING) == 1 && (hash(f / 3, l, 5) % 3 == 0);
                if (!(across || acrossWide || lat || latWide)) continue;
                int down = DOWN - (hash(f, l, 11) % 5);
                int up = UP - (hash(f, l, 13) % 8);
                for (int y = -down; y <= up; y++) {
                    pos.set(o.getX() + fx * f + lx * l, o.getY() + y, o.getZ() + fz * f + lz * l);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) continue;
                    if (st.getDestroySpeed(level, pos) < 0f) continue;
                    if (player != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), st, player))) continue;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private static int hash(int a, int b, int c) {
        int h = (a * 73856093) ^ (b * 19349663) ^ (c * 83492791);
        return (h >>> 3) & 0x7fffffff;
    }

    /** Cuts everything the front has reached. Armor does not help against it. */
    private static void damageFront(Slash s, @Nullable LivingEntity c, double cur) {
        ServerLevel level = s.level;
        Vec3 O = s.origin;
        float power = s.spell.getSpellPower(s.spellLevel, c);
        double W = s.half;
        // The damage zone starts right at the caster (a little behind, too), not at the first cut, so anything
        // standing next to the caster is cut as well.
        Vec3 a = O.add(s.F.scale(-NEAR)).add(s.L.scale(-W - 1)).add(0, -DOWN, 0);
        Vec3 b = O.add(s.F.scale(cur + 1)).add(s.L.scale(W + 1)).add(0, UP, 0);
        AABB box = new AABB(a, b).minmax(new AABB(O, O).inflate(NEAR_RADIUS));

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (c != null && (e == c || e.isAlliedTo(c))) continue;
            if (e.isSpectator() || s.hit.contains(e.getUUID())) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            Vec3 rel = ec.subtract(O);
            double fd = rel.dot(s.F), ld = rel.dot(s.L), yd = rel.y;
            boolean near = rel.length() <= NEAR_RADIUS; // everything around the caster is inside the cut
            if (!near && (fd < -NEAR || fd > cur || Math.abs(ld) > W + 1 || yd < -DOWN || yd > UP)) continue;
            s.hit.add(e.getUUID());

            // standing right on a cut?
            double rl = Math.floorMod((int) Math.round(ld) + (int) W, SPACING);
            double dl = Math.min(rl, SPACING - rl);
            double ra = Math.floorMod((int) Math.round(fd) - START, SPACING);
            double da = Math.min(ra, SPACING - ra);
            float bonus = Math.min(dl, da) <= 1.5 + e.getBbWidth() * 0.5 ? WorldCuttingSlashSpell.DIRECT_BONUS : 1f;

            float flat = power * WorldCuttingSlashSpell.DAMAGE_FACTOR * bonus;
            float pct = e.getMaxHealth() * WorldCuttingSlashSpell.PERCENT * bonus;
            e.invulnerableTime = 0;
            DamageSources.applyDamage(e, flat, s.spell.getDamageSource(c));
            e.invulnerableTime = 0;
            if (pct > 0 && e.isAlive()) SpellDamage.percent(e, pct, c, level.damageSources().indirectMagic(c, c));

            // a cut across the victim
            Vec3 d = s.L.scale(0.8).add(0, 0.7, 0).add(s.F.scale((level.random.nextDouble() - 0.5) * 0.6)).normalize();
            line(level, ModParticles.WC_INK.get(), ec.subtract(d.scale(2.5)), d.scale(5));
            glow(level, ModParticles.WC_GLOW.get(), ec, 1.6f);
        }
    }

    // =============================================================================================
    //  VISUALS
    // =============================================================================================
    /** The part of the grid the front crossed this tick: ink-and-white cut lines in the air, like the manga panel. */
    private static void drawLattice(Slash s, double prev, double cur) {
        ServerLevel level = s.level;
        Vec3 O = s.origin;
        Vec3 F = s.F, L = s.L;
        int[] heights = {-12, -6, 0, 8, 16, 24, 32};
        double from = Math.max(START, prev), to = cur;

        // lines running forward inside each lateral plane, at several heights
        for (int l = -s.half; l <= s.half; l += SPACING) {
            for (int h : heights) {
                Vec3 p = O.add(F.scale(from)).add(L.scale(l)).add(0, h + 0.5, 0);
                line(level, ModParticles.WC_INK.get(), p, F.scale(to - from));
            }
        }
        // for each across-plane inside the window: lines running sideways at the same heights, plus verticals at each crossing
        int fa = (int) Math.ceil(from), fb = (int) Math.floor(to);
        for (int f = fa; f <= fb; f++) {
            if (!onAcross(f)) continue;
            for (int h : heights) {
                Vec3 p = O.add(F.scale(f)).add(L.scale(-s.half)).add(0, h + 0.5, 0);
                line(level, ModParticles.WC_INK.get(), p, L.scale(2.0 * s.half));
            }
            for (int l = -s.half; l <= s.half; l += SPACING) {
                Vec3 p = O.add(F.scale(f)).add(L.scale(l)).add(0, -DOWN * 0.5, 0);
                line(level, ModParticles.WC_INK.get(), p, new Vec3(0, UP + DOWN * 0.5, 0));
            }
        }
    }

    /** Chips and slabs thrown up from the cuts, plus dust. */
    private static void rubble(Slash s, double prev, double cur) {
        ServerLevel level = s.level;
        RandomSource rng = level.random;
        double from = Math.max(START, prev);
        int n = 14;
        for (int k = 0; k < n; k++) {
            double f = from + rng.nextDouble() * Math.max(1, cur - from);
            int lat = (rng.nextInt(2 * s.half / SPACING + 1) - s.half / SPACING) * SPACING;
            double l = lat + (rng.nextBoolean() ? 0 : (rng.nextDouble() - 0.5) * s.half);
            Vec3 base = s.origin.add(s.F.scale(f)).add(s.L.scale(l));
            double y = floorY(level, base.x, s.origin.y + 20, base.z, 50, Double.NaN);
            if (Double.isNaN(y)) continue;
            BlockState st = level.getBlockState(BlockPos.containing(base.x, y - 0.5, base.z));
            if (st.isAir() || !st.getFluidState().isEmpty()) {
                st = Blocks.STONE.defaultBlockState();
            }
            Vec3 p = new Vec3(base.x, y + 0.3, base.z);
            one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), p,
                    new Vec3((rng.nextDouble() - 0.5) * 0.4, 0.35 + rng.nextDouble() * 0.7, (rng.nextDouble() - 0.5) * 0.4));
            if (k % 3 == 0) one(level, ModParticles.FUGA_SMOKE.get(), p.add(0, 0.5, 0), new Vec3(0, 0.06, 0));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------
    private static double floorY(ServerLevel level, double x, double startY, double z, int maxDown, double fallback) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(startY), Mth.floor(z));
        if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p)) return fallback;
        for (int i = 0; i < maxDown; i++) {
            if (level.isOutsideBuildHeight(p)) return fallback;
            BlockState st = level.getBlockState(p);
            if (!st.getCollisionShape(level, p).isEmpty()) return p.getY() + 1.0;
            p.move(Direction.DOWN);
        }
        return fallback;
    }

    private static void one(ServerLevel level, ParticleOptions type, Vec3 pos, Vec3 vel) {
        FX.one(level, type, pos, vel);
    }

    private static void glow(ServerLevel level, ParticleOptions type, Vec3 pos, float radius) {
        FX.one(level, type, pos, new Vec3(radius, 0, 0));
    }

    private static void ring(ServerLevel level, ParticleOptions type, Vec3 pos, float radius) {
        FX.one(level, type, pos, new Vec3(radius, 0, 0));
    }

    private static void line(ServerLevel level, ParticleOptions type, Vec3 from, Vec3 vec) {
        FX.one(level, type, from, vec);
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void announce(ServerLevel level, LivingEntity caster, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(caster) < 96 * 96) sp.displayClientMessage(text, true);
        }
    }
}
