package dev.gigaherz.hudcompass.integrations.journeymap;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.CommonEventRegistry;
import journeymap.api.v2.common.event.common.WaypointEvent;
import journeymap.api.v2.common.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Mirrors JourneyMap's own waypoints onto the compass HUD -- one-directional, read-only:
 * HudCompass never creates or edits JourneyMap waypoints, it just displays them alongside its
 * own. Purely client-side (a JourneyMap client plugin has no server-side counterpart here), so
 * this ports over cleanly the same way {@code LocatorBarPoints} does.
 * <p>
 * Upstream had a version of this already, but it was commented out in its entirety and had
 * bit-rotted against an older shape of {@code PointInfo} (references to a since-removed
 * {@code serializeAdditional}/{@code deserializeAdditional} pair that don't exist anywhere else
 * in the current codebase) -- this is a fresh implementation against the current API on both
 * sides (this mod's {@code PointInfo}, and the real JourneyMap API v2 for MC 26.2, verified
 * against the actual {@code TeamJM/journeymap-api} repo at its {@code 26.2_2.0.0_1} tag, not
 * assumed from the stale draft).
 * <p>
 * <b>Soft dependency, loaded lazily by design</b> -- per the JourneyMap API's own guidance: this
 * class is registered via the {@code "journeymap"} entrypoint in {@code fabric.mod.json}, which
 * Fabric Loader only resolves when something explicitly asks for entrypoints under that key.
 * Nothing does that except JourneyMap itself, so if JourneyMap isn't installed, this class is
 * never loaded at all -- no reference to it exists anywhere else in this mod, deliberately,
 * matching the API docs' explicit instruction not to reference plugin classes elsewhere.
 * <p>
 * <b>Two update paths, deliberately</b>: the {@code WAYPOINT_EVENT} subscription below gives
 * snappy live updates while actively editing waypoints, but observed in practice to be
 * insufficient on its own -- waypoints failed to reappear after rejoining a world. Root cause
 * suspected (not fully confirmed, but consistent with everything observed): {@code READ} events,
 * which JourneyMap fires to replay its waypoint cache at world join, likely happen before {@code
 * Minecraft.player} is set, so {@code handleWaypointEvent}'s null check silently drops exactly
 * the events that would have repopulated the mirror on rejoin -- and since nothing else would
 * ever re-fire them, the mirror just stays empty from then on. Rather than chase the exact
 * timing further, added a periodic reconciliation pass using {@code IClientAPI#getAllWaypoints}
 * (a direct query, not dependent on catching any particular event) as a self-healing safety net
 * -- same pattern already used elsewhere in this port (see {@code LocatorBarPoints},
 * {@code PlayerTracker}) for "mirror an external live state onto the compass" integrations.
 */
@JourneyMapPlugin(apiVersion = "26.2-2.0.0")
public class HudCompassJourneymapPlugin implements IClientPlugin
{
    private static final Logger LOGGER = LogManager.getLogger();

    @Nullable
    private IClientAPI clientApi;

    @Nullable
    private ResourceKey<Level> lastDimension;
    private final Set<UUID> tracked = new HashSet<>();
    private int counter = 0;

    // Never actually sent over the network or saved to disk: these are purely-local mirrors of
    // JourneyMap's own waypoint state (dynamic + clientPoint, same reasoning as
    // LocatorBarPoints' VanillaWaypointAdapter), so both codecs are unreachable in practice.
    private static final MapCodec<JmWaypoint> UNREACHABLE_CODEC = new MapCodec<>()
    {
        @Override
        public <T> Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops)
        {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<JmWaypoint> decode(com.mojang.serialization.DynamicOps<T> ops, MapLike<T> input)
        {
            return DataResult.error(() -> "Cannot be encoded");
        }

        @Override
        public <T> com.mojang.serialization.RecordBuilder<T> encode(JmWaypoint input, com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.RecordBuilder<T> prefix)
        {
            return prefix.withErrorsFrom(DataResult.error(() -> "Cannot be encoded"));
        }
    };

    private static final StreamCodec<RegistryFriendlyByteBuf, JmWaypoint> UNREACHABLE_STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { throw new UnsupportedOperationException("journeymap points are never sent over the network"); },
            buf -> { throw new UnsupportedOperationException("journeymap points are never sent over the network"); }
    );

    public static final PointInfoType<JmWaypoint> TYPE =
            PointInfoType.register("journeymap", UNREACHABLE_CODEC, UNREACHABLE_STREAM_CODEC);

    @Override
    public void initialize(IClientAPI jmClientApi)
    {
        this.clientApi = jmClientApi;
        CommonEventRegistry.WAYPOINT_EVENT.subscribe(getModId(), this::onWaypointEvent);
        ClientTickEvents.END_CLIENT_TICK.register(this::reconcileTick);
    }

    private void reconcileTick(Minecraft mc)
    {
        try
        {
            if (!ConfigData.enableJourneymapIntegration || clientApi == null)
                return;

            var player = mc.player;
            if (player == null)
                return;

            // Once a second is plenty for a safety-net reconciliation pass -- matches the
            // throttle already used by the other per-tick integrations in this port
            // (SpawnPointPoints, VanillaMapPoints, PlayerTracker).
            if ((++counter) < 20)
                return;
            counter = 0;

            var dimension = player.level().dimension();

            if (lastDimension != null && lastDimension != dimension)
            {
                var oldWorldPoints = PointsOfInterest.INSTANCE.get(lastDimension);
                for (UUID id : tracked)
                    oldWorldPoints.removePoint(id);
                tracked.clear();
            }
            lastDimension = dimension;

            var worldPoints = PointsOfInterest.INSTANCE.get(dimension);

            var current = new HashSet<UUID>();
            for (Waypoint jmwp : clientApi.getAllWaypoints(dimension))
            {
                if (!isVisible(jmwp, dimension))
                    continue;

                UUID id = idOf(jmwp);
                if (id == null)
                    continue;

                current.add(id);
                upsert(worldPoints, id, jmwp);
            }

            tracked.removeIf(id -> {
                if (current.contains(id))
                    return false;
                worldPoints.removePoint(id);
                return true;
            });
            tracked.addAll(current);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to reconcile JourneyMap waypoints, ignoring", e);
        }
    }

    @Override
    public String getModId()
    {
        return HudCompass.MODID;
    }

    private void onWaypointEvent(WaypointEvent event)
    {
        // Defensive by design, not habit: this runs synchronously inside JourneyMap's own
        // waypoint CRUD handling (confirmed by observing it in practice -- an uncaught exception
        // here surfaced as a crash deep in JourneyMap's *own* network code, encoding a failure
        // response with a null message, which disconnected the player entirely). Whatever the
        // exact causal chain, a purely cosmetic mirror integration must never be able to take
        // down JourneyMap's actual waypoint operations or the player's connection.
        try
        {
            var mc = Minecraft.getInstance();
            // CommonEventRegistry.WAYPOINT_EVENT really does fire from both JourneyMap's client
            // and server-side handling (confirmed: observed this same subscriber invoked from
            // journeymap.common.network.handler.ServerWaypointHandler, on the "Server thread",
            // even though this is only ever registered via IClientPlugin) -- singleplayer runs
            // the integrated server on a different thread than rendering, and PointsOfInterest
            // .INSTANCE is not thread-safe, so mutating it off the render thread is a real data
            // race even when it doesn't happen to throw. Hop over if we're not already there.
            if (!mc.isSameThread())
            {
                mc.execute(() -> onWaypointEvent(event));
                return;
            }

            handleWaypointEvent(mc, event);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to handle JourneyMap waypoint event, ignoring", e);
        }
    }

    private void handleWaypointEvent(Minecraft mc, WaypointEvent event)
    {
        if (!ConfigData.enableJourneymapIntegration)
            return;

        var player = mc.player;
        if (player == null)
            return;

        var dimension = event.getDimension();
        if (dimension == null)
            return;

        Waypoint jmwp = event.getWaypoint();
        UUID id = idOf(jmwp);
        if (id == null)
            return;

        var worldPoints = PointsOfInterest.INSTANCE.get(dimension);
        boolean visible = isVisible(jmwp, dimension);

        switch (event.getContext())
        {
            case DELETED -> worldPoints.removePoint(id);
            case CREATE, READ -> {
                if (visible)
                    upsert(worldPoints, id, jmwp);
            }
            case UPDATE -> {
                if (visible)
                    upsert(worldPoints, id, jmwp);
                else
                    worldPoints.removePoint(id);
            }
        }
    }

    /**
     * Updates the existing {@link JmWaypoint} in place when one's already tracked under this id,
     * rather than replacing it with a freshly-constructed one -- see this class's own javadoc for
     * why: a fresh object resets {@link PointInfo#fade}, restarting the label fade-in animation,
     * which made every already-visible waypoint's label visibly blink on each reconciliation pass
     * (every second) even though nothing about it had actually changed.
     */
    private static void upsert(PointsOfInterest.WorldPoints worldPoints, UUID id, Waypoint jmwp)
    {
        var existing = worldPoints.find(id);
        if (existing.isPresent() && existing.get() instanceof JmWaypoint jm)
            jm.update(jmwp);
        else
            worldPoints.addPoint(new JmWaypoint(jmwp, id));
    }

    private static boolean isVisible(Waypoint jmwp, ResourceKey<Level> dimension)
    {
        return jmwp.getBlockPos() != null && jmwp.isEnabled()
                && (jmwp.getDimensions() == null || jmwp.getDimensions().isEmpty()
                        || jmwp.getDimensions().contains(dimension.identifier().toString()));
    }

    @Nullable
    private static UUID idOf(Waypoint jmwp)
    {
        String guid = jmwp.getGuid();
        if (guid == null)
            return null;
        return UUID.nameUUIDFromBytes(guid.getBytes(StandardCharsets.UTF_8));
    }

    public static class JmWaypoint extends PointInfo<JmWaypoint>
    {
        private Vec3 position;

        JmWaypoint(Waypoint jmwp, UUID id)
        {
            super(TYPE, true, Component.literal(jmwp.getName() != null ? jmwp.getName() : ""), coloredIcon(jmwp));
            clientPoint();
            setInternalId(id);
            // handleWaypointEvent already checked getBlockPos() != null before constructing this.
            this.position = Vec3.atCenterOf(jmwp.getBlockPos());
        }

        /**
         * Refreshes this already-tracked waypoint's label/position/icon from JourneyMap's current
         * state, in place -- deliberately not a new object, so {@link #fade} (the label animation
         * state) survives across reconciliation passes. See {@link HudCompassJourneymapPlugin#upsert}.
         */
        void update(Waypoint jmwp)
        {
            setLabel(Component.literal(jmwp.getName() != null ? jmwp.getName() : ""));
            if (jmwp.getBlockPos() != null)
                position = Vec3.atCenterOf(jmwp.getBlockPos());
            setIconData(coloredIcon(jmwp));
        }

        private static BasicIconData coloredIcon(Waypoint jmwp)
        {
            Integer rgb = jmwp.getIconColor();
            if (rgb == null)
                return BasicIconData.generic();

            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;
            return new BasicIconData(BasicIconData.generic().spriteName, r, g, b, 1.0f);
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return position;
        }
    }
}
