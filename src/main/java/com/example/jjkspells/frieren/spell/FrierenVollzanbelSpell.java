package com.example.jjkspells.frieren.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.VollzanbelEntity;
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
 * Frieren - Vollzanbel (massive fire magic).
 * Timeline (ticks): circle + small flame 0..START, flamethrower START..FLAME_END (7 s, follows the caster's aim),
 * flames collapse into the aimed point during CONVERGE, explosion at EXPLODE. All in {@link VollzanbelEntity}.
 */
public class FrierenVollzanbelSpell extends AbstractSpell {
    public static final String NAME = "frieren_vollzanbel";

    // ---- tuning -------------------------------------------------------------------------------------------
    public static final int MANA_COST = 450;
    public static final int COOLDOWN_TICKS = 55 * 20;
    public static final double RANGE = 45.0;
    public static final double EXPLOSION_RADIUS = 35.0;
    public static final float INITIAL_FLAT = 320f, INITIAL_PERCENT = 0.15f;   // the explosion
    public static final float BURN_FLAT = 45f, BURN_PERCENT = 0.02f;          // per second while burning
    public static final int BURN_TICKS = 5 * 20;                              // burning lasts this long after the last exposure
    public static final int BURN_PERIOD = 20;

    // ---- timeline ----
    public static final int START = 40;                       // flamethrower starts (circle/rune/small flame before)
    public static final int FLAME_TICKS = 7 * 20;             // 7 seconds
    public static final int FLAME_END = START + FLAME_TICKS;  // 180
    public static final int CONVERGE = 32;                    // flames pulled into one point
    public static final int EXPLODE = FLAME_END + CONVERGE;   // 212
    public static final int END = EXPLODE + 170;              // afterglow (smoke, pool, scorch fade)

    // ---- flame cone: radius at distance d = CONE_BASE + CONE_SPREAD * d ----
    public static final double CONE_BASE = 1.2, CONE_SPREAD = 0.19;

    private final ResourceLocation id = JJKSpells.id(NAME);
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0)
            .build();

    public FrierenVollzanbelSpell() {
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
                level.addFreshEntity(VollzanbelEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("frieren_vollzanbel failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("A circle opens before you; for " + (FLAME_TICKS / 20) + "s you pour a flamethrower inferno where you aim (range " + (int) RANGE + ")"),
                Component.literal("Burning: " + (int) BURN_FLAT + " + " + Math.round(BURN_PERCENT * 100) + "% max health per second"),
                Component.literal("Then the flames collapse into one point and explode (" + (int) EXPLOSION_RADIUS + " blocks): " + (int) INITIAL_FLAT + " + " + Math.round(INITIAL_PERCENT * 100) + "% max health"),
                Component.literal("Never affects you or your allies"));
    }
}
