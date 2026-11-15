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

import java.util.List;

/** 2. Cry of the Banshee: expanding scream that damages, weakens, slows and frightens. */
public class BansheeCrySpell extends OverlordSpell {
    public BansheeCrySpell() {
        super("cry_of_the_banshee", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.RARE, CastType.INSTANT, () -> OverlordConfig.BANSHEE);
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            var c = OverlordConfig.BANSHEE;
            level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.BANSHEE, entity, entity.position(), 40, c.radius.get().floatValue(), spellLevel));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.BANSHEE;
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(c.damage.get() * Magic.scale(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(c.radius.get(), 1)));
    }
}
