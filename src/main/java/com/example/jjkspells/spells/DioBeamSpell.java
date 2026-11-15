package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Space Ripper Stingy Eyes: two crimson streams from DIO's eyes. */
@AutoSpellConfig
public class DioBeamSpell extends DioSpellBase {
    public DioBeamSpell() {
        super("dio_beam", SpellRarity.EPIC, 70, 15);
    }

    @Override
    protected void begin(ServerLevel level, LivingEntity caster) {
        DioManager.startBeam(level, caster, this);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("30 damage + 5% max health, pierces up to 4 targets"),
                Component.literal("Range: 20 blocks")
        );
    }
}
