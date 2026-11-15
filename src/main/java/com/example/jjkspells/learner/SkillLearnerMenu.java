package com.example.jjkspells.learner;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The list of spells. It has no item slots: learning is a button (ids 0..n), and editing is done by the screen with text boxes,
 * sent to the server in a LearnerEditPacket. Two data slots carry which sections the player has unlocked (one bit per section).
 */
public class SkillLearnerMenu extends AbstractContainerMenu {
    public final List<LearnerEntries.Entry> entries;
    private final ContainerLevelAccess access;
    public final boolean prime;
    private final Player owner;      // null on the client
    private int maskLo, maskHi;      // client copies

    /** Server side. */
    public SkillLearnerMenu(int id, Inventory inv, ContainerLevelAccess access, List<LearnerEntries.Entry> entries, boolean prime) {
        super(LearnerRegistry.MENU.get(), id);
        this.prime = prime;
        this.access = access;
        this.entries = entries;
        this.owner = inv.player;
        init();
    }

    /** Client side: entries come from the open packet. */
    public SkillLearnerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(LearnerRegistry.MENU.get(), id);
        this.access = ContainerLevelAccess.NULL;
        this.prime = buf.readBoolean();
        this.entries = LearnerEntries.read(buf);
        this.owner = null;
        init();
    }

    private void init() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return owner != null ? LearnerEntries.mask(owner) & 0xFFFF : maskLo;
            }

            @Override
            public void set(int v) {
                maskLo = v & 0xFFFF;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return owner != null ? (LearnerEntries.mask(owner) >>> 16) & 0xFFFF : maskHi;
            }

            @Override
            public void set(int v) {
                maskHi = v & 0xFFFF;
            }
        });
    }

    /** Client side: has the player unlocked this section? */
    public boolean unlocked(int group) {
        int m = maskLo | (maskHi << 16);
        return group >= 31 || (m & (1 << group)) != 0;
    }

    /** Button ids travel as ONE signed byte, so long lists are addressed in pages: ids 250..255 pick a page of 250 entries, 0..249 an entry in it. */
    private int page;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp)) return false;
        id &= 0xFF;
        if (id >= 250) {
            page = id - 250;
            return true;
        }
        id += page * 250;
        page = 0;
        if (id < 0 || id >= entries.size()) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Learner: entry " + id + " not found (list has " + entries.size() + "), reopen the block").withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        try {
            LearnerEntries.learn(sp, LearnerEntries.refresh(entries.get(id)));
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error("Divine Skill Learner: crafting failed for entry {}", id, t);
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Learner error: " + t).withStyle(net.minecraft.ChatFormatting.RED), false);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access == ContainerLevelAccess.NULL || AbstractContainerMenu.stillValid(access, player, prime ? LearnerRegistry.PRIME_BLOCK.get() : LearnerRegistry.BLOCK.get());
    }
}
