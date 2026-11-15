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

/** Garen's overhead lunge: 500% weapon damage, brief stun, and an empowered next attack. */
@AutoSpellConfig
public class DecisiveStrikeSpell extends GarenSpellBase {
    public DecisiveStrikeSpell() {
        super("decisive_strike", CastType.INSTANT, SpellRarity.EPIC);
    }

    @Override protected int configMana() { return GarenConfig.DS_MANA.get(); }
    @Override protected double configCooldownSeconds() { return GarenConfig.DS_COOLDOWN.get(); }


    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Lunge: " + GarenConfig.DS_LUNGE_BLOCKS.get().intValue() + " blocks"),
                Component.literal("Damage: " + (int) (GarenConfig.DS_DAMAGE_PERCENT.get() * 100) + "% of your weapon damage"),
                Component.literal("Stun: " + GarenConfig.DS_STUN_SECONDS.get() + "s, removes your slowness"),
                Component.literal("Your next melee hit deals +" + (int) (GarenConfig.DS_EMPOWER_PERCENT.get() * 100) + "% weapon damage")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            GarenManager.decisiveStrike(server, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
