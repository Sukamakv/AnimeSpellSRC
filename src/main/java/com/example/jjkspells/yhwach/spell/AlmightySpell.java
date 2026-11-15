package com.example.jjkspells.yhwach.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yhwach.entity.AlmightyEntity;
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
 * Yhwach - The Almighty.
 * 20 s of living darkness around the caster (35 block circle that follows the caster), then it is gathered into an orb that
 * fires a giant beam for 8 s that trails the caster's aim.
 */
public class AlmightySpell extends AbstractSpell {
    public static final String NAME = "yhwach_the_almighty";

    // ---- tweak here ----
    public static final double ZONE_RADIUS = 35.0;
    public static final int ZONE_TICKS = 400;
    /** Zone damage every second, as a percentage of max health. */
    public static final double ZONE_PERCENT_PER_SECOND = 2.0;
    /** Blindness applied (and refreshed) to anything inside the zone. */
    public static final int BLIND_TICKS = 50;
    /** Heal when something dies inside the zone, as a fraction of the caster's max health. */
    public static final double CONSUME_HEAL = 0.10;
    public static final int WAVE_MIN_TICKS = 10;
    public static final int WAVE_MAX_TICKS = 40;
    public static final int WAVE_MIN_PILLARS = 3;
    public static final int WAVE_MAX_PILLARS = 6;
    public static final int PILLAR_MIN_BEAMS = 1;
    public static final int PILLAR_MAX_BEAMS = 3;
    public static final double PILLAR_RANGE = 70.0;
    public static final double PILLAR_FLAT = 100.0;
    public static final double PILLAR_PERCENT = 5.0;
    /** While active the caster cannot take a single hit above this fraction of their max health. */
    public static final double DAMAGE_CAP = 0.09;
    public static final int ORB_CHARGE_TICKS = 60;
    public static final int BEAM_TICKS = 160;
    public static final double BEAM_RADIUS = 5.5;
    public static final double BEAM_LENGTH = 110.0;
    public static final double BEAM_PERCENT_PER_SECOND = 8.0;
    /** The beam turns at this fraction of the caster's own turning speed (1 - 0.35). */
    public static final double BEAM_FOLLOW = 0.65;
    public static final int MANA_COST = 800;
    public static final double COOLDOWN_SECONDS = 240.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public AlmightySpell() {
        this.id = JJKSpells.id(NAME);
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(SpellRarity.LEGENDARY)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(0)
                .build();
        this.baseManaCost = 100;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return id;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public int getManaCost(int level) {
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) (MANA_COST * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(COOLDOWN_SECONDS * 20.0));
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(AlmightyEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yhwach_the_almighty failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal((int) ZONE_RADIUS + " block darkness for " + (ZONE_TICKS / 20) + "s: " + (int) ZONE_PERCENT_PER_SECOND + "% max HP/s, blindness, devoured dead heal you "
                        + (int) (CONSUME_HEAL * 100) + "%"),
                Component.literal("Eyed pillars fire black lasers (" + (int) PILLAR_FLAT + " + " + (int) PILLAR_PERCENT + "% max HP) within " + (int) PILLAR_RANGE + " blocks"),
                Component.literal("You cannot take a hit above " + (int) (DAMAGE_CAP * 100) + "% of your max HP"),
                Component.literal("Then a giant beam for " + (BEAM_TICKS / 20) + "s: " + (int) BEAM_PERCENT_PER_SECOND + "% max HP/s"));
    }
}
