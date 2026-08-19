package dev.gigaherz.hudcompass.integrations.client;

import com.mojang.serialization.*;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.icons.IconDataSerializer;
import dev.gigaherz.hudcompass.icons.client.IIconRenderer;
import dev.gigaherz.hudcompass.icons.client.IconRendererRegistry;
import dev.gigaherz.hudcompass.icons.client.PlayerFaceRenderer;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Surfaces the vanilla locator-bar waypoints on the compass -- whatever
 * {@code ClientWaypointManager} tracks, other than other players (see below). In practice, before
 * that exclusion, this was entirely other connected players: vanilla's {@code
 * ServerWaypointManager} broadcasts each {@code LivingEntity} that implements {@code
 * WaypointTransmitter} to every {@code ServerPlayer}. Not death markers -- those never touch this
 * system.
 * <p>
 * UUID-identified waypoints (i.e. players) are deliberately skipped: {@code PlayerTracker} is the
 * mod's own player-tracking system, matching upstream's original design, and it is intentionally
 * independent of vanilla's {@code locatorBar} game rule (which gates this class's data source
 * entirely) -- so mirroring players here too would both double up the icon and defeat the point
 * of {@code PlayerTracker} not being gameruled off. See that class's javadoc for the full
 * reasoning.
 * <p>
 * Purely client-side, so this ports over without needing the attachment/networking replacement
 * the server-side integrations (spawn point, vanilla map) still need.
 */
public class LocatorBarPoints
{
    public static final LocatorBarPoints INSTANCE = new LocatorBarPoints();

    // Never actually sent over the network: these adapters are purely-local mirrors of vanilla's
    // own locator-bar waypoints (see the class javadoc), so this stream codec is unreachable --
    // matching the CODEC below, which is likewise error-only.
    private static final StreamCodec<RegistryFriendlyByteBuf, VanillaWaypointAdapter> UNREACHABLE_STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { throw new UnsupportedOperationException("vanilla_waypoint points are never sent over the network"); },
            buf -> { throw new UnsupportedOperationException("vanilla_waypoint points are never sent over the network"); }
    );

    public static final PointInfoType<VanillaWaypointAdapter> TYPE =
            PointInfoType.register("vanilla_waypoint", VanillaWaypointAdapter.CODEC, UNREACHABLE_STREAM_CODEC);

    // Never sent over the network -- see UNREACHABLE_STREAM_CODEC above.
    private static final StreamCodec<RegistryFriendlyByteBuf, VanillaWaypointIcon> ICON_UNREACHABLE_STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { throw new UnsupportedOperationException("vanilla_waypoint icons are never sent over the network"); },
            buf -> { throw new UnsupportedOperationException("vanilla_waypoint icons are never sent over the network"); }
    );

    public static final IconDataSerializer<VanillaWaypointIcon> ICON_DATA =
            IconDataSerializer.register("vanilla_waypoint", VanillaWaypointIcon.CODEC, ICON_UNREACHABLE_STREAM_CODEC);

    private final Map<TrackedWaypoint, VanillaWaypointAdapter> addedPoints = new HashMap<>();

    public static void init()
    {
        IconRendererRegistry.registerRenderer(ICON_DATA, INSTANCE.new VanillaWaypointRenderer());

        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::clientTick);
    }

    private void clientTick(Minecraft mc)
    {
        if (!ConfigData.showLocatorBarWaypoints)
            return;

        var player = mc.player;
        if (player == null)
            return;

        var byLevel = PointsOfInterest.INSTANCE.get(player.level());

        var lostPoints = new HashMap<>(addedPoints);

        var cameraEntity = Objects.requireNonNullElse(mc.getCameraEntity(), player);

        var waypoints = player.connection.getWaypointManager();
        waypoints.forEachWaypoint(cameraEntity, waypoint -> {
            // UUID-identified waypoints are other players -- PlayerTracker already adds those
            // (independent of the locatorBar game rule, by design; see its javadoc), so mirroring
            // them here too would draw a duplicate icon for the same player.
            if (waypoint.id().left().isPresent())
                return;

            addedPoints.computeIfAbsent(waypoint, _w -> {
                var icon = new VanillaWaypointIcon(player, waypoint, cameraEntity);
                var point = new VanillaWaypointAdapter(player, waypoint, icon);

                byLevel.addPoint(point);

                return point;
            });
            lostPoints.remove(waypoint);
        });

        for (var entry : lostPoints.entrySet())
        {
            addedPoints.remove(entry.getKey());
            byLevel.removePoint(entry.getValue());
        }
    }

    public class VanillaWaypointAdapter extends PointInfo<VanillaWaypointAdapter>
    {
        public static final MapCodec<VanillaWaypointAdapter> CODEC = new MapCodec<>()
        {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops)
            {
                return Stream.empty();
            }

            @Override
            public <T> DataResult<VanillaWaypointAdapter> decode(DynamicOps<T> ops, MapLike<T> input)
            {
                return DataResult.error(() -> "Cannot be encoded");
            }

            @Override
            public <T> RecordBuilder<T> encode(VanillaWaypointAdapter input, DynamicOps<T> ops, RecordBuilder<T> prefix)
            {
                return prefix.withErrorsFrom(DataResult.error(() -> "Cannot be encoded"));
            }
        };

        private final TrackedWaypoint waypoint;
        private Vec3 position;

        public VanillaWaypointAdapter(Player player, TrackedWaypoint waypoint, VanillaWaypointIcon icon)
        {
            var level = player.level();
            var label = waypoint.id().map(
                    uuid -> {
                        var player0 = level.getEntity(uuid);
                        if (player0 != null)
                            return player0.getDisplayName();
                        return null;
                    },
                    Component::literal
            );

            super(TYPE, true, label, icon);
            clientPoint();
            this.waypoint = waypoint;
            tick(player);
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return position;
        }

        @Override
        public void tick(Player player)
        {
            var mc = Minecraft.getInstance();
            var tickRateManager = mc.level.tickRateManager();
            var yaw = Math.toRadians(-player.getYHeadRot() - waypoint.yawAngleToCamera(player.level(), mc.gameRenderer.mainCamera(), entity ->
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity))));
            var dist = Math.sqrt(waypoint.distanceSquared(player));
            this.position = player.position().add(
                    Mth.sin((float) yaw) * dist,
                    0,
                    Mth.cos((float) yaw) * dist
            );
        }
    }

    public class VanillaWaypointIcon implements IIconData<VanillaWaypointIcon>
    {
        public static final MapCodec<VanillaWaypointIcon> CODEC = new MapCodec<>()
        {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops)
            {
                return Stream.empty();
            }

            @Override
            public <T> DataResult<VanillaWaypointIcon> decode(DynamicOps<T> ops, MapLike<T> input)
            {
                return DataResult.error(() -> "Cannot be encoded");
            }

            @Override
            public <T> RecordBuilder<T> encode(VanillaWaypointIcon input, DynamicOps<T> ops, RecordBuilder<T> prefix)
            {
                return prefix.withErrorsFrom(DataResult.error(() -> "Cannot be encoded"));
            }
        };

        private final TrackedWaypoint waypoint;
        private final Entity cameraEntity;
        private final UUID target;

        public VanillaWaypointIcon(Player player, TrackedWaypoint waypoint, Entity cameraEntity)
        {
            this.target = waypoint.id().map(x -> x, _n -> null);
            this.waypoint = waypoint;
            this.cameraEntity = cameraEntity;
        }

        @Override
        public IconDataSerializer<VanillaWaypointIcon> getSerializer()
        {
            return ICON_DATA;
        }
    }

    public class VanillaWaypointRenderer implements IIconRenderer<VanillaWaypointIcon>
    {
        @Override
        public void renderIcon(VanillaWaypointIcon data, Player player, TextureManager textureManager, GuiGraphicsExtractor graphics, int x, int y, int alpha)
        {
            if (data.target == null || !PlayerFaceRenderer.drawUUID(player, data.target, graphics, x, y, alpha))
            {
                var waypoint = data.waypoint;
                var cameraEntity = data.cameraEntity;
                var icon = waypoint.icon();
                var style = Minecraft.getInstance().gui.hud.getWaypointStyles().get(icon.style);
                var distance = Mth.sqrt((float) waypoint.distanceSquared(cameraEntity));
                var sprite = style.sprite(distance);
                int color = icon.color
                        .orElseGet(
                                () -> waypoint.id()
                                        .map(
                                                uuid -> ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F),
                                                name -> ARGB.setBrightness(ARGB.color(255, name.hashCode()), 0.9F)
                                        )
                        );
                color = (color & 0xFFFFFF) | (alpha << 24);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x - 4, y - 4, 9, 9, color);
            }
        }
    }
}
