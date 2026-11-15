package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Sviga Laevi - Blade of the Burning Horizon. A colossal spectral greatsword grows from her blade and cleaves in a
 * 160 degree arc, then flings a 12 block wide crescent of fire 28 blocks that pierces everything. Its path erupts
 * 0.7 seconds later. She has Resistance III during the swing. Enemies under 30% HP take Molten Execution.
 */
@AutoSpellConfig
public class SvigaLaeviSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "sviga_laevi");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    private static final AnimationHolder CAST_ANIMATION = SpellAnimLookup.find("ONE_HANDED_HORIZONTAL_SWING_ANIMATION", "SLASH_ANIMATION");

    public SvigaLaeviSpell() {
        this.manaCostPerLevel = 12;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 150;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return CAST_ANIMATION != null ? CAST_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Sword swing (7 blocks, 160 degrees): 100 damage + 8%"),
                Component.literal("Crescent (28 blocks, 12 wide, pierces): 75 damage + 6%"),
                Component.literal("Delayed eruption along its path: 40 damage + 3%"),
                Component.literal("Molten Execution below 30% HP: +50 damage, Combustion 10s, crimson explosion (25 damage)")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            LaevatainManager.startSviga(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
