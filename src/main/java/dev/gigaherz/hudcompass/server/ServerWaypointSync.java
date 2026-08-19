package dev.gigaherz.hudcompass.server;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.network.AddWaypoint;
import dev.gigaherz.hudcompass.network.ClientHello;
import dev.gigaherz.hudcompass.network.RemoveWaypoint;
import dev.gigaherz.hudcompass.network.ServerHello;
import dev.gigaherz.hudcompass.network.SyncWaypointData;
import dev.gigaherz.hudcompass.network.UpdateWaypointsFromGui;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side half of multiplayer waypoint sharing. Replaces upstream's NeoForge attachment
 * (holding a {@code PointsOfInterest} per player, auto-persisted and copied across respawn) with
 * a plain in-memory {@code Map<UUID, PointsOfInterest>} -- keyed by player UUID, which is stable
 * across respawn, so no explicit "copy to new entity" step is needed. Persistence across relogs
 * and server restarts is handled separately by a mixin into {@code ServerPlayer}'s own save data
 * (see {@code dev.gigaherz.hudcompass.mixin.ServerPlayerWaypointDataMixin}).
 * <p>
 * Entries are deliberately never evicted from {@link #STORE}: vanilla's own player-data save
 * (which the mixin above hooks) happens around the same disconnect sequence as
 * {@code ServerPlayConnectionEvents.DISCONNECT}, and evicting there raced the save -- wiping the
 * in-memory object (back down to a fresh empty one) before the mixin could serialize it, so every
 * quit silently discarded that session's changes. The cost is one small object per unique player
 * who has ever connected for the life of the server process, which is a straightforward trade
 * against a subtle, silent data-loss bug.
 */
public class ServerWaypointSync
{
    private static final Map<UUID, PointsOfInterest> STORE = new HashMap<>();

    public static PointsOfInterest get(UUID playerId)
    {
        return STORE.computeIfAbsent(playerId, id -> new PointsOfInterest());
    }

    public static PointsOfInterest get(ServerPlayer player)
    {
        return get(player.getUUID());
    }

    public static void init()
    {
        ServerPlayNetworking.registerGlobalReceiver(ClientHello.TYPE, (payload, context) -> {
            var points = get(context.player());
            points.otherSideHasMod = true;
            sendSync(context.player(), points);
        });

        ServerPlayNetworking.registerGlobalReceiver(AddWaypoint.TYPE, (payload, context) -> {
            var points = get(context.player());
            var labelText = payload.label();
            var label = (labelText == null || labelText.isEmpty()) ? null : labelText;
            var waypoint = new BasicWaypoint(new Vec3(payload.x(), payload.y(), payload.z()), label,
                    BasicIconData.basic(payload.spriteName()));
            points.get(context.player().level()).addPoint(waypoint);
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveWaypoint.TYPE, (payload, context) -> {
            var points = get(context.player());
            points.getAllWorlds().forEach(world -> world.removePoint(payload.id()));
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateWaypointsFromGui.TYPE, (payload, context) -> {
            var points = get(context.player());
            var decoded = payload.decode(context.player().registryAccess());
            points.applyUpdatesFromGui(decoded.pointsAdded(), decoded.pointsUpdated(), decoded.pointsRemoved());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ConfigData.disableServerHello)
                return;

            if (ServerPlayNetworking.canSend(handler.player, ServerHello.TYPE))
            {
                ServerPlayNetworking.send(handler.player, ServerHello.INSTANCE);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
            {
                var points = STORE.get(player.getUUID());
                if (points == null)
                    continue;

                if (points.otherSideHasMod && points.syncNumber > points.syncedNumber)
                {
                    sendSync(player, points);
                }
            }
        });
    }

    private static void sendSync(ServerPlayer player, PointsOfInterest points)
    {
        if (ConfigData.disableServerHello)
            return;

        ServerPlayNetworking.send(player, SyncWaypointData.of(points, player.registryAccess()));
        points.syncedNumber = points.syncNumber;
    }
}
