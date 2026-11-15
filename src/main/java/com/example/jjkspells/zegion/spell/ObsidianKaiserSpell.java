package com.example.jjkspells.zegion.spell;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.*;
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

public class ObsidianKaiserSpell extends ZegSpell {
    public ObsidianKaiserSpell() {
        super("obsidian_kaiser", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return ZegConfig.OBSIDIAN.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.OBSIDIAN.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
        return h != null ? h : super.getCastStartAnimation();
    }

    /** A second cast while the carapace stands releases the counterattack instead of paying for a new one. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        if (!level.isClientSide && ObsidianEntity.isActive(entity)) {
            ObsidianEntity.tryRelease(entity);
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, data);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                if (ObsidianEntity.tryRelease(entity)) {
                    return;
                }
                level.addFreshEntity(ObsidianEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("obsidian_kaiser failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.OBSIDIAN;
        return List.of(
                Component.literal("The first "+c.charges.get()+" attacks are negated, then "+t(c.reductionPercent.get())+"% damage reduction for "+t(c.durationSeconds.get())+"s"),
                Component.literal("Blocked projectiles return energy spears ("+t(c.reflectDamage.get())+" damage, up to "+c.maxReflections.get()+")"),
                Component.literal("Counterattack in "+t(c.radius.get())+" blocks: "+t(c.finalFlat.get())+" + "+t(c.finalPercent.get())+"% max HP plus stored damage; cast again to release early"));
    }
}
