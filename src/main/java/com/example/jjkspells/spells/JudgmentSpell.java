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

import java.util.List;

/** Garen spins with his sword out, striking everything around him every half second. */
@AutoSpellConfig
public class JudgmentSpell extends GarenSpellBase {
    public JudgmentSpell() {
        super("judgment", CastType.INSTANT, SpellRarity.EPIC);
    }

    @Override protected int configMana() { return GarenConfig.JU_MANA.get(); }
    @Override protected double configCooldownSeconds() { return GarenConfig.JU_COOLDOWN.get(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Duration: " + GarenConfig.JU_DURATION.get() + "s, radius " + GarenConfig.JU_RADIUS.get() + " blocks"),
                Component.literal("Damage: " + (int) (GarenConfig.JU_DAMAGE_PERCENT.get() * 100) + "% weapon damage every " + GarenConfig.JU_INTERVAL_SECONDS.get() + "s"),
                Component.literal("Movement speed +" + (int) (GarenConfig.JU_SPEED_BONUS.get() * 100) + "% while spinning")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            GarenManager.judgment(server, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
