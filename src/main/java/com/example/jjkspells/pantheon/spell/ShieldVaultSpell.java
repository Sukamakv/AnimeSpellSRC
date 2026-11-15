package com.example.jjkspells.pantheon.spell;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.entity.VaultEntity;
import com.example.jjkspells.spells.SpellAnimLookup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/** Spell 2: Shield Vault - The Unyielding Assault (targeted leap, stun and melee combo). */
public class ShieldVaultSpell extends PanSpell {
    public ShieldVaultSpell() {
        super("shield_vault", SpellRarity.EPIC);
    }

    @Override
    protected int mana() {
        return PanConfig.VAULT.manaCost.get();
    }

    @Override
    protected double cooldownSeconds() {
        return PanConfig.VAULT.cooldownSeconds.get();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = SpellAnimLookup.find("ANIMATION_INSTANT_CAST", "SLASH_ANIMATION");
        return h != null ? h : super.getCastStartAnimation();
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        if (!super.checkPreCastConditions(level, spellLevel, entity, data)) {
            return false;
        }
        if (!level.isClientSide && VaultEntity.findTarget(level, entity) == null) {
            if (entity instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("No target in range"), true);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (!level.isClientSide) {
            try {
                VaultEntity v = VaultEntity.create(level, entity);
                if (v != null) {
                    level.addFreshEntity(v);
                }
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("Shield Vault failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var c = PanConfig.VAULT;
        return List.of(
                Component.literal("Leap up to " + Utils.stringTruncation(c.range.get(), 0) + " blocks onto your target and stun it"),
                Component.literal(c.strikes.get() + " spear strikes, then a shield bash: " + Utils.stringTruncation(c.finalFlat.get(), 0) + " + " + Utils.stringTruncation(c.finalPercent.get(), 0) + "% max HP"),
                Component.literal(Utils.stringTruncation(c.leapReduction.get(), 0) + "% damage reduction while leaping"));
    }
}
