package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Runs every active Crimson Ray.
 *
 *   CHARGE (0-29)    the caster is rooted; a glint grows in the sky straight above them.
 *   BEAM   (30-209)  the laser stands from that fixed point to the caster's aim point, 9 seconds, damaging every tick.
 *   FADE             a last flash.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class CrimsonRayManager {
    private static final int CHARGE = 30;
    private static final int END = CHARGE + CrimsonRaySpell.DURATION_TICKS;
    private static final double SKY_HEIGHT = 48.0;
    private static final double SEGMENT = 24.0;
    private static final double BEAM_RADIUS = 1.9;      // hit radius around the beam line (beam is ~1.75 blocks wide)
    private static final double IMPACT_RADIUS = 4.0;    // extra blast radius where it lands
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static class Ray {
        final ServerLevel level;
        final UUID casterId;
        final Vec3 origin;
        Vec3 end;
        int age = 0;

        Ray(ServerLevel level, LivingEntity caster) {
            this.level = level;
            this.casterId = caster.getUUID();
            double y = Math.min(caster.getY() + SKY_HEIGHT, level.getMaxBuildHeight() - 3);
            this.origin = new Vec3(caster.getX(), y, caster.getZ());
            this.end = aim(level, caster);
        }
    }

    private static final List<Ray> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster) {
        ACTIVE.add(new Ray(level, caster));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Ray> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Ray r = it.next();
            Entity e = r.level.getEntity(r.casterId);
            LivingEntity caster = (e instanceof LivingEntity le && le.isAlive()) ? le : null;
            if (caster == null) { // no caster, no ray
                it.remove();
                continue;
            }
            boolean done;
            try {
                done = tick(r, caster);
            } catch (Exception ex) {
                LOGGER.error("Crimson Ray failed, cancelling it", ex);
                done = true;
            }
            r.age++;
            if (done) it.remove();
        }
    }

    private static boolean tick(Ray r, LivingEntity c) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        int t = r.age;

        if (t < CHARGE) {
            charge(r, c, t);
            return false;
        }
        int k = t - CHARGE;
        if (t >= END) {
            Vec3 e = r.end;
            FX.one(level, ModParticles.WC_GLOW.get(), e.add(0, 1, 0), new Vec3(10, 0, 0));
            FX.one(level, ModParticles.WC_RING.get(), e.add(0, 1, 0), new Vec3(14, 0, 0));
            FX.one(level, ModParticles.CW_GLINT.get(), r.origin, new Vec3(24, 0, 0));
            sound(level, e, SoundEvents.GENERIC_EXPLODE, 8f, 0.45f);
            return true;
        }

        // the beam end follows the aim, a little smoothed so it sweeps instead of snapping
        Vec3 target = aim(level, c);
        r.end = k == 0 ? target : r.end.lerp(target, 0.45);

        if (k == 0) {
            announce(level, r.end, Component.literal("The Ruler of Death's Crimson Ray").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            sound(level, r.origin, SoundEvents.WARDEN_SONIC_BOOM, 10f, 0.5f);
            sound(level, r.end, SoundEvents.LIGHTNING_BOLT_THUNDER, 10f, 0.5f);
        }
        beam(r);
        impactFx(r, rng, k);
        damage(r, c);
        if (k % 2 == 0 && JJKConfig.breaks(JJKConfig.CRIMSON_RAY_BREAKS_BLOCKS)) dig(level, r.end, c);
        if (k % 10 == 0) sound(level, r.end, SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.55f + rng.nextFloat() * 0.2f);
        if (k % 20 == 0) sound(level, r.origin, SoundEvents.BEACON_AMBIENT, 8f, 0.5f);
        return false;
    }

    private static void charge(Ray r, LivingEntity c, int t) {
        ServerLevel level = r.level;
        RandomSource rng = level.random;
        Vec3 p = c.position();
        c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 9, false, false, false));
        if (t == 0) {
            sound(level, p, SoundEvents.WARDEN_ROAR, 4f, 0.5f);
            FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(7, 0, 0));
        }
        // the glint high above blinks faster and bigger
        if (t % 3 == 0) {
            FX.one(level, ModParticles.CW_GLINT.get(), r.origin, new Vec3(6 + t * 0.55, 0, 0));
            sound(level, r.origin, SoundEvents.AMETHYST_BLOCK_CHIME, 4f, 1.2f + t * 0.03f);
        }
        if (t % 5 == 0) FX.one(level, ModParticles.WC_GLOW.get(), r.origin, new Vec3(2 + t * 0.2, 0, 0));
        // a faint thin guide line from the sky down to the caster's aim
        if (t > 14 && t % 2 == 0) {
            Vec3 a = aim(level, c);
            FX.one(level, ModParticles.WC_RING_FLAT.get(), a.add(0, 0.08, 0), new Vec3(2.5, 0, 0));
        }
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI * 2, d = 0.6 + rng.nextDouble() * 1.8;
            FX.one(level, ModParticles.RED_SPARK.get(), p.add(Math.cos(a) * d, 0.2, Math.sin(a) * d), new Vec3(0, 0.12 + rng.nextDouble() * 0.12, 0));
        }
    }

    /** The beam, resent every tick in short segments so it is drawn wherever you look. Body + a hot core inside it. */
    private static void beam(Ray r) {
        Vec3 total = r.end.subtract(r.origin);
        int segs = Math.max(1, (int) Math.ceil(total.length() / SEGMENT));
        Vec3 step = total.scale(1.0 / segs);
        Vec3 stretch = step.scale(1.03);
        for (int i = 0; i < segs; i++) {
            Vec3 a = r.origin.add(step.scale(i));
            FX.one(r.level, ModParticles.CR_BEAM.get(), a, stretch);
            FX.one(r.level, ModParticles.CR_CORE.get(), a, stretch);
        }
        // the glint stays burning at the source
        if (r.age % 2 == 0) FX.one(r.level, ModParticles.CW_GLINT.get(), r.origin, new Vec3(18 + r.level.random.nextDouble() * 6, 0, 0));
        if (r.age % 3 == 0) FX.one(r.level, ModParticles.WC_GLOW.get(), r.origin, new Vec3(7, 0, 0));
    }

    private static void impactFx(Ray r, RandomSource rng, int k) {
        ServerLevel level = r.level;
        Vec3 p = r.end;
        for (int i = 0; i < 3; i++) {
            Vec3 v = new Vec3((rng.nextDouble() - 0.5) * 1.4, 0.3 + rng.nextDouble() * 1.0, (rng.nextDouble() - 0.5) * 1.4);
            FX.one(level, ModParticles.RED_SPARK.get(), p, v);
        }
        FX.one(level, ModParticles.WC_GLOW.get(), p.add(0, 0.5, 0), new Vec3(4.5, 0, 0));
        if (k % 3 == 0) FX.one(level, ModParticles.RED_IMPACT.get(), p.add(0, 0.3, 0), new Vec3(2.5 + rng.nextDouble() * 1.5, 0, 0));
        if (k % 8 == 0) FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.1, 0), new Vec3(IMPACT_RADIUS * 1.3, 0, 0));
        if (k % 4 == 0) {
            BlockState st = level.getBlockState(BlockPos.containing(p.x, p.y - 0.6, p.z));
            if (!st.isAir() && st.getFluidState().isEmpty()) {
                FX.one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), p,
                        new Vec3((rng.nextDouble() - 0.5) * 0.9, 0.6 + rng.nextDouble() * 0.8, (rng.nextDouble() - 0.5) * 0.9));
            }
            FX.one(level, ModParticles.FUGA_SMOKE.get(), p.add(0, 0.5, 0), new Vec3(0, 0.14, 0));
        }
    }

    /** Percent of max health, every tick, to everything on the beam line or in the blast where it lands. */
    private static void damage(Ray r, LivingEntity c) {
        ServerLevel level = r.level;
        double pct = JJKConfig.CRIMSON_RAY_PERCENT_PER_TICK.get();
        if (pct <= 0) return;
        Vec3 a = r.origin, b = r.end;
        AABB box = new AABB(a, b).inflate(IMPACT_RADIUS + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == c || e.isAlliedTo(c)) continue;
            if (e.isSpectator() || (e instanceof Player p && p.getAbilities().invulnerable)) continue;
            AABB hb = e.getBoundingBox();
            boolean onBeam = hb.inflate(BEAM_RADIUS).clip(a, b).isPresent() || hb.inflate(BEAM_RADIUS).contains(a);
            boolean inBlast = hb.getCenter().distanceToSqr(b) <= IMPACT_RADIUS * IMPACT_RADIUS;
            if (!onBeam && !inBlast) continue;
            e.invulnerableTime = 0;
            var source = level.damageSources().indirectMagic(c, c);
            SpellDamage.percent(e, (float) (e.getMaxHealth() * pct), c, source);
            if (r.age % 3 == 0) {
                Vec3 ec = hb.getCenter();
                FX.one(level, ModParticles.WC_GLOW.get(), ec, new Vec3(1.6, 0, 0));
                FX.one(level, ModParticles.RED_SPARK.get(), ec, new Vec3((level.random.nextDouble() - 0.5) * 0.5, 0.2 + level.random.nextDouble() * 0.3, (level.random.nextDouble() - 0.5) * 0.5));
            }
        }
    }

    private static void dig(ServerLevel level, Vec3 end, LivingEntity c) {
        double R = 2.4;
        int ext = (int) Math.ceil(R);
        ServerPlayer player = c instanceof ServerPlayer sp ? sp : null;
        BlockPos o = BlockPos.containing(end.x, end.y - 0.8, end.z);
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

    /** Where the caster is aiming: first block or creature along the line of sight, up to 110 blocks. */
    private static Vec3 aim(ServerLevel level, LivingEntity c) {
        double range = CrimsonRaySpell.RANGE;
        Vec3 eye = c.getEyePosition();
        Vec3 look = c.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        Vec3 hit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        AABB box = c.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, c, eye, hit, box, e -> e.isAlive() && !e.isSpectator() && e.isPickable());
        if (eh != null) hit = eh.getLocation();
        return hit;
    }

    private static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void announce(ServerLevel level, Vec3 where, Component text) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(where) < 160 * 160) sp.displayClientMessage(text, true);
        }
    }
}
