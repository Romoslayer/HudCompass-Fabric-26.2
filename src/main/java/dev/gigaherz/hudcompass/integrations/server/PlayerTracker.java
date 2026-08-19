package dev.gigaherz.hudcompass.integrations.server;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.icons.client.PlayerFaceRenderer;
import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Shows other online players as waypoints on the compass, filtered by {@link
 * ConfigData#playerDisplay} (NONE / TEAM / ALL, using vanilla scoreboard teams -- unrelated to
 * anything social/external).
 * <p>
 * Deliberately independent of vanilla's own locator-bar/waypoint system ({@code
 * LocatorBarPoints}, gated by the {@code locatorBar} game rule): that's a conscious choice
 * matching upstream's original design, which predates vanilla's own player-tracking feature.
 * See the {@code LocatorBarPoints} javadoc for the corresponding half of the de-duplication this
 * requires -- it explicitly skips player-identified vanilla waypoints so the two systems don't
 * both add an icon for the same player.
 * <p>
 * Upstream drives this off NeoForge's {@code PlayerEvent.StartTracking}/{@code StopTracking}
 * (fired by vanilla's per-entity chunk-tracking system). Fabric API has no equivalent event in
 * this version (confirmed: no "tracking" class anywhere across the fabric-api module jars), so
 * this instead does a periodic full scan of online players per observer -- functionally
 * equivalent for this use case, and arguably more correct for a *compass*, which is meant to
 * point at teammates regardless of chunk-tracking/render distance rather than only those nearby
 * enough to normally render.
 */
public class PlayerTracker
{
    public static final PointInfoType<PlayerWaypoint> PLAYER_POINT =
            PointInfoType.register("player", PlayerWaypoint.CODEC, PlayerWaypoint.STREAM_CODEC);

    public static final IconDataSerializer<PlayerIconData> ICON_DATA =
            IconDataSerializer.register("player", PlayerIconData.CODEC, PlayerIconData.STREAM_CODEC);

    private static final class TrackingState
    {
        ResourceKey<Level> dimension;
        final Map<UUID, PlayerWaypoint> shown = new HashMap<>();
    }

    private static final Map<UUID, TrackingState> TRACKED = new HashMap<>();

    private int counter = 0;

    public static void init()
    {
        PlayerTracker instance = new PlayerTracker();

        ServerTickEvents.END_SERVER_TICK.register(instance::serverTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRACKED.remove(handler.player.getUUID()));
    }

    public static void initClientRendering()
    {
        IconRendererRegistry.registerRenderer(ICON_DATA, new OtherPlayerRenderer());
    }

    private void serverTick(net.minecraft.server.MinecraftServer server)
    {
        // Matches upstream's own throttle (once per ~second at 20 TPS): this doesn't need
        // sub-second precision, and rescanning every online player against every other one each
        // tick would be wasteful for no observable benefit.
        if ((++counter) < 20)
            return;
        counter = 0;

        for (ServerPlayer observer : server.getPlayerList().getPlayers())
        {
            updateTrackingFor(observer);
        }
    }

    private void updateTrackingFor(ServerPlayer observer)
    {
        var points = ServerWaypointSync.get(observer);
        var currentDim = observer.level().dimension();
        var state = TRACKED.computeIfAbsent(observer.getUUID(), id -> new TrackingState());

        if (state.dimension != null && state.dimension != currentDim)
        {
            var oldWorldPoints = points.get(state.dimension);
            state.shown.values().forEach(oldWorldPoints::removePoint);
            state.shown.clear();
        }
        state.dimension = currentDim;

        var worldPoints = points.get(observer.level());
        var visible = new HashSet<UUID>();

        if (ConfigData.playerDisplay != ConfigData.PlayerDisplay.NONE)
        {
            PlayerTeam observerTeam = observer.getTeam();
            for (ServerPlayer target : observer.level().players())
            {
                if (target == observer)
                    continue;

                boolean allowed = ConfigData.playerDisplay == ConfigData.PlayerDisplay.ALL
                        || target.getTeam() == observerTeam;
                if (!allowed)
                    continue;

                visible.add(target.getUUID());
                state.shown.computeIfAbsent(target.getUUID(), id -> {
                    var wp = new PlayerWaypoint(target);
                    worldPoints.addPoint(wp);
                    return wp;
                });
            }
        }

        state.shown.entrySet().removeIf(entry -> {
            if (visible.contains(entry.getKey()))
                return false;
            worldPoints.removePoint(entry.getValue());
            return true;
        });
    }

    public static class PlayerWaypoint extends PointInfo<PlayerWaypoint>
    {
        public static final MapCodec<PlayerWaypoint> CODEC = RecordCodecBuilder.mapCodec(instance ->
                codecFragment(instance).and(UUIDUtil.CODEC.fieldOf("player").forGetter(e -> e.playerUUID)).apply(instance, PlayerWaypoint::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerWaypoint> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PointInfo::getInternalId,
                ByteBufCodecs.optional(net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC), e -> Optional.ofNullable(e.getLabel()),
                ByteBufCodecs.BOOL, e -> e.displayVerticalDistance(null),
                IIconData.DISPATCH_STREAM_CODEC, PlayerWaypoint::getIconData,
                UUIDUtil.STREAM_CODEC, e -> e.playerUUID,
                PlayerWaypoint::new
        );

        private final UUID playerUUID;
        private Vec3 position;

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        protected PlayerWaypoint(UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData, UUID playerUUID)
        {
            super(PLAYER_POINT, false, internalId, label, displayVerticalDistance, iconData);
            this.playerUUID = playerUUID;
        }

        public PlayerWaypoint(Player player)
        {
            super(PLAYER_POINT, false, player.getDisplayName(), new PlayerIconData(player.getUUID()));
            dynamic();
            this.playerUUID = player.getUUID();
            this.position = player.position();
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            var target = player.level().getPlayerByUUID(playerUUID);
            if (target == null)
                return position;

            return target.getPosition(partialTicks);
        }

        @Override
        public void tick(Player player)
        {
            if (!player.level().isClientSide())
                return;

            var target = player.level().getPlayerByUUID(playerUUID);
            if (target == null)
                return;

            position = target.position();
            setLabel(target.getDisplayName());
        }
    }

    public static class PlayerIconData implements IIconData<PlayerIconData>
    {
        public static final MapCodec<PlayerIconData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(e -> e.playerId)
        ).apply(instance, PlayerIconData::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerIconData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, e -> e.playerId,
                PlayerIconData::new
        );

        private final UUID playerId;

        public PlayerIconData(UUID playerId)
        {
            this.playerId = playerId;
        }

        @Override
        public IconDataSerializer<PlayerIconData> getSerializer()
        {
            return ICON_DATA;
        }
    }

    public static class OtherPlayerRenderer implements IIconRenderer<PlayerIconData>
    {
        @Override
        public void renderIcon(PlayerIconData data, Player player, TextureManager textureManager, GuiGraphicsExtractor graphics, int x, int y, int alpha)
        {
            PlayerFaceRenderer.drawUUID(player, data.playerId, graphics, x, y, alpha);
        }
    }
}
