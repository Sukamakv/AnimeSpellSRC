package com.example.jjkspells.afterlife.spell;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.entity.FuneralEntity;
import com.example.jjkspells.afterlife.util.Util;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Spell 4: Funeral of a Thousand Butterflies (5-phase ultimate). */
public class FuneralSpell extends AfterlifeSpell {
    public FuneralSpell() {
        super("funeral_of_a_thousand_butterflies", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AfterlifeConfig.FUNERAL.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AfterlifeConfig.FUNERAL.cooldownSeconds.get();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        return !Util.LOCKED.contains(entity.getUUID());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            level.addFreshEntity(FuneralEntity.create(level, entity));
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AfterlifeConfig.FUNERAL;
        return List.of(
                Component.literal("A 7 second ceremony: " + c.strikes.get() + " spear strikes, a spirit gate, a giant ghost"),
                Component.literal("Final explosion: " + Utils.stringTruncation(c.boomFlat.get(), 0) + " + " + Utils.stringTruncation(c.boomPercent.get(), 0) + "% max HP in " + Utils.stringTruncation(c.boomRadius.get(), 0) + " blocks"),
                Component.literal("Below " + Utils.stringTruncation(c.crimsonThresholdPercent.get(), 0) + "% HP: Crimson Funeral (stronger)"));
    }
}
