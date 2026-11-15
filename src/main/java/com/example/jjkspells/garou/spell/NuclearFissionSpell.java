package com.example.jjkspells.garou.spell;

import com.example.jjkspells.garou.entity.GarouTimeline;
import com.example.jjkspells.garou.entity.NuclearFissionEntity;
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

/**
 * Nuclear Fission: Garou (cosmic form) gathers cosmic energy in his fist, launches forward and punches. The impact
 * detonates as a nuclear explosion. Direct punch 300 + 12% max health, explosion 250 + 10% max health, radius 29,
 * cooldown 18 s, mana 180.
 */
public class NuclearFissionSpell extends GarouSpell {
    public NuclearFissionSpell() {
        super("nuclear_fission", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return GarouTimeline.MANA;
    }

    @Override
    protected double cooldownSeconds() {
        return GarouTimeline.COOLDOWN_SECONDS;
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
                level.addFreshEntity(NuclearFissionEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("nuclear_fission failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Pulls the arm back and gathers cosmic energy in the fist for 1s, then launches forward and delivers a single, devastating punch"),
                Component.literal("Direct punch: " + (int) GarouTimeline.PUNCH_FLAT + " + " + (int) GarouTimeline.PUNCH_PERCENT + "% max health"),
                Component.literal("Nuclear explosion: " + (int) GarouTimeline.BLAST_FLAT + " + " + (int) GarouTimeline.BLAST_PERCENT + "% max health within " + (int) GarouTimeline.RADIUS + " blocks"),
                Component.literal("Each target is hit by the punch once and by the explosion once. A final shockwave pushes enemies away"));
    }
}
