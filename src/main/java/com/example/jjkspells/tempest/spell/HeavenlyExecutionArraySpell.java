package com.example.jjkspells.tempest.spell;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class HeavenlyExecutionArraySpell extends TemSpell {
    public HeavenlyExecutionArraySpell() {
        super("heavenly_execution_array", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return TemConfig.ARRAY.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return TemConfig.ARRAY.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(ArrayEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("heavenly_execution_array failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!level.isClientSide && TemConfig.ARRAY.requireSky.get() && !level.canSeeSky(entity.blockPosition().above((int) Math.ceil(entity.getBbHeight())))) {
            if (entity instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("Megiddo needs a clear view of the sky"), true);
            }
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = TemConfig.ARRAY;
        return List.of(
                Component.literal("Water lenses fill the sky above the aimed point (range "+n(c.range)+"; needs open sky unless the config says otherwise) and fire "+ni(c.thinBeams)+" thin solar beams of "+n(c.beamDamage)+" damage at enemies within "+n(c.radius)+" blocks"),
                Component.literal("Then "+ni(c.execBeams)+" concentrated beams, each on a different enemy, the strongest first: "+n(c.execFlat)+" + "+n(c.execPercent)+"%, ignoring "+n(c.armorIgnorePercent)+"% armor, +"+n(c.executeBonusPercent)+"% below "+n(c.executeThresholdPercent)+"% health"),
                Component.literal("Finally every lens converges: "+n(c.finalFlat)+" + "+n(c.finalPercent)+"% within "+n(c.finalRadius)+" blocks"));
    }
}