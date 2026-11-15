package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every active Hollow Purple on the server.
 *
 *   CHARGE   - Blue (pulls in) and Red (pushes out) spiral together, merge in a flash, and the purple orb swells.
 *   FLIGHT   - the orb is released and travels in a straight line. It is a pocket of imaginary space: it does not
 *              pull or push anything, it simply disintegrates every block and creature it touches.
 *   COLLAPSE - at the end of its range (or on a wall, if block breaking is off) the orb unravels into dust.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class HollowPurpleManager {

    // ---- Tuning -------------------------------------------------------------------------------
    private static final int CHARGE_TICKS = 64;
    private static final int MERGE_TICK = 46;
    private static final int COLLAPSE_TICKS = 14;
    private static final double SPEED = 0.95;          // blocks per tick
    private static final int REPEAT_INTERVAL = 6;      // ticks between crush hits on the same target

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private enum Phase { CHARGE, FLIGHT, COLLAPSE }

    private record Basis(Vec3 f, Vec3 r, Vec3 u) {
        static Basis of(Vec3 look) {
            Vec3 f = look.normalize();
            Vec3 r = f.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0);
            r = r.normalize();
            Vec3 u = r.cross(f).normalize();
            return new Basis(f, r, u);
        }
    }

    private static class Orb {
        final ServerLevel level;
        final UUID casterId;
        final HollowPurpleSpell spell;
        final int spellLevel;
        final float radius;
        Phase phase = Phase.CHARGE;
        int age = 0;
        int phaseAge = 0;

        Vec3 pos = Vec3.ZERO;
        Vec3 dir = new Vec3(0, 0, 1);
        double travelled = 0;
        final Map<UUID, Integer> lastHit = new HashMap<>();

        Orb(ServerLevel level, LivingEntity caster, HollowPurpleSpell spell, int spellLevel) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.radius = spell.getRadius(spellLevel);
        }
    }

    private static final List<Orb> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, HollowPurpleSpell spell, int spellLevel) {
        ACTIVE.add(new Orb(level, caster, spell, spellLevel));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Orb> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Orb o = it.next();
            Entity e = o.level.getEntity(o.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;

            if (caster == null && o.phase == Phase.CHARGE) {
                it.remove();
                continue;
            }
            boolean done = false;
            try {
                switch (o.phase) {
                    case CHARGE -> charge(o, caster);
                    case FLIGHT -> flight(o, caster);
                    case COLLAPSE -> done = collapse(o);
                }
            } catch (Exception ex) {
                LOGGER.error("Hollow Purple failed, cancelling it", ex);
                done = true;
            }
            o.age++;
            o.phaseAge++;
            if (done || o.age > 1200) it.remove();
        }
    }

    // =============================================================================================
    //  CHARGE
    // =============================================================================================
    private static void charge(Orb o, LivingEntity c) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        int t = o.age;
        float R = o.radius;

        Basis b = Basis.of(c.getLookAngle());
        Vec3 f = b.f(), r = b.r(), u = b.u();
        double dist = 3.2 + R * 0.9;
        Vec3 C = c.getEyePosition().add(f.scale(dist)).subtract(u.scale(0.15 + R * 0.1));
        o.pos = C;
        o.dir = f;

        if (t % 5 == 0) c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false, false));

        if (t == 0) {
            sound(level, c.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 3f, 0.6f);
            announce(level, c, Component.literal("Lapse: Blue").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        if (t == 20) {
            sound(level, c.position(), SoundEvents.BEACON_POWER_SELECT, 2.5f, 0.5f);
            announce(level, c, Component.literal("Reversal: Red").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }

        if (t < MERGE_TICK) {
            double k = t / (double) MERGE_TICK;
            double sep = 2.7 * (1 - k * k) + 0.05;
            double a = t * 0.12 + t * t * 0.0035;
            Vec3 dv = r.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
            Vec3 tang = r.scale(-Math.sin(a)).add(u.scale(Math.cos(a)));
            Vec3 blue = C.add(dv.scale(sep));
            Vec3 red = C.subtract(dv.scale(sep));
            float gr = (float) (0.35 + 0.55 * k);

            // Blue appears first, Red joins at tick 20
            glow(level, ModParticles.HP_GLOW_BLUE.get(), blue, gr * 1.8f);
            glow(level, ModParticles.HP_CORE.get(), blue, gr * 0.7f);
            if (t >= 20) {
                glow(level, ModParticles.HP_GLOW_RED.get(), red, gr * 1.8f);
                glow(level, ModParticles.HP_CORE.get(), red, gr * 0.7f);
            }
            for (int i = 0; i < 3; i++) {
                // Blue: wisps rush inward (attraction)
                Vec3 p = blue.add(rnd(rng).scale(1.8));
                one(level, ModParticles.HP_WISP_BLUE.get(), p, blue.subtract(p).scale(0.12).add(tang.scale(0.1)));
                if (t >= 20) {
                    // Red: wisps burst outward (repulsion)
                    Vec3 q = red.add(rnd(rng).scale(0.3));
                    one(level, ModParticles.HP_WISP_RED.get(), q, q.subtract(red).normalize().scale(0.22).add(tang.scale(-0.08)));
                }
            }
            if (t % 3 == 0) {
                // a spiral of lines being dragged into the centre
                for (int i = 0; i < 3; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2;
                    Vec3 p = C.add(r.scale(Math.cos(ang) * 4.5).add(u.scale(Math.sin(ang) * 4.5)).add(f.scale((rng.nextDouble() - 0.5) * 2)));
                    line(level, p, C.subtract(p).scale(0.35));
                }
            }
            if (k > 0.3 && t >= 20) {
                Vec3 mid = blue.add(red).scale(0.5);
                Vec3 j1 = mid.add(rnd(rng).scale(0.6));
                line(level, blue, j1.subtract(blue));
                line(level, j1, red.subtract(j1));
            }
            if (t % 8 == 0) sound(level, C, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, (float) (0.5 + k));
            if (t % 12 == 0) ring(level, ModParticles.HP_RING.get(), C, 1.2f + (float) k * 2.5f);
            return;
        }

        // ---- merge and swell -----------------------------------------------------------------
        int m = t - MERGE_TICK;
        double g = smooth(m / (double) (CHARGE_TICKS - MERGE_TICK));
        float rad = (float) (0.4 + (R - 0.4) * g);
        if (m == 0) {
            announce(level, c, Component.literal("Hollow Technique: Purple").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            sound(level, C, SoundEvents.LIGHTNING_BOLT_IMPACT, 3f, 1.4f);
            sound(level, C, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 0.5f);
            glow(level, ModParticles.HP_CORE.get(), C, 4.5f);
            glow(level, ModParticles.HP_GLOW.get(), C, 8f);
            for (int i = 0; i < 3; i++) ring(level, ModParticles.HP_RING.get(), C, 4f + i * 2.5f);
            for (int i = 0; i < 40; i++) {
                one(level, ModParticles.HP_WISP.get(), C, rnd(rng).normalize().scale(0.4 + rng.nextDouble() * 0.5));
            }
            for (int i = 0; i < 18; i++) {
                Vec3 d = rnd(rng).normalize();
                line(level, C.add(d.scale(0.6)), d.scale(3 + rng.nextDouble() * 4));
            }
        }
        drawOrb(level, C, rad, b, t);
        if (t % 4 == 0) sound(level, C, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 2.5f, (float) (0.5 + g * 0.6));

        if (t >= CHARGE_TICKS - 1) release(o, c, b);
    }

    private static void release(Orb o, LivingEntity c, Basis b) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        o.phase = Phase.FLIGHT;
        o.phaseAge = -1;
        o.dir = b.f();
        o.travelled = 0;
        Vec3 C = o.pos;

        sound(level, C, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.7f);
        sound(level, C, SoundEvents.END_GATEWAY_SPAWN, 3f, 1.4f);
        glow(level, ModParticles.HP_CORE.get(), C, o.radius * 2.2f);
        for (int i = 0; i < 3; i++) ring(level, ModParticles.HP_RING.get(), C, o.radius * (1.8f + i * 0.9f));
        for (int i = 0; i < 24; i++) {
            Vec3 d = b.f().scale(-1).add(rnd(rng).scale(0.6)).normalize();
            line(level, C.add(d.scale(o.radius)), d.scale(4 + rng.nextDouble() * 6));
        }
        // everything between the caster and the orb's starting point is caught too
        releaseSweep(o, c);
        // recoil
        c.setDeltaMovement(c.getDeltaMovement().add(-b.f().x * 0.7, 0.15, -b.f().z * 0.7));
        c.hurtMarked = true;
    }

    // =============================================================================================
    //  FLIGHT
    // =============================================================================================
    private static void flight(Orb o, @Nullable LivingEntity c) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        float R = o.radius;
        Basis b = Basis.of(o.dir);
        int t = o.phaseAge;

        Vec3 next = o.pos.add(o.dir.scale(SPEED));
        boolean breaks = JJKConfig.breaks(JJKConfig.HOLLOW_PURPLE_BREAKS_BLOCKS);
        BlockPos nextPos = BlockPos.containing(next);
        if (!level.hasChunkAt(nextPos) || level.isOutsideBuildHeight(nextPos)) {
            startCollapse(o);
            return;
        }
        if (!breaks) {
            Vec3 front = next.add(o.dir.scale(R * 0.6));
            BlockPos fp = BlockPos.containing(front);
            BlockState fs = level.getBlockState(fp);
            if (!fs.getCollisionShape(level, fp).isEmpty()) {
                startCollapse(o);
                return;
            }
        }
        o.pos = next;
        o.travelled += SPEED;
        Vec3 P = o.pos;

        drawOrb(level, P, R, b, o.age);

        // ---- distortion trail ----------------------------------------------------------------
        if (t % 2 == 0) ring(level, ModParticles.HP_RING.get(), P.subtract(o.dir.scale(R * 0.9)), R * 1.7f);
        int puffs = 2 + (int) (R * 0.6);
        for (int i = 0; i < puffs; i++) {
            Vec3 p = P.subtract(o.dir.scale(R * (0.7 + rng.nextDouble() * 1.8))).add(rnd(rng).scale(R * 0.7));
            one(level, ModParticles.HP_VOID.get(), p, rnd(rng).scale(0.05));
        }
        for (int i = 0; i < 4; i++) {
            double a = t * 0.5 + i * Math.PI / 2;
            Vec3 off = b.r().scale(Math.cos(a)).add(b.u().scale(Math.sin(a))).scale(R * 0.95);
            one(level, ModParticles.HP_WISP.get(), P.add(off), o.dir.scale(-0.35).add(off.normalize().scale(0.03)));
        }
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            Vec3 off = b.r().scale(Math.cos(a)).add(b.u().scale(Math.sin(a))).scale(R * (0.5 + rng.nextDouble() * 0.6));
            line(level, P.add(off), o.dir.scale(-(2 + rng.nextDouble() * (3 + R))));
        }
        // the surface of the orb flakes away: space itself is being unmade, not pulled
        for (int i = 0; i < 3 + (int) R; i++) {
            Vec3 d = rnd(rng).normalize();
            one(level, ModParticles.HP_VOID.get(), P.add(d.scale(R * 1.05)), d.scale(0.06));
        }
        if (t % 5 == 0) sound(level, P, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 3f, 0.5f);
        if (t % 12 == 0) sound(level, P, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 0.5f);

        // ---- erase blocks and crush entities ----------------------------------------------------
        if (breaks) eraseBlocks(o, c);
        crush(o, c);

        if (o.travelled >= o.spell.getRange(o.spellLevel)) startCollapse(o);
    }

    /** Damage (first contact large, then every REPEAT_INTERVAL ticks) and carry the target along with the orb. */
    private static void hitEntity(Orb o, @Nullable LivingEntity c, LivingEntity e, Vec3 ec, float power) {
        ServerLevel level = o.level;
        Integer last = o.lastHit.get(e.getUUID());
        float dmg = -1;
        float pctDmg = 0;
        if (last == null) {
            dmg = power * HollowPurpleSpell.FIRST_HIT_FACTOR;
            pctDmg = e.getMaxHealth() * HollowPurpleSpell.FIRST_HIT_PERCENT;
        }
        else if (o.age - last >= REPEAT_INTERVAL) dmg = power * HollowPurpleSpell.REPEAT_FACTOR;
        if (dmg > 0) {
            o.lastHit.put(e.getUUID(), o.age);
            e.invulnerableTime = 0;
            // The orb erases, it does not shove: keep the target exactly where it was (no knockback at all).
            Vec3 before = e.getDeltaMovement();
            DamageSources.applyDamage(e, dmg, o.spell.getDamageSource(c));
            if (pctDmg > 0 && c != null) SpellDamage.percent(e, pctDmg, c, level.damageSources().indirectMagic(c, c));
            e.setDeltaMovement(before);
            e.hurtMarked = true;
            glow(level, ModParticles.HP_CORE.get(), ec, 1.2f);
            ring(level, ModParticles.HP_RING.get(), ec, 2f);
        }
        disintegrate(level, e);
    }

    /** The creature is being unmade: its body flakes away into purple dust and thin cracks of void. */
    private static void disintegrate(ServerLevel level, LivingEntity e) {
        RandomSource rng = level.random;
        AABB bb = e.getBoundingBox();
        int n = 5 + (int) (bb.getXsize() * bb.getYsize() * 3);
        n = Math.min(n, 18);
        for (int i = 0; i < n; i++) {
            Vec3 p = new Vec3(bb.minX + rng.nextDouble() * bb.getXsize(),
                    bb.minY + rng.nextDouble() * bb.getYsize(),
                    bb.minZ + rng.nextDouble() * bb.getZsize());
            // flakes drift slowly upward and apart, like ash coming off the body
            one(level, ModParticles.HP_WISP.get(), p, rnd(rng).scale(0.05).add(0, 0.05, 0));
            if (i % 2 == 0) one(level, ModParticles.HP_VOID.get(), p, rnd(rng).scale(0.02));
        }
        if (rng.nextInt(3) == 0) {
            Vec3 c = bb.getCenter();
            Vec3 d = rnd(rng).normalize();
            line(level, c.add(d.scale(bb.getXsize() * 0.2)), d.scale(0.8 + rng.nextDouble()));
        }
    }

    /**
     * On release the orb is thrown out of the caster's hands, so everything between the caster and the orb
     * (and right next to the caster) is hit too, not just what the orb flies into afterwards.
     */
    private static void releaseSweep(Orb o, LivingEntity c) {
        Vec3 from = c.getEyePosition().subtract(o.dir.scale(0.5));
        Vec3 to = o.pos;
        Vec3 seg = to.subtract(from);
        double segLen2 = Math.max(1.0E-6, seg.lengthSqr());
        double R = o.radius;
        float power = o.spell.getSpellPower(o.spellLevel, c);
        AABB box = new AABB(from, to).inflate(R + 1.5);
        for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == c || e.isAlliedTo(c)) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double t = Mth.clamp(ec.subtract(from).dot(seg) / segLen2, 0.0, 1.0);
            double dist = ec.distanceTo(from.add(seg.scale(t)));
            if (dist <= R + 0.5 + e.getBbWidth() * 0.5) hitEntity(o, c, e, ec, power);
        }
    }

    private static void eraseBlocks(Orb o, @Nullable LivingEntity c) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        Vec3 P = o.pos;
        double R = o.radius;
        int ext = (int) Math.ceil(R);
        BlockPos origin = BlockPos.containing(P);
        ServerPlayer player = c instanceof ServerPlayer sp ? sp : null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double r2 = R * R;
        int debris = 0;

        for (int x = -ext; x <= ext; x++) {
            for (int y = -ext; y <= ext; y++) {
                for (int z = -ext; z <= ext; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    double dx = pos.getX() + 0.5 - P.x, dy = pos.getY() + 0.5 - P.y, dz = pos.getZ() + 0.5 - P.z;
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(level, pos) < 0f) continue;
                    if (player != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, player))) continue;

                    if (debris < 6 && rng.nextInt(6) == 0 && state.getFluidState().isEmpty()) {
                        debris++;
                        Vec3 bp = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        one(level, new BlockParticleOption(ParticleTypes.BLOCK, state), bp, rnd(rng).scale(0.12).add(0, 0.08, 0));
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private static void crush(Orb o, @Nullable LivingEntity c) {
        ServerLevel level = o.level;
        Vec3 P = o.pos;
        double R = o.radius;
        float power = o.spell.getSpellPower(o.spellLevel, c);
        AABB box = new AABB(P, P).inflate(R + 2);

        // Only what the orb actually touches is affected. Nothing is pulled in.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (c != null && (e == c || e.isAlliedTo(c))) continue;
            Vec3 ec = e.getBoundingBox().getCenter();
            double reach = R + 0.5 + e.getBbWidth() * 0.5;
            if (ec.distanceTo(P) <= reach) hitEntity(o, c, e, ec, power);
        }
    }

    // =============================================================================================
    //  COLLAPSE
    // =============================================================================================
    private static void startCollapse(Orb o) {
        o.phase = Phase.COLLAPSE;
        o.phaseAge = -1;
        sound(o.level, o.pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 4f, 0.5f);
    }

    private static boolean collapse(Orb o) {
        ServerLevel level = o.level;
        RandomSource rng = level.random;
        int t = o.phaseAge;
        Vec3 P = o.pos;
        Basis b = Basis.of(o.dir);
        double k = t / (double) COLLAPSE_TICKS;
        float rad = (float) (o.radius * (1 - k * k));

        if (t < COLLAPSE_TICKS) {
            if (rad > 0.2f) drawOrb(level, P, rad, b, o.age);
            // the orb unravels: its surface flakes off into dust, nothing is drawn inward
            for (int i = 0; i < 8; i++) {
                Vec3 d = rnd(rng).normalize();
                Vec3 p = P.add(d.scale(o.radius * (0.7 + rng.nextDouble() * 0.5)));
                one(level, ModParticles.HP_WISP.get(), p, d.scale(0.08 + rng.nextDouble() * 0.1));
                one(level, ModParticles.HP_VOID.get(), p, d.scale(0.03));
            }
            return false;
        }

        // final burst
        float R = o.radius;
        sound(level, P, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.9f);
        sound(level, P, SoundEvents.LIGHTNING_BOLT_IMPACT, 3f, 0.6f);
        glow(level, ModParticles.HP_CORE.get(), P, R * 2.6f);
        glow(level, ModParticles.HP_GLOW.get(), P, R * 4f);
        for (int i = 0; i < 4; i++) ring(level, ModParticles.HP_RING.get(), P, R * (2f + i * 1.3f));
        for (int i = 0; i < 20 + (int) (R * 8); i++) {
            one(level, ModParticles.HP_WISP.get(), P, rnd(rng).normalize().scale(0.3 + rng.nextDouble() * (0.5 + R * 0.12)));
        }
        for (int i = 0; i < 30; i++) {
            Vec3 d = rnd(rng).normalize();
            line(level, P.add(d.scale(R * 0.5)), d.scale(R * (1.5 + rng.nextDouble() * 2.5)));
        }
        return true;
    }

    // =============================================================================================
    //  VISUALS
    // =============================================================================================
    /** The purple orb: layered glows, three spinning gyroscope rings of wisps, and surface arcs. */
    private static void drawOrb(ServerLevel level, Vec3 P, float rad, Basis b, int age) {
        RandomSource rng = level.random;
        glow(level, ModParticles.HP_GLOW.get(), P, rad * 1.8f);
        glow(level, ModParticles.HP_GLOW.get(), P, rad * 1.25f);
        glow(level, ModParticles.HP_CORE.get(), P, rad * 0.8f);

        Vec3[][] planes = {{b.r(), b.u()}, {b.r(), b.f()}, {b.u(), b.f()}};
        int n = 2 + (int) (rad * 0.8);
        for (int pi = 0; pi < 3; pi++) {
            Vec3 A = planes[pi][0], B = planes[pi][1];
            double spin = age * (0.22 + pi * 0.07) * (pi == 1 ? -1 : 1);
            for (int i = 0; i < n; i++) {
                double a = spin + i * Math.PI * 2 / n;
                Vec3 off = A.scale(Math.cos(a)).add(B.scale(Math.sin(a)));
                Vec3 tang = A.scale(-Math.sin(a)).add(B.scale(Math.cos(a)));
                one(level, ModParticles.HP_WISP.get(), P.add(off.scale(rad * 1.05)), tang.scale(0.12 * Math.sqrt(rad)));
            }
        }
        for (int i = 0; i < 3 + (int) (rad * 0.5); i++) {
            Vec3 p1 = rnd(rng).normalize();
            Vec3 p2 = p1.add(rnd(rng).scale(0.7)).normalize();
            Vec3 a = P.add(p1.scale(rad * 1.0));
            Vec3 e = P.add(p2.scale(rad * 1.15));
            line(level, a, e.subtract(a));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------
    private static Vec3 rnd(RandomSource rng) {
        return new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
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

    private static void line(ServerLevel level, Vec3 from, Vec3 vec) {
        FX.one(level, ModParticles.HP_LINE.get(), from, vec);
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
