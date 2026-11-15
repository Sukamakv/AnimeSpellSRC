package com.example.jjkspells.learner;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** One required material: an item ("minecraft:diamond*5") or an item tag ("#forge:ingots/iron*4"). */
public final class Mat {
    public final boolean tag;
    public final ResourceLocation id;
    public final int count;
    public final boolean valid;
    private final Item item;
    private final TagKey<Item> tagKey;
    /** Non-null for a "scroll:namespace:spell*n" requirement: a scroll of that spell must be handed over. */
    public final io.redspace.ironsspellbooks.api.spells.AbstractSpell scrollSpell;

    private Mat(boolean tag, ResourceLocation id, int count, boolean valid) {
        this(tag, id, count, valid, null);
    }

    private Mat(boolean tag, ResourceLocation id, int count, boolean valid, io.redspace.ironsspellbooks.api.spells.AbstractSpell scroll) {
        this.scrollSpell = scroll;
        this.tag = tag;
        this.id = id;
        this.count = count;
        this.valid = valid;
        this.item = (!tag && valid) ? ForgeRegistries.ITEMS.getValue(id) : null;
        this.tagKey = tag ? TagKey.create(Registries.ITEM, id) : null;
    }

    /** Never throws: a bad line gives an invalid Mat (the spell then cannot be learned until the config is fixed). */
    public static Mat parse(String s) {
        try {
            String t = s.trim();
            if (t.startsWith("scroll:")) {
                t = t.substring(7);
                int n = 1;
                int st = t.lastIndexOf('*');
                if (st >= 0) {
                    n = Integer.parseInt(t.substring(st + 1).trim());
                    t = t.substring(0, st).trim();
                }
                ResourceLocation rl = ResourceLocation.tryParse(t);
                io.redspace.ironsspellbooks.api.spells.AbstractSpell sp = rl == null ? null : io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.get().getValue(rl);
                boolean ok = sp != null && !String.valueOf(sp.getSpellResource()).endsWith(":none") && n >= 1;
                return new Mat(false, rl == null ? new ResourceLocation("minecraft", "air") : rl, Math.max(1, n), ok, ok ? sp : null);
            }
            boolean tag = t.startsWith("#");
            if (tag) t = t.substring(1);
            int n = 1;
            int star = t.lastIndexOf('*');
            if (star >= 0) {
                n = Integer.parseInt(t.substring(star + 1).trim());
                t = t.substring(0, star).trim();
            }
            ResourceLocation rl = ResourceLocation.tryParse(t);
            if (rl == null || n < 1) return new Mat(tag, rl == null ? new ResourceLocation("minecraft", "air") : rl, Math.max(1, n), false);
            boolean ok = tag || ForgeRegistries.ITEMS.containsKey(rl);
            if (!tag && ok && ForgeRegistries.ITEMS.getValue(rl) == net.minecraft.world.item.Items.AIR) ok = false;
            return new Mat(tag, rl, n, ok);
        } catch (RuntimeException e) {
            return new Mat(false, new ResourceLocation("minecraft", "air"), 1, false);
        }
    }

    private boolean isScroll(ItemStack st) {
        if (st.getItem() != io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get()) return false;
        for (var f : com.example.jjkspells.capri.IronsBridge.spellsOfAny(st)) {
            if (f.spell() == scrollSpell) return true;
        }
        return false;
    }

    public boolean matches(ItemStack st) {
        if (st.isEmpty() || !valid) return false;
        if (scrollSpell != null) return isScroll(st);
        return tag ? st.is(tagKey) : st.is(item);
    }

    public int have(net.minecraft.world.entity.player.Player p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (ItemStack st : inv.items) if (matches(st)) n += st.getCount();
        for (ItemStack st : inv.offhand) if (matches(st)) n += st.getCount();
        return n;
    }

    /** Takes {@code count} of this from the player's inventory (call only after have() >= count). */
    public void take(net.minecraft.world.entity.player.Player p) {
        int left = count;
        Inventory inv = p.getInventory();
        List<List<ItemStack>> lists = List.of(inv.items, inv.offhand);
        for (List<ItemStack> l : lists) {
            for (ItemStack st : l) {
                if (left <= 0) return;
                if (matches(st)) {
                    int d = Math.min(left, st.getCount());
                    st.shrink(d);
                    left -= d;
                }
            }
        }
    }

    /** Stack to draw as the icon (tags cycle through their members). */
    public ItemStack icon(long time) {
        if (!valid) return ItemStack.EMPTY;
        if (scrollSpell != null) {
            ItemStack sc = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
            try {
                io.redspace.ironsspellbooks.api.spells.ISpellContainer.createScrollContainer(scrollSpell, 1, sc);
            } catch (Throwable ignored) {
                // draws a blank scroll
            }
            return sc;
        }
        if (!tag) return new ItemStack(item);
        var tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) return ItemStack.EMPTY;
        var members = tags.getTag(tagKey).stream().toList();
        if (members.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(members.get((int) ((time / 20) % members.size())));
    }

    public String name() {
        return (scrollSpell != null ? "scroll:" : tag ? "#" : "") + id;
    }

    /** What the screen prints for this material. */
    public String label(ItemStack icon) {
        if (scrollSpell != null) return "Scroll: " + net.minecraft.network.chat.Component.translatable("spell." + id.getNamespace() + "." + id.getPath()).getString();
        return icon.getHoverName().getString();
    }

    @Override
    public String toString() {
        return (scrollSpell != null ? "scroll:" : tag ? "#" : "") + id + "*" + count;
    }
}
