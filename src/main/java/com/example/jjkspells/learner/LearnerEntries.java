package com.example.jjkspells.learner;

import com.example.jjkspells.JJKSpells;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** The ordered list of learnable spells with their materials, built on the server and sent to the client when the GUI opens. */
public final class LearnerEntries {
    public static final class Entry {
        public final int group;
        public final String spellPath;
        public final int level;
        public final List<Mat> mats;
        /** Entity id of the boss that unlocks this section, or "" for none. */
        public final String boss;

        Entry(int group, String spellPath, int level, List<Mat> mats, String boss) {
            this.boss = boss == null ? "" : boss;
            this.group = group;
            this.spellPath = spellPath;
            this.level = level;
            this.mats = mats;
        }

        public boolean affordable(Player p) {
            for (Mat m : mats) {
                if (!m.valid || m.have(p) < m.count) return false;
            }
            return true;
        }
    }

    private static AbstractSpell spell(String path) {
        return SpellRegistry.REGISTRY.get().getValue(new ResourceLocation(JJKSpells.MODID, path));
    }

    private static Item item(String path) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(JJKSpells.MODID, SkillGroups.itemPath(path)));
        return it == null || it == net.minecraft.world.item.Items.AIR ? null : it;
    }

    /** Server side: reads the config. Spells that are not registered are left out. */
    public static List<Entry> build() {
        return build(false);
    }

    /** prime = the PRIME Divinity Skill Learner (only the PRIME sections); otherwise the Divine Skill Learner (everything else). */
    public static List<Entry> build(boolean prime) {
        List<Entry> out = new ArrayList<>();
        for (int gi = 0; gi < SkillGroups.ALL.size(); gi++) {
            if (SkillGroups.isPrime(SkillGroups.ALL.get(gi)) != prime) continue;
            for (String path : SkillGroups.ALL.get(gi).spells()) {
                if (SkillGroups.isItem(path)) {
                    if (item(path) == null) {
                        LogUtils.getLogger().warn("Divine Skill Learner: item jjk_spells:{} is not registered, skipped", SkillGroups.itemPath(path));
                        continue;
                    }
                    out.add(new Entry(gi, path, 1, LearnerConfig.materials(gi, path), LearnerConfig.boss(gi)));
                    continue;
                }
                AbstractSpell s = spell(path);
                if (s == null) {
                    LogUtils.getLogger().warn("Divine Skill Learner: spell jjk_spells:{} is not registered, skipped", path);
                    continue;
                }
                int lvl = Math.max(s.getMinLevel(), Math.min(s.getMaxLevel(), LearnerConfig.level(gi, path)));
                out.add(new Entry(gi, path, lvl, LearnerConfig.materials(gi, path), LearnerConfig.boss(gi)));
            }
        }
        return out;
    }

    /** The same entry with materials and level re-read from the config (so edits apply at once). */
    public static Entry refresh(Entry old) {
        if (SkillGroups.isItem(old.spellPath)) return new Entry(old.group, old.spellPath, 1, LearnerConfig.materials(old.group, old.spellPath), LearnerConfig.boss(old.group));
        AbstractSpell s = spell(old.spellPath);
        int lvl = s == null ? old.level : Math.max(s.getMinLevel(), Math.min(s.getMaxLevel(), LearnerConfig.level(old.group, old.spellPath)));
        return new Entry(old.group, old.spellPath, lvl, LearnerConfig.materials(old.group, old.spellPath), LearnerConfig.boss(old.group));
    }

    public static void write(FriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeVarInt(e.group);
            buf.writeUtf(e.spellPath);
            buf.writeVarInt(e.level);
            buf.writeVarInt(e.mats.size());
            for (Mat m : e.mats) buf.writeUtf(m.toString());
            buf.writeUtf(e.boss);
        }
    }

    public static List<Entry> read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int g = buf.readVarInt();
            String path = buf.readUtf();
            int lvl = buf.readVarInt();
            int k = buf.readVarInt();
            List<Mat> mats = new ArrayList<>(k);
            for (int j = 0; j < k; j++) mats.add(Mat.parse(buf.readUtf()));
            out.add(new Entry(g, path, lvl, mats, buf.readUtf()));
        }
        return out;
    }

    /** True if this player may learn from the section (its boss is dead, nothing is required, or creative bypass). */
    public static boolean unlocked(Player p, int group) {
        if (!LearnerConfig.REQUIRE_BOSS.get()) return true;
        if (p.isCreative() && LearnerConfig.CREATIVE_FREE.get()) return true;
        String boss = LearnerConfig.boss(group);
        return boss.isEmpty() || BossKills.has(p, boss);
    }

    /** One bit per section, set when it is unlocked for the player. */
    public static int mask(Player p) {
        int m = 0;
        for (int g = 0; g < SkillGroups.ALL.size() && g < 31; g++) if (unlocked(p, g)) m |= 1 << g;
        return m;
    }

    /** Server side: pays the materials and hands over the scroll. */
    public static void learn(ServerPlayer p, Entry e) {
        boolean isItem = SkillGroups.isItem(e.spellPath);
        AbstractSpell s = isItem ? null : spell(e.spellPath);
        Item itm = isItem ? item(e.spellPath) : null;
        if (isItem ? itm == null : s == null) {
            p.displayClientMessage(Component.literal("Unknown entry: " + e.spellPath).withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (!unlocked(p, e.group)) {
            p.displayClientMessage(Component.literal("Locked: defeat the section's boss first").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        boolean free = p.isCreative() && LearnerConfig.CREATIVE_FREE.get();
        if (!free && !e.affordable(p)) {
            p.displayClientMessage(Component.literal("Not enough materials").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        ItemStack scroll;
        try {
            scroll = isItem ? new ItemStack(itm) : new ItemStack(ItemRegistry.SCROLL.get());
            if (!isItem) ISpellContainer.createScrollContainer(s, e.level, scroll);
        } catch (Throwable t) {
            LogUtils.getLogger().error("Divine Skill Learner: could not build {}", e.spellPath, t);
            p.displayClientMessage(Component.literal("Could not craft this (see log)").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (scroll.isEmpty()) {
            p.displayClientMessage(Component.literal("Could not craft this (empty result)").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (!free) {
            for (Mat m : e.mats) m.take(p);
        }
        ItemStack give = scroll.copy();
        if (!p.getInventory().add(give) || !give.isEmpty()) {
            p.drop(give.isEmpty() ? scroll.copy() : give, false);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.8f);
        p.displayClientMessage(Component.literal(isItem ? "Obtained: " : "Learned: ").append(isItem ? scroll.getHoverName() : Component.translatable("spell.jjk_spells." + e.spellPath)).withStyle(net.minecraft.ChatFormatting.GOLD), true);
        p.containerMenu.broadcastChanges();
    }

    private LearnerEntries() {
    }
}
