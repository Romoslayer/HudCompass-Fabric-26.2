package dev.gigaherz.hudcompass.integrations.server;

import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Adds a "Home" waypoint at the player's bed/respawn-anchor spawn point, if they have one. Ported
 * from upstream's {@code PlayerTickEvent.Post} listener onto {@code ServerTickEvents}, since
 * Fabric has no per-player tick event -- iterates online players each server tick instead,
 * matching {@code PlayerTracker}'s and {@code VanillaMapPoints}' same approach.
 */
public class SpawnPointPoints
{
    private static final Identifier ADDON_ID = HudCompass.location("spawn_point");

    private int counter = 0;

    public static void init()
    {
        SpawnPointPoints instance = new SpawnPointPoints();
        ServerTickEvents.END_SERVER_TICK.register(instance::serverTick);
    }

    private void serverTick(net.minecraft.server.MinecraftServer server)
    {
        if ((++counter) < 20)
            return;
        counter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            updateFor(player);
        }
    }

    private void updateFor(ServerPlayer player)
    {
        var points = ServerWaypointSync.get(player);
        SpawnPointAddon addon = points.getOrCreateAddonData(ADDON_ID, SpawnPointAddon::new);

        // The tracked waypoint can go stale without ever passing through this method's own
        // add/remove logic below: a relog triggers PointsOfInterest#deserialize(), which clears
        // perWorld entirely (dynamic points -- this one included -- are never saved, by design),
        // but that doesn't touch this addon's own bookkeeping, which lives in a separate map and
        // survives on the same never-evicted PointsOfInterest (see ServerWaypointSync). Left
        // unchecked, the logic below would see addon.waypoint still non-null and the spawn point
        // unchanged, conclude nothing needs to happen, and never re-add it -- the waypoint simply
        // disappears after every relog. Detect that here and treat it the same as "no waypoint
        // yet" so it gets added back below.
        if (addon.waypoint != null && points.get(addon.spawnWorld).find(addon.waypoint.getInternalId()).isEmpty())
        {
            addon.waypoint = null;
            addon.spawnWorld = null;
            addon.spawnPosition = null;
        }

        var respawnConfig = player.getRespawnConfig();
        ResourceKey<Level> worldKey = respawnConfig != null ? respawnConfig.respawnData().dimension() : null;
        BlockPos spawnPosition = respawnConfig != null ? respawnConfig.respawnData().pos() : null;

        boolean enabled = ConfigData.enableSpawnPointWaypoint;
        boolean hasWaypoint = addon.waypoint != null;
        boolean dimensionChanged = addon.spawnWorld != worldKey;
        boolean positionChanged = !Objects.equals(addon.spawnPosition, spawnPosition);
        boolean waypointChanged = dimensionChanged || positionChanged;

        boolean hasBed = respawnConfig != null;

        if (hasWaypoint && (!enabled || !hasBed || waypointChanged))
        {
            points.get(addon.spawnWorld).removePoint(addon.waypoint);
            addon.waypoint = null;
            addon.spawnWorld = null;
            addon.spawnPosition = null;
        }

        if (enabled && hasBed && (!hasWaypoint || waypointChanged))
        {
            addon.spawnWorld = worldKey;
            addon.spawnPosition = spawnPosition;
            addon.waypoint = new BasicWaypoint(Vec3.atCenterOf(spawnPosition), "Home", BasicIconData.basic("home"))
                    .dynamic();
            points.get(addon.spawnWorld).addPoint(addon.waypoint);
        }
    }

    private static class SpawnPointAddon
    {
        @Nullable
        public BasicWaypoint waypoint;
        public ResourceKey<Level> spawnWorld;
        public BlockPos spawnPosition;
    }
}
