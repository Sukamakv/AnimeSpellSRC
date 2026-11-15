package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Instantaneous Onslaught: a 16 block dash that punches everything in the path. */
@AutoSpellConfig
public class JotaroOnslaughtSpell extends JotaroSpellBase {
    public JotaroOnslaughtSpell() {
        super("jotaro_onslaught", SpellRarity.LEGENDARY, 300, 35);
    }

    @Override
    protected void begin(ServerLevel level, LivingEntity caster) {
        JotaroManager.startOnslaught(level, caster, this);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("16 block dash in about 0.2 seconds, up to 10 enemies"),
                Component.literal("240 damage + 10% max health to each, in 12 blows"),
                Component.literal("All damage lands a moment after the dash ends")
        );
    }
}
