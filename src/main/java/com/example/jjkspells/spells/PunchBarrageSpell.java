package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.effect.StunEvents;
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
 * Barrage of Punches. A channelled spell: while the caster holds the cast they throw punch after punch at whatever
 * is in front of them (wherever they are facing, up and down included), ten punches a second. Every punch deals the
 * same small damage, and the barrage pins its victims in place instead of knocking them away.
 */
@AutoSpellConfig
public class PunchBarrageSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "punch_barrage");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(8)
            .setCooldownSeconds(10)
            .build();

    /** One punch every this many ticks (2 = ten punches a second). */
    public static final int HIT_INTERVAL = 2;
    /** Damage per punch = DAMAGE_BASE + spell power * DAMAGE_FACTOR. */
    public static final float DAMAGE_BASE = 0.4f;
    public static final float DAMAGE_FACTOR = 0.14f;
    /** How far in front of the caster the punches land, and how wide the barrage is to each side. */
    public static final double REACH = 3.6;
    public static final double HALF_WIDTH = 1.1;
    /** At most this many enemies are hit by one punch. */
    public static final int MAX_TARGETS = 6;

    public PunchBarrageSpell() {
        this.manaCostPerLevel = 2;
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 1;
        this.castTime = 50;
        this.baseManaCost = 8;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.CONTINUOUS; }

    /** Channel time in ticks: 2.5 seconds at level 1, plus half a second per level. */
    @Override
    public int getCastTime(int spellLevel) { return 40 + 10 * spellLevel; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.PLAYER_ATTACK_SWEEP); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return DAMAGE_BASE + getSpellPower(spellLevel, caster) * DAMAGE_FACTOR;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Punches: " + (20 / HIT_INTERVAL) + " per second, in front of you"),
                Component.literal("Damage per punch: " + String.format("%.1f", getDamage(spellLevel, caster))),
                Component.literal("Hold to channel for " + String.format("%.1f", getCastTime(spellLevel) / 20f) + "s")
        );
    }

    /** Runs every tick while the caster channels. */
    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel server)) return;
        if (StunEvents.isStunned(entity)) return;
        if (entity.tickCount % HIT_INTERVAL != 0) return;

        boolean rightFist = ((entity.tickCount / HIT_INTERVAL) & 1) == 0;
        Vec3 look = entity.getLookAngle();
        Vec3 eye = entity.getEyePosition();
        Vec3 shoulder = eye.add(MartialFX.rightOf(entity).scale(rightFist ? 0.35 : -0.35)).add(0, -0.35, 0);

        // the fist flying out of the caster, with a streak behind it
        Vec3 fistMid = shoulder.add(look.scale(REACH * 0.45));
        Vec3 fistEnd = shoulder.add(look.scale(REACH * 0.85));
        MartialFX.fist(server, fistMid, 0.34f);
        MartialFX.fist(server, fistEnd, 0.46f);
        MartialFX.streak(server, shoulder.add(look.scale(0.6)), fistEnd);

        float dmg = getDamage(spellLevel, entity);
        int hits = 0;
        for (LivingEntity target : MartialFX.inFront(server, entity, eye, look, REACH, HALF_WIDTH)) {
            if (hits >= MAX_TARGETS) break;
            Vec3 before = target.getDeltaMovement();
            target.invulnerableTime = 0;
            if (!DamageSources.applyDamage(target, dmg, getDamageSource(entity))) continue;
            hits++;
            // pin them: cancel the usual knockback so the target stays in reach, nudge them toward the caster's fists
            target.setDeltaMovement(before.x * 0.3 + look.x * 0.07, before.y, before.z * 0.3 + look.z * 0.07);
            target.hurtMarked = true;

            Vec3 c = target.getBoundingBox().getCenter();
            Vec3 p = c.subtract(look.scale(target.getBbWidth() * 0.45))
                    .add((server.random.nextDouble() - 0.5) * 0.4, (server.random.nextDouble() - 0.5) * 0.4, (server.random.nextDouble() - 0.5) * 0.4);
            MartialFX.burst(server, p, 0.55f);
        }

        if (hits > 0) {
            MartialFX.sound(server, eye.add(look.scale(2)), SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 0.85f + server.random.nextFloat() * 0.45f);
        } else if (((entity.tickCount / HIT_INTERVAL) & 1) == 0) {
            MartialFX.sound(server, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.3f + server.random.nextFloat() * 0.3f);
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        // Iron's calls this every 10 ticks while channelling (that is when the mana is paid); the punches are in onServerCastTick.
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
