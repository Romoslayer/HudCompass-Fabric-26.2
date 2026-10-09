package dev.gigaherz.hudcompass.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * Real players in the server's player list, so the mod's per-player server tick handlers
 * (spawn point, map and player tracking) see them exactly as they see a joined player. Same
 * construction as the user's bosslike-mc1.20.1 test harness.
 */
public final class TestPlayers
{
    private TestPlayers()
    {
    }

    public static ServerPlayer survival(MinecraftServer server, String name)
    {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
