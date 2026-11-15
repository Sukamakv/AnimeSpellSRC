package com.example.jjkspells.learner;

import com.example.jjkspells.JJKSpells;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

/** Remembers which bosses a player has defeated (kept in the player's persistent data, survives death). */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class BossKills {
    private static final String KEY = "jjk_boss_kills";

    private BossKills() {
    }

    private static CompoundTag root(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean has(Player p, String boss) {
        ListTag l = root(p).getList(KEY, 8);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(boss)) return true;
        return false;
    }

    public static Set<String> all(Player p) {
        Set<String> s = new HashSet<>();
        ListTag l = root(p).getList(KEY, 8);
        for (int i = 0; i < l.size(); i++) s.add(l.getString(i));
        return s;
    }

    public static void grant(Player p, String boss) {
        if (has(p, boss)) return;
        CompoundTag r = root(p);
        ListTag l = r.getList(KEY, 8);
        l.add(StringTag.valueOf(boss));
        r.put(KEY, l);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide) return;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
        if (key == null) return;
        String id = key.toString();
        boolean wanted = false;
        for (int g = 0; g < SkillGroups.ALL.size(); g++) {
            if (id.equals(LearnerConfig.boss(g))) {
                wanted = true;
                break;
            }
        }
        if (!wanted) return;
        Set<ServerPlayer> credit = new HashSet<>();
        if (e.getSource().getEntity() instanceof ServerPlayer sp) credit.add(sp);
        double radius = LearnerConfig.CREDIT_RADIUS.get();
        if (radius > 0 && dead.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (ServerPlayer p : sl.players()) if (p.distanceTo(dead) <= radius) credit.add(p);
        }
        for (ServerPlayer p : credit) {
            if (has(p, id)) continue;
            grant(p, id);
            p.displayClientMessage(Component.literal("Defeated ").append(dead.getName()).append(Component.literal(": new skills can be learned at the Divine Skill Learner"))
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(Player.PERSISTED_NBT_TAG)) {
            ListTag l = old.getCompound(Player.PERSISTED_NBT_TAG).getList(KEY, 8);
            CompoundTag r = root(e.getEntity());
            r.put(KEY, l.copy());
        }
    }
}
