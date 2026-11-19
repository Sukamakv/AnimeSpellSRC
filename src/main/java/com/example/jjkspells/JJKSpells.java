package com.example.jjkspells;

import com.example.jjkspells.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(JJKSpells.MODID)
public class JJKSpells {
    public static final String MODID = "jjk_spells";

    @SuppressWarnings("removal")
    public JJKSpells() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, JJKConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StormConfig.SPEC, MODID + "-storm.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GaleConfig.SPEC, MODID + "-gale.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.gil.GilConfig.SPEC, MODID + "-gilgamesh.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.rimuru.config.RimConfig.SPEC, MODID + "-rimuru.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.diablo.config.DiaConfig.SPEC, MODID + "-diablo.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.tempest.config.TemConfig.SPEC, MODID + "-tempest.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.veldora.config.VelConfig.SPEC, MODID + "-veldora.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.velgrynd.config.VgrConfig.SPEC, MODID + "-velgrynd.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.zegion.config.ZegConfig.SPEC, MODID + "-zegion.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.shadow.config.ShdConfig.SPEC, MODID + "-shadow.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GarenConfig.SPEC, MODID + "-garen.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AzureConfig.SPEC, MODID + "-azure.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.overlord.config.OverlordConfig.SPEC, MODID + "-overlord.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.dragon.config.DragonConfig.SPEC, MODID + "-dragon.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.afterlife.config.AfterlifeConfig.SPEC, MODID + "-afterlife.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.pantheon.config.PanConfig.SPEC, MODID + "-pantheon.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.malphite.config.MalConfig.SPEC, MODID + "-malphite.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.learner.LearnerConfig.SPEC, MODID + "-skill_learner.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.relic.RelicConfig.SPEC, MODID + "-relics.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.enchant.FlameSlashConfig.SPEC, MODID + "-flame_slash.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.prime.PrimeConfig.SPEC, MODID + "-prime.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.garou.config.GarConfig.SPEC, MODID + "-garou.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BalanceConfig.SPEC, MODID + "-balance.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.example.jjkspells.capri.CapriConfig.SERVER_SPEC, MODID + "-capri.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.example.jjkspells.capri.CapriConfig.CLIENT_SPEC, MODID + "-capri-client.toml");
        com.example.jjkspells.capri.CapriRegistry.register(bus);
        ModAttributes.register(bus);
        ModNetwork.register();
        // Ainz Ooal Gown spells (ported from the Overlord addon)
        com.example.jjkspells.overlord.registry.ModSpells.init();
        com.example.jjkspells.overlord.registry.ModSounds.register(bus);
        com.example.jjkspells.overlord.registry.ModParticles.register(bus);
        com.example.jjkspells.overlord.registry.ModEffects.register(bus);
        com.example.jjkspells.overlord.registry.ModEntities.register(bus);
        // True Dragon Ascension (ported from the True Dragon addon)
        com.example.jjkspells.dragon.registry.ModSounds.register(bus);
        com.example.jjkspells.dragon.registry.ModParticles.register(bus);
        com.example.jjkspells.dragon.registry.ModEffects.register(bus);
        com.example.jjkspells.dragon.registry.ModEntities.register(bus);
        com.example.jjkspells.dragon.registry.ModSpells.register(bus);
        // Hu Tao: Guide to the Afterlife (ported from the Afterlife addon)
        com.example.jjkspells.afterlife.registry.ModSpells.init();
        com.example.jjkspells.afterlife.registry.ModSounds.register(bus);
        com.example.jjkspells.afterlife.registry.ModParticles.register(bus);
        com.example.jjkspells.afterlife.registry.ModEffects.register(bus);
        com.example.jjkspells.afterlife.registry.ModEntities.register(bus);

        // Pantheon: Comet Spear, Shield Vault, Aegis Storm, Starfall Execution, Grand Starfall
        com.example.jjkspells.pantheon.registry.ModSpells.init();
        com.example.jjkspells.pantheon.registry.ModSounds.register(bus);
        com.example.jjkspells.pantheon.registry.ModParticles.register(bus);
        com.example.jjkspells.pantheon.registry.ModEffects.register(bus);
        com.example.jjkspells.pantheon.registry.ModEntities.register(bus);
        com.example.jjkspells.pantheon.registry.ModItems.register(bus);
        // Malphite
        com.example.jjkspells.malphite.registry.ModSpells.init();
        com.example.jjkspells.malphite.registry.ModSounds.register(bus);
        com.example.jjkspells.malphite.registry.ModParticles.register(bus);
        com.example.jjkspells.malphite.registry.ModEffects.register(bus);
        com.example.jjkspells.malphite.registry.ModEntities.register(bus);
        com.example.jjkspells.learner.LearnerRegistry.register(bus);
        // Rimuru: Predator, Black Lightning, Megiddo, Absolute Defense, Spatial Domination, Beelzebuth, Imaginary Collapse, Void God Azathoth
        com.example.jjkspells.rimuru.registry.ModSpells.init();
        com.example.jjkspells.rimuru.registry.ModSounds.register(bus);
        com.example.jjkspells.rimuru.registry.ModParticles.register(bus);
        com.example.jjkspells.rimuru.registry.ModEffects.register(bus);
        com.example.jjkspells.rimuru.registry.ModEntities.register(bus);
        com.example.jjkspells.rimuru.registry.ModItems.register(bus);
        // Diablo: Nether Apocalypse, Primordial Dominion, Dimensional Funeral, Absolute Negation, Despair Time, Temptation World, Abyssal Genesis, Temptation King Azazel
        com.example.jjkspells.diablo.registry.ModSpells.init();
        com.example.jjkspells.diablo.registry.ModSounds.register(bus);
        com.example.jjkspells.diablo.registry.ModParticles.register(bus);
        com.example.jjkspells.diablo.registry.ModEffects.register(bus);
        com.example.jjkspells.diablo.registry.ModEntities.register(bus);
        // True Rimuru: World-Devouring Abyss, Civilization Storm, Heavenly Execution Array, Absolute Law of Protection, Fracture of Infinite Worlds, Abyss That Consumes All, Birth of Nothingness, End of All Creation
        com.example.jjkspells.tempest.registry.ModSpells.init();
        com.example.jjkspells.tempest.registry.ModSounds.register(bus);
        com.example.jjkspells.tempest.registry.ModParticles.register(bus);
        com.example.jjkspells.tempest.registry.ModEffects.register(bus);
        com.example.jjkspells.tempest.registry.ModEntities.register(bus);
        // Veldora: Storm Dragon's Wrath, Black Thunder Apocalypse, Dragon Breath, Storm Dragon's Fist, Law of Impossible Fate, Storm Dragon's Ascension, Dragon Nova, End of the Eternal Storm
        com.example.jjkspells.veldora.registry.ModSpells.init();
        com.example.jjkspells.veldora.registry.ModSounds.register(bus);
        com.example.jjkspells.veldora.registry.ModParticles.register(bus);
        com.example.jjkspells.veldora.registry.ModEffects.register(bus);
        com.example.jjkspells.veldora.registry.ModEntities.register(bus);
        // Velgrynd: Scorch Dragon's Breath, Cardinal Inferno, Cardinal Acceleration, Scorch Dragon's Dominion, Parallel Existence, Dimensional Scorch, Cardinal Cataclysm, Flame God Cthugha
        com.example.jjkspells.velgrynd.registry.ModSpells.init();
        com.example.jjkspells.velgrynd.registry.ModSounds.register(bus);
        com.example.jjkspells.velgrynd.registry.ModParticles.register(bus);
        com.example.jjkspells.velgrynd.registry.ModEffects.register(bus);
        com.example.jjkspells.velgrynd.registry.ModEntities.register(bus);
        // Zegion: the Insect Kaiser, eight cataclysmic spells and his grimoire
        com.example.jjkspells.zegion.registry.ModSpells.init();
        com.example.jjkspells.zegion.registry.ModItems.init();
        com.example.jjkspells.zegion.registry.ModSounds.register(bus);
        com.example.jjkspells.zegion.registry.ModParticles.register(bus);
        com.example.jjkspells.zegion.registry.ModEffects.register(bus);
        com.example.jjkspells.zegion.registry.ModEntities.register(bus);
        // Shadow: Shadow's Garden, Nameless King, Slime Armament, Perfect Counter, I Am Atomic
        com.example.jjkspells.shadow.registry.ModSpells.init();
        com.example.jjkspells.shadow.registry.ModSounds.register(bus);
        com.example.jjkspells.shadow.registry.ModParticles.register(bus);
        com.example.jjkspells.shadow.registry.ModEffects.register(bus);
        com.example.jjkspells.shadow.registry.ModEntities.register(bus);
        // Yhwach: THE ALMIGHTY
        // Garou (Cosmic Fear Mode): Gamma Ray Burst
        com.example.jjkspells.garou.registry.ModSpells.init();
        com.example.jjkspells.garou.registry.ModParticles.register(bus);
        com.example.jjkspells.garou.registry.ModEntities.register(bus);
        com.example.jjkspells.garou.registry.NfSpells.init();
        com.example.jjkspells.garou.registry.NfParticles.register(bus);
        com.example.jjkspells.garou.registry.NfEntities.register(bus);
        com.example.jjkspells.relic.RelicLoot.register(bus);
        com.example.jjkspells.enchant.FlameSlashRegistry.register(bus);
        com.example.jjkspells.gil.GilEffects.register(bus);
        com.example.jjkspells.gil.GilEntities.register(bus);
        ModEffects.register(bus);
        ModSounds.register(bus);
        ModEntities.register(bus);
        ModSpells.SPELLS.register(bus);
        ModItems.ITEMS.register(bus);
        ModCreativeTabs.register(bus);
        ModParticles.PARTICLES.register(bus);
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path.startsWith("textures/environment/") ? TexAlias.of(path) : path);
    }
}
