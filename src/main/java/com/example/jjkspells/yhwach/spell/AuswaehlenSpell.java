package com.example.jjkspells.yhwach.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yhwach.entity.AuswaehlenEntity;
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
 * Yhwach - Auswählen (the Quincy selection).
 * A colossal golden insignia fills the sky, up to 12 enemies are marked and struck by pillars of light, their energy
 * streams into a giant halo behind the caster and the halo collapses into the caster for a final reclamation hit.
 */
public class AuswaehlenSpell extends AbstractSpell {
    public static final String NAME = "yhwach_auswaehlen";

    // ---- tweak here ----
    public static final double RANGE = 70.0;
    public static final int MAX_TARGETS = 12;
    public static final double DIVINE_FLAT = 350.0;
    public static final double DIVINE_PERCENT = 20.0;
    public static final double RECLAIM_FLAT = 200.0;
    public static final double RECLAIM_PERCENT = 15.0;
    /** Caster gets +25% max_hp_damage (multiply-base modifier on the mod's attribute). */
    public static final double CASTER_MAX_HP_DAMAGE_BONUS = 0.25;
    /** Targets lose 15 percentage points of max_hp_resist. */
    public static final double TARGET_RESIST_LOSS = 0.15;
    public static final int BUFF_TICKS = 600;
    public static final int MANA_COST = 600;
    public static final double COOLDOWN_SECONDS = 150.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public AuswaehlenSpell() {
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
                level.addFreshEntity(AuswaehlenEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yhwach_auswaehlen failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Marks up to " + MAX_TARGETS + " enemies within " + (int) RANGE + " blocks"),
                Component.literal("Divine light: " + (int) DIVINE_FLAT + " + " + (int) DIVINE_PERCENT + "% max HP, then reclamation: "
                        + (int) RECLAIM_FLAT + " + " + (int) RECLAIM_PERCENT + "% max HP"),
                Component.literal("You: +" + (int) (CASTER_MAX_HP_DAMAGE_BONUS * 100) + "% max HP damage. Targets: -" + (int) (TARGET_RESIST_LOSS * 100) + "% max HP resist"));
    }
}
