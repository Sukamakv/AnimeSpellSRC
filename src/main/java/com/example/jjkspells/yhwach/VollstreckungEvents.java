package com.example.jjkspells.yhwach;

import com.example.jjkspells.JJKSpells;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Removes the temporary Vollstreckung attribute modifiers when their 30 seconds are over. (They are transient, so a restart also clears them.) */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VollstreckungEvents {
    private static final class Entry {
        final ServerLevel level;
        final UUID entity;
        final UUID modifier;
        final Attribute attribute;
        final long end;

        Entry(ServerLevel level, UUID entity, UUID modifier, Attribute attribute, long end) {
            this.level = level;
            this.entity = entity;
            this.modifier = modifier;
            this.attribute = attribute;
            this.end = end;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private VollstreckungEvents() {
    }

    public static synchronized void track(LivingEntity e, UUID modifier, Attribute attribute, long endGameTime) {
        if (!(e.level() instanceof ServerLevel sl)) {
            return;
        }
        ENTRIES.removeIf(x -> x.entity.equals(e.getUUID()) && x.modifier.equals(modifier));
        ENTRIES.add(new Entry(sl, e.getUUID(), modifier, attribute, endGameTime));
    }

    @SubscribeEvent
    public static synchronized void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel sl) || ENTRIES.isEmpty()) {
            return;
        }
        long now = sl.getGameTime();
        for (Iterator<Entry> it = ENTRIES.iterator(); it.hasNext(); ) {
            Entry en = it.next();
            if (en.level != sl || now < en.end) {
                continue;
            }
            it.remove();
            Entity e = sl.getEntity(en.entity);
            if (e instanceof LivingEntity le) {
                AttributeInstance inst = le.getAttribute(en.attribute);
                if (inst != null) {
                    inst.removeModifier(en.modifier);
                }
            }
        }
    }
}
