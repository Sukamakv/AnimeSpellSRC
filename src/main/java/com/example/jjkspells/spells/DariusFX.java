package com.example.jjkspells.spells;

import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerCooldowns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

/** Helpers shared by Darius's spells: boss checks, cooldown changes, crimson visuals, screen shake, muffling. */
public final class DariusFX {
    private DariusFX() {}

    /** Anything with at least this much max health counts as a boss even if no mod tagged it as one. */
    public static final float BOSS_HEALTH = 150f;

    /** Each spell level adds this fraction on top of the base numbers (level 1 is exactly the base numbers). */
    public static final float SCALE_PER_LEVEL = 0.08f;

    public static float levelScale(int spellLevel) {
        return 1f + SCALE_PER_LEVEL * (spellLevel - 1);
    }

    // ---- what kind of target ------------------------------------------------------------------

    /** Bosses: tagged forge:bosses (Wither, Ender Dragon, most modded bosses) or with a huge health pool. */
    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || e.getMaxHealth() >= BOSS_HEALTH;
    }

    /** Things that cannot be dragged around: bosses and anything with (nearly) full knockback resistance. */
    public static boolean isImmovable(LivingEntity e) {
        return isBoss(e) || e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.9;
    }

    // ---- cooldowns (players only: that is who has Iron's spell cooldowns) -----------------------

    /** Removes {@code fraction} (0..1) of the time left on a spell's cooldown. */
    public static void refundCooldown(LivingEntity caster, AbstractSpell spell, float fraction) {
        if (!(caster instanceof ServerPlayer player)) return;
        PlayerCooldowns cds = MagicData.getPlayerMagicData(player).getPlayerCooldowns();
        CooldownInstance ci = cds.getSpellCooldowns().get(spell.getSpellId());
        if (ci == null) return;
        ci.decrementBy((int) (ci.getCooldownRemaining() * Mth.clamp(fraction, 0f, 1f)));
        cds.syncToPlayer(player);
    }

    /** Ends a spell's cooldown immediately. */
    public static void resetCooldown(LivingEntity caster, AbstractSpell spell) {
        if (!(caster instanceof ServerPlayer player)) return;
        PlayerCooldowns cds = MagicData.getPlayerMagicData(player).getPlayerCooldowns();
        if (cds.removeCooldown(spell.getSpellId())) {
            cds.syncToPlayer(player);
        }
    }

    // ---- client effects delivered as hidden status effects ----------------------------------------

    /** Shakes the screen of every player within {@code radius} of {@code pos} for {@code ticks} ticks. */
    public static void shake(ServerLevel level, Vec3 pos, double radius, int ticks) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(pos) <= radius * radius) {
                p.addEffect(new MobEffectInstance(ModEffects.SHAKEN.get(), ticks, 0, false, false, false));
            }
        }
    }

    /** Muffles the world sounds heard by every player within {@code radius} of {@code pos} for {@code ticks} ticks. */
    public static void muffle(ServerLevel level, Vec3 pos, double radius, int ticks) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(pos) <= radius * radius) {
                p.addEffect(new MobEffectInstance(ModEffects.MUFFLED.get(), ticks, 0, false, false, false));
            }
        }
    }

    // ---- visuals ------------------------------------------------------------------------------

    /** Roughly where the axe head is: out to the caster's right, in front, at chest height. */
    public static Vec3 handPos(LivingEntity c) {
        return c.getEyePosition().add(MartialFX.rightOf(c).scale(0.45)).add(0, -0.55, 0).add(c.getLookAngle().scale(0.7));
    }

    /** A crimson (or other palette) slash ribbon. */
    /** A ribbon that lies flat in its plane (width inside the plane), e.g. a strip on the ground. */
    public static void flatRibbon(ServerLevel level, Vec3 center, Vec3 u, Vec3 v, float r0, float r1,
                                  float th0, float th1, float width, int life, int palette) {
        FX.one(level, MoonRibbonOptions.flat(u, v, r0, r1, th0, th1, width, life, palette), center, Vec3.ZERO);
    }

    public static void ribbon(ServerLevel level, Vec3 center, Vec3 u, Vec3 v, float r0, float r1, float lift,
                              float th0, float th1, float width, int life, int palette) {
        FX.one(level, MoonRibbonOptions.ribbon(u, v, r0, r1, lift, th0, th1, width, life, palette), center, Vec3.ZERO);
    }

    /** A ring of force and red dust expanding along the ground from {@code ground}. */
    public static void ring(ServerLevel level, Vec3 ground, float radius) {
        RandomSource rng = level.random;
        FX.one(level, ModParticles.DR_RING_FLAT.get(), ground.add(0, 0.07, 0), new Vec3(radius, 0, 0));
        int puffs = Mth.clamp((int) (radius * 4), 8, 24);
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < puffs; i++) {
            double a = phase + i * Math.PI * 2 / puffs;
            double sp = radius * 0.075;
            FX.one(level, ModParticles.DR_DUST.get(), ground.add(Math.cos(a) * 0.5, 0.15, Math.sin(a) * 0.5),
                    new Vec3(Math.cos(a) * sp, 0.04 + rng.nextDouble() * 0.04, Math.sin(a) * sp));
            if ((i & 1) == 0) {
                FX.one(level, ModParticles.DR_SPARK.get(), ground.add(Math.cos(a) * 0.5, 0.2, Math.sin(a) * 0.5),
                        new Vec3(Math.cos(a) * sp * 1.4, 0.08 + rng.nextDouble() * 0.08, Math.sin(a) * sp * 1.4));
            }
        }
    }

    /** A red impact: flash, starburst, sparks. */
    public static void redBurst(ServerLevel level, Vec3 p, float size) {
        RandomSource rng = level.random;
        FX.one(level, ModParticles.DR_GLOW.get(), p, new Vec3(size * 1.2, 0, 0));
        FX.one(level, ModParticles.DR_HIT.get(), p, new Vec3(size, 0, 0));
        int sparks = 4 + (int) (size * 4);
        for (int i = 0; i < sparks; i++) {
            FX.one(level, ModParticles.DR_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.25));
        }
    }

    /** A heavy metallic impact: steel sparks flying out and a flash. */
    public static void metalBurst(ServerLevel level, Vec3 p, float size) {
        RandomSource rng = level.random;
        FX.one(level, ModParticles.DR_GLOW.get(), p, new Vec3(size * 0.9, 0, 0));
        int sparks = 7 + (int) (size * 5);
        for (int i = 0; i < sparks; i++) {
            FX.one(level, ModParticles.DR_METAL.get(), p, MartialFX.randomDir(rng).scale(0.2 + rng.nextDouble() * 0.35));
        }
        for (int i = 0; i < 3; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.DR_LINE.get(), p.add(d.scale(0.3)), d.scale(size * (0.8 + rng.nextDouble() * 0.6)));
        }
    }

    /** Red energy being pulled in toward {@code target} from all around. */
    public static void gather(ServerLevel level, Vec3 target, double radius, int count) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.DR_GATHER.get(), target.add(d.scale(radius)), d.scale(-radius * 0.13));
        }
    }
}
