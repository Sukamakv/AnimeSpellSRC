package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** The World: Golden Impact: a heavy lunging punch with a golden shockwave. */
@AutoSpellConfig
public class DioImpactSpell extends DioSpellBase {
    public DioImpactSpell() {
        super("dio_impact", SpellRarity.LEGENDARY, 100, 20);
    }

    @Override
    protected void begin(ServerLevel level, LivingEntity caster) {
        DioManager.startImpact(level, caster, this);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Direct hit: 100 damage + 10% max health"),
                Component.literal("Shockwave (4 blocks): 70 damage to everyone else")
        );
    }
}
