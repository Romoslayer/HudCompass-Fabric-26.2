package dev.gigaherz.hudcompass.client;

import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
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
            return mc.getSingleplayerServer().getServerDirectory().toPath().resolve("client_waypoints").resolve("waypoints.dat").toAbsolutePath();
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
            ResourceLocation dim = mc.player.level().dimension().location();
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
                // No ProblemReporter on 1.20.1 -- a point that fails to decode is logged and
                // skipped individually inside PointsOfInterest instead.
                CompoundTag tag = NbtIo.read(path.toFile());
                PointsOfInterest.INSTANCE.clear();
                PointsOfInterest.INSTANCE.deserialize(tag, mc.getConnection().registryAccess());
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

                CompoundTag tag = new CompoundTag();
                PointsOfInterest.INSTANCE.serialize(tag, mc.getConnection().registryAccess());
                NbtIo.write(tag, path.toFile());

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
