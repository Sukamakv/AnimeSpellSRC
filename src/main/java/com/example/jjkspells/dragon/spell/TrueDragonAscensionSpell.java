package com.example.jjkspells.dragon.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dragon.config.DragonConfig;
import com.example.jjkspells.dragon.entity.AscensionFxEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** True Dragon Ascension: rise 12 blocks inside a golden circle, a beam of light, then the True Dragon Authority buff. */
public class TrueDragonAscensionSpell extends AbstractSpell {
    private final ResourceLocation id = JJKSpells.id("true_dragon_ascension");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0)
            .build();

    public TrueDragonAscensionSpell() {
        this.baseManaCost = 300;
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
        return Math.max(0, (int) (DragonConfig.SPELL.manaCost.get() * mult));
    }

    @Override
    public int getSpellCooldown() {
        return Math.max(0, (int) Math.round(DragonConfig.SPELL.cooldownSeconds.get() * 20.0));
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
        return !AscensionFxEntity.ASCENDING.contains(entity.getUUID());
    }

    private static double groundBelow(Level level, LivingEntity e) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(e.getX(), Math.floor(e.getY()) + 1, e.getZ());
        for (int i = 0; i < 40; i++) {
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return p.getY();
            }
            p.move(0, -1, 0);
        }
        return e.getY();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            level.addFreshEntity(AscensionFxEntity.create(level, entity, groundBelow(level, entity)));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var b = DragonConfig.BUFF;
        return List.of(
                Component.literal("Spell power +" + Utils.stringTruncation(b.spellPowerBonus.get() * 100, 0) + "%"),
                Component.literal("Attack damage +" + Utils.stringTruncation(b.attackDamageBonus.get() * 100, 0) + "%"),
                Component.translatable("ui.irons_spellbooks.duration", Utils.stringTruncation(b.durationSeconds.get(), 0) + "s"));
    }
}
