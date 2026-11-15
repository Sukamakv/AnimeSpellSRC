package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** The five Gilgamesh spells. Each one only starts its manager; the work is in the managers. */
public final class GilSpells {
    private GilSpells() {}

    abstract static class Base extends AbstractSpell {
        private final ResourceLocation id;
        private final DefaultConfig cfg;

        Base(String name, SpellRarity rarity, int cooldown, int cast, int mana) {
            this.id = new ResourceLocation(JJKSpells.MODID, name);
            this.cfg = new DefaultConfig().setMinRarity(rarity).setSchoolResource(SchoolRegistry.HOLY_RESOURCE).setMaxLevel(1).setCooldownSeconds(cooldown).build();
            this.manaCostPerLevel = 0;
            this.baseSpellPower = 10;
            this.spellPowerPerLevel = 0;
            this.castTime = cast;
            this.baseManaCost = mana;
        }

        @Override public ResourceLocation getSpellResource() { return id; }
        @Override public DefaultConfig getDefaultConfig() { return cfg; }
        @Override public CastType getCastType() { return CastType.LONG; }
        @Override public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }
        @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

        abstract void go(ServerLevel level, LivingEntity caster);

        abstract List<MutableComponent> info();

        @Override
        public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
            return info();
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
            com.example.jjkspells.spells.Debuffs.cast(this, entity);
            if (level instanceof ServerLevel server) go(server, entity);
            super.onCast(level, spellLevel, entity, castSource, data);
        }
    }

    private static MutableComponent l(String s) {
        return Component.literal(s);
    }

    @AutoSpellConfig
    public static class Gate extends Base {
        public Gate() { super("gate_of_babylon", SpellRarity.EPIC, 14, 12, 160); }
        @Override void go(ServerLevel level, LivingEntity c) { GateManager.start(level, c); }
        @Override List<MutableComponent> info() {
            return List.of(l("Eighteen portals fire a volley of treasures, then Royal Judgment falls"),
                    l("Weapon " + GilConfig.GATE.weapon.get() + ", heavy " + GilConfig.GATE.heavy.get() + ", judgment " + GilConfig.GATE.finalFlat.get() + " + " + (int) (GilConfig.GATE.finalPercent.get() * 100) + "% max health"),
                    l("King's Arrogance: stand still for bigger, faster, stronger"));
        }
    }

    @AutoSpellConfig
    public static class Enkidu extends Base {
        public Enkidu() { super("enkidu_chains_of_heaven", SpellRarity.EPIC, 22, 10, 200); }
        @Override void go(ServerLevel level, LivingEntity c) { EnkiduManager.start(level, c); }
        @Override List<MutableComponent> info() {
            return List.of(l("Chains of Heaven bind one target, up to " + GilConfig.ENKIDU.seconds.get() + " s"),
                    l("Divine targets are bound longer and take more damage from Gilgamesh"),
                    l("Bosses are only slowed"));
        }
    }

    @AutoSpellConfig
    public static class Vimana extends Base {
        public Vimana() { super("vimana", SpellRarity.LEGENDARY, 35, 20, 300); }
        @Override void go(ServerLevel level, LivingEntity c) { VimanaManager.start(level, c); }
        @Override List<MutableComponent> info() {
            return List.of(l("Ride the Vimana: bombard, Divine Cannon, then a ring of spears"),
                    l("Projectiles -60% and no knockback while riding"),
                    l("Magic hits can stagger the vessel"));
        }
    }

    @AutoSpellConfig
    public static class Merodach extends Base {
        public Merodach() { super("merodach", SpellRarity.LEGENDARY, 45, 16, 400); }
        @Override void go(ServerLevel level, LivingEntity c) { MerodachManager.start(level, c); }
        @Override List<MutableComponent> info() {
            return List.of(l("A great golden slash, then swords fall and detonate"),
                    l("Struck enemies are marked: Gate of Babylon deals more"),
                    l("Uninterrupted: a Perfect Release"));
        }
    }

    @AutoSpellConfig
    public static class Ea extends Base {
        public Ea() { super("enuma_elish", SpellRarity.LEGENDARY, 300, 20, 800); }
        @Override void go(ServerLevel level, LivingEntity c) { EnumaManager.start(level, c); }
        @Override List<MutableComponent> info() {
            return List.of(l("Ea awakens, rends space and collapses reality"),
                    l("Primary " + GilConfig.EA.primary.get() + " + " + (int) (GilConfig.EA.primaryPercent.get() * 100) + "% max health"),
                    l("Killing a boss with it grants Supremacy and resets Gate of Babylon"));
        }
    }
}
