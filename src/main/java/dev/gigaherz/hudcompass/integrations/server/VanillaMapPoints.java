package dev.gigaherz.hudcompass.integrations.server;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.ConfigData;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.icons.IIconData;
import dev.gigaherz.hudcompass.server.ServerWaypointSync;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointInfoType;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adds waypoints for banner and other decoration markers on any map the player is carrying, for
 * the current dimension. Ported from upstream's {@code PlayerTickEvent.Post} listener onto
 * {@code ServerTickEvents}, same as {@code SpawnPointPoints}/{@code PlayerTracker}.
 * <p>
 * Needs {@code MapItemSavedData#bannerMarkers}/{@code #decorations} widened -- both private in
 * the real MC 26.2 jar (confirmed via javap; centerX/centerZ/dimension/scale are already public
 * so those didn't need widening) -- see {@code hudcompass.accesswidener}.
 */
public class VanillaMapPoints
{
    private static final Identifier ADDON_ID = HudCompass.location("vanilla_map");

    public static final PointInfoType<MapDecorationWaypoint> DECORATION_TYPE =
            PointInfoType.register("map_decoration", MapDecorationWaypoint.CODEC, MapDecorationWaypoint.STREAM_CODEC);
    public static final PointInfoType<MapBannerWaypoint> BANNER_TYPE =
            PointInfoType.register("map_banner", MapBannerWaypoint.CODEC, MapBannerWaypoint.STREAM_CODEC);

    private int counter = 0;

    public static void init()
    {
        VanillaMapPoints instance = new VanillaMapPoints();
        ServerTickEvents.END_SERVER_TICK.register(instance::serverTick);
    }

    private void serverTick(MinecraftServer server)
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
        PointsOfInterest.WorldPoints worldPoints = points.get(player.level());

        VanillaMapData addon = points.getOrCreateAddonData(ADDON_ID, VanillaMapData::new);

        Set<MapItemSavedData> seenMaps = getMapData(player, worldPoints, addon);

        Set<MapItemSavedData> toRemove = new HashSet<>(addon.mapDecorations.keySet());
        toRemove.removeAll(seenMaps);

        for (MapItemSavedData remove : toRemove)
        {
            Map<MapDecoration, PointInfo<?>> map = addon.mapDecorations.get(remove);
            for (PointInfo<?> pt : map.values())
            {
                worldPoints.removePoint(pt);
            }
            addon.mapDecorations.remove(remove);
        }
    }

    private Set<MapItemSavedData> getMapData(ServerPlayer player, PointsOfInterest.WorldPoints worldPoints, VanillaMapData addon)
    {
        if (!ConfigData.enableVanillaMapIntegration)
            return Collections.emptySet();

        Set<MapItemSavedData> seenMaps = Sets.newHashSet();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);
            MapItemSavedData mapData = MapItem.getSavedData(stack, player.level());
            if (mapData != null && !seenMaps.contains(mapData) && mapData.dimension == worldPoints.getWorldKey())
            {
                seenMaps.add(mapData);

                Map<MapDecoration, PointInfo<?>> decorationPointInfoMap = addon.mapDecorations.computeIfAbsent(mapData, k -> Maps.newHashMap());

                for (MapBanner banner : mapData.bannerMarkers.values())
                {
                    MapDecoration decoration = mapData.decorations.get(banner.getId());
                    if (decoration != null && !decorationPointInfoMap.containsKey(decoration))
                    {
                        MapBannerWaypoint wp = new MapBannerWaypoint(banner, decoration);
                        decorationPointInfoMap.put(decoration, wp);
                        worldPoints.addPoint(wp);
                    }
                }

                for (Map.Entry<String, MapDecoration> kvp : mapData.decorations.entrySet())
                {
                    MapDecoration decoration = kvp.getValue();

                    // skip players, PlayerTracker already handles those.
                    if (decoration.type() == MapDecorationTypes.PLAYER ||
                            decoration.type() == MapDecorationTypes.PLAYER_OFF_LIMITS ||
                            decoration.type() == MapDecorationTypes.PLAYER_OFF_MAP)
                        continue;

                    if (!decorationPointInfoMap.containsKey(decoration))
                    {
                        MapDecorationWaypoint wp = new MapDecorationWaypoint(mapData, decoration);
                        decorationPointInfoMap.put(decoration, wp);
                        worldPoints.addPoint(wp);
                    }
                }

                Set<MapDecoration> staleDecorations = new HashSet<>(decorationPointInfoMap.keySet());
                staleDecorations.removeAll(mapData.decorations.values());

                for (MapDecoration remove : staleDecorations)
                {
                    worldPoints.removePoint(decorationPointInfoMap.get(remove));
                    decorationPointInfoMap.remove(remove);
                }
            }
        }
        return seenMaps;
    }

    public static class MapBannerWaypoint extends PointInfo<MapBannerWaypoint>
    {
        public static final MapCodec<MapBannerWaypoint> CODEC = RecordCodecBuilder.mapCodec(instance ->
                codecFragment(instance).and(Vec3.CODEC.fieldOf("pos").forGetter(MapBannerWaypoint::getPosition)).apply(instance, MapBannerWaypoint::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, MapBannerWaypoint> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PointInfo::getInternalId,
                ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), e -> Optional.ofNullable(e.getLabel()),
                ByteBufCodecs.BOOL, e -> e.displayVerticalDistance(null),
                IIconData.DISPATCH_STREAM_CODEC, MapBannerWaypoint::getIconData,
                Vec3.STREAM_CODEC, MapBannerWaypoint::getPosition,
                MapBannerWaypoint::new
        );

        private Vec3 position;

        private Vec3 getPosition()
        {
            return position;
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        protected MapBannerWaypoint(UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData, Vec3 exactPosition)
        {
            super(BANNER_TYPE, true, internalId, label, displayVerticalDistance, iconData);
            this.position = exactPosition;
        }

        public MapBannerWaypoint(MapBanner banner, MapDecoration decoration)
        {
            super(BANNER_TYPE, true, banner.name().orElse(null), BasicIconData.basic(decoration.getSpriteLocation()));
            dynamic();
            this.position = Vec3.atCenterOf(banner.pos());
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return position;
        }
    }

    public static class MapDecorationWaypoint extends PointInfo<MapDecorationWaypoint>
    {
        public static final MapCodec<MapDecorationWaypoint> CODEC = RecordCodecBuilder.mapCodec(instance ->
                codecFragment(instance).and(Vec3.CODEC.fieldOf("pos").forGetter(MapDecorationWaypoint::getPosition)).apply(instance, MapDecorationWaypoint::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, MapDecorationWaypoint> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PointInfo::getInternalId,
                ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), e -> Optional.ofNullable(e.getLabel()),
                ByteBufCodecs.BOOL, e -> e.displayVerticalDistance(null),
                IIconData.DISPATCH_STREAM_CODEC, MapDecorationWaypoint::getIconData,
                Vec3.STREAM_CODEC, MapDecorationWaypoint::getPosition,
                MapDecorationWaypoint::new
        );

        private Vec3 position;

        private Vec3 getPosition()
        {
            return position;
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        protected MapDecorationWaypoint(UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData, Vec3 exactPosition)
        {
            super(DECORATION_TYPE, true, internalId, label, displayVerticalDistance, iconData);
            this.position = exactPosition;
        }

        public MapDecorationWaypoint(MapItemSavedData mapData, MapDecoration decoration)
        {
            super(DECORATION_TYPE, true, null, BasicIconData.basic(decoration.getSpriteLocation()));

            dynamic();
            noVerticalDistance();

            float decoX = (decoration.x() - 0.5f) * 0.5f;
            float decoZ = (decoration.y() - 0.5f) * 0.5f;

            int scale = 1 << mapData.scale;
            float worldX = mapData.centerX + decoX * scale;
            float worldZ = mapData.centerZ + decoZ * scale;

            this.position = new Vec3(worldX, 0, worldZ);
        }

        @Override
        public Vec3 getPosition(Player player, float partialTicks)
        {
            return position;
        }
    }

    private static class VanillaMapData
    {
        public Map<MapItemSavedData, Map<MapDecoration, PointInfo<?>>> mapDecorations = Maps.newHashMap();
    }
}
