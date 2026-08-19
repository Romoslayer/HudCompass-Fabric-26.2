package dev.gigaherz.hudcompass;

import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.integrations.server.PlayerTracker;
import dev.gigaherz.hudcompass.integrations.server.SpawnPointPoints;
import dev.gigaherz.hudcompass.integrations.server.VanillaMapPoints;
import dev.gigaherz.hudcompass.network.AddWaypoint;
import dev.gigaherz.hudcompass.network.ClientHello;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.network.ServerHello;
import dev.gigaherz.hudcompass.network.SyncWaypointData;
import dev.gigaherz.hudcompass.network.UpdateWaypointsFromGui;
import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

public class HudCompass implements ModInitializer
{
    public static final String MODID = "hudcompass";

    public static final IconDataSerializer<BasicIconData> BASIC_ICON_SERIALIZER =
            IconDataSerializer.register("basic", BasicIconData.CODEC, BasicIconData.STREAM_CODEC);

    public static final PointInfoType<BasicWaypoint> BASIC_WAYPOINT_TYPE =
            PointInfoType.register("basic", BasicWaypoint.CODEC, BasicWaypoint.STREAM_CODEC);

    @Override
    public void onInitialize()
    {
        ConfigData.load();

        PayloadTypeRegistry.serverboundPlay().register(ClientHello.TYPE, ClientHello.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AddWaypoint.TYPE, AddWaypoint.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemoveWaypoint.TYPE, RemoveWaypoint.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateWaypointsFromGui.TYPE, UpdateWaypointsFromGui.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerHello.TYPE, ServerHello.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncWaypointData.TYPE, SyncWaypointData.STREAM_CODEC);

        ServerWaypointSync.init();
        PlayerTracker.init();
        SpawnPointPoints.init();
        VanillaMapPoints.init();
    }

    public static Identifier location(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
