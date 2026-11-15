package com.example.jjkspells.garou.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.entity.CosmicRadiationEntity;
import com.example.jjkspells.garou.entity.CosmicRadiationHooks;
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
 * Garou (Cosmic form) - Cosmic Radiation.
 * While active, the caster's body emits cosmic radiation: every non-allied living thing within {@link #RADIUS}
 * blocks is irradiated (flat + max-health % damage every 2 s, slowed, healing reduced). Radiation lingers on a target
 * for at most {@link #LINGER_TICKS} ticks after it leaves the aura. All server logic is in {@link CosmicRadiationEntity}.
 */
public class GarouCosmicRadiationSpell extends AbstractSpell {
    public static final String NAME = "garou_cosmic_radiation";

    // ---- tuning -------------------------------------------------------------------------------------------
    public static final int MANA_COST = 600;
    public static final int COOLDOWN_TICKS = 2 * 60 * 20;      // 2 minutes
    public static final int AURA_TICKS = 25 * 20;              // 25 seconds of aura
    public static final int LINGER_TICKS = 10 * 20;            // radiation stays up to 10 s after leaving the aura
    public static final int PULSE_TICKS = 40;                  // damage every 2 seconds
    public static final int FIRST_PULSE_TICK = 20;             // first pulse one second after casting
    public static final double RADIUS = 8.0;
    public static final float FLAT_DAMAGE = 35f;
    public static final float MAX_HP_FRACTION = 0.02f;         // 2% max health, goes through SpellDamage.percent
    public static final double SPEED_REDUCTION = 0.15;         // -15% movement speed
    public static final float HEAL_REDUCTION = 0.20f;          // -20% healing received

    private final ResourceLocation id = JJKSpells.id(NAME);
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0)
            .build();

    public GarouCosmicRadiationSpell() {
        this.baseManaCost = MANA_COST;
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
        return COOLDOWN_TICKS;
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
        AnimationHolder h = SpellAnimLookup.find("CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        // one aura per caster at a time (the cooldown is longer than the aura anyway)
        return !CosmicRadiationHooks.hasAura(entity);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(CosmicRadiationEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("garou_cosmic_radiation failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Cosmic radiation pours from your body for " + (AURA_TICKS / 20) + "s"),
                Component.literal("Enemies within " + (int) RADIUS + " blocks take " + (int) FLAT_DAMAGE + " + "
                        + Math.round(MAX_HP_FRACTION * 100) + "% max health every " + (PULSE_TICKS / 20) + "s"),
                Component.literal("Irradiated enemies move " + Math.round(SPEED_REDUCTION * 100) + "% slower and receive "
                        + Math.round(HEAL_REDUCTION * 100) + "% less healing, for up to " + (LINGER_TICKS / 20)
                        + "s after leaving the aura"),
                Component.literal("Never affects you or your allies"));
    }
}
