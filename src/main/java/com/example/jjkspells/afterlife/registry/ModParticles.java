package com.example.jjkspells.afterlife.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    public static final RegistryObject<SimpleParticleType> BUTTERFLY_RED = reg("butterfly_red");
    public static final RegistryObject<SimpleParticleType> BUTTERFLY_ORANGE = reg("butterfly_orange");
    public static final RegistryObject<SimpleParticleType> BUTTERFLY_GOLD = reg("butterfly_gold");
    public static final RegistryObject<SimpleParticleType> EMBER = reg("ember");
    public static final RegistryObject<SimpleParticleType> PETAL = reg("petal");
    public static final RegistryObject<SimpleParticleType> SPIRIT_FLAME = reg("spirit_flame");
    public static final RegistryObject<SimpleParticleType> WISP = reg("wisp");
    public static final RegistryObject<SimpleParticleType> BLOSSOM = reg("blossom");
    public static final RegistryObject<SimpleParticleType> SLASH = reg("slash");
    public static final RegistryObject<SimpleParticleType> FLASH = reg("flash");
    public static final RegistryObject<SimpleParticleType> AFTERIMAGE = reg("afterimage");
    public static final RegistryObject<SimpleParticleType> LANTERN = reg("lantern");
    public static final RegistryObject<SimpleParticleType> TALISMAN = reg("talisman");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("al_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
