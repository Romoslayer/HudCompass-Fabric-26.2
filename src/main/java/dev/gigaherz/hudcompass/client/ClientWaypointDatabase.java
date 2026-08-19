package dev.gigaherz.hudcompass.client;

import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientWaypointDatabase
{
    private static final Logger LOGGER = LogManager.getLogger();

    public static void init()
    {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> populateFromDisk(client));
        ClientTickEvents.END_CLIENT_TICK.register(ClientWaypointDatabase::clientTick);
    }

    private static Path getPath(Minecraft mc)
    {
        if (mc.isLocalServer())
        {
            return mc.getSingleplayerServer().getServerDirectory().resolve("client_waypoints").resolve("waypoints.dat").toAbsolutePath();
        }
        else
        {
            Connection networkManager = mc.player.connection.getConnection();
            SocketAddress addr = networkManager.getRemoteAddress();
            String address;
            if (addr instanceof InetSocketAddress ip)
            {
                address = ip.getHostString() + "_" + ip.getPort();
            }
            else
            {
                address = addr.toString();
            }
            Identifier dim = mc.player.level().dimension().identifier();
            String dimension = dim.getNamespace() + "_" + dim.getPath();
            return FabricLoader.getInstance().getGameDir().resolve("server_waypoints").resolve(address).resolve(dimension).resolve("waypoints.dat").toAbsolutePath();
        }
    }

    private static void populateFromDisk(Minecraft mc)
    {
        LOGGER.debug("Joined new dimension, loading...");

        Path path = getPath(mc);
        if (!Files.exists(path))
        {
            Path backup = Paths.get(path.toString() + ".bak");
            if (Files.exists(backup))
            {
                path = backup;
                LOGGER.debug("File did not exist, but a backup was found...");
            }
        }
        if (Files.exists(path))
        {
            try
            {
                CompoundTag tag = NbtIo.read(path);
                var reporter = new ProblemReporter.Collector();
                var input = TagValueInput.create(reporter, mc.getConnection().registryAccess(), tag);
                var list = input.childrenListOrEmpty("Worlds");
                PointsOfInterest.INSTANCE.clear();
                PointsOfInterest.INSTANCE.deserialize(input);
                if (!reporter.isEmpty())
                {
                    LOGGER.warn("Problems while deserialising POIs: {}", reporter.getReport());
                }
                LOGGER.debug("Done!");
            }
            catch (IOException e)
            {
                LOGGER.error("Failed to load waypoints", e);
            }
        }
        else
        {
            LOGGER.debug("File did not exist.");
        }
    }

    private static void saveToDisk(Minecraft mc)
    {
        // Once the connected server confirms it has the mod, its sync packets are authoritative
        // and this client-local shadow copy would just go stale -- see ClientWaypointSync.
        if (PointsOfInterest.INSTANCE.otherSideHasMod)
            return;

        if (PointsOfInterest.INSTANCE.changeNumber > PointsOfInterest.INSTANCE.savedNumber)
        {
            LOGGER.debug("Changes detected, saving.");
            try
            {
                Path path = getPath(mc);
                if (Files.exists(path))
                {
                    LOGGER.debug("File already exists, keeping as backup.");
                    Path backup = Paths.get(path.toString() + ".bak");
                    Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                else
                {
                    Files.createDirectories(path.getParent());
                }

                var reporter = new ProblemReporter.Collector();
                var output = TagValueOutput.createWithContext(reporter, mc.getConnection().registryAccess());
                PointsOfInterest.INSTANCE.serialize(output);
                if (!reporter.isEmpty())
                {
                    LOGGER.warn("Problems while serialising POIs: {}", reporter.getReport());
                }

                NbtIo.write(output.buildResult(), path);

                PointsOfInterest.INSTANCE.savedNumber = PointsOfInterest.INSTANCE.changeNumber;

                LOGGER.debug("Done!");
            }
            catch (IOException e)
            {
                LOGGER.error("Failed to save waypoints", e);
            }
        }
    }

    private static void clientTick(Minecraft mc)
    {
        if (mc.player != null)
        {
            saveToDisk(mc);
        }
    }
}
