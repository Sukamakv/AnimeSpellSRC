package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** ROAD ROLLER DA!!: the ultimate. */
@AutoSpellConfig
public class DioRollerSpell extends DioSpellBase {
    public DioRollerSpell() {
        super("dio_roller", SpellRarity.LEGENDARY, 800, 345);
    }

    @Override
    protected void begin(ServerLevel level, LivingEntity caster) {
        DioManager.startRoller(level, caster, this);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Locks onto a target within 8 blocks and holds it helpless"),
                Component.literal("Crash 100 + 10%, crushing 6 damage per hit (15% max health total), final blow 100 + 10%"),
                Component.literal("Impact radius 11.5 blocks; bosses cannot be killed by the final blow")
        );
    }
}
