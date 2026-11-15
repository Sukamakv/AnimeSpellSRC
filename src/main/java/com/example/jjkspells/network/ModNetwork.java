package com.example.jjkspells.network;

import com.example.jjkspells.JJKSpells;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/** The mod's one network channel. Only used by Za Warudo to tell clients when time is stopped. */
public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            JJKSpells.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, TimeStopPacket.class, TimeStopPacket::encode, TimeStopPacket::decode,
                TimeStopPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, com.example.jjkspells.learner.LearnerSyncPacket.class, com.example.jjkspells.learner.LearnerSyncPacket::encode,
                com.example.jjkspells.learner.LearnerSyncPacket::decode, com.example.jjkspells.learner.LearnerSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, com.example.jjkspells.learner.LearnerEditPacket.class, com.example.jjkspells.learner.LearnerEditPacket::encode,
                com.example.jjkspells.learner.LearnerEditPacket::decode, com.example.jjkspells.learner.LearnerEditPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, com.example.jjkspells.enchant.FlameSwingPacket.class, com.example.jjkspells.enchant.FlameSwingPacket::encode,
                com.example.jjkspells.enchant.FlameSwingPacket::decode, com.example.jjkspells.enchant.FlameSwingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        com.example.jjkspells.capri.CapriNet.register(CHANNEL, 10);
    }

    public static void sendToDimension(ResourceKey<Level> dim, Object msg) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dim), msg);
    }
}
