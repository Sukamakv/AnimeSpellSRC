package com.example.jjkspells.prime;

import com.example.jjkspells.ModParticles;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared particle, sound and damage helpers of the PRIME spells. The particles are the ones the base Gojo spells already use, just in far greater numbers and sizes. */
public final class PrimeFx {
    private PrimeFx() {
    }

    public record Basis(Vec3 f, Vec3 r, Vec3 u) {
        public static Basis of(Vec3 look) {
            Vec3 f = look.normalize();
            Vec3 r = f.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0);
            r = r.normalize();
            Vec3 u = r.cross(f).normalize();
            return new Basis(f, r, u);
        }
    }

    public static Vec3 rnd(RandomSource rng) {
        return new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
    }

    public static Vec3 unit(RandomSource rng) {
        Vec3 v = rnd(rng);
        return v.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : v.normalize();
    }

    public static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    public static void one(ServerLevel l, ParticleOptions t, Vec3 p, Vec3 v) {
        FX.one(l, t, p, v);
    }

    /** Glow / ring style particles: x is the size. */
    public static void glow(ServerLevel l, ParticleOptions t, Vec3 p, float size) {
        FX.one(l, t, p, new Vec3(size, 0, 0));
    }

    /** Camera-facing (flat=false) or ground (flat=true) red ring. */
    public static void redRing(ServerLevel l, Vec3 p, float radius, boolean flat) {
        FX.one(l, ModParticles.RED_RING.get(), p, new Vec3(radius, flat ? 0 : 1, 0));
    }

    public static void line(ServerLevel l, ParticleOptions t, Vec3 from, Vec3 vec) {
        FX.one(l, t, from, vec);
    }

    /** A jagged bolt from a to b made of short line particles. */
    public static void bolt(ServerLevel l, ParticleOptions t, Vec3 a, Vec3 b, double jitter, int segs) {
        RandomSource rng = l.random;
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            Vec3 p = i == segs ? b : a.add(b.subtract(a).scale(i / (double) segs)).add(rnd(rng).scale(jitter));
            FX.one(l, t, prev, p.subtract(prev));
            prev = p;
        }
    }

    public static void sound(ServerLevel l, Vec3 p, SoundEvent s, float vol, float pitch) {
        l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Big title in the middle of the screen of everyone within 110 blocks. */
    public static void title(ServerLevel l, Vec3 at, Component title, Component sub) {
        for (ServerPlayer sp : l.players()) {
            if (sp.distanceToSqr(at) > 110 * 110) continue;
            sp.connection.send(new ClientboundSetTitlesAnimationPacket(4, 26, 12));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            sp.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    public static void announce(ServerLevel l, LivingEntity caster, Component text) {
        for (ServerPlayer sp : l.players()) {
            if (sp.distanceToSqr(caster) < 110 * 110) sp.displayClientMessage(text, true);
        }
    }

    /** Anything that may be hurt by the caster's spells. */
    public static boolean foe(LivingEntity caster, LivingEntity e) {
        if (e == caster || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (caster != null && (e.isAlliedTo(caster) || DamageSources.isFriendlyFireBetween(caster, e))) return false;
        return true;
    }

    /** flat damage through Iron's pipeline, then percent-of-max-health damage straight off the health bar. */
    public static void smite(ServerLevel l, AbstractSpell spell, LivingEntity caster, LivingEntity target, float flat, float percent) {
        float max = target.getMaxHealth();
        target.invulnerableTime = 0;
        if (flat > 0) DamageSources.applyDamage(target, flat, spell.getDamageSource(caster));
        if (percent > 0 && target.isAlive()) {
            SpellDamage.percent(target, max * percent, caster, l.damageSources().indirectMagic(caster, caster));
        }
    }

    /** The creature is being unmade: flakes of colour drift off its body. */
    public static void flakes(ServerLevel l, LivingEntity e, ParticleOptions wisp, int n) {
        RandomSource rng = l.random;
        var bb = e.getBoundingBox();
        for (int i = 0; i < n; i++) {
            Vec3 p = new Vec3(bb.minX + rng.nextDouble() * bb.getXsize(), bb.minY + rng.nextDouble() * bb.getYsize(), bb.minZ + rng.nextDouble() * bb.getZsize());
            one(l, wisp, p, rnd(rng).scale(0.07).add(0, 0.06, 0));
        }
    }

    public static Vec3 floor(ServerLevel l, Vec3 p) {
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos(net.minecraft.util.Mth.floor(p.x), net.minecraft.util.Mth.floor(p.y), net.minecraft.util.Mth.floor(p.z));
        for (int i = 0; i < 40 && !l.isOutsideBuildHeight(pos); i++) {
            if (!l.getBlockState(pos).getCollisionShape(l, pos).isEmpty()) return new Vec3(p.x, pos.getY() + 1.0, p.z);
            pos.move(net.minecraft.core.Direction.DOWN);
        }
        return p;
    }
}
