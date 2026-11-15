package com.example.jjkspells.frieren.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.FlammeMagicEntity;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Frieren - Flamme's Ultimate Magic (an original ancient high-level technique, not a named canon spell).
 * INSTANT in Iron's terms: the 2.5 s windup is part of the controller's own timeline (see FlammeMagicEntity).
 */
public class FrierenFlammesUltimateMagicSpell extends AbstractSpell {
    public static final String NAME = "frieren_flammes_ultimate_magic";

    // ---- tuning -------------------------------------------------------------------------------------------
    public static final int MANA_COST = 350;
    public static final int COOLDOWN_TICKS = 40 * 20;
    public static final double RANGE = 40.0;            // how far the target point may be
    public static final double AREA_RADIUS = 29.0;      // the whole area; the final explosion covers all of it
    public static final int LANCES = 6;                 // 5 falling lances + the final lance
    public static final double LANCE_RADIUS = 8.0;      // blast radius of one lance
    public static final float LANCE_FLAT = 100f, LANCE_PERCENT = 0.04f;
    public static final float FINAL_FLAT = 250f, FINAL_PERCENT = 0.10f;

    // ---- timeline (ticks since the circle appeared) ----
    public static final int WINDUP = 50;                // 2.5 s: hand raised, circles, sphere
    public static final int LANCE_INTERVAL = 7;         // rapid succession
    public static final int FINAL_DELAY = 16;           // pause before the final lance
    public static final int FLIGHT = 5;                 // launch -> impact
    public static final int SPHERE_HEIGHT = 34;         // sphere above the target point
    public static final int END = 170;                  // controller removed (visual afterglow)

    private final ResourceLocation id = JJKSpells.id(NAME);
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0)
            .build();

    public FrierenFlammesUltimateMagicSpell() {
        this.baseManaCost = MANA_COST;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override public ResourceLocation getSpellResource() { return id; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public int getManaCost(int level) {
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) (MANA_COST * mult));
    }

    @Override public int getSpellCooldown() { return COOLDOWN_TICKS; }
    @Override public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }
    @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

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
                level.addFreshEntity(FlammeMagicEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("frieren_flammes_ultimate_magic failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("A huge circle opens beneath you; after " + (WINDUP / 20.0) + "s of preparation a sphere above the target (range " + (int) RANGE + ") rains down " + (LANCES - 1) + " lances over " + (int) AREA_RADIUS + " blocks"),
                Component.literal("Each lance (" + (int) LANCE_RADIUS + " block blast): " + (int) LANCE_FLAT + " + " + Math.round(LANCE_PERCENT * 100) + "% max health"),
                Component.literal("The final lance explodes across the whole area: " + (int) FINAL_FLAT + " + " + Math.round(FINAL_PERCENT * 100) + "% max health"),
                Component.literal("Never affects you or your allies"));
    }
}
