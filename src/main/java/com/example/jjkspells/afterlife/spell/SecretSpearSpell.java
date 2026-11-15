package com.example.jjkspells.afterlife.spell;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.entity.SpearDashEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Spell 2: Secret Spear of Wangsheng - Blazing Spirit Thrust (piercing dash). */
public class SecretSpearSpell extends AfterlifeSpell {
    public SecretSpearSpell() {
        super("secret_spear_of_wangsheng", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return AfterlifeConfig.THRUST.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return AfterlifeConfig.THRUST.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "OVERHEAD_MELEE_SWING_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                SpearDashEntity.launch(level, entity);
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Secret Spear launch failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = AfterlifeConfig.THRUST;
        return List.of(
                Component.literal("Dash up to " + Utils.stringTruncation(c.range.get(), 0) + " blocks, piercing every enemy"),
                Component.literal(Utils.stringTruncation(c.flat.get(), 0) + " + " + Utils.stringTruncation(c.percent.get(), 1) + "% max HP damage"),
                Component.literal("Detonates existing Blood Blossoms"));
    }
}
