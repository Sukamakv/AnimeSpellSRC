package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Star Finger: Star Platinum's finger stretches out and pierces up to 4 enemies. */
@AutoSpellConfig
public class JotaroStarFingerSpell extends JotaroSpellBase {
    public JotaroStarFingerSpell() {
        super("jotaro_star_finger", SpellRarity.LEGENDARY, 70, 10);
    }

    @Override
    protected void begin(ServerLevel level, LivingEntity caster) {
        JotaroManager.startFinger(level, caster, this);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Range 20 blocks, pierces up to 4 enemies"),
                Component.literal("30 damage + 5% max health each")
        );
    }
}
