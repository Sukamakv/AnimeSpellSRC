package com.example.jjkspells.veldora.spell;

import com.example.jjkspells.veldora.config.VelConfig;
import com.example.jjkspells.veldora.entity.*;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public class StormDragonsAscensionSpell extends VelSpell {
    public StormDragonsAscensionSpell() {
        super("storm_dragons_ascension", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return VelConfig.ASCEND.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return VelConfig.ASCEND.cooldownSeconds.get();
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
                level.addFreshEntity(AscendEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("storm_dragons_ascension failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = VelConfig.ASCEND;
        return List.of(
                Component.literal("True dragon transformation: wings unfold and a shockwave hits "+n(c.radius)+" blocks for "+n(c.firstFlat)+" + "+n(c.firstPercent)+"%. Then for "+n(c.durationSeconds)+"s: +"+n(c.damageBonusPercent)+"% damage, +"+n(c.speedPercent)+"% speed, "+n(c.reductionPercent)+"% less damage, no knockback, +"+n(c.meleeFlat)+" melee damage"),
                Component.literal("Every "+ni(c.shockEvery)+"th melee hit releases a lightning shockwave ("+n(c.shockRadius)+" blocks, "+n(c.shockFlat)+" + "+n(c.shockPercent)+"%). Projectile damage is cut by "+n(c.projectileReducePercent)+"%, falls are harmless"),
                Component.literal("Defeat "+ni(c.overdriveKills)+" enemies: +"+n(c.overdriveSeconds)+"s and +"+n(c.overdriveDamagePercent)+"% damage. When it ends the dragon detonates within "+n(c.finalRadius)+" blocks for "+n(c.finalFlat)+" + "+n(c.finalPercent)+"%"));
    }
}