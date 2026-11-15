package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** MUDAMUDAMUDA!: The World throws 70 punches in 4 seconds. */
@AutoSpellConfig
public class DioMudaSpell extends DioSpellBase {
    public DioMudaSpell() {
        super("dio_muda", SpellRarity.LEGENDARY, 60, 10);
    }

    @Override
    protected void begin(ServerLevel level, LivingEntity caster) {
        DioManager.startBarrage(level, caster, this);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("70 punches in 4 seconds: 6 damage + 0.25% max health each"),
                Component.literal("Last punch: +60 damage, +1% max health, launches the target"),
                Component.literal("Slowness III while The World barrages")
        );
    }
}
