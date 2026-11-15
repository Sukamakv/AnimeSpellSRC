package com.example.jjkspells.garou.spell;

import com.example.jjkspells.garou.entity.AllLifeEntity;
import com.example.jjkspells.garou.entity.AllLifeTimeline;
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
 * All Life Eradication Fist: Garou's ultimate cosmic martial art. Stance, five strikes, a nuclear finisher, a gravity
 * collapse and a cosmic shockwave. Radius 40, cooldown 90 s, mana 700.
 */
public class AllLifeEradicationFistSpell extends GarouSpell {
    public AllLifeEradicationFistSpell() {
        super("all_life_eradication_fist", SpellRarity.LEGENDARY);
    }

    @Override
    protected int mana() {
        return AllLifeTimeline.MANA;
    }

    @Override
    protected double cooldownSeconds() {
        return AllLifeTimeline.COOLDOWN_SECONDS;
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
                level.addFreshEntity(AllLifeEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("all_life_eradication_fist failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("A cosmic stance for 2s, then a dash and five martial arts strikes: right punch, left punch, spinning kick, elbow and a final double fist"),
                Component.literal("Each strike: " + (int) AllLifeTimeline.STRIKE_FLAT + " + " + (int) AllLifeTimeline.STRIKE_PERCENT + "% max health"),
                Component.literal("Nuclear Fission finisher: " + (int) AllLifeTimeline.NUKE_FLAT + " + " + (int) AllLifeTimeline.NUKE_PERCENT + "% max health within " + (int) AllLifeTimeline.RADIUS + " blocks"),
                Component.literal("Gravity collapse: " + (int) AllLifeTimeline.COLLAPSE_FLAT + " + " + (int) AllLifeTimeline.COLLAPSE_PERCENT + "% max health, everything in range is pulled into the sphere first"),
                Component.literal("Final cosmic shockwave: " + (int) AllLifeTimeline.SHOCK_FLAT + " + " + (int) AllLifeTimeline.SHOCK_PERCENT + "% max health. Every target is hit once by each of these effects"));
    }
}
