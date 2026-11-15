package com.example.jjkspells.malenia.spell;

import com.example.jjkspells.malenia.config.MelConfig;
import com.example.jjkspells.malenia.entity.*;
import com.example.jjkspells.malenia.util.Util;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ScarletBloomSpell extends MelSpell {
    public ScarletBloomSpell() {
        super("scarlet_bloom", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return MelConfig.BLOOM.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return MelConfig.BLOOM.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND","ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                Vec3 aim = Util.aimPoint(level, entity, MelConfig.BLOOM.range.get());
                Double g = Util.findGround(level, aim.x, aim.y + 1, aim.z);
                Vec3 p = g != null ? new Vec3(aim.x, g, aim.z) : aim;
                level.addFreshEntity(BloomEntity.create(level, entity, p));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("scarlet_bloom failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String n(MelConfig.Val<Double> v) {
        return io.redspace.ironsspellbooks.api.util.Utils.stringTruncation(v.get(), 0);
    }

    private static String ni(MelConfig.Val<Integer> v) {
        return String.valueOf(v.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = MelConfig.BLOOM;
        return List.of(
                Component.literal("Ascends and dives onto the marked ground (range "+n(c.range)+"): "+n(c.impactFlat)+" + "+n(c.impactPercent)+"% max HP within "+n(c.impactRadius)+" blocks, spreading Scarlet Rot"),
                Component.literal("A giant flower blooms to "+n(c.bloomRadius)+" blocks, exploding for "+n(c.bloomFlat)+" + "+n(c.bloomPercent)+"% max HP"),
                Component.literal("Leaves a Rotting Garden for "+n(c.gardenSeconds)+"s that rots and slows enemies inside; "+n(c.regenPerEnemy)+" health per enemy hit by the dive"));
    }
}
