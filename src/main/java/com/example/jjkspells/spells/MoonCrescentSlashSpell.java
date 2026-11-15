package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.*;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Moon Breath: Crescent Slash. One sweeping diagonal purple slash in front of the caster, with a scatter of small
 * blue crescent slashes riding along it. Cuts everything inside the arc; damage is a multiple of the damage of the
 * weapon in the caster's main hand.
 */
@AutoSpellConfig
public class MoonCrescentSlashSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "moon_crescent_slash");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(8)
            .setCooldownSeconds(6)
            .build();

    /** Damage = weapon damage * (DAMAGE_MULT + DAMAGE_MULT_PER_LEVEL * (level - 1)). */
    public static final float DAMAGE_MULT = 1.4f;
    public static final float DAMAGE_MULT_PER_LEVEL = 0.2f;
    /** How far the slash reaches at level 1, and how much more per level. */
    public static final double REACH = 4.5;
    public static final double REACH_PER_LEVEL = 0.35;
    /** Half the angle the slash spans, in radians (1.3 = 75 degrees to each side of where you look). */
    public static final float HALF_ARC = 1.3f;
    /** How far above or below the slash's plane things still get cut. */
    public static final double THICKNESS = 1.2;
    /** Tilt of the slash away from horizontal, in radians (0.7 = a clear diagonal). */
    public static final float TILT = 0.7f;

    private static final AnimationHolder SLASH_ANIMATION = SpellAnimLookup.find("SLASH_ANIMATION", "ONE_HANDED_HORIZONTAL_SWING_ANIMATION");

    public MoonCrescentSlashSpell() {
        this.manaCostPerLevel = 3;
        this.baseSpellPower = 5;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
        this.baseManaCost = 18;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SLASH_ANIMATION != null ? SLASH_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    public float getDamageMultiplier(int spellLevel) { return DAMAGE_MULT + DAMAGE_MULT_PER_LEVEL * (spellLevel - 1); }

    public double getReach(int spellLevel) { return REACH + REACH_PER_LEVEL * (spellLevel - 1); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float weapon = MoonFX.weaponDamage(caster);
        return List.of(
                Component.literal("Damage: " + String.format("%.1f", weapon * getDamageMultiplier(spellLevel))
                        + " (" + String.format("%.0f", getDamageMultiplier(spellLevel) * 100) + "% of your weapon's damage)"),
                Component.literal("Reach: " + String.format("%.1f", getReach(spellLevel)) + " blocks")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            slash(server, entity, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void slash(ServerLevel level, LivingEntity caster, int spellLevel) {
        RandomSource rng = level.random;
        Vec3 f = caster.getLookAngle().normalize();
        Vec3 c = caster.position().add(0, caster.getBbHeight() * 0.62, 0);

        // slash plane: contains the look direction f; v is tilted away from "up" by TILT
        Vec3 upPerp = new Vec3(0, 1, 0).subtract(f.scale(f.y));
        if (upPerp.lengthSqr() < 1.0E-4) upPerp = MartialFX.rightOf(caster).cross(f);
        upPerp = upPerp.normalize();
        Vec3 right = f.cross(upPerp).normalize();
        Vec3 v = upPerp.scale(Mth.cos(TILT)).add(right.scale(Mth.sin(TILT))).normalize();
        Vec3 n = f.cross(v).normalize();

        double reach = getReach(spellLevel);
        float r = (float) (reach * 0.85);
        float width = 0.55f + 0.04f * spellLevel;

        // ---- visuals: the big slash, a slimmer echo behind it, and the small crescents riding along
        MoonFX.ribbon(level, c, f, v, r, r * 1.08f, 0f, -HALF_ARC, HALF_ARC, width, 11);
        MoonFX.ribbon(level, c, f, v, r * 0.9f, r * 1.0f, 0.45f, -HALF_ARC * 0.8f, HALF_ARC * 0.9f, width * 0.45f, 9);
        MoonFX.crescentsAlong(level, c, f, v, r, r * 1.08f, 0f, -HALF_ARC, HALF_ARC, 9, 0.3f, 0.7f, 0.7f, f.scale(0.06));
        for (int i = 0; i < 6; i++) {
            float p = rng.nextFloat();
            Vec3 pt = MoonFX.curvePoint(c, f, v, r, r * 1.08f, 0f, -HALF_ARC, HALF_ARC, p);
            FX.one(level, ModParticles.MOON_SPARK.get(), pt, f.scale(0.1).add(MartialFX.randomDir(rng).scale(0.08)));
        }
        MartialFX.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.85f);
        MartialFX.sound(level, c.add(f.scale(reach * 0.6)), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9f, 1.4f);

        // ---- damage: everything inside the slab the slash sweeps through
        float damage = MoonFX.weaponDamage(caster) * getDamageMultiplier(spellLevel);
        AABB box = new AABB(c, c).inflate(reach + 1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            Vec3 o = e.getBoundingBox().getCenter().subtract(c);
            double a = o.dot(f), b = o.dot(v), nrm = o.dot(n);
            double dist = Math.sqrt(a * a + b * b);
            double half = e.getBbWidth() * 0.5;
            if (dist < 0.4 || dist - half > reach + 0.5) continue;
            if (Math.abs(nrm) > THICKNESS + e.getBbHeight() * 0.5) continue;
            if (Math.abs(Math.atan2(b, a)) > HALF_ARC + 0.15) continue;
            if (!caster.hasLineOfSight(e)) continue;

            e.invulnerableTime = 0;
            if (!DamageSources.applyDamage(e, damage, getDamageSource(caster))) continue;
            Vec3 away = e.position().subtract(caster.position());
            MartialFX.shove(e, away.x, away.z, 0.6, 0.25);
            MoonFX.hit(level, e.getBoundingBox().getCenter(), 0.9f);
        }
    }
}
