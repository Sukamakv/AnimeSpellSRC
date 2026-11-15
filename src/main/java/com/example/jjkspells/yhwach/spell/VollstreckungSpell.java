package com.example.jjkspells.yhwach.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.spells.SpellAnimLookup;
import com.example.jjkspells.yhwach.entity.VollstreckungEntity;
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
 * Yhwach - Vollstreckung (the executing sword).
 * A colossal Reishi sword hangs 6 blocks above the target, follows it for 4 seconds, then falls.
 * Normal target: loses 50% max health for 30 s. Boss: takes 12% max health damage and loses 12% max-HP resistance for 30 s.
 */
public class VollstreckungSpell extends AbstractSpell {
    public static final String NAME = "yhwach_vollstreckung";

    // ---- tweak here ----
    public static final double RANGE = 48.0;
    public static final double HOVER_HEIGHT = 6.0;
    public static final int FOLLOW_TICKS = 80;
    /** Fraction of max health removed from a normal target. */
    public static final double MAX_HEALTH_LOSS = 0.50;
    /** Bosses: damage as fraction of max health, and the max-HP resistance removed (0.12 = 12 percentage points). */
    public static final double BOSS_DAMAGE = 0.12;
    public static final double BOSS_RESIST_LOSS = 0.12;
    public static final int DEBUFF_TICKS = 600;
    public static final int MANA_COST = 300;
    public static final double COOLDOWN_SECONDS = 45.0;
    // --------------------

    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;

    public VollstreckungSpell() {
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
                level.addFreshEntity(VollstreckungEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("yhwach_vollstreckung failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("A huge Reishi sword tracks your target for " + (FOLLOW_TICKS / 20) + " seconds, then falls"),
                Component.literal("Target loses " + (int) (MAX_HEALTH_LOSS * 100) + "% max HP for " + (DEBUFF_TICKS / 20) + "s"),
                Component.literal("Bosses: " + (int) (BOSS_DAMAGE * 100) + "% max HP damage and -" + (int) (BOSS_RESIST_LOSS * 100) + "% max HP resist"));
    }
}
