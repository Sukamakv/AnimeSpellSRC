package com.example.jjkspells.spells;

import com.example.jjkspells.GarenConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Garen braces behind his shield arm: heavy damage reduction and no knockback, strongest in the first half second. */
@AutoSpellConfig
public class CourageSpell extends GarenSpellBase {
    public CourageSpell() {
        super("courage", CastType.INSTANT, SpellRarity.EPIC);
    }

    @Override protected int configMana() { return GarenConfig.CO_MANA.get(); }
    @Override protected double configCooldownSeconds() { return GarenConfig.CO_COOLDOWN.get(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Duration: " + GarenConfig.CO_DURATION.get() + "s"),
                Component.literal("Damage taken: -" + (int) (GarenConfig.CO_REDUCTION.get() * 100) + "% (-" + (int) (GarenConfig.CO_EARLY_REDUCTION.get() * 100) + "% in the first " + GarenConfig.CO_EARLY_SECONDS.get() + "s)"),
                Component.literal("Knockback resistance while active")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            int ticks = Math.max(1, (int) Math.round(GarenConfig.CO_DURATION.get() * 20.0));
            entity.getPersistentData().putLong(GarenEvents.COURAGE_START_KEY, server.getGameTime());
            entity.removeEffect(ModEffects.COURAGE.get());
            entity.addEffect(new MobEffectInstance(ModEffects.COURAGE.get(), ticks, 0, false, false, true));

            // golden crest flashing over the chest, in front of the caster
            Vec3 look = entity.getLookAngle();
            Vec3 chest = entity.position().add(0, entity.getBbHeight() * 0.7, 0).add(look.x * 0.55, 0, look.z * 0.55);
            FX.emit(server, ModParticles.GOLD_CREST.get(), chest.x, chest.y, chest.z, 0, 0.95, 0, 0, 1.0);
            FX.emit(server, ModParticles.GG_GLOW.get(), chest.x, chest.y, chest.z, 0, 1.6, 0, 0, 1.0);
            FX.emit(server, ModParticles.GG_RING.get(), chest.x, chest.y, chest.z, 0, 1.4, 0, 0, 1.0);
            FX.emit(server, ModParticles.GG_RING_FLAT.get(), entity.getX(), entity.getY() + 0.1, entity.getZ(), 0, 2.4, 0, 0, 1.0);
            for (int i = 0; i < 18; i++) {
                double a = i / 18.0 * Math.PI * 2;
                FX.one(server, ModParticles.GOLD_MOTE.get(), entity.position().add(Math.cos(a) * 0.7, 0.2, Math.sin(a) * 0.7), new Vec3(0, 0.08, 0));
            }
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.4f, 0.8f);
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
