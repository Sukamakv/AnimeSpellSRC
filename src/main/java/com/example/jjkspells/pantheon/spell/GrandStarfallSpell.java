package com.example.jjkspells.pantheon.spell;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.entity.GrandEntity;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Spell 5: Grand Starfall - The God Who Defied the Heavens (the ultimate). */
public class GrandStarfallSpell extends PanSpell {
    public GrandStarfallSpell() {
        super("grand_starfall", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return PanConfig.GRAND.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return PanConfig.GRAND.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(GrandEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Grand Starfall failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = PanConfig.GRAND;
        return List.of(
                Component.literal("Vanish into the sky and fall onto a point up to " + Utils.stringTruncation(c.range.get(), 0) + " blocks away"),
                Component.literal("Impact " + Utils.stringTruncation(c.impactFlat.get(), 0) + " + " + Utils.stringTruncation(c.impactPercent.get(), 0) + "% max HP, then six spears, a wave and a final detonation"),
                Component.literal(Utils.stringTruncation(c.bossBonusPercent.get(), 0) + "% more damage to bosses; " + c.killsForRefund.get() + " kills cut the cooldown"));
    }
}
