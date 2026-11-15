package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.effect.AatroxEvents;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * World Ender. Aatrox unleashes his true Darkin form: he roars, a shockwave of red energy bursts out of him, enormous
 * crimson wings unfold (drawn from particles) and a dark red aura wraps him while the world around him dims.
 *
 * For 12 seconds: +20% movement speed, +50% damage dealt, 20% of the damage he deals is healed back (capped at 35% of
 * his maximum health per activation), and his sword attacks are 20% bigger and leave larger trails and heavier impacts.
 */
@AutoSpellConfig
public class WorldEnderSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "world_ender");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(60)
            .build();

    /** How long the transformation lasts: 12 seconds. */
    public static final int DURATION_TICKS = 240;

    private static final AnimationHolder ROAR_ANIMATION = SpellAnimLookup.find("CAST_T_POSE", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");

    public WorldEnderSpell() {
        this.manaCostPerLevel = 12;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 100;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return ROAR_ANIMATION != null ? ROAR_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    /** Each spell level above 1 lengthens the transformation by 8%. */
    public int getDuration(int spellLevel) { return Math.round(DURATION_TICKS * DariusFX.levelScale(spellLevel)); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Lasts " + String.format("%.0f", getDuration(spellLevel) / 20f) + "s"),
                Component.literal("+20% movement speed, +50% damage dealt"),
                Component.literal("Heals 20% of damage dealt (up to " + (int) (AatroxEvents.HEAL_CAP_FRACTION * 100) + "% max health)"),
                Component.literal("Sword attacks are 20% bigger")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            transform(server, entity, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void transform(ServerLevel level, LivingEntity e, int spellLevel) {
        e.addEffect(new MobEffectInstance(ModEffects.WORLD_ENDER.get(), getDuration(spellLevel), spellLevel - 1, false, false, true));
        AatroxEvents.resetHealing(e);

        // the roar and the shockwave of red energy
        Vec3 pos = e.position();
        Vec3 chest = pos.add(0, e.getBbHeight() * 0.6, 0);
        DariusFX.ring(level, pos, 9f);
        FX.one(level, ModParticles.DR_SHOCK.get(), chest, new Vec3(7.0, 0, 0));
        FX.one(level, ModParticles.DR_GLOW.get(), chest, new Vec3(4.0, 0, 0));
        FX.one(level, ModParticles.DR_HIT.get(), chest, new Vec3(2.5, 0, 0));
        AatroxFX.smoke(level, pos.add(0, 1.0, 0), 18, 1.6);
        AatroxFX.blood(level, chest, 14, 0.35);
        for (int i = 0; i < 18; i++) {                                   // black-red particles spiralling around him
            double a = i * Math.PI * 2 / 18;
            FX.one(level, ModParticles.DR_SPARK.get(), pos.add(Math.cos(a) * 1.2, 0.2 + i * 0.1, Math.sin(a) * 1.2),
                    new Vec3(-Math.sin(a) * 0.22, 0.12, Math.cos(a) * 0.22));
        }
        AatroxFX.wings(level, e, 0.3f, 1.0f, 6);
        MartialFX.sound(level, pos, SoundEvents.RAVAGER_ROAR, 2.0f, 0.5f);
        MartialFX.sound(level, pos, SoundEvents.ENDER_DRAGON_GROWL, 1.6f, 0.6f);
        MartialFX.sound(level, pos, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.6f);
        DariusFX.shake(level, pos, 20, 8);
    }
}
