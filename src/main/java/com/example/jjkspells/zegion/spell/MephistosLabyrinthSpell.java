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

public class MephistosLabyrinthSpell extends ZegSpell {
    public MephistosLabyrinthSpell() {
        super("mephistos_labyrinth", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return ZegConfig.LABYRINTH.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return ZegConfig.LABYRINTH.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "CHARGE_RAISED_HAND");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(LabyrinthEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("mephistos_labyrinth failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    private static String t(double v) {
        return Utils.stringTruncation(v, 0);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = ZegConfig.LABYRINTH;
        return List.of(
                Component.literal("Traps enemies in a "+t(c.radius.get())+" block mirrored labyrinth: "+t(c.openFlat.get())+" + "+t(c.openPercent.get())+"% max HP"),
                Component.literal("Dimensional blades cut for "+t(c.waveDamage.get())+" each; "+c.fractureStacks.get()+" cuts add "+t(c.soulDamage.get())+" soul damage"),
                Component.literal("The labyrinth collapses for "+t(c.finalFlat.get())+" + "+t(c.finalPercent.get())+"% max HP, ignoring part of armor and magic reduction"));
    }
}
