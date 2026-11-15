package com.example.jjkspells.overlord.spell;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.entity.OverlordFxEntity;
import com.example.jjkspells.overlord.util.Magic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 6. Reality Slash: instant piercing rift. */
public class RealitySlashSpell extends OverlordSpell {
    public RealitySlashSpell() {
        super("reality_slash", SchoolRegistry.ENDER_RESOURCE, SpellRarity.EPIC, CastType.INSTANT, () -> OverlordConfig.SLASH);
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SLASH_ANIMATION;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            var c = OverlordConfig.SLASH;
            Vec3 start = entity.getEyePosition().add(entity.getLookAngle().scale(0.6)).add(0, -0.15, 0);
            Vec3 dir = entity.getLookAngle();
            int windup = c.windupTicks.get();
            level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.SLASH, entity, start, windup + 18, c.width.get().floatValue(), spellLevel)
                    .mark(windup).direction(dir, c.range.get().floatValue()));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.SLASH;
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(c.baseDamage.get() * Magic.scale(spellLevel), 1) + " + " + Utils.stringTruncation(c.maxHealthFraction.get() * 100, 0) + "% max HP"),
                Component.literal("Ignores " + Utils.stringTruncation(c.armorIgnore.get() * 100, 0) + "% of armour"));
    }
}
