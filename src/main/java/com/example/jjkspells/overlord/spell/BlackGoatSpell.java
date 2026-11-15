package com.example.jjkspells.overlord.spell;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.entity.OverlordFxEntity;
import com.example.jjkspells.overlord.util.Magic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 7. Ia Shub-Niggurath - Black Goat Sacrifice. */
public class BlackGoatSpell extends OverlordSpell {
    public BlackGoatSpell() {
        super("black_goat_sacrifice", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, CastType.LONG, () -> OverlordConfig.GOAT);
    }

    @Override
    public int getCastTime(int spellLevel) {
        return ticks(OverlordConfig.GOAT.castTimeSeconds.get());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            var c = OverlordConfig.GOAT;
            int charge = ticks(c.chargeSeconds.get());
            Vec3 p = Magic.aimPoint(level, entity, c.range.get());
            p = new Vec3(p.x, Magic.groundY(level, p.x, p.y, p.z), p.z);
            int life = ticks(Math.max(c.minDurationSeconds.get(), c.maxDurationSeconds.get()));
            level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.GOAT, entity, p, charge + life + 60, 7.0f, spellLevel).mark(charge).lock(charge));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.GOAT;
        float s = Magic.scale(spellLevel);
        return List.of(
                Component.literal("Black Goat HP: " + Utils.stringTruncation(c.goatHealth.get() * s * com.example.jjkspells.overlord.entity.GoatBase.HEALTH_MULT, 0) + ", Young: " + c.minOffspring.get() + "-" + c.maxOffspring.get() + " x " + Utils.stringTruncation(c.offspringHealth.get() * s * com.example.jjkspells.overlord.entity.GoatBase.HEALTH_MULT, 0) + " HP"),
                Component.literal("Summons hit 9x harder and run 1.4x faster. You take 25% less damage while they live"),
                Component.translatable("ui.irons_spellbooks.duration", Utils.stringTruncation(c.minDurationSeconds.get(), 0) + "-" + Utils.stringTruncation(c.maxDurationSeconds.get(), 0) + "s"));
    }
}
