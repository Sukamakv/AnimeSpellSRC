package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.effect.StunEffect;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Hard Strike. The caster steps into one heavy punch: everything right in front of them takes big damage, is thrown
 * away from the caster and is stunned for 1.4 seconds.
 */
@AutoSpellConfig
public class HardStrikeSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "hard_strike");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(6)
            .setCooldownSeconds(9)
            .build();

    /** Stun duration in ticks: 1.4 seconds. */
    public static final int STUN_TICKS = 28;
    /** Damage = DAMAGE_BASE + spell power * DAMAGE_FACTOR. */
    public static final float DAMAGE_BASE = 3f;
    public static final float DAMAGE_FACTOR = 0.9f;
    /** Reach in blocks at level 1 (+ REACH_PER_LEVEL for every level after), and half the width of the punch. */
    public static final double REACH = 3.8;
    public static final double REACH_PER_LEVEL = 0.15;
    public static final double HALF_WIDTH = 1.3;
    /** Knockback speed in blocks per tick at level 1 (+ KNOCKBACK_PER_LEVEL per level) and the hop that goes with it. */
    public static final double KNOCKBACK = 1.0;
    public static final double KNOCKBACK_PER_LEVEL = 0.05;
    public static final double LIFT = 0.35;

    public HardStrikeSpell() {
        this.manaCostPerLevel = 4;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 22;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return DAMAGE_BASE + getSpellPower(spellLevel, caster) * DAMAGE_FACTOR;
    }

    public double getReach(int spellLevel) { return REACH + REACH_PER_LEVEL * (spellLevel - 1); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Damage: " + String.format("%.1f", getDamage(spellLevel, caster))),
                Component.literal("Reach: " + String.format("%.1f", getReach(spellLevel)) + " blocks"),
                Component.literal("Knocks enemies away and stuns them for " + String.format("%.1f", STUN_TICKS / 20f) + "s")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            strike(server, entity, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void strike(ServerLevel level, LivingEntity caster, int spellLevel) {
        Vec3 look = caster.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        Vec3 eye = caster.getEyePosition();
        double reach = getReach(spellLevel);

        // step into the punch
        caster.setDeltaMovement(caster.getDeltaMovement().add(flat.scale(0.5)));
        caster.hurtMarked = true;

        // the fist flying out, with thick streaks behind it
        Vec3 shoulder = eye.add(MartialFX.rightOf(caster).scale(0.3)).add(0, -0.35, 0);
        MartialFX.fist(level, shoulder.add(look.scale(reach * 0.35)), 0.45f);
        MartialFX.fist(level, shoulder.add(look.scale(reach * 0.7)), 0.65f);
        MartialFX.fist(level, shoulder.add(look.scale(reach)), 0.95f);
        Vec3 side = MartialFX.rightOf(caster);
        for (int i = -1; i <= 1; i++) {
            Vec3 off = side.scale(0.25 * i).add(0, 0.12 * i, 0);
            MartialFX.heavyStreak(level, shoulder.add(look.scale(0.5)).add(off), shoulder.add(look.scale(reach)).add(off));
        }

        float dmg = getDamage(spellLevel, caster);
        double kb = KNOCKBACK + KNOCKBACK_PER_LEVEL * (spellLevel - 1);
        int hits = 0;
        for (LivingEntity target : MartialFX.inFront(level, caster, eye, look, reach, HALF_WIDTH)) {
            target.invulnerableTime = 0;
            if (!DamageSources.applyDamage(target, dmg, getDamageSource(caster))) continue;
            hits++;

            // thrown away from the punch: mostly the way the caster faces, a little straight out from them
            Vec3 radial = target.position().subtract(caster.position());
            Vec3 radialFlat = new Vec3(radial.x, 0, radial.z);
            radialFlat = radialFlat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : radialFlat.normalize();
            Vec3 dir = flat.scale(0.7).add(radialFlat.scale(0.3));
            MartialFX.shove(target, dir.x, dir.z, kb, LIFT);

            if (target.isAlive()) StunEffect.apply(target, STUN_TICKS);

            Vec3 c = target.getBoundingBox().getCenter();
            MartialFX.heavyBurst(level, c.subtract(look.scale(target.getBbWidth() * 0.4)), 1.25f);
        }

        if (hits > 0) {
            MartialFX.sound(level, eye.add(look.scale(2)), SoundEvents.IRON_GOLEM_ATTACK, 1.3f, 0.75f);
            MartialFX.sound(level, eye.add(look.scale(2)), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
        } else {
            MartialFX.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
        }
    }
}
