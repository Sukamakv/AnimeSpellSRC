package com.example.jjkspells.irontomb;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** "Has this player slain Phainon?" A permanent flag on the player; it survives death and dimension changes. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class PhainonGate {
    public static final String KEY = "jjk_phainon_slain";

    private PhainonGate() {
    }

    public static boolean has(Player p) {
        return p.getPersistentData().getBoolean(KEY);
    }

    /** True when this player may wake Irontomb: the gate is off, they are in creative, or they slew Phainon. */
    public static boolean unlocked(Player p) {
        return !IrontombBalance.PH_REQUIRE || p.isCreative() || has(p);
    }

    public static void set(Player p, boolean v) {
        if (v) {
            p.getPersistentData().putBoolean(KEY, true);
        } else {
            p.getPersistentData().remove(KEY);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal().getPersistentData().getBoolean(KEY)) {
            e.getEntity().getPersistentData().putBoolean(KEY, true);
        }
    }
}
