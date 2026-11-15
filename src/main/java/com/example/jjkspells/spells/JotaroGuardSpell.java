package com.example.jjkspells.spells;

import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Unbreakable Guard: block everything for 4 seconds, then return 40% of it. */
@AutoSpellConfig
public class JotaroGuardSpell extends JotaroSpellBase {
    public JotaroGuardSpell() {
        super("jotaro_guard", SpellRarity.LEGENDARY, 150, 40);
    }

    @Override
    protected void begin(ServerLevel level, LivingEntity caster) {
        JotaroManager.startGuard(level, caster, this);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("4 seconds: blocks every attack aimed at you, no knockback, you cannot move"),
                Component.literal("Then a ground smash returns 40% of the blocked damage to every enemy within 5 blocks"),
                Component.literal("Enemies caught are thrown outward")
        );
    }
}
