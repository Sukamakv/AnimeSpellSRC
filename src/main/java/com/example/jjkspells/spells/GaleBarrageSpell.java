package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.GaleConfig;
import com.example.jjkspells.entity.GaleBarrageEntity;
import com.example.jjkspells.ModSounds;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Gale Barrage: instant cast, 27 rapid sword strikes in 4 s, each 70% of the main-hand weapon's damage. */
public class GaleBarrageSpell extends AbstractSpell {
    private final ResourceLocation spellId = JJKSpells.id("gale_barrage");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(18)
            .build();

    public GaleBarrageSpell() {
        this.baseManaCost = 120;
        this.manaCostPerLevel = 40;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
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
        var c = GaleConfig.SPELL;
        double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
        return Math.max(0, (int) ((c.manaCostBase.get() + c.manaCostPerLevel.get() * (level - 1)) * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(GaleConfig.SPELL.cooldownSeconds.get() * 20.0));
    }

    /** Damage of the weapon in the caster's main hand (attribute value, which already includes the weapon's modifiers). */
    public static float weaponDamage(LivingEntity caster) {
        double d = caster.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (GaleConfig.SPELL.includeEnchantments.get()) {
            d += EnchantmentHelper.getDamageBonus(caster.getMainHandItem(), MobType.UNDEFINED);
        }
        return (float) d;
    }

    public static float fraction(int level) {
        var c = GaleConfig.SPELL;
        return (float) (c.weaponDamageFraction.get() + c.weaponFractionPerLevel.get() * (level - 1));
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
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (GaleConfig.SPELL.requireWeapon.get() && entity.getMainHandItem().isEmpty()) {
            if (entity instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("spell.jjk_spells.gale_barrage.need_weapon").withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            GaleBarrageEntity e = new GaleBarrageEntity(level, entity);
            e.setup(spellLevel);
            level.addFreshEntity(e);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.BARRAGE_START.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = GaleConfig.SPELL;
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(weaponDamage(caster) * fraction(spellLevel), 1) + " x" + c.hits.get()),
                Component.translatable("ui.irons_spellbooks.duration", Utils.stringTruncation(c.durationSeconds.get(), 1) + "s")
        );
    }
}
