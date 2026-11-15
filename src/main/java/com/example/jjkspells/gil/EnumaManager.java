package com.example.jjkspells.gil;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.entity.EaFxEntity;
import com.example.jjkspells.spells.DariusFX;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.GoldTarget;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ea: Enuma Elish (12 s).
 *   0-100    Ea is drawn and awakens: three rings turn around it
 *   100      fired: a spatial rupture travels the line for 40 ticks (damages everything on the path)
 *   140      impact: primary blast, central zone, distortion field, the Rupture of Creation zone appears
 *   140-220  the sphere compresses; the caster is almost untouchable (final 5 s)
 *   180-220  everything within the pull radius is dragged toward the centre
 *   220      reality collapses
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class EnumaManager {
    private static final int FIRE = EaFxEntity.FIRE;
    private static final int IMPACT = EaFxEntity.FIRE + EaFxEntity.TRAVEL;
    private static final int PULL = 180, FINAL = 220, END = 240;

    private static final class Session {
        final ServerLevel level;
        final UUID casterId;
        UUID targetId;
        Vec3 aim;
        Vec3 dir = new Vec3(0, 0, 1);
        Vec3 from = Vec3.ZERO;
        Vec3 impact = Vec3.ZERO;
        float len;
        EaFxEntity fx;
        final Set<UUID> pathHit = new HashSet<>();
        final Set<UUID> bosses = new HashSet<>();
        boolean supremacyGiven;
        int age;
        int ruptureEnd;

        Session(ServerLevel level, LivingEntity c) {
            this.level = level;
            this.casterId = c.getUUID();
        }
    }

    private static final List<Session> ACTIVE = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster) {
        Session s = new Session(level, caster);
        LivingEntity t = GilDmg.target(level, caster, GilConfig.EA.range.get());
        s.targetId = t == null ? null : t.getUUID();
        s.aim = t != null ? t.getBoundingBox().getCenter() : GilDmg.aim(level, caster, GilConfig.EA.range.get());
        ACTIVE.add(s);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Session> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            Entity e = s.level.getEntity(s.casterId);
            if (!(e instanceof LivingEntity c) || !c.isAlive()) {
                end(s, null);
                it.remove();
                continue;
            }
            boolean done;
            try {
                done = tick(s, c);
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getLogger().error("Enuma Elish failed", ex);
                done = true;
            }
            s.age++;
            if (done) {
                end(s, c);
                it.remove();
            }
        }
    }

    private static void end(Session s, LivingEntity c) {
        if (s.fx != null) s.fx.discard();
        if (c != null) GilState.clearGuard(c);
    }

    private static void aimFx(Session s, Vec3 from, Vec3 dir) {
        s.fx.setPos(from.x, from.y, from.z);
        // arrow convention (what EaFxRenderer expects): yaw = atan2(x, z), pitch positive upward
        float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
        float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
        s.fx.setYRot(yaw);
        s.fx.setXRot(pitch);
        s.fx.yRotO = yaw;
        s.fx.xRotO = pitch;
    }

    private static boolean tick(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        int t = s.age;
        var cfg = GilConfig.EA;

        if (t == 0) {
            s.fx = new EaFxEntity(level);
            Vec3 eye = c.getEyePosition();
            s.dir = s.aim.subtract(eye).normalize();
            aimFx(s, eye, s.dir);
            level.addFreshEntity(s.fx);
            GilUtil.sound(level, c.position(), ModSounds.EA_SPIN.get(), 3f, 0.8f);
            GilUtil.announce(c, Component.literal("Ea").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        if (t < FIRE) {
            // the caster can keep aiming until the moment it fires: Ea follows the crosshair
            Vec3 eye = c.getEyePosition();
            if (c instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
                s.aim = mob.getTarget().getBoundingBox().getCenter();
                s.dir = s.aim.subtract(eye).normalize();
            } else {
                s.dir = c.getLookAngle().normalize();
                s.aim = GoldTarget.aimPoint(level, c, cfg.range.get());
            }
            s.from = eye;
            aimFx(s, eye, s.dir);
            if (t % 8 == 0) GilUtil.sound(level, c.position(), ModSounds.EA_RUMBLE.get(), 1f, 0.6f + t * 0.008f);
            // the caster stands still while Ea awakens
            c.setDeltaMovement(c.getDeltaMovement().multiply(0.1, 1, 0.1));
            c.hurtMarked = true;
        }
        if (t == FIRE) {
            Vec3 eye = c.getEyePosition();
            if (!(c instanceof net.minecraft.world.entity.Mob)) s.dir = c.getLookAngle().normalize();
            s.from = eye;
            double dist = Mth.clamp(s.aim.subtract(eye).length(), 12, cfg.range.get());
            s.len = (float) dist;
            s.impact = eye.add(s.dir.scale(dist));
            aimFx(s, eye, s.dir);
            s.fx.setLen(s.len);
            GilUtil.sound(level, c.position(), ModSounds.EA_FIRE.get(), 4f, 0.8f);
            GilUtil.sound(level, s.impact, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.5f);
            GilUtil.announce(c, Component.literal("Enuma Elish").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }

        // ---- the spatial rupture along the line
        if (t >= FIRE && t <= IMPACT) {
            double front = s.len * (t - FIRE) / (double) EaFxEntity.TRAVEL;
            pathHits(s, c, front);
            if (t >= IMPACT - 10) distort(s, c);
        }
        if (t == IMPACT) impact(s, c);

        // ---- after the impact
        if (t > IMPACT) {
            distort(s, c);
            if (t % 20 == 0 && t < s.ruptureEnd) ruptureTick(s, c);
            if (t % 10 == 0 && t < s.ruptureEnd) ruptureSlow(s, c);
            if (t >= PULL && t < FINAL) pull(s, c, (t - PULL) / (double) (FINAL - PULL));
            if (t % 5 == 0 && t < FINAL) {
                double r = cfg.ruptureRadius.get() * (1.0 - (t - IMPACT) / (double) (FINAL - IMPACT) * 0.85);
                FX.one(level, ModParticles.WC_RING_FLAT.get(), s.impact.add(0, 0.2, 0), new Vec3(r * 0.5, 0, 0));
                FX.one(level, ModParticles.EA_GLOW.get(), s.impact.add(0, 1, 0), new Vec3(r * 0.3, 0, 0));
            }
        }
        if (t == FINAL) collapse(s, c);

        // dead bosses grant Supremacy
        if (!s.supremacyGiven && !s.bosses.isEmpty()) {
            for (UUID id : s.bosses) {
                Entity b = level.getEntity(id);
                if (b == null || !b.isAlive()) {
                    s.supremacyGiven = true;
                    GilState.supremacy(c, cfg.supremacySeconds.i() * 20);
                    try {
                        var spell = SpellRegistry.REGISTRY.get().getValue(new ResourceLocation(JJKSpells.MODID, "gate_of_babylon"));
                        if (spell != null) DariusFX.resetCooldown(c, spell);
                    } catch (RuntimeException ignored) {
                        // cooldown reset is a bonus only
                    }
                    GilUtil.announce(c, Component.literal("Supremacy").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    break;
                }
            }
        }
        return t >= Math.max(END, s.ruptureEnd);
    }

    private static void pathHits(Session s, LivingEntity c, double front) {
        ServerLevel level = s.level;
        var cfg = GilConfig.EA;
        double width = 7;
        Vec3 mid = s.from.add(s.dir.scale(front * 0.5));
        AABB box = new AABB(mid, mid).inflate(front * 0.5 + width);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!GilDmg.canHit(c, e) || s.pathHit.contains(e.getUUID())) continue;
            Vec3 rel = e.getBoundingBox().getCenter().subtract(s.from);
            double along = rel.dot(s.dir);
            if (along < 0 || along > front + 2) continue;
            double off = rel.subtract(s.dir.scale(along)).length();
            if (off > width) continue;
            s.pathHit.add(e.getUUID());
            GilDmg.hit(level, c, e, cfg.spatial.f(), cfg.spatialPercent.f());
            if (GilDmg.isBoss(e)) s.bosses.add(e.getUUID());
            FX.one(level, ModParticles.RED_IMPACT.get(), e.getBoundingBox().getCenter(), new Vec3(2.5, 0, 0));
        }
    }

    private static void distort(Session s, LivingEntity c) {
        if (s.age % 10 != 0) return;
        var cfg = GilConfig.EA;
        for (LivingEntity e : GilDmg.around(s.level, c, s.impact, cfg.distortRadius.get())) {
            int ticks = GilDmg.isBoss(e) ? cfg.bossSlowSeconds.i() * 20 : 30;
            GilDmg.effect(e, GilEffects.DISTORTION.get(), ticks, 0);
        }
    }

    private static void impact(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        var cfg = GilConfig.EA;
        Vec3 p = s.impact;
        s.ruptureEnd = IMPACT + cfg.ruptureSeconds.i() * 20;
        GilUtil.sound(level, p, SoundEvents.GENERIC_EXPLODE, 6f, 0.5f);
        GilUtil.sound(level, p, ModSounds.WEAPON_IMPACT.get(), 5f, 0.4f);
        FX.one(level, ModParticles.EA_GLOW.get(), p, new Vec3(cfg.primaryRadius.get() * 1.4, 0, 0));
        FX.one(level, ModParticles.WC_RING.get(), p, new Vec3(cfg.primaryRadius.get(), 0, 0));
        FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.2, 0), new Vec3(cfg.primaryRadius.get() * 1.1, 0, 0));
        for (LivingEntity e : GilDmg.around(level, c, p, cfg.primaryRadius.get())) {
            float m = 1f;
            if (e.getBoundingBox().getCenter().distanceTo(p) <= cfg.centerRadius.get()) m += cfg.centerBonus.f();
            boolean boss = GilDmg.isBoss(e);
            if (boss) {
                m += cfg.bossPrimary.f();
                s.bosses.add(e.getUUID());
                GilDmg.effect(e, GilEffects.DISTORTION.get(), cfg.bossSlowSeconds.i() * 20, 0);
            }
            GilDmg.hit(level, c, e, cfg.primary.f(), cfg.primaryPercent.f(), m, false, null);
            GilUtil.launch(e, p, 0.9, 0.7);
        }
        // the caster is nearly untouchable for the rest of it
        GilState.guard(c, END - IMPACT, cfg.guardReduction.f(), cfg.guardReduction.f(), true, true);
        crater(s, c);
    }

    private static void crater(Session s, LivingEntity c) {
        if (!(JJKConfig.SPELLS_BREAK_BLOCKS.get() && GilConfig.G.breaksBlocks.get())) return;
        ServerLevel level = s.level;
        int r = GilConfig.EA.craterRadius.i();
        BlockPos base = BlockPos.containing(s.impact);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r / 2; y++) {
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + y * y * 1.6 + z * z);
                    if (d > r) continue;
                    m.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    BlockState st = level.getBlockState(m);
                    if (st.isAir() || st.getDestroySpeed(level, m) < 0) continue;
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void ruptureTick(Session s, LivingEntity c) {
        var cfg = GilConfig.EA;
        for (LivingEntity e : GilDmg.around(s.level, c, s.impact, cfg.ruptureRadius2.get())) {
            GilDmg.hit(s.level, c, e, cfg.ruptureDps.f(), 0f);
        }
        GilUtil.sparks(s.level, s.impact.add(0, 0.5, 0), 6, 0.4);
    }

    private static void ruptureSlow(Session s, LivingEntity c) {
        var cfg = GilConfig.EA;
        for (LivingEntity e : GilDmg.around(s.level, c, s.impact, cfg.ruptureRadius2.get())) {
            GilDmg.effect(e, GilEffects.RUPTURE.get(), 30, 0);
        }
    }

    /** Everything inside the pull radius is dragged toward the centre; bosses only a little. */
    private static void pull(Session s, LivingEntity c, double k) {
        var cfg = GilConfig.EA;
        for (LivingEntity e : GilDmg.around(s.level, c, s.impact, cfg.ruptureRadius.get())) {
            Vec3 d = s.impact.subtract(e.position());
            double dist = d.length();
            if (dist < 2) continue;
            double pow = (0.05 + 0.18 * k) * (GilDmg.isBoss(e) ? 0.3 : 1.0);
            Vec3 v = e.getDeltaMovement().add(d.normalize().scale(pow));
            e.setDeltaMovement(v.x, Math.min(v.y, 0.3), v.z);
            e.hurtMarked = true;
        }
    }

    private static void collapse(Session s, LivingEntity c) {
        ServerLevel level = s.level;
        var cfg = GilConfig.EA;
        Vec3 p = s.impact;
        GilUtil.sound(level, p, SoundEvents.GENERIC_EXPLODE, 6f, 0.4f);
        GilUtil.sound(level, p, ModSounds.EA_FIRE.get(), 4f, 0.5f);
        FX.one(level, ModParticles.EA_GLOW.get(), p.add(0, 1, 0), new Vec3(cfg.collapseRadius.get() * 1.5, 0, 0));
        FX.one(level, ModParticles.WC_RING.get(), p.add(0, 1, 0), new Vec3(cfg.collapseRadius.get(), 0, 0));
        for (int i = 0; i < 3; i++) FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.2 + i * 0.1, 0), new Vec3(cfg.collapseRadius.get() * (0.5 + i * 0.3), 0, 0));
        for (LivingEntity e : GilDmg.around(level, c, p, cfg.collapseRadius.get())) {
            float m = 1f;
            if (GilDmg.isBoss(e)) {
                m += cfg.bossCollapse.f();
                s.bosses.add(e.getUUID());
            }
            GilDmg.hit(level, c, e, cfg.collapse.f(), cfg.collapsePercent.f(), m, false, null);
            GilUtil.launch(e, p, 0.6, 0.6);
        }
    }
}
