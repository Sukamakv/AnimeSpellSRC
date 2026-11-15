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

/** 3. Black Hole: a sphere that drags enemies in for 2 s, then collapses. */
public class BlackHoleSpell extends OverlordSpell {
    public BlackHoleSpell() {
        super("black_hole", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, CastType.INSTANT, () -> OverlordConfig.BLACK_HOLE);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            var c = OverlordConfig.BLACK_HOLE;
            Vec3 p = Magic.aimPoint(level, entity, c.range.get());
            p = p.add(0, 1.6, 0);
            int collapse = 8 + ticks(c.pullSeconds.get()) + ticks(c.warningSeconds.get());
            level.addFreshEntity(OverlordFxEntity.create(level, OverlordFxEntity.HOLE, entity, p, collapse + 24, c.pullRadius.get().floatValue(), spellLevel).mark(collapse));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = OverlordConfig.BLACK_HOLE;
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(c.damage.get() * Magic.scale(spellLevel), 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(c.pullRadius.get(), 1)));
    }
}
