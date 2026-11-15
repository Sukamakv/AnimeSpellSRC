package com.example.jjkspells;

import com.example.jjkspells.effect.BacklashEffect;
import com.example.jjkspells.effect.OverloadEffect;
import com.example.jjkspells.effect.SimpleDomainEffect;
import com.example.jjkspells.effect.StunEffect;
import com.example.jjkspells.effect.WorldEnderEffect;
import com.example.jjkspells.effect.UmbralStanceEffect;
import com.example.jjkspells.effect.BleedingEffect;
import com.example.jjkspells.effect.CourageEffect;
import com.example.jjkspells.effect.CripplingStrikeEffect;
import com.example.jjkspells.effect.MistDrainEffect;
import com.example.jjkspells.effect.SoulTornEffect;
import com.example.jjkspells.effect.SovereignPowerEffect;
import com.example.jjkspells.effect.ViegoMistEffect;
import com.example.jjkspells.effect.SimpleStatusEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Applied by Unlimited Void: no movement, no camera, no healing, no casting, no attacking, -15 armor. */
    public static final RegistryObject<MobEffect> OVERLOAD = EFFECTS.register("overload", OverloadEffect::new);
    /** Applied by Simple Domain: immunity to Overload. */
    public static final RegistryObject<MobEffect> SIMPLE_DOMAIN = EFFECTS.register("simple_domain", SimpleDomainEffect::new);

    /** The cost of Hollow Purple: Nine Ropes: -10% max health. */
    public static final RegistryObject<MobEffect> BACKLASH = EFFECTS.register("backlash", BacklashEffect::new);

    /** Heavenly Onslaught's wound: -15% maximum health. */
    public static final RegistryObject<MobEffect> PRIME_REND = EFFECTS.register("prime_rend", com.example.jjkspells.prime.PrimeRendEffect::new);

    /** PRIME Sukuna: the holder cannot melee attack. */
    public static final RegistryObject<MobEffect> DISMANTLED = EFFECTS.register("dismantled", com.example.jjkspells.prime.DismantledEffect::new);

    /** Applied by Crippling Strike: the holder's next melee attack is the empowered chop. The amplifier is the spell level - 1. */
    public static final RegistryObject<MobEffect> CRIPPLING_STRIKE = EFFECTS.register("crippling_strike", CripplingStrikeEffect::new);
    /** Noxian Guillotine's wind-up: the client silences the world's sounds while the local player has this. */
    public static final RegistryObject<MobEffect> MUFFLED = EFFECTS.register("muffled", () -> new SimpleStatusEffect(MobEffectCategory.NEUTRAL, 0x3A3A48));
    /** Noxian Guillotine's impact: the client shakes the screen while the local player has this. */
    public static final RegistryObject<MobEffect> SHAKEN = EFFECTS.register("shaken", () -> new SimpleStatusEffect(MobEffectCategory.NEUTRAL, 0x8A1C1C));
    /** Courage (Garen): damage reduction and knockback resistance. */
    public static final RegistryObject<MobEffect> COURAGE = EFFECTS.register("courage", CourageEffect::new);

    /** Bleeding: 2 damage a second. Inflicted (with Alex's Caves' Irradiated) by the Darius and Sun Breathing spells. */
    public static final RegistryObject<MobEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);

    /** Aatrox's true Darkin form (+20% speed here; +50% damage, lifesteal and the rest in AatroxEvents). Amplifier = spell level - 1. */
    public static final RegistryObject<MobEffect> WORLD_ENDER = EFFECTS.register("world_ender", WorldEnderEffect::new);
    /** Umbral Dash's braced stance: knockback resistance while he surges. */
    public static final RegistryObject<MobEffect> UMBRAL_STANCE = EFFECTS.register("umbral_stance", UmbralStanceEffect::new);
    /** Marks a player near an active World Ender, so their client darkens and reddens the fog. */
    public static final RegistryObject<MobEffect> AT_DARKNESS = EFFECTS.register("at_darkness", () -> new SimpleStatusEffect(MobEffectCategory.NEUTRAL, 0x3A0A0E));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    /** Viego in his own mist: +35% speed (his melee damage is doubled in ViegoEvents). Refreshed by Harrowed Path while he stands in it. */
    public static final RegistryObject<MobEffect> VIEGO_MIST = EFFECTS.register("viego_mist", ViegoMistEffect::new);
    /** An enemy in Viego's mist: -20% speed, -35% spell power. */
    public static final RegistryObject<MobEffect> MIST_DRAIN = EFFECTS.register("mist_drain", MistDrainEffect::new);
    /** Sovereign's Domination's curse: -40% maximum health. */
    public static final RegistryObject<MobEffect> SOUL_TORN = EFFECTS.register("soul_torn", SoulTornEffect::new);
    /** Viego after a possession: +50% spell power. */
    public static final RegistryObject<MobEffect> SOVEREIGN_POWER = EFFECTS.register("sovereign_power", SovereignPowerEffect::new);
    /** Marks a player standing in Harrowed Path's mist, so their own client draws the mist and the closer fog. */
    public static final RegistryObject<MobEffect> MIST_VISION = EFFECTS.register("mist_vision", () -> new SimpleStatusEffect(MobEffectCategory.NEUTRAL, 0x143A2C));

    /** Applied by Hard Strike: no walking, jumping, attacking, casting or using items (knockback still moves the target). */
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("stun", StunEffect::new);
}
